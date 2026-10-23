begin;

-- Central PitTec pricing replaced the shared RPC and removed its Guincho branch.
-- Restore routing before the generic provider checks and transitions so unpaid
-- Guincho offers move to AWAITING_PAYMENT through the dedicated response RPC.
-- Keep the installed PitTec body, privileges and function signature intact.
do $$
declare
  definition text;
begin
  select pg_get_functiondef('public.respond_to_mobile_dispatch(uuid,boolean,bigint)'::regprocedure)
    into definition;

  if position('public.respond_guincho_request(' in definition) = 0 then
    if definition !~* '\mbegin\M' then
      raise exception 'Unexpected respond_to_mobile_dispatch function body';
    end if;

    definition := regexp_replace(definition, '\mbegin\M', $guard$begin
  if exists (
    select 1 from public.appointments
    where id=p_appointment_id and guincho_quote_id is not null
  ) then
    return public.respond_guincho_request(
      p_appointment_id,p_expected_revision,gen_random_uuid(),p_accept
    );
  end if;
$guard$, 'i');
    execute definition;
  end if;
end;
$$;

notify pgrst,'reload schema';

commit;
