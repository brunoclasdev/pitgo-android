# Tasks: Android Environment Flavors

**Input**: Design documents in `specs/027-android-environment-flavors/`.
**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`.
**Tests**: Não criar suítes automatizadas; executar compilação, lint e validação manual conforme a constituição.
**Organization**: Tarefas por história; `[P]` indica arquivos independentes após os pré-requisitos da fase.

## Phase 1: Setup

- [X] T001 Proteger arquivos locais de ambiente e secrets em `.gitignore`, preservando exemplos sem credenciais e ignorando `config/dev.properties`, `config/homol.properties` e `config/prod.properties`.
- [X] T002 Criar `config/environment.properties.example` com as propriedades públicas exigidas pelo contrato, instruções para os três arquivos e separação explícita dos secrets privados do backend.

## Phase 2: Foundational

- [X] T003 Definir dimensão `environment` e flavors `dev`, `homol`, `prod` em `app/build.gradle.kts`, mantendo `com.trustarx.pitgo`, gerando identidade/modo imutáveis e validando somente configurações das variantes solicitadas, sem fallback para `local.properties` compartilhado.
- [X] T004 Criar `app/src/main/java/com/trustarx/pitgo/core/environment/EnvironmentConfig.kt` com identidade, modo e fingerprint incluindo backend e versão do escopo local; rejeitar combinações inconsistentes sem expor valores.
- [X] T005 Definir `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentGateway.kt` cobrindo criação, recuperação, histórico, consulta, observação, valor da proposta, cancelamento e reembolso com os modelos existentes; reutilizar `PaymentStatus.REJECTED` para a recusa visual.
- [X] T006 Implementar `app/src/main/java/com/trustarx/pitgo/features/payment/repository/IntegratedPaymentGateway.kt` delegando ao `features/payment/repository/PaymentRepository.kt`, preservando contratos, idempotência e recuperação atuais.

**Checkpoint**: Configuração imutável e contrato comum disponíveis; implementação simulada ainda não pode ser distribuída como concluída.

## Phase 3: US1 — Selecionar o ambiente (P1; MVP de configuração)

**Goal**: Gerar versões identificadas, com configuração própria e isolamento de estado.
**Independent validation**: Compilar cada flavor, instalar sequencialmente no mesmo device e confirmar identidade/backend e ausência de sessão anterior; atualização no mesmo backend preserva estado compatível.

- [X] T007 [P] [US1] Criar nomes `PitGo Dev`, `PitGo Homol` e `PitGo Prod` em `app/src/dev/res/values/strings.xml`, `app/src/homol/res/values/strings.xml` e `app/src/prod/res/values/strings.xml`.
- [X] T008 [P] [US1] Implementar DataStore e invalidação por fingerprint em `app/src/main/java/com/trustarx/pitgo/core/environment/EnvironmentStateRepository.kt`, tratando primeira execução sobre dados legados como migração segura e gravando a identidade somente depois da limpeza bem-sucedida.
- [X] T009 [US1] Integrar isolamento antes de restauração/renovação de sessão em `app/src/main/java/com/trustarx/pitgo/core/network/SupabaseClientProvider.kt`, `core/auth/data/SupabaseAuthDataSource.kt` e `core/auth/repository/SessionRepository.kt`; nenhuma credencial do backend anterior deve ser enviada ao novo.
- [X] T010 [US1] Coordenar limpeza de tracking, notificações, recuperação e onboarding em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`, `core/preferences/ProviderTrackingPreferences.kt`, `core/preferences/VehicleOnboardingRepository.kt` e `features/providertracking/repository/ProviderTrackingStateRepository.kt`, preservando tema e impedindo serviços de reiniciar com dados antigos.
- [X] T011 [US1] Revisar backup/restauração de estado incompatível em `app/src/main/res/xml/backup_rules.xml` e `app/src/main/res/xml/data_extraction_rules.xml`, garantindo que sessão e identidade sejam invalidadas também após restore.
- [ ] T012 [US1] Registrar validação de identificação, troca de ambiente e isolamento de backend em `specs/027-android-environment-flavors/quickstart.md`, incluindo assinatura compatível e versionCode não inferior para instalação por substituição.

## Phase 4: US2 — Pagamento simulado em dev (P1)

**Goal**: Percorrer checkout e retorno sem operações financeiras externas.
**Independent validation**: Em dev, escolher aprovação, recusa, expiração, falha e cancelamento; repetir offline após carregar o contexto; confirmar zero chamadas Mercado Pago, tokenização e funções financeiras.

- [X] T013 [US2] Implementar `app/src/main/java/com/trustarx/pitgo/features/payment/repository/SimulatedPaymentGateway.kt` com UUIDs locais, StateFlow, idempotência por contexto, recuperação no escopo local e estados existentes, incluindo cancelamento e reembolso simulado sem RPCs financeiros.
- [X] T014 [US2] Adaptar `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt` para usar o gateway, expor ações determinísticas de simulação e pular documento, CVV e tokenização reais em dev; manter decisões normais de apresentação.
- [X] T015 [US2] Integrar seletor acessível de resultados e identificação de simulação em `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt` e `app/src/main/res/values/strings.xml`, disponível exclusivamente quando o estado do ViewModel indicar simulação; QR/code de teste nunca deve ser pagável.
- [X] T016 [US2] Isolar cartões salvos e cadastro/remoção de cartões em dev em `app/src/main/java/com/trustarx/pitgo/features/paymentmethods/repository/SavedCardRepository.kt`, `features/paymentmethods/viewmodel/PaymentMethodsViewModel.kt` e `features/paymentmethods/ui/PaymentMethodsScreen.kt`, usando somente dados locais de teste e sem SDK/Edge Functions.
- [X] T017 [US2] Implementar continuação local em `app/src/main/java/com/trustarx/pitgo/features/pittec/repository/SimulatedPitTecFlowRepository.kt` com snapshot e transições tipadas de deslocamento, atendimento, conclusão e cancelamento; integrar gateway/StateFlow em `features/payment/viewmodel/PaymentHistoryViewModel.kt`, `features/pittec/viewmodel/PitTecViewModel.kt`, `features/pittec/ui/PitTecScreen.kt` e `core/navigation/PitGoNavHost.kt`, usando os componentes existentes; permitir iniciar cenário local sem atendimento remoto e impedir envio de IDs simulados ou mutações de atendimento ao servidor. Validar retorno após aprovação até conclusão, inclusive offline; preservar homol/prod.
- [X] T018 [US2] Selecionar gateway simulado somente por identidade compilada em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt` e impedir inicialização do SDK Mercado Pago em dev em `app/src/main/java/com/trustarx/pitgo/PitGoApplication.kt`.
- [ ] T019 [US2] Registrar matriz dos cenários dev, offline, repetição e evidências de ausência de chamadas financeiras em `specs/027-android-environment-flavors/quickstart.md`.

## Phase 5: US3 — Pagamentos reais isolados (P1)

**Goal**: Manter integração financeira em homol/prod sem cruzamento de configuração.
**Independent validation**: Conferir cada projeto/conta, executar somente pagamento controlado autorizado e validar recuperação, cancelamento e destino; configuração inválida deve bloquear a operação sem fallback.

- [X] T020 [US3] Exigir configuração pública Mercado Pago própria em homol/prod em `app/build.gradle.kts`, sem exigir chave financeira em dev; validar allowlist de propriedades e rejeitar secrets privados nos arquivos Android, sem registrar seus valores.
- [X] T021 [US3] Selecionar gateway integrado e inicializar SDK somente com a chave do flavor em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt` e `app/src/main/java/com/trustarx/pitgo/PitGoApplication.kt`, mantendo falha explícita sem fallback.
- [X] T022 [US3] Documentar em `specs/027-android-environment-flavors/quickstart.md` preparação dos dois backends (Dev separado; Homol/Prod compartilhados) com projeto alvo explícito, aplicação do conteúdo de `supabase/migrations/` e deploy de `supabase/functions/` via `npx supabase`; fornecer secrets por arquivos privados separados e configurar webhooks/cron no projeto correto, sem criação/deploy/transação financeira implícita nesta tarefa documental.
- [X] T023 [US3] Conferir preservação da integração em `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt` e registrar em `specs/027-android-environment-flavors/quickstart.md` validações executadas ou bloqueadas por credenciais/projetos ausentes, sem marcar como validado um pagamento não executado.

## Phase 6: US4 — Diagnóstico seguro (P2)

**Goal**: Identificar ambiente e erros sem revelar configuração sensível.
**Independent validation**: Localizar identidade em até dez segundos; provocar configuração inválida e erro de operação; inspecionar telas/logs sanitizados.

- [X] T024 [P] [US4] Implementar identificação sanitizada em `app/src/main/java/com/trustarx/pitgo/core/environment/EnvironmentDiagnostics.kt`, expondo somente ambiente, versão, modo e categorias de falha.
- [X] T025 [US4] Exibir identidade por estado do ViewModel em `app/src/main/java/com/trustarx/pitgo/features/profile/viewmodel/ProfileViewModel.kt` e `features/profile/ui/ProfileScreen.kt`, com strings em `app/src/main/res/values/strings.xml`.
- [X] T026 [US4] Sanitizar mensagens de configuração e pagamento em `app/build.gradle.kts`, `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt` e `features/paymentmethods/repository/SavedCardRepository.kt`, mantendo erro acionável sem URL com token, payload, CVV ou segredo.
- [ ] T027 [US4] Registrar inspeção de logs/UI e configuração obrigatória inválida em `specs/027-android-environment-flavors/quickstart.md`, sem incluir valores sensíveis nas evidências.

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T028 Implementar `:app:validateEnvironmentIsolation` em `app/build.gradle.kts` exigindo os três arquivos, verificando `environmentId`/flavor e `supabaseProjectRef`/host canônico, rejeitando Dev no backend de produção e exigindo todas as propriedades públicas de Homol iguais às de Prod, sem expor valores; incluir os campos em `config/environment.properties.example` e exigir a verificação conjunta antes de distribuição.
- [X] T029 Validar a tarefa `:app:validateEnvironmentIsolation` definida em `app/build.gradle.kts` com configurações corretas e cópias temporárias contendo Dev no backend de produção, Homol diferente de Prod, identidade incompatível, project ref divergente do host e propriedades inválidas; restaurar os arquivos locais ao final e registrar resultados ou bloqueios reais sem credenciais em `specs/027-android-environment-flavors/quickstart.md` (depende de T028; não criar suíte automatizada).
- [X] T030 Compilar as seis variantes e executar lint das três variantes debug configuradas em `app/build.gradle.kts`, após a verificação conjunta de isolamento; registrar resultados e bloqueios reais em `specs/027-android-environment-flavors/quickstart.md` (depende de T029).
- [ ] T031 Executar matriz de substituição, reinício, sessão, checkout, isolamento e inspeção de APK descrita em `specs/027-android-environment-flavors/quickstart.md`; não executar cobranças sem autorização específica e contexto de conta/valor.
- [X] T032 Conferir `git diff --check`, arquivos ignorados e cobertura de FR-001 a FR-017 e SC-001 a SC-009 em `specs/027-android-environment-flavors/tasks.md`, atualizar somente tarefas comprovadamente concluídas e relatar requisitos bloqueados.

## Dependencies & Execution Order

```text
Setup (T001–T002) → Foundation (T003–T006) → US1
US1 → US2 → US3 → US4 → Polish
```

US2 e US3 dependem da seleção de ambiente e da invalidação da US1. O adapter integrado é fundação para preservar o comportamento existente durante a introdução da simulação. US3 sucede US2 porque ambas alteram container/initializer. US4 integra o estado final dos gateways. Não paralelizar edições no mesmo arquivo entre histórias.

### Parallel execution examples

- US1: executar T007 (recursos dos flavors) e T008 (DataStore) em paralelo após a fundação; T009–T012 aguardam essas bases.
- US2: T016 pode ser desenvolvido em paralelo a T013–T015 após definir o contrato do modo simulado; integrar T017–T019 sequencialmente. T016 não recebe `[P]` porque exige coordenação do fluxo de cartões com T014.
- US3: preparar a documentação operacional de T022 enquanto T020–T021 são implementadas; as verificações de T023 aguardam a integração.
- US4: T024 pode ocorrer em paralelo com a preparação dos recursos de apresentação de T025; a integração de T025 aguarda a API de diagnóstico.

## Implementation Strategy

1. Concluir setup/fundação e US1 para um MVP de configuração e isolamento. Não distribuir dev para validar pagamentos enquanto US2 estiver incompleta.
2. Entregar US2 com cenários locais e validar que nenhum caminho, inclusive cartões e reembolso, chega ao Mercado Pago.
3. Entregar US3 preservando integração; preparar backends com alvos explícitos e registrar pendências externas quando não houver projetos/credenciais disponíveis.
4. Entregar diagnóstico seguro da US4 e executar a matriz final.

## Traceability

| Requirements | Tasks |
|---|---|
| FR-001, FR-002, FR-014, FR-015 | T001–T004, T007, T012, T020, T028–T030 |
| FR-003 | T007, T024–T025 |
| FR-004, FR-005, FR-006 | T005, T013–T019 |
| FR-007, FR-008, FR-013 | T006, T020–T023 |
| FR-009, FR-010 | T008–T012, T022, T028–T029, T031 |
| FR-011, FR-012 | T001–T002, T020, T024–T027, T032 |
| FR-016, SC-008 | T017–T019, T031 |
| FR-017, SC-009 | T028–T029, T031–T032 |

**External prerequisites**: Valores reais permanecem somente em arquivos locais ignorados. Dev usa o projeto de desenvolvimento; Homol e Prod compartilham o backend e os pagamentos de produção. Mesmo applicationId exige assinatura compatível para substituição; devices distintos podem permanecer conectados e receber instalações direcionadas por serial.
