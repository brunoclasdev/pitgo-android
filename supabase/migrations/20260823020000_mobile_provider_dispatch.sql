begin;

-- Forward-only evolution for mobile provider discovery and sequential dispatch.
-- The device never supplies an owner id; every public function derives it from auth.uid().
create extension if not exists postgis with schema extensions;
create extension if not exists pg_cron with schema pg_catalog;

do $$
begin
  if to_regclass('public.account_roles') is null
    or to_regclass('public.services') is null
    or to_regclass('public.provider_services') is null
    or to_regclass('public.providers') is null
    or to_regclass('public.appointments') is null then
    raise exception 'Mobile dispatch prerequisites are missing';
  end if;
end
$$;

alter table public.services
  add column if not exists dispatch_kind text not null default 'BROWSE';
alter table public.services drop constraint if exists services_dispatch_kind_check;
alter table public.services add constraint services_dispatch_kind_check
  check (dispatch_kind in ('BROWSE', 'MOBILE_SEQUENTIAL'));

alter table public.provider_services
  add column if not exists active boolean not null default true;

-- An empty catalog is allowed during a clean local bootstrap. Once a catalog exists, it must
-- identify exactly one tow service before this migration may continue.
do $$
declare catalog_count integer;
declare tow_count integer;
declare roadside_category_id uuid;
begin
  select count(*) into catalog_count from public.services;
  select count(*) into tow_count
  from public.services
  where lower(btrim(name)) ~ '(^|[^[:alpha:]])(guincho|reboque)([^[:alpha:]]|$)';

  if tow_count = 0 and catalog_count > 0 then
    select category_id into roadside_category_id
    from public.services
    where lower(btrim(name)) in ('troca de bateria','bateria')
    order by id limit 1;
    if roadside_category_id is not null then
      insert into public.services(category_id,name,description,active,dispatch_kind)
      values(roadside_category_id,'Guincho','Guincho móvel para assistência e reboque',true,'MOBILE_SEQUENTIAL');
      tow_count := 1;
    end if;
  end if;

  if catalog_count > 0 and tow_count <> 1 then
    raise exception 'Catalog must identify exactly one tow service';
  end if;

  if tow_count = 1 then
    update public.services set dispatch_kind = 'BROWSE';
    update public.services
    set dispatch_kind = 'MOBILE_SEQUENTIAL'
    where lower(btrim(name)) ~ '(^|[^[:alpha:]])(guincho|reboque)([^[:alpha:]]|$)';
  end if;
end
$$;

create table if not exists public.provider_discovery_locations (
  provider_id uuid primary key references public.providers(id) on delete cascade,
  latitude numeric(9,6) not null,
  longitude numeric(10,6) not null,
  position extensions.geography(point, 4326) not null,
  accuracy_m numeric(7,2) not null,
  captured_at timestamptz not null,
  published_at timestamptz not null default now(),
  version bigint not null default 1,
  tracker_instance_id uuid not null,
  tracker_sequence bigint not null,
  constraint provider_discovery_latitude_check check (latitude between -90 and 90),
  constraint provider_discovery_longitude_check check (longitude between -180 and 180),
  constraint provider_discovery_accuracy_check check (accuracy_m > 0 and accuracy_m <= 100),
  constraint provider_discovery_version_check check (version > 0),
  constraint provider_discovery_sequence_check check (tracker_sequence > 0)
);
create index if not exists provider_discovery_locations_position_gist
  on public.provider_discovery_locations using gist(position);
create index if not exists provider_discovery_locations_published_idx
  on public.provider_discovery_locations(published_at);

create table if not exists public.service_searches (
  id uuid primary key,
  client_id uuid not null references public.profiles(id) on delete cascade,
  service_id uuid not null references public.services(id) on delete restrict,
  status text not null default 'SEARCHING',
  search_latitude numeric(9,6),
  search_longitude numeric(10,6),
  search_point extensions.geography(point, 4326),
  search_accuracy_m numeric(7,2),
  current_radius_m integer not null default 10000,
  current_candidate_id uuid,
  current_appointment_id uuid,
  revision bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  ended_at timestamptz,
  constraint service_search_status_check check (status in (
    'SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','ACCEPTED','IN_PROGRESS',
    'CANCELLED','COMPLETED','EXHAUSTED'
  )),
  constraint service_search_radius_check check (current_radius_m in (10000,25000,50000)),
  constraint service_search_coordinates_check check (
    (search_latitude is null and search_longitude is null and search_point is null)
    or (search_latitude between -90 and 90 and search_longitude between -180 and 180
      and search_point is not null)
  ),
  constraint service_search_accuracy_check check (
    search_accuracy_m is null or (search_accuracy_m > 0 and search_accuracy_m <= 100)
  ),
  constraint service_search_revision_check check (revision > 0)
);

create table if not exists private.dispatch_candidates (
  id uuid primary key default gen_random_uuid(),
  search_id uuid not null references public.service_searches(id) on delete cascade,
  provider_id uuid not null references public.providers(id) on delete restrict,
  provider_service_id uuid not null references public.provider_services(id) on delete restrict,
  sequence integer not null,
  status text not null default 'PRESENTED',
  provider_name text not null,
  locality text not null,
  distance_meters numeric(12,2) not null,
  quoted_price numeric(12,2) not null,
  is_free boolean not null default false,
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  constraint dispatch_candidate_status_check check (status in (
    'PRESENTED','CLIENT_SKIPPED','REQUESTED','PROVIDER_REJECTED','PROPOSAL_EXPIRED',
    'INVALIDATED','ACCEPTED','PROVIDER_CANCELLED'
  )),
  constraint dispatch_candidate_sequence_check check (sequence > 0),
  constraint dispatch_candidate_distance_check check (distance_meters >= 0),
  constraint dispatch_candidate_price_check check (quoted_price >= 0)
);
create unique index if not exists dispatch_candidates_search_provider_unique
  on private.dispatch_candidates(search_id, provider_id);
create unique index if not exists dispatch_candidates_search_sequence_unique
  on private.dispatch_candidates(search_id, sequence);

alter table public.appointments add column if not exists dispatch_search_id uuid;
alter table public.appointments add column if not exists dispatch_candidate_id uuid;
alter table public.appointments add column if not exists service_id uuid;
alter table public.appointments add column if not exists provider_service_id uuid;
alter table public.appointments add column if not exists quoted_price numeric(12,2);
alter table public.appointments add column if not exists is_free boolean not null default false;
alter table public.appointments add column if not exists response_deadline timestamptz;
alter table public.appointments add column if not exists revision bigint not null default 1;
alter table public.appointments add column if not exists cancelled_by text;
alter table public.appointments add column if not exists cancellation_reason text;
alter table public.appointments add column if not exists cancelled_at timestamptz;

do $$
begin
  if not exists (select 1 from pg_constraint where conname='appointments_dispatch_search_fkey') then
    alter table public.appointments add constraint appointments_dispatch_search_fkey
      foreign key(dispatch_search_id) references public.service_searches(id) on delete cascade;
  end if;
  if not exists (select 1 from pg_constraint where conname='appointments_dispatch_candidate_fkey') then
    alter table public.appointments add constraint appointments_dispatch_candidate_fkey
      foreign key(dispatch_candidate_id) references private.dispatch_candidates(id) on delete restrict;
  end if;
  if not exists (select 1 from pg_constraint where conname='appointments_service_fkey') then
    alter table public.appointments add constraint appointments_service_fkey
      foreign key(service_id) references public.services(id) on delete restrict;
  end if;
  if not exists (select 1 from pg_constraint where conname='appointments_provider_service_fkey') then
    alter table public.appointments add constraint appointments_provider_service_fkey
      foreign key(provider_service_id) references public.provider_services(id) on delete restrict;
  end if;
end
$$;

alter table public.service_searches drop constraint if exists service_searches_current_candidate_fkey;
alter table public.service_searches add constraint service_searches_current_candidate_fkey
  foreign key(current_candidate_id) references private.dispatch_candidates(id) on delete set null;
alter table public.service_searches drop constraint if exists service_searches_current_appointment_fkey;
alter table public.service_searches add constraint service_searches_current_appointment_fkey
  foreign key(current_appointment_id) references public.appointments(id) on delete set null;

create unique index if not exists service_searches_client_service_active_unique
  on public.service_searches(client_id, service_id)
  where status in ('SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','ACCEPTED','IN_PROGRESS');
create index if not exists service_searches_client_idx on public.service_searches(client_id, updated_at desc);
create unique index if not exists appointments_dispatch_candidate_unique
  on public.appointments(dispatch_candidate_id) where dispatch_candidate_id is not null;
drop index if exists public.one_active_appointment_per_provider;
create unique index one_active_appointment_per_provider
  on public.appointments(provider_id)
  where status in ('PENDING','ACCEPTED','IN_PROGRESS');

do $$
declare constraint_name text;
begin
  for constraint_name in
    select conname from pg_constraint
    where conrelid='public.appointments'::regclass and contype='c'
      and pg_get_constraintdef(oid) ilike '%status%'
  loop
    execute format('alter table public.appointments drop constraint %I', constraint_name);
  end loop;
end
$$;
alter table public.appointments add constraint appointments_status_check check (status in (
  'PENDING','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED','PROVIDER_REJECTED','EXPIRED'
));
alter table public.appointments add constraint appointments_dispatch_price_check
  check (quoted_price is null or quoted_price >= 0);
alter table public.appointments add constraint appointments_cancellation_check check (
  cancelled_by is null or cancelled_by in ('CLIENT','PROVIDER')
);

create or replace function private.dispatch_is_client()
returns boolean language sql stable security definer set search_path='' as $$
  select auth.uid() is not null and private.has_account_type('CLIENT');
$$;
create or replace function private.dispatch_is_provider()
returns boolean language sql stable security definer set search_path='' as $$
  select auth.uid() is not null and private.has_account_type('PROVIDER');
$$;

create or replace function private.dispatch_snapshot(p_search_id uuid)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare s public.service_searches%rowtype;
declare c private.dispatch_candidates%rowtype;
declare a public.appointments%rowtype;
declare service_name text;
declare provider_account uuid;
declare candidate_latitude numeric;
declare candidate_longitude numeric;
begin
  select * into s from public.service_searches where id=p_search_id;
  if s.id is null then return null; end if;
  select account_id into provider_account from public.providers
    where id=(select provider_id from public.appointments where id=s.current_appointment_id);
  if auth.uid() <> s.client_id and auth.uid() is distinct from provider_account then
    raise exception using errcode='42501', message='Dispatch participant required';
  end if;
  select name into service_name from public.services where id=s.service_id;
  if s.current_candidate_id is not null then
    select * into c from private.dispatch_candidates where id=s.current_candidate_id;
    select latitude,longitude into candidate_latitude,candidate_longitude
    from public.provider_discovery_locations where provider_id=c.provider_id;
  end if;
  if s.current_appointment_id is not null then
    select * into a from public.appointments where id=s.current_appointment_id;
  end if;
  return jsonb_strip_nulls(jsonb_build_object(
    'search_id',s.id,'service_id',s.service_id,'service_name',service_name,
    'status',s.status,'radius_meters',s.current_radius_m,'revision',s.revision,
    'server_now',now(),'allowed_actions',case
      when auth.uid()=s.client_id and s.status='OPTION_FOUND' then jsonb_build_array('NEXT','REQUEST','CANCEL')
      when auth.uid()=s.client_id and s.status in ('SEARCHING','AWAITING_PROVIDER','ACCEPTED','IN_PROGRESS') then jsonb_build_array('CANCEL')
      when auth.uid()=provider_account and a.status='PENDING' then jsonb_build_array('ACCEPT','DECLINE')
      when auth.uid()=provider_account and a.status in ('ACCEPTED','IN_PROGRESS') then jsonb_build_array('CANCEL','COMPLETE')
      else '[]'::jsonb end,
    'candidate',case when c.id is null then null else jsonb_build_object(
      'candidate_id',c.id,'provider_id',c.provider_id,'provider_name',c.provider_name,
      'locality',c.locality,'latitude',candidate_latitude,'longitude',candidate_longitude,
      'distance_meters',c.distance_meters,'price',c.quoted_price,'is_free',c.is_free
    ) end,
    'appointment',case when a.id is null then null else jsonb_build_object(
      'appointment_id',a.id,'status',a.status,'quoted_price',a.quoted_price,
      'is_free',a.is_free,'response_deadline',a.response_deadline,'revision',a.revision,
      'cancelled_by',a.cancelled_by
    ) end
  ));
end;
$$;

create or replace function private.advance_mobile_dispatch(p_search_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.service_searches%rowtype;
declare chosen record;
declare next_sequence integer;
declare candidate_id uuid;
declare radius integer;
begin
  select * into s from public.service_searches where id=p_search_id for update;
  if s.id is null then raise exception using errcode='P0002', message='Search unavailable'; end if;
  if s.search_point is null then raise exception using errcode='22023', message='Search location unavailable'; end if;
  radius := s.current_radius_m;
  loop
    select p.id provider_id, ps.id provider_service_id, p.name provider_name,
      concat_ws(' - ',nullif(btrim(ad.neighbornhood),''),nullif(btrim(ad.city),''),nullif(btrim(ad.state),'')) locality,
      extensions.st_distance(l.position,s.search_point) distance_meters, ps.price, ps.is_free
    into chosen
    from public.provider_services ps
    join public.providers p on p.id=ps.provider_id
    join public.account_roles ar on ar.user_id=p.account_id and ar.account_type='PROVIDER'
    join public.provider_discovery_locations l on l.provider_id=p.id
    left join public.addresses ad on ad.provider_id=p.id
    where ps.service_id=s.service_id and ps.active and p.active and p.available
      and p.location_mode='MOBILE' and l.published_at >= now()-interval '30 seconds'
      and extensions.st_dwithin(l.position,s.search_point,radius)
      and not exists(select 1 from private.dispatch_candidates dc where dc.search_id=s.id and dc.provider_id=p.id)
      and not exists(select 1 from public.appointments ap where ap.provider_id=p.id and ap.status in ('PENDING','ACCEPTED','IN_PROGRESS'))
    order by extensions.st_distance(l.position,s.search_point),p.id limit 1;
    exit when chosen.provider_id is not null or radius=50000;
    radius := case radius when 10000 then 25000 else 50000 end;
  end loop;

  if chosen.provider_id is null then
    update public.service_searches set status='EXHAUSTED',current_radius_m=50000,
      current_candidate_id=null,search_latitude=null,search_longitude=null,search_point=null,
      search_accuracy_m=null,revision=revision+1,updated_at=now(),ended_at=now() where id=s.id;
    return jsonb_build_object('outcome','EXHAUSTED');
  end if;
  select coalesce(max(sequence),0)+1 into next_sequence from private.dispatch_candidates where search_id=s.id;
  insert into private.dispatch_candidates(search_id,provider_id,provider_service_id,sequence,
    provider_name,locality,distance_meters,quoted_price,is_free)
  values(s.id,chosen.provider_id,chosen.provider_service_id,next_sequence,chosen.provider_name,
    coalesce(nullif(chosen.locality,''),'Localidade não informada'),chosen.distance_meters,
    chosen.price,chosen.is_free) returning id into candidate_id;
  update public.service_searches set status='OPTION_FOUND',current_radius_m=radius,
    current_candidate_id=candidate_id,revision=revision+1,updated_at=now() where id=s.id;
  return jsonb_build_object('outcome','ADVANCED');
end;
$$;

create or replace function public.publish_mobile_provider_location(
  p_latitude double precision,p_longitude double precision,p_accuracy_m double precision,
  p_captured_at timestamptz,p_tracker_instance_id uuid,p_tracker_sequence bigint
) returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.providers%rowtype; current_location public.provider_discovery_locations%rowtype;
declare appointment_id uuid; new_version bigint;
begin
  if not private.dispatch_is_provider() then raise exception using errcode='42501',message='Provider required'; end if;
  select * into p from public.providers where account_id=auth.uid() for update;
  if p.id is null or not p.active or not p.available or p.location_mode<>'MOBILE' then
    raise exception using errcode='22023',message='Provider ineligible';
  end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_m<=0 or p_accuracy_m>100 or p_captured_at<now()-interval '30 seconds'
    or p_captured_at>now()+interval '10 seconds' or p_tracker_sequence<=0 then
    raise exception using errcode='22023',message='Invalid location';
  end if;
  select * into current_location from public.provider_discovery_locations where provider_id=p.id for update;
  if current_location.provider_id is not null and current_location.tracker_instance_id<>p_tracker_instance_id
    and current_location.published_at>=now()-interval '30 seconds' then
    return jsonb_build_object('outcome','PUBLISHER_CONFLICT','version',current_location.version);
  end if;
  if current_location.provider_id is not null and current_location.tracker_instance_id=p_tracker_instance_id
    and (p_tracker_sequence<=current_location.tracker_sequence or p_captured_at<current_location.captured_at) then
    return jsonb_build_object('outcome','IGNORED','version',current_location.version);
  end if;
  new_version:=coalesce(current_location.version,0)+1;
  insert into public.provider_discovery_locations(provider_id,latitude,longitude,position,accuracy_m,
    captured_at,published_at,version,tracker_instance_id,tracker_sequence)
  values(p.id,p_latitude,p_longitude,extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography,
    p_accuracy_m,p_captured_at,now(),new_version,p_tracker_instance_id,p_tracker_sequence)
  on conflict(provider_id) do update set latitude=excluded.latitude,longitude=excluded.longitude,
    position=excluded.position,accuracy_m=excluded.accuracy_m,captured_at=excluded.captured_at,
    published_at=excluded.published_at,version=excluded.version,tracker_instance_id=excluded.tracker_instance_id,
    tracker_sequence=excluded.tracker_sequence;

  select id into appointment_id from public.appointments
    where provider_id=p.id and status in ('ACCEPTED','IN_PROGRESS') order by updated_at desc limit 1;
  if appointment_id is not null then
    insert into public.provider_current_locations(appointment_id,latitude,longitude,accuracy_m,captured_at,
      published_at,version,tracker_instance_id,tracker_sequence)
    values(appointment_id,p_latitude,p_longitude,p_accuracy_m,p_captured_at,now(),new_version,
      p_tracker_instance_id,p_tracker_sequence)
    on conflict(appointment_id) do update set latitude=excluded.latitude,longitude=excluded.longitude,
      accuracy_m=excluded.accuracy_m,captured_at=excluded.captured_at,published_at=excluded.published_at,
      version=public.provider_current_locations.version+1,tracker_instance_id=excluded.tracker_instance_id,
      tracker_sequence=excluded.tracker_sequence;
  end if;
  return jsonb_build_object('outcome','PUBLISHED','version',new_version,'target',
    case when appointment_id is null then 'DISCOVERY' else 'APPOINTMENT' end,'published_at',now());
end;
$$;

create or replace function public.stop_mobile_provider_location(p_tracker_instance_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare provider_id uuid; removed integer;
begin
  if not private.dispatch_is_provider() then raise exception using errcode='42501',message='Provider required'; end if;
  select id into provider_id from public.providers where account_id=auth.uid();
  delete from public.provider_discovery_locations
    where provider_id=provider_id and tracker_instance_id=p_tracker_instance_id;
  get diagnostics removed=row_count;
  return jsonb_build_object('outcome',case when removed=1 then 'STOPPED' else 'IGNORED' end);
end;
$$;

create or replace function private.clear_provider_discovery_location()
returns trigger language plpgsql security definer set search_path='' as $$
begin
  if not new.active or not new.available or new.location_mode<>'MOBILE' then
    delete from public.provider_discovery_locations where provider_id=new.id;
  end if;
  return new;
end;
$$;
drop trigger if exists provider_discovery_location_cleanup on public.providers;
create trigger provider_discovery_location_cleanup
after update of active,available,location_mode on public.providers for each row
execute function private.clear_provider_discovery_location();

create or replace function public.start_mobile_dispatch(
  p_search_id uuid,p_service_id uuid,p_latitude double precision,p_longitude double precision,
  p_accuracy_m double precision
) returns jsonb language plpgsql security definer set search_path='' as $$
declare existing_id uuid; kind text;
begin
  if not private.dispatch_is_client() then raise exception using errcode='42501',message='Client required'; end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_m<=0 or p_accuracy_m>100 then raise exception using errcode='22023',message='Invalid search point'; end if;
  select dispatch_kind into kind from public.services where id=p_service_id and active;
  if kind is distinct from 'MOBILE_SEQUENTIAL' then raise exception using errcode='22023',message='Service unavailable'; end if;
  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text,1301));
  select id into existing_id from public.service_searches where client_id=auth.uid() and service_id=p_service_id
    and status in ('SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','ACCEPTED','IN_PROGRESS') limit 1;
  if existing_id is not null then return private.dispatch_snapshot(existing_id); end if;
  insert into public.service_searches(id,client_id,service_id,search_latitude,search_longitude,
    search_point,search_accuracy_m)
  values(p_search_id,auth.uid(),p_service_id,p_latitude,p_longitude,
    extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography,p_accuracy_m);
  perform private.advance_mobile_dispatch(p_search_id);
  return private.dispatch_snapshot(p_search_id);
end;
$$;

create or replace function public.next_mobile_dispatch_candidate(p_search_id uuid,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.service_searches%rowtype;
begin
  if not private.dispatch_is_client() then raise exception using errcode='42501',message='Client required'; end if;
  select * into s from public.service_searches where id=p_search_id and client_id=auth.uid() for update;
  if s.id is null then raise exception using errcode='P0002',message='Search unavailable'; end if;
  if s.revision<>p_expected_revision then return jsonb_build_object('outcome','STALE_REVISION','snapshot',private.dispatch_snapshot(s.id)); end if;
  if s.status<>'OPTION_FOUND' then return private.dispatch_snapshot(s.id); end if;
  update private.dispatch_candidates set status='CLIENT_SKIPPED',decided_at=now() where id=s.current_candidate_id;
  update public.service_searches set status='SEARCHING',current_candidate_id=null,revision=revision+1,updated_at=now() where id=s.id;
  perform private.advance_mobile_dispatch(s.id);
  return private.dispatch_snapshot(s.id);
end;
$$;

create or replace function public.request_mobile_dispatch_candidate(p_search_id uuid,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.service_searches%rowtype; c private.dispatch_candidates%rowtype; appointment_id uuid;
begin
  if not private.dispatch_is_client() then raise exception using errcode='42501',message='Client required'; end if;
  select * into s from public.service_searches where id=p_search_id and client_id=auth.uid() for update;
  if s.id is null then raise exception using errcode='P0002',message='Search unavailable'; end if;
  if s.revision<>p_expected_revision then return jsonb_build_object('outcome','STALE_REVISION','snapshot',private.dispatch_snapshot(s.id)); end if;
  if s.current_appointment_id is not null then return private.dispatch_snapshot(s.id); end if;
  select * into c from private.dispatch_candidates where id=s.current_candidate_id for update;
  if c.id is null or s.status<>'OPTION_FOUND' or not exists(
    select 1 from public.provider_services ps join public.providers p on p.id=ps.provider_id
    join public.provider_discovery_locations l on l.provider_id=p.id
    where ps.id=c.provider_service_id and ps.active and p.active and p.available and p.location_mode='MOBILE'
      and l.published_at>=now()-interval '30 seconds'
      and not exists(select 1 from public.appointments a where a.provider_id=p.id and a.status in ('PENDING','ACCEPTED','IN_PROGRESS'))
  ) then
    if c.id is not null then update private.dispatch_candidates set status='INVALIDATED',decided_at=now() where id=c.id; end if;
    update public.service_searches set status='SEARCHING',current_candidate_id=null,revision=revision+1,updated_at=now() where id=s.id;
    perform private.advance_mobile_dispatch(s.id);
    return jsonb_build_object('outcome','CANDIDATE_INVALIDATED','snapshot',private.dispatch_snapshot(s.id));
  end if;
  insert into public.appointments(client_id,provider_id,status,dispatch_search_id,dispatch_candidate_id,
    service_id,provider_service_id,quoted_price,is_free,response_deadline,revision)
  values(auth.uid(),c.provider_id,'PENDING',s.id,c.id,s.service_id,c.provider_service_id,
    c.quoted_price,c.is_free,now()+interval '60 seconds',1) returning id into appointment_id;
  update private.dispatch_candidates set status='REQUESTED',decided_at=now() where id=c.id;
  update public.service_searches set status='AWAITING_PROVIDER',current_appointment_id=appointment_id,
    revision=revision+1,updated_at=now() where id=s.id;
  return private.dispatch_snapshot(s.id);
exception when unique_violation then
  perform private.advance_mobile_dispatch(s.id);
  return jsonb_build_object('outcome','CANDIDATE_INVALIDATED','snapshot',private.dispatch_snapshot(s.id));
end;
$$;

create or replace function private.expire_mobile_dispatch_appointment(p_appointment_id uuid)
returns void language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype;
begin
  select * into a from public.appointments where id=p_appointment_id and status='PENDING'
    and response_deadline<=now() for update;
  if a.id is null then return; end if;
  update public.appointments set status='EXPIRED',ended_at=now(),revision=revision+1,updated_at=now() where id=a.id;
  update private.dispatch_candidates set status='PROPOSAL_EXPIRED',decided_at=now() where id=a.dispatch_candidate_id;
  update public.service_searches set status='SEARCHING',current_appointment_id=null,current_candidate_id=null,
    revision=revision+1,updated_at=now() where id=a.dispatch_search_id;
  perform private.advance_mobile_dispatch(a.dispatch_search_id);
end;
$$;

create or replace function public.expire_due_mobile_dispatch_requests(p_limit integer default 100)
returns integer language plpgsql security definer set search_path='' as $$
declare row record; total integer:=0;
begin
  for row in select id from public.appointments where status='PENDING' and response_deadline<=now()
    order by response_deadline for update skip locked limit greatest(1,least(p_limit,500))
  loop perform private.expire_mobile_dispatch_appointment(row.id); total:=total+1; end loop;
  return total;
end;
$$;

create or replace function public.get_my_active_mobile_dispatch()
returns jsonb language plpgsql security definer set search_path='' as $$
declare search_id uuid;
begin
  if private.dispatch_is_client() then
    select id into search_id from public.service_searches where client_id=auth.uid()
      and status in ('SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','ACCEPTED','IN_PROGRESS')
      order by updated_at desc limit 1;
  elsif private.dispatch_is_provider() then
    select s.id into search_id from public.service_searches s join public.appointments a on a.dispatch_search_id=s.id
      join public.providers p on p.id=a.provider_id where p.account_id=auth.uid()
      and a.status in ('PENDING','ACCEPTED','IN_PROGRESS') order by a.updated_at desc limit 1;
  else raise exception using errcode='42501',message='Account required'; end if;
  if search_id is null then return jsonb_build_object('outcome','NONE','server_now',now()); end if;
  perform private.expire_mobile_dispatch_appointment((select current_appointment_id from public.service_searches where id=search_id));
  return jsonb_build_object('outcome','ACTIVE','snapshot',private.dispatch_snapshot(search_id));
end;
$$;

create or replace function public.get_my_pending_mobile_dispatch()
returns jsonb language sql security definer set search_path='' as $$ select public.get_my_active_mobile_dispatch(); $$;

create or replace function public.respond_to_mobile_dispatch(
  p_appointment_id uuid,p_accept boolean,p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; provider_id uuid;
begin
  if not private.dispatch_is_provider() then raise exception using errcode='42501',message='Provider required'; end if;
  select p.id into provider_id from public.providers p where p.account_id=auth.uid();
  select * into a from public.appointments where id=p_appointment_id and provider_id=provider_id for update;
  if a.id is null then raise exception using errcode='P0002',message='Request unavailable'; end if;
  if a.status='PENDING' and a.response_deadline<=now() then
    perform private.expire_mobile_dispatch_appointment(a.id);
    return jsonb_build_object('outcome','NONE');
  end if;
  if a.revision<>p_expected_revision then return jsonb_build_object('outcome','STALE_REVISION','snapshot',private.dispatch_snapshot(a.dispatch_search_id)); end if;
  if a.status<>'PENDING' then return private.dispatch_snapshot(a.dispatch_search_id); end if;
  if p_accept then
    update public.appointments set status='ACCEPTED',accepted_at=now(),revision=revision+1,updated_at=now() where id=a.id;
    update private.dispatch_candidates set status='ACCEPTED',decided_at=now() where id=a.dispatch_candidate_id;
    update public.service_searches set status='ACCEPTED',revision=revision+1,updated_at=now() where id=a.dispatch_search_id;
  else
    update public.appointments set status='PROVIDER_REJECTED',ended_at=now(),revision=revision+1,updated_at=now() where id=a.id;
    update private.dispatch_candidates set status='PROVIDER_REJECTED',decided_at=now() where id=a.dispatch_candidate_id;
    update public.service_searches set status='SEARCHING',current_appointment_id=null,current_candidate_id=null,
      revision=revision+1,updated_at=now() where id=a.dispatch_search_id;
    perform private.advance_mobile_dispatch(a.dispatch_search_id);
    return jsonb_build_object('outcome','NONE');
  end if;
  return private.dispatch_snapshot(a.dispatch_search_id);
end;
$$;

create or replace function public.start_mobile_appointment(p_appointment_id uuid,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; provider_id uuid;
begin
  if not private.dispatch_is_provider() then raise exception using errcode='42501',message='Provider required'; end if;
  select id into provider_id from public.providers where account_id=auth.uid();
  select * into a from public.appointments where id=p_appointment_id and provider_id=provider_id for update;
  if a.id is null then raise exception using errcode='P0002',message='Appointment unavailable'; end if;
  if a.revision=p_expected_revision and a.status='ACCEPTED' then
    update public.appointments set status='IN_PROGRESS',revision=revision+1,updated_at=now() where id=a.id;
    update public.service_searches set status='IN_PROGRESS',revision=revision+1,updated_at=now() where id=a.dispatch_search_id;
  end if;
  return private.dispatch_snapshot(a.dispatch_search_id);
end;
$$;

create or replace function public.cancel_mobile_appointment(
  p_appointment_id uuid,p_reason text,p_expected_revision bigint
) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; provider_id uuid; actor text;
begin
  if nullif(btrim(p_reason),'') is null then raise exception using errcode='22023',message='Cancellation reason required'; end if;
  select id into provider_id from public.providers where account_id=auth.uid();
  select * into a from public.appointments where id=p_appointment_id for update;
  if a.id is null or (a.client_id<>auth.uid() and a.provider_id is distinct from provider_id) then
    raise exception using errcode='42501',message='Participant required';
  end if;
  if a.revision<>p_expected_revision then return jsonb_build_object('outcome','STALE_REVISION','snapshot',private.dispatch_snapshot(a.dispatch_search_id)); end if;
  if a.status not in ('PENDING','ACCEPTED','IN_PROGRESS') then return private.dispatch_snapshot(a.dispatch_search_id); end if;
  actor:=case when a.client_id=auth.uid() then 'CLIENT' else 'PROVIDER' end;
  update public.appointments set status='CANCELLED',cancelled_by=actor,cancellation_reason=btrim(p_reason),
    cancelled_at=now(),ended_at=now(),revision=revision+1,updated_at=now() where id=a.id;
  if actor='PROVIDER' then
    update private.dispatch_candidates set status='PROVIDER_CANCELLED',decided_at=now() where id=a.dispatch_candidate_id;
    update public.service_searches set status='SEARCHING',current_appointment_id=null,current_candidate_id=null,
      revision=revision+1,updated_at=now() where id=a.dispatch_search_id;
    perform private.advance_mobile_dispatch(a.dispatch_search_id);
    return jsonb_build_object('outcome','NONE');
  else
    update public.service_searches set status='CANCELLED',search_latitude=null,search_longitude=null,
      search_point=null,search_accuracy_m=null,revision=revision+1,updated_at=now(),ended_at=now() where id=a.dispatch_search_id;
  end if;
  return private.dispatch_snapshot(a.dispatch_search_id);
end;
$$;

create or replace function public.complete_mobile_appointment(p_appointment_id uuid,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; provider_id uuid;
begin
  if not private.dispatch_is_provider() then raise exception using errcode='42501',message='Provider required'; end if;
  select id into provider_id from public.providers where account_id=auth.uid();
  select * into a from public.appointments where id=p_appointment_id and provider_id=provider_id for update;
  if a.id is null then raise exception using errcode='P0002',message='Appointment unavailable'; end if;
  if a.revision=p_expected_revision and a.status in ('ACCEPTED','IN_PROGRESS') then
    update public.appointments set status='COMPLETED',ended_at=now(),revision=revision+1,updated_at=now() where id=a.id;
    update public.service_searches set status='COMPLETED',search_latitude=null,search_longitude=null,
      search_point=null,search_accuracy_m=null,revision=revision+1,updated_at=now(),ended_at=now() where id=a.dispatch_search_id;
  end if;
  return private.dispatch_snapshot(a.dispatch_search_id);
end;
$$;

create or replace function private.broadcast_mobile_dispatch_change()
returns trigger language plpgsql security definer set search_path='' as $$
declare search_id uuid; provider_id uuid; revision bigint;
begin
  if tg_table_name='service_searches' then search_id:=new.id; revision:=new.revision;
  else search_id:=new.dispatch_search_id; provider_id:=new.provider_id; revision:=new.revision; end if;
  if search_id is not null then
    perform realtime.send(jsonb_build_object('search_id',search_id,'revision',revision),
      'CHANGED','mobile-dispatch:'||search_id::text,true);
  end if;
  if provider_id is not null then
    perform realtime.send(jsonb_build_object('appointment_id',new.id,'revision',revision),
      'CHANGED','provider-dispatch:'||provider_id::text,true);
  end if;
  return new;
end;
$$;
drop trigger if exists service_search_dispatch_broadcast on public.service_searches;
create trigger service_search_dispatch_broadcast after insert or update on public.service_searches
  for each row execute function private.broadcast_mobile_dispatch_change();
drop trigger if exists appointment_dispatch_broadcast on public.appointments;
create trigger appointment_dispatch_broadcast after insert or update on public.appointments
  for each row when (new.dispatch_search_id is not null)
  execute function private.broadcast_mobile_dispatch_change();

alter table public.provider_discovery_locations enable row level security;
alter table public.service_searches enable row level security;
alter table private.dispatch_candidates enable row level security;
revoke all on public.provider_discovery_locations,public.service_searches from public,anon,authenticated;
revoke all on private.dispatch_candidates from public,anon,authenticated;

drop policy if exists mobile_dispatch_receive on realtime.messages;
create policy mobile_dispatch_receive on realtime.messages for select to authenticated using (
  extension='broadcast' and (
    (split_part(realtime.topic(),':',1)='mobile-dispatch' and exists(
      select 1 from public.service_searches s left join public.appointments a on a.dispatch_search_id=s.id
      left join public.providers p on p.id=a.provider_id
      where s.id::text=split_part(realtime.topic(),':',2)
        and (s.client_id=auth.uid() or p.account_id=auth.uid())
    )) or
    (split_part(realtime.topic(),':',1)='provider-dispatch' and exists(
      select 1 from public.providers p where p.id::text=split_part(realtime.topic(),':',2)
        and p.account_id=auth.uid()
    ))
  )
);

-- Generic browsing deliberately exposes fixed business addresses only. Mobile positions are
-- available solely through the active dispatch snapshot for the involved client/provider.
drop function if exists public.list_home_services();
create or replace function public.list_home_services()
returns table(service_id uuid,name text,description text,category_name text,minimum_price numeric,dispatch_kind text)
language sql stable security definer set search_path='' as $$
  select service.id,service.name,service.description,category.name,min(offer.price),service.dispatch_kind
  from public.services service join public.service_categories category on category.id=service.category_id
  left join public.provider_services offer on offer.service_id=service.id and offer.active
  left join public.providers provider on provider.id=offer.provider_id and provider.active and provider.available
  where service.active and category.active and auth.uid() is not null and private.has_account_type('CLIENT')
  group by service.id,service.name,service.description,category.name,service.dispatch_kind order by service.name;
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
    and a.geocode_expires_at>now() and extensions.st_dwithin(a.address_point,
      extensions.st_setsrid(extensions.st_makepoint(p_center_long,p_center_lat),4326)::extensions.geography,p_radius_meters)
  group by p.id,p.name,p.location_mode,a.latitude,a.longitude,a.address_point order by 6,p.id limit p_limit;
end;
$$;

revoke all on function private.dispatch_is_client() from public,anon,authenticated;
revoke all on function private.dispatch_is_provider() from public,anon,authenticated;
revoke all on function private.dispatch_snapshot(uuid) from public,anon,authenticated;
revoke all on function private.advance_mobile_dispatch(uuid) from public,anon,authenticated;
revoke all on function private.expire_mobile_dispatch_appointment(uuid) from public,anon,authenticated;
revoke all on function private.broadcast_mobile_dispatch_change() from public,anon,authenticated;
revoke all on function private.clear_provider_discovery_location() from public,anon,authenticated;

do $$ declare signature regprocedure; begin
  for signature in select p.oid::regprocedure from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname in (
      'publish_mobile_provider_location','stop_mobile_provider_location','start_mobile_dispatch',
      'next_mobile_dispatch_candidate','request_mobile_dispatch_candidate','get_my_active_mobile_dispatch',
      'get_my_pending_mobile_dispatch','respond_to_mobile_dispatch','start_mobile_appointment',
      'cancel_mobile_appointment','complete_mobile_appointment'
    )
  loop execute format('revoke all on function %s from public, anon, authenticated',signature);
       execute format('grant execute on function %s to authenticated',signature); end loop;
end $$;
revoke all on function public.expire_due_mobile_dispatch_requests(integer) from public,anon,authenticated;
revoke all on function public.list_home_services() from public,anon;
grant execute on function public.list_home_services() to authenticated;
revoke all on function public.discover_service_providers(uuid,double precision,double precision,integer,integer) from public,anon;
grant execute on function public.discover_service_providers(uuid,double precision,double precision,integer,integer) to authenticated;

do $$
begin
  perform cron.unschedule(jobid) from cron.job where jobname='pitgo-expire-mobile-dispatch';
  perform cron.schedule('pitgo-expire-mobile-dispatch','1 second',
    'select public.expire_due_mobile_dispatch_requests(100);');
exception when others then
  raise notice 'pg_cron scheduling must be enabled by the deployment operator';
end
$$;

notify pgrst,'reload schema';
commit;
