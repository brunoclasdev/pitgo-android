-- Allow the trusted geocoding Edge Function to create short-lived proofs without
-- exposing the private schema through the Data API.

create or replace function public.create_provider_geocode_proof(
  p_account_id uuid,
  p_token text,
  p_normalized_address jsonb,
  p_place_id text,
  p_latitude numeric,
  p_longitude numeric,
  p_device_distance_m numeric,
  p_mismatch_confirmed boolean,
  p_geocoded_at timestamptz,
  p_expires_at timestamptz
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_account_id is null
    or p_token is null
    or p_token !~ '^[A-Za-z0-9_-]{43,128}$'
    or p_normalized_address is null
    or nullif(pg_catalog.btrim(p_place_id), '') is null
    or p_latitude not between -90 and 90
    or p_longitude not between -180 and 180
    or p_device_distance_m < 0
    or p_geocoded_at is null
    or p_expires_at <= p_geocoded_at
    or p_expires_at > p_geocoded_at + interval '15 minutes'
  then
    raise exception using errcode = '22023', message = 'Invalid geocode proof';
  end if;

  if not exists (
    select 1
    from public.account_roles role
    where role.user_id = p_account_id
      and role.account_type = 'PROVIDER'
  ) then
    raise exception using errcode = '42501', message = 'Provider account required';
  end if;

  insert into private.provider_geocode_proofs (
    account_id,
    token_hash,
    normalized_address,
    place_id,
    latitude,
    longitude,
    device_distance_m,
    mismatch_confirmed,
    geocoded_at,
    expires_at
  ) values (
    p_account_id,
    extensions.digest(p_token, 'sha256'),
    p_normalized_address,
    p_place_id,
    p_latitude,
    p_longitude,
    p_device_distance_m,
    p_mismatch_confirmed,
    p_geocoded_at,
    p_expires_at
  );
end;
$$;

revoke all on function public.create_provider_geocode_proof(
  uuid, text, jsonb, text, numeric, numeric, numeric, boolean, timestamptz, timestamptz
) from public, anon, authenticated;

grant execute on function public.create_provider_geocode_proof(
  uuid, text, jsonb, text, numeric, numeric, numeric, boolean, timestamptz, timestamptz
) to service_role;
