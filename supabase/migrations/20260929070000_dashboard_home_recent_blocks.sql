-- Read-only summaries for active staff; leave mobile grants and policies unchanged.
create index if not exists dashboard_providers_updated_idx on public.providers(updated_at desc, id desc);

create index if not exists dashboard_profiles_updated_idx on public.profiles(updated_at desc, id desc);

create index if not exists dashboard_searches_updated_idx on public.service_searches(updated_at desc, id desc);

create function public.dashboard_home_recent_blocks(p_limit integer default 5)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  result jsonb := '{}'::jsonb;
  category text;
  items jsonb;
  row_limit integer := least(greatest(coalesce(p_limit, 5), 1), 5);
begin
  if private.dashboard_access_level() is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;
  foreach category in array array['providers', 'clientes', 'pedidos'] loop
    begin
      if category = 'providers' then
        select coalesce(jsonb_agg(to_jsonb(r) order by r.updated_at desc, r.id desc), '[]'::jsonb) into items
        from (select id, name, active, updated_at from public.providers order by updated_at desc, id desc limit row_limit) r;
      elsif category = 'clientes' then
        select coalesce(jsonb_agg(to_jsonb(r) order by r.updated_at desc, r.id desc), '[]'::jsonb) into items
        from (select id, name, active, updated_at from public.profiles order by updated_at desc, id desc limit row_limit) r;
      else
        select coalesce(jsonb_agg(to_jsonb(r) order by r.updated_at desc, r.id desc), '[]'::jsonb) into items
        from (
          select s.id, s.status, s.client_id, p.name as client_name, svc.name as service_name, s.updated_at
          from (select id, status, client_id, service_id, updated_at from public.service_searches order by updated_at desc, id desc limit row_limit) s
          left join public.profiles p on p.id = s.client_id
          left join public.services svc on svc.id = s.service_id
        ) r;
      end if;
      result := result || jsonb_build_object(category, jsonb_build_object(
        'status', case when jsonb_array_length(items) = 0 then 'empty' else 'success' end,
        'items', items, 'errorMessage', null));
    exception when others then
      -- Do not leak SQL details or turn failure into an empty/successful list.
      result := result || jsonb_build_object(category, jsonb_build_object(
        'status', 'error', 'items', '[]'::jsonb, 'errorMessage', 'Não foi possível carregar este bloco.'));
    end;
  end loop;
  return result;
end;
$$;

revoke all on function public.dashboard_home_recent_blocks(integer) from public, anon;

grant execute on function public.dashboard_home_recent_blocks(integer) to authenticated;

notify pgrst, 'reload schema';

