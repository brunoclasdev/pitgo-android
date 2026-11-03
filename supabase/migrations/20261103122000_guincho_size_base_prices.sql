begin;

-- Administrative configuration requested by the project owner.
-- Keep previous price versions for quotes already issued.
do $$
declare actor uuid; item record;
begin
 select user_id into actor from public.dashboard_staff
 where active and access_level='OWNER' order by created_at limit 1;
 if actor is null then raise exception 'An active dashboard owner is required to attribute initial pricing'; end if;
 for item in
  select s.id, v.amount from (values
   ('LEVE',200::numeric),('MEDIO',300::numeric),('PESADO',400::numeric),
   ('EXTRAPESADO',500::numeric),('ESPECIALIZADO',600::numeric)
  ) v(code,amount) join public.guincho_sizes s on s.code=v.code and s.active
 loop
  perform 1 from public.guincho_sizes where id=item.id for update;
  if exists(select 1 from public.guincho_size_prices where size_id=item.id and active and base_amount=item.amount) then continue; end if;
  update public.guincho_size_prices set active=false,effective_until=now() where size_id=item.id and active;
  insert into public.guincho_size_prices(size_id,request_id,base_amount,currency,created_by)
  values(item.id,gen_random_uuid(),item.amount,'BRL',actor);
 end loop;
end $$;

commit;
