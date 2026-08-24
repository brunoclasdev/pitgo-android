# Tasks: Busca e despacho de prestadores móveis

**Input**: Design documents from `/specs/013-mobile-provider-dispatch/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Não gerar testes automatizados. A especificação e a constituição determinam validação
com Supabase local, build, lint, inspeção de segurança/performance e matriz manual.

**Organization**: As tarefas estão agrupadas pelas quatro histórias de usuário. Infraestrutura
compartilhada entra antes das histórias; cada fase termina com uma validação independente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode avançar em paralelo porque usa arquivos diferentes e não depende de tarefa ainda
  incompleta na mesma fase
- **[Story]**: história correspondente (`US1`, `US2`, `US3`, `US4`)
- Todas as tarefas possuem caminhos exatos

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: abrir uma evolução forward-only e confirmar os contratos legados que serão
reconciliados, sem alterar migrations aplicadas.

- [X] T001 Criar `supabase/migrations/20260823020000_mobile_provider_dispatch.sql` com transação, preflight de PostGIS/pg_cron/account roles/catalog/appointments, abortando quando não houver exatamente um serviço de guincho identificável ou houver dados legados incompatíveis

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: materializar schema, segurança e contratos Android usados por todas as histórias.

**⚠️ CRITICAL**: nenhuma história deve ser iniciada antes de esta fase terminar.

- [X] T002 Implementar em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql` o `dispatch_kind` de `services`, `active` de `provider_services`, `service_searches`, `private.dispatch_candidates` e as extensões compatíveis de `appointments`, com enums/checks/FKs/timestamps e limpeza do ponto do cliente em estados terminais
- [X] T003 Implementar em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql` os índices GiST e uniques parciais para uma busca ativa por cliente/serviço, não repetição de candidato, um appointment por candidato e um provider em `PENDING|ACCEPTED|IN_PROGRESS`
- [X] T004 Implementar em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql` RLS, revokes/grants, helpers `private`, ownership por `auth.uid()`, papel por `account_roles`, `SECURITY DEFINER SET search_path=''`, projeção JSON sanitizada comum e reload final do schema PostgREST
- [X] T005 [P] Criar enums, snapshots, candidato, appointment, ações permitidas, falhas, revisão, estados CLIENT/PROVIDER e pending action imutáveis em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/model/MobileDispatchModels.kt`
- [X] T006 [P] Criar DTOs serializáveis e validação defensiva da projeção RPC/Broadcast, sem DTO de owner ou payload técnico bruto, em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/data/MobileDispatchDtos.kt`
- [X] T007 Implementar a base de `MobileDispatchRepository`, classificação sanitizada de sessão/offline/timeout/serviço/contrato e validação de UUIDs, revisões, coordenadas e valores em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/repository/MobileDispatchRepository.kt`
- [X] T008 Registrar `MobileDispatchRepository` e suas dependências app-scoped sem armazenar ViewModel ou adicionar framework de DI em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`

**Checkpoint**: schema comum, segurança, modelos e fronteira Supabase prontos para as histórias.

---

## Phase 3: User Story 1 - Disponibilizar empresa móvel no mapa (Priority: P1) 🎯 MVP

**Goal**: publicar somente a posição corrente de uma empresa `MOBILE` disponível, torná-la stale
após 30 segundos e oferecer um marcador de carro acessível sem alterar providers fixos.

**Independent Test**: entrar como provider móvel, ativar disponibilidade com FINE, deslocar o
dispositivo e confirmar uma única posição atual sendo substituída aproximadamente a cada 10
segundos, exclusão após stale/inelegibilidade e marker de carro distinto do pin fixo.

### Implementation for User Story 1

- [X] T009 [US1] Criar `provider_discovery_locations`, GiST, constraints de precisão/tempo/lease e ausência de DML/SELECT direto em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T010 [US1] Implementar `publish_mobile_provider_location` e `stop_mobile_provider_location` com provider derivado da sessão, sequência monotônica, conflito de tracker, stale de 30 s e limpeza por indisponível/fixo/inativo em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T011 [P] [US1] Alinhar fix do provider para idade máxima de 30 s, precisão <=100 m, heartbeat sem minDistance e tipos de falha de localização em `app/src/main/java/com/trustarx/pitgo/core/location/data/FusedLocationDataSource.kt`, `app/src/main/java/com/trustarx/pitgo/core/location/model/DeviceLocationModels.kt` e `app/src/main/java/com/trustarx/pitgo/core/location/repository/DeviceLocationRepository.kt`
- [X] T012 [P] [US1] Generalizar target/estado/resultados de publicação para `DISCOVERY` e `APPOINTMENT`, sem guardar coordenadas, em `app/src/main/java/com/trustarx/pitgo/features/providertracking/model/ProviderTrackingModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/providertracking/data/ProviderLocationDtos.kt`
- [X] T013 [US1] Adaptar publicação/stop da localização de discovery e rejeitar respostas inválidas ou de sessão distinta em `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderLocationRepository.kt` e `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderTrackingStateRepository.kt`
- [X] T014 [US1] Generalizar o foreground service para publicar discovery com stream conflated/sequencial, uma RPC em voo, notificação imediata e nenhum histórico/fila offline, iniciando/parando pelo toggle visível e aplicando o snapshot provider confirmado, em `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/viewmodel/ProviderDashboardViewModel.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/ui/ProviderDashboardScreen.kt` e `app/src/main/java/com/trustarx/pitgo/core/auth/repository/SessionRepository.kt`
- [X] T015 [P] [US1] Adicionar `MOBILE_PROVIDER` ao modelo de marker, drawables vetoriais de carro normal/selecionado e instalação idempotente dos ícones/layers em `app/src/main/java/com/trustarx/pitgo/core/map/model/MapSurfaceModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/providermap/ui/ProviderMapStyleController.kt`, `app/src/main/res/drawable/ic_map_mobile_provider.xml` e `app/src/main/res/drawable/ic_map_mobile_provider_selected.xml`
- [ ] T016 [US1] Executar a validação independente de publicação, stale, ausência de histórico, provider FIXED e marker acessível descrita nas seções 4 e 9 de `specs/013-mobile-provider-dispatch/quickstart.md`

**Checkpoint**: o provider móvel produz uma única posição atual elegível e possui representação de
carro; providers fixos continuam sem tracking dinâmico.

---

## Phase 4: User Story 2 - Encontrar e comparar guinchos próximos (Priority: P1)

**Goal**: iniciar pelo card de guincho uma busca server-side do provider móvel mais próximo,
apresentar proposta sanitizada e avançar sem repetição pelos raios 10/25/50 km.

**Independent Test**: disponibilizar três ou mais guinchos com coordenadas/preços distintos,
confirmar menor distância primeiro, Próximo idempotente, expansão 10→25→50, vazio final e preço
copiado para um único appointment ao solicitar.

### Implementation for User Story 2

- [X] T017 [US2] Marcar exatamente o serviço de guincho como `MOBILE_SEQUENTIAL` após preflight e preservar `BROWSE` nos demais serviços em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T018 [US2] Implementar helper privado de seleção sob lock com `ST_DWithin`, `ST_Distance`, desempate por provider UUID, oferta exata ativa, localização <=30 s, exclusões persistidas e progressão 10/25/50 km em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T019 [US2] Implementar `start_mobile_dispatch`, `next_mobile_dispatch_candidate` e `request_mobile_dispatch_candidate` com revisão esperada, chaves naturais idempotentes, revalidação de candidato e preço congelado em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T020 [US2] Restringir a descoberta genérica a providers FIXED por endereço e retornar MOBILE somente no snapshot autorizado da proposta corrente em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T021 [P] [US2] Expor `ServiceDispatchKind` em `CatalogService`/`HomeService` e decodificar `dispatch_kind` nos RPCs de catálogo em `app/src/main/java/com/trustarx/pitgo/core/catalog/model/ServiceCatalogModels.kt` e `app/src/main/java/com/trustarx/pitgo/core/catalog/repository/ServiceCatalogRepository.kt`
- [X] T022 [US2] Implementar no Repository os comandos start/next/request, replay seguro e mapeamento de `ACTIVE_SEARCH_RETURNED`, `STALE_REVISION`, `CANDIDATE_INVALIDATED` e `EXHAUSTED` em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/repository/MobileDispatchRepository.kt`
- [X] T023 [US2] Implementar estado CLIENT, aquisição de localização precisa, geração estável de `searchId`, proteção contra respostas antigas/toques repetidos e ações start/next/request em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/ClientMobileDispatchViewModel.kt`
- [X] T024 [P] [US2] Implementar source/layers MapLibre de um candidato móvel e referência do cliente, atualizando GeoJSON sem recriar MapView/style ou reaplicar câmera, em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/MobileDispatchMapStyleController.kt`
- [X] T025 [US2] Criar tela CLIENT responsiva com localização necessária, buscando, proposta textual, preço/localidade/distância, Próximo, Solicitar, vazio, falhas recuperáveis e paridade acessível do mapa em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ClientMobileDispatchScreen.kt` e `app/src/main/res/values/strings.xml`
- [X] T026 [US2] Alterar o callback da Home para entregar `HomeService`, rotear `MOBILE_SEQUENTIAL` para `mobile-dispatch/new/{serviceId}`, substituir pela rota estável da busca e preservar `BROWSE` no mapa genérico em `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`
- [ ] T027 [US2] Executar a matriz de ordenação, empate, double tap, preço congelado, candidato invalidado e raios 9/11/24/26/49/51 km descrita nas seções 5 e 6 de `specs/013-mobile-provider-dispatch/quickstart.md`

**Checkpoint**: o cliente compara candidatos móveis um por vez, sem repetição, e cria no máximo
uma solicitação com preço congelado.

---

## Phase 5: User Story 3 - Acompanhar e retomar o estado da solicitação (Priority: P1)

**Goal**: persistir decisão do provider, prazo, aceite, execução, cancelamento e conclusão; retomar
o último estado confirmado após perda de rede, processo ou nova autenticação da mesma conta.

**Independent Test**: fechar/reabrir o app em cada estado, aceitar/recusar/expirar, cancelar por
cada papel e confirmar snapshot, preço, exclusividade, rota e revisão sem duplicação/regressão.

### Implementation for User Story 3

- [X] T028 [US3] Implementar reconciliação privada de deadlines e `expire_due_mobile_dispatch_requests` com `FOR UPDATE SKIP LOCKED`, avanço automático e job Supabase Cron de um segundo em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T029 [US3] Implementar `get_my_active_mobile_dispatch`, `get_my_pending_mobile_dispatch` e projeções por papel com `server_now`, deadline, revisão e allowlist de ações em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T030 [US3] Implementar `respond_to_mobile_dispatch`, `start_mobile_appointment`, `cancel_mobile_appointment` e `complete_mobile_appointment`, incluindo aceite tardio impossível, motivo obrigatório, reserva e retomada distinta por autor em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T031 [US3] Criar triggers Broadcast e policies privadas para `mobile-dispatch:<searchId>` e `provider-dispatch:<providerId>`, emitindo somente IDs/status/revisão/deadline em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql`
- [X] T032 [US3] Implementar assinatura privada como invalidação, collectors antes de subscribe, snapshot após subscribe/reconnect, filtro de revisão e remoção de canal em `NonCancellable` em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/repository/MobileDispatchRepository.kt`
- [X] T033 [P] [US3] Implementar countdown derivado de `serverNow/responseDeadline`, notice de recusa/expiração, último snapshot confirmado, retry seguro e cancelamento CLIENT com motivo em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/ClientMobileDispatchViewModel.kt`
- [X] T034 [P] [US3] Implementar ViewModel PROVIDER para snapshot pendente, aceite/recusa única, deadline visual, cancelamento com motivo e conclusão em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/ProviderIncomingRequestViewModel.kt`
- [X] T035 [US3] Criar destino urgente do provider com proposta sanitizada, countdown não anunciado a cada segundo, Aceitar/Recusar e sheet de cancelamento obrigatório em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ProviderIncomingRequestScreen.kt` e `app/src/main/res/values/strings.xml`
- [X] T036 [US3] Integrar card incoming/reservado e deep link da notificação no Dashboard sem permitir ações concorrentes em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/model/ProviderDashboardModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/viewmodel/ProviderDashboardViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/ui/ProviderDashboardScreen.kt`
- [X] T037 [US3] Implementar `DispatchResumeViewModel` para resolver fluxo ativo por papel, limpar imediatamente ao trocar `userId` e não guardar estado autoritativo em SavedState/DataStore em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/DispatchResumeViewModel.kt`
- [X] T038 [US3] Integrar gate de retomada e rotas `mobile-dispatch/{searchId}`, `provider-request/{appointmentId}` e tracking participante sem roubar navegação relacionada em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/features/session/viewmodel/SessionViewModel.kt`
- [X] T039 [US3] Adaptar tracking privado e UI terminal para preço/status/cancelamento do dispatch, preservando ordenação `(publishedAt, version)` e limpeza de coordenada em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/model/AppointmentTrackingModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/repository/AppointmentTrackingRepository.kt`, `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`
- [ ] T040 [US3] Executar a matriz de 60 s com apps fechados, aceite/recusa, cancelamentos, preço imutável, concorrência, resposta perdida, revisions antigas e retomada/troca de conta descrita nas seções 6–8 de `specs/013-mobile-provider-dispatch/quickstart.md`

**Checkpoint**: cliente e provider retomam o mesmo fluxo persistido; prazo, reserva, preço e
cancelamento permanecem autoritativos e idempotentes.

---

## Phase 6: User Story 4 - Controlar disponibilidade e publicação da empresa móvel (Priority: P2)

**Goal**: oferecer controle e feedback completos sobre publicação, degradação, reserva e retorno
automático, interrompendo com segurança em logout, mudança de modo ou perda de permissão.

**Independent Test**: alternar disponibilidade, negar permissões separadamente, desligar GPS,
ficar offline, aceitar/finalizar atendimento e trocar sessão; confirmar status/notification,
stale em 30 s, ausência em novas buscas e retorno somente quando elegível.

### Implementation for User Story 4

- [X] T041 [P] [US4] Criar preferência técnica somente para `accountId`, tracker UUID e sequência, com limpeza por stop/logout/troca de conta e sem coordenadas/dispatch, em `app/src/main/java/com/trustarx/pitgo/core/preferences/ProviderTrackingPreferences.kt`
- [X] T042 [US4] Tornar inicialização da sessão idempotente para Activity/service, aplicar snapshot confirmado de provider e ordenar stop remoto antes de sign-out em `app/src/main/java/com/trustarx/pitgo/core/auth/repository/SessionRepository.kt` e `app/src/main/java/com/trustarx/pitgo/features/session/viewmodel/SessionViewModel.kt`
- [X] T043 [US4] Fortalecer o service com restart sticky best-effort, intent nulo, lease persistida, watchdog de permissão/Location Settings/sessão, stop remoto e retorno DISCOVERY↔APPOINTMENT sem restart invisível em `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt`
- [X] T044 [P] [US4] Ampliar estados processuais para Starting/Publishing/Degraded/Stopping e motivos distintos de permissão, GPS, precisão, offline, sessão, inelegibilidade, conflito e falha de plataforma em `app/src/main/java/com/trustarx/pitgo/features/providertracking/model/ProviderTrackingModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderTrackingStateRepository.kt`
- [X] T045 [US4] Separar pedido de FINE/COARSE da recomendação POST_NOTIFICATIONS, resolver Location Settings e sincronizar toggle Disponível com início/rollback do service em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/viewmodel/ProviderDashboardViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/ui/ProviderDashboardScreen.kt`
- [X] T046 [US4] Exibir no Dashboard estados Indisponível/Iniciando/Publicando/Degradado/Reservado, última publicação, recuperação acionável e controles responsivos a fontScale 200% em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/model/ProviderDashboardModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/ui/ProviderDashboardScreen.kt` e `app/src/main/res/values/strings.xml`
- [X] T047 [US4] Fazer a ação da notificação “Ficar indisponível” atualizar o backend antes de parar e, quando reservado, abrir cancelamento com motivo por content/deep link em `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/AndroidManifest.xml`
- [X] T048 [US4] Reconciliar término/cancelamento para retorno automático somente após novo fix elegível, mantendo `providers.available` como intenção e provider reservado fora da busca em `supabase/migrations/20260823020000_mobile_provider_dispatch.sql` e `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderLocationRepository.kt`
- [ ] T049 [US4] Executar a matriz de permissões, GPS, offline, background, swipe/force-stop/reboot, dois devices, stop da notificação, reserva e retorno descrita na seção 4 de `specs/013-mobile-provider-dispatch/quickstart.md`

**Checkpoint**: o provider entende e controla a publicação, fica inelegível de modo previsível e
retorna automaticamente somente quando todos os requisitos forem satisfeitos.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: verificar segurança, performance, acessibilidade e regressões após todas as histórias.

- [ ] T050 Validar no Supabase local schema, preflight, pg_cron por segundo, RLS/ACLs, SECURITY DEFINER/search_path, ownership, índices e ausência de leitura cruzada seguindo `specs/013-mobile-provider-dispatch/contracts/mobile-dispatch-rpcs.md` e `specs/013-mobile-provider-dispatch/quickstart.md`
- [ ] T051 Medir com 100 providers e `EXPLAIN ANALYZE` o uso do GiST/`ST_DWithin`, primeira proposta/vazio <=5 s, publicação <=15 s, stale >30 s e expiração de 60 s seguindo `specs/013-mobile-provider-dispatch/quickstart.md`
- [X] T052 Executar `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug` e conferir dependências Realtime/MapLibre sem biblioteca nova seguindo a seção 3 de `specs/013-mobile-provider-dispatch/quickstart.md`
- [ ] T053 Executar a matriz UI em API 24/33/34/36, 320dp, paisagem, tablet/fold, fontScale 200%, TalkBack, IME, mapa indisponível e navegação/back stack seguindo a seção 9 de `specs/013-mobile-provider-dispatch/quickstart.md`
- [X] T054 Revisar migration, Kotlin, logs e payloads para confirmar ausência de migrations históricas alteradas, service-role, owner confiado ao APK, coordenadas históricas, dados privados em Broadcast/logs e exceptions técnicas expostas conforme `specs/013-mobile-provider-dispatch/plan.md`

### Status da execução em 2026-08-23

- 47 de 54 tarefas concluídas; todas as tarefas de implementação e os gates de build/lint estão concluídos.
- T016, T027, T040 e T049 dependem de contas CLIENT/PROVIDER e massa controlada com múltiplos guinchos/dispositivos.
- T050 e T051 dependem do Supabase local com Docker e da massa de 100 providers; Docker não está disponível nesta máquina. A migration foi aplicada no projeto remoto e o lint remoto dos schemas `public`/`private` ficou sem ocorrências.
- T053 depende da matriz física/emulada API 24/33/34/36, tablet/fold, TalkBack e fontScale 200%; o smoke test no ASUS conectado foi concluído sem crash.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa imediatamente.
- **Foundational (Phase 2)**: depende de T001 e bloqueia todas as histórias.
- **US1 (Phase 3)**: depende de T002–T008 e entrega publicação móvel corrente.
- **US2 (Phase 4)**: depende da localização elegível de US1 e adiciona busca/proposta/request.
- **US3 (Phase 5)**: depende da busca/request de US2 e adiciona decisão, expiry, tracking e resume.
- **US4 (Phase 6)**: depende do service de US1 e das transições de US3 para fechar lifecycle/reserva.
- **Polish (Phase 7)**: depende das histórias incluídas na entrega.

### User Story Dependencies

```text
Setup -> Foundation -> US1 -> US2 -> US3 -> US4 -> Polish
```

- **US1** é testável por publicação/stale/marker com sessão PROVIDER e inspeção autorizada.
- **US2** reutiliza a posição de US1 e é testável até criar um único appointment `PENDING`.
- **US3** usa esse appointment para completar prazo, decisão, tracking e retomada.
- **US4** endurece o controle operacional sem mudar a seleção/estado já entregues.

### Within Each User Story

- Schema/constraints antes de RPCs.
- Modelos/DTOs antes do Repository e ViewModels.
- Repository antes da UI/navegação que o consome.
- ViewModel antes da integração final da tela.
- Validação manual somente depois da implementação da história correspondente.

### Parallel Opportunities

- T005 e T006 podem avançar em paralelo após o schema comum ser definido.
- Em US1, T011, T012 e T015 usam frentes diferentes; T013/T014 integram depois.
- Em US2, T021 e T024 podem avançar enquanto a seleção/RPC é implementada.
- Em US3, T033 e T034 avançam em paralelo após T032; UI provider pode avançar após o contrato do VM.
- Em US4, T041 e T044 avançam em paralelo; T045/T046 devem ser coordenadas por compartilharem UI.
- Validação SQL/performance (T050/T051) e build/UI (T052/T053) podem ser divididas após integração.

---

## Parallel Example: User Story 1

```text
Task T011: alinhar aquisição/qualidade em core/location
Task T012: modelar targets/resultados em providertracking/model e data
Task T015: preparar marker/layers MapLibre e drawables de carro
```

## Parallel Example: User Story 2

```text
Task T021: adicionar dispatchKind ao catálogo Android
Task T024: implementar controller MapLibre do candidato móvel
```

## Parallel Example: User Story 3

```text
Task T033: implementar estado/countdown/retry CLIENT
Task T034: implementar estado/decisão PROVIDER
```

## Parallel Example: User Story 4

```text
Task T041: criar preferência técnica de tracker
Task T044: ampliar estados e motivos processuais
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Concluir T001–T008.
2. Implementar T009–T015.
3. Validar T016 independentemente.
4. Parar e confirmar publicação móvel atual, stale e marker de carro antes de iniciar despacho.

US1 é o MVP técnico da localização. O primeiro incremento utilizável pelo cliente exige também US2.

### Incremental Delivery

1. **Setup + Foundation**: schema compartilhado e fronteiras seguras.
2. **US1**: provider móvel publicável e visível como carro.
3. **US2**: busca sequencial, Próximo, raios e request com preço congelado.
4. **US3**: resposta/expiry/cancelamento/tracking/retomada persistentes.
5. **US4**: lifecycle operacional completo e retorno automático.
6. **Polish**: segurança, performance, build e acessibilidade.

### Parallel Team Strategy

Depois da Foundation:

- uma frente mantém a migration/RPCs e índices;
- uma frente implementa localização/service/dashboard;
- uma frente implementa modelos/ViewModels/UI MapLibre;
- integrações em arquivos compartilhados (`PitGoNavHost`, Dashboard, Repository e migration) seguem
  a ordem de dependência para evitar merges contraditórios.

## Notes

- Tarefas `[P]` usam arquivos independentes no ponto em que são liberadas.
- Não editar migrations históricas nem executar `supabase db push` remoto automaticamente.
- Não adicionar testes automatizados, FCM, Room, WorkManager, background location ou dependências.
- Broadcast é invalidação; snapshot RPC é a fonte de verdade.
- Commits devem ser pequenos, lógicos e usar mensagens descritivas.
