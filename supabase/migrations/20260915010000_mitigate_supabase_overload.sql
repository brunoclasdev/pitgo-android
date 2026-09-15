-- Mitigação do relatório de sobrecarga de infra (sessão 2026-09-15, "Health: CRITICAL",
-- CONNECT_TIMEOUT, CPU perto de 100% em picos periódicos). Ver relatorio_sobrecarga_supabase.md
-- entregue ao usuário na mesma sessão para o levantamento completo com evidências de
-- `pg_stat_statements` (33% do tempo de CPU do banco vinha só de `expire_due_mobile_dispatch_requests`
-- a 1s/5s, mais 27% do overhead de bookkeeping do próprio `cron.job_run_details`).
--
-- Essas mudanças foram aplicadas ao vivo via `cron.alter_job`/`cron.schedule` no banco remoto
-- durante a sessão; este arquivo só registra no histórico de migrations.
begin;

-- 5s (migration 20260914060000) ainda era mais frequente que necessário: o polling ativo do
-- app (client/provider, todo lugar usa 5s de fallback) já cobre a maior parte dos casos reais,
-- e `get_my_active_pittec_dispatch()` já expira/avança lazily a cada chamada desses polls. O
-- cron agora serve só de rede de segurança pra sessões sem ninguém olhando (app em background/
-- fechado) — 30s é suficiente pra isso sem gerar carga de fundo constante.
select cron.alter_job(job_id := (select jobid from cron.job where jobname = 'pitgo-expire-mobile-dispatch'), schedule := '30 seconds');
select cron.alter_job(job_id := (select jobid from cron.job where jobname = 'pitgo-advance-pittec-searches'), schedule := '30 seconds');
select cron.alter_job(job_id := (select jobid from cron.job where jobname = 'pitgo-expire-pittec-confirmations'), schedule := '30 seconds');

-- `net._http_response` (extensão pg_net, usada por chamadas HTTP assíncronas de triggers/RPCs)
-- não tinha nenhuma limpeza automática — achamos 2.980 linhas paradas há 11 dias (achado M2 do
-- relatório). Mesmo padrão de limpeza já aplicado em `cron.job_run_details` (migration
-- 20260914060000).
select cron.schedule(
  'cleanup_net_http_response',
  '0 * * * *',
  $$delete from net._http_response where created < now() - interval '1 day'$$
);

commit;
