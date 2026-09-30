begin;
do $$
declare definition text;
begin
 definition:=pg_get_functiondef('public.dashboard_get_guincho_dispute(uuid)'::regprocedure);
 definition:=replace(definition,'''support_required'',p.support_required)',
   '''support_required'',p.support_required_at is not null)');
 execute definition;
end $$;
commit;
