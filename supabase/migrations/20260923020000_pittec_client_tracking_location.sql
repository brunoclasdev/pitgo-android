begin;

-- Preserve the client's destination when the payment approval changes the
-- appointment to ACCEPTED. This also repairs active appointments created by
-- the previous approval function that did not seed the location.
create or replace function private.seed_pittec_client_location_on_payment()
returns trigger language plpgsql security definer set search_path = '' as $$
declare appointment_row public.appointments%rowtype; search_row public.service_searches%rowtype;
begin
  if new.dispatch_kind <> 'PITTEC_AUTO_SEQUENTIAL' or new.status <> 'APPROVED' then return new; end if;
  select * into appointment_row from public.appointments where id = new.appointment_id;
  select * into search_row from public.service_searches where id = new.request_id;
  if appointment_row.id is not null and search_row.id is not null
    and appointment_row.status in ('ACCEPTED','IN_PROGRESS')
    and search_row.search_latitude is not null and search_row.search_longitude is not null then
    insert into public.client_current_locations(
      appointment_id, latitude, longitude, accuracy_m, captured_at, published_at
    ) values (
      appointment_row.id, search_row.search_latitude, search_row.search_longitude,
      coalesce(search_row.search_accuracy_m, 50),
      coalesce(search_row.request_captured_at, now()), now()
    ) on conflict (appointment_id) do nothing;
  end if;
  return new;
end; $$;

drop trigger if exists seed_pittec_client_location on public.payments;
create trigger seed_pittec_client_location
after insert or update of status on public.payments
for each row when (new.status = 'APPROVED')
execute function private.seed_pittec_client_location_on_payment();
revoke all on function private.seed_pittec_client_location_on_payment() from public, anon, authenticated;

-- A participant may publish a fresh client position while the appointment is
-- active. Authorization still requires the client who owns the appointment.
create or replace function public.publish_client_location(
  p_appointment_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_m double precision,
  p_captured_at timestamptz
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare appointment_row public.appointments%rowtype;
begin
  if not private.dispatch_is_client() then
    raise exception using errcode = '42501', message = 'Client required';
  end if;
  if p_latitude not between -90 and 90 or p_longitude not between -180 and 180
    or p_accuracy_m <= 0 or p_accuracy_m > 5000
    or p_captured_at < now() - interval '30 seconds'
    or p_captured_at > now() + interval '10 seconds' then
    raise exception using errcode = '22023', message = 'Invalid location';
  end if;
  select * into appointment_row
  from public.appointments
  where id = p_appointment_id
    and client_id = auth.uid()
    and status in ('ACCEPTED', 'IN_PROGRESS')
  for update;
  if appointment_row.id is null then
    raise exception using errcode = 'P0002', message = 'Appointment unavailable';
  end if;
  insert into public.client_current_locations(
    appointment_id, latitude, longitude, accuracy_m, captured_at, published_at
  ) values (
    appointment_row.id, p_latitude, p_longitude, p_accuracy_m, p_captured_at, now()
  ) on conflict (appointment_id) do update set
    latitude = excluded.latitude,
    longitude = excluded.longitude,
    accuracy_m = excluded.accuracy_m,
    captured_at = excluded.captured_at,
    published_at = excluded.published_at;
  return jsonb_build_object('outcome', 'PUBLISHED', 'published_at', now());
end; $$;
revoke all on function public.publish_client_location(uuid, double precision, double precision, double precision, timestamptz) from public, anon;
grant execute on function public.publish_client_location(uuid, double precision, double precision, double precision, timestamptz) to authenticated;

-- Repair currently active PitTec appointments that were approved before the
-- location seed trigger existed.
insert into public.client_current_locations(
  appointment_id, latitude, longitude, accuracy_m, captured_at, published_at
)
select a.id, s.search_latitude, s.search_longitude,
       coalesce(s.search_accuracy_m, 50), coalesce(s.request_captured_at, now()), now()
from public.appointments a
join public.service_searches s on s.id = a.dispatch_search_id
join public.services service on service.id = a.service_id
left join public.client_current_locations current_location on current_location.appointment_id = a.id
where a.status in ('ACCEPTED', 'IN_PROGRESS')
  and service.dispatch_kind = 'PITTEC_AUTO_SEQUENTIAL'
  and current_location.appointment_id is null
  and s.search_latitude is not null
  and s.search_longitude is not null;

notify pgrst, 'reload schema';
commit;
