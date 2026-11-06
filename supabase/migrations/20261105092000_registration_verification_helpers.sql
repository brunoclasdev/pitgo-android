create function private.registration_enabled() returns boolean language sql stable security definer set search_path='' as $$
 select enabled from private.registration_runtime where singleton;
$$;
create function private.can_use_account_mode(p_user_id uuid,p_type text) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.account_roles r
 join private.registration_cases c on c.user_id=r.user_id and c.account_type=r.account_type
 where r.user_id=p_user_id and r.account_type=p_type and (c.status='APPROVED' or not c.verification_required)
 and case when p_type='CLIENT' then exists(select 1 from public.profiles p where p.id=p_user_id and p.active)
 else exists(select 1 from public.providers p where p.account_id=p_user_id and p.active) end);
$$;
create function private.require_registration_access(p_type text) returns void language plpgsql stable security definer set search_path='' as $$
begin
 if not private.registration_enabled() then return; end if;
 if auth.uid() is null or not private.can_use_account_mode(auth.uid(),p_type) then
 raise exception using errcode='42501',message='REGISTRATION_REQUIRED'; end if;
end $$;
-- Server checks are callable by authenticated clients without exposing another user's state.
create function public.assert_my_registration_access(p_account_type text) returns boolean language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null or p_account_type not in ('CLIENT','PROVIDER') then raise exception using errcode='42501',message='AUTH_REQUIRED'; end if;
 perform private.require_registration_access(p_account_type); return true;
end $$;
create function private.registration_case_lock(p_case_id uuid,p_revision bigint) returns private.registration_cases language plpgsql security definer set search_path='' as $$
declare c private.registration_cases;
begin
 select * into c from private.registration_cases where id=p_case_id for update;
 if not found or c.user_id<>auth.uid() then raise exception using errcode='42501',message='FORBIDDEN'; end if;
 if p_revision is null or c.revision is distinct from p_revision then raise exception using errcode='40001',message='REVISION_CONFLICT'; end if;
 return c;
end $$;
-- Serialize per-actor operation keys; check receipt BEFORE expected_revision on retries.
create function private.registration_replay(p_operation uuid,p_command text,p_payload jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare o private.registration_operations;
begin
 if auth.uid() is null or p_operation is null then raise exception using errcode='42501',message='AUTH_REQUIRED'; end if;
 perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text||p_operation::text,0));
 select * into o from private.registration_operations where actor_id=auth.uid() and operation_id=p_operation;
 if found then
 if o.command<>p_command or o.payload<>jsonb_build_object('hash',encode(extensions.digest(p_payload::text,'sha256'),'hex')) then raise exception using errcode='22023',message='OPERATION_KEY_REUSED'; end if;
 if o.result ? 'case_id' and o.result ? 'account_type' then return public.get_my_registration(o.result->>'account_type'); end if;
 return o.result;
 end if; return null;
end $$;
create function private.registration_receipt(p_operation uuid,p_command text,p_payload jsonb,p_result jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
begin
 insert into private.registration_operations values(auth.uid(),p_operation,p_command,jsonb_build_object('hash',encode(extensions.digest(p_payload::text,'sha256'),'hex')),p_result-'identity'-'data'-'evidence'-'reviews'); return p_result;
end $$;
create function private.registration_staff() returns boolean language sql stable security definer set search_path='' as $$
 select coalesce(private.dashboard_access_at_least('MANAGER'),false);
$$;
create function private.registration_readable(p_evidence uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from private.registration_evidence e where e.id=p_evidence and (e.user_id=auth.uid() or private.registration_staff()));
$$;
revoke all on function public.assert_my_registration_access(text) from public,anon;
grant execute on function public.assert_my_registration_access(text) to authenticated;
revoke all on all functions in schema private from public,anon;
