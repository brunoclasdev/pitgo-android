begin;
create function private.guincho_admin_retry(p_request_id uuid,p_payload jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare o private.guincho_catalog_operations%rowtype;
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 if p_request_id is null then raise exception using errcode='22023',message='INVALID_CATALOG'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,3600));
 select * into o from private.guincho_catalog_operations where request_id=p_request_id;
 if o.request_id is not null and (o.actor_id<>auth.uid() or o.payload<>p_payload) then raise exception using errcode='22023',message='IDEMPOTENCY_CONFLICT'; end if;
 return o.result;
end $$;
create function public.set_dashboard_vehicle_transport_category(p_request_id uuid,p_category_id uuid,p_expected_revision bigint,p_config jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare r public.vehicle_transport_categories%rowtype; result jsonb; payload jsonb:=jsonb_build_object('kind','CATEGORY','id',p_category_id,'revision',p_expected_revision,'config',p_config);
begin
 result:=private.guincho_admin_retry(p_request_id,payload); if result is not null then return result; end if;
 if p_config is null or (jsonb_typeof(p_config->'name') is distinct from 'string' or length(btrim(p_config->>'name')) not between 1 and 100) or coalesce(p_config->>'code','') !~ '^[A-Z][A-Z0-9_]{1,59}$' or jsonb_typeof(p_config->'active') is distinct from 'boolean' then raise exception using errcode='22023',message='INVALID_CATALOG'; end if;
 if p_category_id is null then
  if p_expected_revision is not null then raise exception using errcode='22023',message='INVALID_CATALOG'; end if;
  insert into public.vehicle_transport_categories(code,name,active) values(p_config->>'code',btrim(p_config->>'name'),(p_config->>'active')::boolean) returning * into r;
 else
  select * into r from public.vehicle_transport_categories where id=p_category_id for update;
  if r.id is null then raise exception using errcode='P0002',message='CATALOG_NOT_FOUND'; end if;
  if p_expected_revision is null or r.revision<>p_expected_revision then raise exception using errcode='40001',message='CATALOG_CONFLICT'; end if;
  if r.code<>p_config->>'code' then raise exception using errcode='22023',message='INVALID_CATALOG'; end if;
  update public.vehicle_transport_categories set name=btrim(p_config->>'name'),active=(p_config->>'active')::boolean,revision=revision+1,updated_at=now() where id=r.id returning * into r;
 end if;
 result:=to_jsonb(r); insert into private.guincho_catalog_operations values(p_request_id,auth.uid(),payload,result);
 perform public.record_dashboard_action('SET_CATEGORY','VEHICLE_TRANSPORT_CATEGORY',r.id); return result;
end $$;
create function public.set_dashboard_guincho_size(p_request_id uuid,p_size_id uuid,p_expected_revision bigint,p_config jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare r public.guincho_sizes%rowtype; ids uuid[]; result jsonb; payload jsonb:=jsonb_build_object('kind','SIZE','id',p_size_id,'revision',p_expected_revision,'config',p_config);
begin
 result:=private.guincho_admin_retry(p_request_id,payload); if result is not null then return result; end if;
 if p_config is null or (jsonb_typeof(p_config->'name') is distinct from 'string' or length(btrim(p_config->>'name')) not between 1 and 100) or coalesce(p_config->>'code','') !~ '^[A-Z][A-Z0-9_]{1,59}$' or jsonb_typeof(p_config->'active') is distinct from 'boolean' or jsonb_typeof(p_config->'vehicle_category_ids') is distinct from 'array' then raise exception using errcode='22023',message='INVALID_CATALOG'; end if;
 select coalesce(array_agg(distinct value::uuid),'{}') into ids from jsonb_array_elements_text(p_config->'vehicle_category_ids');
 if (p_config->>'active')::boolean and cardinality(ids)=0 then raise exception using errcode='22023',message='INVALID_CATALOG'; end if;
 perform 1 from public.vehicle_transport_categories where id=any(ids) and active for share;
 if (select count(*) from public.vehicle_transport_categories where id=any(ids) and active)<>cardinality(ids) then raise exception using errcode='22023',message='INVALID_CATALOG'; end if;
 if p_size_id is null then
  if p_expected_revision is not null then raise exception using errcode='22023',message='INVALID_CATALOG'; end if;
  insert into public.guincho_sizes(code,name,active) values(p_config->>'code',btrim(p_config->>'name'),(p_config->>'active')::boolean) returning * into r;
 else
  select * into r from public.guincho_sizes where id=p_size_id for update;
  if r.id is null then raise exception using errcode='P0002',message='CATALOG_NOT_FOUND'; end if;
  if p_expected_revision is null or r.revision<>p_expected_revision then raise exception using errcode='40001',message='CATALOG_CONFLICT'; end if;
  if r.code<>p_config->>'code' then raise exception using errcode='22023',message='INVALID_CATALOG'; end if;
  update public.guincho_sizes set name=btrim(p_config->>'name'),active=(p_config->>'active')::boolean,revision=revision+1,updated_at=now() where id=r.id returning * into r;
 end if;
 delete from public.guincho_size_compatibility where size_id=r.id;
 insert into public.guincho_size_compatibility select r.id,unnest(ids);
 result:=to_jsonb(r)||jsonb_build_object('vehicle_category_ids',to_jsonb(ids));
 insert into private.guincho_catalog_operations values(p_request_id,auth.uid(),payload,result);
 perform public.record_dashboard_action('SET_SIZE','GUINCHO_SIZE',r.id); return result;
end $$;
create function public.set_dashboard_guincho_size_price(p_request_id uuid,p_size_id uuid,p_expected_version bigint,p_base_amount numeric)
returns jsonb language plpgsql security definer set search_path='' as $$
declare r public.guincho_size_prices%rowtype; result jsonb; payload jsonb:=jsonb_build_object('kind','PRICE','id',p_size_id,'version',p_expected_version,'amount',p_base_amount);
begin
 result:=private.guincho_admin_retry(p_request_id,payload); if result is not null then return result; end if;
 if p_base_amount is null or not(p_base_amount>0 and p_base_amount<=9999999999.99) or p_base_amount<>round(p_base_amount,2) then raise exception using errcode='22023',message='INVALID_PRICING'; end if;
 perform 1 from public.guincho_sizes where id=p_size_id and active for update;
 if not found then raise exception using errcode='P0002',message='CATALOG_NOT_FOUND'; end if;
 select * into r from public.guincho_size_prices where size_id=p_size_id and active for update;
 if r.version is distinct from p_expected_version then raise exception using errcode='40001',message='PRICING_CONFLICT'; end if;
 update public.guincho_size_prices set active=false,effective_until=clock_timestamp() where size_id=p_size_id and active;
 insert into public.guincho_size_prices(size_id,request_id,base_amount,created_by) values(p_size_id,p_request_id,p_base_amount,auth.uid()) returning * into r;
 result:=to_jsonb(r); insert into private.guincho_catalog_operations values(p_request_id,auth.uid(),payload,result);
 perform public.record_dashboard_action('SET_PRICING','GUINCHO_SIZE_PRICE',r.id); return result;
end $$;
create function public.get_dashboard_guincho_catalog() returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 return jsonb_build_object('vehicle_categories',coalesce((select jsonb_agg(to_jsonb(c) order by c.name) from public.vehicle_transport_categories c),'[]'::jsonb),
 'sizes',coalesce((select jsonb_agg(to_jsonb(s)||jsonb_build_object('vehicle_category_ids',coalesce((select jsonb_agg(c.vehicle_category_id) from public.guincho_size_compatibility c where c.size_id=s.id),'[]'::jsonb),'price',(select to_jsonb(p) from public.guincho_size_prices p where p.size_id=s.id and p.active)) order by s.name) from public.guincho_sizes s),'[]'::jsonb));
end $$;
create function public.get_guincho_transport_catalog() returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 return jsonb_build_object('vehicle_categories',coalesce((select jsonb_agg(jsonb_build_object('id',id,'name',name,'code',code) order by name) from public.vehicle_transport_categories where active),'[]'::jsonb),
 'sizes',coalesce((select jsonb_agg(jsonb_build_object('id',s.id,'name',s.name,'code',s.code,'revision',s.revision,'vehicle_category_ids',coalesce((select jsonb_agg(c.vehicle_category_id) from public.guincho_size_compatibility c join public.vehicle_transport_categories v on v.id=c.vehicle_category_id and v.active where c.size_id=s.id),'[]'::jsonb)) order by s.name) from public.guincho_sizes s where active),'[]'::jsonb));
end $$;
revoke all on function private.guincho_admin_retry(uuid,jsonb) from public,anon,authenticated;
revoke all on function public.set_dashboard_vehicle_transport_category(uuid,uuid,bigint,jsonb),public.set_dashboard_guincho_size(uuid,uuid,bigint,jsonb),public.set_dashboard_guincho_size_price(uuid,uuid,bigint,numeric),public.get_dashboard_guincho_catalog(),public.get_guincho_transport_catalog() from public,anon;
grant execute on function public.set_dashboard_vehicle_transport_category(uuid,uuid,bigint,jsonb),public.set_dashboard_guincho_size(uuid,uuid,bigint,jsonb),public.set_dashboard_guincho_size_price(uuid,uuid,bigint,numeric),public.get_dashboard_guincho_catalog(),public.get_guincho_transport_catalog() to authenticated;
commit;
