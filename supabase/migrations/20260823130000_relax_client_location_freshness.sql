-- Forward fix for 20260823110000 (kept immutable): that migration widened the accuracy
-- tolerance to match DeviceLocationRepository.currentClientFix()'s cache tolerance
-- (MAX_CLIENT_CACHE_AGE_MILLIS = 5 minutes) but left the temporal freshness check at 30
-- seconds. Since currentClientFix() deliberately prefers a cached fix up to 5 minutes old
-- before requesting a new one (that's the whole point of the cache), most real calls were
-- landing outside the 30s window and getting rejected with "Invalid location" (22023) on
-- their first attempt — silently, until AppointmentTrackingRepository.publishClientLocation
-- stopped swallowing the error (see AppointmentTrackingViewModel.publishOwnLocation retry).
-- Widen the temporal check to the same 5-minute tolerance so the backend accepts whatever
-- the client-side capability already decided was good enough.
begin;

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
    or p_accuracy_m <= 0 or p_accuracy_m > 5000 or p_captured_at < now() - interval '5 minutes'
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

notify pgrst, 'reload schema';

commit;
