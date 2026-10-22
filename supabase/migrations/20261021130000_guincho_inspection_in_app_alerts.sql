begin;
-- Inspection approval uses an authenticated in-app notice. Retire the unused push path
-- while retaining existing inspection alerts and historical delivery records.
do $$
declare job record;
begin
 for job in select jobid from cron.job where jobname='pitgo-inspection-push' loop
  perform cron.unschedule(job.jobid);
 end loop;
end $$;

drop trigger if exists inspection_push_delivery_wake on private.guincho_inspection_push_deliveries;

create or replace function private.invalidate_inspection_alerts(p_id uuid) returns void
language plpgsql security definer set search_path='' as $$
begin
 update private.guincho_inspection_review_alerts set invalidated_at=now(),revision=revision+1
 where appointment_id=p_id and invalidated_at is null and not private.inspection_alert_pending(id);
end $$;

create or replace function private.inspection_review_event() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if tg_table_name='guincho_inspection_versions' then
  if new.state='SUBMITTED' and old.state is distinct from new.state then
   insert into private.guincho_inspection_review_alerts(client_id,appointment_id,version_id,environment_id)
   select a.client_id,a.id,new.id,private.inspection_alert_environment(a.id)
   from public.appointments a where a.id=new.appointment_id
   on conflict do nothing;
  end if;
  perform private.invalidate_inspection_alerts(new.appointment_id);
 else
  perform private.invalidate_inspection_alerts(new.id);
 end if;
 return new;
end $$;

-- No client or worker may register or dispatch inspection push after this migration.
revoke all on function public.register_my_guincho_inspection_push(uuid,text,text,text),
 public.revoke_my_guincho_inspection_push(uuid,text,uuid),
 public.claim_guincho_inspection_push_deliveries(),
 public.get_guincho_inspection_push_delivery(uuid,uuid),
 public.ack_guincho_inspection_push_delivery(uuid,uuid,boolean),
 public.defer_guincho_inspection_push_delivery(uuid,uuid,text,integer,boolean,boolean)
 from public,anon,authenticated,service_role;

create or replace function private.run_inspection_push_dispatch() returns void
language plpgsql security definer set search_path='' as $$
begin
 return;
end $$;

notify pgrst,'reload schema';
commit;
