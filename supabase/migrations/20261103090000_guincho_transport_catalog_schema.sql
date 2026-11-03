begin;
create table public.vehicle_transport_categories (
 id uuid primary key default gen_random_uuid(), code text not null unique check(code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
 name text not null check(length(btrim(name)) between 1 and 100), active boolean not null default true,
 revision bigint not null default 1 check(revision>0), created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.guincho_sizes (
 id uuid primary key default gen_random_uuid(), code text not null unique check(code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
 name text not null check(length(btrim(name)) between 1 and 100), active boolean not null default true,
 revision bigint not null default 1 check(revision>0), created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.guincho_size_compatibility (
 size_id uuid not null references public.guincho_sizes(id), vehicle_category_id uuid not null references public.vehicle_transport_categories(id),
 primary key(size_id,vehicle_category_id)
);
create index guincho_compatibility_category on public.guincho_size_compatibility(vehicle_category_id,size_id);
create table public.guincho_size_prices (
 id uuid primary key default gen_random_uuid(), size_id uuid not null references public.guincho_sizes(id),
 version bigint generated always as identity unique, request_id uuid not null unique,
 base_amount numeric(12,2) not null check(base_amount>0), currency text not null default 'BRL' check(currency='BRL'),
 active boolean not null default true, effective_from timestamptz not null default now(), effective_until timestamptz,
 created_by uuid not null references auth.users(id), created_at timestamptz not null default now()
);
create unique index guincho_size_price_current on public.guincho_size_prices(size_id) where active;
create table private.guincho_catalog_operations (
 request_id uuid primary key, actor_id uuid not null, payload jsonb not null, result jsonb not null
);
revoke all on private.guincho_catalog_operations from public,anon,authenticated;
alter table public.vehicles add column transport_category_id uuid references public.vehicle_transport_categories(id);
alter table public.provider_vehicles add column guincho_size_id uuid references public.guincho_sizes(id), add column revision bigint not null default 1;
alter table public.providers add column dispatch_vehicle_id uuid references public.provider_vehicles(id);
alter table public.guincho_quotes add column dispatch_vehicle_id uuid references public.provider_vehicles(id),
 add column guincho_size_id uuid references public.guincho_sizes(id), add column transport_category_id uuid references public.vehicle_transport_categories(id),
 add column size_price_id uuid references public.guincho_size_prices(id), add column size_price_version bigint;
create index vehicles_transport_category on public.vehicles(transport_category_id);
create index provider_vehicles_size on public.provider_vehicles(guincho_size_id);
create index providers_dispatch_vehicle on public.providers(dispatch_vehicle_id);
alter table public.vehicle_transport_categories enable row level security;
alter table public.guincho_sizes enable row level security;
alter table public.guincho_size_compatibility enable row level security;
alter table public.guincho_size_prices enable row level security;
revoke all on public.vehicle_transport_categories,public.guincho_sizes,public.guincho_size_compatibility,public.guincho_size_prices from public,anon,authenticated;
-- Catalogs and prices are projected through authenticated RPCs; no direct write grants.
commit;
