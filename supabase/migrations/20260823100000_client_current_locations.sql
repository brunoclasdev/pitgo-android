-- Client's one-shot GPS fix during an active appointment, mirroring provider_current_locations
-- (feature 013) so the provider's new map (spec 014, US3) can see where the client is. Unlike the
-- provider's continuous tracking, this is a single reading per screen-open/reopen on the client's
-- device (no background service, no tracker lease/sequence to resolve against concurrent
-- publishers) — see specs/014-client-ux-improvements/data-model.md #3 and
-- contracts/client-location-rpcs.md.
begin;

create table if not exists public.client_current_locations (
  appointment_id uuid primary key references public.appointments(id) on delete cascade,
  latitude numeric(9,6) not null check (latitude between -90 and 90),
  longitude numeric(10,6) not null check (longitude between -180 and 180),
  accuracy_m numeric(7,2) not null check (accuracy_m > 0 and accuracy_m <= 100),
  captured_at timestamptz not null,
  published_at timestamptz not null default now()
);

alter table public.client_current_locations enable row level security;
revoke all on table public.client_current_locations from public, anon, authenticated;

-- Renamed from private.broadcast_provider_location(): the function body was already fully
-- generic (driven by tg_table_name/tg_table_schema/new/old), so it is reused as-is for
-- client_current_locations instead of duplicating a second broadcast trigger function. Both
-- tables' rows carry appointment_id, which is all this function relies on.
create or replace function private.broadcast_appointment_location_change()
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
for each row execute function private.broadcast_appointment_location_change();

drop trigger if exists broadcast_client_current_location on public.client_current_locations;
create trigger broadcast_client_current_location
after insert or update or delete on public.client_current_locations
for each row execute function private.broadcast_appointment_location_change();

revoke all on function private.broadcast_appointment_location_change() from public, anon, authenticated;
drop function if exists private.broadcast_provider_location();

create or replace function public.publish_client_location(
  p_appointment_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_m double precision,
  p_captured_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  appointment_row public.appointments%rowtype;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'Client required';
  end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_m <= 0 or p_accuracy_m > 100 or p_captured_at < now() - interval '30 seconds'
    or p_captured_at > now() + interval '10 seconds' then
    raise exception using errcode = '22023', message = 'Invalid location';
  end if;

  select appointment.* into appointment_row
  from public.appointments as appointment
  where appointment.id = p_appointment_id
    and appointment.client_id = auth.uid()
    and appointment.status in ('ACCEPTED', 'IN_PROGRESS')
  for update;

  if appointment_row.id is null then
    raise exception using errcode = 'P0002', message = 'Appointment unavailable';
  end if;

  -- The broadcast_client_current_location trigger fires from this upsert; no manual
  -- realtime.send() call is needed here.
  insert into public.client_current_locations(
    appointment_id, latitude, longitude, accuracy_m, captured_at, published_at
  ) values (
    appointment_row.id, p_latitude, p_longitude, p_accuracy_m, p_captured_at, now()
  )
  on conflict (appointment_id) do update set
    latitude = excluded.latitude,
    longitude = excluded.longitude,
    accuracy_m = excluded.accuracy_m,
    captured_at = excluded.captured_at,
    published_at = excluded.published_at;

  return jsonb_build_object('outcome', 'PUBLISHED', 'published_at', now());
end;
$$;

create or replace function public.get_appointment_client_location(p_appointment_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  is_participant boolean;
  location_row public.client_current_locations%rowtype;
begin
  select exists (
    select 1 from public.appointments as appointment
    left join public.providers as provider on provider.id = appointment.provider_id
    where appointment.id = p_appointment_id
      and (appointment.client_id = auth.uid() or provider.account_id = auth.uid())
  ) into is_participant;
  if not is_participant then
    raise exception using errcode = '42501', message = 'Participant required';
  end if;

  select * into location_row
  from public.client_current_locations
  where appointment_id = p_appointment_id;

  if location_row.appointment_id is null then
    return jsonb_build_object('outcome', 'NOT_FOUND');
  end if;

  return jsonb_build_object(
    'outcome', 'FOUND',
    'location', jsonb_build_object(
      'latitude', location_row.latitude,
      'longitude', location_row.longitude,
      'accuracy_m', location_row.accuracy_m,
      'captured_at', location_row.captured_at,
      'published_at', location_row.published_at
    )
  );
end;
$$;

-- Mirrors provider_current_locations cleanup: once the appointment leaves ACCEPTED/IN_PROGRESS,
-- the client's last fix is no longer meaningful and must not linger.
create or replace function private.clear_client_current_location()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.status not in ('ACCEPTED', 'IN_PROGRESS') then
    delete from public.client_current_locations where appointment_id = new.id;
  end if;
  return new;
end;
$$;

drop trigger if exists client_current_location_cleanup on public.appointments;
create trigger client_current_location_cleanup
after update of status on public.appointments
for each row execute function private.clear_client_current_location();

revoke all on function private.clear_client_current_location() from public, anon, authenticated;

revoke all on function public.publish_client_location(
  uuid, double precision, double precision, double precision, timestamptz
) from public, anon;
revoke all on function public.get_appointment_client_location(uuid) from public, anon;
grant execute on function public.publish_client_location(
  uuid, double precision, double precision, double precision, timestamptz
) to authenticated;
grant execute on function public.get_appointment_client_location(uuid) to authenticated;

-- The existing appointment_location_receive policy on realtime.messages already authorizes both
-- participants for the whole 'appointment-location' topic family regardless of which underlying
-- table changed, so no new realtime.messages policy is needed for client_current_locations.

notify pgrst, 'reload schema';

commit;
