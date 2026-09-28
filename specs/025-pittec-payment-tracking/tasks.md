---

description: "Task list for post-payment PitTec tracking and role-based pricing"

---

# Tasks: Acompanhamento pós-pagamento e cobrança PitTec

**Input**: Design documents from `/specs/025-pittec-payment-tracking/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/pricing-and-dispatch.md`, `quickstart.md`

**Tests**: Não foram criadas tarefas de testes automatizados porque não foram solicitadas. Cada fase possui critérios de validação manual e a fase final executa o quickstart.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar a estrutura existente e preparar a implementação incremental.

- [X] T001 Mapear os pontos atuais de despacho, proposta, checkout, pagamento e acompanhamento em `app/src/main/java/com/trustarx/pitgo/features/` e registrar qualquer divergência do plano em `specs/025-pittec-payment-tracking/research.md`
- [X] T002 [P] Conferir a ordem das migrações Supabase e o estado das funções de pagamento em `supabase/migrations/` e `supabase/functions/mp-create-payment/index.ts`
- [X] T003 [P] Conferir as rotas e argumentos existentes de pagamento e acompanhamento em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Criar a autoridade de preço, a projeção por papel e o vínculo financeiro que todas as histórias usam.

**Checkpoint**: A fundação só estará pronta quando uma proposta puder ser congelada, projetada de forma diferente para cliente e PitTec e reservada usando o total do cliente.

- [X] T004 Criar a migração `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` adicionando tarifa por quilômetro, componentes congelados em `appointments`, validações monetárias e índices necessários
- [X] T005 Atualizar o cálculo server-side em `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` para usar preço do serviço, distância do candidato, tarifa por quilômetro, 40%, taxas configuráveis e arredondamento em centavos
- [X] T006 Atualizar a projeção `private.pittec_dispatch_snapshot` em `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` para retornar somente o total ao cliente e serviço, quilometragem e valor do PitTec ao PitTec
- [X] T007 Corrigir `reserve_order_payment` e a validação de aprovação em `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` para usar `appointments.customer_total_amount`, `quote_version`, moeda e expiração
- [X] T008 [P] Atualizar os contratos de transporte em `app/src/main/java/com/trustarx/pitgo/features/pittec/data/PitTecDtos.kt` e `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt` para representar as projeções financeiras específicas de cada papel
- [X] T009 [P] Atualizar os modelos compartilhados de despacho em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/data/MobileDispatchDtos.kt` e `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/model/MobileDispatchModels.kt` sem expor componentes financeiros fora do papel autorizado
- [X] T010 Atualizar os contratos documentados em `specs/025-pittec-payment-tracking/contracts/pricing-and-dispatch.md` após validar os nomes finais dos campos e códigos de erro da migração

---

## Phase 3: User Story 5 - Exibir valores conforme o perfil (Priority: P1)

**Goal**: Calcular e exibir o valor correto para cada papel, com preço configurável e sem vazamento do total do cliente para o PitTec ou do valor do PitTec para o cliente.

**Independent Test**: Aceitar uma oferta com preço de serviço, distância, tarifa por quilômetro e taxas configuradas; confirmar que o PitTec vê seu valor composto e o cliente vê somente o total final.

### Implementation for User Story 5

- [X] T011 [US5] Ajustar o carregamento e a validação da proposta no cliente em `app/src/main/java/com/trustarx/pitgo/features/pittec/data/PitTecDtos.kt` e `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt` para aceitar apenas `customer_total_amount` como valor financeiro exibível
- [X] T012 [US5] Atualizar `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt` para mostrar ao cliente somente o total, removendo markup, taxas e demais componentes da tela
- [X] T013 [US5] Atualizar `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt` e os recursos de texto em `app/src/main/res/values/strings.xml` para mostrar ao PitTec serviço, distância, adicional por quilômetro e valor do PitTec
- [X] T014 [US5] Remover o uso do valor fixo de teste no fluxo PitTec em `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt`, usando o total da proposta e mantendo validação de contexto no backend
- [X] T015 [US5] Tratar proposta sem preço, tarifa, taxa ou regra vigente em `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt` com mensagem acionável e sem abrir cobrança

**Checkpoint**: A tela do cliente mostra apenas o total; a tela do PitTec mostra apenas seu valor; a reserva registra o total correto.

---

## Phase 4: User Story 1 - Receber o resultado do pagamento Pix (Priority: P1) 🎯 MVP

**Goal**: Informar o resultado do pagamento com estados distintos e liberar o atendimento somente quando o total correto estiver aprovado.

**Independent Test**: Executar Pix aprovado, em processamento, falho e recusado; verificar feedback correspondente, ações disponíveis e bloqueio do mapa antes da aprovação.

### Implementation for User Story 1

- [X] T016 [US1] Atualizar `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentCheckoutUiState.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentResults.kt` para representar processamento, sucesso, falha, recusa, expiração e contexto inválido sem estados ambíguos
- [X] T017 [US1] Ajustar `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt` para carregar o total da proposta, acompanhar o pagamento, encerrar loading indefinido e bloquear o avanço sem aprovação
- [X] T018 [US1] Ajustar `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/data/PaymentDtos.kt` para preservar `appointment_id`, `quote_version`, total, status e códigos de erro do contrato
- [X] T019 [US1] Atualizar `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt` para exibir feedback acionável de sucesso, processamento, falha, recusa e expiração
- [X] T020 [US1] Integrar a conclusão aprovada do checkout com o fluxo de despacho em `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`, abrindo o acompanhamento somente com snapshot aprovado

**Checkpoint**: O cliente consegue distinguir todos os resultados do Pix e não entra no mapa nem inicia o serviço antes de `APPROVED`.

---

## Phase 5: User Story 2 - Cliente acompanhar o PitTec e confirmar o início (Priority: P1)

**Goal**: Mostrar ao cliente o mapa sincronizado, a chegada do PitTec e a confirmação de início.

**Independent Test**: Com pagamento aprovado, atualizar a posição do PitTec, registrar chegada e verificar mapa, banner de chegada e reconhecimento do início no cliente.

### Implementation for User Story 2

- [X] T021 [US2] Ajustar `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/model/AppointmentTrackingModels.kt` para representar posições atualizadas, desatualizadas, ausência de localização, chegada confirmada e serviço iniciado
- [X] T022 [US2] Atualizar `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/repository/AppointmentTrackingRepository.kt` para sincronizar estado do atendimento e localização após pagamento aprovado, mantendo fallback de consulta
- [X] T023 [US2] Atualizar `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` para reagir a chegada, início, perda de conexão e encerramento sem duplicar transições
- [X] T024 [US2] Atualizar `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt` para exibir cliente, PitTec, estado da localização, confirmação de chegada e reconhecimento do início
- [X] T025 [US2] Integrar a navegação pós-pagamento e o retorno ao mapa em `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`

**Checkpoint**: O cliente vê a posição atual ou a última posição com horário, recebe a chegada e reconhece o início sem que o mapa fique ativo após encerramento.

---

## Phase 6: User Story 3 - PitTec acompanhar o cliente e validar chegada e início (Priority: P1)

**Goal**: Permitir que o PitTec acompanhe o cliente, valide a chegada e inicie o serviço com pagamento aprovado.

**Independent Test**: Com pagamento aprovado, abrir a tela do PitTec, verificar as duas posições, marcar chegada, confirmar início e observar o mesmo estado no cliente.

### Implementation for User Story 3

- [X] T026 [P] [US3] Ajustar `app/src/main/java/com/trustarx/pitgo/features/providertracking/model/ProviderTrackingModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/model/MobileDispatchModels.kt` para explicitar chegada, início, posição do cliente e posição do PitTec
- [X] T027 [US3] Atualizar `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderLocationRepository.kt` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/repository/AppointmentTrackingRepository.kt` para publicar e consumir as posições autorizadas
- [X] T028 [US3] Atualizar `app/src/main/java/com/trustarx/pitgo/features/providertracking/viewmodel/ProviderTrackingViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/ProviderIncomingRequestViewModel.kt` para controlar ações de mapa, chegada e início
- [X] T029 [US3] Atualizar `app/src/main/java/com/trustarx/pitgo/features/providertracking/ui/ProviderTrackingScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ProviderIncomingRequestScreen.kt` para exibir o cliente, localização atual, estado desatualizado e ações válidas
- [X] T030 [US3] Garantir em `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` que chegada e início exigem pagamento aprovado, atendimento correto e revisão válida, com operação idempotente

**Checkpoint**: O PitTec só inicia após pagamento aprovado e chegada validada; cliente e PitTec recebem o mesmo estado.

---

## Phase 7: User Story 4 - Retomar o acompanhamento com estados consistentes (Priority: P2)

**Goal**: Retomar pagamento, mapa, chegada e início após fechar a aplicação ou perder conexão, encerrando localização em estados terminais.

**Independent Test**: Interromper a aplicação em cada etapa, reabrir nos dois papéis e verificar recuperação do estado sem repetir ações ou manter compartilhamento após cancelamento/conclusão.

### Implementation for User Story 4

- [X] T031 [US4] Atualizar `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/DispatchResumeViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecResumeViewModel.kt` para restaurar a solicitação ativa e encaminhar cada papel ao estado correto
- [X] T032 [US4] Atualizar `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt` para retomar status e proposta sem criar tentativa duplicada após recriação do processo
- [X] T033 [US4] Atualizar `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt` e `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderTrackingStateRepository.kt` para parar publicação em cancelamento/conclusão e indicar localização obsoleta
- [X] T034 [US4] Atualizar `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/providertracking/ui/ProviderTrackingScreen.kt` para mostrar offline, sessão expirada, atendimento encerrado e última posição
- [X] T035 [US4] Validar transições repetidas e encerramento de localização em `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` e nas funções de cancelamento existentes em `supabase/migrations/20260915029000_orders_payment_cancellation.sql`

**Checkpoint**: Fechar e reabrir a aplicação não cria cobrança ou transição duplicada, e estados terminais encerram o compartilhamento.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verificar integração, documentação e entrega nos dispositivos conectados.

- [X] T036 [P] Atualizar `specs/025-pittec-payment-tracking/data-model.md`, `specs/025-pittec-payment-tracking/contracts/pricing-and-dispatch.md` e `specs/025-pittec-payment-tracking/quickstart.md` com nomes finais após implementação
- [X] T037 [P] Revisar mensagens, content descriptions e estados de loading/erro em `app/src/main/res/values/strings.xml` e nas telas alteradas para manter acessibilidade e linguagem acionável
- [X] T038 Executar `./gradlew :app:assembleDebug` e corrigir falhas de compilação nos arquivos alterados
- [X] T039 Publicar as migrações e funções Supabase da feature usando o fluxo de deploy já configurado e validar as RPCs do contrato em `specs/025-pittec-payment-tracking/contracts/pricing-and-dispatch.md`
- [ ] T040 Executar todos os cenários de `specs/025-pittec-payment-tracking/quickstart.md` e registrar resultados de preço, pagamento, mapa, chegada e início
- [X] T041 Instalar `app/build/outputs/apk/debug/app-debug.apk` nos quatro dispositivos Android conectados e abrir os fluxos de cliente e PitTec para validação manual

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: sem dependências; apenas inspeção e preparação.
- **Phase 2 — Foundational**: depende da Phase 1 e bloqueia todas as histórias.
- **Phase 3 — US5**: depende da fundação; entrega o cálculo e a exibição por perfil.
- **Phase 4 — US1**: depende de US5 para usar o total correto no checkout.
- **Phase 5 — US2**: depende de US1 para abrir o mapa somente após pagamento aprovado.
- **Phase 6 — US3**: depende da fundação e integra com US2 para refletir chegada e início.
- **Phase 7 — US4**: depende de US1, US2 e US3 para retomar todos os estados.
- **Phase 8 — Polish**: depende das histórias desejadas concluídas.

### User Story Dependencies

- **US5 (P1)**: primeira história funcional; depende apenas da fundação.
- **US1 (P1)**: depende de US5; sem o total correto não há checkout válido.
- **US2 (P1)**: depende de US1; precisa do pagamento aprovado.
- **US3 (P1)**: depende da fundação e compartilha o estado de acompanhamento com US2.
- **US4 (P2)**: depende das três histórias operacionais para cobrir retomada completa.

### Parallel Opportunities

- T002 e T003 podem ser executadas em paralelo após T001.
- T008 e T009 podem ser executadas em paralelo após T004–T007.
- Dentro de US3, T026 e T027 podem ser executadas em paralelo; T028–T030 dependem dos contratos de estado.
- Dentro de US4, T031–T034 podem ser divididas por tela/serviço após as histórias anteriores; T035 depende da revisão final do backend.
- T036 e T037 podem ser executadas em paralelo antes de T038–T041.

## Parallel Example: User Story 5

```text
T011 — ajustar DTO/modelo de proposta do cliente
T012 — remover detalhamento da tela do cliente
T013 — ajustar detalhamento da tela do PitTec
```

Essas tarefas podem ser divididas depois que T004–T010 definirem os campos finais do contrato.

## Implementation Strategy

### MVP First

1. Completar Phases 1 e 2.
2. Completar US5 para congelar e exibir o cálculo correto.
3. Completar US1 para permitir Pix/cartão com resultado claro e total correto.
4. Validar preço e pagamento pelo quickstart antes de avançar para o mapa.

### Incremental Delivery

1. Entregar US5 + US1 como primeiro incremento funcional.
2. Entregar US2 com mapa e confirmação do cliente.
3. Entregar US3 com mapa e validação do PitTec.
4. Entregar US4 com retomada, encerramento e privacidade.
5. Executar Polish e instalar nos quatro dispositivos.

## Notes

- `[P]` indica tarefas que podem ser executadas em paralelo sem compartilhar arquivos incompletos.
- Toda tarefa possui checkbox, ID sequencial, labels obrigatórios quando aplicável e caminho de arquivo.
- Não foram incluídas tarefas de testes automatizados; a validação manual está concentrada em `quickstart.md`.

## Evidência da implementação

As tarefas T001–T039 e T041 foram implementadas ou verificadas por reutilização do fluxo existente, com compilação, publicação e validação descritas em `validation.md`. T040 permanece pendente para a execução manual completa dos cenários com pagamento, mapa, chegada e início.

Desvios de caminhos: a exibição real de valores do PitTec foi corrigida em `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt` e no mapa compartilhado. O serviço de localização do PitTec e os ViewModels de retomada já implementavam os mecanismos necessários e foram preservados após inspeção. A chegada/início usam timestamps distintos em ACCEPTED/IN_PROGRESS, evitando adicionar um estado que invalidaria índices e permissões existentes.
