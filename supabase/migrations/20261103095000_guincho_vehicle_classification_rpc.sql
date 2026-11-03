begin;
-- Keep the existing field validation and insert transaction; the new argument is additive.
alter function public.create_vehicle(text,text,text,integer,integer,text,text,text,text,text,text,text,integer,boolean) rename to create_vehicle_without_transport_category;
alter function public.create_vehicle_without_transport_category(text,text,text,integer,integer,text,text,text,text,text,text,text,integer,boolean) set schema private;
revoke all on function private.create_vehicle_without_transport_category(text,text,text,integer,integer,text,text,text,text,text,text,text,integer,boolean) from public,anon,authenticated;
create function public.create_vehicle(p_brand text,p_model text,p_version text,p_manufacture_year integer,p_model_year integer,p_plate text,p_renavam text,p_chassis text,p_color text,p_fuel_type text,p_transmission text,p_engine text,p_mileage integer,p_make_primary boolean,p_transport_category_id uuid default null)
returns setof public.vehicles language plpgsql security definer set search_path='' as $$
declare v public.vehicles%rowtype;
begin
 if p_transport_category_id is not null and not exists(select 1 from public.vehicle_transport_categories where id=p_transport_category_id and active) then raise exception using errcode='22023',message='VEHICLE_CATEGORY_REQUIRED'; end if;
 select * into v from private.create_vehicle_without_transport_category(p_brand,p_model,p_version,p_manufacture_year,p_model_year,p_plate,p_renavam,p_chassis,p_color,p_fuel_type,p_transmission,p_engine,p_mileage,p_make_primary);
 update public.vehicles set transport_category_id=p_transport_category_id where id=v.id returning * into v;
 return next v;
end $$;
create function public.set_my_vehicle_transport_category(p_vehicle_id uuid,p_category_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare v public.vehicles%rowtype;
begin
 if auth.uid() is null then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 if not exists(select 1 from public.vehicle_transport_categories where id=p_category_id and active) then raise exception using errcode='22023',message='VEHICLE_CATEGORY_REQUIRED'; end if;
 update public.vehicles set transport_category_id=p_category_id,updated_at=now() where id=p_vehicle_id and user_id=auth.uid() and active returning * into v;
 if v.id is null then raise exception using errcode='42501',message='INVALID_VEHICLE'; end if;
 return to_jsonb(v);
end $$;
create function private.snapshot_guincho_transport_category() returns trigger language plpgsql security definer set search_path='' as $$
declare category uuid;
begin
 select v.transport_category_id into category from public.vehicles v join public.vehicle_transport_categories c on c.id=v.transport_category_id and c.active where v.id=new.vehicle_id and v.user_id=new.client_id and v.active;
 if category is null then raise exception using errcode='22023',message='VEHICLE_CATEGORY_REQUIRED'; end if;
 new.vehicle_snapshot:=new.vehicle_snapshot||jsonb_build_object('transport_category_id',category); return new;
end $$;
create trigger snapshot_guincho_transport_category before insert or update of vehicle_id,vehicle_snapshot on public.guincho_trip_drafts for each row execute function private.snapshot_guincho_transport_category();
revoke all on function private.snapshot_guincho_transport_category() from public,anon,authenticated;
create function private.classify_registered_guincho() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if exists(select 1 from public.providers where id=new.provider_id and provider_kind='GUINCHO') and not exists(select 1 from public.guincho_sizes where id=new.guincho_size_id and active) then raise exception using errcode='22023',message='GUINCHO_SIZE_REQUIRED'; end if;
 if tg_op='UPDATE' then new.revision:=old.revision+1; new.updated_at:=now(); end if;
 return new;
end $$;
create trigger classify_registered_guincho before insert or update on public.provider_vehicles for each row execute function private.classify_registered_guincho();
create function private.validate_dispatch_vehicle() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if new.dispatch_vehicle_id is not null and not exists(select 1 from public.provider_vehicles where id=new.dispatch_vehicle_id and provider_id=new.id and guincho_size_id is not null) then raise exception using errcode='22023',message='INVALID_VEHICLE'; end if;
 return new;
end $$;
create trigger validate_dispatch_vehicle before update of dispatch_vehicle_id on public.providers for each row execute function private.validate_dispatch_vehicle();
-- Extend the current registration body without bypassing existing address/identity validation.
do $$ declare definition text; begin
 definition:=pg_get_functiondef('public.create_provider_registration(jsonb,jsonb,jsonb)'::regprocedure);
 if position('insert into public.provider_vehicles' in definition)=0 then raise exception 'Unexpected registration contract'; end if;
 definition:=replace(definition,'provider_vehicles(provider_id,plate,make,model,color)', 'provider_vehicles(provider_id,plate,make,model,color,guincho_size_id)');
 definition:=replace(definition,'provider_vehicles (provider_id,plate,make,model,color)', 'provider_vehicles(provider_id,plate,make,model,color,guincho_size_id)');
 definition:=replace(definition,'btrim(p_provider->''vehicle''->>''color''))','btrim(p_provider->''vehicle''->>''color''), (p_provider->''vehicle''->>''guincho_size_id'')::uuid)');
 if position('provider_vehicles(provider_id,plate,make,model,color,guincho_size_id)' in definition)=0 or position('(p_provider->''vehicle''->>''guincho_size_id'')::uuid' in definition)=0 then raise exception 'Unexpected registration vehicle insert'; end if;
 execute definition;
end $$;
create function private.select_initial_guincho_vehicle() returns trigger language plpgsql security definer set search_path='' as $$
begin
 update public.providers set dispatch_vehicle_id=new.id where id=new.provider_id and provider_kind='GUINCHO' and dispatch_vehicle_id is null;
 return new;
end $$;
create trigger select_initial_guincho_vehicle after insert on public.provider_vehicles for each row execute function private.select_initial_guincho_vehicle();
create function public.get_my_guincho_vehicles() returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if not private.has_account_type('PROVIDER') then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 return coalesce((select jsonb_agg(to_jsonb(v)||jsonb_build_object('selected',coalesce(p.dispatch_vehicle_id=v.id,false)) order by v.created_at) from public.provider_vehicles v join public.providers p on p.id=v.provider_id where p.account_id=auth.uid() and p.provider_kind='GUINCHO'),'[]'::jsonb);
end $$;
create function public.set_my_guincho_vehicle(p_vehicle_id uuid,p_size_id uuid,p_expected_revision bigint,p_request_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare v public.provider_vehicles%rowtype; o private.guincho_catalog_operations%rowtype; payload jsonb:=jsonb_build_object('vehicle',p_vehicle_id,'size',p_size_id,'revision',p_expected_revision); result jsonb;
begin
 if auth.uid() is null or p_request_id is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,3600));
 select * into o from private.guincho_catalog_operations where request_id=p_request_id;
 if o.request_id is not null then
  if o.actor_id<>auth.uid() or o.payload<>payload then raise exception using errcode='22023',message='IDEMPOTENCY_CONFLICT'; end if;
  return o.result;
 end if;
 select v0.* into v from public.provider_vehicles v0 join public.providers p on p.id=v0.provider_id where v0.id=p_vehicle_id and p.account_id=auth.uid() and p.provider_kind='GUINCHO' for update of v0;
 if v.id is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 if p_expected_revision is null or v.revision<>p_expected_revision then raise exception using errcode='40001',message='CATALOG_CONFLICT'; end if;
 if not exists(select 1 from public.guincho_sizes where id=p_size_id and active) then raise exception using errcode='22023',message='GUINCHO_SIZE_REQUIRED'; end if;
 update public.provider_vehicles set guincho_size_id=p_size_id where id=v.id returning * into v;
 result:=to_jsonb(v); insert into private.guincho_catalog_operations values(p_request_id,auth.uid(),payload,result); return result;
end $$;
create function public.select_my_guincho_dispatch_vehicle(p_vehicle_id uuid,p_request_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.providers%rowtype; o private.guincho_catalog_operations%rowtype; payload jsonb:=jsonb_build_object('select_vehicle',p_vehicle_id); result jsonb;
begin
 if auth.uid() is null or p_request_id is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,3600));
 select * into o from private.guincho_catalog_operations where request_id=p_request_id;
 if o.request_id is not null then
  if o.actor_id<>auth.uid() or o.payload<>payload then raise exception using errcode='22023',message='IDEMPOTENCY_CONFLICT'; end if;
  return o.result;
 end if;
 select * into p from public.providers where account_id=auth.uid() and provider_kind='GUINCHO' for update;
 if p.id is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 if not exists(select 1 from public.provider_vehicles v join public.guincho_sizes s on s.id=v.guincho_size_id and s.active where v.id=p_vehicle_id and v.provider_id=p.id) then raise exception using errcode='22023',message='GUINCHO_SIZE_REQUIRED'; end if;
 update public.providers set dispatch_vehicle_id=p_vehicle_id where id=p.id;
 result:=jsonb_build_object('vehicle_id',p_vehicle_id); insert into private.guincho_catalog_operations values(p_request_id,auth.uid(),payload,result); return result;
end $$;
create function public.register_my_guincho_vehicle(p_request_id uuid,p_plate text,p_make text,p_model text,p_color text,p_size_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare owner_id uuid; v public.provider_vehicles%rowtype; o private.guincho_catalog_operations%rowtype;
 payload jsonb:=jsonb_build_object('kind','REGISTER_VEHICLE','plate',p_plate,'make',p_make,'model',p_model,'color',p_color,'size',p_size_id); result jsonb;
begin
 if auth.uid() is null or p_request_id is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,3600));
 select * into o from private.guincho_catalog_operations where request_id=p_request_id;
 if o.request_id is not null then
  if o.actor_id<>auth.uid() or o.payload<>payload then raise exception using errcode='22023',message='IDEMPOTENCY_CONFLICT'; end if;
  return o.result;
 end if;
 select id into owner_id from public.providers where account_id=auth.uid() and provider_kind='GUINCHO' for update;
 if owner_id is null then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
 if coalesce(regexp_replace(upper(p_plate),'[^A-Z0-9]','','g'),'') !~ '^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$'
 or p_make is null or length(btrim(p_make)) not between 1 and 100 or p_model is null or length(btrim(p_model)) not between 1 and 100
 or p_color is null or length(btrim(p_color)) not between 1 and 60 then raise exception using errcode='22023',message='VEHICLE_REQUIRED'; end if;
 insert into public.provider_vehicles(provider_id,plate,make,model,color,guincho_size_id)
 values(owner_id,regexp_replace(upper(p_plate),'[^A-Z0-9]','','g'),btrim(p_make),btrim(p_model),btrim(p_color),p_size_id) returning * into v;
 result:=to_jsonb(v); insert into private.guincho_catalog_operations values(p_request_id,auth.uid(),payload,result); return result;
end $$;
revoke all on function public.register_my_guincho_vehicle(uuid,text,text,text,text,uuid) from public,anon;
grant execute on function public.register_my_guincho_vehicle(uuid,text,text,text,text,uuid) to authenticated;

revoke all on function private.classify_registered_guincho(),private.validate_dispatch_vehicle(),private.select_initial_guincho_vehicle() from public,anon,authenticated;
revoke all on function public.create_vehicle(text,text,text,integer,integer,text,text,text,text,text,text,text,integer,boolean,uuid),public.set_my_vehicle_transport_category(uuid,uuid),public.get_my_guincho_vehicles(),public.set_my_guincho_vehicle(uuid,uuid,bigint,uuid),public.select_my_guincho_dispatch_vehicle(uuid,uuid) from public,anon;
grant execute on function public.create_vehicle(text,text,text,integer,integer,text,text,text,text,text,text,text,integer,boolean,uuid),public.set_my_vehicle_transport_category(uuid,uuid),public.get_my_guincho_vehicles(),public.set_my_guincho_vehicle(uuid,uuid,bigint,uuid),public.select_my_guincho_dispatch_vehicle(uuid,uuid) to authenticated;
commit;
