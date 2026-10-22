begin;
create table public.guincho_route_snapshots (
 appointment_id uuid primary key references public.appointments(id) on delete cascade,
 route_version bigint not null default 0 check(route_version>=0),
 destination_latitude double precision not null check(destination_latitude between -90 and 90),
 destination_longitude double precision not null check(destination_longitude between -180 and 180),
 geometry jsonb, distance_meters integer check(distance_meters between 0 and 2000000), generated_at timestamptz,
 origin_latitude double precision, origin_longitude double precision, origin_accuracy_m double precision,
 origin_captured_at timestamptz, origin_published_at timestamptz, provider_location_revision bigint,
 provider text not null default 'osrm' check(provider='osrm'),
 lease_id uuid, lease_expires_at timestamptz, lease_context jsonb,
 last_attempt_at timestamptz, retry_after_at timestamptz, failure_count integer not null default 0, last_failure_code text,
 check((geometry is null and distance_meters is null and generated_at is null and origin_latitude is null and origin_longitude is null
 and origin_accuracy_m is null and origin_captured_at is null and origin_published_at is null and provider_location_revision is null)
 or (geometry is not null and distance_meters is not null and generated_at is not null and route_version>0
 and origin_latitude is not null and origin_longitude is not null and origin_accuracy_m is not null and provider_location_revision is not null
 and origin_latitude between -90 and 90 and origin_longitude between -180 and 180 and origin_accuracy_m>0 and origin_accuracy_m<=100
 and origin_captured_at is not null and origin_published_at is not null and provider_location_revision>0)),
 check((lease_id is null and lease_expires_at is null and lease_context is null) or (lease_id is not null and lease_expires_at is not null and lease_context is not null))
);
alter table public.guincho_route_snapshots enable row level security;
revoke all on public.guincho_route_snapshots from public,anon,authenticated;
grant all on public.guincho_route_snapshots to service_role;
create function private.guincho_route_authorize(p_id uuid,p_user uuid) returns public.appointments
language plpgsql stable security definer set search_path='' as $$
declare a public.appointments%rowtype;
begin
 if p_user is null then raise exception using errcode='42501',message='AUTH_REQUIRED'; end if;
 select x.* into a from public.appointments x join public.providers p on p.id=x.provider_id
 where x.id=p_id and (x.client_id=p_user or p.account_id=p_user);
 if a.id is null then raise exception using errcode='42501',message='NOT_PARTICIPANT'; end if;
 if a.guincho_quote_id is null or a.status<>'IN_PROGRESS' or a.service_started_at is null
 or not exists(select 1 from public.service_searches where id=a.dispatch_search_id and dispatch_kind='GUINCHO_DIRECT')
 or (a.guincho_inspection_required and not exists(select 1 from public.guincho_inspection_versions
 where appointment_id=a.id and state='APPROVED' and version_number=(select max(version_number) from public.guincho_inspection_versions where appointment_id=a.id)))
 or not exists(select 1 from public.payments p where p.request_id=a.dispatch_search_id and p.appointment_id=a.id and p.quote_version=a.quote_version
 and p.amount=coalesce(a.payment_amount,a.customer_total_amount) and private.payment_can_release(p,a.service_id)) then
  raise exception using errcode='22023',message='APPOINTMENT_NOT_TRACKABLE';
 end if;
 return a;
end $$;
create function private.guincho_route_json(r public.guincho_route_snapshots) returns jsonb
language sql stable security definer set search_path='' as $$
 select jsonb_build_object('appointment_id',r.appointment_id,'route_version',r.route_version,'geometry',r.geometry,
 'distance_meters',r.distance_meters,'generated_at',r.generated_at,'provider_location_revision',r.provider_location_revision,
 'provider_location_published_at',r.origin_published_at,
 'origin',jsonb_build_object('latitude',r.origin_latitude,'longitude',r.origin_longitude,'accuracy_m',r.origin_accuracy_m,
 'captured_at',r.origin_captured_at,'published_at',r.origin_published_at),
 'destination',jsonb_build_object('latitude',r.destination_latitude,'longitude',r.destination_longitude));
$$;
create function public.get_guincho_route_snapshot(p_appointment_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare r public.guincho_route_snapshots%rowtype;
begin
 perform private.guincho_route_authorize(p_appointment_id,auth.uid());
 select * into r from public.guincho_route_snapshots where appointment_id=p_appointment_id;
 if r.geometry is null then return jsonb_build_object('outcome','NOT_FOUND'); end if;
 return private.guincho_route_json(r)||jsonb_build_object('outcome','FOUND');
end $$;
revoke all on function private.guincho_route_authorize(uuid,uuid),private.guincho_route_json(public.guincho_route_snapshots) from public,anon,authenticated;
revoke all on function public.get_guincho_route_snapshot(uuid) from public,anon;
grant execute on function public.get_guincho_route_snapshot(uuid) to authenticated;
commit;
