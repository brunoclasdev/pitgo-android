-- Client-facing address: does not exist yet despite the Home screen implying it does (it only
-- renders a static "não cadastrado" string today — see specs/014-client-ux-improvements/research.md
-- #1). Deliberately a separate table from public.addresses, which is tied to the provider's
-- public FIXED-mode business address (geocoding/discovery semantics from features 009/011) — a
-- different purpose and lifecycle than a client's private contact address. Mirrors the same
-- validation, RLS and RPC-only-access pattern already established for public.addresses.
begin;

create table if not exists public.client_addresses (
  client_id uuid primary key references public.profiles(id) on delete cascade,
  street text not null,
  number text not null,
  neighborhood text not null,
  zip_code text not null,
  city text not null,
  state text not null,
  country text not null default 'BR',
  complement text,
  latitude numeric(9,6),
  longitude numeric(10,6),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint client_addresses_street_normalized
    check (street = btrim(street) and street <> '' and char_length(street) <= 160),
  constraint client_addresses_number_normalized
    check (number = btrim(number) and number <> '' and char_length(number) <= 40),
  constraint client_addresses_neighborhood_normalized
    check (neighborhood = btrim(neighborhood) and neighborhood <> '' and char_length(neighborhood) <= 100),
  constraint client_addresses_zip_code_normalized check (zip_code ~ '^[0-9]{8}$'),
  constraint client_addresses_city_normalized
    check (city = btrim(city) and city <> '' and char_length(city) <= 100),
  constraint client_addresses_state_normalized check (
    state = upper(state) and state = any (array[
      'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR',
      'PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
    ])
  ),
  constraint client_addresses_country_brazil check (country = 'BR'),
  constraint client_addresses_complement_length
    check (complement is null or char_length(complement) <= 160),
  constraint client_addresses_coordinates_pair
    check ((latitude is null) = (longitude is null)),
  constraint client_addresses_latitude_range
    check (latitude is null or latitude between -90 and 90),
  constraint client_addresses_longitude_range
    check (longitude is null or longitude between -180 and 180)
);

alter table public.client_addresses enable row level security;
revoke all on table public.client_addresses from public, anon, authenticated;

create or replace function public.get_my_client_address()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  address_row public.client_addresses%rowtype;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'Client required';
  end if;

  select * into address_row from public.client_addresses where client_id = auth.uid();
  if address_row.client_id is null then
    return jsonb_build_object('outcome', 'NOT_FOUND');
  end if;

  return jsonb_build_object(
    'outcome', 'FOUND',
    'address', jsonb_build_object(
      'street', address_row.street,
      'number', address_row.number,
      'neighborhood', address_row.neighborhood,
      'zip_code', address_row.zip_code,
      'city', address_row.city,
      'state', address_row.state,
      'country', address_row.country,
      'complement', address_row.complement,
      'latitude', address_row.latitude,
      'longitude', address_row.longitude
    )
  );
end;
$$;

create or replace function public.upsert_my_client_address(
  p_street text,
  p_number text,
  p_neighborhood text,
  p_zip_code text,
  p_city text,
  p_state text,
  p_complement text,
  p_latitude double precision,
  p_longitude double precision
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  address_row public.client_addresses%rowtype;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'Client required';
  end if;

  insert into public.client_addresses(
    client_id, street, number, neighborhood, zip_code, city, state, complement, latitude, longitude
  ) values (
    auth.uid(), btrim(p_street), btrim(p_number), btrim(p_neighborhood),
    regexp_replace(p_zip_code, '[^0-9]', '', 'g'), btrim(p_city), upper(btrim(p_state)),
    nullif(btrim(p_complement), ''), p_latitude, p_longitude
  )
  on conflict (client_id) do update set
    street = excluded.street,
    number = excluded.number,
    neighborhood = excluded.neighborhood,
    zip_code = excluded.zip_code,
    city = excluded.city,
    state = excluded.state,
    complement = excluded.complement,
    latitude = excluded.latitude,
    longitude = excluded.longitude,
    updated_at = now()
  returning * into address_row;

  return jsonb_build_object(
    'outcome', 'SAVED',
    'address', jsonb_build_object(
      'street', address_row.street,
      'number', address_row.number,
      'neighborhood', address_row.neighborhood,
      'zip_code', address_row.zip_code,
      'city', address_row.city,
      'state', address_row.state,
      'country', address_row.country,
      'complement', address_row.complement,
      'latitude', address_row.latitude,
      'longitude', address_row.longitude
    )
  );
exception
  when check_violation or not_null_violation then
    raise exception using errcode = '22023', message = 'Invalid address';
end;
$$;

revoke all on function public.get_my_client_address() from public, anon;
revoke all on function public.upsert_my_client_address(
  text, text, text, text, text, text, text, double precision, double precision
) from public, anon;
grant execute on function public.get_my_client_address() to authenticated;
grant execute on function public.upsert_my_client_address(
  text, text, text, text, text, text, text, double precision, double precision
) to authenticated;

notify pgrst, 'reload schema';

commit;
