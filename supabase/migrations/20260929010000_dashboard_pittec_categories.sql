-- specs/002-pittec-category-earnings: category filter + category display for Pittecs.
--
-- public.service_categories / public.services already carry an RLS policy scoped to `active =
-- true` and granted to `authenticated` — dashboard staff can already read them directly (no RPC
-- needed for the filter dropdown, see research.md §1). This migration only touches the two
-- Pittec read RPCs from 001-pittec-client-dashboard:
--   - dashboard_get_pittec_detail(uuid): same signature, body replaced to add `categories`.
--   - dashboard_list_pittecs(...): signature changes (new input + new output column), so
--     `create or replace` would create a second overload instead of replacing it — the old
--     4-argument version is dropped first (research.md §2). This repo is the only caller.

drop function if exists public.dashboard_list_pittecs(text, text, integer, integer);
create function public.dashboard_list_pittecs(
  p_query text default null,
  p_status text default null,
  p_category_id uuid default null,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table (
  id uuid,
  name text,
  phone text,
  email text,
  active boolean,
  city text,
  categories text[],
  total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  return query
  select provider.id, provider.name, provider.phone, provider.email, provider.active,
    address.city,
    (
      select array_agg(distinct sc.name order by sc.name)
      from public.provider_services ps
      join public.services svc on svc.id = ps.service_id
      join public.service_categories sc on sc.id = svc.category_id
      where ps.provider_id = provider.id
    ) as categories,
    count(*) over ()::bigint as total_count
  from public.providers provider
  left join public.addresses address on address.provider_id = provider.id
  where (p_query is null or provider.name ilike '%' || p_query || '%')
    and (
      p_status is null
      or (p_status = 'active' and provider.active)
      or (p_status = 'inactive' and not provider.active)
    )
    and (
      p_category_id is null
      or exists (
        select 1
        from public.provider_services ps
        join public.services svc on svc.id = ps.service_id
        where ps.provider_id = provider.id and svc.category_id = p_category_id
      )
    )
  order by provider.name asc
  limit least(greatest(p_limit, 1), 100)
  offset greatest(p_offset, 0);
end;
$$;
revoke all on function public.dashboard_list_pittecs(text, text, uuid, integer, integer)
  from public, anon;
grant execute on function public.dashboard_list_pittecs(text, text, uuid, integer, integer)
  to authenticated;
create or replace function public.dashboard_get_pittec_detail(p_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  caller_level text := private.dashboard_access_level();
  provider_row public.providers%rowtype;
  address_row public.addresses%rowtype;
  services_json jsonb;
  categories_json jsonb;
  active_appointment_id uuid;
  location_row public.provider_current_locations%rowtype;
  masked_cnpj text;
begin
  if caller_level is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  select * into provider_row from public.providers where id = p_id;
  if provider_row.id is null then
    return null;
  end if;

  select * into address_row from public.addresses where provider_id = p_id limit 1;

  select coalesce(jsonb_agg(jsonb_build_object(
      'provider_service_id', ps.id,
      'service_id', ps.service_id,
      'service_name', svc.name,
      'price', ps.price,
      'duration_minutes', ps.duration_minutes,
      'active', ps.active,
      'updated_at', ps.updated_at
    ) order by svc.name), '[]'::jsonb)
  into services_json
  from public.provider_services ps
  join public.services svc on svc.id = ps.service_id
  where ps.provider_id = p_id;

  select coalesce(jsonb_agg(distinct sc.name order by sc.name), '[]'::jsonb)
  into categories_json
  from public.provider_services ps
  join public.services svc on svc.id = ps.service_id
  join public.service_categories sc on sc.id = svc.category_id
  where ps.provider_id = p_id;

  masked_cnpj := case
    when provider_row.cnpj is null then null
    when caller_level = 'OWNER' then provider_row.cnpj
    else repeat('*', greatest(length(provider_row.cnpj) - 4, 0)) || right(provider_row.cnpj, 4)
  end;

  if caller_level = 'OWNER' then
    select appointment.id into active_appointment_id
    from public.appointments appointment
    where appointment.provider_id = p_id and appointment.status in ('ACCEPTED', 'IN_PROGRESS')
    order by appointment.updated_at desc
    limit 1;

    if active_appointment_id is not null then
      select * into location_row
      from public.provider_current_locations
      where appointment_id = active_appointment_id;
    end if;
  end if;

  return jsonb_build_object(
    'id', provider_row.id,
    'name', provider_row.name,
    'description', provider_row.description,
    'email', provider_row.email,
    'phone', provider_row.phone,
    'cnpj', masked_cnpj,
    'cnpj_masked', caller_level <> 'OWNER',
    'active', provider_row.active,
    'updated_at', provider_row.updated_at,
    'address', case when address_row.id is null then null else jsonb_build_object(
      'street', address_row.street, 'number', address_row.number,
      'neighborhood', address_row.neighborhood, 'city', address_row.city,
      'state', address_row.state, 'zip_code', address_row.zip_code,
      'complement', address_row.complement
    ) end,
    'services', services_json,
    'categories', categories_json,
    'live_location', case when location_row.appointment_id is null then null else jsonb_build_object(
      'latitude', location_row.latitude, 'longitude', location_row.longitude,
      'captured_at', location_row.captured_at
    ) end
  );
end;
$$;
