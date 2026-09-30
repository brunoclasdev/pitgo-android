# Tasks: Refinamento do fluxo de Guincho

**Input**: `specs/028-refinamento-guincho/` — spec.md, plan.md, research.md, data-model.md, contracts/ e quickstart.md.
**Organization**: quatro histórias P1, dependências explícitas e validação manual por incremento.
**Tests**: nenhuma suíte nova solicitada; executar build/lint existentes e cenários manuais, conforme constituição.

## Format and Path Conventions

Cada item usa checkbox, ID sequencial, `[P]` apenas quando independente e `[USn]` nas fases de história. Caminhos Android/backend são relativos à raiz deste repositório. Caminhos do dashboard são absolutos porque ele é outro repositório: `/Users/brunocesar/projetos-web/pitgo`. Migrações propostas devem conservar ordem e receber timestamp único se o histórico avançar durante a implementação. Não editar migrações já aplicadas.

## Phase 1: Setup

- [X] T001 Conferir contratos, estado das duas branches/worktrees, versões e regras locais em `specs/028-refinamento-guincho/plan.md` e `/Users/brunocesar/projetos-web/pitgo/AGENTS.md`; registrar no plano os pontos reais de integração e ler documentação local Next antes de alterações web.
- [X] T002 [P] Inventariar constraints/enums, restore, workers, notificações e funções Orders afetadas; mapear cada ramo legado/dirigido em `specs/028-refinamento-guincho/research.md`, incluindo limites de projeto Dev/Homol/Prod e configuração de rota existente.

## Phase 2: Foundational

**Purpose**: fechar infraestrutura e contratos compartilhados antes das histórias.

- [X] T003 Criar modalidade GUINCHO_DIRECT, tabelas rules/drafts/quotes/disputes e campos de vínculo em `supabase/migrations/20260930130000_guincho_direct_schema.sql`, conforme data-model.md; preservar modalidades antigas e referências nulas de candidatos somente no ramo dirigido.
- [X] T004 Implementar constraints de estados, índices operacionais, unicidade da contestação, grants/RLS e regras iniciais 3/km, 10% e taxas copiadas da política vigente em `supabase/migrations/20260930131000_guincho_security_and_pricing.sql`; impedir escrita monetária pelo cliente e leitura pré-pagamento pelo prestador.
- [X] T005 [P] Acrescentar GUINCHO_DIRECT/DISPUTED e contratos de estado/cotação/snapshot em `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoModels.kt`, `features/guincho/data/GuinchoDtos.kt` e `features/mobiledispatch/model/MobileDispatchModels.kt` (todos sob `app/src/main/java/com/trustarx/pitgo/`); preservar parsers e valores legados, validar JSON null.
- [X] T006 Definir snapshots por ator, envelopes/erros/revisões e getters de pedido dirigido em `supabase/migrations/20260930132000_guincho_snapshot_contract.sql`; cliente só total, prestador só remuneração/dados após APPROVED, suporte via autorização própria. Integrar DTOs da T005 sem expor breakdown ao prestador.

**Checkpoint**: schema/RLS/contratos prontos; nenhum fluxo legado muda por efeito colateral.

## Phase 3: US1 — Escolher guincheiro no mapa (P1, MVP de descoberta)

**Goal**: confirmar dados e comparar prestadores por total definitivo sem cobrar.
**Independent validation**: mapa com dois prestadores, entrada incompleta bloqueia seleção, tocar cada ícone troca mini card, sem pagamento (quickstart 1–2).

- [X] T007 [US1] Implementar save_guincho_trip_draft e discover_guincho_providers em `supabase/migrations/20260930133000_guincho_discovery_and_drafts.sql`: veículo próprio, revisão invalidante, providers MOBILE/Guincho ativos/disponíveis, limite 200 e região de retirada.
- [X] T008 [US1] Implementar cálculo numeric e store_guincho_quote exclusivo service-role em `supabase/migrations/20260930134000_guincho_quotes.sql`, validade 15 min, rota confiável, tarifa/versionamento, frações/half-up, total 148 e remuneração 130 no exemplo; mudanças posteriores não alteram cotação paga.
- [X] T009 [US1] Criar `supabase/functions/guincho-quote/index.ts` e registrar em `supabase/config.toml`: autenticar proprietário, ler draft/revisão, calcular rota retirada→destino com configuração existente, timeout/limites, reutilizar rota do draft, rejeitar haversine e dados monetários enviados pelo app, persistir via T008.
- [X] T010 [US1] Criar `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoRepository.kt` com entrada/descoberta/cotação, classificação central de falhas e cancelamento de coroutines; usar DTOs T005 e contratos T007–009.
- [X] T011 [US1] Implementar estado de entrada, veículo, localização, revisão e seleção em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoMapViewModel.kt`; confirmar dados antes de selecionar, descartar cotação obsoleta ao trocar marcador/entrada, manter uma chamada de cotação em voo.
- [X] T012 [US1] Criar `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoMapScreen.kt` usando mapa/design system existentes, confirmação retirada/destino/veículo, ícones, mini card nome/total/Contratar e estados loading/vazio/offline/rota indisponível; medir card para preservar mapa.
- [X] T013 [US1] Integrar categoria Guincho à rota de mapa antes de checkout em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`, `core/navigation/PitGoNavHost.kt` e `core/app/PitGoAppContainer.kt`; manter identificação por modalidade/oferta e evitar roteamento pelo nome traduzido.
- [X] T014 [US1] Adicionar textos pt-BR e descrições acessíveis em `app/src/main/res/values/strings.xml`, incluindo input obrigatório, rota falha, recotação e nome/total do mini card; usar strings na T012 e temas/fonte ampliada existentes.
- [X] T015 [US1] Executar validação de descoberta/preços da US1 e registrar evidência em `specs/028-refinamento-guincho/quickstart.md`; conferir ausência de pagamento/reserva antes de Contratar e permissões/erros do mapa. **Escopo ajustado em 30/09/2026:** usuário dispensou a execução de testes manuais pelo agente e assumiu essa validação; não declarar estes cenários/SC como executados.

**Checkpoint**: demonstração de mapa/cotação pronta; contratação só é habilitada quando US2 e fluxo pago estiverem integrados.

## Phase 4: US2 — Pagar o guincheiro escolhido (P1)

**Goal**: reserva dirigida e cobrança única do total confirmado.
**Independent validation**: partir de uma cotação válida e verificar pending/failed/approved, concorrência e reabertura, sem exigir execução do transporte (quickstart 2–4).

- [X] T016 [US2] Criar create_guincho_request em `supabase/migrations/20260930135000_guincho_direct_reservation.sql`: revalidar cotação/oferta, reservar provider e criar search/appointment AWAITING_PAYMENT sem fila, operation_id idempotente e erro de ocupação antes da cobrança.
- [X] T017 [US2] Estender reserva/recovery/status/cancelamento SQL de Orders em `supabase/migrations/20260930136000_guincho_payment_context.sql` exigindo request/appointment/quote_version/amount/BRL consistentes, total real sem payment_test_mode PitTec e prioridade de intenção existente.
- [X] T018 [US2] Estender validação GUINCHO_DIRECT em `supabase/functions/mp-create-payment/index.ts` e helpers realmente usados em `supabase/functions/_shared/`, preservando chaves duráveis Pix/cartão, rejeitando contexto manipulado e mantendo modalidades antigas.
- [X] T019 [US2] Implementar reconciliação APPROVED e compensação tardia em `supabase/migrations/20260930137000_guincho_payment_reconciliation.sql`: reserva válida → PENDING uma vez com prazo de aceite vigente; cancelado/expirado → refund pipeline, sem ressuscitar reserva ou expor oferta.
- [X] T020 [US2] Implementar expiração de reserva/intenções canceladas e limpeza operacional em `supabase/migrations/20260930138000_guincho_reservation_expiry.sql`, com job/configuração vigente, revisão e operação idempotentes; não expirar confirmação/disputa e não avançar prestador.
- [X] T021 [US2] Integrar criar/recuperar pedido e checkout dirigido em `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt`, `features/payment/repository/PaymentRepository.kt` e `core/navigation/PitGoRoutes.kt`; quote_version/appointment/request e total confiáveis, recotação exige confirmação, somente total visível.
- [X] T022 [US2] Integrar simulação e recuperação dos estados pagos por flavor em `app/src/main/java/com/trustarx/pitgo/features/payment/repository/SimulatedPaymentGateway.kt`, `features/guincho/viewmodel/GuinchoMapViewModel.kt` e `core/navigation/PitGoNavHost.kt`; Dev sem MP, Homol/Prod integração existente cobrando total exibido, retry/Voltar sem duplicar ou cancelar silenciosamente.
- [X] T023 [US2] Validar valores fracionários, preço alterado, reserva simultânea, payload adulterado e aprovação tardia/reabertura em `specs/028-refinamento-guincho/quickstart.md`, registrando resultado e acompanhamento de compensações sem dados sensíveis. **Escopo ajustado em 30/09/2026:** usuário dispensou a execução de testes manuais pelo agente e assumiu essa validação; não declarar estes cenários/SC como executados.

## Phase 5: US3 — Aceitar e executar serviço pago (P1)

**Goal**: oferta exclusiva com dados autorizados e remuneração, aceite/recusa, Cheguei e início.
**Independent validation**: partir de pedido pago atribuído e verificar acesso de destinatário/outro usuário, recusa/timeout e sequência operacional (quickstart 5–6).

- [X] T024 [US3] Implementar respond_guincho_request/expiração de oferta em `supabase/migrations/20260930139000_guincho_provider_response.sql`, proprietário/revisão/deadline, aceite → ACCEPTED com prazo de serviço vigente, recusa/timeout → cancelar/refund integral sem advance_mobile_dispatch; preservar snapshots privados.
- [X] T025 [US3] Implementar arrive_guincho_appointment/start_guincho_transport e leitura de acompanhamento autorizada em `supabase/migrations/20260930140000_guincho_transport.sql`, Cheguei antes de início, timestamps idempotentes, posição privada após aceite e sem confirmação do cliente para iniciar.
- [X] T026 [US3] Integrar restore/getter/respond do dirigido em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/repository/MobileDispatchRepository.kt` e `features/mobiledispatch/viewmodel/ProviderIncomingRequestViewModel.kt`; só APPROVED libera oferta, manter legado sem ambiguidades.
- [X] T027 [US3] Renderizar dados/valor do guincheiro e ações aceitar/recusar em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ProviderIncomingRequestScreen.kt`; nome, veículo, retirada/destino e remuneração sem total/markup/taxas do cliente, erro recuperável.
- [X] T028 [US3] Integrar notificação privada, seleção do pedido correto e publicação de localização em `app/src/main/java/com/trustarx/pitgo/features/providertracking/notification/ProviderIncomingRequestNotifier.kt`, `features/providertracking/service/ProviderLocationTrackingService.kt` e `features/providertracking/repository/ProviderTrackingStateRepository.kt`; sem dados pessoais em lockscreen/logs, suporte GUINCHO_DIRECT e deduplicação por revisão.
- [X] T029 [US3] Integrar Cheguei/Iniciar transporte e mapa do cliente/prestador em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`, `features/appointmenttracking/ui/AppointmentTrackingScreen.kt` e `core/navigation/PitGoNavHost.kt`; usar estado dirigido e manter comportamento PitTec anterior.
- [X] T030 [US3] Validar oferta exclusivamente paga, privacidade, recusa/reembolso, expiração e chegada/início em `specs/028-refinamento-guincho/quickstart.md`, sem encaminhamento ao segundo profissional. **Escopo ajustado em 30/09/2026:** usuário dispensou a execução de testes manuais pelo agente e assumiu essa validação; não declarar estes cenários/SC como executados.

## Phase 6: US4 — Conclusão bilateral e contestação (P1)

**Goal**: conclusão confirmada, contestação libera novas operações e suporte resolve pelo dashboard.
**Independent validation**: partir de pedido IN_PROGRESS, concluir/reabrir/confirmar; em outro pedido contestar, atender novo pedido e resolver o caso antigo sem interferência (quickstart 6–9).

- [X] T031 [US4] Criar request_guincho_completion/confirm_guincho_completion em `supabase/migrations/20260930141000_guincho_completion.sql` com participantes/revisão/op, IN_PROGRESS → espera → COMPLETED, JSON null seguro, liberação só após confirmação e espera sem autoexpiração.
- [X] T032 [US4] Implementar dispute_guincho_completion e list_my_guincho_appointments em `supabase/migrations/20260930142000_guincho_disputes.sql`: motivo obrigatório, caso único, DISPUTED fora de exclusividade operacional, limpar localização e liberar provider, histórico paginado com resultado/refund.
- [X] T033 [US4] Criar fila/detalhe/resolução dashboard em `supabase/migrations/20260930143000_guincho_support_resolution.sql`, MANAGER+ ativo, justificativa/revisão/lock/auditoria, COMPLETE sem refund ou CANCEL_FULL_REFUND pelo pipeline; caso antigo não altera reserva/pedido atual.
- [X] T034 [P] [US4] Atualizar enums/dados de conclusão/contestação e erros em `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoModels.kt`, `features/guincho/data/GuinchoDtos.kt` e `features/mobiledispatch/data/MobileDispatchDtos.kt`; todos os estados decodificam com nulls opcionais sem perder snapshots.
- [X] T035 [US4] Implementar concluir/confirmar/contestar e feedback no acompanhamento em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` e `features/appointmenttracking/ui/AppointmentTrackingScreen.kt`; card sempre visível, motivo obrigatório, evitar sucesso falso/duplicação.
- [X] T036 [US4] Integrar restauração/retorno inicial por ID/revisão em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/DispatchResumeViewModel.kt`, `features/providerdashboard/viewmodel/ProviderDashboardViewModel.kt`, `features/providertracking/repository/ProviderTrackingStateRepository.kt` e `core/navigation/PitGoNavHost.kt`; espera retoma conclusão, DISPUTED não disputa novo pedido e terminal antigo não fecha tela nova.
- [X] T037 [US4] Criar histórico/detalhe de contestação em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoHistoryViewModel.kt`, `features/guincho/ui/GuinchoHistoryScreen.kt` e `features/guincho/repository/GuinchoRepository.kt`; integrar acesso para cliente/prestador por `core/navigation/PitGoNavHost.kt` e textos em `app/src/main/res/values/strings.xml`.
- [X] T038 [US4] Integrar contratos de suporte no dashboard em `/Users/brunocesar/projetos-web/pitgo/src/services/contestacoes.ts` e `/Users/brunocesar/projetos-web/pitgo/src/types/database.ts`, autorização MANAGER e tipos gerados do backend atualizado, sem service-role no browser.
- [X] T039 [US4] Implementar fila/filtros/paginação e navegação em `/Users/brunocesar/projetos-web/pitgo/src/app/(dashboard)/contestacoes/page.tsx` e componente de navegação existente identificado na T001; renderizar casos abertos/resolvidos com acesso autorizado.
- [X] T040 [US4] Implementar detalhe, formulário/ação de decisão e feedback em `/Users/brunocesar/projetos-web/pitgo/src/app/(dashboard)/contestacoes/[id]/page.tsx`, `actions.ts` no mesmo diretório e `/Users/brunocesar/projetos-web/pitgo/src/components/contestacoes/ResolveDisputeForm.tsx`; justificativa obrigatória, confirmação, revisão/duplo toque e estados refund pending/failed.
- [X] T041 [US4] Integrar simulação de conclusão/contestação e seus históricos sem MP no Dev em `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoRepository.kt` e `core/app/PitGoAppContainer.kt`, reutilizando o gateway simulado e evitando marcar pagamento Dev como integração aprovada em Prod.
- [X] T042 [US4] Validar confirmação, retomada, contestação, suporte com/sem permissão e nova contratação durante caso antigo em `specs/028-refinamento-guincho/quickstart.md`; registrar primeira decisão/reembolso único e preservação do novo pedido. **Escopo ajustado em 30/09/2026:** usuário dispensou a execução de testes manuais pelo agente e assumiu essa validação; não declarar estes cenários/SC como executados.

## Phase 7: Polish and Cross-Cutting Validation

- [X] T043 Revisar grants/RLS, índices/jobs/retention, todos os parsers/enums, idempotência e cancelamento pelo cliente em `supabase/migrations/20260930130000_guincho_direct_schema.sql` até `20260930143000_guincho_support_resolution.sql` e `specs/028-refinamento-guincho/contracts/guincho-api.md`; criar migration corretiva se necessário, proteger dados de prestadores/clientes e registrar autorização de cancelamento/resultado financeiro vigente.
- [X] T044 Executar builds Dev/Homol/Prod e lint Android a partir de `app/build.gradle.kts`, Deno check das funções alteradas em `supabase/functions/` e typecheck/lint/build em `/Users/brunocesar/projetos-web/pitgo/package.json`; corrigir falhas e registrar validações em `specs/028-refinamento-guincho/quickstart.md`.
- [X] T045 Executar rollout das migrations e Edge Functions já validadas no projeto de entrega explicitamente selecionado em `supabase/config.toml`, conferir regras/jobs/schema cache antes do APK e integrar dashboard; registrar release/dependências em `specs/028-refinamento-guincho/quickstart.md`, preservando o vínculo Homol/Prod e sem novas credenciais versionadas.
- [X] T046 Executar todos os cenários ponta a ponta/regressões e SC-001–008 de `specs/028-refinamento-guincho/quickstart.md`, incluindo amostra de 20 mudanças para SC-004, fonte ampliada/temas/mapa e funcionamento PitTec/BROWSE/legado; instalar build correto nos devices alvo e encerrar reservas de validação/acompanhar refunds antes de concluir. **Escopo ajustado em 30/09/2026:** usuário dispensou a execução de testes manuais pelo agente e assumiu essa validação; não declarar estes cenários/SC como executados.

## Dependencies and Execution Order

```text
Setup T001–002 → Foundation T003–006
 → US1 T007–015 → US2 T016–023 → US3 T024–030 → US4 T031–042
 → Polish T043–046
```

Todas as histórias P1: a ordem reflete o fluxo e dependências reais, não uma mudança de prioridade. Validação independente usa um pedido/cotação existente na etapa adequada; não exige refazer toda a jornada para validar uma história, mas a entrega financeira completa exige as quatro.

T015, T023, T030 e T042 incluem preparar incrementalmente o backend de validação explicitamente selecionado: aplicar as migrations concluídas e implantar as funções necessárias à história com `npx supabase`, conferir regras/jobs/schema cache e compatibilidade do APK antes de executar os cenários. T038 requer esse backend atualizado para gerar tipos. Registrar projeto, migrations, versões de funções e resultados em `specs/028-refinamento-guincho/quickstart.md`. Usar Dev para simulação e validar integrações MP no ambiente apropriado sem confundir Homol, que compartilha Prod, com uma base isolada. T045 é o rollout de entrega após esses checkpoints, não o primeiro preparo do backend. A geração e revisão destas tarefas não executam deployments.

- T003 → T004 → T006; T005 pode acontecer com T004, mas T006 requer ambos.
- US1: T007 → T008 → T009 → T010 → T011 → T012 → T013/014 → T015.
- US2: reserva T016 antes de contexto T017 e reconciliação T019; T018 requer contrato SQL fechado; expiração T020 depende da reconciliação. UI T021/022 segue contratos prontos.
- US3: T024/025 → repository T026 → UI/notificação/tracking T027–029 → T030.
- US4: T031 → T032 → T033; T034 pode ocorrer junto de T033 depois de T032. T035–037 consomem backend/modelos; T038–040 consomem T033 e não editam arquivos Android. T041 depende do fluxo real definido, T042 exige ambos.
- T045 só depois de T044 aprovada; T046 depois de rollout preparado. Nenhuma alteração de produção nesta geração de tarefas.

## Parallel Examples per Story

| História | Oportunidade após dependências prontas |
| --- | --- |
| US1 | Depois de T013, textos/acessibilidade T014 podem ser refinados enquanto outro executor revisa queries T007–009; validação T015 espera integração final. |
| US2 | Após T017, Edge T018 e reconciliação SQL T019 em arquivos distintos; T021/022 compartilham arquivos e ficam sequenciais. |
| US3 | Após T026, UI T027 e notificações T028 em arquivos distintos; tracking T029 aguarda ambos. |
| US4 | Após T033/034, Android T035–037 e dashboard T038–040 em worktrees distintas; nenhuma escrita concorrente em arquivos compartilhados. |

`[P]` identifica tarefas imediatamente independentes no respectivo estágio (T002 em relação a T001; T005 junto de T004; T034 junto de T033). Exemplos condicionais acima não autorizam iniciar tarefas antes de seus pré-requisitos.

## Requirement Coverage

| Requisitos | Tarefas principais |
| --- | --- |
| FR-001–005 | T007, T010–015, T016 |
| FR-006–010 | T008–009, T016–018, T021–023 |
| FR-011–013 | T004, T006, T017–020, T024–030 |
| FR-014–017 | T031, T034–036, T042 |
| FR-018 | T016–020, T024–025, T031–033, T043 |
| FR-019 | T022, T041, T044–046 |
| FR-020 | T025, T029–030 |
| FR-021–023 | T032–040, T042–043 |

## Implementation Strategy

MVP demonstrável: Setup + Foundation + US1, mapa e cotação sem transações. Não habilitar Contratar em release enquanto US2/US3 não suportarem o pedido pago. Entrega financeira utilizável inclui US2/US3 e conclusão normal US4; o escopo solicitado só está completo com contestação e dashboard US4.

Implementar incrementalmente, validar cada checkpoint e preservar compatibilidade legada. Sem criar suítes/fixtures novas. Não marcar tarefas completas só por criar arquivos; evidência deve cobrir integrações reais. Hooks before/after_tasks ausentes (`.specify/extensions.yml` inexistente).

## Ajuste autorizado de validação

Em 30/09/2026, o usuário informou: “não precisa testar, o teste eu faço manualmente”. T015/T023/T030/T042/T046 foram encerradas por transferência da validação manual ao usuário, sem alegar evidência ponta a ponta ou coleta SC-004. Instalação Homol nos quatro devices continua no escopo. Builds/lint e verificações já executadas constam no quickstart.
