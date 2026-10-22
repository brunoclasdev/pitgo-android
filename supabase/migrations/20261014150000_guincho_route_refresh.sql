begin;
create function public.claim_guincho_route_refresh(p_appointment_id uuid,p_requesting_user_id uuid,p_reason text,p_known_route_version bigint) returns jsonb
language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; r public.guincho_route_snapshots%rowtype; l public.provider_current_locations%rowtype;
 lat double precision; lon double precision; lease uuid; origin jsonb; reuse boolean:=false;
begin
 if p_reason is null or p_reason not in ('INITIAL','RESUME','DEVIATION','RETRY') or p_known_route_version is null or p_known_route_version<0 then
  raise exception using errcode='22023',message='INVALID_REQUEST'; end if;
 perform 1 from public.appointments where id=p_appointment_id for update;
 a:=private.guincho_route_authorize(p_appointment_id,p_requesting_user_id);
 lat:=(a.guincho_trip_snapshot->>'destination_latitude')::double precision;
 lon:=(a.guincho_trip_snapshot->>'destination_longitude')::double precision;
 if lat is null or lon is null or lat not between -90 and 90 or lon not between -180 and 180 then raise exception using errcode='22023',message='APPOINTMENT_NOT_TRACKABLE'; end if;
 select * into l from public.provider_current_locations where appointment_id=a.id;
 if l.appointment_id is null or l.published_at<now()-interval '30 seconds' or l.captured_at<now()-interval '30 seconds' or l.captured_at>now()+interval '10 seconds' then
  return jsonb_build_object('outcome','LOCATION_NOT_READY'); end if;
 if l.accuracy_m<=0 or l.accuracy_m>100 or (p_reason='DEVIATION' and l.accuracy_m>35) then return jsonb_build_object('outcome','LOCATION_UNCERTAIN'); end if;
 insert into public.guincho_route_snapshots(appointment_id,destination_latitude,destination_longitude) values(a.id,lat,lon) on conflict do nothing;
 select * into r from public.guincho_route_snapshots where appointment_id=a.id for update;
 if r.geometry is not null and r.destination_latitude=lat and r.destination_longitude=lon then
  if p_reason='DEVIATION' then
   reuse:=extensions.st_dwithin(extensions.st_setsrid(extensions.st_makepoint(l.longitude,l.latitude),4326)::extensions.geography,
    extensions.st_setsrid(extensions.st_geomfromgeojson(r.geometry::text),4326)::extensions.geography,greatest(50,2*l.accuracy_m));
  else
   reuse:=extensions.st_dwithin(extensions.st_setsrid(extensions.st_makepoint(l.longitude,l.latitude),4326)::extensions.geography,
    extensions.st_setsrid(extensions.st_makepoint(r.origin_longitude,r.origin_latitude),4326)::extensions.geography,50);
  end if;
  if reuse then return private.guincho_route_json(r)||jsonb_build_object('outcome','REUSED','reused',true); end if;
 end if;
 if r.lease_expires_at>now() then return jsonb_build_object('outcome','BUSY','retry_after_ms',500); end if;
 if greatest(r.retry_after_at,r.last_attempt_at+interval '5 seconds')>now() then
  return jsonb_build_object('outcome','RETRY_AFTER','retry_after_ms',greatest(1,ceil(extract(epoch from (greatest(r.retry_after_at,r.last_attempt_at+interval '5 seconds')-now()))*1000)::integer));
 end if;
 lease:=gen_random_uuid();
 origin:=jsonb_build_object('latitude',l.latitude,'longitude',l.longitude,'accuracy_m',l.accuracy_m,'captured_at',l.captured_at,'published_at',l.published_at);
 update public.guincho_route_snapshots set lease_id=lease,lease_expires_at=now()+interval '10 seconds',last_attempt_at=now(),
 lease_context=jsonb_build_object('requesting_user_id',p_requesting_user_id,'origin',origin,'provider_location_revision',l.version,'destination',jsonb_build_object('latitude',lat,'longitude',lon)) where appointment_id=a.id;
 return jsonb_build_object('outcome','CLAIMED','lease_id',lease,'origin',origin,'destination',jsonb_build_object('latitude',lat,'longitude',lon));
end $$;
revoke all on function public.claim_guincho_route_refresh(uuid,uuid,text,bigint) from public,anon,authenticated;
grant execute on function public.claim_guincho_route_refresh(uuid,uuid,text,bigint) to service_role;
commit;
