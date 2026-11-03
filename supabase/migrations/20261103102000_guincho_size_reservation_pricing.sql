begin;
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
 perform private.lock_guincho_transport(d.id,q.provider_id);
 if not private.guincho_transport_eligible(d.id,q.provider_id) then raise exception using errcode='22023',message='INCOMPATIBLE_VEHICLE'; end if;
 if q.size_price_id is not null and (q.dispatch_vehicle_id is null or not exists(select 1 from public.providers p join public.provider_vehicles v on v.id=p.dispatch_vehicle_id and v.provider_id=p.id
   join public.vehicles cv on cv.id=d.vehicle_id and cv.user_id=d.client_id
   where p.id=q.provider_id and v.id=q.dispatch_vehicle_id and v.guincho_size_id=q.guincho_size_id and cv.transport_category_id=q.transport_category_id)
  or not exists(select 1 from public.guincho_size_prices where id=q.size_price_id and version=q.size_price_version and size_id=q.guincho_size_id and base_amount=q.base_amount)) then
  raise exception using errcode='22023',message='QUOTE_CONFLICT'; end if;
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
 trip:=jsonb_build_object('client_name',client_name,'vehicle',d.vehicle_snapshot,'dispatch_vehicle_id',q.dispatch_vehicle_id,'guincho_size_id',q.guincho_size_id,
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


create or replace function public.create_guincho_request_before_payment(p_quote_id uuid,p_operation_id uuid) returns jsonb
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
 perform private.lock_guincho_transport(d.id,q.provider_id);
 if not private.guincho_transport_eligible(d.id,q.provider_id) then raise exception using errcode='22023',message='INCOMPATIBLE_VEHICLE'; end if;
 if q.size_price_id is not null and (q.dispatch_vehicle_id is null or not exists(select 1 from public.providers p join public.provider_vehicles v on v.id=p.dispatch_vehicle_id and v.provider_id=p.id
   join public.vehicles cv on cv.id=d.vehicle_id and cv.user_id=d.client_id
   where p.id=q.provider_id and v.id=q.dispatch_vehicle_id and v.guincho_size_id=q.guincho_size_id and cv.transport_category_id=q.transport_category_id)
  or not exists(select 1 from public.guincho_size_prices where id=q.size_price_id and version=q.size_price_version and size_id=q.guincho_size_id and base_amount=q.base_amount)) then
  raise exception using errcode='22023',message='QUOTE_CONFLICT'; end if;
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
 trip:=jsonb_build_object('client_name',client_name,'vehicle',d.vehicle_snapshot,'dispatch_vehicle_id',q.dispatch_vehicle_id,'guincho_size_id',q.guincho_size_id,
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
end; $$;


revoke all on function public.create_guincho_request_before_payment(uuid,uuid) from public,anon;
grant execute on function public.create_guincho_request_before_payment(uuid,uuid) to authenticated;
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
 and r.version=q.pricing_rule_version and q.expires_at>now()
 and (q.size_price_id is null or exists(select 1 from public.guincho_size_prices price where price.id=q.size_price_id and price.version=q.size_price_version and price.size_id=q.guincho_size_id and price.base_amount=q.base_amount))) then
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
   and r.version=q.pricing_rule_version and q.expires_at>now()
 and (q.size_price_id is null or exists(select 1 from public.guincho_size_prices price where price.id=q.size_price_id and price.version=q.size_price_version and price.size_id=q.guincho_size_id and price.base_amount=q.base_amount))) then
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

commit;
