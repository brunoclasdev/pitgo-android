begin;
create function public.create_guincho_request(p_quote_id uuid,p_operation_id uuid) returns jsonb
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
 select * into r from public.guincho_pricing_rules where id=q.pricing_rule_id and active
 and effective_from<=now() and (effective_until is null or effective_until>now());
 select ps.* into o from public.provider_services ps join public.services s on s.id=ps.service_id
 join public.service_categories c on c.id=s.category_id
 where ps.id=q.provider_service_id and ps.provider_id=q.provider_id and ps.active and s.active
 and c.active and s.dispatch_kind='MOBILE_SEQUENTIAL';
 if q.expires_at<=now() then raise exception using errcode='22023',message='QUOTE_EXPIRED'; end if;
 if d.revision is distinct from q.input_revision or r.version is distinct from q.pricing_rule_version
 or o.price is distinct from q.base_amount then raise exception using errcode='22023',message='QUOTE_CONFLICT'; end if;
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
revoke all on function public.create_guincho_request(uuid,uuid) from public,anon;
grant execute on function public.create_guincho_request(uuid,uuid) to authenticated;
commit;
