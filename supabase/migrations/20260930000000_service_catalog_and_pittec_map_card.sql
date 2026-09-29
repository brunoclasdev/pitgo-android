-- 026: specialties are BROWSE offers for FIXED providers; implicit services retain IDs.
begin;

with seed(name) as (values ('Borracharia'), ('Mecânica'), ('Auto elétrica'), ('Funilaria e pintura'), ('Lavagem e estética'), ('Guincho'), ('PitTec'))
insert into public.service_categories(name, active)
select name, true from seed
on conflict do nothing;

with seed(category_name, service_name) as (values
('Borracharia', 'Conserto de pneu furado'),
('Borracharia', 'Remendo de pneu'),
('Borracharia', 'Troca de pneu'),
('Borracharia', 'Calibragem de pneus'),
('Borracharia', 'Rodízio de pneus'),
('Borracharia', 'Alinhamento'),
('Borracharia', 'Balanceamento'),
('Borracharia', 'Montagem de pneu'),
('Borracharia', 'Desmontagem de pneu'),
('Borracharia', 'Venda de pneus'),
('Borracharia', 'Venda de rodas'),
('Borracharia', 'Conserto de roda'),
('Borracharia', 'Troca de válvula'),
('Borracharia', 'Reparo de pneu run flat'),
('Borracharia', 'Venda de acessórios para pneus'),
('Mecânica', 'Troca de óleo'),
('Mecânica', 'Troca de filtro'),
('Mecânica', 'Revisão preventiva'),
('Mecânica', 'Diagnóstico do motor'),
('Mecânica', 'Diagnóstico eletrônico'),
('Mecânica', 'Troca de bateria'),
('Mecânica', 'Troca de pastilhas de freio'),
('Mecânica', 'Troca de discos de freio'),
('Mecânica', 'Manutenção de suspensão'),
('Mecânica', 'Troca de amortecedores'),
('Mecânica', 'Manutenção de embreagem'),
('Mecânica', 'Troca de correia'),
('Mecânica', 'Manutenção do sistema de arrefecimento'),
('Mecânica', 'Manutenção do ar-condicionado'),
('Mecânica', 'Inspeção pré-viagem'),
('Auto elétrica', 'Diagnóstico elétrico'),
('Auto elétrica', 'Instalação de bateria'),
('Auto elétrica', 'Recarga de bateria'),
('Auto elétrica', 'Troca de alternador'),
('Auto elétrica', 'Reparo de alternador'),
('Auto elétrica', 'Reparo de motor de partida'),
('Auto elétrica', 'Instalação de faróis'),
('Auto elétrica', 'Reparo de iluminação'),
('Auto elétrica', 'Instalação de alarme'),
('Auto elétrica', 'Instalação de acessórios elétricos'),
('Auto elétrica', 'Instalação de câmera veicular'),
('Auto elétrica', 'Instalação de som automotivo'),
('Auto elétrica', 'Reparo de vidros elétricos'),
('Auto elétrica', 'Reparo de travas elétricas'),
('Auto elétrica', 'Rastreamento de falha elétrica'),
('Funilaria e pintura', 'Reparo de amassado'),
('Funilaria e pintura', 'Martelinho de ouro'),
('Funilaria e pintura', 'Pintura de peça'),
('Funilaria e pintura', 'Pintura parcial'),
('Funilaria e pintura', 'Pintura completa'),
('Funilaria e pintura', 'Polimento técnico'),
('Funilaria e pintura', 'Recuperação de para-choque'),
('Funilaria e pintura', 'Troca de para-choque'),
('Funilaria e pintura', 'Reparo de capô'),
('Funilaria e pintura', 'Reparo de porta'),
('Funilaria e pintura', 'Reparo de paralama'),
('Funilaria e pintura', 'Reparo de teto'),
('Funilaria e pintura', 'Restauração de roda'),
('Funilaria e pintura', 'Aplicação de retoque'),
('Funilaria e pintura', 'Avaliação de colisão'),
('Lavagem e estética', 'Lavagem simples'),
('Lavagem e estética', 'Lavagem completa'),
('Lavagem e estética', 'Lavagem técnica'),
('Lavagem e estética', 'Higienização interna'),
('Lavagem e estética', 'Higienização de ar-condicionado'),
('Lavagem e estética', 'Limpeza de bancos'),
('Lavagem e estética', 'Limpeza de estofados'),
('Lavagem e estética', 'Polimento de pintura'),
('Lavagem e estética', 'Enceramento'),
('Lavagem e estética', 'Cristalização de pintura'),
('Lavagem e estética', 'Vitrificação de pintura'),
('Lavagem e estética', 'Revitalização de plásticos'),
('Lavagem e estética', 'Limpeza de motor'),
('Lavagem e estética', 'Descontaminação de pintura'),
('Lavagem e estética', 'Remoção de chuva ácida'),
('Guincho', 'Guincho urbano'),
('Guincho', 'Reboque de carro'),
('Guincho', 'Reboque de moto'),
('Guincho', 'Transporte de veículo'),
('Guincho', 'Auxílio para pane mecânica'),
('Guincho', 'Auxílio para pane elétrica'),
('Guincho', 'Resgate em garagem'),
('Guincho', 'Resgate em estacionamento'),
('Guincho', 'Remoção após colisão'),
('Guincho', 'Transporte para oficina'),
('Guincho', 'Transporte para concessionária'),
('Guincho', 'Troca de pneu emergencial'),
('Guincho', 'Recarga emergencial de bateria'),
('Guincho', 'Chaveiro automotivo emergencial'),
('Guincho', 'Apoio em estrada'),
('PitTec', 'Diagnóstico automotivo no local'),
('PitTec', 'Avaliação de pane'),
('PitTec', 'Inspeção antes da compra'),
('PitTec', 'Inspeção pré-viagem'),
('PitTec', 'Orientação para manutenção'),
('PitTec', 'Leitura de falhas'),
('PitTec', 'Avaliação de bateria'),
('PitTec', 'Avaliação de pneus'),
('PitTec', 'Avaliação de freios'),
('PitTec', 'Avaliação de suspensão'),
('PitTec', 'Avaliação de sistema elétrico'),
('PitTec', 'Avaliação de superaquecimento'),
('PitTec', 'Avaliação de ruído'),
('PitTec', 'Avaliação após colisão'),
('PitTec', 'Encaminhamento para oficina'))
insert into public.services(category_id, name, active, dispatch_kind)
select category.id, seed.service_name, true, 'BROWSE'
from seed join public.service_categories category on lower(category.name) = lower(seed.category_name)
on conflict do nothing;

create or replace function public.create_provider_registration(
  p_provider jsonb,
  p_address jsonb,
  p_offers jsonb
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  current_user_id uuid := auth.uid();
  new_provider_id uuid := gen_random_uuid();
  proof private.provider_geocode_proofs%rowtype;
  proof_token text := p_address ->> 'verification_token';
  provider_mode text := upper(coalesce(p_provider ->> 'location_mode',''));
  normalized_neighborhood text := btrim(coalesce(
    p_address ->> 'neighborhood', p_address ->> 'neighbornhood', ''
  ));
  offer_count integer;
begin
  if current_user_id is null or not private.has_account_type('PROVIDER') then
    raise exception using errcode = '42501', message = 'PROVIDER_REQUIRED';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(current_user_id::text,9010));
  if exists (select 1 from public.providers where account_id = current_user_id) then
    select count(*) into offer_count
    from public.provider_services as offer
    join public.providers as provider on provider.id = offer.provider_id
    where provider.account_id = current_user_id;
    return jsonb_build_object('outcome','ALREADY_REGISTERED','offer_count',offer_count);
  end if;
  if provider_mode not in ('FIXED','MOBILE','PITTEC') then
    raise exception using errcode = '22023', message = 'INVALID_PROVIDER_MODE';
  end if;
  if proof_token is null or proof_token !~ '^[A-Za-z0-9_-]{43,128}$' then
    raise exception using errcode = '22023', message = 'LOCATION_VERIFICATION_REQUIRED';
  end if;
  select candidate.* into proof
  from private.provider_geocode_proofs as candidate
  where candidate.account_id = current_user_id
    and candidate.token_hash = extensions.digest(proof_token,'sha256')
  for update;
  if proof.id is null or proof.used_at is not null or proof.expires_at <= now()
    or (proof.device_distance_m > 500 and not proof.mismatch_confirmed) then
    raise exception using errcode = '22023', message = 'LOCATION_VERIFICATION_UNAVAILABLE';
  end if;
  if normalized_neighborhood = '' then
    raise exception using errcode = '22023', message = 'INVALID_ADDRESS';
  end if;
  if p_offers is null or jsonb_typeof(p_offers) <> 'array'
    or jsonb_array_length(p_offers) = 0 then
    raise exception using errcode = '22023', message = 'OFFER_REQUIRED';
  end if;
  if (select count(*) <> count(distinct (item ->> 'service_id')::uuid)
      from jsonb_array_elements(p_offers) as item) then
    raise exception using errcode = '22023', message = 'DUPLICATE_SERVICE';
  end if;

  -- Lock both entities through the insert: catalog changes cannot invalidate this save.
  perform category.id from public.service_categories category
    where category.id in (select service.category_id from public.services service
      join jsonb_array_elements(p_offers) item on service.id = (item ->> 'service_id')::uuid)
    order by category.id for share;
  perform service.id from public.services service
    join jsonb_array_elements(p_offers) item on service.id = (item ->> 'service_id')::uuid
    order by service.id for share of service;
  if exists (
    select 1 from jsonb_array_elements(p_offers) item
    left join public.services service on service.id = (item ->> 'service_id')::uuid
    left join public.service_categories category on category.id = service.category_id
    where service.id is null or not service.active or category.id is null or not category.active
  ) then
    raise exception using errcode = 'P0002', message = 'CATALOG_CHANGED';
  end if;
  if provider_mode = 'FIXED' and (
    (select count(distinct service.category_id) from public.services service
      join jsonb_array_elements(p_offers) item on service.id = (item ->> 'service_id')::uuid) <> 1
    or exists (select 1 from public.services service
      join jsonb_array_elements(p_offers) item on service.id = (item ->> 'service_id')::uuid
      where service.dispatch_kind <> 'BROWSE')
  ) then
    raise exception using errcode = '22023', message = 'INVALID_OFFER';
  end if;
  if provider_mode in ('MOBILE','PITTEC') and (
    jsonb_array_length(p_offers) <> 1 or not exists (
      select 1 from public.services service
      where service.id = (p_offers -> 0 ->> 'service_id')::uuid
        and service.dispatch_kind = case provider_mode
          when 'MOBILE' then 'MOBILE_SEQUENTIAL' else 'PITTEC_AUTO_SEQUENTIAL' end
    )
  ) then
    raise exception using errcode = '22023', message = 'INVALID_OFFER';
  end if;

  if exists (
    select 1
    from jsonb_array_elements(p_offers) as item
    left join public.services as service on service.id = (item ->> 'service_id')::uuid
    left join public.service_categories as category on category.id = service.category_id
    where service.id is null or not service.active or category.id is null or not category.active
      or coalesce(item ->> 'duration_minutes','') !~ '^[0-9]+$'
      or (item ->> 'duration_minutes')::integer not between 1 and 10080
      or coalesce(item ->> 'price','') !~ '^[0-9]+(\.[0-9]{1,2})?$'
      or (coalesce((item ->> 'is_free')::boolean,false)
        and (item ->> 'price')::numeric <> 0)
      or (not coalesce((item ->> 'is_free')::boolean,false)
        and (item ->> 'price')::numeric <= 0)
  ) then
    raise exception using errcode = '22023', message = 'INVALID_OFFER';
  end if;

  insert into public.providers(
    id,account_id,name,description,email,phone,cnpj,location_mode,active,available
  ) values (
    new_provider_id,current_user_id,
    btrim(coalesce(p_provider ->> 'name','')),
    nullif(btrim(coalesce(p_provider ->> 'description','')),''),
    lower(btrim(coalesce(p_provider ->> 'email',''))),
    regexp_replace(coalesce(p_provider ->> 'phone',''),'[^0-9]','','g'),
    regexp_replace(upper(coalesce(p_provider ->> 'cnpj','')),'[^0-9A-Z]','','g'),
    provider_mode,true,false
  );
  insert into public.addresses(
    provider_id,street,number,neighborhood,zip_code,city,state,country,complement,
    latitude,longitude,address_point,geocode_place_id,geocoded_at,geocode_expires_at,
    location_verified_at,verification_distance_m,mismatch_confirmed
  ) values (
    new_provider_id,
    btrim(coalesce(p_address ->> 'street','')),
    btrim(coalesce(p_address ->> 'number','')),
    normalized_neighborhood,
    regexp_replace(coalesce(p_address ->> 'zip_code',''),'[^0-9]','','g'),
    btrim(coalesce(p_address ->> 'city','')),
    upper(btrim(coalesce(p_address ->> 'state',''))),'BR',
    nullif(btrim(coalesce(p_address ->> 'complement','')),''),
    proof.latitude,proof.longitude,
    extensions.st_setsrid(extensions.st_makepoint(proof.longitude,proof.latitude),4326)::extensions.geography,
    proof.place_id,proof.geocoded_at,proof.geocoded_at + interval '30 days',now(),
    proof.device_distance_m,proof.mismatch_confirmed
  );
  insert into public.provider_services(
    provider_id,service_id,price,duration_minutes,description,is_free
  ) select
    new_provider_id,(item ->> 'service_id')::uuid,(item ->> 'price')::numeric,
    (item ->> 'duration_minutes')::integer,
    nullif(btrim(coalesce(item ->> 'description','')),''),
    coalesce((item ->> 'is_free')::boolean,false)
  from jsonb_array_elements(p_offers) as item;
  insert into public.provider_categories(provider_id,category_id)
  select distinct new_provider_id, service.category_id
  from jsonb_array_elements(p_offers) as item
  join public.services as service on service.id = (item ->> 'service_id')::uuid;
  update private.provider_geocode_proofs set used_at = now() where id = proof.id;
  return jsonb_build_object(
    'outcome','CREATED','provider_id',new_provider_id,
    'offer_count',jsonb_array_length(p_offers)
  );
end;
$$;

-- Catalog mutations never permit callers to create or modify implicit dispatch services.
create or replace function public.create_service_category(
  p_name text, p_description text default null, p_active boolean default true
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare item public.service_categories%rowtype;
begin
  if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;
  if p_name is null or btrim(p_name) = '' or char_length(btrim(p_name)) > 150
    or char_length(p_description) > 1000 or p_active is null then
    raise exception using errcode = '22023', message = 'INVALID_CATALOG_INPUT';
  end if;
  insert into public.service_categories(name, description, active)
    values (btrim(p_name),nullif(btrim(p_description),''),p_active) returning * into item;
  perform public.record_dashboard_action('CATALOG_CREATE','service_category',item.id);
  return to_jsonb(item);
exception when unique_violation then
  raise exception using errcode = '22023', message = 'DUPLICATE_NAME';
end;
$$;

create or replace function public.create_service(
  p_category_id uuid, p_name text, p_description text default null,
  p_dispatch_kind text default 'BROWSE', p_active boolean default true
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare item public.services%rowtype;
begin
  if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;
  if p_name is null or btrim(p_name) = '' or char_length(btrim(p_name)) > 150
    or char_length(p_description) > 1000 or p_dispatch_kind is distinct from 'BROWSE'
    or p_active is null then
    raise exception using errcode = '22023', message = 'INVALID_CATALOG_INPUT';
  end if;
  perform id from public.service_categories where id = p_category_id and active for share;
  if not found then
    raise exception using errcode = '22023', message = 'CATEGORY_NOT_FOUND';
  end if;
  insert into public.services(category_id,name,description,dispatch_kind,active)
    values (p_category_id,btrim(p_name),nullif(btrim(p_description),''),'BROWSE',p_active)
    returning * into item;
  perform public.record_dashboard_action('CATALOG_CREATE','service',item.id);
  return to_jsonb(item);
exception when unique_violation then
  raise exception using errcode = '22023', message = 'DUPLICATE_NAME';
end;
$$;

create or replace function public.update_service_catalog_item(
  p_entity_type text, p_entity_id uuid, p_name text, p_description text, p_active boolean
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare result jsonb;
begin
  if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;
  if p_entity_type not in ('category','service') or p_entity_type is null
    or p_name is null or btrim(p_name) = '' or char_length(btrim(p_name)) > 150
    or char_length(p_description) > 1000 or p_active is null then
    raise exception using errcode = '22023', message = 'INVALID_CATALOG_INPUT';
  end if;
  if p_entity_type = 'category' then
    perform id from public.service_categories where id = p_entity_id for update;
    if not found then
      raise exception using errcode = '22023', message = 'CATEGORY_NOT_FOUND';
    end if;
    if exists (select 1 from public.services where category_id = p_entity_id and dispatch_kind <> 'BROWSE') then
      raise exception using errcode = '22023', message = 'IMPLICIT_SERVICE_PROTECTED';
    end if;
    update public.service_categories set name = btrim(p_name),
      description = nullif(btrim(p_description),''), active = p_active, updated_at = now()
      where id = p_entity_id returning to_jsonb(service_categories.*) into result;
  else
    perform id from public.services where id = p_entity_id for update;
    if not found then
      raise exception using errcode = '22023', message = 'SERVICE_NOT_FOUND';
    end if;
    if exists (select 1 from public.services where id = p_entity_id and dispatch_kind <> 'BROWSE') then
      raise exception using errcode = '22023', message = 'IMPLICIT_SERVICE_PROTECTED';
    end if;
    update public.services set name = btrim(p_name),
      description = nullif(btrim(p_description),''), active = p_active, updated_at = now()
      where id = p_entity_id returning to_jsonb(services.*) into result;
  end if;
  perform public.record_dashboard_action('CATALOG_UPDATE',p_entity_type,p_entity_id);
  return result;
exception when unique_violation then
  raise exception using errcode = '22023', message = 'DUPLICATE_NAME';
end;
$$;

revoke all on function public.create_service_category(text,text,boolean) from public, anon;
revoke all on function public.create_service(uuid,text,text,text,boolean) from public, anon;
revoke all on function public.update_service_catalog_item(text,uuid,text,text,boolean) from public, anon;
grant execute on function public.create_service_category(text,text,boolean) to authenticated;
grant execute on function public.create_service(uuid,text,text,text,boolean) to authenticated;
grant execute on function public.update_service_catalog_item(text,uuid,text,text,boolean) to authenticated;
commit;
