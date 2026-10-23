begin;
create or replace function public.store_guincho_quote(p_client_id uuid,p_draft_id uuid,p_input_revision bigint,
 p_provider_id uuid,p_distance_meters numeric) returns jsonb
language plpgsql security definer set search_path='' as $$
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
 select * into r from public.guincho_pricing_rules where active and effective_from<=now()
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
end; $$;
create or replace function public.reserve_order_payment(p_client_id uuid,p_request_id uuid,p_dispatch_kind text,p_service_id uuid,
 p_appointment_id uuid,p_quote_version bigint,p_method text,p_card_id uuid,
 p_expected_amount numeric default null,p_creation_enabled boolean default false)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype;
 p public.payments%rowtype; card public.saved_cards%rowtype; attempt uuid:=gen_random_uuid(); lease uuid:=gen_random_uuid();
begin
 if p_dispatch_kind is distinct from 'GUINCHO_DIRECT' then
  return public.reserve_order_payment_before_guincho(p_client_id,p_request_id,p_dispatch_kind,p_service_id,
   p_appointment_id,p_quote_version,p_method,p_card_id,p_expected_amount,p_creation_enabled);
 end if;
 if p_client_id is null or p_request_id is null or p_appointment_id is null or p_quote_version is null
 or p_quote_version<=0 or p_method is null or p_method not in ('PIX','SAVED_CARD') then
 raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,2200));
 select * into s from public.service_searches where id=p_request_id for update;
 select * into a from public.appointments where id=p_appointment_id for update;
 if s.client_id is distinct from p_client_id or s.dispatch_kind is distinct from 'GUINCHO_DIRECT'
 or a.client_id is distinct from p_client_id or a.dispatch_search_id is distinct from s.id
 or a.guincho_quote_id is null or a.quote_version is distinct from p_quote_version
 or (p_service_id is not null and a.service_id is distinct from p_service_id) then
 raise exception using errcode='22023',message='INVALID_PAYMENT_CONTEXT'; end if;
 select * into p from public.payments where request_id=s.id and
 (sync_state<>'CURRENT' or status in ('PENDING','APPROVED','REFUND_PENDING','REFUND_FAILED'))
 order by is_compensation,created_at desc limit 1 for update;
 if p.id is not null then
  if p.client_id<>p_client_id or p.dispatch_kind<>'GUINCHO_DIRECT' or p.appointment_id is distinct from a.id
  or p.quote_version is distinct from a.quote_version or p.amount is distinct from a.customer_total_amount
  or p.currency<>'BRL' then raise exception using errcode='22023',message='INVALID_PAYMENT_CONTEXT'; end if;
  return jsonb_build_object('created',false,'payment',to_jsonb(p));
 end if;
 if not p_creation_enabled then raise exception using errcode='55000',message='SERVICE_UNAVAILABLE'; end if;
 if a.status<>'AWAITING_PAYMENT' or s.status<>'AWAITING_PAYMENT' then
 raise exception using errcode='22023',message='INVALID_PAYMENT_CONTEXT'; end if;
 if a.quote_expires_at<=now() then raise exception using errcode='22023',message='QUOTE_EXPIRED'; end if;
 if not exists(select 1 from public.guincho_quotes q join public.guincho_trip_drafts d on d.id=q.draft_id
 join public.provider_services o on o.id=q.provider_service_id join public.guincho_pricing_rules r on r.id=q.pricing_rule_id
 where q.id=a.guincho_quote_id and d.revision=q.input_revision and o.active
 and r.version=q.pricing_rule_version and q.expires_at>now()) then
 raise exception using errcode='22023',message='PRICE_CHANGED'; end if;
 if p_expected_amount is distinct from a.customer_total_amount then
 raise exception using errcode='22023',message='PRICE_CHANGED'; end if;
 if p_method='SAVED_CARD' then
  select * into card from public.saved_cards where id=p_card_id and client_id=p_client_id;
  if card.id is null then raise exception using errcode='22023',message='CARD_NOT_FOUND'; end if;
 end if;
 insert into public.payments(id,request_id,client_id,dispatch_kind,service_id,appointment_id,quote_version,
 method,saved_card_id,card_brand,card_last_four_digits,amount,currency,provider_api,external_reference,idempotency_key,
 status,sync_state,next_action_at,work_action,lease_token,lease_until)
 values(attempt,s.id,p_client_id,'GUINCHO_DIRECT',a.service_id,a.id,a.quote_version,p_method,card.id,card.brand,
 card.last_four_digits,a.customer_total_amount,'BRL','ORDERS',attempt::text,attempt::text,'PENDING','CREATING',
 now()+interval '90 seconds','RECONCILE',lease,now()+interval '90 seconds') returning * into p;
 return jsonb_build_object('created',true,'payment',to_jsonb(p));
end; $$;
create or replace function public.simulate_guincho_payment(p_request_id uuid,p_status text,p_method text default 'PIX') returns jsonb
language plpgsql security definer set search_path='' as $$
#variable_conflict use_column
declare a public.appointments%rowtype; s public.service_searches%rowtype; p public.payments%rowtype; payment_uuid uuid:=gen_random_uuid();
begin
 if not exists(select 1 from private.guincho_runtime_settings where simulation_enabled) then
 raise exception using errcode='42501',message='SIMULATION_DISABLED'; end if;
 if p_status is null or p_status not in ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')
 or p_method is null or p_method not in ('PIX','SAVED_CARD') then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,2200));
 select * into s from public.service_searches where id=p_request_id and client_id=auth.uid() and dispatch_kind='GUINCHO_DIRECT' for update;
 if s.id is null then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 select * into a from public.appointments where id=s.current_appointment_id for update;
 select * into p from public.payments where request_id=s.id order by created_at desc limit 1 for update;
 if p.id is not null and p.provider_api<>'SIMULATED' then raise exception using errcode='22023',message='INVALID_PAYMENT_CONTEXT'; end if;
 if p.id is null then
  if a.status<>'AWAITING_PAYMENT' or a.quote_expires_at<=now() or p_status<>'PENDING' then
   raise exception using errcode='22023',message='INVALID_TRANSITION'; end if;
  if not exists(select 1 from public.guincho_quotes q join public.guincho_trip_drafts d on d.id=q.draft_id
   join public.provider_services o on o.id=q.provider_service_id join public.guincho_pricing_rules r on r.id=q.pricing_rule_id
   where q.id=a.guincho_quote_id and d.revision=q.input_revision and o.active
   and r.version=q.pricing_rule_version and q.expires_at>now()) then
   raise exception using errcode='22023',message='PRICE_CHANGED'; end if;
  insert into public.payments(id,request_id,client_id,dispatch_kind,service_id,appointment_id,quote_version,
   method,amount,currency,provider_api,external_reference,idempotency_key,status,sync_state)
  values(payment_uuid,s.id,auth.uid(),'GUINCHO_DIRECT',a.service_id,a.id,a.quote_version,p_method,
   a.customer_total_amount,'BRL','SIMULATED','SIMULATED-'||payment_uuid::text,payment_uuid::text,'PENDING','CURRENT') returning * into p;
 elsif p.status='PENDING' and p_status<>'PENDING' then
  update public.payments set status=p_status,settled_amount=case when p_status='APPROVED' then amount else 0 end,
   revision=revision+1,updated_at=now() where id=p.id returning * into p;
 end if;
 select * into p from public.payments where id=p.id;
 return private.payment_snapshot(p);
end; $$;
create function private.snapshot_guincho_service_duration() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if new.guincho_quote_id is not null then
  select duration_minutes into new.service_duration_minutes from public.guincho_quotes where id=new.guincho_quote_id;
 end if;
 return new;
end $$;
revoke all on function private.snapshot_guincho_service_duration() from public,anon,authenticated;
create trigger snapshot_guincho_service_duration before insert on public.appointments
 for each row execute function private.snapshot_guincho_service_duration();
create or replace function public.create_guincho_request(p_quote_id uuid,p_operation_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
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
 values(request_uuid,auth.uid(),o.service_id,'GUINCHO_DIRECT','AWAITING_PAYMENT',d.pickup_latitude,d.pickup_longitude,
 extensions.st_setsrid(extensions.st_makepoint(d.pickup_longitude,d.pickup_latitude),4326)::extensions.geography,25000,now());
 insert into public.appointments(client_id,provider_id,dispatch_search_id,service_id,provider_service_id,
 status,guincho_quote_id,guincho_trip_snapshot,guincho_operation_id,quote_version,client_confirmed_quote_version,
 quoted_price,provider_amount,service_amount,distance_meters,distance_rate_per_km,distance_charge_amount,
 markup_amount,customer_fee_amount,customer_total_amount,payment_amount,payment_test_mode,pricing_currency,
 pricing_rule_version,quote_expires_at,is_free)
 values(auth.uid(),q.provider_id,request_uuid,o.service_id,o.id,'AWAITING_PAYMENT',q.id,trip,p_operation_id,
 q.version,q.version,q.provider_amount,q.provider_amount,q.base_amount,q.distance_meters,q.distance_rate_per_km,
 q.distance_charge_amount,q.markup_amount,q.fee_amount,q.customer_total_amount,q.customer_total_amount,false,'BRL',
 q.pricing_rule_version,q.expires_at,false) returning * into a;
 update public.service_searches set current_appointment_id=a.id,revision=revision+1 where id=request_uuid;
 return jsonb_build_object('outcome','CREATED','snapshot',private.guincho_snapshot(a.id));
exception when unique_violation then
 raise exception using errcode='22023',message='PROVIDER_UNAVAILABLE';
end; $$;

commit;
