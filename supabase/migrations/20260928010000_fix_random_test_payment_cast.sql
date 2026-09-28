begin;

create or replace function private.pittec_calculate_pricing(p_provider_amount numeric, p_distance_meters numeric)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  rule_row public.pittec_pricing_rules%rowtype;
  base numeric(10,2);
  markup numeric(10,2);
  fee numeric(10,2);
  distance_charge numeric(10,2);
  payment_amount numeric(10,2);
begin
  base := round(p_provider_amount::numeric, 2);
  select * into rule_row from public.pittec_pricing_rules
  where active and effective_from <= now() and (effective_until is null or effective_until > now())
  order by effective_from desc, version desc limit 1;
  if rule_row.id is null or rule_row.distance_rate_per_km is null
    or p_provider_amount is null or p_provider_amount <= 0
    or p_distance_meters is null or p_distance_meters < 0 then
    raise exception using errcode='22023', message='PITTEC_PRICING_UNAVAILABLE';
  end if;
  distance_charge := round(p_distance_meters / 1000 * rule_row.distance_rate_per_km, 2);
  base := base + distance_charge;
  markup := round(base * rule_row.markup_percent / 100, 2);
  fee := round(base * rule_row.fee_percent / 100 + rule_row.fee_fixed_amount, 2);
  payment_amount := case
    when rule_row.payment_test_mode then round((
      rule_row.test_payment_min_amount
      + floor(random() * ((rule_row.test_payment_max_amount - rule_row.test_payment_min_amount) * 100 + 1)) / 100
    )::numeric, 2)
    else round(base + markup + fee, 2)
  end;
  return jsonb_build_object(
    'rule_id', rule_row.id, 'rule_version', rule_row.version,
    'service_amount', round(p_provider_amount, 2), 'distance_meters', p_distance_meters,
    'distance_rate_per_km', rule_row.distance_rate_per_km, 'distance_charge_amount', distance_charge,
    'provider_amount', base, 'markup_amount', markup, 'customer_fee_amount', fee,
    'customer_total_amount', round(base + markup + fee, 2), 'payment_amount', payment_amount,
    'payment_test_mode', rule_row.payment_test_mode, 'currency', rule_row.currency,
    'quote_expires_at', now() + interval '15 minutes'
  );
end; $$;

revoke all on function private.pittec_calculate_pricing(numeric, numeric) from public, anon, authenticated;
commit;
