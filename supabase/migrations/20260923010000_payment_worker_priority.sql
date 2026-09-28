begin;

-- Do not let old support cases occupy the whole worker batch. Recent payments
-- must be reconciled quickly so the client can leave the Pix checkout screen.
create or replace function public.mp_claim_work(p_limit integer default 20)
returns setof jsonb language plpgsql security definer set search_path='' as $$
declare
  item record;
  count_claimed integer := 0;
  token uuid;
begin
  for item in
    select id
    from public.payments
    where next_action_at <= now()
      and (lease_until is null or lease_until < now())
      and support_required_at is null
    order by case when created_at >= now() - interval '2 hours' then 0 else 1 end,
             created_at desc, next_action_at, id
    limit least(greatest(p_limit, 1), 20) / 2
    for update skip locked
  loop
    token := gen_random_uuid();
    return query
      update public.payments
      set lease_token = token,
          lease_until = now() + interval '90 seconds',
          reconcile_attempts = reconcile_attempts + 1
      where id = item.id
      returning jsonb_build_object('kind', 'payment', 'item', to_jsonb(payments));
    count_claimed := count_claimed + 1;
  end loop;

  for item in
    select id
    from private.payment_webhook_inbox
    where processed_at is null
      and next_action_at <= now()
      and (lease_until is null or lease_until < now())
    order by next_action_at, id
    limit least(greatest(p_limit, 1), 20) - count_claimed
    for update skip locked
  loop
    token := gen_random_uuid();
    return query
      update private.payment_webhook_inbox
      set lease_token = token,
          lease_until = now() + interval '90 seconds',
          attempts = attempts + 1
      where id = item.id
      returning jsonb_build_object('kind', 'inbox', 'item', to_jsonb(payment_webhook_inbox));
  end loop;
end;
$$;

revoke all on function public.mp_claim_work(integer) from public, anon, authenticated;
grant execute on function public.mp_claim_work(integer) to service_role;

commit;
