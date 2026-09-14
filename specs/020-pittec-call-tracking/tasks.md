---

description: "Dependency-ordered implementation tasks for the PitTec call and live tracking flow"
---

# Tasks: Chamada e acompanhamento do PitTec

**Input**: Design documents from `specs/020-pittec-call-tracking/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Nenhum teste automatizado foi solicitado. Conforme a constituição, não serão criados
testes, mocks ou fixtures; cada história termina com validação manual baseada em `quickstart.md`, e a
fase final executa build/lint e verificações locais de Supabase/Deno.

**Organization**: As tarefas estão agrupadas pelas quatro histórias da spec. US1 e US2 são P1 e
formam o fluxo mínimo completo de chamada/contratação; US3 e US4 são P2 e estendem o mesmo appointment
autorizado para os mapas dos dois participantes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode executar em paralelo depois das dependências declaradas, pois toca arquivos distintos.
- **[Story]**: corresponde a US1, US2, US3 ou US4 da `spec.md`.
- Toda tarefa cita seus arquivos-alvo exatos.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Criar a evolução forward-only de schema que receberá todas as mudanças server-side.

- [X] T001 Criar `supabase/migrations/20260913010000_pittec_call_tracking.sql` com as adições de `next_dispatch_at` em `service_searches`, versão/deadline/confirmação em `appointments`, vínculos `appointment_id`/`quote_version` em `payments`, checks dos novos estados e índices parciais/únicos atualizados conforme `data-model.md`

**Checkpoint**: A migration representa os novos dados sem editar as migrations `015`, `018` ou
`019` e aplica sobre o schema atual.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Estabelecer snapshot, estados e modelos compartilhados antes de implementar qualquer
história.

**⚠️ CRITICAL**: Nenhuma user story começa antes desta fase estar completa.

- [X] T002 Implementar na migration `supabase/migrations/20260913010000_pittec_call_tracking.sql` os predicados de estado ativo, ordem canônica de locks `service_searches → appointments`, grants mínimos e `private.pittec_dispatch_snapshot` com `server_now`, revisão, proposta versionada, status de pagamento, `tracking_authorized`, ações permitidas e visibilidade distinta para cliente/PitTec
- [X] T003 [P] Ampliar statuses, ações, proposta versionada, deadline e `trackingAuthorized` do snapshot compartilhado do prestador em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/model/MobileDispatchModels.kt` e mapear o novo JSON em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/data/MobileDispatchDtos.kt`
- [X] T004 [P] Remodelar o domínio/snapshot PitTec com fases server-side explícitas, proposta, pagamento e autorização de tracking, removendo `PitTecCandidate`, `CandidateFound` e `paymentApproved` local, em `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/pittec/data/PitTecDtos.kt`
- [X] T005 [P] Acrescentar `appointmentId`, `quoteVersion` e falhas estáveis de proposta/pagamento aos comandos e DTOs em `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentResults.kt`, `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/data/PaymentDtos.kt`, mantendo o valor recebido pelo Android apenas para exibição

**Checkpoint**: Schema, snapshot e modelos Kotlin compilam com os estados
`AWAITING_CLIENT_CONFIRMATION` e `AWAITING_PAYMENT`; nenhum Boolean local autoriza tracking.

---

## Phase 3: User Story 1 - Buscar até um PitTec aceitar (Priority: P1) 🎯 MVP

**Goal**: Manter uma única chamada procurando automaticamente até o primeiro aceite válido ou até o
cancelamento explícito do cliente.

**Independent Test**: Iniciar uma chamada, fazer um PitTec recusar, deixar outra oferta expirar e um
terceiro aceitar; o mesmo `request_id` deve chegar a `AWAITING_CLIENT_CONFIRMATION`. Sem candidatos, a
busca permanece ativa e encontra um prestador que fique disponível depois.

### Implementation for User Story 1

- [X] T006 [US1] Alterar `private.advance_pittec_dispatch` em `supabase/migrations/20260913010000_pittec_call_tracking.sql` para preservar o ponto/request, manter `SEARCHING`, definir `next_dispatch_at` e nunca terminar em `EXHAUSTED` quando uma varredura não encontrar candidato, sem mudar elegibilidade, prioridade, raio ou ordenação
- [X] T007 [US1] Implementar `advance_due_pittec_searches`, o schedule pg_cron em lote com `FOR UPDATE SKIP LOCKED` e o avanço lazy em `get_my_active_pittec_dispatch` dentro de `supabase/migrations/20260913010000_pittec_call_tracking.sql`, garantindo no máximo uma oferta ativa e reagendamento sem busy loop
- [X] T008 [US1] Atualizar o branch PitTec de `respond_to_mobile_dispatch` e as rotinas de timeout/cancelamento em `supabase/migrations/20260913010000_pittec_call_tracking.sql` para recusa/expiração voltarem a mesma busca, aceite concorrente escolher apenas um PitTec e o vencedor entrar em `AWAITING_CLIENT_CONFIRMATION` sem criar localização compartilhada
- [X] T009 [P] [US1] Corrigir `observeProvider` e restore por revisão para recuperar `PITTEC_AUTO_SEQUENTIAL` além de mobile dispatch em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/repository/MobileDispatchRepository.kt`
- [X] T010 [P] [US1] Manter start/restore/cancelamento idempotentes e observação da mesma solicitação durante `SEARCHING`/`AWAITING_PROVIDER` em `app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt`
- [X] T011 [US1] Refatorar `PitTecViewModel` para restaurar antes de criar, impedir toques duplicados, remover confirmação local de candidato e manter Realtime/polling de cinco segundos em todo snapshot não terminal em `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt`
- [X] T012 [US1] Renderizar busca persistente e cancelável sem card de candidato nem estado terminal de ausência momentânea em `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt` e `app/src/main/res/values/strings.xml`
- [ ] T013 [US1] Executar e registrar os resultados dos cenários 1 e 2 de `specs/020-pittec-call-tracking/quickstart.md`, incluindo toque duplicado, nenhum candidato, três decisões sequenciais, cancelamento e dois aceites concorrentes

**Checkpoint**: US1 funciona isoladamente — a chamada nunca exige novo toque após recusa/timeout e
somente o primeiro aceite válido encerra a distribuição.

---

## Phase 4: User Story 2 - Conferir e confirmar o valor (Priority: P1)

**Goal**: Mostrar o mesmo preço real ao PitTec e ao cliente, registrar confirmação/recusa em até dois
minutos e liberar o deslocamento apenas após pagamento aprovado correspondente.

**Independent Test**: PitTec vê o preço e aceita; cliente recebe o mesmo valor e confirma, recusa ou
deixa expirar. Apenas confirmação vigente seguida de pagamento aprovado muda o appointment para
`ACCEPTED`; valor adulterado, pagamento pendente ou webhook tardio não libera tracking.

### Implementation for User Story 2

- [X] T014 [US2] Implementar a RPC idempotente `respond_to_pittec_quote`, expiração lazy e `expire_due_pittec_confirmations` agendado em `supabase/migrations/20260913010000_pittec_call_tracking.sql`, validando cliente, request, appointment, revisão, `quote_version` e deadline pelo relógio do banco e retornando outcomes estáveis
- [X] T015 [US2] Implementar a reconciliação transacional de pagamento PitTec em `supabase/migrations/20260913010000_pittec_call_tracking.sql`: validar request/cliente/appointment/valor/BRL/versão confirmada, promover `AWAITING_PAYMENT → ACCEPTED` uma vez, materializar o ponto fixo e impedir que aprovação tardia reative estado terminal
- [X] T016 [US2] Fortalecer em `supabase/migrations/20260913010000_pittec_call_tracking.sql` a guarda de `arrive_pittec_appointment`, a autorização privada do tópico `payment:<request_id>` e as invalidações de dispatch/pagamento, usando helper `SECURITY DEFINER`, `search_path = ''`, execução pública revogada e grants mínimos
- [X] T017 [P] [US2] Alterar `supabase/functions/mp-create-payment/index.ts` para resolver server-side o dispatch PitTec, cliente, appointment, `quoted_price` e `quote_version`, não confiar em `amount`/`dispatch_kind` do APK, persistir refresh de pagamento pendente e reutilizar/criar tentativas com idempotência segura
- [X] T018 [P] [US2] Alterar `supabase/functions/mp-webhook/index.ts` para validar assinatura, `external_reference` e valor obtido do Mercado Pago, persistir toda mudança de status, acionar a reconciliação única e nunca reabrir chamada terminal em aprovação atrasada
- [X] T019 [P] [US2] Implementar `respondToQuote` e mapear `CONFIRMED`, `DECLINED`, `CONFIRMATION_EXPIRED`, `REVISION_CONFLICT`, `INVALID_TRANSITION` e `NOT_FOUND` no `app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt`
- [X] T020 [P] [US2] Mostrar no card real da oferta o `quotedPrice` autoritativo e representar `AWAITING_CLIENT_CONFIRMATION`/`AWAITING_PAYMENT` sem iniciar deslocamento em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/ProviderIncomingRequestViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt`, removendo o uso real de `ServiceEstimate`/faixas fictícias
- [X] T021 [US2] Implementar no `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt` as fases `AwaitingClientConfirmation` e `AwaitingPayment`, contador derivado de `server_now`/deadline, ações confirm/decline com operação em voo, rejeição de card stale e restauração contínua por revisão
- [X] T022 [US2] Criar o card Material de proposta com identificação do PitTec, valor BRL, prazo, confirmar/recusar e estados acionáveis de expiração/rede em `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt`, integrá-lo em `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt` e adicionar textos em `app/src/main/res/values/strings.xml`
- [X] T023 [P] [US2] Atualizar o comando e a chamada da Edge Function para enviar request, appointment, versão e meio de pagamento — sem tratar o valor do client como autoridade — e mapear conflitos de proposta em `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/data/PaymentDtos.kt`
- [X] T024 [US2] Fazer o checkout PitTec usar a proposta confirmada para exibição, sem carregar `services.minimum_price`, manter/repetir pagamento `PENDING`/`REJECTED` conforme o fluxo existente e restaurar o dispatch antes de liberar sucesso em `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentCheckoutUiState.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt`
- [X] T025 [US2] Estender a rota de checkout com `requestId`, `appointmentId`, `quoteVersion` e valor apenas visual, conectar confirmação → pagamento e exigir snapshot `tracking_authorized=true` antes de navegar ao tracking em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt`
- [ ] T026 [US2] Executar e registrar os cenários 3, 4 e 5 de `specs/020-pittec-call-tracking/quickstart.md`, incluindo paridade de preço, adulteração de amount, confirmação idempotente, recusa, timeout de 120 segundos, pagamento pendente/recusado/aprovado e corrida cancelamento versus webhook

**Checkpoint**: US1 + US2 formam o fluxo P1 completo — existe acordo explícito e pagamento válido
antes de `ACCEPTED`, e ambos os perfis restauram a mesma etapa.

---

## Phase 5: User Story 3 - Acompanhar a aproximação no mapa do cliente (Priority: P2)

**Goal**: Após pagamento aprovado, o cliente acompanha o PitTec e o local fixo no mesmo mapa, com
posição, distância, stale e câmera respeitando interação manual.

**Independent Test**: Com appointment `ACCEPTED`, mover o PitTec e verificar no cliente dois
marcadores enquadrados, distância atualizada sem reload, última posição stale em falha, destino
imutável após movimento do cliente e ação de reenquadrar após pan/zoom.

### Implementation for User Story 3

- [X] T027 [US3] Restringir leitura/publicação/tópicos de localização e contexto PitTec a `ACCEPTED`/`IN_PROGRESS`, impedir `publish_client_location` de sobrescrever o destino PitTec e encerrar acesso em estado terminal na `supabase/migrations/20260913010000_pittec_call_tracking.sql`
- [X] T028 [P] [US3] Modelar `serviceLocation`, revisão da posição, distância direta, freshness e `ViewportMode.FOLLOW_BOTH/MANUAL` em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/model/AppointmentTrackingModels.kt`
- [X] T029 [P] [US3] Carregar dispatch kind, destino fixo e posição atual com revisão, observar invalidações e descartar snapshot de rota incompatível com a posição vigente em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/repository/AppointmentTrackingRepository.kt`
- [X] T030 [US3] Impedir publicação da localização atual do cliente em appointment PitTec, calcular Haversine a cada posição válida e manter último ponto stale/offline sem inventar coordenadas em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`
- [X] T031 [P] [US3] Implementar bounds dos dois marcadores, detecção de pan/zoom manual e comando de reenquadramento sem saltos contínuos em `app/src/main/java/com/trustarx/pitgo/core/map/ui/ParticipantMapStyleController.kt` e `app/src/main/java/com/trustarx/pitgo/core/map/ui/MapLibreMapHost.kt`
- [X] T032 [US3] Renderizar no mapa do cliente os marcadores rotulados `PitTec`/`Local do atendimento`, distância ou `Chegou`, estado stale e ação `Reenquadrar` com acessibilidade em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt` e `app/src/main/res/values/strings.xml`
- [ ] T033 [US3] Executar e registrar o cenário 6 e a parte do cliente do cenário 7 de `specs/020-pittec-call-tracking/quickstart.md`, cobrindo privacidade pré-pagamento, destino fixo, marcador/distância, sobreposição, pan/zoom, reenquadramento e leitor de tela

**Checkpoint**: US3 funciona para o cliente sem localização de background nem dependência de rota/ETA.

---

## Phase 6: User Story 4 - Acompanhar o local no mapa do PitTec (Priority: P2)

**Goal**: O PitTec vê a própria posição e o destino fixo no mapa e continua publicando localização em
segundo plano durante o deslocamento autorizado.

**Independent Test**: Após confirmação e pagamento, abrir/reabrir o appointment como PitTec, mover o
prestador, bloquear a tela e colocar o app em segundo plano; mapa e distância continuam atualizando no
cliente em até 10 segundos, com notificação persistente e encerramento ao cancelar/concluir.

### Implementation for User Story 4

- [X] T034 [P] [US4] Fazer o `ProviderLocationTrackingService` selecionar target `APPOINTMENT` apenas quando o snapshot PitTec estiver `ACCEPTED`/`IN_PROGRESS`, conservar coleta de 10 segundos/lease em background e parar no terminal em `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt` e `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderLocationRepository.kt`
- [X] T035 [US4] Representar permissão revogada, GPS desligado, falha de início do foreground service e publicação degradada com ações de recuperação e notificação persistente em `app/src/main/java/com/trustarx/pitgo/features/providertracking/model/ProviderTrackingModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/providertracking/viewmodel/ProviderTrackingViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/providertracking/ui/ProviderTrackingScreen.kt` e `app/src/main/res/values/strings.xml`
- [X] T036 [P] [US4] Restaurar os estados de espera sem navegar e abrir/retomar tracking somente em `ACCEPTED`/`IN_PROGRESS` no `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecProviderViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt`
- [X] T037 [US4] Integrar o papel PitTec à viewport compartilhada, exibindo própria posição, local fixo, distância/chegada e ação de reenquadrar em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`
- [ ] T038 [US4] Executar e registrar a parte do PitTec do cenário 7 e todo o cenário 8 de `specs/020-pittec-call-tracking/quickstart.md`, medindo ao menos 20 atualizações, background/tela bloqueada, notificação, stale, permissão/GPS/rede, recuperação e encerramento do lease

**Checkpoint**: Os dois participantes compartilham o mesmo appointment e destino; somente o PitTec
publica em background e o tracking termina com o atendimento.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validar integrações, segurança, regressões e qualidade antes da entrega.

- [ ] T039 [P] Aplicar do zero e sobre o schema anterior, executar `supabase db lint --local` e confirmar os schedules pg_cron de avanço/expiração para `supabase/migrations/20260913010000_pittec_call_tracking.sql`, corrigindo qualquer falha local sem executar reset remoto
- [ ] T040 [P] Executar verificação TypeScript/Deno e revisão de idempotência, assinatura, amount e logs sensíveis em `supabase/functions/mp-create-payment/index.ts` e `supabase/functions/mp-webhook/index.ts`, corrigindo findings introduzidos pela feature
- [X] T041 [P] Executar `./gradlew :app:assembleDebug :app:lintDebug` e corrigir erros/warnings novos nos arquivos Android listados em `specs/020-pittec-call-tracking/plan.md`
- [ ] T042 Executar e registrar o cenário 9 e o critério de encerramento de `specs/020-pittec-call-tracking/quickstart.md`, cobrindo restore em cada estado, queda de Realtime, autorização de tópico, mobile dispatch/guincho e fluxo pós-chegada
- [X] T043 Revisar e remover comentários, imports, strings e ramificações obsoletas diretamente substituídas pela feature, sem apagar os mocks intencionais de `017`, em `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt` e `app/src/main/res/values/strings.xml`

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Setup (T001)
  → Foundational (T002–T005)
    → US1 busca contínua (T006–T013)
      → US2 proposta/pagamento (T014–T026)
        → US3 mapa cliente (T027–T033)
          → US4 mapa/background PitTec (T034–T038)
            → Polish (T039–T043)
```

- **Setup** não possui dependências e cria a única migration forward-only.
- **Foundational** depende de T001 e bloqueia todas as histórias.
- **US1 (P1)** depende da fundação e entrega o MVP independente de busca/aceite.
- **US2 (P1)** depende do aceite de US1 para apresentar e confirmar a proposta; conclui o fluxo P1.
- **US3 (P2)** depende do gate de pagamento de US2, porque localização exata só abre em `ACCEPTED`.
- **US4 (P2)** depende do mapa compartilhado de US3 para reutilizar viewport e distância; T034 e T036
  podem começar após US2 em paralelo ao núcleo de mapa da US3, mas T037/T038 aguardam US3.
- **Polish** depende de todas as histórias desejadas para a entrega.

### Within Each User Story

- Migration/RPC antes de Repository; modelo/DTO antes de ViewModel; ViewModel antes de UI/navegação.
- Edge Functions e clientes Android podem ser implementados em paralelo depois do contrato SQL que
  validará suas chamadas.
- A tarefa manual encerra cada história e só começa após todas as tarefas de implementação da fase.
- Nenhuma história considera callback local, timer ou broadcast como autoridade de estado.

### Parallel Opportunities

- T003, T004 e T005 podem executar em paralelo depois de T001/T002, pois alteram features distintas.
- T009 e T010 podem executar em paralelo depois de T008.
- T017, T018, T019, T020 e T023 podem executar em paralelo depois de T014–T016.
- T027 e T028 podem começar em paralelo; depois de T028, T029 e T031 podem executar em paralelo.
- T034 e T036 podem começar em paralelo depois de US2; T035 depende de T034 e T037 depende da US3.
- T039, T040 e T041 são validações independentes e podem executar em paralelo antes do cenário final.

---

## Parallel Execution Examples

### User Story 1

```text
Após T008:
- T009: corrigir restore/Realtime do prestador em MobileDispatchRepository.kt
- T010: corrigir start/restore/cancelamento do cliente em PitTecRepository.kt
```

### User Story 2

```text
Após T014–T016:
- T017: proteger mp-create-payment
- T018: reconciliar mp-webhook
- T019: implementar RPC no PitTecRepository
- T020: implementar valor/espera na experiência do PitTec
- T023: adaptar PaymentRepository/DTOs Android
```

### User Story 3

```text
Após T028:
- T029: adaptar AppointmentTrackingRepository
- T031: adaptar ParticipantMapStyleController/MapLibreMapHost
Em paralelo, T027 fecha as políticas server-side de localização.
```

### User Story 4

```text
Após US2:
- T034: corrigir target e ciclo de vida do foreground tracking
- T036: corrigir restore/navegação do dashboard PitTec
Após US3, integrar ambos em T037.
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Completar T001–T005.
2. Completar T006–T013.
3. **PARAR E VALIDAR**: a mesma chamada sobrevive a ausência, recusa e timeout e somente um PitTec
   aceita.
4. Demonstrar o MVP de busca contínua antes de introduzir pagamento/mapa.

### Incremental Delivery

1. Setup + Foundational → estado e contratos compartilhados.
2. US1 → busca contínua/aceite único → validação independente.
3. US2 → preço, confirmação, timeout e pagamento → fluxo P1 completo.
4. US3 → acompanhamento do lado do cliente.
5. US4 → acompanhamento/background do lado do PitTec.
6. Polish → regressão, segurança e critérios mensuráveis.

### Parallel Team Strategy

Depois da fundação, um responsável pode executar a migration/SQL sequencialmente enquanto outros
preparam, contra os contratos já definidos, os modelos/repositories Android e as Edge Functions
marcadas `[P]`. Arquivos compartilhados de navegação, tracking e `strings.xml` permanecem nas tarefas
sequenciais para evitar conflitos.

---

## Notes

- Nenhuma dependência, serviço Android ou tabela de domínio nova deve ser criado.
- Não editar migrations antigas; toda mudança SQL desta feature fica em
  `supabase/migrations/20260913010000_pittec_call_tracking.sql`.
- Não confiar em amount, dispatch kind, timer, callback ou coordenada enviados pelo APK.
- Não ampliar RLS/tópicos de localização aos estados pré-pagamento.
- Preservar mobile dispatch, guincho, meios de pagamento e fluxo pós-chegada fora do escopo.
- Fazer commit por tarefa ou grupo lógico pequeno; cada checkpoint deve permanecer demonstrável.
