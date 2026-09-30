begin;
create function private.cancel_guincho_appointment(p_appointment_id uuid,p_reason text) returns void
language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; search_uuid uuid;
begin
 select dispatch_search_id into search_uuid from public.appointments where id=p_appointment_id and guincho_quote_id is not null;
 perform 1 from public.service_searches where id=search_uuid for update;
 select * into a from public.appointments where id=p_appointment_id and guincho_quote_id is not null for update;
 if a.id is null or a.status in ('CANCELLED','COMPLETED','DISPUTED','AWAITING_CLIENT_COMPLETION_CONFIRMATION') then return; end if;
 update public.appointments set status='CANCELLED',cancelled_by='SYSTEM',cancellation_reason=p_reason,
 cancelled_at=now(),ended_at=now(),revision=revision+1,updated_at=now() where id=a.id;
 update public.service_searches set status='CANCELLED',ended_at=now(),revision=revision+1,updated_at=now(),
 next_dispatch_at=null,search_latitude=null,search_longitude=null,search_point=null,search_accuracy_m=null
 where id=a.dispatch_search_id;
 delete from public.provider_current_locations where appointment_id=a.id;
 delete from public.client_current_locations where appointment_id=a.id;
 delete from public.appointment_route_snapshots where appointment_id=a.id;
 perform private.apply_pending_provider_mode(a.provider_id);
end; $$;
revoke all on function private.cancel_guincho_appointment(uuid,text) from public,anon,authenticated;

create function private.reconcile_guincho_payment_change() returns trigger
language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype;
begin
 if new.dispatch_kind<>'GUINCHO_DIRECT' then return new; end if;
 if tg_op='UPDATE' and old.status=new.status and old.sync_state=new.sync_state then return new; end if;
 select * into s from public.service_searches where id=new.request_id for update;
 select * into a from public.appointments where id=new.appointment_id and dispatch_search_id=s.id for update;
 if new.status='APPROVED' and new.sync_state='CURRENT' then
  if a.id is not null and a.status in ('PENDING','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION','DISPUTED','COMPLETED')
  and new.client_id=a.client_id and new.quote_version=a.quote_version and new.amount=a.customer_total_amount
  and private.payment_can_release(new,a.service_id) then return new; end if;
  if a.id is null or s.dispatch_kind is distinct from 'GUINCHO_DIRECT' or new.client_id is distinct from a.client_id
  or new.quote_version is distinct from a.quote_version or new.amount is distinct from a.customer_total_amount
  or new.currency<>'BRL' or a.status<>'AWAITING_PAYMENT' or s.status<>'AWAITING_PAYMENT'
  or a.quote_expires_at<=now() or not private.payment_can_release(new,a.service_id) then
   update public.payments set status='REFUND_PENDING',cancel_requested_at=coalesce(cancel_requested_at,now()),
    next_action_at=now(),work_action='REFUND',lease_token=null,lease_until=null,revision=revision+1,updated_at=now()
   where id=new.id and status='APPROVED';
   if a.id is not null then perform private.cancel_guincho_appointment(a.id,'PAYMENT_NOT_RELEASABLE'); end if;
   return new;
  end if;
  update public.appointments set status='PENDING',response_deadline=now()+interval '60 seconds',
   revision=revision+1,updated_at=now() where id=a.id;
  update public.service_searches set status='AWAITING_PROVIDER',revision=revision+1,updated_at=now(),next_dispatch_at=null where id=s.id;
 elsif new.status in ('REJECTED','EXPIRED','CANCELLED') and new.sync_state='CURRENT' and a.status='AWAITING_PAYMENT' then
  perform private.cancel_guincho_appointment(a.id,'PAYMENT_NOT_APPROVED');
 end if;
 return new;
end; $$;
revoke all on function private.reconcile_guincho_payment_change() from public,anon,authenticated;
create trigger reconcile_guincho_payment_insert after insert on public.payments
for each row execute function private.reconcile_guincho_payment_change();
create trigger reconcile_guincho_payment_update after update of status,sync_state on public.payments
for each row execute function private.reconcile_guincho_payment_change();
commit;
