---

> Correção de escopo pelo usuário em 2026-10-21: somente aviso dentro do app; Firebase/FCM, notificação do sistema, registro de instalações, ledger de postagem e worker foram retirados. As seções/tarefas abaixo que descrevem push representam o planejamento anterior e estão superadas por esta correção. Referências atuais: `spec.md`, contrato de avisos e seção de correção do A33 no `quickstart.md`. Recuperação da foto e retorno ao mapa mantêm os requisitos existentes. O aviso global usa diálogo Material sem alterar as medidas da navegação, consulta autenticada a cada 5 s em primeiro plano e deduplicação de dispensa restaurável por conta/versão. A migration `20261021130000_guincho_inspection_in_app_alerts.sql` retira cron/despacho/fanout e revoga RPCs de push, preservando avisos e registros existentes.
description: "Tarefas de implementação dos ajustes no fluxo de vistoria do Guincho"
---

# Tasks: Aviso, recuperação de fotos e retorno ao mapa na vistoria

**Input**: Documentos em `specs/032-ajustes-fluxo-vistoria/`.
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contratos](contracts/) e [quickstart.md](quickstart.md).
**Tests**: Não foram solicitados testes automatizados. As tarefas de verificação abaixo são build/lint e validação manual; não criar suítes, mocks, fixtures ou utilitários de teste.
**Organization**: Três incrementos verificáveis independentemente: US1 (P1), US2 (P1) e US3 (P2). Todos os itens começam pendentes; planejamento não comprova implementação nem entrega FCM.

## Format: `[ID] [P?] [Story] Description`

- `[P]` indica trabalho em arquivos diferentes que pode ocorrer junto a outro ramo após cumprir os pré-requisitos indicados em Dependencies.
- `[US1]`, `[US2]`, `[US3]` identificam a história da especificação; Setup, Foundational e Polish não levam história.
- Concluir o comportamento e a verificação associados antes de marcar a tarefa; falta de credencial ou aparelho deixa a tarefa correspondente pendente, sem impedir trabalho independente.

## Path Conventions

Todos os caminhos das tarefas são relativos à raiz do repositório. Kotlin permanece em `app/src/main/java/com/trustarx/pitgo/`, recursos em `app/src/main/res/`, backend em `supabase/`. Não criar outro módulo, outra fila de fotos ou infraestrutura genérica. Os nomes de arquivos novos são os destinos propostos; preservar alterações locais preexistentes.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Identificar a base real antes de alterar componentes já em evolução.

- [X] T001 Conferir o diff existente e a implementação da feature 031 em `app/src/main/java/com/trustarx/pitgo/features/guincho/` e `supabase/migrations/20261014143000_guincho_inspection_commands_v2.sql`; confirmar no checkout os contratos de `retryCapture`, journal, submit idempotente e aprovação, preservando mudanças preexistentes e registrando somente divergências materiais em `specs/032-ajustes-fluxo-vistoria/plan.md`.
- [X] T002 Obter baseline executando `./gradlew :app:validateEnvironmentIsolation :app:assembleDevDebug` a partir da configuração de `app/build.gradle.kts`; registrar resultado ou impedimento concreto, sem credenciais, em `specs/032-ajustes-fluxo-vistoria/quickstart.md`, distinguindo falha anterior de regressão futura.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Verificar o contexto comum que já existe; não gerar nova autenticação, DI ou framework de estado.

- [X] T003 Verificar em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`, `app/src/main/java/com/trustarx/pitgo/core/environment/EnvironmentConfig.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionSyncViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` os pontos de injeção, fingerprint, logout, ciclo de vida visível e mapa `appointmentTracking(appointmentId)`; confirmar que cada história pode reutilizá-los sem mudar permissões ou início do transporte e ajustar apenas referências desatualizadas em `specs/032-ajustes-fluxo-vistoria/plan.md`.

**Checkpoint**: Base e pontos de integração conhecidos. US1, US2 e US3 podem ser implementadas independentemente; infraestrutura Firebase é requisito somente de US1, não uma nova fundação para as três histórias.

## Phase 3: User Story 1 — Receber aviso de vistoria aguardando aceite (Priority: P1) — MVP

**Goal**: Avisar o cliente no mapa, em outras telas e em segundo plano, permitindo revisão da versão vigente sem aceitar automaticamente.

**Independent Test**: Enviar uma vistoria existente e verificar aviso nos três contextos, abertura autenticada correta, repetição sem novo alerta, reenvio após correção, invalidação e recuperação com permissão bloqueada. SC-001: pelo menos 19/20 avisos em até 10 s em cada contexto nas condições previstas; SC-002: nenhuma abertura/decisão indevida nos casos avaliados.

### Implementation for User Story 1

- [X] T004 [US1] Acrescentar ao catálogo `gradle/libs.versions.toml`, `build.gradle.kts` e `app/build.gradle.kts` somente Google Services, Firebase BoM/Messaging e WorkManager indicados em `specs/032-ajustes-fluxo-vistoria/research.md`, reconfirmando versões estáveis e compatibilidade nas fontes primárias antes de integrar; manter versões alheias, SDKs, applicationId e política atual dos flavors.
- [ ] T005 [US1] Configurar `app/src/dev/google-services.json`, `app/src/homol/google-services.json` e `app/src/prod/google-services.json` com configurações públicas reais dos apps Firebase de cada ambiente, preservando Dev isolado e Homol/Prod compartilhados; ajustar `app/build.gradle.kts` para falha clara de configuração ausente/incompatível em Release ou com `-PrequireInspectionPush=true`, sem expor valores; Debug sem configuração mantém `INSPECTION_PUSH_CONFIGURED=false` para validar as demais histórias, sem declarar entrega externa disponível. Não fabricar arquivos/credenciais; se o provisionamento externo estiver indisponível, manter esta tarefa pendente e registrar o requisito em `specs/032-ajustes-fluxo-vistoria/quickstart.md`.
- [X] T006 [P] [US1] Criar `supabase/migrations/20261021120000_guincho_inspection_alert_schema.sql` com tabelas privadas de instalação/vínculo, aviso e entrega de `specs/032-ajustes-fluxo-vistoria/data-model.md`, chaves estrangeiras, unicidade por cliente/atendimento/versão e instalação/vínculo/revisão, índices de paginação/trabalho devido, RLS e revogação de acesso direto para `anon`/`authenticated`.
- [X] T007 [US1] Implementar em `supabase/migrations/20261021121000_guincho_inspection_alert_api.sql` as quatro RPCs de registro/revogação/listagem/consulta do contrato `specs/032-ajustes-fluxo-vistoria/contracts/client-inspection-alerts.md`, incluindo helper restrito de fanout reutilizável, usuário derivado de `auth.uid()`, reassociação atômica do token, binding estável em replay, filtro de ambiente server-side, paginação 20/máximo 100 e criação de entregas de pendências existentes ao registrar instalação.
- [X] T008 [US1] Implementar em `supabase/migrations/20261021122000_guincho_inspection_alert_events.sql` geração transacional somente na transição efetiva `DRAFT → SUBMITTED`, ambiente Dev definido pelo backend/Homol-Prod derivado da cobrança vinculada, backfill somente de versões vigentes pendentes, fanout e invalidação revisionada após decisão/substituição/encerramento; replay do submit não cria aviso, e rollback ou envio recusado não gera entrega.
- [X] T009 [US1] Criar em `supabase/migrations/20261021123000_guincho_inspection_push_dispatch.sql` RPCs claim/ACK/defer exclusivas de `service_role`, lote até 50, lease de 60 s, disparo imediato por trigger de entrega com `pg_net` após commit e recuperação por `pg_cron` de minuto com Vault/segredo próprios; incluir retomada de falha de configuração após correção, retenção de resolvidos por 30 dias e nenhuma limpeza de aviso ainda pendente ou evidência da vistoria.
- [X] T010 [US1] Implementar `supabase/functions/guincho-inspection-push/index.ts` e sua entrada em `supabase/config.toml`: validar `x-inspection-push-secret`, obter OAuth2 e enviar FCM HTTP v1 com credenciais exclusivamente server-side, revalidar cliente/versão/vínculo/ambiente, enviar dados HIGH para pendência e NORMAL para invalidação, TTL 300 s, timeout inferior ao lease, ACK, retry desde 60 s até 1 h com `Retry-After`, revogação de token comprovadamente inválido e métricas sem dados privados.
- [X] T011 [P] [US1] Definir `GuinchoInspectionAlertModels` e DTOs mínimos em `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoInspectionAlertModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionAlertDtos.kt`, com destino tipado, envelope FCM versão 1, IDs, ambiente, binding, revisão do aviso e resultados PENDING/RESOLVED, recusando dados inválidos sem confundir revisão do aviso com a do atendimento.
- [X] T012 [US1] Implementar vínculo/ledger persistente e mutex compartilhado em `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionAlertStore.kt` usando DataStore; isolar por fingerprint/conta/aviso, manter UUID de instalação e binding fora de backup, atualização monotônica e `lastAlertedRevision` somente após postagem bem-sucedida, preservando essa marca após dispensa/expiração visual para impedir republicação da mesma versão; excluir os dados apropriados em `app/src/main/res/xml/backup_rules.xml` e `app/src/main/res/xml/data_extraction_rules.xml`, sem remover regras de fotos existentes.
- [X] T013 [US1] Implementar `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoInspectionAlertRepository.kt` com RPCs autenticadas, registro/revogação/rotação de token, sessão central, snapshot completo paginado, classificação de erros e aplicação monotônica sob o mutex do ledger; antes de autorizar postagem, consultar a marca persistente e suprimir reentrega já notificada, inclusive após dispensa/expiração, mantendo a pendência interna; erro ou página incompleta não resolve avisos e mensagem antiga não reativa revisão invalidada.
- [X] T014 [P] [US1] Implementar `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionNotifier.kt` e `app/src/main/res/drawable/ic_notification_inspection.xml`, com canal próprio, tag estável ambiente/conta/aviso, `onlyAlertOnce`, timeout visual de 300 s, cancelamento e `PendingIntent` explícito/imutável distinto do chamado do prestador; receber autorização de primeira postagem do fluxo que consulta o ledger, sem tratar `onlyAlertOnce` como dedupe persistente, e informar sucesso ou impedimento por permissão sem marcar postagem recusada como entregue.
- [X] T015 [US1] Implementar `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionMessagingService.kt` e declaração não exportada com esse pacote em `app/src/main/AndroidManifest.xml`; validar schema/IDs/tipo/ambiente/conta/binding, reagir a `onNewToken` guardando rotação até autenticação e agendar trabalho único por aviso/revisão, expedited para mensagem de alta prioridade com fallback, sem rede longa no callback.
- [X] T016 [US1] Implementar `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionAlertWorker.kt`, recebendo Repository/notifier por injeção simples: aguardar sessão central, conferir o mesmo vínculo, consultar situação autorizada antes de exibir/cancelar e compartilhar com foreground o fluxo protegido pelo mutex que verifica o ledger antes de postar; não republicar versão já notificada após dispensa/expiração, preservar pendência interna, marcar somente postagem bem-sucedida, repetir falha transitória apenas na validade do envelope e cancelar trabalho de outra conta/ambiente.
- [X] T017 [US1] Integrar factory do Worker e componentes de `features/guincho/data` em `app/src/main/java/com/trustarx/pitgo/PitGoApplication.kt`, `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt` e `app/src/main/java/com/trustarx/pitgo/core/environment/EnvironmentStateRepository.kt`, ajustando inicialização do WorkManager em `app/src/main/AndroidManifest.xml` se necessário; registrar token após login/renovação, revogar antes de logout quando possível e cancelar trabalhos/notificações/vínculo local na saída ou troca, inclusive offline, sem adicionar Hilt ou retenção de Context em ViewModel.
- [X] T018 [US1] Implementar `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionAlertViewModel.kt` combinando sessão, conectividade e visibilidade: snapshot inicial/retomada/reconexão, polling global de 5 s com uma consulta em voo, cancelamento ao ocultar/sair, compartilhamento da aplicação do snapshot com o Worker e ação de abrir aviso com revalidação independente da tela de vistoria.
- [X] T019 [US1] Criar aviso Material acionável em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionAlert.kt` e textos de aviso/ação/permissão em `app/src/main/res/values/strings.xml`, com fonte ampliada e leitor de tela; solicitar `POST_NOTIFICATIONS` no contexto de atendimento/revisão com recusa não bloqueante e preservar pendência interna mesmo quando notificação for dispensada ou expirar.
- [X] T020 [US1] Integrar evento de abertura fria/`onNewIntent` em `app/src/main/java/com/trustarx/pitgo/MainActivity.kt`, destino central em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e observador/aviso global em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`; manter destino durante autenticação correta, descartar em troca de conta/ambiente, revalidar ao tocar, abrir versão vigente ou acompanhamento/histórico resolvido, sem navegação automática do banner nem duplicação da vistoria já aberta.
- [ ] T021 [US1] Preparar implantação e configurar no backend Dev o worker de `supabase/functions/guincho-inspection-push/index.ts`, a cadeia aditiva `supabase/migrations/20261021120000_guincho_inspection_alert_schema.sql` até `supabase/migrations/20261021123000_guincho_inspection_push_dispatch.sql` e mapeamento/credenciais FCM/Vault protegidos; usar projeto Dev explicitamente conferido e registrar presença, implantação e recuperação de disparo imediato/cron em `specs/032-ajustes-fluxo-vistoria/quickstart.md`, sem imprimir secretos nem implantar Homol/Prod ou criar pagamentos reais. Ausência de acesso/configuração mantém a tarefa pendente.
- [ ] T022 [US1] Executar o Cenário A e a conferência backend de `specs/032-ajustes-fluxo-vistoria/quickstart.md` com cliente, guincheiro e terceiro usuário em aparelhos/Google APIs, cobrindo três contextos, 20 envios por contexto, erro de envio sem aviso, dedupe, nova versão, permissão negada sem marca de entrega, invalidação/aviso antigo, token, troca de conta/ambiente e acesso indevido; após postagem bem-sucedida, dispensar ou aguardar expiração e reentregar a mesma mensagem, confirmando ausência de republicação/novo alerta e manutenção da pendência interna; registrar tempos e resultados reais, sem usar notificação local ou aceitação FCM como prova de entrega externa.

**Checkpoint**: US1 só está completa com aviso global e entrega FCM real validados. Configuração faltante não autoriza declarar esta história pronta; US2/US3 continuam independentes.

## Phase 4: User Story 2 — Tentar salvar novamente a foto capturada (Priority: P1)

**Goal**: Salvar a mesma imagem após falha recuperável, preservando fonte e contexto, sem desfazer nem abrir a câmera.

**Independent Test**: Capturar uma foto, interromper preparo/upload/confirmação, corrigir a causa e acionar “Tentar salvar novamente”; confirmar a mesma captura/ponto e um único resultado. Repetir após rotação/reabertura, falhas consecutivas e retomada automática concorrente; recaptura somente sem fonte recuperável.

### Implementation for User Story 2

- [X] T023 [US2] Estender somente o necessário em `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoInspectionModels.kt` para representar recuperabilidade/tentativa ativa e reparação persistida da reserva, mantendo `captureId`, contexto, metadados e IDs estáveis no retry normal, com defaults compatíveis com journals versão 1 e sem nova fila ou entidade remota.
- [X] T024 [P] [US2] Distinguir fonte recuperável de miniatura isolada em `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionCaptureFiles.kt`, permitir reconstrução de derivados em temporário e conferir tamanho/hash antes de trocar arquivos; classificar falta de espaço e erro transitório sem apagar raw ou exigir câmera por qualquer `IMAGE`.
- [X] T025 [P] [US2] Adaptar `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionPendingStore.kt` para ler journals existentes e persistir atomicamente intenção/retomada de reparação e troca de manifesto/UUIDs após abandono confirmado; reparação interna não entra em `DISCARDED`, não remove fonte e sobrevive a interrupção entre abandono e nova reserva.
- [X] T026 [US2] Corrigir `retryCapture`, `syncPending` e `process` em `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoInspectionRepository.kt` para reconciliar reserva/confirmação antes de gates de edição, reconhecer `READY`, reutilizar fonte/derivados íntegros e IDs no retry normal, distinguir falha recuperável de fonte irrecuperável e usar o mutex existente para ignorar repetição já processada ou em execução, preservando o resultado das demais fotos.
- [X] T027 [US2] Completar no mesmo `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoInspectionRepository.kt` a reparação de bytes divergentes após reserva: persistir intenção, repetir descarte idempotente com IDs antigos, confirmar abandono ou reconciliar `READY`, só então trocar manifesto/UUIDs e reprocessar a mesma captura; não usar descarte do usuário, sobrescrever objetos ou alterar metadados da operação antiga, e bloquear com mensagem se perder autorização/contexto.
- [X] T028 [P] [US2] Acrescentar mensagens de “Tentar salvar novamente”, espaço insuficiente, impedimento de edição e recaptura indispensável em `app/src/main/res/values/strings.xml`, reaproveitando textos existentes adequados e distinguindo falha de captura de falha de salvamento recuperável.
- [X] T029 [US2] Expor estado por captura e ajustar `retryPending`, `PendingActions` e recaptura explícita em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionScreen.kt`; habilitar retry somente com conta/versão editável, fonte disponível e sem tentativa conflitante, mostrar progresso/sucesso real, manter retomada de `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionSyncViewModel.kt` e preservar o gate de envio para obrigatórias/opcionais incluídas pendentes.
- [ ] T030 [US2] Executar o Cenário B de `specs/032-ajustes-fluxo-vistoria/quickstart.md`, incluindo falha repetida, raw preservado com falta de espaço, derivado perdido antes/depois de reserva, confirmação remota com resposta perdida, concorrência manual/automática, rotação/reabertura, fonte realmente ausente e atendimento não editável; registrar evidência de SC-003 sem nova captura nos casos recuperáveis e sem duplicação ou descarte silencioso.

**Checkpoint**: US2 funciona com a infraestrutura atual de fotos, sem depender de Firebase; miniatura visível sozinha não comprova recuperação da imagem exigida.

## Phase 5: User Story 3 — Voltar ao mapa após enviar a vistoria (Priority: P2)

**Goal**: Fechar a vistoria após envio próprio confirmado, inclusive replay com resposta perdida, e exibir o mapa correto sem fechar consultas comuns.

**Independent Test**: Enviar versão inicial e após correção, com e sem mapa anterior; conferir retorno automático, Voltar e consulta explícita. Falha mantém a tela; resposta perdida usa mesma operação/versão e retorna uma vez. Aceite do cliente preserva o retorno atual e início manual do transporte.

### Implementation for User Story 3

- [X] T031 [P] [US3] Preservar outcome tipado `APPLIED`/`EXISTING`/`REVISION_CONFLICT` e documento válido do submit em `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionDtos.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoInspectionModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoInspectionRepository.kt`; aceitar versão original explícita para replay com o mesmo `p_operation_id`, sem derivá-la de versão posterior nem enfraquecer gates das operações novas.
- [X] T032 [US3] Persistir intenção própria de envio e retorno confirmado em `SavedStateHandle` de `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionViewModel.kt`, vinculando fingerprint/conta/atendimento/versão/operação; aplicar documento retornado, conservar intenção incerta em falha, reconciliar com a mesma identidade mesmo quando `canSubmit=false`, confirmar só `APPLIED`/`EXISTING` correspondentes e limpar após consumo da navegação, sem navegar por observar `SUBMITTED` no polling.
- [X] T033 [US3] Consumir o retorno pendente uma vez em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionScreen.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, reutilizando mapa anterior do mesmo atendimento ou navegando para `PitGoRoutes.appointmentTracking(appointmentId)` com remoção inclusiva da vistoria e `launchSingleTop`; preservar `onApprovalConfirmed`, recuperação após rotação, consulta posterior aberta e situação vigente do mapa caso o cliente já tenha decidido.
- [ ] T034 [US3] Executar o Cenário C de `specs/032-ajustes-fluxo-vistoria/quickstart.md`, com 20 envios/reenvios confirmados, falha real, confirmação perdida e replay, entrada sem mapa prévio, rotação, consulta de versão enviada/aprovada e aceite rápido do cliente; registrar SC-004/SC-005, incluindo ≥19/20 retornos em até 2 s, ausência de duplicata/reabertura pelo Voltar e transporte iniciado somente por ação explícita.

**Checkpoint**: US3 funciona sem aviso push e mantém o comportamento do aceite do cliente; simples consulta de uma versão enviada não fecha a vistoria.

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Conferir integração e evidências das três entregas, sem ampliar o escopo.

- [X] T035 Executar `./gradlew :app:validateEnvironmentIsolation`, os builds `:app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug` e lint `:app:lintDevDebug :app:lintHomolDebug :app:lintProdDebug` para a configuração final de `app/build.gradle.kts`; conferir tipos do worker com `deno check supabase/functions/guincho-inspection-push/index.ts` e registrar resultados/impedimentos em `specs/032-ajustes-fluxo-vistoria/quickstart.md`, sem considerar build bem-sucedido como prova do fluxo no aparelho.
- [ ] T036 Revisar manualmente a integração de `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionAlert.kt` e `supabase/functions/guincho-inspection-push/index.ts`: fonte ampliada/leitor de tela, acesso só a participantes, sessão/ambiente trocados, notificação antiga, ausência de dados privados nos logs e regressão de alertas do prestador/PitTec, álbum, aprovação e início manual; registrar achados em `specs/032-ajustes-fluxo-vistoria/quickstart.md`.
- [ ] T037 Consolidar as medições já obtidas em T022/T030/T034 em `specs/032-ajustes-fluxo-vistoria/quickstart.md`, relacionando SC-001 a SC-005, resultado por contexto, tempos, dedupe e preservação de fotos/etapas; confirmar em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionAlertViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoInspectionRepository.kt` uma consulta global em voo, suspensão em background e exclusão mútua das tentativas, sem repetir medições já conclusivas salvo mudança/falha nova.
- [X] T038 Atualizar somente desvios efetivos do desenho em `specs/032-ajustes-fluxo-vistoria/plan.md`, `specs/032-ajustes-fluxo-vistoria/data-model.md` e contratos em `specs/032-ajustes-fluxo-vistoria/contracts/`, mantendo evidências e pendências operacionais em `specs/032-ajustes-fluxo-vistoria/quickstart.md`; conferir diff final e marcar em `specs/032-ajustes-fluxo-vistoria/tasks.md` somente tarefas realmente concluídas, sem declarar push validado se faltar configuração, implantação ou aparelho.

## Dependencies & Execution Order

### Phase Dependencies

Setup T001 → T002 → Foundational T003. Em seguida, as histórias não dependem funcionalmente umas das outras. A sequência preferida segue a especificação: US1 (P1) → US2 (P1) → US3 (P2). Polish T035–T038 sucede a integração dos incrementos e respeita os pré-requisitos de cada verificação.

```text
T001 → T002 → T003
               ├── US1: backend + Android + provisionamento → T021 → T022
               ├── US2: captura + journal + retry → T029 → T030
               └── US3: envelope + intenção própria + navegação → T034
                                 │
                    incrementos integrados → T035 → T036 → T037 → T038
```

### Within Each User Story

| História/ramo | Pré-requisitos e sequência |
|---|---|
| US1 configuração Android | T003 → T004 → T005; configuração real é necessária para os builds e validação FCM, não para escrever SQL/modelos |
| US1 backend | T003 → T006 → T007 → T008 → T009 → T010; helper de fanout de T007 não depende de trigger futuro; dispatcher de T009 acopla entrega ao worker sem depender de rede na transação |
| US1 modelos e Android | T003 → T011 → T012 → T013; T014 pode começar após T011, independente de SQL/Repository; T015 depende de T004/T011 e T016 depende de T012/T013/T014/T015 |
| US1 integração | T017 após T004/T012/T013/T015/T016; T018 após T013/T017; T019 após T018; T020 após T017/T018/T019 e T014; T021 após T005/T006–T010; T022 após T005/T014–T021 |
| US2 | T003 → T023; T024 e T025 após T023, em arquivos distintos, podem ocorrer em paralelo; T026 após T024/T025, T027 após T026, T028 independente após T003, T029 após T023–T028, T030 após T029 |
| US3 | T003 → T031 → T032 → T033 → T034; serializar com US2 quando editar os mesmos arquivos, sem criar dependência funcional de US2 |
| Polish | T035 exige configuração e código final; T036 exige integração executável; T037 usa evidências T022/T030/T034 e não fabrica resultados faltantes; T038 consolida o que foi efetivamente entregue |

T004 e T005 são preparação específica de US1 e levam seu rótulo; não movê-las para uma fundação que bloqueie US2/US3. Disponibilidade de Firebase/credenciais e acesso ao backend Dev é dependência operacional de T005/T021/T022. Se ausente, avançar US2/US3 antes de integrar configuração Android incompleta; manter os itens de entrega FCM pendentes. Não introduzir placeholders nem bypass que declare notificação local como solução de segundo plano.

### Parallel Opportunities and Shared Files

- Após T003, T006 (schema), T011 (modelos de aviso) e a preparação Android de T004 podem avançar em arquivos diferentes.
- Depois de T011, T014 (notifier/ícone) pode avançar enquanto T012/T013 cuidam de persistência/Repository; não disputar `strings.xml` ou `AndroidManifest.xml` nessas frentes.
- Em US2, T024 (arquivos) e T025 (journal) não se sobrepõem após modelo de T023; T028 (textos) também pode avançar se T019 não estiver editando o mesmo recurso.
- T031 pode ocorrer junto ao backend de US1 (por exemplo, T010), desde que as tarefas US2 que editam modelo/Repository estejam paradas ou concluídas. Dentro de US3, envelope, intenção e navegação formam sequência, sem paralelismo seguro entre si.
- Serializar T019/T028 em `strings.xml`, T020/T033 em `PitGoNavHost.kt`, T026/T027/T031 em `GuinchoInspectionRepository.kt`, T023/T031 em `GuinchoInspectionModels.kt` e T029/T032/T033 em ViewModel/tela. Atualizações de evidências em `quickstart.md` também são sequenciais.

## Parallel Example: User Story 1

Após T003, executar T006 (schema SQL) junto a T011 (modelos/DTOs Kotlin). Após T011, executar T014 (notifier e ícone) junto a T012 (ledger), enquanto o ramo SQL segue T007–T010. Integrar receptor/Worker somente depois de seus pré-requisitos; não marcar `[P]` como autorização para editar simultaneamente o mesmo arquivo.

## Parallel Example: User Story 2

Após T023, executar T024 (recuperação de fonte/derivados) junto a T025 (persistência atômica do journal). T028 (textos) pode ocorrer em paralelo se `strings.xml` estiver livre. Reunir ambos os componentes antes de T026/T027 e integrar UI em T029 depois do comportamento de recuperação.

## Parallel Example: User Story 3

Executar T031 (envelope do submit) junto a T010 (envio FCM no backend), quando seus pré-requisitos estiverem prontos e US2 não editar os arquivos Kotlin envolvidos. Depois, seguir T032 → T033 → T034; não dividir intenção persistida e consumo de retorno em edições concorrentes dos mesmos arquivos.

## Requirement Coverage

| Requisitos/resultados | Tarefas responsáveis |
|---|---|
| FR-001: evento somente após envio confirmado | T006–T010, T022 |
| FR-002: aviso no mapa/outras telas sem navegação forçada | T018–T020, T022 |
| FR-003: segundo plano/permissão/retomada | T004–T005, T010, T012–T022 |
| FR-004: abertura autorizada e invalidação | T007–T008, T013–T020, T022 |
| FR-005: dedupe e nova versão com aceite explícito | T006–T010, T012–T016, T020, T022 |
| FR-006–FR-009: mesma captura, falha repetida, retomada e exceção irrecuperável | T023–T030 |
| FR-010: bloquear envio de fotos incluídas pendentes | T026, T029–T030 |
| FR-011–FR-013: envio próprio confirmado, erro/replay e navegação | T031–T034 |
| FR-014: preservação do aceite/início manual | T020, T029, T032–T034, T036 |
| SC-001–SC-002 | T022, T037 |
| SC-003 | T030, T037 |
| SC-004–SC-005 | T034, T036–T037 |

## Implementation Strategy

### MVP First — User Story 1

Completar T001–T003, depois US1 até T022. O MVP inclui aviso global, notificação FCM real em segundo plano, abertura segura e dedupe/invalidação; implementar somente banner interno não conclui a história. Não exigir recuperação nova ou retorno automático do guincheiro para demonstrar o aviso sobre o fluxo atual de envio.

### Incremental Delivery

Acrescentar US2 e validá-la com a fila existente; depois US3 e validar confirmação/navegação com o envio existente. Se Firebase não estiver disponível, inverter apenas a execução prática para US2/US3, conservando prioridades e tarefas pendentes de US1. Cada incremento mantém o aceite e o início explícito do transporte.

Migrations e worker são aditivos. Publicação em Homol/Prod requer solicitação explícita; não criar cobranças reais para comprovar o fluxo. Usar ambiente Dev para os cenários do guia; impedimento externo deve ser registrado no item correspondente, sem mascarar a entrega incompleta. Em 2026-10-21, o usuário autorizou adicionalmente a publicação em Homol por `npx supabase`; implantação concluída no backend compartilhado Homol/Prod e registrada no quickstart. O provisionamento FCM e a validação de entrega permanecem pendentes.

### Notes

Total: 38 tarefas — 3 comuns, 19 de US1, 8 de US2, 4 de US3 e 4 finais. Todas exigem caminhos concretos e descrição executável. Não criar testes automatizados sem solicitação; não usar marcadores do checklist de qualidade como comprovação de implementação. Preservar mudanças preexistentes e atualizar o checklist de tarefas somente após executar o trabalho.
