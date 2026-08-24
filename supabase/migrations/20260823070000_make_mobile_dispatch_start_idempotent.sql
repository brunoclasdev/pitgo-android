-- A client may retry with the same search id after losing a response or after
-- process/navigation restoration. Return that search instead of attempting a
-- second insert and leaking a unique-violation through PostgREST.
create or replace function public.start_mobile_dispatch(
  p_search_id uuid,
  p_service_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_m double precision
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  existing_id uuid;
  kind text;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'Client required';
  end if;

  if p_latitude not between -90 and 90
    or p_longitude not between -180 and 180
    or p_accuracy_m <= 0
    or p_accuracy_m > 100 then
    raise exception using errcode = '22023', message = 'Invalid search point';
  end if;

  select dispatch_kind
    into kind
    from public.services
   where id = p_service_id
     and active;

  if kind is distinct from 'MOBILE_SEQUENTIAL' then
    raise exception using errcode = '22023', message = 'Service unavailable';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text, 1301));

  -- The request UUID is the idempotency key. This includes terminal searches,
  -- which are intentionally omitted from the "active search" lookup below.
  select id
    into existing_id
    from public.service_searches
   where id = p_search_id
     and client_id = auth.uid()
     and service_id = p_service_id;

  if existing_id is not null then
    return private.dispatch_snapshot(existing_id);
  end if;

  -- Do not disclose whether a request UUID belongs to another account/service.
  if exists (select 1 from public.service_searches where id = p_search_id) then
    raise exception using errcode = 'P0002', message = 'Search unavailable';
  end if;

  select id
    into existing_id
    from public.service_searches
   where client_id = auth.uid()
     and service_id = p_service_id
     and status in ('SEARCHING', 'OPTION_FOUND', 'AWAITING_PROVIDER', 'ACCEPTED', 'IN_PROGRESS')
   order by updated_at desc
   limit 1;

  if existing_id is not null then
    return private.dispatch_snapshot(existing_id);
  end if;

  insert into public.service_searches(
    id,
    client_id,
    service_id,
    search_latitude,
    search_longitude,
    search_point,
    search_accuracy_m
  ) values (
    p_search_id,
    auth.uid(),
    p_service_id,
    p_latitude,
    p_longitude,
    extensions.st_setsrid(
      extensions.st_makepoint(p_longitude, p_latitude),
      4326
    )::extensions.geography,
    p_accuracy_m
  );

  perform private.advance_mobile_dispatch(p_search_id);
  return private.dispatch_snapshot(p_search_id);
end;
$$;

revoke execute on function public.start_mobile_dispatch(
  uuid, uuid, double precision, double precision, double precision
) from public, anon;
grant execute on function public.start_mobile_dispatch(
  uuid, uuid, double precision, double precision, double precision
) to authenticated;

notify pgrst, 'reload schema';
