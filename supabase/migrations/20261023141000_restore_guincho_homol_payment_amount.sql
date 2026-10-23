begin;

-- Central Guincho pricing replaced reserve_order_payment and lost the Homol
-- amount handling installed by 20260930153000. The commercial quote remains
-- unchanged; payment_amount is the charge agreed for this checkout.
do $$
declare
  definition text;
begin
  select pg_get_functiondef(
    'public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean)'::regprocedure
  ) into definition;

  definition := replace(
    definition,
    'coalesce(a.payment_amount,a.customer_total_amount)',
    'a.customer_total_amount'
  );
  definition := replace(
    definition,
    'a.customer_total_amount',
    'coalesce(a.payment_amount,a.customer_total_amount)'
  );
  execute definition;
end;
$$;

notify pgrst,'reload schema';

commit;
