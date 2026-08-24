-- Let an authenticated client render the public fixed-provider map immediately, even when the
-- device has no recent location fix. Distance-based discovery remains authoritative after the
-- client chooses/obtains a map center.

begin;

create or replace function public.list_service_provider_locations(
  p_service_id uuid,
  p_limit integer default 200
) returns table(
  provider_id uuid,
  display_name text,
  location_mode text,
  latitude double precision,
  longitude double precision,
  distance_meters double precision,
  minimum_price numeric
)
language plpgsql
stable
security definer
set search_path=''
as $$
begin
  if not private.dispatch_is_client() then
    raise exception using errcode='42501',message='Client required';
  end if;
  if p_limit not between 1 and 200 then
    raise exception using errcode='22023',message='Invalid result limit';
  end if;
  if not exists(
    select 1 from public.services as service
    where service.id=p_service_id and service.active and service.dispatch_kind='BROWSE'
  ) then
    raise exception using errcode='22023',message='Service unavailable';
  end if;

  return query
  select
    provider.id,
    provider.name,
    provider.location_mode,
    address.latitude::double precision,
    address.longitude::double precision,
    null::double precision,
    min(offer.price)
  from public.provider_services as offer
  join public.providers as provider on provider.id=offer.provider_id
  join public.addresses as address on address.provider_id=provider.id
  join public.services as service on service.id=offer.service_id
  join public.service_categories as category on category.id=service.category_id
  where offer.service_id=p_service_id
    and offer.active
    and service.active
    and category.active
    and service.dispatch_kind='BROWSE'
    and provider.active
    and provider.available
    and provider.location_mode='FIXED'
    and address.address_point is not null
    and address.geocode_expires_at>now()
  group by provider.id,provider.name,provider.location_mode,address.latitude,address.longitude
  order by provider.name,provider.id
  limit p_limit;
end;
$$;

revoke all on function public.list_service_provider_locations(uuid,integer)
  from public,anon,authenticated;
grant execute on function public.list_service_provider_locations(uuid,integer)
  to authenticated;

notify pgrst,'reload schema';

commit;
