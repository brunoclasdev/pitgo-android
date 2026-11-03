begin;

insert into public.vehicle_transport_categories(code,name)
values
 ('LEVE','Leve'),
 ('MEDIO','Médio'),
 ('PESADO','Pesado'),
 ('EXTRAPESADO','Extrapesado'),
 ('ESPECIALIZADO','Especializado')
on conflict (code) do nothing;

commit;
