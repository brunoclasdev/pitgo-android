---

description: "Task list for Melhoria de UX das telas do cliente"
---

# Tasks: Melhoria de UX das telas do cliente

**Input**: Design documents from `/specs/014-client-ux-improvements/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Não solicitados nesta feature (constituição do produto, princípio V); nenhuma task de
teste automatizado é gerada. Validação é manual via `quickstart.md`.

**Organization**: Tasks agrupadas por user story (spec.md), na ordem de prioridade (P1 → P2 → P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência)
- **[Story]**: a qual user story a task pertence (US1..US6)

## Path Conventions

Projeto único Android (`app/src/main/java/com/trustarx/pitgo/...`) + migrations SQL
(`supabase/migrations/...`), conforme `plan.md` § Project Structure.

---

## Phase 1: Setup

**Purpose**: Confirmar linha de base antes de qualquer mudança.

- [X] T001 Confirmar baseline: `./gradlew :app:assembleDebug` compila limpo e
      `supabase migration list` mostra local/remoto em sincronia (sem alteração de arquivo;
      pré-requisito para as migrations novas de US2 e US5)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Único pré-requisito compartilhado por mais de uma user story nesta feature.

**⚠️ CRITICAL**: T002 deve estar concluída antes de US5 (T022) e US6 (T030), que reaproveitam a
busca de CEP.

- [X] T002 Mover `ViaCepAddressLookupRepository` de
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ViaCepAddressLookupRepository.kt`
      para `app/src/main/java/com/trustarx/pitgo/core/address/repository/ViaCepAddressLookupRepository.kt`,
      atualizando os imports em `providerregistration` (nenhuma mudança de comportamento)

**Checkpoint**: Fundação pronta. US1-US4 não dependem de T002 e podem começar imediatamente;
US5/US6 dependem de T002.

---

## Phase 3: User Story 1 - Feedback claro durante a solicitação de serviço (Priority: P1) 🎯 MVP

**Goal**: Botões de ação mostram estado de carregamento explícito; distância nunca aparece como
"0,0 km"; contagem regressiva e busca esgotada (já implementadas) continuam sem regressão.

**Independent Test**: Solicitar um serviço e observar cada estado (buscando, proposta, aguardando,
aceito, recusado/expirado, sem prestador) sem alterar nenhuma outra tela do app.

### Implementation for User Story 1

- [X] T003 [P] [US1] Adicionar indicador de carregamento inline (`CircularProgressIndicator`
      pequeno) junto aos botões ACEITAR/RECUSAR quando `state.pending != null` em
      `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ProviderIncomingRequestScreen.kt`
- [X] T004 [P] [US1] Adicionar indicador de carregamento inline junto aos botões
      SOLICITAR/PRÓXIMO quando `state.pending != null` em
      `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ClientMobileDispatchScreen.kt`
- [X] T005 [US1] Na mesma tela (`ClientMobileDispatchScreen.kt`), formatar
      `candidate.distanceMeters` com um limiar (ex.: < 500 m) exibindo um texto amigável ("Muito
      próximo") em vez do número em km; adicionar a nova string em
      `app/src/main/res/values/strings.xml` (depende de T004, mesmo arquivo)
- [X] T006 [US1] Validar manualmente, sem alterar código, que `ClientMobileDispatchViewModel.startClock()`
      (contagem regressiva) e o estado `ClientMobileDispatchUiState.Empty` (busca esgotada)
      continuam corretos, conforme `quickstart.md` seção 4 passos 4-5 (nenhuma regressão esperada)

**Checkpoint**: User Story 1 funcional e testável isoladamente.

---

## Phase 4: User Story 2 - Cliente acompanha o guincho se movimentando no mapa (Priority: P1)

**Goal**: Cliente vê a própria posição centralizada e o guincho se movendo em tempo real na tela
de acompanhamento.

**Independent Test**: Aceitar um atendimento com um prestador `MOBILE` publicando localização e
observar, na tela de acompanhamento do cliente, a própria posição centralizada e o marcador do
guincho se deslocando, sem depender de nenhuma outra história.

### Implementation for User Story 2

- [X] T007 [P] [US2] Criar `supabase/migrations/20260823100000_client_current_locations.sql` com a
      tabela `client_current_locations`, RLS e as RPCs `publish_client_location` /
      `get_appointment_client_location`, conforme `contracts/client-location-rpcs.md` e
      `data-model.md` §3
- [X] T008 [US2] Aplicar a migration (`supabase db push`) e confirmar via
      `supabase migration list` que local/remoto ficaram em sincronia (depende de T007)
- [X] T009 [P] [US2] Estender os modelos em
      `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/model/AppointmentTrackingModels.kt`
      para incluir a posição pontual do cliente (`ClientCurrentLocation?`) junto do evento/snapshot
      já existente da posição do prestador
- [X] T010 [US2] Em
      `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/repository/AppointmentTrackingRepository.kt`,
      chamar `get_appointment_client_location` junto de `get_appointment_provider_location` dentro
      de `snapshot()`, e adicionar `suspend fun publishClientLocation(appointmentId, latitude,
      longitude, accuracyMeters, capturedAt)` chamando `publish_client_location` (depende de T008,
      T009)
- [X] T011 [US2] Em
      `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`,
      capturar a posição do cliente via `DeviceLocationRepository` em `onVisible()` (e a cada
      reabertura da tela) e chamar `repository.publishClientLocation(...)` (depende de T010)
- [X] T012 [US2] Na mesma classe (`AppointmentTrackingViewModel.kt`), ajustar `moveCameraTo` para
      enquadrar a posição do cliente e a do prestador juntas (hoje usa só a coordenada do
      prestador), preservando a suspensão de recentramento automático após gesto manual
      (`followProvider`) (depende de T011, mesmo arquivo)
- [X] T013 [US2] Em
      `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`,
      renderizar dois marcadores visualmente distintos (cliente e guincho) e o aviso textual de
      "localização do guincho ainda não disponível" quando aplicável (depende de T009, T012)

**Checkpoint**: User Stories 1 e 2 funcionais e testáveis independentemente.

---

## Phase 5: User Story 3 - Guincho vê o cliente e a si mesmo no mapa em tempo real (Priority: P1)

**Goal**: Tela de atendimento do prestador (hoje sem mapa) passa a exibir sua posição e a do
cliente, enquadradas e atualizando em tempo real.

**Independent Test**: Aceitar um atendimento do lado do prestador e verificar que a tela de
atendimento exibe um mapa com os dois marcadores enquadrados, atualizando com o deslocamento do
prestador.

### Implementation for User Story 3

- [X] T014 [US3] Em
      `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/ProviderIncomingRequestViewModel.kt`,
      consumir `AppointmentTrackingRepository` (reaproveitado de US2/T010) durante o estado de
      atendimento aceito, expondo posição do prestador e do cliente para a UI (depende de T010)
- [X] T015 [US3] Em
      `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ProviderIncomingRequestScreen.kt`,
      adicionar um `MapLibreMapHost` (reaproveitado de `core/map/ui/MapLibreMapHost.kt`) no estado
      de atendimento aceito, enquadrando as duas posições na câmera inicial e suspendendo
      recentramento automático após gesto manual (depende de T014)
- [X] T016 [US3] Na mesma tela, exibir aviso textual quando a posição do cliente ainda não estiver
      disponível, nunca um marcador na origem (0,0) (depende de T015, mesmo arquivo)

**Checkpoint**: User Stories 1-3 funcionais e testáveis independentemente.

---

## Phase 6: User Story 4 - Voltar sem travar entre as telas do atendimento (Priority: P1)

**Goal**: Um "voltar" manual sai efetivamente da tela do atendimento; a retomada automática só
dispara em início de sessão ou mudança real de status.

**Independent Test**: Com um atendimento ativo, tocar em voltar repetidamente a partir da tela de
acompanhamento/atendimento, como cliente e como prestador, confirmando que o app permanece na tela
anterior.

### Implementation for User Story 4

- [X] T017 [US4] Em
      `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, ajustar o
      `LaunchedEffect(dispatchResumeState, sessionState, currentRoute)` para só disparar a
      navegação de retomada quando a `revision` do snapshot for maior que a última revisão já
      processada nesta sessão do app (novo estado local `remember`), preservando o disparo único
      ao iniciar/retomar sessão — conforme `contracts/appointment-map-and-navigation.md`
- [ ] T018 [US4] Validar manualmente os 3 cenários de aceite (voltar como cliente, voltar como
      prestador, cold start com atendimento ativo) conforme `quickstart.md` seção 7 (depende de
      T017)

**Checkpoint**: User Stories 1-4 (todas as P1) funcionais e testáveis independentemente.

---

## Phase 7: User Story 5 - Início (Home) acionável (Priority: P2)

**Goal**: Endereço do cliente passa a existir de fato (hoje só texto estático) e vira ação
tocável na Home; card de veículo sem regressão.

**Independent Test**: Logar com uma conta cliente sem endereço cadastrado e verificar que a Home
oferece um caminho direto para cadastrá-lo.

### Implementation for User Story 5

- [X] T019 [P] [US5] Criar `supabase/migrations/20260823090000_client_addresses.sql` com a tabela
      `client_addresses`, RLS e as RPCs `get_my_client_address` / `upsert_my_client_address`,
      conforme `contracts/client-address-rpcs.md` e `data-model.md` §1
- [X] T020 [US5] Aplicar a migration (`supabase db push`) e confirmar via `supabase migration list`
      (depende de T019)
- [X] T021 [US5] Criar `ClientAddressRepository` em
      `app/src/main/java/com/trustarx/pitgo/features/clientaddress/repository/ClientAddressRepository.kt`,
      chamando `get_my_client_address`/`upsert_my_client_address` e reaproveitando
      `ViaCepAddressLookupRepository` (depende de T002, T020)
- [X] T022 [US5] Criar `ClientAddressViewModel` em
      `app/src/main/java/com/trustarx/pitgo/features/clientaddress/viewmodel/ClientAddressViewModel.kt`
      com estados Loading/Empty/Content/Saving/Error (depende de T021)
- [X] T023 [US5] Criar `ClientAddressScreen` em
      `app/src/main/java/com/trustarx/pitgo/features/clientaddress/ui/ClientAddressScreen.kt`
      (formulário com busca de CEP, seguindo o padrão visual já usado no cadastro de endereço do
      prestador) (depende de T022)
- [X] T024 [US5] Registrar a nova rota em
      `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e o wiring em
      `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` para
      `ClientAddressScreen` (depende de T023)
- [X] T025 [US5] Em
      `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt` e
      `app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt`, tornar
      `NeutralLocationRow` tocável, buscando o estado real via `ClientAddressRepository` e
      navegando para `ClientAddressScreen` (cadastrar ou editar) (depende de T021, T024)
- [X] T026 [US5] Validar manualmente, sem alterar código, que `NoVehicleCard`/`VehicleCard` em
      `HomeScreen.kt` continuam funcionando conforme `quickstart.md` seção 2 passo 4 (nenhuma
      regressão esperada)

**Checkpoint**: User Stories 1-5 funcionais e testáveis independentemente.

---

## Phase 8: User Story 6 - Perfil completo e autoexplicativo (Priority: P3)

**Goal**: Aba Perfil passa a exibir/editar endereço, veículo padrão e aparência (Claro/Escuro/
Sistema), sem sair da aba.

**Independent Test**: Abrir a aba Perfil e confirmar que endereço, veículo padrão e tema podem ser
vistos e alterados a partir dali. *(A parte de endereço depende do backend introduzido em US5 —
ver nota em Dependencies.)*

### Implementation for User Story 6

- [X] T027 [P] [US6] Criar `AppearancePreferences` (DataStore) em
      `app/src/main/java/com/trustarx/pitgo/core/preferences/AppearancePreferences.kt` com
      `AppearanceMode` (`LIGHT`/`DARK`/`SYSTEM`), padrão `SYSTEM`
- [X] T028 [US6] Ajustar `app/src/main/java/com/trustarx/pitgo/MainActivity.kt` para ler
      `AppearancePreferences` e repassar o override (`Boolean?`) para
      `PitGoTheme(darkTheme = ...)` (depende de T027)
- [X] T029 [US6] Criar `ProfileViewModel` em
      `app/src/main/java/com/trustarx/pitgo/features/profile/viewmodel/ProfileViewModel.kt`,
      agregando endereço (via `ClientAddressRepository` de US5), veículo padrão (sessão já
      existente) e `AppearancePreferences` (depende de T021, T027)
- [X] T030 [US6] Ajustar
      `app/src/main/java/com/trustarx/pitgo/features/profile/ui/ProfileScreen.kt` para exibir/
      editar endereço e veículo padrão (reaproveitando a navegação de US5/Garagem) e a seleção de
      aparência, consumindo `ProfileViewModel` (depende de T029)

**Checkpoint**: Todas as 6 user stories funcionais e testáveis independentemente.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Verificação final, sem novo comportamento.

- [X] T031 [P] Rodar `./gradlew :app:lintDebug :app:assembleDebug` e corrigir quaisquer avisos
      introduzidos pelas mudanças desta feature
- [X] T032 [P] Revisar as novas strings em `app/src/main/res/values/strings.xml` quanto a clareza e
      consistência de tom com o restante do app
- [X] T033 Executar `quickstart.md` de ponta a ponta nos dois papéis (cliente e prestador),
      confirmando o critério de encerramento da seção 8

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup; bloqueia apenas US5/US6 (reuso de CEP lookup).
- **US1 (Phase 3)** e **US4 (Phase 6)**: sem dependência de Foundational; podem começar logo após
  o Setup.
- **US2 (Phase 4)**: sem dependência de Foundational; depende só de si mesma.
- **US3 (Phase 5)**: depende de US2 (T010 — reaproveita `AppointmentTrackingRepository` já
  estendido); não é totalmente independente na prática, apesar de testável como incremento
  separado.
- **US5 (Phase 7)**: depende de Foundational (T002).
- **US6 (Phase 8)**: depende de US5 (T021, `ClientAddressRepository`) para a parte de endereço; a
  parte de veículo padrão e aparência não depende de nenhuma outra história. Isto é uma exceção
  pontual à independência descrita no spec — motivada pela descoberta do research.md de que o
  endereço do cliente não existia antes desta feature, então só pode haver uma implementação dele
  (em US5), não uma duplicada em US6.
- **Polish (Phase 9)**: depende de todas as user stories desejadas estarem completas.

### Parallel Opportunities

- T003 e T004 (arquivos diferentes) podem rodar em paralelo.
- T007 e T009 (migration SQL vs. modelo Kotlin) podem rodar em paralelo.
- T019 e T027 (migration de endereço vs. preferência de aparência local) podem rodar em paralelo,
  já que pertencem a stories diferentes (US5/US6) sem dependência mútua direta.
- T031 e T032 podem rodar em paralelo.

---

## Parallel Example: User Story 1

```bash
Task: "Adicionar indicador de carregamento inline nos botões ACEITAR/RECUSAR em
ProviderIncomingRequestScreen.kt"
Task: "Adicionar indicador de carregamento inline nos botões SOLICITAR/PRÓXIMO em
ClientMobileDispatchScreen.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 apenas)

1. Completar Phase 1 (Setup).
2. Completar Phase 3 (US1) — não depende de Foundational.
3. **PARAR e VALIDAR**: testar US1 isoladamente via `quickstart.md` seção 4.

### Entrega incremental

1. Setup → US1 (MVP) → validar.
2. US2 → validar (mapa do cliente em tempo real).
3. US3 → validar (mapa do prestador, reaproveita infra de US2).
4. US4 → validar (botão voltar).
5. Foundational → US5 → validar (Home acionável).
6. US6 → validar (Perfil completo, reaproveita endereço de US5).

Cada etapa agrega valor sem quebrar as anteriores; a ordem sugerida segue a prioridade do spec
(P1 → P2 → P3), com US3 logo após US2 por reaproveitar sua infraestrutura de backend.
