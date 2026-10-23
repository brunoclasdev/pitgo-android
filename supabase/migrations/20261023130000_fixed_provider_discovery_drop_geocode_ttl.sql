-- A geolocalização de um prestador FIXED (loja/oficina) não muda como a de um guincho em
-- movimento: exigir que o geocode tenha sido verificado nos últimos 30 dias só fazia prestadores
-- ativos e disponíveis sumirem silenciosamente da busca, sem nenhum fluxo de renovação no app.
-- O endereço em si continua obrigatório (address_point is not null); só a janela de validade sai.

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
  group by provider.id,provider.name,provider.location_mode,address.latitude,address.longitude
  order by provider.name,provider.id
  limit p_limit;
end;
$$;

create or replace function public.discover_service_providers(
  p_service_id uuid,p_center_lat double precision,p_center_long double precision,
  p_radius_meters integer default 25000,p_limit integer default 100
) returns table(provider_id uuid,display_name text,location_mode text,latitude double precision,
  longitude double precision,distance_meters double precision,minimum_price numeric)
language plpgsql stable security definer set search_path='' as $$
begin
  if not private.dispatch_is_client() then raise exception using errcode='42501',message='Client required'; end if;
  if p_center_lat not between -90 and 90 or p_center_long not between -180 and 180
    or p_radius_meters not between 1 and 200000 or p_limit not between 1 and 200 then
    raise exception using errcode='22023',message='Invalid discovery area'; end if;
  return query select p.id,p.name,p.location_mode,a.latitude::double precision,a.longitude::double precision,
    extensions.st_distance(a.address_point,extensions.st_setsrid(extensions.st_makepoint(p_center_long,p_center_lat),4326)::extensions.geography),min(ps.price)
  from public.provider_services ps join public.providers p on p.id=ps.provider_id
  join public.addresses a on a.provider_id=p.id join public.services s on s.id=ps.service_id
  join public.service_categories c on c.id=s.category_id
  where ps.service_id=p_service_id and ps.active and s.active and c.active and s.dispatch_kind='BROWSE'
    and p.active and p.available and p.location_mode='FIXED' and a.address_point is not null
    and extensions.st_dwithin(a.address_point,
      extensions.st_setsrid(extensions.st_makepoint(p_center_long,p_center_lat),4326)::extensions.geography,p_radius_meters)
  group by p.id,p.name,p.location_mode,a.latitude,a.longitude,a.address_point order by 6,p.id limit p_limit;
end;
$$;

revoke all on function public.list_service_provider_locations(uuid,integer) from public,anon,authenticated;
grant execute on function public.list_service_provider_locations(uuid,integer) to authenticated;
revoke all on function public.discover_service_providers(uuid,double precision,double precision,integer,integer) from public,anon,authenticated;
grant execute on function public.discover_service_providers(uuid,double precision,double precision,integer,integer) to authenticated;

notify pgrst,'reload schema';

commit;
