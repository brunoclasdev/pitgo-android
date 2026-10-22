begin;
create function private.guincho_route_geometry_valid(g jsonb) returns boolean language plpgsql immutable set search_path='' as $$
declare point jsonb; lon double precision; lat double precision;
begin
 if g->>'type' is distinct from 'LineString' or jsonb_typeof(g->'coordinates') is distinct from 'array' then return false; end if;
 if jsonb_array_length(g->'coordinates') not between 2 and 10000 then return false; end if;
 for point in select value from jsonb_array_elements(g->'coordinates') loop
  if jsonb_typeof(point)<>'array' then return false; end if;
  if jsonb_array_length(point)<>2 or jsonb_typeof(point->0)<>'number' or jsonb_typeof(point->1)<>'number' then return false; end if;
  lon:=(point->>0)::double precision; lat:=(point->>1)::double precision;
  if lon not between -180 and 180 or lat not between -90 and 90 then return false; end if;
 end loop;
 return true;
exception when others then return false;
end $$;
alter table public.guincho_route_snapshots add constraint guincho_route_geometry_check check(geometry is null or private.guincho_route_geometry_valid(geometry));
create function public.finish_guincho_route_refresh(p_appointment_id uuid,p_lease_id uuid,p_geometry jsonb,p_distance_meters integer) returns jsonb
language plpgsql security definer set search_path='' as $$
declare r public.guincho_route_snapshots%rowtype; a public.appointments%rowtype; c jsonb;
begin
 select * into a from public.appointments where id=p_appointment_id for update;
 select * into r from public.guincho_route_snapshots where appointment_id=p_appointment_id for update;
 if r.lease_id is distinct from p_lease_id or p_lease_id is null or r.lease_expires_at<=now() or a.status<>'IN_PROGRESS' or a.service_started_at is null then
  return jsonb_build_object('outcome','LEASE_LOST'); end if;
 c:=r.lease_context;
 perform private.guincho_route_authorize(p_appointment_id,(c->>'requesting_user_id')::uuid);
 if (c->'destination'->>'latitude')::double precision is distinct from (a.guincho_trip_snapshot->>'destination_latitude')::double precision
 or (c->'destination'->>'longitude')::double precision is distinct from (a.guincho_trip_snapshot->>'destination_longitude')::double precision
 or (r.origin_published_at,r.provider_location_revision)>((c->'origin'->>'published_at')::timestamptz,(c->>'provider_location_revision')::bigint) then return jsonb_build_object('outcome','LEASE_LOST'); end if;
 if not private.guincho_route_geometry_valid(p_geometry) or p_distance_meters is null or p_distance_meters not between 0 and 2000000 then
  raise exception using errcode='22023',message='ROUTER_INVALID_RESPONSE'; end if;
 update public.guincho_route_snapshots set route_version=route_version+1,geometry=p_geometry,distance_meters=p_distance_meters,generated_at=now(),
 destination_latitude=(c->'destination'->>'latitude')::double precision,destination_longitude=(c->'destination'->>'longitude')::double precision,
 origin_latitude=(c->'origin'->>'latitude')::double precision,origin_longitude=(c->'origin'->>'longitude')::double precision,
 origin_accuracy_m=(c->'origin'->>'accuracy_m')::double precision,origin_captured_at=(c->'origin'->>'captured_at')::timestamptz,
 origin_published_at=(c->'origin'->>'published_at')::timestamptz,provider_location_revision=(c->>'provider_location_revision')::bigint,
 lease_id=null,lease_expires_at=null,lease_context=null,failure_count=0,last_failure_code=null,retry_after_at=null
 where appointment_id=p_appointment_id returning * into r;
 return private.guincho_route_json(r)||jsonb_build_object('outcome','APPLIED','reused',false);
end $$;
create function public.fail_guincho_route_refresh(p_appointment_id uuid,p_lease_id uuid,p_failure_code text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare wait_seconds integer;
begin
 perform 1 from public.appointments where id=p_appointment_id for update;
 select least(30,5*power(2,least(failure_count,3)))::integer into wait_seconds from public.guincho_route_snapshots
 where appointment_id=p_appointment_id and lease_id=p_lease_id for update;
 if not found then return jsonb_build_object('outcome','LEASE_LOST'); end if;
 update public.guincho_route_snapshots set lease_id=null,lease_expires_at=null,lease_context=null,failure_count=least(failure_count+1,100),
 last_failure_code=case when p_failure_code in ('ROUTER_TIMEOUT','ROUTER_FAILURE','ROUTER_INVALID_RESPONSE','SERVICE_UNAVAILABLE') then p_failure_code else 'ROUTER_FAILURE' end,
 retry_after_at=now()+make_interval(secs=>wait_seconds) where appointment_id=p_appointment_id;
 return jsonb_build_object('outcome','FAILED','retry_after_ms',wait_seconds*1000);
end $$;
create function private.guincho_route_cleanup() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if new.status<>'IN_PROGRESS' then delete from public.guincho_route_snapshots where appointment_id=new.id; end if;
 return new;
end $$;
create trigger guincho_route_cleanup after update of status on public.appointments for each row execute function private.guincho_route_cleanup();
revoke all on function private.guincho_route_geometry_valid(jsonb),private.guincho_route_cleanup() from public,anon,authenticated;
revoke all on function public.finish_guincho_route_refresh(uuid,uuid,jsonb,integer),public.fail_guincho_route_refresh(uuid,uuid,text) from public,anon,authenticated;
grant execute on function public.finish_guincho_route_refresh(uuid,uuid,jsonb,integer),public.fail_guincho_route_refresh(uuid,uuid,text) to service_role;
notify pgrst,'reload schema';
commit;
