begin;

create function public.list_my_provider_ratings(
  p_before timestamptz default null,
  p_before_id uuid default null,
  p_limit integer default 20
) returns setof jsonb language plpgsql stable security definer set search_path='' as $$
begin
  if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
  if not exists(select 1 from public.providers p where p.account_id=auth.uid()) then
    raise exception using errcode='42501',message='PROVIDER_REQUIRED';
  end if;
  if (p_before is null) <> (p_before_id is null) then
    raise exception using errcode='22023',message='INVALID_CURSOR';
  end if;
  -- The provider is resolved from the session, never from a caller-supplied identifier.
  return query select jsonb_build_object('id',r.id,'rating',r.rating,'comment',r.comment,'created_at',r.created_at)
  from public.service_ratings r join public.providers p on p.id=r.provider_id
  where p.account_id=auth.uid()
    and (p_before is null or (r.created_at,r.id)<(p_before,p_before_id))
  order by r.created_at desc,r.id desc
  limit greatest(1,least(coalesce(p_limit,20),50));
end $$;

create index service_ratings_provider_created_idx on public.service_ratings(provider_id,created_at desc,id desc);
revoke all on function public.list_my_provider_ratings(timestamptz,uuid,integer) from public,anon;
grant execute on function public.list_my_provider_ratings(timestamptz,uuid,integer) to authenticated;

notify pgrst,'reload schema';
commit;
