# Cópia de produção para dev/homol — 30/09/2026

**Registro histórico**: a cópia foi feita quando Dev/Homol compartilhavam o destino. Na configuração vigente, somente Dev usa essa cópia; Homol usa diretamente a base de Prod.

## Resultado

Origem: `pitgo-database` (produção). Destino: `pitgo-database-dev` (compartilhado por dev/homol). Os projetos e regiões foram conferidos via `npx supabase projects list`. O destino estava vazio (zero tabelas públicas e usuários). Produção recebeu somente consultas e exportações de leitura.

Foi salvo um backup do destino antes da importação. A cópia ocorreu via ferramentas PostgreSQL locais (`pg_dump`/`psql`), pois Docker não está instalado. Estrutura e catálogos foram restaurados em uma transação, mantendo os usuários internos do Supabase e sem recriar os schemas gerenciados auth/storage.

## Conteúdo copiado

- Estrutura dos schemas public/private, com 20 tabelas públicas, funções, índices, constraints, políticas RLS e grants.
- 20 categorias e 108 serviços, incluindo registros inativos e preservando IDs; comparação por hashes de todas as linhas passou.
- Trigger customizado de criação de usuário em auth, sem copiar usuários.
- Histórico de 78 migrations, para evitar reaplicação de alterações já restauradas.
- Extensões necessárias postgis, pg_net e pg_cron, sem tarefas agendadas.

## Validação

Conferidos 282 colunas, 123 funções da aplicação, 13 políticas RLS, configuração RLS de 20 tabelas, 172 constraints, 83 índices, 21 triggers e 325 grants de tabela. As lacunas físicas deixadas por colunas removidas em produção não são reproduzidas pelo pg_dump; a ordem relativa e definições das colunas são iguais. Um CHECK de coordenadas foi normalizado pelo parser em parênteses redundantes de AND, sem alteração de condição.

O projeto novo tinha permissões automáticas adicionais: foram ajustados 102 objetos. As ACLs de tabelas, sequências, funções e schemas foram comparadas integralmente à origem. A função de plataforma `rls_auto_enable()` preexistente no destino foi preservada.

Confirmados zero usuários, pagamentos, cartões, provedores, atendimentos, secrets Vault e jobs cron no destino. A origem não tinha tabelas publicadas em supabase_realtime; nenhuma publicação adicional foi criada.

## Backup e evidências locais

Diretório protegido: `/private/tmp/pitgo-db-copy-20260930` (acesso apenas ao usuário local).

- `dev-before.dump`: backup anterior à importação.
- `prod-app-schema.sql`, `prod-catalog-data.sql`: exportações da estrutura e catálogo.
- `restore-dev.sql`, `permissions-dev.sql`: comandos aplicados ao destino.
- `verification.json`, `acl-verification.json`: resultados das conferências.

Esses arquivos não foram adicionados ao Git. Copiar o backup para armazenamento privado durável antes de limpar arquivos temporários.

## Integrações pendentes

Esta operação copia estrutura e o catálogo selecionado, não o projeto Supabase inteiro. Edge Functions, configurações de autenticação, Storage, webhooks e secrets não foram copiados nem implantados. Tabelas operacionais e regras de precificação PitTec permanecem sem dados; configurar dados de teste e precificação antes de validar o fluxo integrado de homol. Agendamentos devem ser preparados com destinos de dev/homol após configurar suas integrações. Nenhuma cobrança foi executada.

## Atualização posterior

As integrações descritas como pendentes ao finalizar a cópia foram conferidas e configuradas posteriormente. Estado e evidências: [backend-integrations.md](backend-integrations.md). Resta o registro externo do callback Mercado Pago e a validação operacional financeira.
