-- Investigação do relato "demora muito pra acionar notificação" (sessão 2026-09-14, 5 devices
-- físicos: 4 Android + 1 iOS). A busca do cliente ficava presa girando entre candidatos porque o
-- Postgres estava lento demais pra dar conta da própria carga de fundo: um UPDATE trivial por PK
-- numa tabela de ~250 linhas chegou a levar 1min15s (sem lock — `pg_locks` vazio, `pg_stat_activity`
-- sem transação longa; instância pequena, `max_connections=60`, sem sinal de bloat real nas tabelas
-- do dispatch). A causa mais provável: os 3 jobs abaixo rodavam a cada 1 segundo, 24h/dia
-- (`pitgo-expire-mobile-dispatch`, `pitgo-advance-pittec-searches`, `pitgo-expire-pittec-confirmations`),
-- cada execução fazendo SELECT+UPDATE contra as mesmas tabelas do dispatch — carga de fundo
-- desproporcional ao tamanho real do projeto. Some a isso `cron.job_run_details` sem retenção
-- (3 linhas/segundo, sem limpeza, virou grande o bastante pra até um `count(*)` travar — truncada e
-- coberta por um novo job `cleanup_cron_job_run_details`, ver `cron.job` id 7, criado direto no
-- banco nesta mesma sessão).
--
-- Reduz a cadência de 1s pra 5s: o app inteiro já tolera até 5s de atraso em qualquer lugar (todo
-- poll de fallback client/provider — `PitTecViewModel`, `ProviderIncomingRequestViewModel`,
-- `PitTecIncomingRequestViewModel` no iOS — usa 5s), então isso não muda nenhum comportamento
-- perceptível, só corta a carga de fundo em ~5x.
--
-- Aplicado ao vivo no projeto remoto via `cron.alter_job` durante a investigação; este arquivo só
-- registra a mudança no histórico de migrations pra não divergir do que está publicado.
begin;

select cron.alter_job(job_id := (select jobid from cron.job where jobname = 'pitgo-expire-mobile-dispatch'), schedule := '5 seconds');
select cron.alter_job(job_id := (select jobid from cron.job where jobname = 'pitgo-advance-pittec-searches'), schedule := '5 seconds');
select cron.alter_job(job_id := (select jobid from cron.job where jobname = 'pitgo-expire-pittec-confirmations'), schedule := '5 seconds');

-- Log de execução dos cron jobs não tinha nenhuma retenção — cresce sem limite (3 linhas/s antes
-- desta migration). Sem isso, mesmo com a cadência reduzida pra 5s, a tabela voltaria a inchar em
-- questão de semanas.
select cron.schedule(
  'cleanup_cron_job_run_details',
  '0 * * * *',
  $$delete from cron.job_run_details where end_time < now() - interval '1 day'$$
);

commit;
