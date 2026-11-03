begin;

-- The linked database already owns these tables and mobile contracts. Do not recreate them.
alter table public.pittec_service_pricing
  add column if not exists markup_percent numeric(7,4) check (markup_percent between 0 and 100),
  add column if not exists fee_percent numeric(7,4) check (fee_percent between 0 and 100);

-- Superseded versions remain untouched; current versions are selected deterministically.
drop index if exists public.pittec_service_pricing_one_active;
drop index if exists public.guincho_pricing_rules_one_active;
create index if not exists pittec_service_pricing_latest on public.pittec_service_pricing(service_id, created_at desc, version desc);
create index if not exists guincho_pricing_rules_latest on public.guincho_pricing_rules(created_at desc, version desc);
alter table public.pittec_service_pricing enable row level security;
alter table public.guincho_pricing_rules enable row level security;
revoke all on public.pittec_service_pricing, public.guincho_pricing_rules from public, anon, authenticated;

create or replace view private.current_pittec_service_pricing as
select distinct on (service_id) * from public.pittec_service_pricing
where active and effective_from <= now() order by service_id, created_at desc, version desc;
create or replace view private.current_guincho_pricing_rules as
select * from public.guincho_pricing_rules
where active and effective_from <= now() and (effective_until is null or effective_until > now())
order by created_at desc, version desc limit 1;
revoke all on private.current_pittec_service_pricing, private.current_guincho_pricing_rules from public, anon, authenticated;

create or replace function public.dashboard_save_service_pricing(p_config jsonb, p_request_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 k text := p_config->>'kind'; sid uuid := nullif(p_config->>'serviceId','')::uuid;
 b numeric := (p_config->>'value')::numeric;
 m numeric := (p_config->>'pitgoFeePercent')::numeric;
 f numeric := (p_config->>'otherFeesPercent')::numeric;
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
  select * into g from public.guincho_pricing_rules where request_id=p_request_id;
  if g.id is not null then
   if (g.base_amount,g.markup_percent,g.fee_percent,g.duration_minutes) is distinct from (b,m,f,d) then
    raise exception using errcode='22023',message='IDEMPOTENCY_CONFLICT'; end if;
   return jsonb_build_object('id',g.id,'version',g.version,'createdAt',g.created_at);
  end if;
  insert into public.guincho_pricing_rules(request_id,base_amount,duration_minutes,distance_rate_per_km,markup_percent,fee_percent,fee_fixed_amount,guincheiro_price,created_at)
   values(p_request_id,b,d,0,m,f,0,b,clock_timestamp()) returning * into g;
  perform public.record_dashboard_action('SET_PRICING','GUINCHO_PRICING',g.id);
  return jsonb_build_object('id',g.id,'version',g.version,'createdAt',g.created_at);
 end if;
end; $$;
revoke all on function public.dashboard_save_service_pricing(jsonb,uuid) from public, anon;
grant execute on function public.dashboard_save_service_pricing(jsonb,uuid) to authenticated;

-- Preserve existing dashboard RPC signatures, but enforce OWNER and append-only writes.
create or replace function public.set_dashboard_pittec_pricing(p_service_id uuid,p_request_id uuid,p_price numeric,p_duration_minutes integer)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v jsonb; t public.pittec_service_pricing%rowtype; r public.pittec_pricing_rules%rowtype;
begin
 select * into t from private.current_pittec_service_pricing where service_id=p_service_id;
 select * into r from public.pittec_pricing_rules where active and effective_from<=now() and (effective_until is null or effective_until>now()) order by version desc limit 1;
 v:=public.dashboard_save_service_pricing(jsonb_build_object('kind','PITTEC','serviceId',p_service_id,'value',p_price,
 'durationMinutes',p_duration_minutes,'pitgoFeePercent',coalesce(t.markup_percent,r.markup_percent,0),'otherFeesPercent',coalesce(t.fee_percent,r.fee_percent,0)),p_request_id);
 return (select to_jsonb(r) from public.pittec_service_pricing r where id=(v->>'id')::uuid);
end; $$;
create or replace function public.set_dashboard_guincho_pricing(p_request_id uuid,p_config jsonb)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v jsonb;
begin
 v:=public.dashboard_save_service_pricing(jsonb_build_object('kind','GUINCHO','value',p_config->'base_amount',
 'durationMinutes',p_config->'duration_minutes','pitgoFeePercent',p_config->'markup_percent','otherFeesPercent',p_config->'fee_percent'),p_request_id);
 return (select to_jsonb(r) from public.guincho_pricing_rules r where id=(v->>'id')::uuid);
end; $$;
revoke all on function public.set_dashboard_pittec_pricing(uuid,uuid,numeric,integer), public.set_dashboard_guincho_pricing(uuid,jsonb) from public, anon;
grant execute on function public.set_dashboard_pittec_pricing(uuid,uuid,numeric,integer), public.set_dashboard_guincho_pricing(uuid,jsonb) to authenticated;

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
   t.duration_minutes,t.created_at,(t.markup_percent is null or t.fee_percent is null) as legacy,
   exists(select 1 from private.current_pittec_service_pricing c where c.id=t.id) as is_current
  from public.pittec_service_pricing t join public.services s on s.id=t.service_id
  where p_kind='PITTEC' and (p_service_id is null or t.service_id=p_service_id)
  union all
  select t.id,t.version,null::uuid,'Guincho',t.base_amount,t.markup_percent,t.fee_percent,t.duration_minutes,t.created_at,(t.distance_rate_per_km<>0 or t.fee_fixed_amount<>0) as legacy,
   exists(select 1 from private.current_guincho_pricing_rules c where c.id=t.id)
  from public.guincho_pricing_rules t where p_kind='GUINCHO'
 ), mapped as (
  select created_at,version,is_current,jsonb_build_object('id',id,'version',version,'serviceId',service_id,
   'serviceName',service_name,'value',value,'pitgoFeePercent',pitgo_fee,'otherFeesPercent',other_fees,
   'durationMinutes',duration_minutes,'createdAt',created_at,'isCurrent',is_current,'legacy',legacy) as row
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

CREATE OR REPLACE FUNCTION private.advance_pittec_dispatch(p_search_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  search_row public.service_searches%rowtype;
  chosen record;
  radius integer;
  next_sequence integer;
  candidate_uuid uuid;
  appointment_uuid uuid;
begin
  select search.* into search_row
  from public.service_searches as search where search.id = p_search_id for update;
  if search_row.id is null or not private.is_pittec_service(search_row.service_id) then
    raise exception using errcode = 'P0002', message = 'PITTEC_SEARCH_UNAVAILABLE';
  end if;
  if search_row.status <> 'SEARCHING' then
    return jsonb_build_object('outcome','UNCHANGED');
  end if;
  if search_row.search_point is null then
    raise exception using errcode = '22023', message = 'INVALID_LOCATION';
  end if;

  if not exists(select 1 from private.current_pittec_service_pricing where service_id=search_row.service_id and active and effective_from<=now()) then
    raise exception using errcode='22023',message='PITTEC_PRICING_UNAVAILABLE';
  end if;
  radius := greatest(10000, search_row.current_radius_m);
  loop
    select
      provider.id as provider_id,
      offer.id as provider_service_id,
      provider.name as provider_name,
      coalesce(
        nullif(concat_ws(' - ',nullif(btrim(client_address.city),''),nullif(btrim(client_address.state),'')),''),
        case radius when 10000 then 'Região de até 10 km'
          when 25000 then 'Região de até 25 km' else 'Região de até 50 km' end
      ) as locality,
      extensions.st_distance(location.position, search_row.search_point) as distance_meters,
      tariff.price, false as is_free, tariff.id as tariff_id
    into chosen
    from public.provider_services as offer
    join private.current_pittec_service_pricing tariff on tariff.service_id=offer.service_id and tariff.active and tariff.effective_from<=now()
    join public.providers as provider on provider.id = offer.provider_id
    join public.account_roles as role
      on role.user_id = provider.account_id and role.account_type = 'PROVIDER'
    join public.provider_discovery_locations as location on location.provider_id = provider.id
    left join public.client_addresses as client_address on client_address.client_id = search_row.client_id
    where offer.service_id = search_row.service_id and offer.active
      and provider.active and provider.available and provider.location_mode = 'PITTEC'
      and location.published_at >= now() - interval '30 seconds'
      and extensions.st_dwithin(location.position, search_row.search_point, radius)
      and not exists (
        select 1 from private.dispatch_candidates as prior
        where prior.search_id = search_row.id and prior.provider_id = provider.id
      )
      and not exists (
        select 1 from public.appointments as active_appointment
        where active_appointment.provider_id = provider.id
          and active_appointment.status in (
            'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
          )
      )
    order by extensions.st_distance(location.position, search_row.search_point), provider.id
    limit 1;

    if chosen.provider_id is null then
      if radius = 10000 then radius := 25000; continue; end if;
      if radius = 25000 then radius := 50000; continue; end if;
      update public.service_searches
      set status = 'SEARCHING', current_radius_m = 50000,
          current_candidate_id = null, current_appointment_id = null,
          next_dispatch_at = now() + interval '5 seconds',
          last_event = 'SEARCH_CONTINUES', last_event_at = now(),
          revision = revision + 1, updated_at = now(), ended_at = null
      where id = search_row.id;
      return jsonb_build_object('outcome','SEARCH_CONTINUES');
    end if;

    select coalesce(max(candidate.sequence),0) + 1 into next_sequence
    from private.dispatch_candidates as candidate where candidate.search_id = search_row.id;
    begin
      insert into private.dispatch_candidates(
        search_id,provider_id,provider_service_id,sequence,status,provider_name,
        locality,distance_meters,quoted_price,is_free,decided_at
      ) values (
        search_row.id,chosen.provider_id,chosen.provider_service_id,next_sequence,'REQUESTED',
        chosen.provider_name,chosen.locality,chosen.distance_meters,chosen.price,chosen.is_free,now()
      ) returning id into candidate_uuid;

      insert into public.appointments(
        client_id,provider_id,status,dispatch_search_id,dispatch_candidate_id,
        service_id,provider_service_id,quoted_price,is_free,response_deadline,revision,quote_version,service_pricing_id
      ) values (
        search_row.client_id,chosen.provider_id,'PENDING',search_row.id,candidate_uuid,
        search_row.service_id,chosen.provider_service_id,chosen.price,chosen.is_free,
        now() + interval '60 seconds',1,1,chosen.tariff_id
      ) returning id into appointment_uuid;
    exception when unique_violation then
      update private.dispatch_candidates
      set status = 'INVALIDATED', decided_at = now()
      where id = candidate_uuid;
      continue;
    end;

    update public.service_searches
    set status = 'AWAITING_PROVIDER', current_radius_m = radius,
        current_candidate_id = candidate_uuid, current_appointment_id = appointment_uuid,
        next_dispatch_at = null, revision = revision + 1, updated_at = now()
    where id = search_row.id;
    return jsonb_build_object('outcome','OFFERED','appointment_id',appointment_uuid);
  end loop;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.list_home_services()
 RETURNS TABLE(service_id uuid, name text, description text, category_name text, minimum_price numeric, dispatch_kind text)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  select service.id, service.name, service.description, category.name,
    case service.dispatch_kind
      when 'PITTEC_AUTO_SEQUENTIAL' then (select tariff.price from private.current_pittec_service_pricing tariff where tariff.service_id=service.id and tariff.active and tariff.effective_from<=now())
      when 'MOBILE_SEQUENTIAL' then (select rule.base_amount from private.current_guincho_pricing_rules rule where rule.active and rule.effective_from<=now() and (rule.effective_until is null or rule.effective_until>now()))
      else min(offer.price) filter (where offer.active) end, service.dispatch_kind
  from public.services as service
  join public.service_categories as category on category.id = service.category_id
  join public.provider_services as offer on offer.service_id = service.id and offer.active
  join public.providers as provider on provider.id = offer.provider_id and provider.active
  where service.active and category.active
    and auth.uid() is not null and private.has_account_type('CLIENT')
  group by service.id, service.name, service.description, category.name, service.dispatch_kind
  order by service.name;
$function$
;

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
 'guincho',(select jsonb_build_object('id',r.id,'version',r.version,'base_amount',r.base_amount,'duration_minutes',r.duration_minutes,'distance_rate_per_km',r.distance_rate_per_km,'currency',r.currency) from private.current_guincho_pricing_rules r where active and base_amount is not null
 and duration_minutes is not null and effective_from<=now() and (effective_until is null or effective_until>now()) limit 1));
end $function$
;

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
 distance_charge:=round(p_distance_meters/1000*r.distance_rate_per_km,2);
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

CREATE OR REPLACE FUNCTION public.create_guincho_request_before_payment(p_quote_id uuid, p_operation_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
#variable_conflict use_column
declare q public.guincho_quotes%rowtype; d public.guincho_trip_drafts%rowtype;
 r public.guincho_pricing_rules%rowtype; o public.provider_services%rowtype;
 a public.appointments%rowtype; request_uuid uuid:=gen_random_uuid(); client_name text; trip jsonb;
begin
 if not private.dispatch_is_client() then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 if p_operation_id is null or p_quote_id is null then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text,2801));
 select * into a from public.appointments where client_id=auth.uid() and guincho_operation_id=p_operation_id;
 if a.id is not null then
  if a.guincho_quote_id<>p_quote_id then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
  return jsonb_build_object('outcome','EXISTING','snapshot',private.guincho_snapshot(a.id));
 end if;
 select * into q from public.guincho_quotes where id=p_quote_id and client_id=auth.uid();
 if q.id is null then raise exception using errcode='42501',message='QUOTE_UNAVAILABLE'; end if;
 -- Serialize provider reservations before locking the draft and creating the operational row.
 perform pg_advisory_xact_lock(hashtextextended(q.provider_id::text,2802));
 perform 1 from public.providers where id=q.provider_id and active and available and location_mode='MOBILE' for update;
 if not found then raise exception using errcode='22023',message='PROVIDER_UNAVAILABLE'; end if;
 select * into d from public.guincho_trip_drafts where id=q.draft_id and client_id=auth.uid() for update;
 select * into r from public.guincho_pricing_rules where id=q.pricing_rule_id;
 select ps.* into o from public.provider_services ps join public.services s on s.id=ps.service_id
 join public.service_categories c on c.id=s.category_id
 where ps.id=q.provider_service_id and ps.provider_id=q.provider_id and ps.active and s.active
 and c.active and s.dispatch_kind='MOBILE_SEQUENTIAL';
 if q.expires_at<=now() then raise exception using errcode='22023',message='QUOTE_EXPIRED'; end if;
 if d.revision is distinct from q.input_revision or r.version is distinct from q.pricing_rule_version
 or o.id is null then raise exception using errcode='22023',message='QUOTE_CONFLICT'; end if;
 if not exists(select 1 from public.vehicles where id=d.vehicle_id and user_id=auth.uid() and active) then
 raise exception using errcode='22023',message='INVALID_VEHICLE'; end if;
 if exists(select 1 from public.appointments where provider_id=q.provider_id
 and status in ('PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION')) then
 raise exception using errcode='22023',message='PROVIDER_UNAVAILABLE'; end if;
 if exists(select 1 from public.service_searches where client_id=auth.uid() and service_id=o.service_id
 and status in ('SEARCHING','OPTION_FOUND','AWAITING_PROVIDER','AWAITING_PAYMENT','AWAITING_CLIENT_CONFIRMATION','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION')) then
 raise exception using errcode='22023',message='REQUEST_ALREADY_ACTIVE'; end if;
 select name into client_name from public.profiles where id=auth.uid();
 trip:=jsonb_build_object('client_name',client_name,'vehicle',d.vehicle_snapshot,
 'pickup_address',d.pickup_address,'destination_address',d.destination_address,
 'pickup_latitude',d.pickup_latitude,'pickup_longitude',d.pickup_longitude,
 'destination_latitude',d.destination_latitude,'destination_longitude',d.destination_longitude);
 insert into public.service_searches(id,client_id,service_id,dispatch_kind,status,search_latitude,search_longitude,
 search_point,current_radius_m,request_captured_at)
 values(request_uuid,auth.uid(),o.service_id,'GUINCHO_DIRECT','AWAITING_PROVIDER',d.pickup_latitude,d.pickup_longitude,
 extensions.st_setsrid(extensions.st_makepoint(d.pickup_longitude,d.pickup_latitude),4326)::extensions.geography,25000,now());
 insert into public.appointments(client_id,provider_id,dispatch_search_id,service_id,provider_service_id,
 status,response_deadline,guincho_quote_id,guincho_trip_snapshot,guincho_operation_id,quote_version,client_confirmed_quote_version,
 quoted_price,provider_amount,service_amount,distance_meters,distance_rate_per_km,distance_charge_amount,
 markup_amount,customer_fee_amount,customer_total_amount,payment_amount,payment_test_mode,pricing_currency,
 pricing_rule_version,quote_expires_at,is_free)
 values(auth.uid(),q.provider_id,request_uuid,o.service_id,o.id,'PENDING',now()+interval '60 seconds',q.id,trip,p_operation_id,
 q.version,q.version,q.provider_amount,q.provider_amount,q.base_amount,q.distance_meters,q.distance_rate_per_km,
 q.distance_charge_amount,q.markup_amount,q.fee_amount,q.customer_total_amount,q.customer_total_amount,false,'BRL',
 q.pricing_rule_version,q.expires_at,false) returning * into a;
 update public.service_searches set current_appointment_id=a.id,revision=revision+1 where id=request_uuid;
 return jsonb_build_object('outcome','CREATED','snapshot',private.guincho_snapshot(a.id));
exception when unique_violation then
 raise exception using errcode='22023',message='PROVIDER_UNAVAILABLE';
end; $function$
;

create or replace function private.snapshot_central_pittec_pricing()
returns trigger language plpgsql security definer set search_path = '' as $$
declare t public.pittec_service_pricing%rowtype; r public.pittec_pricing_rules%rowtype;
 d numeric; base numeric; markup numeric; fee numeric;
begin
 if not private.is_pittec_service(new.service_id) then return new; end if;
 if new.service_pricing_id is not null then
  select * into t from public.pittec_service_pricing where id=new.service_pricing_id;
 else select * into t from private.current_pittec_service_pricing where service_id=new.service_id; end if;
 if t.id is null then raise exception using errcode='22023',message='PITTEC_PRICING_UNAVAILABLE'; end if;
 select distance_meters into d from private.dispatch_candidates where id=new.dispatch_candidate_id;
 if d is null or d<0 then raise exception using errcode='22023',message='INVALID_LOCATION'; end if;
 if t.markup_percent is null or t.fee_percent is null then
  -- Legacy tariffs preserve their original formula until OWNER publishes a new version.
  select * into r from public.pittec_pricing_rules where active and effective_from<=now()
   and (effective_until is null or effective_until>now()) order by version desc limit 1;
  if r.id is null then raise exception using errcode='22023',message='PITTEC_PRICING_UNAVAILABLE'; end if;
  base:=t.price+round(d/1000*r.distance_rate_per_km,2);
  markup:=round(base*r.markup_percent/100,2); fee:=round(base*r.fee_percent/100+r.fee_fixed_amount,2);
  new.distance_rate_per_km:=r.distance_rate_per_km; new.distance_charge_amount:=base-t.price;
  new.pricing_rule_id:=r.id; new.pricing_rule_version:=r.version;
 else
  base:=t.price; markup:=round(base*t.markup_percent/100,2); fee:=round(base*t.fee_percent/100,2);
  new.distance_rate_per_km:=0; new.distance_charge_amount:=0;
  new.pricing_rule_id:=null; new.pricing_rule_version:=t.version;
 end if;
 new.service_pricing_id:=t.id; new.service_duration_minutes:=t.duration_minutes;
 new.service_amount:=t.price; new.distance_meters:=d; new.provider_amount:=base;
 new.quoted_price:=base; new.is_free:=false; new.markup_amount:=markup; new.customer_fee_amount:=fee;
 new.customer_total_amount:=base+markup+fee; new.payment_amount:=base+markup+fee; new.payment_test_mode:=false;
 new.pricing_currency:='BRL'; new.quote_expires_at:=now()+interval '15 minutes';
 return new;
end; $$;
revoke all on function private.snapshot_central_pittec_pricing() from public,anon,authenticated;

commit;
