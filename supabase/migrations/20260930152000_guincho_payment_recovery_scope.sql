begin;
-- Old financial disputes/refunds stay in history and cannot replace a newer operational trip.
do $$
declare definition text;
begin
 definition:=pg_get_functiondef('public.get_recoverable_payment()'::regprocedure);
 definition:=replace(definition, 'p.dispatch_kind<>''PITTEC_AUTO_SEQUENTIAL''',
   'p.dispatch_kind not in (''PITTEC_AUTO_SEQUENTIAL'',''GUINCHO_DIRECT'')');
 execute definition;
end $$;
commit;
