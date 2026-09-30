-- Preserve the snapshot while completion timestamps are still unset.
begin;
create or replace function private.pittec_dispatch_snapshot(p_search_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  snapshot jsonb;
  search_row public.service_searches%rowtype;
  appointment_row public.appointments%rowtype;
  caller_is_client boolean;
  caller_is_provider boolean;
begin
  snapshot := private.pittec_dispatch_snapshot_base(p_search_id);
  select search.* into search_row from public.service_searches as search where search.id = p_search_id;
  select appointment.* into appointment_row from public.appointments as appointment where appointment.id = search_row.current_appointment_id;
  caller_is_client := auth.uid() = search_row.client_id;
  select exists(
    select 1 from public.providers as provider where provider.id = appointment_row.provider_id and provider.account_id = auth.uid()
  ) into caller_is_provider;
  snapshot := jsonb_set(snapshot, '{tracking_authorized}', to_jsonb(search_row.status in ('ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION')), true);
  if appointment_row.id is not null then
    snapshot := jsonb_set(snapshot, '{appointment,completion_requested_at}', coalesce(to_jsonb(appointment_row.completion_requested_at), 'null'::jsonb), true);
    snapshot := jsonb_set(snapshot, '{appointment,client_completion_confirmed_at}', coalesce(to_jsonb(appointment_row.client_completion_confirmed_at), 'null'::jsonb), true);
  end if;
  if search_row.status = 'AWAITING_CLIENT_COMPLETION_CONFIRMATION' then
    snapshot := jsonb_set(snapshot, '{allowed_actions}', case
      when caller_is_client then jsonb_build_array('CONFIRM_COMPLETION')
      when caller_is_provider then jsonb_build_array('OPEN_TRACKING')
      else '[]'::jsonb end, true);
  end if;
  return snapshot;
end;
$$;
commit;
