begin;

-- Covers refunds requested by existing domain triggers as well as the new worker.
create function private.schedule_payment_refund() returns trigger
language plpgsql set search_path='' as $$
begin
  if new.status='REFUND_PENDING' and (tg_op='INSERT' or old.status is distinct from new.status) then
    new.next_action_at:=now();
    new.work_action:='REFUND';
    new.cancel_requested_at:=coalesce(new.cancel_requested_at,now());
    new.lease_token:=null;
    new.lease_until:=null;
    new.revision:=new.revision+1;
  end if;
  return new;
end;
$$;
create trigger schedule_payment_refund before insert or update on public.payments
for each row execute function private.schedule_payment_refund();
revoke all on function private.schedule_payment_refund() from public,anon,authenticated;

create function private.run_payment_reconciliation() returns void
language plpgsql security definer set search_path='' as $$
declare project_url text; worker_secret text;
begin
  delete from private.payment_webhook_inbox where id in (
    select id from private.payment_webhook_inbox where processed_at<now()-interval '7 days'
    order by processed_at limit 200
  );
  if not exists(select 1 from public.payments where next_action_at<=now() and (lease_until is null or lease_until<now()))
    and not exists(select 1 from private.payment_webhook_inbox where processed_at is null
      and next_action_at<=now() and (lease_until is null or lease_until<now())) then return; end if;
  select decrypted_secret into project_url from vault.decrypted_secrets where name='project_url' limit 1;
  select decrypted_secret into worker_secret from vault.decrypted_secrets where name='MP_RECONCILE_SECRET' limit 1;
  if project_url is null or worker_secret is null then return; end if;
  perform net.http_post(url:=rtrim(project_url,'/')||'/functions/v1/mp-reconcile-payments',
    headers:=jsonb_build_object('Content-Type','application/json','x-reconcile-secret',worker_secret),
    body:='{}'::jsonb,timeout_milliseconds:=45000);
end;
$$;
revoke all on function private.run_payment_reconciliation() from public,anon,authenticated;
select cron.schedule('pitgo-reconcile-payments','* * * * *','select private.run_payment_reconciliation();');
create index payment_webhook_retention_idx on private.payment_webhook_inbox(processed_at) where processed_at is not null;
commit;
