begin;
create function public.cancel_my_guincho_search() returns void
language plpgsql security definer set search_path='' as $$
declare c public.guincho_search_contexts%rowtype;
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 select * into c from public.guincho_search_contexts where client_id=auth.uid() and state<>'CLOSED' for update;
 if c.state='REQUESTED' then raise exception using errcode='22023',message='ACTIVE_REQUEST'; end if;
 update public.guincho_search_contexts set state='CLOSED',revision=revision+1,updated_at=now() where id=c.id;
end $$;
revoke all on function public.cancel_my_guincho_search() from public,anon;
grant execute on function public.cancel_my_guincho_search() to authenticated;
commit;
