create table if not exists public.appointments (
  id uuid primary key default gen_random_uuid(),
  client_id uuid not null references public.profiles(id) on delete restrict,
  provider_id uuid not null references public.providers(id) on delete restrict,
  status text not null default 'PENDING',
  accepted_at timestamptz,
  ended_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint appointments_status_valid
    check (status in ('PENDING', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
  constraint appointments_end_consistent check (
    (status in ('COMPLETED', 'CANCELLED') and ended_at is not null)
    or (status not in ('COMPLETED', 'CANCELLED') and ended_at is null)
  )
);

create unique index if not exists appointments_one_active_provider
  on public.appointments(provider_id)
  where status in ('ACCEPTED', 'IN_PROGRESS');
create index if not exists appointments_client_idx on public.appointments(client_id);

create table if not exists public.provider_current_locations (
  appointment_id uuid primary key references public.appointments(id) on delete cascade,
  latitude numeric(9,6) not null check (latitude between -90 and 90),
  longitude numeric(10,6) not null check (longitude between -180 and 180),
  accuracy_m numeric(7,2) not null check (accuracy_m > 0 and accuracy_m <= 100),
  captured_at timestamptz not null,
  published_at timestamptz not null default now(),
  version bigint not null check (version > 0),
  tracker_instance_id uuid not null,
  tracker_sequence bigint not null check (tracker_sequence > 0)
);

alter table public.appointments enable row level security;
alter table public.provider_current_locations enable row level security;
revoke all on table public.appointments from public, anon, authenticated;
revoke all on table public.provider_current_locations from public, anon, authenticated;
grant select on table public.appointments to authenticated;

create policy appointments_select_participant on public.appointments
for select to authenticated using (
  client_id = (select auth.uid()) or exists (
    select 1 from public.providers provider
    where provider.id = appointments.provider_id and provider.account_id = (select auth.uid())
  )
);

create or replace function public.publish_provider_location(
  p_appointment_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_m double precision,
  p_captured_at timestamptz,
  p_tracker_instance_id uuid,
  p_tracker_sequence bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_provider public.providers%rowtype;
  appointment public.appointments%rowtype;
  current_location public.provider_current_locations%rowtype;
  next_version bigint;
begin
  if auth.uid() is null or not private.has_account_type('PROVIDER') then
    raise exception using errcode = '42501', message = 'Provider session required';
  end if;
  select * into current_provider from public.providers
  where account_id = auth.uid() for update;
  if current_provider.id is null or not current_provider.active or not current_provider.available
    or current_provider.location_mode <> 'MOBILE' then
    raise exception using errcode = '42501', message = 'Provider is not eligible';
  end if;
  select * into appointment from public.appointments
  where id = p_appointment_id and provider_id = current_provider.id for update;
  if appointment.id is null or appointment.status not in ('ACCEPTED', 'IN_PROGRESS') then
    raise exception using errcode = '42501', message = 'Active appointment required';
  end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_m <= 0 or p_accuracy_m > 100
    or p_captured_at < now() - interval '30 seconds'
    or p_captured_at > now() + interval '10 seconds'
    or p_tracker_sequence <= 0 then
    raise exception using errcode = '22023', message = 'Invalid location update';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(p_appointment_id::text, 9020)
  );
  select * into current_location from public.provider_current_locations
  where appointment_id = p_appointment_id for update;
  if current_location.appointment_id is not null
    and current_location.tracker_instance_id <> p_tracker_instance_id
    and current_location.published_at >= now() - interval '30 seconds' then
    return jsonb_build_object('outcome', 'PUBLISHER_CONFLICT');
  end if;
  if current_location.tracker_instance_id = p_tracker_instance_id
    and (p_tracker_sequence <= current_location.tracker_sequence
      or p_captured_at < current_location.captured_at) then
    return jsonb_build_object('outcome', 'IGNORED', 'version', current_location.version);
  end if;
  next_version := coalesce(current_location.version, 0) + 1;
  insert into public.provider_current_locations(
    appointment_id, latitude, longitude, accuracy_m, captured_at, published_at,
    version, tracker_instance_id, tracker_sequence
  ) values (
    p_appointment_id, p_latitude, p_longitude, p_accuracy_m, p_captured_at, now(),
    next_version, p_tracker_instance_id, p_tracker_sequence
  ) on conflict (appointment_id) do update set
    latitude = excluded.latitude, longitude = excluded.longitude,
    accuracy_m = excluded.accuracy_m, captured_at = excluded.captured_at,
    published_at = excluded.published_at, version = excluded.version,
    tracker_instance_id = excluded.tracker_instance_id,
    tracker_sequence = excluded.tracker_sequence;
  return jsonb_build_object('outcome', 'PUBLISHED', 'version', next_version);
end;
$$;

create or replace function public.stop_provider_location(
  p_appointment_id uuid,
  p_tracker_instance_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.provider_current_locations location
  using public.appointments appointment, public.providers provider
  where location.appointment_id = p_appointment_id
    and location.tracker_instance_id = p_tracker_instance_id
    and appointment.id = location.appointment_id
    and provider.id = appointment.provider_id
    and provider.account_id = auth.uid();
end;
$$;

create or replace function public.get_appointment_provider_location(p_appointment_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare result public.provider_current_locations%rowtype;
begin
  if not exists (
    select 1 from public.appointments appointment
    left join public.providers provider on provider.id = appointment.provider_id
    where appointment.id = p_appointment_id
      and appointment.status in ('ACCEPTED', 'IN_PROGRESS')
      and (appointment.client_id = auth.uid() or provider.account_id = auth.uid())
  ) then raise exception using errcode = '42501', message = 'Participant session required'; end if;
  select * into result from public.provider_current_locations
  where appointment_id = p_appointment_id;
  if result.appointment_id is null then return jsonb_build_object('state', 'EMPTY'); end if;
  return jsonb_build_object(
    'state', case when result.published_at >= now() - interval '30 seconds' then 'LIVE' else 'STALE' end,
    'latitude', result.latitude, 'longitude', result.longitude,
    'accuracy_m', result.accuracy_m, 'captured_at', result.captured_at,
    'published_at', result.published_at, 'version', result.version
  );
end;
$$;

create or replace function private.clear_ineligible_provider_location()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_table_name = 'appointments' and new.status not in ('ACCEPTED', 'IN_PROGRESS') then
    delete from public.provider_current_locations where appointment_id = new.id;
  elsif tg_table_name = 'providers'
    and (not new.active or not new.available or new.location_mode <> 'MOBILE') then
    delete from public.provider_current_locations location using public.appointments appointment
    where location.appointment_id = appointment.id and appointment.provider_id = new.id;
  end if;
  return new;
end;
$$;

drop trigger if exists clear_location_on_appointment_change on public.appointments;
create trigger clear_location_on_appointment_change after update on public.appointments
for each row execute function private.clear_ineligible_provider_location();
drop trigger if exists clear_location_on_provider_change on public.providers;
create trigger clear_location_on_provider_change after update on public.providers
for each row execute function private.clear_ineligible_provider_location();

create or replace function private.broadcast_provider_location()
returns trigger language plpgsql security definer set search_path = '' as $$
declare appointment_id uuid := coalesce(new.appointment_id, old.appointment_id);
begin
  perform realtime.broadcast_changes(
    'appointment-location:' || appointment_id::text,
    tg_op,
    tg_op,
    tg_table_name,
    tg_table_schema,
    new,
    old
  );
  return coalesce(new, old);
end;
$$;

drop trigger if exists broadcast_provider_current_location on public.provider_current_locations;
create trigger broadcast_provider_current_location
after insert or update or delete on public.provider_current_locations
for each row execute function private.broadcast_provider_location();

-- realtime.messages is owned by supabase_realtime_admin on hosted Supabase; RLS is already
-- enabled on it by the platform, and this role cannot ALTER it (only CREATE POLICY on it, which
-- Supabase does allow for the broadcast-authorization pattern below).
create policy appointment_location_receive on realtime.messages
for select to authenticated using (
  extension = 'broadcast' and split_part(realtime.topic(), ':', 1) = 'appointment-location'
  and exists (
    select 1 from public.appointments appointment
    left join public.providers provider on provider.id = appointment.provider_id
    where appointment.id::text = split_part(realtime.topic(), ':', 2)
      and appointment.status in ('ACCEPTED', 'IN_PROGRESS')
      and (appointment.client_id = auth.uid() or provider.account_id = auth.uid())
  )
);

revoke all on function public.publish_provider_location(uuid,double precision,double precision,double precision,timestamptz,uuid,bigint) from public, anon;
revoke all on function public.stop_provider_location(uuid,uuid) from public, anon;
revoke all on function public.get_appointment_provider_location(uuid) from public, anon;
grant execute on function public.publish_provider_location(uuid,double precision,double precision,double precision,timestamptz,uuid,bigint) to authenticated;
grant execute on function public.stop_provider_location(uuid,uuid) to authenticated;
grant execute on function public.get_appointment_provider_location(uuid) to authenticated;
