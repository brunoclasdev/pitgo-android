begin;

-- The size-pricing migration recreated this function and dropped the Homol
-- payment_amount support. Keep the commercial quote frozen; validate and charge
-- the server-owned checkout amount, as before size-based pricing.
do $$
declare definition text;
begin
 definition := pg_get_functiondef(
  'public.reserve_order_payment(uuid,uuid,text,uuid,uuid,bigint,text,uuid,numeric,boolean)'::regprocedure
 );
 -- Normalize first so reapplying this repair cannot nest the fallback.
 definition := replace(definition,
  'coalesce(a.payment_amount,a.customer_total_amount)', 'a.customer_total_amount');
 if position('p_dispatch_kind is distinct from ''GUINCHO_DIRECT''' in definition)=0
    or position('p_expected_amount is distinct from a.customer_total_amount' in definition)=0
    or position('q.size_price_id' in definition)=0 then
  raise exception 'Unexpected size-based Guincho payment contract';
 end if;
 definition := replace(definition,
  'a.customer_total_amount', 'coalesce(a.payment_amount,a.customer_total_amount)');
 -- Acceptance grants its own payment deadline. The appointment deadline above
 -- remains mandatory; the discovery quote's earlier deadline must not shorten it.
 if position('if a.quote_expires_at<=now()' in definition)=0 then
  raise exception 'Accepted appointment deadline guard is missing';
 end if;
 definition := replace(definition,
  'and r.version=q.pricing_rule_version and q.expires_at>now()',
  'and r.version=q.pricing_rule_version');
 execute definition;
end $$;

notify pgrst, 'reload schema';
commit;
