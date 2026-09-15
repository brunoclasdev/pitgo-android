# Tasks: Mercado Pago Orders

**Input**: Design documents from `specs/022-mercadopago-orders/`.
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contratos internos](contracts/payment-interfaces.md), [contrato externo](contracts/mercadopago-orders.md), [quickstart.md](quickstart.md).
**Organization**: Quatro histórias P1, em ordem da especificação. Infraestrutura compartilhada primeiro; recuperação e reembolso completos antes da ativação da migração.
**Tests**: Não criar testes automatizados, mocks, fixtures ou suítes: não foram solicitados. As tarefas finais executam compilação/lint e validação manual prevista no plano.

## Format: `[ID] [P?] [Story] Description`

- [P] identifica trabalho em arquivos distintos que pode ocorrer em paralelo quando seus pré-requisitos estiverem concluídos; não autoriza iniciar tarefas dependentes antecipadamente.
- [US1]–[US4] identificam as histórias. Setup, fundação e acabamento não recebem etiqueta de história.
- Cada tarefa permanece desmarcada até implementação/validação real. Evidência ausente não equivale a aprovação.

## Path Conventions

Caminhos relativos à raiz do repositório. Novos arquivos são destinos de implementação, não arquivos criados por este comando. Timestamps de migrations abaixo foram escolhidos após o último existente; conferir colisões antes de implementar e, se necessário, renomear preservando a ordem e atualizar estas referências. Não editar migrations já aplicadas.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar execução e homologação preservando stack e dados existentes.

- [X] T001 Conferir Java/Android SDK, CLI Supabase, Deno e dependências existentes em `gradle/libs.versions.toml` e `app/build.gradle.kts`; registrar disponibilidade e comandos aplicáveis em `specs/022-mercadopago-orders/quickstart.md`, sem upgrades genéricos.
- [X] T002 Consolidar em `specs/022-mercadopago-orders/quickstart.md` os pré-requisitos de homologação, cartão/pagamentos legados, segredos ignorados pelo Git, Vault e sequência de ativação/suspensão; não substituir credenciais ausentes por valores fictícios nem publicar produção.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Identidade financeira durável, validação server-side, aplicação atômica e execução independente do app. Esta fase bloqueia a integração das histórias.

- [X] T003 [P] Criar `supabase/migrations/20260915020000_orders_payment_schema.sql` com expansão de payments, backfill PAYMENTS sem alterar resultado financeiro, IDs Orders separados, estados de sincronização, valores liquidados/devolvidos, cancelamento, reembolso, compensação, revisão, agendamento e leases; criar inbox privada e índices/RLS/grants conforme `specs/022-mercadopago-orders/data-model.md`.
- [X] T004 [P] Implementar HTTP Mercado Pago compartilhado em `supabase/functions/_shared/mercadopago-http.ts` com autenticação server-side, timeout 7 s, parsing seguro inclusive 402/409/423, Retry-After, erros sanitizados e nenhuma repetição automática de criação ou log de payload sensível.
- [X] T005 [P] Implementar normalização de Orders em `supabase/functions/_shared/mercadopago-orders.ts`: valores decimais exatos, IDs distintos, QR/vencimento, estados de ordem/transação e evidência de quitação/devolução; desconhecidos, chargeback e reembolso parcial não aprovam atendimento.
- [X] T006 Criar `supabase/migrations/20260915021000_orders_payment_reservation.sql` com reserva atômica por request_id, titularidade, índice bloqueador incluindo incerteza/REFUND_FAILED e compensações, preço móvel obtido do catálogo e proposta PitTec confirmada; persistir UUID/chave/referência antes de HTTP e rejeitar contexto conflitante (depende de T003).
- [X] T007 Criar `supabase/migrations/20260915022000_orders_payment_reconciliation.sql` com aplicação transacional do snapshot externo, comparação exata de valor/moeda/vínculos, revision/lease, precedência de cancelamento e no-op em duplicatas; claims e reagendamento de payments/inbox somente por service_role, sem transação aberta durante HTTP (depende de T003/T006).
- [X] T008 Implementar conciliação de recursos com ID conhecido em `supabase/functions/_shared/payment-reconciliation.ts` usando T004/T005/T007, GET autoritativo e aplicação comum para resposta de criação/inbox/executor; preservar eventos sem associação para recuperação posterior e agendar pendências sem assumir aprovação.
- [X] T009 Criar `supabase/functions/mp-reconcile-payments/index.ts` com segredo interno dedicado, claim de 20 itens, concorrência 2, orçamento 40 s, lease 90 s, liberação de itens não iniciados, backoff/jitter e métricas sanitizadas; processar consultas conhecidas de T008 e preservar trabalho ainda não resolvido sem descartá-lo.
- [X] T010 Criar `supabase/migrations/20260915023000_orders_payment_scheduler.sql` e configurar `supabase/config.toml` para um cron de minuto que só invoca T009 quando houver trabalho, usando Vault/MP_RECONCILE_SECRET; impedir JWT comum de executar worker, indexar vencimentos e limpar inbox processada após 7 dias em lotes, sem remover pendências nem alterar cadência PitTec.

**Checkpoint**: Estrutura e operações compartilhadas implementadas, com claims/leases, autorização e agendamento funcionais. Rotas por história completam os casos de criação e recuperação abaixo; não publicar um worker incompleto como migração concluída.

---

## Phase 3: User Story 1 — Pagar via Pix (Priority: P1) — MVP de homologação

**Goal**: Criar Pix Orders nos fluxos móvel e PitTec com valor/vencimento corretos e avanço somente após confirmação válida.
**Independent Test**: Com cliente sem cartão, percorrer ambos os fluxos, obter QR/copia e cola, confirmar pagamento, observar recusa/expiração e impedir cobrança de proposta PitTec não confirmada. Usar quickstart; nenhum cartão é pré-requisito.

### Implementation for User Story 1

- [X] T011 [P] [US1] Ampliar `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentResults.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/data/PaymentDtos.kt` para service_id, sync_state, revision, snapshot/contexto, decimais e respostas 200/202, preservando campos legados e distinguindo erro de contrato de recusa.
- [X] T012 [P] [US1] Migrar o caminho Pix de `supabase/functions/mp-create-payment/index.ts` para POST /v1/orders automático, PT30M, chave/referência reservadas por T006 e aplicação comum T008; ativação MP_ORDERS_ENABLED controla apenas novas criações, sem fallback silencioso, e timeout retorna tentativa VERIFYING existente.
- [X] T013 [P] [US1] Atualizar `supabase/functions/mp-webhook/index.ts` para Order e Payment, limite 64 KiB, HMAC pelo identificador assinado com comparação segura, checagem URL/body e inbox deduplicada; ACK após persistência, 5xx se persistência falhar, sem aceitar preço/status do corpo como confirmação.
- [X] T014 [P] [US1] Criar `supabase/migrations/20260915024000_orders_payment_snapshot.sql` ampliando get_payment_status com snapshot Pix/contexto somente do titular e broadcast CHANGED com revision/sync_state apenas em mudança visível; preservar assinatura e campos existentes, sem HTTP dentro da RPC.
- [X] T015 [US1] Atualizar `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt` para enviar service_id, consumir snapshot 200/202 e erros acionáveis e consultar estado autenticado; corrigir classificação de timeout sem engolir cancelamento real de coroutine (depende de T011/T012/T014).
- [X] T016 [US1] Adaptar `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentCheckoutUiState.kt` para Pix, valor confirmado, validade, criação/verificação e reconsulta antes de retry/troca de meio, sem recriar tentativa incerta (depende de T015).
- [X] T017 [US1] Integrar QR/copia e cola, vencimento e mensagens acionáveis em `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt`, `app/src/main/res/values/strings.xml` e passagem de service_id/proposta em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`; preservar confirmação de valor PitTec antes da cobrança (depende de T016).
- [X] T018 [P] [US1] Criar `supabase/migrations/20260915025000_orders_dispatch_payment_guards.sql` reforçando start_mobile_dispatch e guard PitTec com titular/serviço, quitação integral líquida, CURRENT e ausência de conflito/cancelamento/devolução; impedir reaplicação de APPROVED no trigger PitTec, preservar serviços gratuitos e delimitar compatibilidade PAYMENTS sem inventar service_id legado.

**Checkpoint**: Pix pode ser demonstrado isoladamente em homologação. Ativação completa permanece condicionada a US2/US3/US4, todas P1.

---

## Phase 4: User Story 2 — Continuar usando cartões salvos (Priority: P1)

**Goal**: Pagar com cartões anteriores e novos, com token novo e cadastro opcional.
**Independent Test**: Selecionar cartão cadastrado antes da migração, gerar token nativo com CVV e pagar; repetir cadastro/recusa/remoção sem perda de histórico e confirmar que Pix funciona sem cartão.

### Implementation for User Story 2

- [X] T019 [P] [US2] Criar `supabase/migrations/20260915026000_orders_saved_card_context.sql` com get_saved_card_tokenization_context restrita ao titular e metadados de método/tipo necessários, preservando mp_customer_id/mp_card_id existentes e sem armazenar dados completos de cartão.
- [X] T020 [US2] Atualizar `supabase/functions/mp-save-card/index.ts` e `supabase/functions/mp-remove-card/index.ts` para preservar Customers/Cards, validar titularidade e completar metadados do método a partir do provedor, sem recadastro em massa ou perda de histórico após remoção (depende de T019 antes de integração ao banco).
- [X] T021 [US2] Adicionar DTO/modelo e consulta do contexto de tokenização em `app/src/main/java/com/trustarx/pitgo/features/paymentmethods/data/SavedCardDtos.kt`, `app/src/main/java/com/trustarx/pitgo/features/paymentmethods/model/SavedCardModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/paymentmethods/repository/SavedCardRepository.kt`, expondo apenas dados autorizados ao fluxo nativo (depende de T019).
- [X] T022 [P] [US2] Implementar componente nativo de confirmação CVV em `app/src/main/java/com/trustarx/pitgo/features/payment/ui/SavedCardSecurityCodeSheet.kt` usando SDK 1.0.0 SecurityCodeTextField/PCIFieldState e generateCardToken(cardId, securityCodeState, buyerIdentification), devolvendo data.token e descartando dados sensíveis ao sair; nenhum acesso direto a Repository na UI.
- [X] T023 [P] [US2] Completar cartão em `supabase/functions/mp-create-payment/index.ts` com token novo obrigatório, payer.customer_id, método/tipo do cartão do titular e installments=1; remover fallback card-ID-as-token e tratar HTTP 402 com identidade da order preservada (depende de T019/T020).
- [X] T024 [US2] Integrar seleção, obtenção de contexto, tokenização e envio do token em `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentCheckoutUiState.kt`, `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt` e `app/src/main/res/values/strings.xml`; recusa permite Pix/outro cartão sem contornar tentativa incerta (depende de T021/T022/T023).
- [X] T025 [US2] Ajustar estados de cadastro/listagem/remoção em `app/src/main/java/com/trustarx/pitgo/features/paymentmethods/viewmodel/PaymentMethodsViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/paymentmethods/ui/PaymentMethodsScreen.kt` para erros recuperáveis e cartão removido durante seleção; manter cadastro opcional exclusivamente na área dedicada e dados mascarados (depende de T020/T021/T024).

**Checkpoint**: Cartão salvo funciona sem recadastro causado pela migração. A cobrança usa a infraestrutura comum de US1; não depende de usuário pagar via Pix antes.

---

## Phase 5: User Story 3 — Recuperar resultado sem duplicar cobrança (Priority: P1)

**Goal**: Recuperar tentativas e histórico, incluindo legado, com concorrência, eventos fora de ordem e app fechado.
**Independent Test**: Interromper resposta de criação, encerrar processo e repetir ação em dois dispositivos; verificar mesma obrigação/tentativa, consulta autônoma, ausência de liberação duplicada e retomada de pagamento antigo. Busca vazia deve manter verificação.

### Implementation for User Story 3

- [X] T026 [P] [US3] Adicionar busca Orders paginada por external_reference e janela de criação com margem de 5 min em `supabase/functions/_shared/mercadopago-orders.ts`; busca vazia não prova ausência, múltiplos resultados retornam identidades para compensação e nenhuma consulta gera POST de cobrança.
- [X] T027 [P] [US3] Implementar adaptador legado em `supabase/functions/_shared/mercadopago-payments.ts` com consulta por mp_payment_id, moeda/valor/referência legados validados e normalização para a aplicação comum; recuperar fatos de liquidação/devolução sem supor valores ausentes, mantendo IDs PAYMENTS separados de ORDERS.
- [X] T028 [US3] Completar recuperação pré/pós-envio sem ID e associação de inbox em `supabase/functions/_shared/payment-reconciliation.ts` e `supabase/functions/mp-reconcile-payments/index.ts`; backoff 1/5/15 min, suporte após 24 h e consulta horária continuada, sem reter token, ACK perdido ou nova chave por timeout (depende de T026/T027).
- [X] T029 [P] [US3] Criar `supabase/migrations/20260915027000_orders_payment_recovery_reads.sql` com get_recoverable_payment e get_payment_history paginada (20 padrão/50 máximo) somente do titular, incluindo múltiplas pendências e aprovado móvel sem busca; preparar backfill/agendamento de pendências legadas sem recriar cobranças.
- [X] T030 [US3] Adicionar leitura de recuperação/histórico, snapshots completos e Realtime por revisão em `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/data/PaymentDtos.kt`; fallback único a cada 10 s apenas com tela ativa e descarte de revisões antigas (depende de T029).
- [X] T031 [US3] Persistir request_id em SavedStateHandle e reconstruir Pix/contexto/status antes de nova seleção em `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt`; cancelar observadores ao sair e diferenciar retorno à tentativa de criação nova (depende de T030).
- [X] T032 [US3] Integrar descoberta de pendência após nova sessão/abertura em `app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`; permitir retomar mesma solicitação e pedir localização atual antes do despacho móvel, sem polling de Home a cada reemissão da sessão (depende de T031).
- [X] T033 [US3] Implementar histórico mínimo autenticado em `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentHistoryUiState.kt`, `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentHistoryViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentHistoryScreen.kt`, com rota em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`/`app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e entrada em `app/src/main/java/com/trustarx/pitgo/features/profile/ui/ProfileScreen.kt`; paginação, vazio/erro/loading, múltiplas pendências e meio mascarado após remoção de cartão (depende de T030/T032).
- [X] T034 [US3] Criar `supabase/migrations/20260915028000_orders_payment_compensation.sql` e integrar `supabase/functions/_shared/payment-reconciliation.ts` para recebimentos excedentes/aprovações de propostas antigas: registrar cada identidade sem colidir com obrigação ativa, não liberar duas vezes, agendar devolução integral, marcar suporte e bloquear nova cobrança enquanto compensação estiver pendente (depende de T028/T029).

**Checkpoint**: Recuperação e acompanhamento funcionam sem ação do cliente. Excedentes identificados ficam registrados/agendados; a conclusão da devolução pertence a US4, sem tornar US3 dependente de US4 para detectar/proteger a obrigação financeira.

---

## Phase 6: User Story 4 — Cancelar e acompanhar reembolso (Priority: P1)

**Goal**: Persistir cancelamento e concluir devoluções elegíveis de forma autônoma, com recuperação de falhas.
**Independent Test**: Cancelar pagamentos pendentes/aprovados, Orders e legados, antes da conclusão; fechar app e acompanhar aprovação tardia, estorno integral, falhas transitórias e limite de três envios com suporte. Somente devolução confirmada aparece como concluída.

### Implementation for User Story 4

- [X] T035 [P] [US4] Criar `supabase/migrations/20260915029000_orders_payment_cancellation.sql` para intenção atômica de cancelamento/reembolso no checkout e nas RPCs vigentes de cancelamento/encerramento móvel/PitTec; incluir pendentes e aprovação tardia, preservar cancelamento pós-conclusão e não reintroduzir encerramento de busca removido por migrations mais recentes.
- [X] T036 [P] [US4] Implementar cancelamento e devolução por origem em `supabase/functions/_shared/mercadopago-orders.ts` e `supabase/functions/_shared/mercadopago-payments.ts`: Orders /cancel e /refund integral, Payments nas rotas legadas, chaves persistentes por operação e consulta após conflitos/timeouts; 2xx isolado não confirma reembolso.
- [X] T037 [US4] Implementar execução de CANCEL/REFUND em `supabase/functions/_shared/payment-reconciliation.ts` e `supabase/functions/mp-reconcile-payments/index.ts` com contador persistido antes de envio, até 3 envios seguros, intervalos 1/5 min, consultas separadas e proteção de lease/revisão; saldo/prazo/erro definitivo sinalizam suporte e resultado incerto continua observado (depende de T035/T036).
- [X] T038 [US4] Criar `supabase/functions/mp-cancel-payment/index.ts`, adaptar `supabase/functions/mp-refund-payment/index.ts` e registrar configuração em `supabase/config.toml` para autenticação/titularidade/elegibilidade, persistência e resposta 200/202 idempotente; remover sleeps e dependência de invocação pelo celular (depende de T035/T037).
- [X] T039 [US4] Adaptar comandos/DTOs e métodos de cancelamento/reembolso em `app/src/main/java/com/trustarx/pitgo/features/payment/data/PaymentDtos.kt` e `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt` para snapshot assíncrono, sem esperar ciclo de retries remoto ou tratar 202 como devolução concluída (depende de T038).
- [X] T040 [US4] Apresentar cancelamento, reembolso em andamento, confirmado ou com suporte em `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentCheckoutUiState.kt`, `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentHistoryScreen.kt` e `app/src/main/res/values/strings.xml`; fechar app não deve desfazer intenção persistida (depende de T039).
- [X] T041 [US4] Integrar retorno de cancelamento e reembolso em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/ClientMobileDispatchViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt`, removendo responsabilidade de completar estorno no lifecycle do app; refletir estado servidor e preservar demais transições do atendimento (depende de T039/T040).
- [X] T042 [US4] Documentar consulta operacional restrita de REFUND_FAILED, compensações e support_required_at em `specs/022-mercadopago-orders/quickstart.md`, incluindo confirmação tardia após encaminhamento e distinção entre tentativas e consultas; usar os campos/métricas implementados, sem criar portal de suporte ou enviar mensagens externas (depende de T037/T041).

**Checkpoint**: Cancelamento e reembolso preservam as regras existentes e continuam com app fechado; nenhuma chamada do celular é requisito para concluir o processo.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verificar os incrementos integrados e registrar evidência antes de ativar a migração. Homologação requer ambiente selecionado e credenciais válidas; qualquer falta deve ser registrada, não contornada.

- [ ] T043 Executar verificação de tipos das funções alteradas em `supabase/functions/`, aplicar novas migrations em banco local de validação e executar lint SQL conforme `specs/022-mercadopago-orders/quickstart.md`; corrigir falhas do escopo e registrar impedimentos reais de ambiente sem marcar validação não executada como concluída. Parcial em 2026-09-15: `deno check` de todas as `mp-*` functions passou sem erros; `supabase migration up --local`/`supabase db lint --local` não executados — Docker ausente do PATH deste ambiente (impedimento real, ver quickstart.md). Permanece pendente até rodar com Docker disponível.
- [X] T044 [P] Executar `./gradlew :app:assembleDebug :app:lintDebug` usando `app/build.gradle.kts` e revisar acessibilidade/estados dos fluxos alterados em `app/src/main/java/com/trustarx/pitgo/features/payment/ui/`; corrigir falhas do escopo, sem gerar suítes automatizadas ou atualizar dependências não relacionadas. Executado 2026-09-15: BUILD SUCCESSFUL, lintDebug sem erros (115 avisos pré-existentes do projeto, nenhum em `features/payment`).
- [ ] T045 Validar em homologação explicitamente selecionada a transição descrita em `specs/022-mercadopago-orders/quickstart.md`: schema/backend compatíveis antes da ativação, segredos/Vault, notificações Order+Payment, cron e Android; demonstrar cartão salvo anterior e consulta Orders antes de habilitar novas criações, preservando leitura/devolução legadas.
- [ ] T046 Executar a matriz manual US1/US2/US3/US4 de `specs/022-mercadopago-orders/quickstart.md`, incluindo app fechado, crash/lease, criação sem resposta, duplicatas, aprovação tardia, três tentativas de reembolso, moeda/valor e legado; registrar evidências sanitizadas por SC-001–005/008, sem bypass de assinatura ou fixtures no produto (depende de T045).
- [ ] T047 Medir SC-006 com 20 ações (ao menos 19 com feedback em até 10 s), revisar SC-007 e isolamento do executor/RPCs em `supabase/functions/`, `supabase/config.toml` e `specs/022-mercadopago-orders/quickstart.md`; conferir lotes/backoff, cron sem HTTP ocioso, retenção e nenhum segredo/PAN/CVV/token persistido ou logado (depende de T045/T046).
- [ ] T048 Consolidar resultados, limitações e procedimento de suspensão de novas cobranças em `specs/022-mercadopago-orders/quickstart.md` e atualizar conclusão em `specs/022-mercadopago-orders/tasks.md`; confirmar que reconciliação/cancelamento/reembolso permanecem ativos para ambas as origens e que nenhuma migration destrutiva ou ativação de produção foi executada implicitamente (depende de T043–T047).

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Setup (T001–T002)
  └─ Fundação (T003–T010)
       └─ US1 Pix (T011–T018)
            ├─ US2 Cartão (T019–T025)
            └─ US3 Recuperação (T026–T034)
                 └─ US4 Reembolso (T035–T042)
US2 + US3 + US4 → Verificação integrada (T043–T048)
```

Ordem padrão: IDs crescentes. O grafo representa dependências funcionais; US2 e US3 compartilham ViewModel/Repository/rotas, portanto integrar essas edições sequencialmente (US2 antes de US3) mesmo quando trabalhos isolados no backend começarem em paralelo. US4 usa o executor/adaptador legado de US3; pode ser validada com Pix, sem cartão.

### Dependências específicas

- T003/T004/T005 podem começar juntos após Setup. T006 depende de T003; T007 depende de T006; T008 depende de T004/T005/T007; T009 depende de T008; T010 integra T009.
- Depois da fundação, T011/T012/T013/T014/T018 são arquivos distintos. T015 depende de T011/T012/T014; T016 depende de T015; T017 depende de T016. Checkpoint US1 exige todas, inclusive webhook/executor e guards.
- US2: T019 e T022 podem começar juntas após US1; T020/T021 aguardam T019. T023 aguarda T020 e pode coincidir com T022 se esta ainda não terminou. T024 reúne T021/T022/T023; T025 encerra a história.
- US3: T026/T027/T029 podem começar juntos após US1 e fundação, sem editar arquivos de US2 em paralelo. T028 depende de T026/T027; T030 depende de T029 e da conclusão das edições US2 em DTO/Repository; T031 → T032 → T033; T034 depende de T028/T029 e conclui o backend de compensação.
- US4: T035/T036 podem começar juntas após US3; T037 depende de ambas; T038 → T039 → T040 → T041; T042 depende de T037/T041.
- T043/T044 podem executar juntas após as histórias. T045 exige ambas concluídas; T046 → T047 → T048. Não ativar Orders para usuários finais apenas porque compilação passou.

### Parallel Examples per User Story

| História | Exemplo seguro | Pré-requisitos |
|---|---|---|
| US1 | T011 DTOs Android + T012 Pix backend + T013 webhook + T014 snapshot SQL + T018 guards SQL | Fundação concluída; migrations aplicadas em ordem antes da validação |
| US2 | T019 contexto SQL + T022 componente CVV | US1 concluída; contrato do contexto já definido |
| US3 | T026 busca Orders + T027 adaptador Payments + T029 leituras SQL | US1/fundação concluídas; integração Android aguarda US2 |
| US4 | T035 intenções SQL + T036 operações externas | US3 concluída |

[P] expressa uma oportunidade de organização de trabalho, não solicitação de criação de agentes.

## Implementation Strategy

### MVP First

Setup + Fundação + US1 formam o primeiro incremento demonstrável: pagamento Pix nos dois fluxos, com preço validado e confirmação. Validar esse incremento em homologação e continuar as demais histórias. Como esta é uma migração de meios já oferecidos, US1 isolada não é autorização para desativar cartões, legado, recuperação ou reembolsos em produção.

### Incremental Delivery

1. Fundação e Pix → confirmação correta.
2. Cartão salvo → compatibilidade nativa preservada.
3. Recuperação/histórico → tolerância a falhas e legado.
4. Reembolso → devolução autônoma.
5. Verificações integradas → evidências e ativação controlada de homologação. Publicação de produção não está implicitamente incluída.

Não criar camadas/frameworks sem uso, não alterar preço/regra de atendimento e não gerar suítes de testes. Se um contrato oficial demonstrar incompatibilidade em homologação, registrar a evidência e atualizar o design antes de alterar a garantia de produto.

## Requirement Coverage

| Requisito | Tarefas principais |
|---|---|
| FR-001/002: Orders e meios existentes | T004–T005, T012, T019–T025, T036 |
| FR-003: Pix e validade | T005, T012, T014–T017 |
| FR-004: cartões preservados/opcionais | T019–T025, T045–T046 |
| FR-005: estados claros | T011, T015–T017, T030–T033, T039–T041 |
| FR-006/007: contexto e quitação | T006–T008, T018, T034–T035 |
| FR-008: deduplicação/compensação | T003, T006–T009, T026–T028, T034, T037 |
| FR-009: recuperação | T013–T014, T026–T033 |
| FR-010: legado | T003, T027–T029, T035–T038 |
| FR-011/012: cancelamento/reembolso | T034–T042 |
| FR-013: histórico | T003, T029–T033, T040 |
| FR-014: segurança | T003–T007, T009, T013, T019–T024, T029, T038, T047 |
| FR-015: gratuito/pós-conclusão | T018, T035, T041, T046 |
| FR-016: execução sem app | T007–T010, T026–T028, T031–T032, T035–T042 |
| SC-001–005/008 | T045–T046 e critérios independentes de cada história |
| SC-006/007 | T047 |

## Notes

48 tarefas: Setup 2, Fundação 8, US1 8, US2 7, US3 9, US4 8, acabamento 6. Todas inicialmente pendentes. Nenhuma tarefa representa implementação já realizada por esta geração.
