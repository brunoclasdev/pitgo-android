begin;
create function private.inspection_alert_environment(p_id uuid) returns text language plpgsql stable security definer set search_path='' as $$
declare mode text; result text;
begin
 select decrypted_secret into mode from vault.decrypted_secrets where name='INSPECTION_PUSH_ENVIRONMENT' limit 1;
 if mode='dev' then return 'dev'; end if;
 select p.checkout_environment into result from public.payments p join public.appointments a on a.id=p.appointment_id
 where a.id=p_id and p.request_id=a.dispatch_search_id and p.quote_version=a.quote_version order by p.created_at desc limit 1;
 return coalesce(result,'prod');
end $$;
create function private.invalidate_inspection_alerts(p_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare r record;
begin
 for r in update private.guincho_inspection_review_alerts set invalidated_at=now(),revision=revision+1
 where appointment_id=p_id and invalidated_at is null and not private.inspection_alert_pending(id) returning id,revision loop
 update private.guincho_inspection_push_deliveries set state='OBSOLETE',lease_token=null,lease_until=null where alert_id=r.id and revision<r.revision and state<>'ACCEPTED';
 perform private.inspection_alert_fanout(r.id);
 end loop;
end $$;
create function private.inspection_review_event() returns trigger language plpgsql security definer set search_path='' as $$
declare rid uuid;
begin
 if tg_table_name='guincho_inspection_versions' then
  if new.state='SUBMITTED' and old.state is distinct from new.state then
   insert into private.guincho_inspection_review_alerts(client_id,appointment_id,version_id,environment_id)
   select a.client_id,a.id,new.id,private.inspection_alert_environment(a.id) from public.appointments a where a.id=new.appointment_id
   on conflict do nothing returning id into rid;
   if rid is not null then perform private.inspection_alert_fanout(rid); end if;
  end if;
  perform private.invalidate_inspection_alerts(new.appointment_id);
 else perform private.invalidate_inspection_alerts(new.id);
 end if;
 return new;
end $$;
create trigger inspection_review_version_event after update on public.guincho_inspection_versions for each row execute function private.inspection_review_event();
create trigger inspection_review_appointment_event after update of status,service_started_at on public.appointments for each row execute function private.inspection_review_event();
insert into private.guincho_inspection_review_alerts(client_id,appointment_id,version_id,environment_id,created_at)
select a.client_id,a.id,v.id,private.inspection_alert_environment(a.id),v.submitted_at from public.guincho_inspection_versions v join public.appointments a on a.id=v.appointment_id
where v.state='SUBMITTED' and private.inspection_writable(a.id) and not exists(select 1 from public.guincho_inspection_versions n where n.appointment_id=a.id and n.version_number>v.version_number)
on conflict do nothing;
select private.inspection_alert_fanout(id) from private.guincho_inspection_review_alerts where private.inspection_alert_pending(id);
revoke all on function private.inspection_alert_environment(uuid),private.invalidate_inspection_alerts(uuid),private.inspection_review_event() from public,anon,authenticated;
commit;
