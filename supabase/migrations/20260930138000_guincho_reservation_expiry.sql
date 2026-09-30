begin;
create function private.expire_guincho_reservations() returns void
language plpgsql security definer set search_path='' as $$
declare appointment_id uuid;
begin
 for appointment_id in select a.id from public.appointments a
 where a.guincho_quote_id is not null and a.status='AWAITING_PAYMENT' and a.quote_expires_at<=now()
 order by a.quote_expires_at limit 100 loop
  perform private.cancel_guincho_appointment(appointment_id,'PAYMENT_RESERVATION_EXPIRED');
 end loop;
end; $$;
revoke all on function private.expire_guincho_reservations() from public,anon,authenticated;
select cron.schedule('guincho-reservation-expiry','* * * * *','select private.expire_guincho_reservations()');
commit;
