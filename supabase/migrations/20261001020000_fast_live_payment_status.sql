begin;

-- Valid pending checkouts need prompt GETs even when the provider webhook is delayed.
-- Keep cancellation, compensation, support cases and older retries on their existing cadence.
do $$
declare definition text; old_fragment text := 'then now()+case when created_at<';
begin
  definition := pg_get_functiondef('public.mp_apply_evidence(uuid,bigint,uuid,jsonb)'::regprocedure);
  if position(old_fragment in definition)=0 then
    raise exception 'Unexpected payment evidence scheduling contract';
  end if;
  definition := replace(definition, old_fragment,
    'then now()+case when next_status=''PENDING'' and next_sync=''CURRENT''
      and created_at>=now()-interval ''30 minutes'' and not is_compensation and not conflicting
      and cancel_requested_at is null and support_required_at is null
      and (appointment_id is null or exists(select 1 from public.appointments a
        where a.id=payments.appointment_id and a.status not in (''CANCELLED'',''DISPUTED'',''COMPLETED'')))
      then interval ''10 seconds'' when created_at<');
  execute definition;
end $$;

create or replace function private.run_payment_reconciliation() returns void
language plpgsql security definer set search_path='' as $$
declare project_url text; worker_secret text;
begin
  delete from private.payment_webhook_inbox where id in (
    select id from private.payment_webhook_inbox where processed_at<now()-interval '7 days'
    order by processed_at limit 200
  );
  -- Match mp_claim_work's eligibility, so held support cases cannot wake an idle worker.
  if not exists(select 1 from public.payments p where p.next_action_at<=now()
    and (p.lease_until is null or p.lease_until<now())
    and (p.support_required_at is null or (p.status='PENDING' and p.sync_state in ('VERIFYING','CURRENT')
      and not p.is_compensation and p.cancel_requested_at is null and p.created_at>=now()-interval '24 hours'
      and (p.appointment_id is null or exists(select 1 from public.appointments a
        where a.id=p.appointment_id and a.status not in ('CANCELLED','DISPUTED','COMPLETED'))))))
    and not exists(select 1 from private.payment_webhook_inbox where processed_at is null
      and next_action_at<=now() and (lease_until is null or lease_until<now())) then return; end if;
  select decrypted_secret into project_url from vault.decrypted_secrets where name='project_url' limit 1;
  select decrypted_secret into worker_secret from vault.decrypted_secrets where name='MP_RECONCILE_SECRET' limit 1;
  if project_url is null or worker_secret is null then return; end if;
  perform net.http_post(url:=rtrim(project_url,'/')||'/functions/v1/mp-reconcile-payments',
    headers:=jsonb_build_object('Content-Type','application/json','x-reconcile-secret',worker_secret),
    body:='{}'::jsonb,timeout_milliseconds:=45000);
end $$;
revoke all on function private.run_payment_reconciliation() from public,anon,authenticated;
select cron.schedule('pitgo-reconcile-payments','10 seconds','select private.run_payment_reconciliation();');
commit;
