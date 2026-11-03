begin;

insert into public.guincho_sizes(code,name)
values
 ('LEVE','Leve'),
 ('MEDIO','Médio'),
 ('PESADO','Pesado'),
 ('EXTRAPESADO','Extrapesado'),
 ('ESPECIALIZADO','Especializado')
on conflict (code) do nothing;

commit;
