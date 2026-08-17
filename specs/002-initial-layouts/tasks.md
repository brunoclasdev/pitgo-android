---

description: "Implementation tasks for the PitGo Welcome and Home layouts"
---

# Tasks: Layouts iniciais do PitGo

**Input**: Design documents from `/specs/002-initial-layouts/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Nenhum teste automatizado foi solicitado. A implementação será verificada por build,
lint e pelos cenários manuais de `quickstart.md`, conforme a constituição.

**Organization**: As tarefas são agrupadas por história de usuário e mantêm os caminhos exatos
para que cada incremento possa ser implementado e validado sem decisões adicionais.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode executar em paralelo após as dependências anteriores, pois altera arquivos
  diferentes.
- **[Story]**: História atendida (`US1`, `US2` ou `US3`); setup, fundação e polish não usam rótulo.
- Toda tarefa usa o formato obrigatório e inclui os arquivos que cria, altera, remove ou valida.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar a base Compose existente e impedir mudanças desnecessárias de toolchain ou
dependências antes de alterar os layouts.

- [X] T001 Confirmar o baseline com `./gradlew assembleDebug` e auditar que nenhuma dependência ou plugin novo é necessário em `app/build.gradle.kts` e `gradle/libs.versions.toml`

**Checkpoint**: A configuração atual compila e está preservada para uma implementação sem novas
dependências.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Atualizar o design system compartilhado que será consumido pelas duas telas.

**⚠️ CRITICAL**: Nenhuma história começa antes desta fase terminar.

- [X] T002 [P] Atualizar a paleta de marca Light/Dark, incluindo `#7C32E8`, `#5520A8`, `#FAF9FD`, `#17151D`, `#6F6B78` e contrastes escuros em `app/src/main/java/com/trustarx/pitgo/core/design/theme/Color.kt`
- [X] T003 [P] Estender somente os tokens repetidos de tipografia, espaçamento, tamanhos, formas e elevação exigidos pelos layouts em `app/src/main/java/com/trustarx/pitgo/core/design/theme/Type.kt`, `app/src/main/java/com/trustarx/pitgo/core/design/theme/Dimensions.kt` e `app/src/main/java/com/trustarx/pitgo/core/design/theme/Shape.kt`
- [X] T004 Integrar a nova paleta/tokens nos esquemas Material Light/Dark, manter dynamic color desabilitado e preservar o tema do sistema em `app/src/main/java/com/trustarx/pitgo/core/design/theme/Theme.kt` após T002–T003

**Checkpoint**: A identidade roxa e os papéis semânticos Light/Dark estão centralizados e prontos
para Welcome e Home.

---

## Phase 3: User Story 1 - Conhecer o PitGo e entrar (Priority: P1) 🎯 MVP

**Goal**: Apresentar a Welcome completa, responsiva e acessível como destino inicial, com somente
Entrar navegando uma única vez para a Home e Back retornando corretamente.

**Independent Test**: Abrir o app, validar wordmark/título/subtítulo/benefícios/ações, confirmar que
Criar conta e Cadastrar permanecem na Welcome, tocar Entrar repetidamente e observar uma única Home;
Back deve retornar à Welcome e uma nova entrada deve continuar funcionando.

### Implementation for User Story 1

- [X] T005 [P] [US1] Adicionar wordmark, título, subtítulo definido no plano, três benefícios, três ações e descrições acessíveis da Welcome em `app/src/main/res/values/strings.xml`
- [X] T006 [P] [US1] Criar os vetores locais dos três benefícios em `app/src/main/res/drawable/ic_benefit_fast_service.xml`, `app/src/main/res/drawable/ic_benefit_verified.xml` e `app/src/main/res/drawable/ic_benefit_all_in_app.xml`
- [X] T007 [P] [US1] Implementar `WelcomeBenefitId`, `WelcomeBenefit` e a lista local ordenada de três benefícios em `app/src/main/java/com/trustarx/pitgo/features/welcome/model/WelcomeBenefit.kt`
- [X] T008 [US1] Implementar `WelcomeUiState` imutável e `WelcomeViewModel` com `StateFlow` imediatamente preenchido em `app/src/main/java/com/trustarx/pitgo/features/welcome/viewmodel/WelcomeViewModel.kt` após T005–T007
- [X] T009 [US1] Implementar `WelcomeRoute`, conteúdo stateless, gradiente fixo, safe drawing insets, rolagem vertical, `FeatureCard` e as três ações Material em `app/src/main/java/com/trustarx/pitgo/features/welcome/ui/WelcomeScreen.kt` após T008
- [X] T010 [US1] Reduzir as rotas ativas para Welcome/Home e integrar start destination, callbacks, barras do sistema edge-to-edge, flag reiniciável em RESUMED, `dropUnlessResumed`, `launchSingleTop` e Back padrão em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/MainActivity.kt` após T009
- [X] T011 [US1] Executar e registrar os cenários Welcome content, Welcome responsiveness e Welcome actions and navigation de `specs/002-initial-layouts/quickstart.md`

**Checkpoint**: A User Story 1 funciona como MVP visual e de navegação, ainda que a Home mantenha
temporariamente seu conteúdo anterior até a próxima fase.

---

## Phase 4: User Story 2 - Visualizar o contexto do atendimento (Priority: P2)

**Goal**: Substituir o conteúdo anterior da Home por saudação, avatar, veículo, endereço e card de
diagnóstico demonstrativos, mantendo a tela rolável e pronta para o catálogo.

**Independent Test**: Entrar ou abrir o destino Home e confirmar Rafael/R, BMW 320i M Sport,
`2020 · PGO-2A20`, endereço, ícones e diagnóstico, sem depender do catálogo ou de fonte externa.

### Implementation for User Story 2

- [X] T012 [P] [US2] Adicionar saudação, perfil, rótulo/modelo/ano/placa/endereço do veículo, título principal, diagnóstico e descrições acessíveis em `app/src/main/res/values/strings.xml`
- [X] T013 [P] [US2] Criar os vetores locais de veículo, localização e diagnóstico em `app/src/main/res/drawable/ic_home_vehicle.xml`, `app/src/main/res/drawable/ic_home_location.xml` e `app/src/main/res/drawable/ic_home_diagnostic.xml`
- [X] T014 [P] [US2] Implementar os modelos imutáveis `DemoProfile`, `DemoVehicle` e `DiagnosticPrompt` em `app/src/main/java/com/trustarx/pitgo/features/home/model/HomeContent.kt`
- [X] T015 [US2] Substituir o estado anterior por `HomeUiState` com perfil, veículo e diagnóstico locais, exposto em um único `StateFlow`, em `app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt` após T012–T014
- [X] T016 [US2] Reescrever `HomeRoute` e `HomeScreen` com cabeçalho/avatar, `VehicleCard`, endereço, título e `DiagnosticCard` clicável em uma única `LazyVerticalGrid(GridCells.Fixed(2))`, usando full-span e callbacks sem navegação, em `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt` após T015
- [X] T017 [US2] Executar e registrar o cenário Home demonstrative content e a parte de contexto/diagnóstico dos cenários Home interactions e Accessibility de `specs/002-initial-layouts/quickstart.md`

**Checkpoint**: A User Story 2 é verificável diretamente na Home e não contém mais o layout do
catálogo anterior.

---

## Phase 5: User Story 3 - Explorar serviços disponíveis (Priority: P3)

**Goal**: Completar a Home com seis serviços/preços em duas colunas e uma NavigationBar fixa de
cinco itens, todos com feedback sem continuidade.

**Independent Test**: Abrir a Home, confirmar serviços/valores/ordem, duas colunas e barra fixa;
rolar até Carro não liga e tocar cards/itens inferiores sem alterar destino nem seleção Início.

### Implementation for User Story 3

- [X] T018 [P] [US3] Substituir strings obsoletas de categorias/prestadores e adicionar seis serviços, preços, cinco rótulos inferiores e descrições acessíveis finais em `app/src/main/res/values/strings.xml`
- [X] T019 [P] [US3] Criar ou alinhar vetores de serviços e navegação em `app/src/main/res/drawable/ic_service_battery.xml`, `app/src/main/res/drawable/ic_service_oil_change.xml`, `app/src/main/res/drawable/ic_service_tire_change.xml`, `app/src/main/res/drawable/ic_service_dashboard_light.xml`, `app/src/main/res/drawable/ic_service_mechanic.xml`, `app/src/main/res/drawable/ic_service_car_wont_start.xml`, `app/src/main/res/drawable/ic_nav_home.xml`, `app/src/main/res/drawable/ic_nav_services.xml`, `app/src/main/res/drawable/ic_nav_garage.xml`, `app/src/main/res/drawable/ic_nav_appointments.xml` e `app/src/main/res/drawable/ic_nav_profile.xml`
- [X] T020 [P] [US3] Estender `HomeContent` com `DemoServiceId`, seis `DemoService`, `BottomItemId`, cinco `BottomNavigationItem` e invariantes de ordem/preço em `app/src/main/java/com/trustarx/pitgo/features/home/model/HomeContent.kt`
- [X] T021 [US3] Estender `HomeUiState` e `HomeViewModel` com serviços, itens inferiores e `HOME` permanentemente selecionado em `app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt` após T018–T020
- [X] T022 [US3] Adicionar título, `ServiceCard`, chaves estáveis, quebra de texto, seis células, `HomeBottomNavigation` com `NavigationBarItem` e `innerPadding` que mantém o último card visível em `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt` após T021
- [X] T023 [US3] Remover código e recursos supersedidos em `app/src/main/java/com/trustarx/pitgo/features/home/model/ServiceCategory.kt`, `app/src/main/java/com/trustarx/pitgo/features/provider/ui/ProviderSearchEntryScreen.kt`, `app/src/main/res/drawable/ic_service_tire_shop.xml` e `app/src/main/res/drawable/ic_service_vehicle_electrician.xml` após T022
- [X] T024 [US3] Executar e registrar os cenários Grid and fixed bottom navigation, Home interactions e a parte de catálogo/barra de Accessibility de `specs/002-initial-layouts/quickstart.md`

**Checkpoint**: As três histórias estão funcionais; o aplicativo contém somente Welcome e a nova
Home, sem destinos ou dados antigos de prestadores.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Auditar o escopo, validar integração e confirmar os requisitos que atravessam as três
histórias.

- [X] T025 Auditar `app/build.gradle.kts`, `gradle/libs.versions.toml` e `app/src/main/` para confirmar ausência de dependências novas, XML de layout, provider-search, código/recursos órfãos, backend, Supabase, rede, persistência, DI, autenticação, localização, placeholders e testes
- [X] T026 Executar `./gradlew clean assembleDebug lintDebug` e corrigir todos os erros bloqueantes, confirmando o APK em `app/build/outputs/apk/debug/app-debug.apk`
- [ ] T027 Executar e registrar o roteiro manual completo de Light/Dark, barras do sistema, offline, portrait/landscape, escalas 1,0×–2,0×, gestos/três botões, TalkBack, reentrada e limites de escopo em `specs/002-initial-layouts/quickstart.md`

**Checkpoint**: Build e lint passam, a validação manual está registrada e nenhum item fora do
escopo foi introduzido.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa imediatamente.
- **Foundational (Phase 2)**: depende de Setup e bloqueia todas as histórias.
- **User Story 1 (Phase 3)**: depende da Foundation e entrega a Welcome/MVP.
- **User Story 2 (Phase 4)**: depende de US1 para manter o fluxo Welcome → Home enquanto substitui
  a Home anterior.
- **User Story 3 (Phase 5)**: depende de US2 porque estende os mesmos modelos, ViewModel e tela.
- **Polish (Phase 6)**: depende das três histórias.

### User Story Dependencies

```text
Setup → Foundation → US1 (Welcome/MVP) → US2 (Contexto Home) → US3 (Catálogo/Barra) → Polish
```

- **User Story 1 (P1)**: independente após a Foundation; navega para o destino Home já existente.
- **User Story 2 (P2)**: reutiliza a navegação de US1, mas seu conteúdo pode ser validado
  diretamente na Home sem catálogo.
- **User Story 3 (P3)**: estende a Home de US2 e preserva todas as informações de contexto.

### Within Each User Story

- Textos, vetores e modelos distintos podem ser produzidos em paralelo.
- Modelos e recursos precedem ViewModels.
- ViewModels precedem coleta/renderização das telas.
- Conteúdo da tela precede integração de navegação ou extensão seguinte.
- Implementação precede o cenário manual da história.
- Nenhuma tarefa de teste automatizado é criada.

### Parallel Opportunities

- T002 e T003 podem rodar em paralelo após T001.
- T005, T006 e T007 podem rodar em paralelo após T004.
- T012, T013 e T014 podem rodar em paralelo após T011.
- T018, T019 e T020 podem rodar em paralelo após T017.
- Histórias não devem ser implementadas em paralelo porque US2 e US3 modificam incrementalmente
  `HomeContent.kt`, `HomeViewModel.kt`, `HomeScreen.kt` e `strings.xml`.

---

## Parallel Example: User Story 1

```text
Task T005: Textos da Welcome em app/src/main/res/values/strings.xml
Task T006: Vetores dos benefícios em app/src/main/res/drawable/ic_benefit_*.xml
Task T007: Modelo WelcomeBenefit em app/src/main/java/com/trustarx/pitgo/features/welcome/model/WelcomeBenefit.kt
```

## Parallel Example: User Story 2

```text
Task T012: Textos do contexto Home em app/src/main/res/values/strings.xml
Task T013: Vetores de contexto em app/src/main/res/drawable/ic_home_*.xml
Task T014: Modelos de contexto em app/src/main/java/com/trustarx/pitgo/features/home/model/HomeContent.kt
```

## Parallel Example: User Story 3

```text
Task T018: Textos finais em app/src/main/res/values/strings.xml
Task T019: Vetores de serviços/navegação em app/src/main/res/drawable/ic_service_*.xml e ic_nav_*.xml
Task T020: Modelos de serviços/navegação em app/src/main/java/com/trustarx/pitgo/features/home/model/HomeContent.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Concluir Setup e Foundation.
2. Implementar T005–T010.
3. Executar T011 e demonstrar Welcome → Home com proteção de duplicação e Back.
4. Parar nesse checkpoint se uma entrega parcial for desejada.

### Incremental Delivery

1. Setup + Foundation preparam a identidade visual compartilhada.
2. US1 entrega Welcome e a jornada mínima de entrada.
3. US2 substitui a Home anterior pelo contexto demonstrativo.
4. US3 completa catálogo e barra inferior, removendo resíduos da feature anterior.
5. Polish confirma integração, qualidade e limites de escopo.

### Parallel Team Strategy

1. A equipe conclui Setup e Foundation respeitando T004 após T002/T003.
2. Em cada história, recursos textuais, vetores e modelos podem ser atribuídos em paralelo.
3. ViewModel, tela e integração seguem sequencialmente dentro da história.
4. Finalizar e validar uma história antes de modificar os mesmos arquivos na próxima.

## Notes

- `[P]` representa trabalho em arquivos distintos no mesmo estágio de dependência.
- `[US1]`, `[US2]` e `[US3]` preservam rastreabilidade com `spec.md`.
- Não adicionar Repository, Use Case, Hilt, Retrofit/Ktor, Supabase, Room, DataStore, Coil,
  Material Icons Extended, Window Size Classes ou testes.
- Não criar telas placeholder, Snackbars “em breve”, estados artificiais ou código futuro.
- Remover arquivos obsoletos somente quando nenhum código ativo os referencia.
- Commits podem agrupar tarefas relacionadas, mas cada mudança lógica deve permanecer pequena.
