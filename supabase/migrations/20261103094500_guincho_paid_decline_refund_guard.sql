begin;
create function private.refund_declined_guincho() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if new.guincho_quote_id is not null and new.status='CANCELLED' and new.cancellation_reason='PROVIDER_DECLINED' and old.status is distinct from new.status then
  update public.payments set status='REFUND_PENDING',cancel_requested_at=coalesce(cancel_requested_at,now()),
   next_action_at=now(),work_action='REFUND',lease_token=null,lease_until=null,revision=revision+1,updated_at=now()
   where appointment_id=new.id and request_id=new.dispatch_search_id and client_id=new.client_id and status='APPROVED';
 end if;
 return new;
end $$;
create trigger refund_declined_guincho after update of status on public.appointments for each row execute function private.refund_declined_guincho();
revoke all on function private.refund_declined_guincho() from public,anon,authenticated;
commit;
