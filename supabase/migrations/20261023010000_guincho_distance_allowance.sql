begin;

-- Zero included kilometers preserves the distance formula of existing versions.
alter table public.guincho_pricing_rules
 add column included_distance_km numeric(10,3) not null default 0
 check (included_distance_km between 0 and 2000);

create or replace view private.current_guincho_pricing_rules as
select * from public.guincho_pricing_rules
where active and effective_from<=now() and (effective_until is null or effective_until>now())
order by created_at desc,version desc limit 1;
revoke all on private.current_guincho_pricing_rules from public,anon,authenticated;

create or replace function public.dashboard_save_service_pricing(p_config jsonb, p_request_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 k text := p_config->>'kind'; sid uuid := nullif(p_config->>'serviceId','')::uuid;
 b numeric := (p_config->>'value')::numeric;
 m numeric := (p_config->>'pitgoFeePercent')::numeric;
 f numeric := (p_config->>'otherFeesPercent')::numeric;
 included_km numeric; km_rate numeric;
 d integer := (p_config->>'durationMinutes')::integer;
 t public.pittec_service_pricing%rowtype; g public.guincho_pricing_rules%rowtype;
begin
 if private.dashboard_access_level() is distinct from 'OWNER' then
  raise exception using errcode='42501', message='OWNER_REQUIRED'; end if;
 if p_request_id is null or k is null or k not in ('PITTEC','GUINCHO')
 or b is null or b <= 0 or b > 99999999.99 or b <> round(b,2)
 or m is null or m not between 0 and 100 or m <> round(m,4)
 or f is null or f not between 0 and 100 or f <> round(f,4)
 or d is null or d not between 1 and 10080 then
  raise exception using errcode='22023', message='INVALID_PRICING'; end if;
 perform pg_advisory_xact_lock(hashtextextended('service-pricing:' || k || case when k='PITTEC' then coalesce(sid::text,'') else '' end,3510));
 if k='PITTEC' then
  if not exists(select 1 from public.services where id=sid and active and dispatch_kind='PITTEC_AUTO_SEQUENTIAL') then
   raise exception using errcode='22023', message='SERVICE_NOT_FOUND'; end if;
  select * into t from public.pittec_service_pricing where request_id=p_request_id;
  if t.id is not null then
   if (t.service_id,t.price,t.markup_percent,t.fee_percent,t.duration_minutes) is distinct from (sid,b,m,f,d) then
    raise exception using errcode='22023',message='IDEMPOTENCY_CONFLICT'; end if;
   return jsonb_build_object('id',t.id,'version',t.version,'createdAt',t.created_at);
  end if;
  insert into public.pittec_service_pricing(service_id,request_id,price,duration_minutes,markup_percent,fee_percent,created_at)
   values(sid,p_request_id,b,d,m,f,clock_timestamp()) returning * into t;
  perform public.record_dashboard_action('SET_PRICING','PITTEC_SERVICE_PRICING',t.id);
  return jsonb_build_object('id',t.id,'version',t.version,'createdAt',t.created_at);
 else
  select coalesce((p_config->>'includedDistanceKm')::numeric, included_distance_km),
   coalesce((p_config->>'distanceRatePerKm')::numeric, distance_rate_per_km)
   into included_km,km_rate from private.current_guincho_pricing_rules;
  included_km:=coalesce((p_config->>'includedDistanceKm')::numeric,included_km,0);
  km_rate:=coalesce((p_config->>'distanceRatePerKm')::numeric,km_rate,0);
  if included_km not between 0 and 2000 or included_km<>round(included_km,3)
   or km_rate not between 0 and 99999999.99 or km_rate<>round(km_rate,2) then
   raise exception using errcode='22023',message='INVALID_PRICING'; end if;
  select * into g from public.guincho_pricing_rules where request_id=p_request_id;
  if g.id is not null then
   if (g.base_amount,g.markup_percent,g.fee_percent,g.duration_minutes,g.included_distance_km,g.distance_rate_per_km) is distinct from (b,m,f,d,included_km,km_rate) then
    raise exception using errcode='22023',message='IDEMPOTENCY_CONFLICT'; end if;
   return jsonb_build_object('id',g.id,'version',g.version,'createdAt',g.created_at);
  end if;
  insert into public.guincho_pricing_rules(request_id,base_amount,duration_minutes,distance_rate_per_km,markup_percent,fee_percent,fee_fixed_amount,guincheiro_price,created_at,included_distance_km)
   values(p_request_id,b,d,km_rate,m,f,0,b,clock_timestamp(),included_km) returning * into g;
  perform public.record_dashboard_action('SET_PRICING','GUINCHO_PRICING',g.id);
  return jsonb_build_object('id',g.id,'version',g.version,'createdAt',g.created_at);
 end if;
end; $$;
revoke all on function public.dashboard_save_service_pricing(jsonb,uuid) from public, anon;
grant execute on function public.dashboard_save_service_pricing(jsonb,uuid) to authenticated;

create or replace function public.set_dashboard_guincho_pricing(p_request_id uuid,p_config jsonb)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v jsonb;
begin
 v:=public.dashboard_save_service_pricing(jsonb_build_object('kind','GUINCHO','value',p_config->'base_amount',
 'durationMinutes',p_config->'duration_minutes','pitgoFeePercent',p_config->'markup_percent','otherFeesPercent',p_config->'fee_percent',
 'includedDistanceKm',p_config->'included_distance_km','distanceRatePerKm',p_config->'distance_rate_per_km'),p_request_id);
 return (select to_jsonb(r) from public.guincho_pricing_rules r where id=(v->>'id')::uuid);
end; $$;
create or replace function public.dashboard_service_pricing(p_kind text,p_page integer default 1,p_service_id uuid default null)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare result jsonb;
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then
  raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 if p_kind is null or p_kind not in ('PITTEC','GUINCHO') or p_page is null or p_page not between 1 and 100000 then
  raise exception using errcode='22023',message='INVALID_FILTER'; end if;
 with legacy as (
  select markup_percent,fee_percent from public.pittec_pricing_rules
  where active and effective_from<=now() and (effective_until is null or effective_until>now())
  order by effective_from desc,version desc limit 1
 ), records as (
  select t.id,t.version,t.service_id,s.name as service_name,t.price as value,
   coalesce(t.markup_percent,(select markup_percent from legacy),0) as pitgo_fee,
   coalesce(t.fee_percent,(select fee_percent from legacy),0) as other_fees,
   null::numeric as included_km,null::numeric as km_rate,t.duration_minutes,t.created_at,(t.markup_percent is null or t.fee_percent is null) as legacy,
   exists(select 1 from private.current_pittec_service_pricing c where c.id=t.id) as is_current
  from public.pittec_service_pricing t join public.services s on s.id=t.service_id
  where p_kind='PITTEC' and (p_service_id is null or t.service_id=p_service_id)
  union all
  select t.id,t.version,null::uuid,'Guincho',t.base_amount,t.markup_percent,t.fee_percent,t.included_distance_km,t.distance_rate_per_km,t.duration_minutes,t.created_at,(t.fee_fixed_amount<>0) as legacy,
   exists(select 1 from private.current_guincho_pricing_rules c where c.id=t.id)
  from public.guincho_pricing_rules t where p_kind='GUINCHO'
 ), mapped as (
  select created_at,version,is_current,jsonb_build_object('id',id,'version',version,'serviceId',service_id,
   'serviceName',service_name,'value',value,'pitgoFeePercent',pitgo_fee,'otherFeesPercent',other_fees,
   'durationMinutes',duration_minutes,'createdAt',created_at,'isCurrent',is_current,'legacy',legacy,'includedDistanceKm',included_km,'distanceRatePerKm',km_rate) as row
  from records
 ) select jsonb_build_object(
  'current',coalesce((select jsonb_agg(row order by created_at desc,version desc) from (select * from mapped where is_current order by created_at desc,version desc limit 200) c),'[]'::jsonb),
  'items',coalesce((select jsonb_agg(row order by created_at desc,version desc) from (select * from mapped order by created_at desc,version desc limit 20 offset (p_page-1)*20) paged),'[]'::jsonb),
  'total',(select count(*) from mapped),'page',p_page,'pageSize',20,
  'services',coalesce((select jsonb_agg(jsonb_build_object('id',id,'name',name) order by name) from
   (select id,name from public.services where active and dispatch_kind='PITTEC_AUTO_SEQUENTIAL' order by name,id limit 200) choices),'[]'::jsonb)
 ) into result;
 return result;
end; $$;
revoke all on function public.dashboard_service_pricing(text,integer,uuid) from public, anon;
grant execute on function public.dashboard_service_pricing(text,integer,uuid) to authenticated;

CREATE OR REPLACE FUNCTION public.store_guincho_quote(p_client_id uuid, p_draft_id uuid, p_input_revision bigint, p_provider_id uuid, p_distance_meters numeric)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare d public.guincho_trip_drafts%rowtype; r public.guincho_pricing_rules%rowtype;
 o public.provider_services%rowtype; q public.guincho_quotes%rowtype; provider_name text;
 distance_charge numeric(10,2); remuneration numeric(10,2); markup numeric(10,2); fee numeric(10,2);
begin
 select * into d from public.guincho_trip_drafts where id=p_draft_id and client_id=p_client_id for update;
 if d.id is null then raise exception using errcode='42501',message='DRAFT_UNAVAILABLE'; end if;
 if d.revision is distinct from p_input_revision then raise exception using errcode='40001',message='QUOTE_CONFLICT'; end if;
 if p_distance_meters is null or not (p_distance_meters between 0 and 2000000) then
 raise exception using errcode='22023',message='ROUTE_UNAVAILABLE'; end if;
 select ps.* into o from public.provider_services ps join public.providers p on p.id=ps.provider_id
 join public.services s on s.id=ps.service_id join public.service_categories c on c.id=s.category_id
 join public.provider_discovery_locations l on l.provider_id=p.id
 where p.id=p_provider_id and p.active and p.available and p.location_mode='MOBILE'
 and ps.active and s.active and c.active and s.dispatch_kind='MOBILE_SEQUENTIAL'
 and l.published_at>=now()-interval '30 seconds'
 and extensions.st_dwithin(l.position,extensions.st_setsrid(extensions.st_makepoint(d.pickup_longitude,d.pickup_latitude),4326)::extensions.geography,25000)
 and not exists(select 1 from public.appointments a where a.provider_id=p.id
 and a.status in ('PENDING','AWAITING_PAYMENT','AWAITING_CLIENT_CONFIRMATION','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION'))
 order by ps.id limit 1;
 if o.id is null then raise exception using errcode='22023',message='PROVIDER_UNAVAILABLE'; end if;
 select * into r from private.current_guincho_pricing_rules where active and effective_from<=now()
 and (effective_until is null or effective_until>now()) order by version desc limit 1;
 if r.id is null or r.base_amount is null or r.duration_minutes is null then raise exception using errcode='22023',message='PRICING_UNAVAILABLE'; end if;
 distance_charge:=round(greatest(p_distance_meters/1000-r.included_distance_km,0)*r.distance_rate_per_km,2);
 remuneration:=r.base_amount+distance_charge;
 markup:=round(remuneration*r.markup_percent/100,2);
 fee:=round(remuneration*r.fee_percent/100+r.fee_fixed_amount,2);
 insert into public.guincho_quotes(draft_id,client_id,provider_id,provider_service_id,input_revision,
 pricing_rule_id,pricing_rule_version,base_amount,distance_meters,distance_rate_per_km,distance_charge_amount,
 provider_amount,markup_amount,fee_amount,customer_total_amount,duration_minutes)
 values(d.id,d.client_id,p_provider_id,o.id,d.revision,r.id,r.version,r.base_amount,p_distance_meters,
 r.distance_rate_per_km,distance_charge,remuneration,markup,fee,remuneration+markup+fee,r.duration_minutes) returning * into q;
 update public.guincho_trip_drafts set route_distance_meters=p_distance_meters,route_input_revision=d.revision,
 route_calculated_at=now() where id=d.id;
 select name into provider_name from public.providers where id=p_provider_id;
 return jsonb_build_object('quote_id',q.id,'version',q.version,'provider_id',q.provider_id,
 'provider_name',provider_name,'customer_total_amount',q.customer_total_amount::text,
 'duration_minutes',q.duration_minutes,'pricing_rule_version',q.pricing_rule_version,'currency',q.currency,'expires_at',q.expires_at);
end; $function$
;

-- Include the threshold in the existing public configuration payload.
CREATE OR REPLACE FUNCTION public.get_current_service_pricing(p_service_id uuid DEFAULT NULL::uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 return jsonb_build_object('pittec',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'service_id',r.service_id,'version',r.version,'price',r.price,'duration_minutes',r.duration_minutes,'currency',r.currency)) from private.current_pittec_service_pricing r
 where active and effective_from<=now() and (p_service_id is null or service_id=p_service_id)),'[]'::jsonb),
 'guincho',(select jsonb_build_object('id',r.id,'version',r.version,'base_amount',r.base_amount,'duration_minutes',r.duration_minutes,'distance_rate_per_km',r.distance_rate_per_km,'included_distance_km',r.included_distance_km,'currency',r.currency) from private.current_guincho_pricing_rules r where active and base_amount is not null
 and duration_minutes is not null and effective_from<=now() and (effective_until is null or effective_until>now()) limit 1));
end $function$
;


commit;
