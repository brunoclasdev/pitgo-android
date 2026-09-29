-- Keep the values history aligned with the dashboard date filters.
-- Periods are based on appointments.created_at, not ended_at.

create or replace function public.dashboard_get_pittec_earnings(
  p_id uuid,
  p_granularity text,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table (
  period_start date,
  total_amount numeric,
  appointment_count integer,
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
  if p_granularity not in ('year', 'month', 'week', 'day') then
    raise exception using errcode = '22023', message = 'INVALID_GRANULARITY';
  end if;

  return query
  select bucket.period_start, bucket.total_amount, bucket.appointment_count,
    count(*) over ()::bigint as total_count
  from (
    select date_trunc(p_granularity, a.created_at)::date as period_start,
      coalesce(sum(a.provider_amount), 0)::numeric as total_amount,
      count(*)::integer as appointment_count
    from public.appointments a
    where a.provider_id = p_id and a.status = 'COMPLETED'
    group by date_trunc(p_granularity, a.created_at)::date
  ) bucket
  order by bucket.period_start desc
  limit least(greatest(p_limit, 1), 100)
  offset greatest(p_offset, 0);
end;
$$;

