begin;
-- Composite row variables and SQL aliases share names in these functions.
-- Resolve qualified SQL alias references as columns, as intended by each query.
do $$
declare routine regprocedure; definition text;
begin
 for routine in select p.oid::regprocedure from pg_proc p
 join pg_namespace n on n.oid=p.pronamespace join pg_language l on l.oid=p.prolang
 where n.nspname in ('public','private') and p.proname like '%guincho%' and l.lanname='plpgsql' loop
  definition:=pg_get_functiondef(routine);
  if definition not like '%#variable_conflict%' then
   definition:=replace(definition,'AS $function$','AS $function$'||chr(10)||'#variable_conflict use_column');
   execute definition;
  end if;
 end loop;
end $$;
commit;
