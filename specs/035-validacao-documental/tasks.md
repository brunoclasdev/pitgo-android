# Tasks: Cadastro em duas etapas e validação documental

**Input**: Design documents from `specs/035-validacao-documental/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/registration-api.md](contracts/registration-api.md), [contracts/access-and-android.md](contracts/access-and-android.md), [quickstart.md](quickstart.md).

**Tests**: Não criar testes automatizados, mocks ou fixtures sem solicitação. As validações indicadas são verificações manuais/build requeridas pelo plano e não novas suítes de teste. Nenhuma validação foi executada durante a geração desta lista.

**Organization**: Tarefas por história, com dependências reais. Todos os caminhos são relativos à raiz. Arquivos SQL/Android novos listados são destinos a criar, não arquivos já existentes. Timestamps de migrations estão reservados em ordem; conferir colisões antes de implementar.

## Format: `[ID] [P?] [Story] Description`

`[P]` indica trabalho em arquivo distinto, executável em paralelo somente após os pré-requisitos citados na seção de dependências. `[USn]` vincula a história da especificação. Não iniciar trabalho de fase posterior com a fundação incompleta.

## Path Conventions

Código Android em `app/src/main/java/com/trustarx/pitgo/`; recursos em `app/src/main/res/`; banco em `supabase/migrations/`; serviços em `supabase/functions/`. Não alterar migrations históricas nem a migration local de guincho fora do escopo.

## Phase 1: Setup (Shared Infrastructure)

**Goal**: Conferir pontos de integração e preparar a implementação sem modificar dependências ou dados remotos.

- [X] T001 Inventariar todas as RPCs, RLS, grants, Edge Functions, ativadores administrativos e consumidores escalares de account_roles; registrar caminhos e modalidade exigida em `specs/035-validacao-documental/contracts/access-and-android.md`, conferindo `supabase/migrations/` e `supabase/functions/`.
- [X] T002 Conferir conflitos de CPF/e-mail, dados pessoais legados, regras complementares efetivas e dependências da chave de account_roles; registrar diagnóstico e sequência de corte em `specs/035-validacao-documental/data-model.md`, preservando `supabase/migrations/20261104000000_restore_guincho_size_homol_amount.sql` fora da feature.

**Checkpoint**: Conferir os resultados da fase antes de avançar.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Goal**: Criar a autoridade de modalidade, identidade e versões que sustenta todas as histórias. Concluir a fase antes das histórias; não implantar um backend parcialmente protegido.

- [X] T003 Criar identidade privada comum e evoluir account_roles para chave composta (user_id, account_type), adaptando FKs, joins e rotinas identificadas no inventário, em `supabase/migrations/20261105090000_registration_identity_memberships.sql`; preservar IDs e propriedade de providers.account_id.
- [X] T004 Criar cases por modalidade, submissões versionadas, arquivos de evidência, vínculos registration_submission_evidence por versão, decisões append-only e recibos mínimos de idempotência em `supabase/migrations/20261105091000_registration_verification_schema.sql`, com FKs, índices, revisão, uma versão corrente e constraints de slots pessoais/veiculares conforme data-model.md.
- [X] T005 Implementar helpers de titularidade, elegibilidade por modalidade, validação de snapshot, lock/revisão e operação idempotente em `supabase/migrations/20261105092000_registration_verification_helpers.sql`; manter has_account_type como membership, não aprovação; usar search_path vazio e grants mínimos.
- [X] T006 Preparar instalador transacional de backfill da identidade, dispensa somente para modalidades existentes e trigger comum com exceção dashboard_staff, sem executar o corte nesta migration em `supabase/migrations/20261105093000_registration_verification_cutover.sql`; exigir CNPJ único para novos prestadores e CPF ou CNPJ único para novos clientes, preservar flags operacionais e nunca dispensar ausência de case. A execução ocorrerá somente na migration final de corte, junto da ativação dos guards; preparar marcador privado e funções instaladoras sem habilitar exigência ou conceder dispensa antecipadamente.
- [X] T007 Implementar get_my_account_context_v2, get_my_registration e start_my_registration_mode com ownership e idempotência em `supabase/migrations/20261105094000_registration_context_api.sql`; manter contexto v1 compatível com modalidade original, sem conversão destrutiva ou promoção implícita.
- [X] T008 [P] Criar enums/modelos imutáveis para case, submissão, evidência, veículo declarado, revisão e erros de domínio em `app/src/main/java/com/trustarx/pitgo/features/registrationverification/model/RegistrationVerificationModels.kt`, refletindo contratos e estados fechados.
- [X] T009 [P] Criar DTOs e parâmetros serializáveis de contexto, rascunho, evidência e decisão em `app/src/main/java/com/trustarx/pitgo/features/registrationverification/data/RegistrationVerificationDtos.kt`, sem senha/confirmacão e sem URL pública persistente.

**Checkpoint**: Conferir os resultados da fase antes de avançar.

---

## Phase 3: User Story 1 — Criar conta e entrar com acesso restrito (Priority: P1) — MVP

**Goal**: Criar conta básica, permitir login/retomada e bloquear negócio até aprovação da modalidade.

**Independent Test**: Criar cliente e prestador com os sete campos, sair/entrar e tentar acesso direto ao marketplace; ambos mantêm sessão restrita. Uma conta legada preserva a situação anterior.

### Implementation

- [X] T010 [US1] Atualizar formulário, modelo e validação da etapa básica em `app/src/main/java/com/trustarx/pitgo/features/signup/model/SignUpModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/signup/model/SignUpValidation.kt` e `app/src/main/java/com/trustarx/pitgo/features/signup/ui/SignUpScreen.kt`: nome, sobrenome, e-mail, telefone, documento (CNPJ para prestador; CPF ou CNPJ para cliente), senha mínima vigente e confirmação, sem dados empresariais antecipados.
- [X] T011 [US1] Unificar envio básico das modalidades e reconhecimento de cadastro restrito válido em `app/src/main/java/com/trustarx/pitgo/features/signup/repository/SignUpRepository.kt` e `app/src/main/java/com/trustarx/pitgo/features/providersignup/repository/ProviderSignUpRepository.kt`; enviar metadados comuns, não tratar perfil pendente como recuperação infinita nem recriar conta após timeout.
- [X] T012 [US1] Integrar etapa básica comum em `app/src/main/java/com/trustarx/pitgo/features/signup/viewmodel/SignUpViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/providersignup/viewmodel/ProviderSignUpViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/providersignup/ui/ProviderSignUpScreen.kt`, preservando intenção de modalidade, feedback e prevenção de duplo envio.
- [X] T013 [US1] Evoluir contexto/DTOs/repositórios de conta para memberships e capacidades por modalidade em `app/src/main/java/com/trustarx/pitgo/core/account/model/AccountContext.kt`, `app/src/main/java/com/trustarx/pitgo/core/account/data/AccountContextDtos.kt`, `app/src/main/java/com/trustarx/pitgo/core/account/repository/AccountContextRepository.kt` e `app/src/main/java/com/trustarx/pitgo/core/account/repository/AccountRepository.kt`; separar pendência documental de inconsistência e bloqueio operacional.
- [X] T014 [US1] Representar sessão autenticada restrita e modalidades coexistentes em `app/src/main/java/com/trustarx/pitgo/core/auth/model/AppSessionState.kt` e `app/src/main/java/com/trustarx/pitgo/core/auth/repository/SessionRepository.kt`; login deve ter sucesso para conta restrita, atualizar capacidades no bootstrap/retomada e não presumir aprovação quando offline.
- [X] T015 [US1] Implementar gate/rotas de cadastro e estado, seleção de modalidade e preservação do cliente aprovado em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/features/session/ui/SessionGateScreen.kt`; impedir restauração indevida de telas/dispatch da modalidade bloqueada.
- [X] T016 [P] [US1] Aplicar capacidade CLIENT a leituras comerciais e mutações de contratação, busca, cotação e pedidos inventariadas em `supabase/migrations/20261105100000_registration_client_business_guards.sql`, incluindo políticas PostgREST e comandos de guincho/PitTec/móvel; preparar ativação pelo marcador privado da transação final; manter operações necessárias ao onboarding autorizadas separadamente.
- [X] T017 [P] [US1] Aplicar capacidade PROVIDER à disponibilidade, publicação de ofertas, candidatos de discovery, aceite e execução inventariados em `supabase/migrations/20261105101000_registration_provider_business_guards.sql`; impedir aparecimento de pendentes nas buscas e preservar condições operacionais existentes e preparar ativação pelo marcador privado da transação final.
- [X] T018 [US1] Adicionar guarda compartilhada com JWT validado em `supabase/functions/_shared/registration-access.ts` e integrá-la antes de efeitos externos às funções de negócio inventariadas em T001, incluindo `supabase/functions/mp-create-payment/index.ts` e `supabase/functions/guincho-quote/index.ts`; proteger save/remove card e demais rotas equivalentes sem alterar autenticação de webhook/reconciliação. Antes do corte, usar autorização legada; após o commit, exigir a nova capacidade consultando o marcador no servidor a cada operação, sem cache nem fallback permissivo.
- [X] T019 [US1] Remover disponibilidade/ativação automática após salvar cadastro em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt` e proteger create_provider_registration/ativadores genéricos do dashboard em `supabase/migrations/20261105102000_registration_activation_guards.sql`; após o corte, somente decisão documental válida pode conceder nova capacidade; preparar os ativadores para a mesma troca atômica.
- [X] T020 [US1] Implementar solicitação por e-mail e confirmação de nova senha pelo Supabase Auth em `app/src/main/java/com/trustarx/pitgo/core/auth/data/SupabaseAuthDataSource.kt` e `app/src/main/java/com/trustarx/pitgo/features/login/repository/PasswordRecoveryRepository.kt`; mensagens sem enumeração de contas, tratamento de link inválido/expirado/reutilizado e rate limit, mantendo aprovação e documentos inalterados.
- [X] T021 [US1] Implementar formulário de recuperação, nova senha e confirmação em `app/src/main/java/com/trustarx/pitgo/features/login/ui/PasswordRecoveryScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/login/viewmodel/PasswordRecoveryViewModel.kt`; integrar entrada/retorno em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, `app/src/main/AndroidManifest.xml` e `supabase/config.toml`, com callbacks permitidos por ambiente, retomada segura e recarga de contexto após troca; sessão temporária não libera negócio.
- [ ] T022 [US1] Validar manualmente signup, duplicidade, login restrito, rotas diretas, guards SQL/Edge, legado e falhas de conexão dos cenários 1, 2, 11 e 13 em `specs/035-validacao-documental/quickstart.md`; registrar evidências sintéticas e limitações reais sem gerar suíte automatizada.

**Checkpoint**: Validar o critério independente acima antes de considerar esta história concluída.

---

## Phase 4: User Story 2 — Completar dados e enviar documentos (Priority: P1)

**Goal**: Persistir complemento e fotos privadas, retomar progresso e submeter uma versão completa para análise.

**Independent Test**: Completar cliente e prestador com dois veículos; cliente exige quatro fotos e prestador seis. Reabrir em outro dispositivo, interromper upload e tentar submeter sem confirmação; somente envio completo entra em análise.

### Implementation

- [X] T023 [US2] Implementar save_my_registration_draft, validação dos dados vigentes por modalidade e IDs estáveis dos veículos de atendimento em `supabase/migrations/20261105103000_registration_draft_commands.sql`; permitir salvamento parcial, impedir edição de envio submetido e reaproveitar identidade inicial.
- [X] T024 [US2] Implementar reserve_my_registration_evidence, substituição por novo caminho e confirmação interna restrita ao servidor em `supabase/migrations/20261105104000_registration_evidence_commands.sql`; conferir titular, versão, revisão, metadados e slot, mantendo reservas idempotentes e objetos anteriores imutáveis.
- [X] T025 [US2] Criar bucket privado registration-documents com JPEG/PNG até 10 MiB e políticas de reserva/titular/staff em `supabase/migrations/20261105105000_registration_evidence_storage.sql`; usar políticas restritivas contra permissões amplas existentes e negar UPDATE/DELETE diretos de objetos.
- [X] T026 [US2] Implementar `supabase/functions/registration-evidence-confirm/index.ts` com autenticação, limite de leitura, verificação de JPEG/PNG decodificável e confirmação transacional interna; rejeitar arquivo corrompido, objeto ausente e reserva alheia sem registrar conteúdo ou credenciais.
- [X] T027 [US2] Implementar submit_my_registration em `supabase/migrations/20261105105500_registration_submission_commands.sql`, validando campos, quatro fotos pessoais e documento por veículo declarado pelos vínculos registration_submission_evidence do mesmo case, congelando snapshot e vínculos de arquivos da versão e passando a IN_REVIEW com lock e revisão esperada.
- [X] T028 [US2] Implementar operações de leitura, salvar rascunho, iniciar modalidade, reservar/upload/confirmar e submeter em `app/src/main/java/com/trustarx/pitgo/features/registrationverification/repository/RegistrationVerificationRepository.kt`; manter operation_id entre retries, upload sem upsert, confirmação explícita e erros acionáveis.
- [X] T029 [P] [US2] Implementar captura em URI privada, orientação/remoção de EXIF, processamento fora da Main Thread e limpeza de temporários em `app/src/main/java/com/trustarx/pitgo/features/registrationverification/data/RegistrationDocumentCaptureFiles.kt`; usar FileProvider existente sem acoplar regras de vistoria ou gravar na galeria.
- [X] T030 [P] [US2] Implementar estrutura visual de complemento, slots pessoais/veiculares, revisão de foto, progresso e mensagens acessíveis em `app/src/main/java/com/trustarx/pitgo/features/registrationverification/ui/RegistrationDocumentsScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/registrationverification/ui/RegistrationCompletionScreen.kt`, usando modelos existentes e callbacks sem acesso direto ao Repository.
- [X] T031 [US2] Implementar carregamento de rascunho, persistência, retomada, uploads sequenciais e submissão em `app/src/main/java/com/trustarx/pitgo/features/registrationverification/viewmodel/RegistrationVerificationViewModel.kt`, com StateFlow imutável, cancelamento por sessão e sem Context; estado local não concede capacidade.
- [X] T032 [US2] Integrar formulários complementares existentes de cliente/prestador ao rascunho em `app/src/main/java/com/trustarx/pitgo/features/clientaddress/viewmodel/ClientAddressViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`, preservando regras atuais e mantendo gravação de onboarding separada de publicação operacional.
- [X] T033 [US2] Registrar dependências, telas e callbacks de captura em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/AndroidManifest.xml`; preparar entrada para status IN_REVIEW sem substituir navegação de cliente aprovado.
- [X] T034 [US2] Excluir documentos locais de backup e limitar compartilhamento de URI em `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` e `app/src/main/res/xml/registration_document_file_paths.xml`, conectando a configuração ao FileProvider; limpar arquivos da conta ao sair em `app/src/main/java/com/trustarx/pitgo/core/auth/repository/SessionRepository.kt`.
- [ ] T035 [US2] Validar manualmente cenários 3–6 e negativas de acesso a imagens do cenário 5 em `specs/035-validacao-documental/quickstart.md`: quatro slots pessoais, um por veículo do prestador, upload interrompido, corrupção, submissão incompleta e retomada sem duplicações.

**Checkpoint**: Validar o critério independente acima antes de considerar esta história concluída.

---

## Phase 5: User Story 3 — Revisar e ativar pelo dashboard (Priority: P1)

**Goal**: Entregar operações administrativas e decisão consistente; construção das telas do dashboard permanece no projeto administrativo.

**Independent Test**: Com submissão pronta, staff autorizado consulta detalhes, confere documentos e aprova; usuário comum/staff inativo não consulta nem decide. Concorrência não duplica ativação e cliente aprovado continua acessível durante análise como prestador.

### Implementation

- [X] T036 [US3] Implementar list_registration_reviews e get_registration_review em `supabase/migrations/20261105110000_registration_review_queries.sql`, com filtros/cursor, limite 20–100, histórico privado e MANAGER ativo ou superior; negar autoanálise e não expor fotos/CPF completo na listagem.
- [X] T037 [US3] Implementar decide_registration_review em `supabase/migrations/20261105111000_registration_review_decisions.sql`, exigindo evidências conferidas, versão corrente e revisão esperada; registrar decisão, aprovação/ativação da modalidade e auditoria numa transação, sem remover bloqueios independentes.
- [X] T038 [US3] Conectar perfil e prestador operacionais ao snapshot aprovado em `supabase/migrations/20261105112000_registration_approved_projection.sql`, publicando apenas dados validados e preservando disponibilidade como escolha operacional; não alterar ativação da outra modalidade nem reativar bloqueio anterior.
- [X] T039 [P] [US3] Documentar exemplos sanitizados de chamada/resultado administrativo e integração externa em `specs/035-validacao-documental/contracts/registration-api.md`, com erros, leitura autenticada de fotos e distinção entre aprovação e active operacional; não criar telas de dashboard neste repositório.
- [ ] T040 [US3] Validar manualmente staff ativo/inativo, não titular, aprovação incompleta, versão antiga e duas decisões concorrentes dos cenários 7 e 9 por RPC/contexto; no cenário 10 validar apenas cliente aprovado com prestador incompleto/em análise e posterior aprovação, sem exigir ainda correção ou tela de acompanhamento em `specs/035-validacao-documental/quickstart.md`, incluindo repetição idempotente da operação vencedora.

**Checkpoint**: Validar o critério independente acima antes de considerar esta história concluída.

---

## Phase 6: User Story 4 — Corrigir pendências e acompanhar resultado (Priority: P2)

**Goal**: Mostrar andamento, motivos e correção na mesma conta, mantendo histórico e acesso de modalidade já aprovada.

**Independent Test**: Devolver um documento ilegível, corrigir/re enviar sem outra conta e aprovar a nova versão; conferir preservação dos dados mantidos e histórico anterior, sem aprovação herdada.

### Implementation

- [X] T041 [US4] Completar decisão CORRECTION_REQUESTED com motivo e itens obrigatórios em `supabase/migrations/20261105111000_registration_review_decisions.sql` e implementar begin_my_registration_correction em `supabase/migrations/20261105113000_registration_correction_commands.sql`; criar nova versão com novos vínculos registration_submission_evidence aos mesmos arquivos RECEIVED mantidos no case, preservando IDs/caminhos de origem e referências estáveis dos veículos; nunca duplicar arquivo, alterar vínculo antigo, mover/apagar objeto histórico ou copiar aprovação.
- [X] T042 [US4] Adicionar consulta de histórico/pendências e comando de abertura de correção em `app/src/main/java/com/trustarx/pitgo/features/registrationverification/repository/RegistrationVerificationRepository.kt`, mantendo identidade e chaves de operação, conferindo versão atual e mapeando conflitos para releitura.
- [X] T043 [P] [US4] Criar tela de análise, aprovação e pendências com atualização manual, sem polling contínuo, em `app/src/main/java/com/trustarx/pitgo/features/registrationverification/ui/RegistrationStatusScreen.kt`, com orientação acionável e acesso a correção/sair; interface sem cores como único indicador.
- [X] T044 [US4] Integrar motivo/itens, nova versão, substituição de fotos e reenvio em `app/src/main/java/com/trustarx/pitgo/features/registrationverification/viewmodel/RegistrationVerificationViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`; impedir edição de versão em análise e preservar navegação de cliente aprovado.
- [ ] T045 [US4] Validar manualmente correção, reenvio, aprovação da versão atual, atualização no próximo login e preservação do cliente dos cenários 8–10 em `specs/035-validacao-documental/quickstart.md`; histórico deve permanecer consultável e conta continuar única; executar integralmente os cenários 8–10 no aplicativo, incluindo acompanhamento antes adiado de T040, e verificar mesmos IDs de arquivo para fotos mantidas e vínculos históricos intactos.

**Checkpoint**: Validar o critério independente acima antes de considerar esta história concluída.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Goal**: Concluir compilação, privacidade, acessibilidade e validação integrada sem ampliar o escopo.

- [X] T046 Consolidar textos, descrições de acessibilidade, mensagens de erro e estados de carregamento/vazio em `app/src/main/res/values/strings.xml` e telas de `app/src/main/java/com/trustarx/pitgo/features/registrationverification/ui/`, conferindo temas claro/escuro, fonte ampliada e dimensões de toque.
- [X] T047 Revisar cobertura final de guards/grants/RLS contra o inventário em `specs/035-validacao-documental/contracts/access-and-android.md`, incluindo contextos v1/v2, service role nas Edge Functions, objetos imutáveis, autoanálise, migração de modalidade legada e logs sem PII; corrigir os caminhos efetivos encontrados.
- [ ] T048 Preparar e validar em ambiente isolado a migration final `supabase/migrations/20261105114000_registration_atomic_cutover.sql`, executando instaladores preparados, backfill, snapshot legado, troca de trigger e ativação dos guards SQL numa transação com serialização de signup/membership; documentar em `specs/035-validacao-documental/quickstart.md` a pré-condição de Edge Functions compatíveis já implantadas, verificação concorrente de que novas modalidades não recebem dispensa, falha/rollback sem ativação parcial e recuperação aditiva mantendo bloqueios. Não aplicar corte em ambiente compartilhado nesta tarefa.
- [ ] T049 Executar `./gradlew :app:assembleDevDebug :app:assembleHomolDebug`, conferir função nova com ferramenta Deno compatível e aplicar somente migrations da feature em ambiente isolado; registrar resultados e falhas reais em `specs/035-validacao-documental/quickstart.md`, sem aplicar a migration local alheia ou alterar produção.
- [ ] T050 Executar roteiro integrado de `specs/035-validacao-documental/quickstart.md` incluindo repetição/concorrência, webhook/reconciliação, dispensa legada e cliente/prestador independentes; registrar os resultados, deixando pendências externas explícitas em vez de marcar validação não executada como concluída.
- [ ] T051 Medir metas de consulta e rodada de cadastro definidas em `specs/035-validacao-documental/plan.md`/`specs/035-validacao-documental/quickstart.md` quando o ambiente e participantes estiverem disponíveis; registrar amostra e resultados reais, sem gerar fixtures/suítes automatizadas ou afirmar sucesso sem medição.

**Checkpoint**: Conferir os resultados da fase antes de avançar.

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Setup T001–T002
  └─ Foundation T003–T009
       └─ US1 T010–T022 (MVP restrito)
            └─ US2 T023–T035 (submissão documental)
                 └─ US3 T036–T040 (revisão/ativação)
                      └─ US4 T041–T045 (correção/retomada)
                           └─ Polish T046–T051
```

As histórias compartilham um fluxo de estados; não são entregas totalmente desacopladas. Cada uma tem critério independente verificável com seus pré-requisitos reais: US1 não exige revisão; US2 não exige aprovação; US3 precisa de uma submissão de US2; US4 precisa de uma decisão administrativa de US3. Usar contas sintéticas preparadas manualmente para demonstrar os estados iniciais, sem criar ferramentas de teste novas.

### Within Each User Story

- Fundação: T003 → T004 → T005 → T006 → T007; T008 e T009 podem seguir em paralelo após T007. Antes do corte, fechar diagnóstico de T002 e verificar a definição mais recente do trigger, incluindo staff. T006 somente prepara instaladores; a troca de autoridade, snapshot legado e trigger será executada na transação final de T048, após todas as proteções estarem prontas.
- US1: T010 → T011 → T012 e T013 → T014 → T015. T016 e T017 podem seguir em paralelo após T015; T018 vem após essas guardas para usar sua semântica estável; T019 conclui os caminhos de ativação; T020 → T021 concluem recuperação de senha antes de T022 validar o conjunto. Modelos de sessão novos precisam ser integrados em todos os consumidores para o build, não somente nos arquivos exemplificados.
- US2: T023 → T024 → T025 → T026 → T027 → T028; T029 e T030 podem seguir em paralelo após T022, usando T008/T009. T031 depende de T028/T029/T030; T032 integra os formulários e T033 integra navegação/container. T034 fecha FileProvider, backup e logout antes de T035.
- US3: T036 → T037 → T038 → T040; T039 pode seguir em paralelo a T036 depois de T035, com nomes e formatos já definidos. Atualizar seus exemplos se a implementação ajustar o contrato. T040 requer os dois ramos concluídos.
- US4: T041 → T042; T043 pode seguir em paralelo a T041 após T040. T044 requer T042/T043, depois T045 valida a história.
- Finalização: T046 → T047 → T048 → T049 → T050 → T051. Build e validações externas não podem ser marcados como aprovados sem execução; registrar impedimentos concretos quando existirem.

### Deployment Dependencies

Os checkpoints permitem implementação/demonstração incremental, não autorizam implantar migrations parcialmente em ambiente compartilhado. T006 prepara instaladores sem executá-los. T048 define a única ativação: implantar primeiro Edge Functions compatíveis com o marcador consultado no servidor, então executar backfill, snapshot legado, troca do trigger e ativação de guards SQL na transação final 20261105114000_registration_atomic_cutover.sql, serializando signup/membership. Falha na consulta do marcador nega a operação, sem cache ou fallback permissivo. Validar cada incremento em banco isolado com essa sequência protegida, incluindo somente as partes implementadas; em ambiente compartilhado aguardar todos os guards e o pacote completo. Nenhuma janela pode liberar conta nova por ausência de case ou autorizar prestador pendente. Não deixar um corte publicado antes das proteções de negócio. Preparar o procedimento de recuperação aditiva mantendo guards. Aplicação remota/publicação não é parte desta geração de tarefas.

### Parallel Opportunities and Examples

| História | Exemplo de execução em paralelo | Pré-requisito comum |
|---|---|---|
| US1 | T016: guards CLIENT em migration própria; T017: guards PROVIDER em migration própria. | T015 concluída e semântica de capacidade definida em T005. Não editar simultaneamente funções SQL compartilhadas; atribuir ajustes comuns a T005/T019. |
| US2 | T029: arquivos privados de captura; T030: telas de complemento/documentos. | T022 e modelos/DTOs T008–T009 concluídos; telas recebem callbacks, sem depender da implementação de captura. |
| US3 | T036: RPCs de consulta administrativa; T039: exemplos do contrato externo. | T035 concluída e contrato base estável. |
| US4 | T041: comandos de correção; T043: apresentação de estado/pendências. | T040 concluída e modelos/contratos estáveis; integrar juntos em T044. |

T008/T009 também são paralelizáveis na fundação. A marcação não autoriza delegação automática; descreve divisão possível do trabalho. Tarefas que alteram o mesmo Repository, ViewModel, NavHost ou migration devem seguir sequencialmente, mesmo em histórias diferentes.

## Requirements Coverage

| Requisitos | Tarefas principais |
|---|---|
| FR-001–FR-003: dados básicos, validação e criação | T003, T006, T010–T012 |
| FR-004–FR-005: login restrito, recuperação e bloqueio definitivo | T005, T007, T013–T022, T020, T021 |
| FR-006: complemento por modalidade | T023, T030–T033 |
| FR-007–FR-009: quatro fotos, retomada e submissão | T024–T031, T034–T035 |
| FR-010–FR-012: estados, transições e persistência | T004–T007, T023–T027, T037, T041 |
| FR-013–FR-015: dashboard, análise e ativação | T019, T036–T040 |
| FR-016–FR-019: histórico, privacidade, correção e concorrência | T004–T005, T024–T028, T034, T037, T041–T045, T047 |
| FR-020: preservação de contas existentes e corte atômico | T002–T007, T022, T048, T050 |
| FR-021: documento por veículo de atendimento | T004, T023–T027, T030, T035, T037 |
| FR-022: cliente aprovado com prestador em análise | T003, T005, T007, T013–T017, T033, T038, T040, T044–T045 |
| SC-001–SC-008: resultados e aceite | T022, T035, T040, T045, T049–T051 |

## Implementation Strategy

### MVP First

Concluir Setup, Fundação e US1 (T001–T022). Demonstrar criação básica e login restrito para as duas modalidades, com bloqueio comprovado no servidor e legado preservado. Esse é o MVP do cadastro inicial; não constitui entrega completa da feature nem libera usuários novos para o marketplace.

### Incremental Delivery

Acrescentar US2 para receber envios completos, US3 para revisão/ativação e US4 para corrigir documentos recusados. Finalizar verificações compartilhadas e integração externa. Não encerrar a implementação no MVP se o comando solicitado abranger todas as tarefas. As telas administrativas ficam fora deste repositório, mas dados, permissões e operações precisam estar concretos e validáveis aqui.

### Notes

Não adicionar dependências sem necessidade comprovada; preservar MVVM e DI atual. Não transformar esta lista em solicitação de testes automatizados ou publicação. Não alterar a migration local de guincho alheia à feature. Marcar uma tarefa concluída somente quando seu resultado existir e as verificações correspondentes tiverem sido realizadas. O checklist de qualidade da especificação não representa tarefas implementadas.

## Registro de execução

Implementação escrita para os itens marcados, com build dev/homol e verificação de tipos das funções modificadas. A primeira etapa comum está em `features/signup/ui/BasicRegistrationScreen.kt` e `features/signup/viewmodel/BasicRegistrationViewModel.kt`, usada pelas duas rotas; esse desenho substitui a duplicação de campos prevista inicialmente nos formulários antigos. `sync_my_registration_complement` integra os formulários atuais com os snapshots privados.

T022, T035, T040 e T045 continuam pendentes: precisam de execução funcional com Auth, Storage e banco que contenha as migrations. T048 tem migration e procedimento preparados, mas corte/concorrência/rollback não foram executados em Supabase isolado. T049 tem builds e análise Deno concluídos, mas falta aplicar as migrations em ambiente isolado. T050 depende dessa mesma integração; T051 requer amostra de dados e participantes para medições reais. Nenhum desses itens foi marcado por inferência.

O checklist de qualidade não foi alterado. A migration local de guincho foi preservada. Não houve publicação, alteração de banco remoto ou instalação nos dispositivos conectados.

### Atualização após autorização de publicação

As migrations foram aplicadas na nuvem via `npx supabase`, incluindo uma corretiva adicional (18 no total), e 12 Edge Functions relacionadas foram publicadas antes da ativação final. O teste SQL de signup/restrição/rascunho/modalidades foi executado com ROLLBACK e passou. O registro anterior de ausência de alterações remotas descreve a etapa de implementação local, anterior à autorização. Os itens de validação integrada e medições permanecem abertos; a publicação não os conclui automaticamente.
