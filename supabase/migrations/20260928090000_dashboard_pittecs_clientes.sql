-- specs/001-pittec-client-dashboard: dashboard read/write access to the mobile-owned Pittec
-- (providers), Cliente (profiles), Atendimento (appointments) and Pagamento (payments) data.
--
-- None of the existing RLS policies on these tables grant anything to a caller that isn't the
-- row's own client/provider — a dashboard staff account matches neither, so today it would see
-- nothing. Rather than adding many raw-table SELECT/UPDATE grants (which cannot express FR-015's
-- per-field masking), every read/write here goes through a SECURITY DEFINER RPC gated by
-- private.dashboard_access_level(), mirroring the pattern the mobile schema already uses
-- everywhere else (e.g. private.pittec_dispatch_snapshot). See research.md §7, §9.
--
-- dashboard_refund_pagamento mirrors the exact payments UPDATE already used by the trusted
-- public.request_payment_cancellation (Android repo,
-- supabase/migrations/20260915029000_orders_payment_cancellation.sql) for its "mark for refund"
-- path, so the existing background reconciliation worker (work_action='RECONCILE') picks it up
-- and performs the real Mercado Pago refund — this never reimplements payment-provider logic.
-- dashboard_cancel_atendimento only ever transitions PENDING/ACCEPTED/IN_PROGRESS -> CANCELLED;
-- the already-installed private.persist_payment_cancellation trigger (same Android migration)
-- fires on that transition and automatically marks any live APPROVED payment as REFUND_PENDING,
-- so cancelling an appointment already cascades correctly without extra code here.

-- ---------------------------------------------------------------------------------------------
-- Pittecs (public.providers)
-- ---------------------------------------------------------------------------------------------

create or replace function public.dashboard_list_pittecs(
  p_query text default null,
  p_status text default null,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table (
  id uuid,
  name text,
  phone text,
  email text,
  active boolean,
  city text,
  total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  return query
  select provider.id, provider.name, provider.phone, provider.email, provider.active,
    address.city, count(*) over ()::bigint as total_count
  from public.providers provider
  left join public.addresses address on address.provider_id = provider.id
  where (p_query is null or provider.name ilike '%' || p_query || '%')
    and (
      p_status is null
      or (p_status = 'active' and provider.active)
      or (p_status = 'inactive' and not provider.active)
    )
  order by provider.name asc
  limit least(greatest(p_limit, 1), 100)
  offset greatest(p_offset, 0);
end;
$$;
create or replace function public.dashboard_get_pittec_detail(p_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  caller_level text := private.dashboard_access_level();
  provider_row public.providers%rowtype;
  address_row public.addresses%rowtype;
  services_json jsonb;
  active_appointment_id uuid;
  location_row public.provider_current_locations%rowtype;
  masked_cnpj text;
begin
  if caller_level is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  select * into provider_row from public.providers where id = p_id;
  if provider_row.id is null then
    return null;
  end if;

  select * into address_row from public.addresses where provider_id = p_id limit 1;

  select coalesce(jsonb_agg(jsonb_build_object(
      'provider_service_id', ps.id,
      'service_id', ps.service_id,
      'service_name', svc.name,
      'price', ps.price,
      'duration_minutes', ps.duration_minutes,
      'active', ps.active,
      'updated_at', ps.updated_at
    ) order by svc.name), '[]'::jsonb)
  into services_json
  from public.provider_services ps
  join public.services svc on svc.id = ps.service_id
  where ps.provider_id = p_id;

  masked_cnpj := case
    when provider_row.cnpj is null then null
    when caller_level = 'OWNER' then provider_row.cnpj
    else repeat('*', greatest(length(provider_row.cnpj) - 4, 0)) || right(provider_row.cnpj, 4)
  end;

  if caller_level = 'OWNER' then
    select appointment.id into active_appointment_id
    from public.appointments appointment
    where appointment.provider_id = p_id and appointment.status in ('ACCEPTED', 'IN_PROGRESS')
    order by appointment.updated_at desc
    limit 1;

    if active_appointment_id is not null then
      select * into location_row
      from public.provider_current_locations
      where appointment_id = active_appointment_id;
    end if;
  end if;

  return jsonb_build_object(
    'id', provider_row.id,
    'name', provider_row.name,
    'description', provider_row.description,
    'email', provider_row.email,
    'phone', provider_row.phone,
    'cnpj', masked_cnpj,
    'cnpj_masked', caller_level <> 'OWNER',
    'active', provider_row.active,
    'updated_at', provider_row.updated_at,
    'address', case when address_row.id is null then null else jsonb_build_object(
      'street', address_row.street, 'number', address_row.number,
      'neighborhood', address_row.neighborhood, 'city', address_row.city,
      'state', address_row.state, 'zip_code', address_row.zip_code,
      'complement', address_row.complement
    ) end,
    'services', services_json,
    'live_location', case when location_row.appointment_id is null then null else jsonb_build_object(
      'latitude', location_row.latitude, 'longitude', location_row.longitude,
      'captured_at', location_row.captured_at
    ) end
  );
end;
$$;
create or replace function public.dashboard_update_pittec_fields(
  p_id uuid,
  p_name text,
  p_description text,
  p_phone text,
  p_active boolean,
  p_expected_updated_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  exists_row public.providers%rowtype;
  updated_row public.providers%rowtype;
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  select * into exists_row from public.providers where id = p_id;
  if exists_row.id is null then
    return jsonb_build_object('outcome', 'NOT_FOUND');
  end if;

  update public.providers
  set name = btrim(p_name),
    description = nullif(btrim(coalesce(p_description, '')), ''),
    phone = p_phone,
    active = p_active,
    updated_at = now()
  where id = p_id and updated_at = p_expected_updated_at
  returning * into updated_row;

  if updated_row.id is null then
    return jsonb_build_object('outcome', 'CONFLICT');
  end if;

  perform public.record_dashboard_action('PITTEC_UPDATE', 'provider', p_id);
  return jsonb_build_object('outcome', 'OK', 'updated_at', updated_row.updated_at);
end;
$$;
create or replace function public.dashboard_update_provider_service(
  p_provider_service_id uuid,
  p_price numeric,
  p_duration_minutes integer,
  p_expected_updated_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  exists_row public.provider_services%rowtype;
  updated_row public.provider_services%rowtype;
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  select * into exists_row from public.provider_services where id = p_provider_service_id;
  if exists_row.id is null then
    return jsonb_build_object('outcome', 'NOT_FOUND');
  end if;

  update public.provider_services
  set price = p_price, duration_minutes = p_duration_minutes, updated_at = now()
  where id = p_provider_service_id and updated_at = p_expected_updated_at
  returning * into updated_row;

  if updated_row.id is null then
    return jsonb_build_object('outcome', 'CONFLICT');
  end if;

  perform public.record_dashboard_action(
    'PITTEC_SERVICE_UPDATE', 'provider_service', p_provider_service_id
  );
  return jsonb_build_object('outcome', 'OK', 'updated_at', updated_row.updated_at);
end;
$$;
-- ---------------------------------------------------------------------------------------------
-- Clientes (public.profiles, account_type = 'CLIENT')
-- ---------------------------------------------------------------------------------------------

create or replace function public.dashboard_list_clientes(
  p_query text default null,
  p_status text default null,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table (
  id uuid,
  name text,
  phone text,
  email text,
  active boolean,
  city text,
  total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  return query
  select profile.id, profile.name, profile.phone, profile.email, profile.active,
    addr.city, count(*) over ()::bigint as total_count
  from public.profiles profile
  join public.account_roles role
    on role.user_id = profile.id and role.account_type = 'CLIENT'
  left join public.client_addresses addr on addr.client_id = profile.id
  where (p_query is null or profile.name ilike '%' || p_query || '%')
    and (
      p_status is null
      or (p_status = 'active' and profile.active)
      or (p_status = 'inactive' and not profile.active)
    )
  order by profile.name asc nulls last
  limit least(greatest(p_limit, 1), 100)
  offset greatest(p_offset, 0);
end;
$$;
create or replace function public.dashboard_get_cliente_detail(p_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  caller_level text := private.dashboard_access_level();
  profile_row public.profiles%rowtype;
  address_row public.client_addresses%rowtype;
  vehicles_json jsonb;
  doc text;
  masked_doc text;
begin
  if caller_level is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  select * into profile_row from public.profiles where id = p_id;
  if profile_row.id is null then
    return null;
  end if;

  select * into address_row from public.client_addresses where client_id = p_id;

  select coalesce(jsonb_agg(jsonb_build_object(
      'id', v.id, 'brand', v.brand, 'model', v.model, 'plate', v.plate, 'active', v.active
    ) order by v.created_at), '[]'::jsonb)
  into vehicles_json
  from public.vehicles v
  where v.user_id = p_id;

  doc := coalesce(profile_row.cpf, profile_row.cnpj);
  masked_doc := case
    when doc is null then null
    when caller_level = 'OWNER' then doc
    else repeat('*', greatest(length(doc) - 4, 0)) || right(doc, 4)
  end;

  return jsonb_build_object(
    'id', profile_row.id,
    'name', profile_row.name,
    'email', profile_row.email,
    'phone', profile_row.phone,
    'document', masked_doc,
    'document_masked', caller_level <> 'OWNER',
    'active', profile_row.active,
    'updated_at', profile_row.updated_at,
    'address', case when address_row.client_id is null then null else jsonb_build_object(
      'street', address_row.street, 'number', address_row.number,
      'neighborhood', address_row.neighborhood, 'city', address_row.city,
      'state', address_row.state, 'zip_code', address_row.zip_code,
      'complement', address_row.complement
    ) end,
    'vehicles', vehicles_json
  );
end;
$$;
create or replace function public.dashboard_update_cliente_fields(
  p_id uuid,
  p_name text,
  p_phone text,
  p_active boolean,
  p_expected_updated_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  exists_row public.profiles%rowtype;
  updated_row public.profiles%rowtype;
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  select * into exists_row from public.profiles where id = p_id;
  if exists_row.id is null then
    return jsonb_build_object('outcome', 'NOT_FOUND');
  end if;

  update public.profiles
  set name = btrim(p_name), phone = p_phone, active = p_active, updated_at = now()
  where id = p_id and updated_at = p_expected_updated_at
  returning * into updated_row;

  if updated_row.id is null then
    return jsonb_build_object('outcome', 'CONFLICT');
  end if;

  perform public.record_dashboard_action('CLIENTE_UPDATE', 'profile', p_id);
  return jsonb_build_object('outcome', 'OK', 'updated_at', updated_row.updated_at);
end;
$$;
-- ---------------------------------------------------------------------------------------------
-- Atendimentos (public.appointments) & Pagamentos (public.payments)
-- ---------------------------------------------------------------------------------------------

create or replace function public.dashboard_list_atendimentos_for(
  p_entity_type text,
  p_entity_id uuid,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table (
  id uuid,
  status text,
  provider_id uuid,
  provider_name text,
  client_id uuid,
  client_name text,
  quoted_price numeric,
  customer_total_amount numeric,
  created_at timestamptz,
  updated_at timestamptz,
  revision bigint,
  total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;
  if p_entity_type not in ('pittec', 'cliente') then
    raise exception using errcode = '22023', message = 'INVALID_ENTITY_TYPE';
  end if;

  return query
  select a.id, a.status, a.provider_id, provider.name, a.client_id, profile.name,
    a.quoted_price, a.customer_total_amount, a.created_at, a.updated_at, a.revision::bigint,
    count(*) over ()::bigint as total_count
  from public.appointments a
  join public.providers provider on provider.id = a.provider_id
  join public.profiles profile on profile.id = a.client_id
  where (p_entity_type = 'pittec' and a.provider_id = p_entity_id)
     or (p_entity_type = 'cliente' and a.client_id = p_entity_id)
  order by a.created_at desc
  limit least(greatest(p_limit, 1), 100)
  offset greatest(p_offset, 0);
end;
$$;
create or replace function public.dashboard_cancel_atendimento(
  p_id uuid,
  p_expected_revision bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  exists_row public.appointments%rowtype;
  updated_row public.appointments%rowtype;
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  select * into exists_row from public.appointments where id = p_id;
  if exists_row.id is null then
    return jsonb_build_object('outcome', 'NOT_FOUND');
  end if;
  if exists_row.status not in ('PENDING', 'ACCEPTED', 'IN_PROGRESS') then
    return jsonb_build_object('outcome', 'INVALID_STATUS');
  end if;

  update public.appointments
  set status = 'CANCELLED',
    cancellation_reason = 'PAINEL_ADMINISTRATIVO',
    cancelled_at = now(),
    ended_at = now(),
    revision = revision + 1,
    updated_at = now()
  where id = p_id and revision = p_expected_revision
  returning * into updated_row;

  if updated_row.id is null then
    return jsonb_build_object('outcome', 'CONFLICT');
  end if;

  perform public.record_dashboard_action('ATENDIMENTO_CANCEL', 'appointment', p_id);
  return jsonb_build_object('outcome', 'OK', 'revision', updated_row.revision);
end;
$$;
create or replace function public.dashboard_get_pagamento_for(p_appointment_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  payment_row public.payments%rowtype;
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  select * into payment_row from public.payments
  where appointment_id = p_appointment_id
  order by created_at desc
  limit 1;

  if payment_row.id is null then
    return null;
  end if;

  return jsonb_build_object(
    'id', payment_row.id,
    'method', payment_row.method,
    'amount', payment_row.amount,
    'status', payment_row.status,
    'card_brand', payment_row.card_brand,
    'card_last_four_digits', payment_row.card_last_four_digits,
    'revision', payment_row.revision,
    'updated_at', payment_row.updated_at
  );
end;
$$;
create or replace function public.dashboard_refund_pagamento(
  p_id uuid,
  p_expected_revision bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  exists_row public.payments%rowtype;
  updated_row public.payments%rowtype;
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;

  select * into exists_row from public.payments where id = p_id;
  if exists_row.id is null then
    return jsonb_build_object('outcome', 'NOT_FOUND');
  end if;
  if exists_row.status <> 'APPROVED' then
    return jsonb_build_object('outcome', 'INVALID_STATUS');
  end if;

  update public.payments
  set cancel_requested_at = coalesce(cancel_requested_at, now()),
    status = 'REFUND_PENDING',
    next_action_at = now(),
    work_action = 'RECONCILE',
    lease_token = null,
    lease_until = null,
    revision = revision + 1
  where id = p_id and revision = p_expected_revision and status = 'APPROVED'
  returning * into updated_row;

  if updated_row.id is null then
    return jsonb_build_object('outcome', 'CONFLICT');
  end if;

  perform public.record_dashboard_action('PAGAMENTO_REFUND', 'payment', p_id);
  return jsonb_build_object('outcome', 'OK', 'revision', updated_row.revision);
end;
$$;
-- ---------------------------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------------------------

revoke all on function public.dashboard_list_pittecs(text, text, integer, integer)
  from public, anon;
revoke all on function public.dashboard_get_pittec_detail(uuid) from public, anon;
revoke all on function public.dashboard_update_pittec_fields(uuid, text, text, text, boolean, timestamptz)
  from public, anon;
revoke all on function public.dashboard_update_provider_service(uuid, numeric, integer, timestamptz)
  from public, anon;
revoke all on function public.dashboard_list_clientes(text, text, integer, integer)
  from public, anon;
revoke all on function public.dashboard_get_cliente_detail(uuid) from public, anon;
revoke all on function public.dashboard_update_cliente_fields(uuid, text, text, boolean, timestamptz)
  from public, anon;
revoke all on function public.dashboard_list_atendimentos_for(text, uuid, integer, integer)
  from public, anon;
revoke all on function public.dashboard_cancel_atendimento(uuid, bigint) from public, anon;
revoke all on function public.dashboard_get_pagamento_for(uuid) from public, anon;
revoke all on function public.dashboard_refund_pagamento(uuid, bigint) from public, anon;
grant execute on function public.dashboard_list_pittecs(text, text, integer, integer)
  to authenticated;
grant execute on function public.dashboard_get_pittec_detail(uuid) to authenticated;
grant execute on function public.dashboard_update_pittec_fields(uuid, text, text, text, boolean, timestamptz)
  to authenticated;
grant execute on function public.dashboard_update_provider_service(uuid, numeric, integer, timestamptz)
  to authenticated;
grant execute on function public.dashboard_list_clientes(text, text, integer, integer)
  to authenticated;
grant execute on function public.dashboard_get_cliente_detail(uuid) to authenticated;
grant execute on function public.dashboard_update_cliente_fields(uuid, text, text, boolean, timestamptz)
  to authenticated;
grant execute on function public.dashboard_list_atendimentos_for(text, uuid, integer, integer)
  to authenticated;
grant execute on function public.dashboard_cancel_atendimento(uuid, bigint) to authenticated;
grant execute on function public.dashboard_get_pagamento_for(uuid) to authenticated;
grant execute on function public.dashboard_refund_pagamento(uuid, bigint) to authenticated;
