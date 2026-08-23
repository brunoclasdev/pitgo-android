-- Make the private appointment-location stream deterministic across empty snapshots,
-- reconnects and terminal transitions. No location history is introduced.

create or replace function public.get_appointment_provider_location(p_appointment_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  appointment public.appointments%rowtype;
  provider_account_id uuid;
  result public.provider_current_locations%rowtype;
begin
  select * into appointment
  from public.appointments current_appointment
  where current_appointment.id = p_appointment_id;

  if appointment.id is not null then
    select provider.account_id into provider_account_id
    from public.providers provider
    where provider.id = appointment.provider_id;
  end if;

  if appointment.id is null
    or (appointment.client_id <> auth.uid() and provider_account_id <> auth.uid()) then
    raise exception using errcode = '42501', message = 'Participant session required';
  end if;

  if appointment.status in ('COMPLETED', 'CANCELLED') then
    return pg_catalog.jsonb_build_object('state', 'ENDED', 'status', appointment.status);
  end if;

  if appointment.status not in ('ACCEPTED', 'IN_PROGRESS') then
    return pg_catalog.jsonb_build_object('state', 'EMPTY');
  end if;

  select * into result
  from public.provider_current_locations
  where appointment_id = p_appointment_id;

  if result.appointment_id is null then
    return pg_catalog.jsonb_build_object('state', 'EMPTY');
  end if;

  return pg_catalog.jsonb_build_object(
    'state', case
      when result.published_at >= pg_catalog.now() - interval '30 seconds' then 'LIVE'
      else 'STALE'
    end,
    'latitude', result.latitude,
    'longitude', result.longitude,
    'accuracy_m', result.accuracy_m,
    'captured_at', result.captured_at,
    'published_at', result.published_at,
    'version', result.version
  );
end;
$$;

create or replace function private.clear_ineligible_provider_location()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_table_name = 'appointments' and new.status not in ('ACCEPTED', 'IN_PROGRESS') then
    delete from public.provider_current_locations where appointment_id = new.id;

    if new.status in ('COMPLETED', 'CANCELLED')
      and old.status is distinct from new.status then
      perform realtime.send(
        pg_catalog.jsonb_build_object('status', new.status),
        'ENDED',
        'appointment-location:' || new.id::text,
        true
      );
    end if;
  elsif tg_table_name = 'providers'
    and (not new.active or not new.available or new.location_mode <> 'MOBILE') then
    delete from public.provider_current_locations location
    using public.appointments appointment
    where location.appointment_id = appointment.id
      and appointment.provider_id = new.id;
  end if;
  return new;
end;
$$;

drop policy if exists appointment_location_receive on realtime.messages;
create policy appointment_location_receive on realtime.messages
for select to authenticated using (
  extension = 'broadcast'
  and pg_catalog.split_part(realtime.topic(), ':', 1) = 'appointment-location'
  and exists (
    select 1
    from public.appointments appointment
    left join public.providers provider on provider.id = appointment.provider_id
    where appointment.id::text = pg_catalog.split_part(realtime.topic(), ':', 2)
      and (appointment.client_id = auth.uid() or provider.account_id = auth.uid())
  )
);

revoke all on function public.get_appointment_provider_location(uuid) from public, anon;
grant execute on function public.get_appointment_provider_location(uuid) to authenticated;
