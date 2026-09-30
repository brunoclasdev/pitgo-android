begin;
-- Off by default in every project. Enable only through service-role configuration in Dev.
create table private.guincho_runtime_settings(id boolean primary key default true check(id),simulation_enabled boolean not null default false);
insert into private.guincho_runtime_settings values(true,false);
revoke all on private.guincho_runtime_settings from public,anon,authenticated;
grant all on private.guincho_runtime_settings to service_role;
alter table public.payments drop constraint payments_provider_api_check;
alter table public.payments add constraint payments_provider_api_check check(provider_api in ('PAYMENTS','ORDERS','SIMULATED'));
alter table public.payments add constraint payments_simulated_guincho_only check(provider_api<>'SIMULATED' or dispatch_kind='GUINCHO_DIRECT');

create function private.guard_guincho_simulated_payment() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if new.provider_api<>'SIMULATED' then return new; end if;
 if not exists(select 1 from private.guincho_runtime_settings where simulation_enabled) then
 raise exception using errcode='42501',message='SIMULATION_DISABLED'; end if;
 if new.status in ('REFUND_PENDING','REFUND_FAILED') then
  new.status:='REFUNDED'; new.refunded_amount:=new.amount;
 end if;
 if new.cancel_requested_at is not null and new.status='PENDING' then new.status:='CANCELLED'; end if;
 new.next_action_at:=null; new.work_action:=null; new.lease_token:=null; new.lease_until:=null;
 return new;
end; $$;
revoke all on function private.guard_guincho_simulated_payment() from public,anon,authenticated;
create trigger guard_guincho_simulated_payment before insert or update on public.payments
for each row execute function private.guard_guincho_simulated_payment();

create function public.simulate_guincho_payment(p_request_id uuid,p_status text,p_method text default 'PIX') returns jsonb
language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype; p public.payments%rowtype; payment_uuid uuid:=gen_random_uuid();
begin
 if not exists(select 1 from private.guincho_runtime_settings where simulation_enabled) then
 raise exception using errcode='42501',message='SIMULATION_DISABLED'; end if;
 if p_status is null or p_status not in ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')
 or p_method is null or p_method not in ('PIX','SAVED_CARD') then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,2200));
 select * into s from public.service_searches where id=p_request_id and client_id=auth.uid() and dispatch_kind='GUINCHO_DIRECT' for update;
 if s.id is null then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 select * into a from public.appointments where id=s.current_appointment_id for update;
 select * into p from public.payments where request_id=s.id order by created_at desc limit 1 for update;
 if p.id is not null and p.provider_api<>'SIMULATED' then raise exception using errcode='22023',message='INVALID_PAYMENT_CONTEXT'; end if;
 if p.id is null then
  if a.status<>'AWAITING_PAYMENT' or a.quote_expires_at<=now() or p_status<>'PENDING' then
   raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
  insert into public.payments(id,request_id,client_id,dispatch_kind,service_id,appointment_id,quote_version,
   method,amount,currency,provider_api,external_reference,idempotency_key,status,sync_state)
  values(payment_uuid,s.id,auth.uid(),'GUINCHO_DIRECT',a.service_id,a.id,a.quote_version,p_method,
   a.customer_total_amount,'BRL','SIMULATED','SIMULATED-'||payment_uuid::text,payment_uuid::text,'PENDING','CURRENT') returning * into p;
 elsif p.status='PENDING' and p_status<>'PENDING' then
  update public.payments set status=p_status,settled_amount=case when p_status='APPROVED' then amount else 0 end,
   revision=revision+1,updated_at=now() where id=p.id returning * into p;
 end if;
 select * into p from public.payments where id=p.id;
 return private.payment_snapshot(p);
end; $$;
revoke all on function public.simulate_guincho_payment(uuid,text,text) from public,anon;
grant execute on function public.simulate_guincho_payment(uuid,text,text) to authenticated;
commit;
