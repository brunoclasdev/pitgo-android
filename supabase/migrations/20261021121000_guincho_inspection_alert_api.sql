begin;
create function private.inspection_alert_pending(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from private.guincho_inspection_review_alerts r join public.guincho_inspection_versions v on v.id=r.version_id
 where r.id=p_id and r.invalidated_at is null and v.state='SUBMITTED' and private.inspection_writable(r.appointment_id)
 and not exists(select 1 from public.guincho_inspection_versions n where n.appointment_id=r.appointment_id and n.version_number>v.version_number));
$$;
create function private.inspection_alert_fanout(p_id uuid) returns void language sql security definer set search_path='' as $$
 insert into private.guincho_inspection_push_deliveries(alert_id,installation_id,environment_id,binding_id,revision)
 select r.id,i.installation_id,i.environment_id,i.binding_id,r.revision from private.guincho_inspection_review_alerts r
 join private.guincho_inspection_push_installations i on i.user_id=r.client_id and i.environment_id=r.environment_id and i.revoked_at is null
 where r.id=p_id on conflict do nothing;
$$;
create function private.inspection_alert_json(p_id uuid) returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('alert_id',r.id,'appointment_id',r.appointment_id,'version_id',r.version_id,'version_number',v.version_number,
 'submitted_at',v.submitted_at,'created_at',r.created_at,'revision',r.revision,'environment_id',r.environment_id)
 from private.guincho_inspection_review_alerts r join public.guincho_inspection_versions v on v.id=r.version_id where r.id=p_id;
$$;
create function public.register_my_guincho_inspection_push(p_installation_id uuid,p_environment_id text,p_fcm_token text,p_firebase_project_id text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare i private.guincho_inspection_push_installations%rowtype; configured_project text; mode text; r record;
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 select decrypted_secret into mode from vault.decrypted_secrets where name='INSPECTION_PUSH_ENVIRONMENT' limit 1;
 select decrypted_secret into configured_project from vault.decrypted_secrets where name='INSPECTION_FIREBASE_PROJECT_ID' limit 1;
 if p_installation_id is null or p_environment_id is null or p_environment_id not in ('dev','homol','prod') or
 ((mode='dev') is distinct from (p_environment_id='dev')) or mode not in ('dev','shared') or mode is null or
 configured_project is null or p_firebase_project_id is distinct from configured_project or
 p_fcm_token is null or char_length(p_fcm_token) not between 1 and 4096 then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 -- Serializes token reassociation as well as retries for one installation.
 perform pg_advisory_xact_lock(hashtextextended(p_fcm_token,0));
 update private.guincho_inspection_push_installations set revoked_at=now() where fcm_token=p_fcm_token
 and revoked_at is null and (installation_id<>p_installation_id or environment_id<>p_environment_id);
 insert into private.guincho_inspection_push_installations as t(installation_id,environment_id,user_id,fcm_token,firebase_project_id)
 values(p_installation_id,p_environment_id,auth.uid(),p_fcm_token,p_firebase_project_id)
 on conflict(installation_id,environment_id) do update set user_id=auth.uid(),fcm_token=excluded.fcm_token,firebase_project_id=excluded.firebase_project_id,
 binding_id=case when t.user_id=auth.uid() and t.fcm_token=excluded.fcm_token and t.revoked_at is null then t.binding_id else gen_random_uuid() end,
 revoked_at=null,updated_at=now() returning * into i;
 for r in select id from private.guincho_inspection_review_alerts where client_id=auth.uid() and environment_id=p_environment_id and private.inspection_alert_pending(id) loop
 perform private.inspection_alert_fanout(r.id); end loop;
 return jsonb_build_object('installation_id',i.installation_id,'binding_id',i.binding_id,'environment_id',i.environment_id);
end $$;
create function public.revoke_my_guincho_inspection_push(p_installation_id uuid,p_environment_id text,p_binding_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 update private.guincho_inspection_push_installations set revoked_at=now() where installation_id=p_installation_id and environment_id=p_environment_id
 and binding_id=p_binding_id and user_id=auth.uid() and revoked_at is null;
 return jsonb_build_object('outcome',case when found then 'REVOKED' else 'EXISTING' end);
end $$;
create function public.list_my_pending_guincho_inspection_approvals(p_environment_id text,p_after_created_at timestamptz default null,p_after_id uuid default null,p_limit integer default 20)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare result jsonb; last_row record; lim integer:=greatest(1,least(coalesce(p_limit,20),100));
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 if (p_after_created_at is null)<>(p_after_id is null) then raise exception using errcode='22023',message='INVALID_INPUT'; end if;
 select coalesce(jsonb_agg(private.inspection_alert_json(x.id) order by x.created_at,x.id),'[]'::jsonb) into result from
 (select id,created_at from private.guincho_inspection_review_alerts where client_id=auth.uid() and environment_id=p_environment_id and private.inspection_alert_pending(id)
 and (p_after_id is null or (created_at,id)>(p_after_created_at,p_after_id)) order by created_at,id limit lim) x;
 if jsonb_array_length(result)=lim then return jsonb_build_object('items',result,'next_cursor',jsonb_build_object('created_at',result->-1->>'created_at','id',result->-1->>'alert_id')); end if;
 return jsonb_build_object('items',result,'next_cursor',null);
end $$;
create function public.get_my_guincho_inspection_alert(p_alert_id uuid,p_environment_id text) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 if not exists(select 1 from private.guincho_inspection_review_alerts where id=p_alert_id and client_id=auth.uid() and environment_id=p_environment_id) then
 return jsonb_build_object('outcome','RESOLVED','alert',null); end if;
 return jsonb_build_object('outcome',case when private.inspection_alert_pending(p_alert_id) then 'PENDING' else 'RESOLVED' end,'alert',private.inspection_alert_json(p_alert_id));
end $$;
revoke all on function private.inspection_alert_pending(uuid),private.inspection_alert_fanout(uuid),private.inspection_alert_json(uuid) from public,anon,authenticated;
revoke all on function public.register_my_guincho_inspection_push(uuid,text,text,text),public.revoke_my_guincho_inspection_push(uuid,text,uuid),
 public.list_my_pending_guincho_inspection_approvals(text,timestamptz,uuid,integer),public.get_my_guincho_inspection_alert(uuid,text) from public,anon;
grant execute on function public.register_my_guincho_inspection_push(uuid,text,text,text),public.revoke_my_guincho_inspection_push(uuid,text,uuid),
 public.list_my_pending_guincho_inspection_approvals(text,timestamptz,uuid,integer),public.get_my_guincho_inspection_alert(uuid,text) to authenticated;
commit;
