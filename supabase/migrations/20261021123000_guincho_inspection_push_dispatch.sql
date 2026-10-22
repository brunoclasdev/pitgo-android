begin;
create function public.claim_guincho_inspection_push_deliveries() returns jsonb language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
 with due as (select d.id from private.guincho_inspection_push_deliveries d where
 d.state in ('PENDING','LEASED','FAILED_CONFIGURATION') and d.next_attempt_at<=now() and (d.lease_until is null or d.lease_until<now())
 order by d.next_attempt_at,d.id for update skip locked limit 50), claimed as (
 update private.guincho_inspection_push_deliveries d set state='LEASED',lease_token=gen_random_uuid(),lease_until=now()+interval '60 seconds',attempt_count=attempt_count+1
 from due where d.id=due.id returning d.*)
 select coalesce(jsonb_agg(jsonb_build_object('id',d.id,'lease_token',d.lease_token,'attempt',d.attempt_count,
 'alert_id',r.id,'appointment_id',r.appointment_id,'version_id',r.version_id,'recipient_user_id',r.client_id,
 'environment_id',r.environment_id,'binding_id',i.binding_id,'revision',r.revision,'fcm_token',i.fcm_token,'firebase_project_id',i.firebase_project_id,
 'valid',i.revoked_at is null and i.binding_id=d.binding_id and i.user_id=r.client_id and d.revision=r.revision,
 'pending',private.inspection_alert_pending(r.id))),'[]') into result
 from claimed d join private.guincho_inspection_review_alerts r on r.id=d.alert_id
 join private.guincho_inspection_push_installations i on i.installation_id=d.installation_id and i.environment_id=d.environment_id;
 return result;
end $$;
create function public.get_guincho_inspection_push_delivery(p_id uuid,p_lease_token uuid) returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('alert_id',r.id,'appointment_id',r.appointment_id,'version_id',r.version_id,'recipient_user_id',r.client_id,
 'environment_id',r.environment_id,'binding_id',i.binding_id,'revision',r.revision,'fcm_token',i.fcm_token,
 'valid',i.revoked_at is null and i.binding_id=d.binding_id and i.user_id=r.client_id and d.revision=r.revision,
 'pending',private.inspection_alert_pending(r.id)) from private.guincho_inspection_push_deliveries d
 join private.guincho_inspection_review_alerts r on r.id=d.alert_id
 join private.guincho_inspection_push_installations i on i.installation_id=d.installation_id and i.environment_id=d.environment_id
 where d.id=p_id and d.lease_token=p_lease_token and d.lease_until>now();
$$;
revoke all on function public.get_guincho_inspection_push_delivery(uuid,uuid) from public,anon,authenticated;
grant execute on function public.get_guincho_inspection_push_delivery(uuid,uuid) to service_role;
create function public.ack_guincho_inspection_push_delivery(p_id uuid,p_lease_token uuid,p_obsolete boolean default false) returns void language sql security definer set search_path='' as $$
 update private.guincho_inspection_push_deliveries set state=case when p_obsolete then 'OBSOLETE' else 'ACCEPTED' end,
 accepted_at=case when p_obsolete then null else now() end,lease_token=null,lease_until=null
 where id=p_id and lease_token=p_lease_token and lease_until>now();
$$;
create function public.defer_guincho_inspection_push_delivery(p_id uuid,p_lease_token uuid,p_error text,p_retry_after integer default 60,p_invalid_token boolean default false,p_configuration boolean default false)
returns void language plpgsql security definer set search_path='' as $$
declare d private.guincho_inspection_push_deliveries%rowtype;
begin
 update private.guincho_inspection_push_deliveries set state=case when p_invalid_token then 'OBSOLETE' when p_configuration then 'FAILED_CONFIGURATION' else 'PENDING' end,
 last_error_code=left(p_error,80),next_attempt_at=now()+make_interval(secs=>greatest(coalesce(p_retry_after,60),least(3600,60*power(2,least(attempt_count-1,6))::integer))),
 lease_token=null,lease_until=null where id=p_id and lease_token=p_lease_token and lease_until>now() returning * into d;
 if p_invalid_token and d.id is not null then update private.guincho_inspection_push_installations set revoked_at=now()
 where installation_id=d.installation_id and environment_id=d.environment_id and binding_id=d.binding_id; end if;
end $$;
create function private.run_inspection_push_dispatch() returns void language plpgsql security definer set search_path='' as $$
declare endpoint text; secret text;
begin
 -- Retention cannot delete live pending inspections, including unavailable delivery.
 delete from private.guincho_inspection_review_alerts where invalidated_at<now()-interval '30 days';
 if not exists(select 1 from private.guincho_inspection_push_deliveries where state in ('PENDING','LEASED','FAILED_CONFIGURATION') and next_attempt_at<=now() and (lease_until is null or lease_until<now())) then return; end if;
 select decrypted_secret into endpoint from vault.decrypted_secrets where name='project_url' limit 1;
 select decrypted_secret into secret from vault.decrypted_secrets where name='INSPECTION_PUSH_SECRET' limit 1;
 if endpoint is null or secret is null then return; end if;
 perform net.http_post(url:=rtrim(endpoint,'/')||'/functions/v1/guincho-inspection-push',
 headers:=jsonb_build_object('Content-Type','application/json','x-inspection-push-secret',secret),body:='{}'::jsonb,timeout_milliseconds:=45000);
end $$;
create function private.wake_inspection_push() returns trigger language plpgsql security definer set search_path='' as $$
begin perform private.run_inspection_push_dispatch(); return null; end $$;
create trigger inspection_push_delivery_wake after insert on private.guincho_inspection_push_deliveries for each statement execute function private.wake_inspection_push();
select cron.schedule('pitgo-inspection-push','* * * * *','select private.run_inspection_push_dispatch();');
revoke all on function public.claim_guincho_inspection_push_deliveries(),public.ack_guincho_inspection_push_delivery(uuid,uuid,boolean),public.defer_guincho_inspection_push_delivery(uuid,uuid,text,integer,boolean,boolean) from public,anon,authenticated;
grant execute on function public.claim_guincho_inspection_push_deliveries(),public.ack_guincho_inspection_push_delivery(uuid,uuid,boolean),public.defer_guincho_inspection_push_delivery(uuid,uuid,text,integer,boolean,boolean) to service_role;
revoke all on function private.run_inspection_push_dispatch(),private.wake_inspection_push() from public,anon,authenticated;
commit;
