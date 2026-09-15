begin;
create function public.mp_register_order_identity(p_id uuid,p_lease uuid,p_order text,p_reference text)
returns uuid language plpgsql security definer set search_path='' as $$
declare p public.payments%rowtype; known uuid; new_id uuid:=gen_random_uuid();
begin
  select * into p from public.payments where id=p_id for update;
  if p.id is null or p.provider_api<>'ORDERS' or p.external_reference is distinct from p_reference
    or p.lease_token is distinct from p_lease or p_lease is null or p.lease_until<now()
    or length(p_order) not between 1 and 128 then return null; end if;
  select id into known from public.payments where mp_order_id=p_order;
  if known is not null then return known; end if;
  if p.mp_order_id is null then
    update public.payments set mp_order_id=p_order where id=p.id;
    return p.id;
  end if;
  insert into public.payments(id,request_id,client_id,dispatch_kind,service_id,appointment_id,quote_version,
    method,card_brand,card_last_four_digits,amount,currency,provider_api,external_reference,
    idempotency_key,mp_order_id,status,sync_state,is_compensation,cancel_requested_at,
    support_required_at,next_action_at,work_action)
  values(new_id,p.request_id,p.client_id,p.dispatch_kind,p.service_id,p.appointment_id,p.quote_version,
    p.method,p.card_brand,p.card_last_four_digits,p.amount,p.currency,'ORDERS',p.external_reference,
    new_id::text,p_order,'PENDING','VERIFYING',true,now(),now(),now(),'RECONCILE')
  on conflict(mp_order_id) do nothing;
  update public.payments set support_required_at=coalesce(support_required_at,now()) where id=p.id;
  return (select id from public.payments where mp_order_id=p_order);
end;
$$;

create or replace function public.mp_claim_payment(p_api text,p_resource text,p_reference text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare row_data public.payments%rowtype; registered uuid;
begin
  select * into row_data from public.payments where provider_api=p_api and (
    (p_api='ORDERS' and mp_order_id=p_resource) or (p_api='PAYMENTS' and mp_payment_id=p_resource)
  ) limit 1 for update;
  if row_data.id is null and p_api='ORDERS' then
    select * into row_data from public.payments where provider_api='ORDERS' and external_reference=p_reference
      and not is_compensation order by created_at limit 1 for update;
    if row_data.id is null or row_data.lease_until>now() then return null; end if;
    update public.payments set lease_token=gen_random_uuid(),lease_until=now()+interval '90 seconds'
      where id=row_data.id returning * into row_data;
    registered:=public.mp_register_order_identity(row_data.id,row_data.lease_token,p_resource,p_reference);
    if registered is distinct from row_data.id then
      update public.payments set lease_token=null,lease_until=null where id=row_data.id;
      select * into row_data from public.payments where id=registered for update;
    else return to_jsonb(row_data); end if;
  end if;
  if row_data.id is null or row_data.lease_until>now() then return null; end if;
  update public.payments set lease_token=gen_random_uuid(),lease_until=now()+interval '90 seconds'
    where id=row_data.id returning * into row_data;
  return to_jsonb(row_data);
end;
$$;
revoke all on function public.mp_register_order_identity(uuid,uuid,text,text) from public,anon,authenticated;
grant execute on function public.mp_register_order_identity(uuid,uuid,text,text) to service_role;
commit;
