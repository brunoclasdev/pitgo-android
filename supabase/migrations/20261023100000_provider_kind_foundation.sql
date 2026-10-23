begin;
alter table public.providers add column provider_kind text
  check (provider_kind in ('PITTEC','GUINCHO','STORE'));
update public.providers set provider_kind='PITTEC' where location_mode='PITTEC';
update public.providers set provider_kind='GUINCHO' where location_mode='MOBILE';
create index providers_kind_idx on public.providers(provider_kind);
commit;
