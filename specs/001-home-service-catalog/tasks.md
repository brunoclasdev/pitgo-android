---

description: "Implementation tasks for the homepage service catalog"
---

# Tasks: Catálogo de Serviços na Homepage

**Input**: Design documents from `/specs/001-home-service-catalog/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Nenhum teste automatizado foi solicitado. As tarefas usam build, lint e os cenários
manuais de `quickstart.md`, conforme a constituição.

**Organization**: As tarefas são agrupadas por história de usuário para permitir a entrega e a
validação incremental de cada jornada.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode ser executada em paralelo após suas dependências, pois altera arquivos distintos.
- **[Story]**: História atendida (`US1` ou `US2`); setup, fundação e polish não usam esse rótulo.
- Toda tarefa inclui os caminhos exatos que serão criados, modificados, removidos ou validados.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Tornar o módulo compatível com Kotlin/Compose e remover dependências do scaffold
legado antes de implementar comportamento de produto.

- [X] T001 Registrar Compose Compiler 2.2.10, Compose BOM 2026.06.01, Activity Compose 1.13.0, Lifecycle 2.10.0, Navigation Compose 2.9.8 e os aliases mínimos de UI/tooling em `gradle/libs.versions.toml`
- [X] T002 [P] Declarar o alias do plugin Compose Compiler com `apply false` em `build.gradle.kts` após T001
- [X] T003 [P] Atualizar `compileSdk` para 37, habilitar Compose, aplicar o plugin Compose Compiler, trocar dependências Views pelas dependências do plano e remover ViewBinding/CMake/dependências de teste em `app/build.gradle.kts` após T001
- [X] T004 Remover o scaffold obsoleto em `app/src/main/res/layout/activity_main.xml`, `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/native-lib.cpp`, `app/src/main/res/values/colors.xml`, `app/src/test/java/com/trustarx/pitgo/ExampleUnitTest.kt` e `app/src/androidTest/java/com/trustarx/pitgo/ExampleInstrumentedTest.kt` após T003

**Checkpoint**: O projeto possui somente a infraestrutura necessária para uma aplicação Compose e
não mantém XML/JNI/testes de exemplo sem uso.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Criar tema e launch shell compartilhados que bloqueiam todas as histórias.

**⚠️ CRITICAL**: Nenhuma história de usuário começa antes desta fase terminar.

- [X] T005 [P] Definir paletas roxas Light/Dark e cores de fundo, superfície e texto em `app/src/main/java/com/trustarx/pitgo/core/design/theme/Color.kt`
- [X] T006 [P] Definir a tipografia Material 3 sem dimensões rígidas incompatíveis com font scaling em `app/src/main/java/com/trustarx/pitgo/core/design/theme/Type.kt`
- [X] T007 Integrar paletas e tipografia em `PitGoTheme`, respeitando o tema do sistema e sem dynamic color ou seletor manual, em `app/src/main/java/com/trustarx/pitgo/core/design/theme/Theme.kt` após T005 e T006
- [X] T008 [P] Converter os launch themes para NoActionBar compatível com Compose e manter o tema do manifest em `app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml` e `app/src/main/AndroidManifest.xml`

**Checkpoint**: O tema central Light/Dark e o shell Android estão prontos para receber qualquer
história.

---

## Phase 3: User Story 1 - Visualizar o catálogo principal (Priority: P1) 🎯 MVP

**Goal**: Abrir a homepage offline e apresentar as cinco categorias, na ordem especificada, em uma
grade Material responsiva e acessível.

**Independent Test**: Abrir o aplicativo sem internet e confirmar que Borracharia, Eletricista
veicular, Bateria, Troca de pneu e Mecânico aparecem em até 2 segundos, com nomes completos,
identificação além de cor e acesso a todos os itens em janela estreita ou fonte ampliada.

### Implementation for User Story 1

- [X] T009 [P] [US1] Adicionar nome do app, títulos da homepage, cinco nomes de categoria e descrições acessíveis do catálogo em `app/src/main/res/values/strings.xml`
- [X] T010 [P] [US1] Criar cinco ícones vetoriais locais coerentes e distintos em `app/src/main/res/drawable/ic_service_tire_shop.xml`, `app/src/main/res/drawable/ic_service_vehicle_electrician.xml`, `app/src/main/res/drawable/ic_service_battery.xml`, `app/src/main/res/drawable/ic_service_tire_change.xml` e `app/src/main/res/drawable/ic_service_mechanic.xml`
- [X] T011 [P] [US1] Implementar `ServiceCategoryId` e `ServiceCategory` com IDs/route values estáveis, recursos locais e ordem 0–4 em `app/src/main/java/com/trustarx/pitgo/features/home/model/ServiceCategory.kt`
- [X] T012 [US1] Implementar `HomeUiState` imutável e `HomeViewModel` com StateFlow imediatamente preenchido pelas cinco categorias locais em `app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt` após T009–T011
- [X] T013 [US1] Implementar route collector lifecycle-aware, conteúdo stateless, Scaffold, heading e `LazyVerticalGrid` adaptável com chaves estáveis, cards sem truncamento e `rememberLazyGridState` em `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt` após T012
- [X] T014 [US1] Substituir AppCompat/JNI por `ComponentActivity.setContent` e conectar `PitGoTheme` diretamente à homepage em `app/src/main/java/com/trustarx/pitgo/MainActivity.kt` após T007, T008 e T013
- [ ] T015 [US1] Executar e registrar os cenários Offline catalog, Responsive layout e Font scaling and themes de `specs/001-home-service-catalog/quickstart.md` para validar o MVP visual

**Checkpoint**: A User Story 1 funciona de forma independente como homepage offline; os cards
permanecem informativos até a próxima fase.

---

## Phase 4: User Story 2 - Escolher uma categoria de serviço (Priority: P2)

**Goal**: Transformar cada card em uma entrada acessível para a busca de prestadores, transportando
o ID correto, impedindo navegação duplicada e preservando o retorno à homepage.

**Independent Test**: Selecionar individualmente cada categoria e confirmar o nome correto no
destino “Busca de prestadores”; repetir toques rapidamente e observar somente uma entrada no back
stack; voltar e recuperar a mesma homepage e posição de rolagem.

### Implementation for User Story 2

- [X] T016 [P] [US2] Centralizar `home`, `provider-search/{categoryId}`, construção da rota e validação dos cinco IDs aceitos em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`
- [X] T017 [P] [US2] Implementar o destino mínimo com categoria recebida, erro local para ID inválido e ação Voltar em `app/src/main/java/com/trustarx/pitgo/features/provider/ui/ProviderSearchEntryScreen.kt` e adicionar seus textos em `app/src/main/res/values/strings.xml`
- [X] T018 [P] [US2] Tornar cada card um único alvo Material clicável de pelo menos 48 dp, com feedback e semântica “Buscar prestadores de {categoria}”, emitindo somente `ServiceCategoryId`, em `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt`
- [X] T019 [US2] Criar o NavHost com seleção protegida por `dropUnlessResumed`, `launchSingleTop`, argumento mínimo e back stack padrão, e conectá-lo à Activity em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/MainActivity.kt` após T016–T018
- [ ] T020 [US2] Executar e registrar os cenários Accessibility, Selection and duplicate protection, Back behavior and state restoration e Invalid internal argument de `specs/001-home-service-catalog/quickstart.md`

**Checkpoint**: As duas histórias estão funcionais; a jornada termina intencionalmente na entrada da
busca, sem lista, rede, localização ou dados de prestadores.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Validar integração, limpar resíduos e confirmar requisitos que atravessam as duas
histórias.

- [X] T021 Auditar `app/build.gradle.kts` e `app/src/main/` para confirmar ausência de AppCompat, Views, JNI/CMake, dependências de teste, rede, banco, DI, autenticação, localização, dados de prestadores e código sem uso
- [X] T022 Instalar/confirmar Android SDK Platform 37 e executar `./gradlew clean assembleDebug lintDebug` conforme `specs/001-home-service-catalog/quickstart.md`, corrigindo todos os erros de build, AAR metadata, recursos, Compose e lint bloqueante
- [ ] T023 Executar o roteiro manual completo de `specs/001-home-service-catalog/quickstart.md` em Light/Dark, offline, janela compacta/expandida, fonte ampliada e TalkBack, confirmando todos os critérios de `specs/001-home-service-catalog/spec.md`

**Checkpoint**: Build e lint passam, o roteiro manual está concluído e nenhum item fora do escopo
foi introduzido.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa imediatamente; T002 e T003 podem rodar em paralelo após T001; T004
  depende de T003.
- **Foundational (Phase 2)**: depende do Setup; T005, T006 e T008 podem rodar em paralelo, e T007
  depende de T005/T006.
- **User Story 1 (Phase 3)**: depende da Foundation; T009–T011 podem rodar em paralelo, seguidas por
  T012, T013, T014 e T015.
- **User Story 2 (Phase 4)**: depende da User Story 1 porque torna os cards existentes interativos;
  T016–T018 podem rodar em paralelo, seguidas por T019 e T020.
- **Polish (Phase 5)**: depende das histórias desejadas; executar T021, T022 e T023 nessa ordem.

### User Story Dependencies

```text
Setup → Foundation → US1 (MVP) → US2 → Polish
```

- **User Story 1 (P1)**: não depende de outra história e entrega o catálogo visual offline.
- **User Story 2 (P2)**: reutiliza o catálogo de US1, mas possui critério de teste próprio para
  seleção, categoria, duplicação e retorno.

### Within Each User Story

- Recursos e modelos antes do ViewModel.
- ViewModel antes da coleta/renderização da tela.
- Conteúdo da tela antes da integração no NavHost.
- Rotas, destino e callback antes da integração completa de navegação.
- Implementação antes da validação manual da história.

### Parallel Opportunities

- T002 e T003 após T001.
- T005, T006 e T008 após Setup.
- T009, T010 e T011 após Foundation.
- T016, T017 e T018 após US1.
- Não paralelizar tarefas que alteram `HomeScreen.kt`, `PitGoNavHost.kt` ou `strings.xml` em fases
  diferentes.

---

## Parallel Example: User Story 1

```text
Task T009: Recursos textuais da homepage em app/src/main/res/values/strings.xml
Task T010: Vetores das categorias em app/src/main/res/drawable/ic_service_*.xml
Task T011: Modelo do catálogo em app/src/main/java/com/trustarx/pitgo/features/home/model/ServiceCategory.kt
```

## Parallel Example: User Story 2

```text
Task T016: Rotas centralizadas em app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt
Task T017: Destino de entrada em app/src/main/java/com/trustarx/pitgo/features/provider/ui/ProviderSearchEntryScreen.kt
Task T018: Interação dos cards em app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational.
3. Complete Phase 3: User Story 1.
4. **STOP AND VALIDATE**: execute T015 e confirme o catálogo visual offline.
5. Demonstre a homepage antes de iniciar navegação, se uma entrega parcial for desejada.

### Incremental Delivery

1. Setup + Foundation deixam o projeto pronto para Compose.
2. US1 entrega o catálogo offline navegável visualmente (MVP).
3. US2 adiciona seleção, destino correto, proteção contra duplicação e retorno.
4. Polish confirma build, lint, acessibilidade, responsividade e limites de escopo.

### Parallel Team Strategy

1. A equipe conclui Setup e Foundation respeitando as dependências internas.
2. Em US1, recursos textuais, vetores e modelo podem ser produzidos em paralelo.
3. Após o MVP, rotas, destino mínimo e interação dos cards podem ser produzidos em paralelo.
4. Cada fase integra e valida antes de iniciar a seguinte.

---

## Notes

- Tarefas `[P]` alteram arquivos distintos no mesmo estágio de dependência.
- Rótulos `[US1]` e `[US2]` garantem rastreabilidade com `spec.md`.
- Não criar testes, mocks, fixtures ou utilitários de teste sem nova solicitação explícita.
- Não adicionar Repository, Use Case, Hilt, Retrofit/Ktor, Room, DataStore, Coil ou Window Size
  Classes para esta feature.
- Commits podem agrupar tarefas relacionadas, mas cada commit deve representar uma mudança lógica.
- Parar em qualquer checkpoint mantém uma entrega verificável e dentro do escopo.
