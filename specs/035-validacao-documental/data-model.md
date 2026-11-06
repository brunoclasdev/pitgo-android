# Data Model: Validação documental

Nomes abaixo são contratos de design para novas migrations, não tabelas já implantadas. UUIDs são gerados no servidor; timestamps usam UTC. Tipos de domínio têm valores fechados, sem textos livres para estados.

## Identidade cadastral

`private.registration_identities`: `user_id` PK/FK Auth, `first_name`, `last_name`, `email`, `phone`, `cpf`, `cnpj`, `created_at`, `updated_at`. Obrigatório para novas contas móveis de ambas as modalidades; não criar para staff do dashboard. Nome e sobrenome não vazios, Documento normalizado e válido: apenas CNPJ para prestador, CPF ou CNPJ para cliente, telefone normalizado, e-mail igual ao Auth. Índices únicos de CPF e CNPJ não nulos e e-mail normalizado. Senha e confirmação nunca entram nesta tabela.

Importar dados pessoais disponíveis das contas anteriores, sem exigir preenchimento retroativo nem inventar sobrenome/CPF. Colunas ausentes em contas legadas podem ser nulas; trigger/RPC exige completude para novas. Conferir conflitos legados antes de habilitar a unicidade. CPF existente também em `profiles` não pode ser alterado por um caminho que ignore essa autoridade. Projeções compatíveis de contato/nome são mantidas pelas mesmas operações transacionais, não escritas independentes do app.

## Modalidades e elegibilidade

- `public.account_roles`: chave composta `(user_id, account_type)`, tipos `CLIENT | PROVIDER`; mantém demais campos existentes. Inserção de modalidade adicional somente por RPC autenticada, sem privilégio de negócio implícito. Manter `providers.account_id` como proprietário, sem assumir `providers.id = auth.uid()`.
- `private.registration_cases`: `id`, `user_id`, `account_type`, `verification_required`, `legacy_exempt_at`, `status`, `revision`, `current_submission_id`, `approved_submission_id`, `activated_at`, `created_at`, `updated_at`; UNIQUE `(user_id, account_type)` e FK composta para membership. `status` é `INCOMPLETE | IN_REVIEW | CORRECTION_REQUESTED | APPROVED`; legado dispensado tem `status` nulo e `legacy_exempt_at` preenchido, sem aprovação documental fictícia.
- CHECK: dispensa e exigência de análise são mutuamente exclusivas; aprovado exige submissão aprovada e data de ativação. Exceção só é criada no corte de implantação para modalidade então existente. Sem case = sem acesso, não dispensa.
- Disponibilidade e `active` operacionais existentes não são substituídos. Capacidade efetiva exige modalidade, aprovação ou dispensa e condições operacionais atuais. `activated_at` documenta concessão documental por modalidade, não pode reativar conta previamente bloqueada por outro motivo.

## Submissão

`private.registration_submissions`: `id`, `case_id`, `version`, `state`, `draft_revision`, `snapshot` de dados pessoais e complementares, `submitted_at`, `decided_at`, `created_at`, `operation_id`. UNIQUE `(case_id, version)`; uma única versão corrente editável/em análise por case. Estado `DRAFT | SUBMITTED | CORRECTION_REQUESTED | APPROVED`. Snapshot validado por modalidade ao submeter, sem senha e sem campos arbitrários aceitos pelo servidor.

Dados complementares seguem modelos atuais: endereço de cliente, empresa/endereço/ofertas de prestador e veículos de atendimento quando aplicáveis. Novos dados são rascunhos até submissão; registros operacionais necessários podem existir inativos, sem publicação ou disponibilidade. Não usar garagem do cliente como autoridade de veículo de atendimento.

Cada veículo declarado no rascunho recebe identificador estável `vehicle_reference_id` e dados obrigatórios da modalidade; o snapshot conserva sua identificação. Quando não houver tabela operacional de veículos do prestador nesta branch, manter lista estruturada validada no rascunho, sem criar módulo de frota. A referência é única dentro da submissão e será usada no documento veicular. Remover veículo de rascunho remove sua exigência apenas antes do envio e nunca altera o histórico.

## Evidência e reserva de upload

`private.registration_evidence`: `id`, `user_id`, `case_id`, `origin_submission_id`, `kind`, `vehicle_reference_id` opcional, `storage_path` UNIQUE, `mime_type`, `byte_size`, `upload_state`, `created_at`, `confirmed_at`, `operation_id`. Representa um único arquivo e sua reserva de origem; titular, case, tipo, veículo e caminho não mudam ao reutilizá-lo. Tipos `CNH_FRONT | CNH_BACK | SELFIE | SELFIE_WITH_DOCUMENT | VEHICLE_DOCUMENT`; estado de upload `RESERVED | RECEIVED`. Substituir uma foto altera somente a seleção no rascunho, nunca marca como inválido o arquivo usado por versão anterior.

`private.registration_submission_evidence`: `submission_id`, `evidence_id`, `kind`, `vehicle_reference_id` opcional. Esta relação seleciona os arquivos de cada versão. FKs e validação transacional exigem mesmo titular/case, tipo e veículo compatíveis entre arquivo e vínculo. UNIQUE `(submission_id, evidence_id)` e índices únicos parciais por slot pessoal ou documento de veículo tratam referência nula corretamente.

- `VEHICLE_DOCUMENT` exige referência a veículo presente no snapshot da submissão de destino; outros tipos proíbem referência veicular. Ao corrigir, preservar IDs de veículos mantidos; veículo novo recebe novo ID e exige outro documento.
- Somente um vínculo selecionado por slot em cada versão. Vínculos podem ser trocados somente no DRAFT; ficam imutáveis junto ao snapshot após submissão.
- Somente `RECEIVED` conta para completude. Reserva não é confirmação. Uma substituição cria novo UUID/caminho; não sobrescreve imagem anterior.
- Bucket privado `registration-documents`, JPEG/PNG, 1 byte a 10 MiB, caminho `{user_id}/{case_id}/{submission_id}/{evidence_id}.{ext}`; path gerado por servidor.
- INSERT no objeto exige reserva pertencente ao titular e submissão DRAFT; confirmar exige existência e metadados corretos e imagem decodificável. UPDATE/DELETE diretos proibidos, inclusive após submissão.
- Leitura autenticada somente titular ou staff ativo autorizado, conferindo vínculos reais. Políticas restritivas específicas impedem que alguma política permissiva já instalada em `storage.objects` exponha este bucket.

Ao reenviar correção, criar nova versão, copiar snapshot corrigível e criar novos vínculos em `registration_submission_evidence` para os mesmos arquivos RECEIVED mantidos. Não duplicar linha de arquivo/caminho, mover objeto, alterar vínculos históricos ou copiar fotos entre cases/modalidades. O caminho conserva a submissão de origem; a leitura e a completude consultam os vínculos da versão de destino, não exigem origem igual ao destino. A troca de um vínculo no novo DRAFT não modifica o anterior. Uma aprovação anterior não é copiada como decisão da nova versão.

## Decisão

`private.registration_reviews`: `id`, `submission_id` UNIQUE, `reviewer_user_id`, `result` (`APPROVED | CORRECTION_REQUESTED`), `checked_evidence_ids`, `correction_items`, `reason`, `decided_at`, `operation_id` UNIQUE por ator. Append-only, sem edição pelo titular ou revisor após commit.

Aprovar exige conjunto exato de IDs de evidências selecionados pelos vínculos obrigatórios da versão submetida conferido explicitamente; correção exige motivo não vazio e itens válidos (campo ou evidência). Snapshot determina quais veículos precisam de documentos. Revisor não é titular e precisa de staff ativo nível MANAGER ou superior na hora da decisão.

Reutilizar `private.dashboard_audit_log` para evento de decisão, com ator, entidade, ação e data, sem fotos, CPF, contato ou texto livre da análise. O histórico privado específico guarda motivos necessários à correção, acessíveis somente ao titular e revisores.

## Transições e atomicidade

| Case exigindo análise | Comando | Próximo estado |
|---|---|---|
| INCOMPLETE | Salvar dados/reservar/confirmar fotos | INCOMPLETE |
| INCOMPLETE | Submeter versão completa | IN_REVIEW |
| IN_REVIEW | Solicitar correção com motivo | CORRECTION_REQUESTED |
| CORRECTION_REQUESTED | Criar/editar nova versão | CORRECTION_REQUESTED |
| CORRECTION_REQUESTED | Reenviar versão completa | IN_REVIEW |
| IN_REVIEW | Conferir tudo e aprovar/ativar | APPROVED |

Todos os comandos mutáveis recebem `operation_id` e `expected_revision`. Lock no case serializa criação de versão e decisão; versão corrente e revisão são verificadas antes de mutação. Mesma chave e mesmo payload retorna resultado anterior; chave reutilizada com outro payload falha. Manter recibo mínimo de operação no domínio cadastral para comandos repetíveis sem armazenar dados pessoais no recibo. Na aprovação, histórico, ponteiro aprovado, estado, ativação documental e auditoria são uma transação. Outras modalidades não são alteradas.

## Migração e índices

1. Diagnosticar unicidade e dependências de `account_roles.user_id`, rotinas e views antes de trocar a chave; adaptar joins e selects escalares para modalidade explícita.
2. Backfill de identidade disponível e cases dispensados apenas para memberships existentes, preservando todos os flags operacionais.
3. Preparar instaladores sem executar backfill ou trocar o trigger antecipadamente. Na migration final `20261105114000_registration_atomic_cutover.sql`, bloquear escrita concorrente de signup/membership nas tabelas relevantes, executar backfill e snapshot legado, trocar o trigger e ativar os guards SQL na mesma transação. Staff continua fora do cadastro móvel. Guards preparados antes do corte ficam condicionados ao marcador privado de ativação; ausência de case após ativação sempre nega acesso.
4. Novas entradas de prestador, inclusive por cliente antigo dispensado, recebem case exigindo análise.
5. Índices de fila `(status, account_type, submitted_at, id)` nas estruturas correspondentes, FK/ownership, submissões por case/versão e evidências por submissão; consultar fila paginada, sem fotos em listagem.

O histórico documental não é alvo do job de limpeza de logs administrativos. Não introduzir nova retenção permanente: aplicar política de dados do produto; limpeza de uploads cancelados deve distinguir órfãos de evidências referenciadas. Nenhuma exclusão automática de histórico nesta feature.

Antes da transação de corte, implantar as Edge Functions com validação JWT e guards compatíveis com o marcador de ativação consultado no servidor a cada operação. Antes do corte usam a autorização anterior; após o commit exigem a nova capacidade. Não armazenar o marcador em cache ou abrir acesso quando a consulta falhar. Após commit, verificar signup concorrente, nenhuma dispensa nova e preservação dos flags legados; qualquer falha aborta a transação ou exige recuperação aditiva mantendo os bloqueios.

## Diagnóstico de preparação

A branch usa account_roles.user_id como PK, sem FKs declaradas para essa coluna nas migrations pesquisadas. O trigger atual faz ON CONFLICT(user_id) em caminhos de transferência: esses consumidores devem passar a especificar modalidade. Perfis têm unicidade de CPF/e-mail; prestadores legados podem não ter CPF. A migration de corte deve abortar em conflito de CPF entre titulares e não mesclar contas. Nenhum banco remoto foi consultado; a verificação de valores existentes ocorre na migration em transação antes de qualquer ativação.

## Mapeamento implementado

`registration_evidence.origin_submission_id` identifica a reserva original; a tabela de vínculos seleciona fotos nas demais versões do mesmo case. Alterar dados de um veículo retira o vínculo documental apenas do rascunho atual e exige novo envio; vínculos históricos permanecem intactos.

`providers.registration_document_hold` distingue a inatividade inicial por análise de uma desativação operacional explícita. Prestador criado durante cadastro pendente fica inativo e indisponível; aprovação retira somente esse bloqueio documental. Uma desativação explícita pelo staff retira o marcador, impedindo reativação por aprovação posterior.

O corte também reconhece providers já vinculados a contas CLIENT antigas, acrescentando membership PROVIDER dispensado sem substituir o membership CLIENT. As projeções operacionais aprovadas usam os dados do snapshot e preservam os bloqueios independentes.
