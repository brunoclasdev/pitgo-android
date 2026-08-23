-- A trigger record only exposes columns from its own table. Keep table-specific
-- field access inside separate branches so provider updates never resolve status.
create or replace function private.clear_ineligible_provider_location()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_table_name = 'appointments' then
    if new.status not in ('ACCEPTED', 'IN_PROGRESS') then
      delete from public.provider_current_locations
      where appointment_id = new.id;

      if new.status in ('COMPLETED', 'CANCELLED')
        and old.status is distinct from new.status then
        perform realtime.send(
          pg_catalog.jsonb_build_object('status', new.status),
          'ENDED',
          'appointment-location:' || new.id::text,
          true
        );
      end if;
    end if;
  elsif tg_table_name = 'providers' then
    if not new.active or not new.available or new.location_mode <> 'MOBILE' then
      delete from public.provider_current_locations location
      using public.appointments appointment
      where location.appointment_id = appointment.id
        and appointment.provider_id = new.id;
    end if;
  end if;

  return new;
end;
$$;
