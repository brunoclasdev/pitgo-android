# Contract: Supabase Auth, profiles e vehicles

## Purpose

Definir a fronteira entre o aplicativo Android, Supabase Auth e o schema público. A implementação
deve usar a publishable/anon key já configurada; nenhuma operação depende de service role no APK.

## Auth contract

### Required project settings

- provedor Email habilitado;
- criação de usuário habilitada;
- confirmação de e-mail desabilitada para que cadastro/login resulte em sessão imediata;
- senha mínima compatível com a regra atual de oito caracteres;
- trigger de perfil instalado e funcionando antes da validação end-to-end.

### Operations

| Operation | Input | Success | Domain failures |
|---|---|---|---|
| initialize | none | local state loaded, then remotely verified when present | offline, invalid session |
| sign in | normalized email + in-memory password | SDK session imported, identity verified | invalid credentials, rate limit, offline, timeout, service |
| validate | current SDK session | current Auth user returned | invalid/revoked, offline, timeout |
| sign out | explicit local scope | local session removed and app state cleared | remote failure does not retain local private UI |

The app must never log the Auth exception, request headers, URL containing credentials, access
token, refresh token, password or raw response. Mapping may inspect only stable exception class,
status and allowlisted Auth error code.

## `public.profiles`

### Required projection

| Column | PostgreSQL type | Android | Contract |
|---|---|---|---|
| `id` | `uuid` | string | PK/FK to `auth.users.id` |
| `name` | `text` | string | required for an authorized profile |
| `email` | `text` | string | normalized, must match Auth email |
| `phone` | `text` | string | required for an authorized profile |
| `cpf` | `text` nullable | string? | XOR with CNPJ |
| `cnpj` | `text` nullable | string? | XOR with CPF |
| `avatar_url` | `text` nullable | string? | optional HTTPS avatar |
| `active` | `boolean` | boolean | only true authorizes Home |

The canonical status column is exactly `active`; `activity` and `activty` are forbidden aliases.
Existing `created_at`/`updated_at` columns may remain but are not required by the Android projection.

### Read

```text
table: profiles
filter: id == current Auth user id
limit: 1
```

Outcome classification:

- no row -> `ProfileBlocked.Missing`;
- row with `active = false` -> `ProfileBlocked.Inactive`;
- null/blank required data, ID/e-mail mismatch or invalid document XOR ->
  `ProfileBlocked.Inconsistent`;
- valid row -> profile portion of `AccountSnapshot`.

### Write

The Android client does not INSERT/UPDATE a profile in this feature. Signup metadata is consumed by
exactly one database trigger. Retry only reads again; it never upserts, recreates or reactivates.

### Trigger invariant

- exactly one `AFTER INSERT ON auth.users` trigger creates the profile;
- function is `SECURITY DEFINER SET search_path = ''` and fully qualifies relations;
- metadata keys are exactly `name`, `phone`, `cpf`, `cnpj`;
- e-mail comes from `new.email`, status from database default `active = true`;
- trigger/function cannot be executed directly by `public`, `anon` or `authenticated`.

## `public.vehicles`

### Table projection

| Column | Type | Nullable | Writer |
|---|---|---:|---|
| `id` | `uuid` | no | database default |
| `user_id` | `uuid` | no | RPC from `auth.uid()` |
| `brand` | `text` | no | RPC |
| `model` | `text` | no | RPC |
| `version` | `text` | yes | RPC |
| `manufacture_year` | `integer` | yes | RPC |
| `model_year` | `integer` | yes | RPC |
| `plate` | `text` | yes | RPC, canonical uppercase seven characters |
| `renavam` | `text` | yes | RPC, 11 digits |
| `chassis` | `text` | yes | RPC, uppercase VIN 17 |
| `color` | `text` | yes | RPC |
| `fuel_type` | `text` | yes | RPC |
| `transmission` | `text` | yes | RPC |
| `engine` | `text` | yes | RPC |
| `mileage` | `integer` | yes | RPC, nonnegative |
| `is_primary` | `boolean` | no | RPC only |
| `active` | `boolean` | no | database/RPC, true on create |
| `created_at` | `timestamptz` | no | database default |
| `updated_at` | `timestamptz` | no | database/RPC |

### Read list

```text
table: vehicles
filters:
  user_id == current Auth user id
  active == true
order:
  is_primary descending
  created_at ascending
maximum expected result: 100 rows
```

The explicit `user_id` filter is required even with RLS. A profile bootstrap may additionally query
`is_primary == true` with limit 1.

### Required indexes/constraints

- PK `vehicles_pkey` on `id`;
- FK `user_id -> profiles(id) ON DELETE CASCADE`;
- unique non-null canonical plate: `vehicles_plate_unique`;
- unique non-null Renavam: `vehicles_renavam_unique`;
- unique non-null canonical chassis: `vehicles_chassis_unique`;
- partial unique principal per user: `one_primary_vehicle_per_user`;
- `is_primary` implies `active`;
- brand/model nonblank after trim;
- mileage >= 0;
- year >= 1900; upper bound is enforced at operation time as next calendar year;
- plate/Renavam/chassis representation checks match the data model.

## RPC `create_vehicle`

### Signature

```text
public.create_vehicle(
  p_brand text,
  p_model text,
  p_version text,
  p_manufacture_year integer,
  p_model_year integer,
  p_plate text,
  p_renavam text,
  p_chassis text,
  p_color text,
  p_fuel_type text,
  p_transmission text,
  p_engine text,
  p_mileage integer,
  p_make_primary boolean
) returns setof public.vehicles
```

All keys are sent in one JSON parameter object; absent optionals are JSON null. No `user_id`,
`active`, `is_primary`, ID or timestamp parameter is accepted.

### Transactional behavior

1. require non-null `auth.uid()` and an active matching profile;
2. acquire transaction-scoped advisory lock derived from that UUID;
3. normalize and validate all inputs;
4. determine whether any active vehicle already exists;
5. if first or `p_make_primary = true`, clear any previous principal for the user;
6. insert one active vehicle, primary when first/requested;
7. return exactly the inserted row;
8. any failure rolls back every update/insert.

## RPC `set_primary_vehicle`

### Signature

```text
public.set_primary_vehicle(
  p_vehicle_id uuid
) returns setof public.vehicles
```

### Transactional behavior

1. require non-null `auth.uid()`;
2. acquire the same per-user transaction lock;
3. find an active target whose `user_id = auth.uid()`;
4. if it does not exist, return a neutral not-found/forbidden failure;
5. clear every previous principal for that user;
6. mark only the target principal and update its `updated_at`;
7. return exactly the target row;
8. any failure rolls back all changes.

## Function security

Both RPCs:

- are `SECURITY DEFINER SET search_path = ''` because direct writes are intentionally revoked;
- fully qualify `auth.uid`, `public.profiles`, `public.vehicles` and other objects;
- must not accept owner/status fields;
- revoke EXECUTE from `public` and `anon`;
- grant EXECUTE only to `authenticated`;
- must be reviewed whenever the table contract changes.

## Grants and RLS

| Object | anon | authenticated |
|---|---|---|
| `profiles` | none | SELECT only |
| `vehicles` | none | SELECT only |
| `create_vehicle` | no EXECUTE | EXECUTE |
| `set_primary_vehicle` | no EXECUTE | EXECUTE |

Policies:

- profile SELECT: `(select auth.uid()) = id`;
- vehicle SELECT: `(select auth.uid()) = user_id`;
- RLS enabled on both tables;
- stale INSERT/UPDATE/DELETE policies may be removed; in every case direct DML grants remain revoked.

## Error mapping

| Source | Stable discriminator | Android result |
|---|---|---|
| Auth | invalid credentials code/401 | invalid login; clear password |
| Auth/PostgREST | network exception | offline |
| Auth/PostgREST | timeout | timeout/service unavailable |
| Postgres | `23505` + `vehicles_plate_unique` token | plate unavailable |
| Postgres | `23505` + `vehicles_renavam_unique` token | Renavam unavailable |
| Postgres | `23505` + `vehicles_chassis_unique` token | chassis unavailable |
| Postgres | `23514`/`22023` | invalid server input/contract failure |
| Postgres | `42501` or unauthorized response | session/permission failure |
| Postgres | neutral not found | vehicle unavailable; do not reveal owner |
| any | 5xx | service unavailable |
| any | unrecognized | generic failure |

Only the allowlisted constraint tokens above may be inspected. `message`, `details`, response
headers, request URL and raw Throwable must not be logged or shown.

## Migration reconciliation

The implementation migration must support both states currently known:

1. a clean local database containing the repository's profile migration but no vehicles;
2. the remote project containing the manually created `profiles`/`vehicles`, indexes, broad policies
   and `public.handle_new_user` trigger supplied by the user.

It must preserve rows, ensure `avatar_url` exists, converge to one secure profile trigger, converge
indexes/constraints/grants/policies, and install the RPCs. It must not drop either table or perform
remote `db push` automatically.
