-- specs/002-pittec-category-earnings: earnings breakdown for a Pittec, by year/month/week/day.
--
-- "Valores que o Pittec já fez" = provider_amount (repasse ao prestador), never
-- customer_total_amount (Clarifications). Only COMPLETED appointments count (FR-007); ended_at
-- anchors the period (research.md §3 — the appointments_end_consistent constraint from feature
-- 001 already guarantees ended_at is set whenever status = 'COMPLETED'). date_trunc('week', ...)
-- is Postgres's native ISO-8601 (Monday-start) week, matching the Clarifications answer exactly.
-- p_granularity is validated against a fixed allow-list before use — never interpolated into
-- dynamic SQL, so passing it as a date_trunc argument carries no injection risk.

create function public.dashboard_get_pittec_earnings(
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
    select date_trunc(p_granularity, a.ended_at)::date as period_start,
      sum(a.provider_amount)::numeric as total_amount,
      count(*)::integer as appointment_count
    from public.appointments a
    where a.provider_id = p_id and a.status = 'COMPLETED'
    group by date_trunc(p_granularity, a.ended_at)::date
  ) bucket
  order by bucket.period_start desc
  limit least(greatest(p_limit, 1), 100)
  offset greatest(p_offset, 0);
end;
$$;
revoke all on function public.dashboard_get_pittec_earnings(uuid, text, integer, integer)
  from public, anon;
grant execute on function public.dashboard_get_pittec_earnings(uuid, text, integer, integer)
  to authenticated;
