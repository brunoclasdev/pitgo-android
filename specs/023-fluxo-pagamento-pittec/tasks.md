# Tasks: Fluxo de pagamento do PitTec

**Input**: Design documents from specs/023-fluxo-pagamento-pittec/

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/pittec-payment-flow.md,
quickstart.md

**Tests**: Nenhuma tarefa de teste automatizado foi criada porque a spec não solicita TDD nem
novos testes automatizados. A validação manual runnable está em quickstart.md e será executada na
fase de polish.

**Organization**: As tarefas estão agrupadas por história para permitir entrega incremental.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar o escopo de implementação e preservar os contratos existentes.

- [X] T001 [P] Registrar no comentário da migration e no plano os contratos compartilhados que não podem sofrer alteração no fluxo MOBILE_SEQUENTIAL em supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T002 [P] Catalogar os pontos de navegação e injeção atuais do checkout PitTec em app/src/main/java/com/trustarx/pitgo/PitGoNavHost.kt e app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt
- [X] T003 [P] Mapear os estados e códigos de erro existentes que precisam de mensagens para proposta, confirmação, expiração e pagamento em app/src/main/res/values/strings.xml

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Criar o armazenamento, as regras de segurança e o contrato comum que bloqueiam as
três histórias.

**Critical**: Esta fase deve terminar antes das fases de história.

- [X] T004 Criar pittec_pricing_rules, seus índices, constraints de vigência, RLS e grants, e os campos financeiros versionados de appointments em supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T005 Atualizar as constraints e funções compartilhadas de appointments/service_searches para representar quote_expires_at de 15 minutos, customer_total_amount e a transição de expiração que libera o prestador em supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T006 Atualizar a reserva de Orders para validar appointment, quote_version, client_confirmed_quote_version e customer_total_amount, mantendo provider_amount fora do amount cobrado, em supabase/migrations/20260918010000_pittec_customer_pricing.sql e supabase/migrations/20260915021000_orders_payment_reservation.sql
- [X] T007 [P] Adicionar no contrato compartilhado os campos role-aware de quote, os códigos de erro e as regras de amount em specs/023-fluxo-pagamento-pittec/contracts/pittec-payment-flow.md
- [X] T008 [P] Estender os modelos de domínio e DTOs para representar ProviderQuoteView, CustomerQuoteView, PricingBreakdown e a validade da proposta em app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt e app/src/main/java/com/trustarx/pitgo/features/pittec/data/PitTecDtos.kt
- [X] T009 Estender PitTecRepository com métodos para confirmação, restauração e observação de snapshots role-aware, preservando classificação de timeout, sessão expirada, revisão e contrato em app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt
- [X] T010 Atualizar o contrato de criação e recuperação para transportar customer_total_amount sem aceitar valor financeiro como autoridade do cliente em supabase/functions/mp-create-payment/index.ts e supabase/functions/_shared/payment-reconciliation.ts

**Checkpoint**: schema, estados, modelos e contratos base prontos; as histórias podem avançar em
ordem.

---

## Phase 3: User Story 1 - Aceitar solicitação e apresentar valores separados (Priority: P1) 🎯 MVP

**Goal**: Aceitar uma solicitação PitTec, congelar a proposta por 15 minutos e mostrar a cada
participante somente o valor permitido pelo seu papel.

**Independent Test**: Solicitar sem pagamento, aceitar como prestador e conferir que o prestador
vê somente provider_amount, enquanto o cliente vê markup, taxas e total sem provider_amount.

### Implementation for User Story 1

- [X] T011 [US1] Calcular provider_amount a partir do provider_services.price ativo e selecionar uma regra vigente de pittec_pricing_rules no aceite em supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T012 [US1] Persistir markup_amount de 40%, customer_fee_amount, customer_total_amount, pricing_rule_version, quote_expires_at e quote_version com arredondamento decimal exato no aceite em supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T013 [US1] Reescrever o snapshot PitTec para serializar ProviderQuoteView ao prestador e CustomerQuoteView ao cliente, sem enviar campos financeiros do papel oposto, em supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T014 [US1] Alterar o fluxo PitTec para iniciar busca sem checkout ou pagamento prévio e conservar o request_id da busca em app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt e app/src/main/java/com/trustarx/pitgo/PitGoNavHost.kt
- [X] T015 [US1] Renderizar os cards de proposta do cliente e do prestador com estados loading, indisponível, erro e expiração em app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt e app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt
- [X] T016 [US1] Atualizar a projeção de snapshot e mensagens de proposta para exibir prazo, markup, taxas e total apenas ao cliente e provider_amount apenas ao prestador em app/src/main/java/com/trustarx/pitgo/features/pittec/data/PitTecDtos.kt, app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt e app/src/main/res/values/strings.xml
- [X] T017 [US1] Implementar expiração server-side da proposta após 15 minutos, liberar provider availability, encerrar service_searches e bloquear pagamento tardio em supabase/migrations/20260918010000_pittec_customer_pricing.sql

**Checkpoint**: US1 entrega busca gratuita, aceite, cálculo congelado, isolamento financeiro e
expiração sem depender do app aberto.

---

## Phase 4: User Story 2 - Validar a cobrança antes de pagar (Priority: P1)

**Goal**: Permitir que o cliente revise o valor permitido, confirme a versão vigente e só então
avance para o pagamento.

**Independent Test**: Abrir uma proposta aceita, verificar bloqueio antes da confirmação, confirmar
com revision/quote_version atuais e receber a mesma composição na etapa de pagamento.

### Implementation for User Story 2

- [X] T018 [US2] Implementar a transição autenticada de respond_to_pittec_quote para confirmar/recusar, validar prazo de 15 minutos, revision e quote_version, e retornar conflitos sem alterar a proposta em supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T019 [US2] Adicionar estados explícitos de quote loading, ready, confirming, confirmed, expired, conflict e failure em app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt e app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt
- [X] T020 [US2] Conectar ações confirmar/recusar ao RPC e impedir abertura de checkout antes de CONFIRMED em app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt e app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt
- [X] T021 [US2] Criar o card de confirmação do cliente com composição segura, prazo restante, ação primária e mensagens para revisão vencida ou conflito em app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt e app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt
- [X] T022 [US2] Navegar de AWAITING_PAYMENT para o checkout usando request_id, appointment_id, quote_version e o total apenas como valor de conferência em app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt e app/src/main/java/com/trustarx/pitgo/PitGoNavHost.kt

**Checkpoint**: US2 entrega confirmação explícita, rejeição e expiração sem criar uma cobrança.

---

## Phase 5: User Story 3 - Pagar com cartão e iniciar o serviço (Priority: P1)

**Goal**: Permitir cartão salvo ou novo, cobrar o total confirmado uma única vez e iniciar o
atendimento somente depois de APPROVED.

**Independent Test**: Confirmar proposta, pagar com cartão salvo ou recém-cadastrado, recuperar o
resultado e verificar que o atendimento só muda para ACCEPTED/IN_PROGRESS após aprovação.

### Implementation for User Story 3

- [X] T023 [US3] Ajustar PaymentRepository e seus DTOs para enviar appointment_id/quote_version e interpretar customer_total_amount, conflitos, QUOTE_EXPIRED e tentativa VERIFYING em app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt e app/src/main/java/com/trustarx/pitgo/features/payment/data/PaymentDtos.kt
- [X] T024 [US3] Atualizar Payment e PaymentCheckoutUiState para carregar contexto PitTec confirmado, estados de cartão novo, rejeição, pendência, aprovação e expiração sem liberar o atendimento localmente em app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentModels.kt e app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentCheckoutUiState.kt
- [ ] T025 [US3] Integrar a seleção de cartão salvo e a tokenização de cartão novo durante o checkout, reutilizando SavedCardRepository/CoreMethods e sem armazenar PAN, CVV ou token em app/src/main/java/com/trustarx/pitgo/features/paymentmethods/repository/SavedCardRepository.kt, app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt e app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt
- [X] T026 [US3] Atualizar a tela de checkout para exibir apenas o total confirmado, permitir novo cartão, manter retry seguro e bloquear navegação para tracking até APPROVED em app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt e app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt
- [X] T027 [US3] Fazer mp-create-payment reservar e criar Orders usando customer_total_amount, validação de proposta confirmada e idempotência existente em supabase/functions/mp-create-payment/index.ts e supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T028 [US3] Ajustar webhook, reconciliação e trigger de aprovação para validar valor total, versão, confirmação e intenção antes de mudar appointment/service_searches para ACCEPTED em supabase/functions/mp-webhook/index.ts, supabase/functions/_shared/payment-reconciliation.ts e supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T029 [US3] Impedir arrive_pittec_appointment e início do tracking enquanto não houver pagamento APPROVED válido e integrar o retorno aprovado ao fluxo PitTec em supabase/migrations/20260918010000_pittec_customer_pricing.sql, app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt e app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt
- [X] T030 [US3] Implementar retomada após fechamento, timeout ou perda de realtime preservando request_id, payment_id, quote_version e uma única cobrança em app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt e app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt

**Checkpoint**: US3 entrega checkout, aprovação e início condicionado ao pagamento, com recuperação
e sem duplicidade.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validar segurança, compatibilidade e o fluxo completo.

- [X] T031 [P] Revisar RLS, RPC grants, snapshots role-aware e ausência de provider_amount/customer_total_amount no papel oposto em supabase/migrations/20260918010000_pittec_customer_pricing.sql
- [X] T032 [P] Atualizar textos, acessibilidade, loading, erro, expiração, cartão recusado, offline e sessão expirada em app/src/main/res/values/strings.xml e app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt
- [X] T033 [P] Revisar compatibilidade do fluxo MOBILE_SEQUENTIAL e dos pagamentos históricos PAYMENTS em supabase/migrations/20260918010000_pittec_customer_pricing.sql e supabase/functions/_shared/payment-reconciliation.ts
- [ ] T034 Executar os cenários manuais de quickstart.md em homologação com cliente e prestador PitTec, registrando somente evidências sanitizadas em specs/023-fluxo-pagamento-pittec/quickstart.md
- [X] T035 Executar ./gradlew :app:assembleDebug :app:lintDebug, deno check nas funções alteradas e supabase db lint --local quando o ambiente permitir, registrando impedimentos reais em specs/023-fluxo-pagamento-pittec/quickstart.md

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 não depende de outra fase.
- Phase 2 depende da leitura/auditoria da Phase 1 e bloqueia todas as histórias.
- US1 depende da Phase 2 e é o MVP funcional.
- US2 depende de US1 porque confirma a proposta criada após o aceite.
- US3 depende de US2 porque só pode abrir checkout depois da confirmação.
- Polish depende das histórias escolhidas para entrega.

### User Story Dependencies

- **US1 (P1)**: depende da fundação; independente de US2/US3 após a proposta role-aware e expiração.
- **US2 (P1)**: depende de US1; pode ser demonstrada sem criar cobrança.
- **US3 (P1)**: depende de US2; usa PaymentRepository, cartões e Orders existentes.

### Parallel Opportunities

- T001, T002 e T003 podem ocorrer em paralelo.
- T007, T008 e T010 podem ocorrer em paralelo depois da definição da migration.
- T014/T015 e T011/T012 devem ser sequenciais por contrato de snapshot; T016 depende dos dois.
- T019 e T021 podem ser trabalhadas em paralelo depois de T018; T020 integra ambos.
- T023/T024 podem ocorrer em paralelo; T025 pode ocorrer em paralelo com T027 após T022.
- T031, T032 e T033 podem ocorrer em paralelo antes de T034/T035.

## Parallel Example: User Story 1

    T011: congelar preço base e selecionar regra no aceite
    T014: remover checkout prévio da navegação PitTec
    T015: preparar cards de proposta e estados visuais

T012 depende de T011; T013 depende de T011/T012; T016 depende do contrato final do snapshot.

## Parallel Example: User Story 2

    T019: modelar estados de confirmação no ViewModel
    T021: construir card de confirmação

T018 deve estar pronto antes de T020; T022 integra confirmação e checkout.

## Parallel Example: User Story 3

    T023: adaptar PaymentRepository/DTOs
    T024: adaptar estados de pagamento
    T025: integrar cartão salvo/novo
    T027: adaptar criação Orders server-side

T026 depende de T023/T024/T025; T028 depende de T027; T029/T030 fecham o fluxo.

## Implementation Strategy

### MVP First

1. Completar Phase 1 e Phase 2.
2. Implementar US1: busca sem cobrança, aceite, composição role-aware e expiração.
3. Validar US1 com os cenários de privacidade e preço do quickstart.
4. Implementar US2 para tornar a proposta confirmável.
5. Só então ativar US3 e o caminho Orders em homologação.

### Incremental Delivery

1. Entregar US1 sem habilitar novas cobranças.
2. Entregar US2 e validar revisão/expiração.
3. Entregar US3 com feature flag de Orders desligada até backend e app estarem compatíveis.
4. Executar a matriz manual e habilitar novas criações somente após aprovação de homologação.

## Task Format Validation

Todas as tarefas usam checkbox, ID sequencial, marcador [P] somente quando aplicável, rótulo
[US1]/[US2]/[US3] nas fases de história e caminho de arquivo explícito.
