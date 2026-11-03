begin;
create function public.get_my_guincho_search_resume() returns jsonb language plpgsql security definer set search_path='' as $$
declare c public.guincho_search_contexts%rowtype; d public.guincho_trip_drafts%rowtype; a public.appointments%rowtype;
begin
 if not private.dispatch_is_client() then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
 perform private.expire_guincho_reservations();
 if exists(select 1 from public.appointments where client_id=auth.uid() and guincho_quote_id is null and status in ('PENDING','AWAITING_PAYMENT','AWAITING_CLIENT_CONFIRMATION','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION')) then return jsonb_build_object('outcome','NONE'); end if;
 select * into a from public.appointments where client_id=auth.uid() and guincho_quote_id is not null and status in ('PENDING','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION') order by created_at desc limit 1;
 if a.id is not null then return jsonb_build_object('outcome','ACTIVE_DISPATCH','snapshot',private.guincho_snapshot(a.id)); end if;
 select * into c from public.guincho_search_contexts where client_id=auth.uid() and state='DISCOVERY';
 if c.id is null then return jsonb_build_object('outcome','NONE'); end if;
 select * into d from public.guincho_trip_drafts where id=c.draft_id and client_id=auth.uid();
 if d.id is null then return jsonb_build_object('outcome','NONE'); end if;
 return jsonb_build_object('outcome','RESUMABLE_SEARCH','context_id',c.id,'generation',c.generation,'revision',c.revision,
 'draft',to_jsonb(d),'rejected_appointment_id',c.appointment_id,'excluded_provider_ids',coalesce((select jsonb_agg(provider_id) from public.guincho_search_exclusions where context_id=c.id and generation=c.generation),'[]'::jsonb));
end $$;
revoke all on function public.get_my_guincho_search_resume() from public,anon;
grant execute on function public.get_my_guincho_search_resume() to authenticated;
-- Preserve the latest discovery projection (including rating fields) while adding exclusions.
do $$ declare definition text; begin
 definition:=pg_get_functiondef('private.discover_guincho_providers_before_ratings(uuid,bigint,integer)'::regprocedure);
 if position('where p.active' in definition)=0 then raise exception 'Unexpected discovery contract'; end if;
 definition:=replace(definition,'where p.active','where not exists(select 1 from public.guincho_search_contexts ctx join public.guincho_search_exclusions ex on ex.context_id=ctx.id and ex.generation=ctx.generation where ctx.client_id=d.client_id and ctx.draft_id=d.id and ctx.state<>''CLOSED'' and ex.provider_id=p.id) and p.active');
 execute definition;
end $$;
do $$ declare definition text; begin
 definition:=pg_get_functiondef('private.guincho_snapshot_before_ratings(uuid)'::regprocedure);
 if position('''appointment_id'',a.id,''status'',a.status' in definition)=0 then raise exception 'Unexpected guincho snapshot contract'; end if;
 definition:=replace(definition,'''appointment_id'',a.id,''status'',a.status', '''appointment_id'',a.id,''cancellation_reason'',a.cancellation_reason,''status'',a.status');
 execute definition;
end $$;
commit;
