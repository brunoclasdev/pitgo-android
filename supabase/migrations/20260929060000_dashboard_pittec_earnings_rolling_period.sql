-- List each completed earning in a rolling window based on appointments.created_at.

create function public.dashboard_list_pittec_earnings_for_period(
  p_id uuid,
  p_start timestamptz,
  p_end timestamptz,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table (
  appointment_id uuid,
  created_at timestamptz,
  provider_amount numeric,
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
  select a.id, a.created_at, coalesce(a.provider_amount, 0)::numeric,
    count(*) over ()::bigint
  from public.appointments a
  where a.provider_id = p_id
    and a.status = 'COMPLETED'
    and a.created_at >= p_start
    and a.created_at < p_end
  order by a.created_at desc, a.id desc
  limit least(greatest(p_limit, 1), 100)
  offset greatest(p_offset, 0);
end;
$$;

revoke all on function public.dashboard_list_pittec_earnings_for_period(uuid, timestamptz, timestamptz, integer, integer)
  from public, anon;

grant execute on function public.dashboard_list_pittec_earnings_for_period(uuid, timestamptz, timestamptz, integer, integer)
  to authenticated;

