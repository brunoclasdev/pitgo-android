# Tasks: Guincho por porte e retomada da busca

**Input**: Artefatos de `specs/036-guincho-porte-busca/`: spec.md, plan.md, research.md, data-model.md, contracts/ e quickstart.md.

**Prerequisites**: Plano aprovado, duas decisões de clarify incorporadas e checklist de requisitos revisado.

**Tests**: Não gerar testes automatizados, mocks, fixtures ou utilitários, conforme constituição. As verificações abaixo são compilação, revisão de contratos e walkthrough manual solicitado pelos critérios de aceitação.

**Organization**: Tarefas agrupadas pelas quatro histórias da especificação, em ordem P1/P1/P1/P2. Cada checkpoint mede o incremento da história; a conclusão da feature exige a integração entre elas e a validação final.

## Format: `[ID] [P?] [Story] Description`

`[P]` identifica tarefas em arquivos diferentes que podem executar em paralelo quando seus pré-requisitos explícitos estiverem completos. Não autoriza execução concorrente em arquivos compartilhados. Todos os caminhos são relativos à raiz do repositório; arquivos novos estão identificados como criação na tarefa. Prefixos SQL abaixo são propostos e devem permanecer únicos/ordenados após a última migration existente.

## Path Conventions

- Android: `app/src/main/java/com/trustarx/pitgo/`; recursos: `app/src/main/res/`.
- Backend: `supabase/migrations/` e `supabase/functions/guincho-quote/index.ts`.
- Evidências/contratos: `specs/036-guincho-porte-busca/`.
- Dashboard: repositório externo ainda não disponível; integração explicitamente pendente, com contrato local como referência, sem inventar caminho de código externo.

## Phase 1: Setup — contexto e integração

Confirmar o ponto de partida e as dependências sem adicionar infraestrutura.

- [X] T001 Revisar contratos e registrar em `specs/036-guincho-porte-busca/quickstart.md` os pré-requisitos disponíveis para homol, backend isolado, contas e dashboard externo, sem copiar credenciais; preservar aceite antes do pagamento e não iniciar implantação remota.
- [X] T002 Rastrear a partir de `supabase/migrations/20260930154000_guincho_accept_before_payment.sql` e `supabase/migrations/20260930137000_guincho_payment_reconciliation.sql` os caminhos atuais de reserva, cancelamento, triggers e worker de reembolso; registrar os pontos concretos de extensão em `specs/036-guincho-porte-busca/research.md`, incluindo ambas as reservas e `reserve_order_payment`.

**Checkpoint**: Etapa concluída com evidência; pendências reais permanecem desmarcadas.

---

## Phase 2: Foundational — base aditiva compartilhada

Bloqueia o início das histórias. Nesta fase, criar estruturas sem ativar exigências novas nos fluxos antigos.

- [X] T003 Criar `supabase/migrations/20261103090000_guincho_transport_catalog_schema.sql` com `vehicle_transport_categories`, `guincho_sizes`, relação N:N e `guincho_size_prices` versionada; FKs opcionais em `vehicles`, `provider_vehicles`, `providers` e `guincho_quotes`, revision do veículo do provider, índices/unicidade de preço vigente e RLS/grants mínimos conforme `data-model.md`; não inventar classificações, preços ou backfill comercial.
- [X] T004 Criar `supabase/migrations/20261103091000_guincho_catalog_admin_rpc.sql` com as quatro RPCs administrativas de `contracts/dashboard-catalog.md`, gate MANAGER, auditoria, lock/revisão e request_id com comparação de payload; preservar preço histórico e desativação lógica e expor `get_guincho_transport_catalog` somente com catálogo ativo para usuários autenticados.
- [X] T005 Criar modelos e DTOs de catálogo em `app/src/main/java/com/trustarx/pitgo/core/vehicle/model/TransportCatalogModels.kt` e `app/src/main/java/com/trustarx/pitgo/core/vehicle/data/TransportCatalogDtos.kt`, além de `app/src/main/java/com/trustarx/pitgo/core/vehicle/repository/TransportCatalogRepository.kt`; implementar leitura de portes/categorias, validação de contrato e falhas explícitas de sessão, offline e catálogo vazio, sem consultas em UI.
- [X] T006 Disponibilizar o repository de catálogo em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt` e injetá-lo pelas factories existentes usadas em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, mantendo a construção simples e o isolamento por sessão; não introduzir framework de DI.

**Checkpoint**: Etapa concluída com evidência; pendências reais permanecem desmarcadas.

---

## Phase 3: US1 — Retomar busca após recusa (P1, MVP)

**Goal**: Retomar a descoberta com viagem preservada e recusantes excluídos, inclusive após reabrir o app.

**Independent Test**: Solicitar no fluxo atual, recusar e verificar retorno em até 3 segundos após confirmação recebida; atualizar/reabrir preserva dados e exclusões; trocar um dado confirmado encerra a exclusão. Validar também pedido pago legado e prioridade de atendimento novo.

- [X] T007 [US1] Criar `supabase/migrations/20261103092000_guincho_search_context_schema.sql` com contextos e exclusões de `data-model.md`, um contexto aberto por cliente, FK de source_appointment_id, PK de exclusão por contexto/geração/provider, índices e leitura RLS do proprietário; revogar escrita direta e não duplicar endereços do draft.
- [X] T008 [US1] Criar `supabase/migrations/20261103093000_guincho_search_context_lifecycle.sql` para integrar `save_guincho_trip_draft` e ambas as funções de reserva ao contexto persistente: geração só muda com veículo/retirada/destino confirmados diferentes, não em refresh/revision técnica; ligar cada novo appointment sem reabrir pedido financeiro cancelado, encerrar somente o contexto do pedido correspondente em conclusão, cancelamento voluntário, expiração ou outro encerramento definitivo, exceto recusa explícita tratada por T009; preservar prazos, navegação e regras financeiras existentes, sem gerar exclusão nesses outros encerramentos nem alterar contexto de outro atendimento; manter locks/retries existentes.
- [X] T009 [US1] Criar `supabase/migrations/20261103094000_guincho_decline_search_resume.sql` estendendo `respond_guincho_request` para registrar exclusão e retomar contexto atomicamente com PROVIDER_DECLINED; para pedido anterior à implantação sem contexto, criá-lo uma única vez a partir do rascunho/cotação vinculados ao appointment, validando propriedade do mesmo cliente e preservando veículo/retirada/destino, sem sobrescrever contexto de viagem mais recente; não alterar contexto de atendimento novo quando evento antigo chegar, acumular recusas sem duplicar, filtrar exclusões em `discover_guincho_providers` e implementar `get_my_guincho_search_resume` com prioridade ACTIVE_DISPATCH > RESUMABLE_SEARCH > NONE e causa explícita de recusa no snapshot autorizado.
- [X] T010 [US1] Completar em `supabase/migrations/20261103094500_guincho_paid_decline_refund_guard.sql` a ligação de recusa paga legada com o fluxo financeiro encontrado no Setup, somente se faltar; garantir uma única solicitação de reembolso, manter falhas/pêndencias acessíveis e permitir busca sem aguardar conclusão financeira; se a ligação já for suficiente, não criar migration vazia e registrar a evidência em `specs/036-guincho-porte-busca/quickstart.md`.
- [X] T011 [P] [US1] Adicionar união tipada de retomada/contexto e mapeamento DTO em `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoDtos.kt`, e adaptar projeção de recusa em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/model/MobileDispatchModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/data/MobileDispatchDtos.kt`, preservando leitura de snapshots legados; pode executar junto das migrations US1 após contrato fechado.
- [X] T012 [US1] Implementar recuperação autenticada em `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoRepository.kt` e restauração/reconciliação em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoMapViewModel.kt`: preencher viagem do servidor, entrar em DISCOVERY, limpar selection/quote/checkout/idempotência da reserva anterior, invalidar trabalhos assíncronos antigos e preservar contexto ao reconectar; mapear vazio e erros sem perder input.
- [X] T013 [US1] Integrar retorno somente por recusa do pedido corrente em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/DispatchResumeViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`; impedir loops/eventos repetidos, não redirecionar histórico e priorizar atendimento operacional novo; usar `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` para argumentos se necessários.
- [X] T014 [US1] Apresentar aviso de recusa, restauração, indisponibilidade e nova tentativa em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoMapScreen.kt` e `app/src/main/res/values/strings.xml`; oferecer acesso ao histórico financeiro existente sem fazer contratação/pagamento automáticos.
- [ ] T015 [US1] Executar os cenários US1 de `specs/036-guincho-porte-busca/quickstart.md` com recusa sem pagamento, paga legada e pedido criado antes da implantação sem contexto, inclusive recusa com app fechado e retry que deve criar só um contexto/exclusão; verificar cancelamento voluntário, expiração e conclusão fechando apenas seu contexto, sem exclusão ou retorno por recusa; incluir evento duplicado/atrasado, viagem mais recente, logout, conexão perdida e mudança dos dados; registrar duração do retorno e resultados sem dados pessoais; marcar concluído somente com evidências.

**Checkpoint**: Retomada e exclusões verificadas sem alterar pagamento; US1 é o MVP funcional.

---

## Phase 4: US2 — Oferecer guincho compatível (P1)

**Goal**: Classificar veículos e guinchos e filtrar por capacidade explicitamente declarada.

**Independent Test**: Usar catálogo controlado administrado pelas RPCs e dois guincheiros; micro-ônibus não pode visualizar/cotar/reservar guincho incompatível. Verificar regularização de legados e seleção de veículo de atendimento. Validação financeira completa fica em US3.

- [X] T016 [US2] Criar `supabase/migrations/20261103095000_guincho_vehicle_classification_rpc.sql` para salvar `vehicles.transport_category_id` pelas RPCs atuais de cadastro/edição, incluir categoria no vehicle_snapshot do draft, estender `create_provider_registration` com porte obrigatório GUINCHO e escolha inicial do dispatch_vehicle_id, e implementar `set_my_guincho_vehicle`/`select_my_guincho_dispatch_vehicle` com posse, revisão e idempotência; manter outras modalidades compatíveis.
- [X] T017 [P] [US2] Atualizar categoria/erros de formulário em `app/src/main/java/com/trustarx/pitgo/core/vehicle/model/VehicleModels.kt`, `app/src/main/java/com/trustarx/pitgo/core/vehicle/data/VehicleDtos.kt`, `app/src/main/java/com/trustarx/pitgo/core/vehicle/repository/VehicleRepository.kt` e `app/src/main/java/com/trustarx/pitgo/core/vehicle/validation/VehicleValidation.kt`; suportar nulo legado sem classificá-lo implicitamente e serializar categoria na mutação. Pode executar junto à migration anterior usando contrato fechado.
- [X] T018 [US2] Adicionar seleção de categoria com catálogo, loading/erro/retry e preservação de input em `app/src/main/java/com/trustarx/pitgo/features/garage/viewmodel/VehicleFormViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/garage/ui/VehicleFormScreen.kt`; permitir regularizar veículo existente e exigir classificação antes de usá-lo na busca de guincho.
- [X] T019 [P] [US2] Verificar que `app/src/main/java/com/trustarx/pitgo/features/onboarding/viewmodel/VehicleOnboardingViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/onboarding/ui/VehicleOnboardingScreen.kt` encaminham à rota compartilhada em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`; reutilizar a seleção de categoria implementada em `app/src/main/java/com/trustarx/pitgo/features/garage/ui/VehicleFormScreen.kt` no modo ONBOARDING, sem duplicar formulário ou inferir categoria por modelo, preservando input em falhas. Pode executar junto ao formulário de garagem depois dos modelos de veículo.
- [X] T020 [US2] Adicionar porte e validação em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerregistration/data/ProviderRegistrationDtos.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`; enviar ID ativo com o veículo, preservando placa/marca/modelo/cor e demais modalidades.
- [X] T021 [US2] Integrar escolha obrigatória de porte no cadastro em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`; renderizar catálogo/erros e impedir conclusão GUINCHO sem porte, sem permitir preço informado pelo prestador.
- [X] T022 [US2] Implementar leitura/edição de porte de veículo legado, cadastro do primeiro veículo quando ausente e seleção explícita do veículo de atendimento em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/repository/ProviderDashboardRepository.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/model/ProviderDashboardModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/viewmodel/ProviderDashboardViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoProviderHomeScreen.kt`; consumir RPCs US2, oferecer acesso no perfil GUINCHO e não escolher o maior veículo automaticamente.
- [X] T023 [US2] Criar `supabase/migrations/20261103100000_guincho_capacity_eligibility.sql` aplicando o mesmo predicado de categoria/porte/compatibilidade/veículo próprio e exclusão a `discover_guincho_providers`, `store_guincho_quote` e ambas as reservas; validar nulos legados e mudanças de capacidade, capturar identidade/categoria/porte nos snapshots e preservar critérios atuais de disponibilidade/localização; se alterar RETURNS TABLE, reconstruir grants/dependências de forma transacional.
- [X] T024 [US2] Consumir identidade/porte e falhas de capacidade em `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoDtos.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoRepository.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoMapViewModel.kt` e `supabase/functions/guincho-quote/index.ts`; invalidar seleção/quote ao mudar veículo ou oferta, exigir categoria e mapear novos erros estáveis sem mascará-los como falha genérica.
- [ ] T025 [US2] Integrar mensagens/ações de completar classificação, capacidade incompatível e recotação em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoMapScreen.kt` e `app/src/main/res/values/strings.xml`; executar cenários US2 de `specs/036-guincho-porte-busca/quickstart.md`, incluindo chamadas diretas incompatíveis, frota e legados, e registrar resultados.

**Checkpoint**: Cadastro/classificação e enforcement de capacidade verificáveis; a política monetária final é concluída na US3.

---

## Phase 5: US3 — Preço por porte e dashboard (P1)

**Goal**: Aplicar valores administrados por porte a novas cotações, preservando propostas vigentes e integração administrativa completa.

**Independent Test**: Administrar dois portes com valores distintos, conferir composição e negar fallback quando faltar preço; mudar preço e confirmar preservação de cotação válida/pedido confirmado. Interface externa é necessária para concluir a história de produto.

- [X] T026 [US3] Criar `supabase/migrations/20261103101000_guincho_size_quote_pricing.sql` estendendo `store_guincho_quote` para resolver preço vigente do porte e regra comum na mesma transação, capturar size_price_id/version e valores e eliminar fallback global; aplicar composição/arredondamento de `data-model.md`, omitir oferta sem preço em descoberta e não ativar guincheiro_price no cálculo.
- [X] T027 [US3] Criar `supabase/migrations/20261103102000_guincho_size_reservation_pricing.sql` atualizando `create_guincho_request_before_payment`, `create_guincho_request`, `reserve_order_payment` e validação da simulação homol quando aplicável; usar snapshots e revisões sem exigir que a tarifa histórica ainda esteja ativa, manter guards de capacidade/exclusão da US2 e preservar aceite antes de pagamento e pedidos legados.
- [X] T028 [US3] Criar `supabase/migrations/20261103103000_guincho_size_pricing_reads.sql` para ajustar `get_current_service_pricing` e `get_dashboard_provider_pricing`, identificando guincho por porte e separando leitura móvel da administrativa; preservar PitTec, histórico, regra comum e driver price reservado e impedir que valor global legado apareça como nova oferta contratável.
- [X] T029 [P] [US3] Estender preço/versionamento em `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoDtos.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoRepository.kt` para snapshot por porte, mantendo BigDecimal e compatibilidade legada; atualizar `supabase/functions/guincho-quote/index.ts` para resposta/erros sem aceitar preço do aplicativo. Pode executar junto às migrations US3 depois da US2 usando contrato fechado.
- [X] T030 [US3] Atualizar cotação e indisponibilidade de preço em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoMapViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoMapScreen.kt` e `app/src/main/res/values/strings.xml`; mostrar total retornado e porte correto, impedir contratação sem tarifa e não alterar valor de proposta vigente após simples atualização administrativa.
- [ ] T031 [US3] Resolver a dependência externa identificando e registrando em `specs/036-guincho-porte-busca/contracts/dashboard-catalog.md` o repositório acessível, caminhos reais dos arquivos de interface/cliente RPC e responsável pela integração antes de iniciar sua execução; em seguida, integrar no frontend externo do dashboard os formulários de categorias, portes, compatibilidade múltipla e preço conforme `specs/036-guincho-porte-busca/contracts/dashboard-catalog.md`, com conflitos/retries/permissões e estado sem tarifa; registrar nesse contrato o repositório, caminhos reais dos arquivos e revisão da integração quando disponíveis. Dependência externa: o frontend não está no workspace; não criar caminho fictício, não substituir a UI por documentação e manter esta tarefa pendente enquanto não for executada no repositório correto.
- [ ] T032 [US3] Validar autorização, auditoria, idempotência e edição concorrente pelas RPCs de `supabase/migrations/20261103091000_guincho_catalog_admin_rpc.sql`, usando contas MANAGER, staff insuficiente, cliente, prestador e anon; executar cenários administrativos/backend de `specs/036-guincho-porte-busca/quickstart.md` e registrar evidências sem segredos.
- [ ] T033 [US3] Executar pelo dashboard integrado e Android os cenários US3 de `specs/036-guincho-porte-busca/quickstart.md`: dois preços, mudança de tarifa, mudança de capacidade, falta de preço, snapshots antigos e pedido pago legado; conferir total e ausência de fallback global e registrar resultados. Depende de T031 efetivamente concluída no repositório externo identificado; manter T033 pendente enquanto essa integração não estiver disponível, sem impedir execução das tarefas locais independentes.

**Checkpoint**: Preço por porte e administração somente completos com RPCs, app e dashboard externo verificados.

---

## Phase 6: US4 — Destino/status no topo e bordas roxas (P2)

**Goal**: Priorizar destino e status nas respectivas telas e padronizar bordas sem redesenhar outras modalidades.

**Independent Test**: Abrir mapa e home: destino no topo, status primeiro card e bordas roxas em todos os cards dessas duas telas, em claro/escuro, tela compacta, fonte ampliada e teclado aberto.

- [X] T034 [P] [US4] Reposicionar o uso de `app/src/main/java/com/trustarx/pitgo/core/ui/AddressEntryMapCard.kt` no topo de `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoMapScreen.kt`, abaixo do cabeçalho/insets, mantendo rolagem e IME e reposicionando controles do mapa que ficariam cobertos; não mudar alinhamento de outras telas que usam o componente.
- [X] T035 [P] [US4] Mover status/disponibilidade para primeiro card acima do mapa em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoProviderHomeScreen.kt` e aplicar bordas roxas aos cards da home, incluindo `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoIncomingRequestCard.kt`, reutilizando tema/BorderStroke atuais; preservar switch, ações, rótulos e acesso ao perfil.
- [ ] T036 [US4] Revisar todos os cards das duas telas em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoMapScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoProviderHomeScreen.kt`, completar bordas ausentes e executar US4 de `specs/036-guincho-porte-busca/quickstart.md` em temas, fontes, teclado e orientação suportados; registrar resultado e eventuais ajustes de espaçamento com tokens existentes.

**Checkpoint**: Hierarquia visual e bordas verificadas nas duas telas.

---

## Phase 7: Polish & Cross-Cutting Concerns

Concluir verificações integradas sem criar suítes de testes ou alterar escopo.

- [ ] T037 Aplicar as novas migrations de `supabase/migrations/` em backend isolado com `supabase migration up --local`, verificar grants/RLS, constraints, índices, snapshots antigos e ausência de classificações inventadas; executar `supabase/functions/guincho-quote/index.ts` no ambiente de validação e registrar resultados em `specs/036-guincho-porte-busca/quickstart.md`, sem publicação remota implícita.
- [ ] T038 Compilar a variante homol definida em `app/build.gradle.kts` com `./gradlew :app:assembleHomolDebug`, executar o walkthrough integrado de `specs/036-guincho-porte-busca/quickstart.md`, incluindo aceite/pagamento/vistoria/conclusão e regressão de PitTec/loja/histórico, corrigir falhas diretamente relacionadas e registrar evidências.
- [ ] T039 Executar validação de usabilidade SC-007 de `specs/036-guincho-porte-busca/spec.md` com pelo menos cinco participantes por perfil, verificando 80% sem ajuda em até 10 segundos; registrar resultados em `specs/036-guincho-porte-busca/quickstart.md`. Se participantes não estiverem disponíveis, deixar pendente, sem simular aprovação.
- [ ] T040 Revisar diffs e executar `git diff --check` para os arquivos alterados; atualizar `specs/036-guincho-porte-busca/quickstart.md` com estado real de SC-001–007, dependência do dashboard e prontidão operacional do catálogo/matriz/valores; marcar tarefas de `specs/036-guincho-porte-busca/tasks.md` somente quando efetivamente concluídas, sem considerar integração externa ou validação pendente como entrega completa.

**Checkpoint**: Etapa concluída com evidência; pendências reais permanecem desmarcadas.

---

## Dependencies & Execution Order

### Phase Dependencies

Setup T001–T002 → Foundational T003–T006 → histórias. T003 cria somente schema; T004 cria administração compartilhada, mas não ativa novos filtros. T005 consome o contrato definido e T006 integra a injeção.

```text
Setup → Fundação → US1 (T007–T015) → US2 (T016–T025) → US3 (T026–T033)
                 └→ US4 (T034–T036, em arquivos livres)                 │
                                  US1 + US2 + US3 + US4 → Final T037–T040
```

Ordem padrão sequencial: T001 a T040. US1 pode ser demonstrada antes da capacidade/preço novos, com dados/fluxo existentes. Não ativar restrições incompletas em produção como parte de uma entrega parcial.

### User Story Dependencies

| História | Pré-requisitos | Independência de validação |
|---|---|---|
| US1 | Fundação | Recusa/retomada/exclusões independem de novos preços e da UI externa. |
| US2 | Fundação; preservar filtros de exclusão US1 ao redefinir as mesmas funções | Pode validar classificação e compatibilidade com catálogo controlado cadastrado via RPC; política de preço definitivo é US3. |
| US3 | Fundação e identidade/enforcement US2; preservar lifecycle US1 | Cotações e snapshots podem ser validados diretamente; administração fim a fim requer T031 externo. |
| US4 | Fundação; coordenar arquivos do mapa/home com US1–US3 | Layout verificável com estado de tela válido, sem depender da nova política de preço. |

T031 é dependência externa explícita; se faltar acesso ao repositório do dashboard, continuar o trabalho local independente e manter T031/T033 pendentes. T039 depende de participantes reais. Essas dependências não impedem gerar ou executar as tarefas locais, mas impedem declarar toda a feature concluída.

### Within Each User Story

- Modelos/contratos antes de Repository/ViewModel/UI; SQL autoritativo antes de validar o fluxo integrado.
- US1: T007 → T008 → T009 → T010; T011 pode executar em paralelo com T007–T010 após contrato fechado; T012 requer T009/T011; T013 → T014 → T015 depois da recuperação integrada.
- US2: T016 e T017 podem coexistir com contrato fechado; T018/T019 requerem T017 e catálogo T005. T020 → T021; T022 depende de T016. T023 requer as estruturas e lifecycle anteriores; T024 requer T023 e T017/T020; T025 fecha integração. T019 é paralelo a T018, não a alterações nos mesmos modelos.
- US3: T026 → T027 → T028; T029 pode coexistir com esse grupo SQL depois de US2. T030 requer T029/T026. T031 só pode iniciar a integração externa depois de identificar repositório/arquivos reais e concluir o contrato/RPC administrativo T004; T033 permanece pendente até a integração externa concluída; T032 requer T004, T026–T028; T033 requer T030–T032.
- US4: T034 e T035 são paralelos entre si; T036 requer ambos. Não executar T034 enquanto outra tarefa modifica GuinchoMapScreen.
- Final: T037–T040 após integração dos incrementos; limitações externas ou de ambiente devem ser registradas, sem marcar validação não executada.

## Parallel Examples by User Story

| História | Exemplo seguro | Condição |
|---|---|---|
| US1 | T007–T010 (SQL sequencial) em paralelo a T011 (modelos/DTOs) | Contrato de recuperação fechado; sem alterações nos mesmos arquivos. |
| US2 | T018 (garagem) em paralelo a T019 (onboarding) | T017/T005 concluídas; cada tarefa modifica apenas sua feature. |
| US3 | T026–T028 (SQL sequencial) em paralelo a T029 (Kotlin/Edge) | US2 concluída e contrato de resposta estável. |
| US4 | T034 (mapa) em paralelo a T035 (home/card de solicitação) | Arquivos não ocupados por US1–US3. |

Não paralelizar migrations dependentes nem tarefas que compartilham `GuinchoModels.kt`, `GuinchoDtos.kt`, `GuinchoRepository.kt`, `GuinchoMapViewModel.kt`, `GuinchoMapScreen.kt`, navegação ou strings.

## Implementation Strategy

### MVP First

Concluir Setup + Fundação + US1, compilar e validar recusa/recuperação/exclusões. Esse incremento resolve o bloqueio do cliente com o fluxo de contratação atual; não representa entrega de cadastro/compatibilidade/preço por porte.

### Incremental Delivery

Adicionar classificação/enforcement US2, depois cobrança por porte e administração US3, e concluir layout US4. Alterações de preço nunca reescrevem snapshots e cada nova contratação após recusa cria pedido financeiro distinto. Catalogar portas de ativação e preparar a classificação dos legados antes da liberação, sem atribuir capacidade presumida.

A operação cadastra no dashboard o catálogo/matriz/valores reais e regulariza os veículos. Validar administração apenas por RPC pode comprovar o backend, mas não substitui a UI externa prevista em T031/T033. Finalizar com compilação, segurança/concorrência, regressão e usabilidade antes de afirmar conclusão integral.

## Requirement Coverage

| Requisitos | Tarefas principais |
|---|---|
| FR-001–004: recusa, busca, histórico e recuperação | T007–T015 |
| FR-005: porte obrigatório e catálogo compartilhado | T003–T006, T016, T020–T022 |
| FR-006–009: matriz, classificação e revalidação | T016–T025, T027 |
| FR-010: administração de portes, compatibilidades e preço | T004, T028, T031–T033 |
| FR-011–013: composição, validação e preservação monetária | T026–T030, T032–T033 |
| FR-014–017: topo, bordas e acessibilidade | T034–T036, T039 |
| SC-001–002 | T015, T038 |
| SC-003–004 | T025, T037–T038 |
| SC-005 | T032–T033, T037–T038 |
| SC-006–007 | T036, T039 |

## Notes

Os marcadores abaixo refletem o estado real da implementação; tarefas de validação não executadas permanecem pendentes. Não há tarefas de criação de testes automatizados. Novos arquivos só devem existir com implementação real; T010 não exige migration vazia se o fluxo financeiro existente já cumprir o contrato. Não atualizar marcadores do checklist de requisitos para simular progresso de implementação. T040 consolida evidências sem encobrir dependências externas.


## Estado de execução — 2026-11-03

30 tarefas de implementação/preparação concluídas. Permanecem pendentes T015, T025, T031, T032, T033, T036, T037, T038, T039 e T040.

- T015/T025/T036: código implementado; walkthrough e verificação visual em dispositivo ainda não executados.
- T031/T033: frontend externo não disponível/identificado; RPCs e contrato locais implementados, sem afirmar integração administrativa concluída.
- T032/T037: análise de sintaxe SQL/PLpgSQL concluída, mas sem execução das migrations, verificação real de RLS/concorrência ou contas em banco isolado; Docker/Podman não disponível.
- T038: `assembleHomolDebug` aprovado e APK gerado; walkthrough operacional/regressão real pendente, portanto tarefa não marcada concluída.
- T039: participantes de usabilidade não disponíveis; avaliação não foi simulada.
- T040: revisão de diffs concluída; prontidão operacional, matriz comercial e resultados SC-001–007 não podem ser aprovados sem as validações pendentes.

T019 reutiliza o formulário de veículo já compartilhado por onboarding e garagem. T022 foi ampliada por solicitação posterior: a regularização fica em Perfil → Atualizar cadastro, na tela dedicada `features/profile/ui/RegistrationEditScreen.kt`, com entrada também para cliente, PitTec e loja. T009 implementa a recusa atomicamente via trigger de appointment acionado por `respond_guincho_request`, em vez de duplicar o corpo dessa RPC.
