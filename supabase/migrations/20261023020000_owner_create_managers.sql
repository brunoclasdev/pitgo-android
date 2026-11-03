begin;

-- OWNER can create either lower role; ADMIN can create only MANAGER.
CREATE OR REPLACE FUNCTION public.create_dashboard_staff(p_user_id uuid, p_access_level text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  caller_level text := private.dashboard_access_level();
begin
  if caller_level is null then
    raise exception using errcode = '42501', message = 'STAFF_REQUIRED';
  end if;
  if p_access_level is null or p_access_level not in ('MANAGER', 'ADMIN') then
    raise exception using errcode = '22023', message = 'INVALID_ACCESS_LEVEL';
  end if;
  if not (caller_level = 'OWNER' or (caller_level = 'ADMIN' and p_access_level = 'MANAGER'))
  then
    raise exception using errcode = '42501', message = 'HIERARCHY_VIOLATION';
  end if;

  insert into public.dashboard_staff (user_id, access_level, created_by)
  values (p_user_id, p_access_level, auth.uid());
end;
$function$
;

revoke all on function public.create_dashboard_staff(uuid,text) from public,anon;
grant execute on function public.create_dashboard_staff(uuid,text) to authenticated;

commit;
