begin;
-- An ambiguous, recent charge still needs provider GETs. A support flag must
-- not silently remove it from reconciliation. Settlement guards stay intact.
do $$
declare definition text;
begin
 definition:=pg_get_functiondef('public.mp_claim_work(integer)'::regprocedure);
 if position('and support_required_at is null' in definition)=0 then
  raise exception 'Unexpected reconciliation priority contract';
 end if;
 definition:=replace(definition,'and support_required_at is null',
  'and (support_required_at is null or (status=''PENDING'' and sync_state in (''VERIFYING'',''CURRENT'')
   and not is_compensation and cancel_requested_at is null and created_at>=now()-interval ''24 hours''
   and (appointment_id is null or exists(select 1 from public.appointments a
    where a.id=payments.appointment_id and a.status not in (''CANCELLED'',''DISPUTED'',''COMPLETED'')))))');
 execute definition;
end $$;
commit;
