begin;
create function public.save_guincho_trip_draft(
 p_vehicle_id uuid,p_pickup_address text,p_pickup_latitude numeric,p_pickup_longitude numeric,
 p_destination_address text,p_destination_latitude numeric,p_destination_longitude numeric,
 p_draft_id uuid default null,p_expected_revision bigint default null
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v public.vehicles%rowtype; d public.guincho_trip_drafts%rowtype; vehicle_json jsonb;
begin
 if not private.dispatch_is_client() then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 select * into v from public.vehicles where id=p_vehicle_id and user_id=auth.uid() and active;
 if v.id is null then raise exception using errcode='22023',message='INVALID_VEHICLE'; end if;
 if p_pickup_latitude is null or p_pickup_longitude is null or p_destination_latitude is null
 or p_destination_longitude is null or p_pickup_address is null or p_destination_address is null then
 raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 vehicle_json:=jsonb_build_object('id',v.id,'brand',v.brand,'model',v.model,'plate',v.plate,'color',v.color);
 if p_draft_id is null then
  insert into public.guincho_trip_drafts(client_id,vehicle_id,vehicle_snapshot,pickup_address,pickup_latitude,
   pickup_longitude,destination_address,destination_latitude,destination_longitude)
  values(auth.uid(),v.id,vehicle_json,btrim(p_pickup_address),p_pickup_latitude,p_pickup_longitude,
   btrim(p_destination_address),p_destination_latitude,p_destination_longitude) returning * into d;
 else
  select * into d from public.guincho_trip_drafts where id=p_draft_id and client_id=auth.uid() for update;
  if d.id is null then raise exception using errcode='42501',message='DRAFT_UNAVAILABLE'; end if;
  if d.revision is distinct from p_expected_revision then
   raise exception using errcode='40001',message='REVISION_CONFLICT'; end if;
  update public.guincho_trip_drafts set vehicle_id=v.id,vehicle_snapshot=vehicle_json,
   pickup_address=btrim(p_pickup_address),pickup_latitude=p_pickup_latitude,pickup_longitude=p_pickup_longitude,
   destination_address=btrim(p_destination_address),destination_latitude=p_destination_latitude,
   destination_longitude=p_destination_longitude,revision=revision+1,route_distance_meters=null,
   route_input_revision=null,route_calculated_at=null,updated_at=now() where id=d.id returning * into d;
 end if;
 return jsonb_build_object('draft_id',d.id,'revision',d.revision);
end; $$;

create function public.discover_guincho_providers(p_draft_id uuid,p_expected_revision bigint,p_limit integer default 200)
returns table(provider_id uuid,provider_service_id uuid,display_name text,latitude double precision,
 longitude double precision,distance_meters double precision)
language plpgsql stable security definer set search_path='' as $$
declare d public.guincho_trip_drafts%rowtype;
begin
 select * into d from public.guincho_trip_drafts where id=p_draft_id and client_id=auth.uid();
 if d.id is null then raise exception using errcode='42501',message='DRAFT_UNAVAILABLE'; end if;
 if d.revision is distinct from p_expected_revision then raise exception using errcode='40001',message='REVISION_CONFLICT'; end if;
 return query select distinct on (p.id) p.id,ps.id,p.name,l.latitude::double precision,l.longitude::double precision,
 extensions.st_distance(l.position,extensions.st_setsrid(extensions.st_makepoint(d.pickup_longitude,d.pickup_latitude),4326)::extensions.geography)
 from public.providers p join public.provider_services ps on ps.provider_id=p.id
 join public.services s on s.id=ps.service_id join public.service_categories c on c.id=s.category_id
 join public.provider_discovery_locations l on l.provider_id=p.id
 where p.active and p.available and p.location_mode='MOBILE' and ps.active and s.active and c.active
 and s.dispatch_kind='MOBILE_SEQUENTIAL' and l.published_at>=now()-interval '30 seconds'
 and extensions.st_dwithin(l.position,extensions.st_setsrid(extensions.st_makepoint(d.pickup_longitude,d.pickup_latitude),4326)::extensions.geography,25000)
 and not exists(select 1 from public.appointments a where a.provider_id=p.id
  and a.status in ('PENDING','AWAITING_PAYMENT','AWAITING_CLIENT_CONFIRMATION','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION'))
 order by p.id,ps.id limit greatest(1,least(coalesce(p_limit,200),200));
end; $$;
revoke all on function public.save_guincho_trip_draft(uuid,text,numeric,numeric,text,numeric,numeric,uuid,bigint),
public.discover_guincho_providers(uuid,bigint,integer) from public,anon;
grant execute on function public.save_guincho_trip_draft(uuid,text,numeric,numeric,text,numeric,numeric,uuid,bigint),
public.discover_guincho_providers(uuid,bigint,integer) to authenticated;
commit;
