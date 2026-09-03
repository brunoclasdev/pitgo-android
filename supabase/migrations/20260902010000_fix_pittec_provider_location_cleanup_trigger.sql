-- A trigger record only exposes columns from the table that fired it. Keep each
-- table's NEW-field access in a separate branch so provider updates never try
-- to resolve appointments.status.
begin;

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
    end if;
  elsif tg_table_name = 'providers' then
    if not new.active
      or not new.available
      or new.location_mode not in ('MOBILE', 'PITTEC') then
      delete from public.provider_current_locations as location
      using public.appointments as appointment
      where location.appointment_id = appointment.id
        and appointment.provider_id = new.id;
    end if;
  end if;

  return new;
end;
$$;

commit;
