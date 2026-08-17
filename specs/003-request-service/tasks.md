---

description: "Dependency-ordered implementation tasks for Solicitar Atendimento"
---

# Tasks: Solicitar Atendimento

**Input**: Design documents from `/specs/003-request-service/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [request-service-navigation.md](contracts/request-service-navigation.md)
and [quickstart.md](quickstart.md)

**Tests**: Não criar testes automatizados. A feature exige build, lint e validação manual pelo
`quickstart.md`.

**Organization**: As tarefas são agrupadas por história de usuário. Setup e Foundation preparam
recursos compartilhados; cada fase posterior entrega um incremento observável e validável.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode ser executada em paralelo com outras tarefas marcadas na mesma fase porque altera
  arquivos diferentes e não depende do resultado incompleto delas.
- **[Story]**: associa a tarefa a US1, US2, US3 ou US4 da especificação.
- Todas as descrições indicam os arquivos exatos afetados ou o roteiro exato usado na validação.

## Phase 1: Setup (Shared Resources)

**Purpose**: Preparar textos e vetores locais necessários à tela sem adicionar dependências.

- [X] T001 Adicionar título, labels, mocks, opções de pagamento, CTA, aviso, erro e descrições acessíveis de RequestService em app/src/main/res/values/strings.xml
- [X] T002 [P] Criar os vetores locais de voltar, check selecionado e relógio em app/src/main/res/drawable/ic_request_back.xml, app/src/main/res/drawable/ic_request_check.xml e app/src/main/res/drawable/ic_request_clock.xml

**Checkpoint**: todos os recursos visuais e textuais da nova feature estão disponíveis localmente.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Estabelecer as fontes compartilhadas, os tipos de estado e o contrato de rota que
bloqueiam todas as histórias.

**⚠️ CRITICAL**: nenhuma história deve começar antes desta fase terminar.

- [X] T003 Extrair DemoServiceId, DemoService, DemoServices, DemoVehicle e DemoHomeVehicle para app/src/main/java/com/trustarx/pitgo/core/common/model/DemoServiceCatalog.kt e app/src/main/java/com/trustarx/pitgo/core/common/model/DemoVehicle.kt, removê-los de app/src/main/java/com/trustarx/pitgo/features/home/model/HomeContent.kt e atualizar imports em app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt e app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt
- [X] T004 [P] Extrair BottomItemId, BottomNavigationItem, DemoBottomNavigation e HomeBottomNavigation para app/src/main/java/com/trustarx/pitgo/core/ui/PitGoBottomNavigation.kt e atualizar o uso em app/src/main/java/com/trustarx/pitgo/features/home/model/HomeContent.kt, app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt e app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt
- [X] T005 [P] Definir PaymentMethod, SubmissionStatus e o tipo selado RequestServiceUiState com Content e InvalidService em app/src/main/java/com/trustarx/pitgo/features/requestservice/model/RequestServiceContent.kt
- [X] T006 [P] Centralizar RequestServiceArg, o padrão opcional request-service e o builder que recebe somente DemoServiceId em app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt

**Checkpoint**: Home compila usando as fontes compartilhadas, e a nova feature possui tipos e rota
estáveis sem Repository, persistência ou serialização adicional.

---

## Phase 3: User Story 1 - Revisar o atendimento selecionado (Priority: P1) 🎯 MVP

**Goal**: Abrir RequestService a partir de qualquer um dos seis cards e apresentar o serviço,
veículo, local, chegada e preço corretos em conteúdo rolável, com erro seguro para ID inválido.

**Independent Test**: Selecionar cada serviço na Home e conferir título, nome, ícone e preço
correspondentes, veículo/endereço/chegada mockados, rolagem acima da barra fixa e ausência de
fallback quando o ID estiver ausente ou inválido.

### Implementation for User Story 1

- [X] T007 [P] [US1] Implementar RequestServiceViewModel com SavedStateHandle, lookup exato do serviceId, estado Content imediato, preço/endereço derivados e InvalidService sem fallback em app/src/main/java/com/trustarx/pitgo/features/requestservice/viewmodel/RequestServiceViewModel.kt
- [X] T008 [P] [US1] Implementar o shell stateless com Scaffold, TopAppBar, LazyColumn, bottom navigation fixa, InfoCard, EstimateCard, conteúdo válido responsivo e estado InvalidService em app/src/main/java/com/trustarx/pitgo/features/requestservice/ui/RequestServiceScreen.kt
- [X] T009 [US1] Implementar RequestServiceRoute com criação/coleta lifecycle-aware do ViewModel e encaminhamento de estado/callbacks em app/src/main/java/com/trustarx/pitgo/features/requestservice/ui/RequestServiceScreen.kt
- [X] T010 [US1] Propagar onServiceClick em HomeRoute e registrar o destino RequestService com argumento opcional, guarda de navegação e launchSingleTop em app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt e app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt
- [X] T011 [US1] Executar e confirmar os cenários manuais 1, 2 e a inspeção de ID inválido do cenário 6 em specs/003-request-service/quickstart.md

**Checkpoint**: o MVP Home → RequestService funciona para os seis serviços, apresenta o contexto
correto e não mostra preço incorreto diante de entrada inválida.

---

## Phase 4: User Story 2 - Informar o problema e o pagamento (Priority: P2)

**Goal**: Permitir descrição multiline opcional e seleção exclusiva entre PIX, Crédito e Débito.

**Independent Test**: Digitar, editar e apagar várias linhas com o teclado aberto e alternar
rapidamente as três opções, confirmando que o texto permanece no estado e exatamente uma forma de
pagamento fica selecionada, iniciando em PIX.

### Implementation for User Story 2

- [X] T012 [P] [US2] Implementar onProblemDescriptionChanged e onPaymentMethodSelected com atualização imutável apenas do estado Content em app/src/main/java/com/trustarx/pitgo/features/requestservice/viewmodel/RequestServiceViewModel.kt
- [X] T013 [P] [US2] Adicionar OutlinedTextField multiline com IME seguro e PaymentOption em selectableGroup com papel de radio, check e estilos selecionado/não selecionado em app/src/main/java/com/trustarx/pitgo/features/requestservice/ui/RequestServiceScreen.kt
- [X] T014 [US2] Executar e confirmar os cenários manuais 3 e 4 em specs/003-request-service/quickstart.md

**Checkpoint**: descrição e pagamento funcionam sem persistência, sem validação indevida do campo
opcional e sem estado de seleção múltipla.

---

## Phase 5: User Story 3 - Acionar a solicitação simulada (Priority: P3)

**Goal**: Executar o callback mock uma única vez por instância, com feedback imediato, CTA
gradiente e aviso, sem criar atendimento ou integração.

**Independent Test**: Tocar repetidamente em “SOLICITAR PITTECH”, confirmar uma única execução do
callback, mudança `READY → TRIGGERED`, botão desabilitado e ausência de rede, cobrança, localização
ou confirmação real.

### Implementation for User Story 3

- [X] T015 [P] [US3] Implementar onSubmitClicked com guarda síncrona READY → TRIGGERED e retorno de aceitação única em app/src/main/java/com/trustarx/pitgo/features/requestservice/viewmodel/RequestServiceViewModel.kt
- [X] T016 [P] [US3] Adicionar Button Material em largura máxima com gradiente BrandPurple → DarkPurple, altura mínima de 56 dp, estado disabled após acionamento, callback mock e aviso centralizado em app/src/main/java/com/trustarx/pitgo/features/requestservice/ui/RequestServiceScreen.kt
- [X] T017 [US3] Executar e confirmar o cenário manual 5 e as verificações de ausência de integrações do cenário 10 em specs/003-request-service/quickstart.md

**Checkpoint**: a jornada visual termina em um único callback local e não cria entidade, efeito ou
dependência fora do escopo.

---

## Phase 6: User Story 4 - Sair ou mudar de área (Priority: P4)

**Goal**: Oferecer retorno seguro à mesma Home e preservar a bottom navigation demonstrativa,
fixa, acessível e com Início selecionado.

**Independent Test**: Usar seta e Back do sistema, inclusive no estado inválido, e acionar os cinco
itens inferiores, confirmando retorno à Home quando solicitado e permanência/feedback sem mudança
de seleção para os itens demonstrativos.

### Implementation for User Story 4

- [X] T018 [P] [US4] Conectar ação de voltar da TopAppBar, retorno do estado InvalidService e callbacks no-op acessíveis dos itens inferiores em app/src/main/java/com/trustarx/pitgo/features/requestservice/ui/RequestServiceScreen.kt
- [X] T019 [P] [US4] Implementar popBackStack com fallback seguro para Home e atualizar o contraste das barras do sistema para a rota RequestService em app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt
- [X] T020 [US4] Executar e confirmar os cenários manuais 6 e 7 em specs/003-request-service/quickstart.md

**Checkpoint**: nenhuma ação aprisiona o usuário, duplica destinos ou cria telas para Serviços,
Garagem, Atendimentos e Perfil.

---

## Phase 7: Polish & Cross-Cutting Validation

**Purpose**: Verificar responsividade, acessibilidade, qualidade do código e conformidade de escopo
em toda a feature.

- [X] T021 Revisar fonte ampliada, portrait/landscape, IME, temas Light/Dark, headings, ordem semântica, alvos de 48 dp e ausência de informação dependente somente de cor em app/src/main/java/com/trustarx/pitgo/features/requestservice/ui/RequestServiceScreen.kt e app/src/main/res/values/strings.xml
- [X] T022 Executar ./gradlew clean assembleDebug lintDebug e conferir os resultados esperados da seção Build and static validation em specs/003-request-service/quickstart.md
- [X] T023 Executar integralmente os cenários manuais 1–10, confirmar operação offline e ausência de backend, persistência, permissões, cobrança, destinos placeholder e arquivos de teste conforme specs/003-request-service/quickstart.md

**Checkpoint**: build e lint passam, o fluxo completo atende os quatro incrementos e nenhum item
fora do escopo foi introduzido.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências; T001 e T002 podem ser executadas em paralelo.
- **Foundational (Phase 2)**: depende do Setup e bloqueia todas as histórias. T003 vem primeiro;
  depois dela, T004, T005 e T006 podem avançar em paralelo porque alteram conjuntos distintos de
  arquivos usando os modelos já extraídos.
- **US1 (Phase 3)**: depende de toda a Foundation. T007 e T008 podem avançar em paralelo; T009
  integra ambos; T010 integra Home/NavHost; T011 valida o incremento.
- **US2 (Phase 4)**: depende da base visual/estado de US1. T012 e T013 podem avançar em paralelo;
  T014 valida ambos.
- **US3 (Phase 5)**: depende da base de US1, mas não depende funcionalmente de US2. T015 e T016
  podem avançar em paralelo; T017 valida ambos.
- **US4 (Phase 6)**: depende da tela/destino de US1, mas não depende funcionalmente de US2 ou US3.
  T018 e T019 podem avançar em paralelo; T020 valida ambos.
- **Polish (Phase 7)**: depende de todas as histórias escolhidas para a entrega.

### User Story Dependency Graph

```text
Setup → Foundation → US1 (MVP)
                         ├──→ US2
                         ├──→ US3
                         └──→ US4
US2 + US3 + US4 → Polish
```

US2, US3 e US4 são funcionalmente independentes após US1. Como todos alteram
`RequestServiceScreen.kt` e alguns alteram `RequestServiceViewModel.kt`, execução simultânea por
pessoas diferentes exige coordenação de merge; a sequência P2 → P3 → P4 minimiza conflitos.

### Within Each User Story

- Tipos e contratos compartilhados precedem ViewModel e UI.
- ViewModel e UI marcados [P] podem ser implementados em paralelo usando o contrato de
  `data-model.md`.
- A integração Route/NavHost ocorre após estado e tela compilarem.
- A validação manual da história ocorre somente após suas tarefas de implementação.
- Nenhum teste unitário, de UI ou integração deve ser criado.

## Parallel Execution Examples

### Foundation

```text
Após T003:
T004: extrair a bottom navigation e atualizar a Home
T005: definir PaymentMethod, SubmissionStatus e RequestServiceUiState
T006: centralizar o padrão e o builder de rota
```

### User Story 1

```text
T007: implementar resolução/estado no RequestServiceViewModel.kt
T008: implementar shell/cards/estimativas no RequestServiceScreen.kt
```

### User Story 2

```text
T012: implementar eventos de descrição/pagamento no RequestServiceViewModel.kt
T013: implementar campo e grupo de pagamento no RequestServiceScreen.kt
```

### User Story 3

```text
T015: implementar guarda de acionamento no RequestServiceViewModel.kt
T016: implementar CTA gradiente e aviso no RequestServiceScreen.kt
```

### User Story 4

```text
T018: implementar ações de retorno/barra na RequestServiceScreen.kt
T019: implementar back/fallback/barras do sistema no PitGoNavHost.kt
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Concluir Phase 1: recursos locais.
2. Concluir Phase 2: fontes, modelos, barra e rota compartilhados.
3. Concluir Phase 3: US1.
4. Parar e validar T011 para os seis serviços e entradas inválidas.
5. Demonstrar Home → RequestService com resumo correto e conteúdo rolável.

### Incremental Delivery

1. Setup + Foundation → base compilável e compartilhada.
2. US1 → navegação e resumo do atendimento (MVP).
3. US2 → descrição e pagamento exclusivo.
4. US3 → CTA mock protegido e aviso.
5. US4 → retorno e navegação inferior completa no comportamento demonstrativo.
6. Polish → build, lint, acessibilidade, responsividade e escopo offline.

### Suggested Commit Boundaries

1. `refactor: share service and navigation demo models`
2. `feat: add request service summary flow`
3. `feat: add request details and payment selection`
4. `feat: add guarded mock service request action`
5. `feat: finish request service navigation and accessibility`
6. `chore: validate request service flow`

## Notes

- Tasks [P] alteram arquivos diferentes dentro do mesmo ponto de partida estável.
- Labels [US1]–[US4] mapeiam diretamente para as histórias da especificação.
- Validações manuais são tarefas de aceite, não testes automatizados.
- Nenhuma tarefa adiciona Repository, Use Case, DI, backend, persistência, localização ou cobrança.
- Interromper em qualquer checkpoint mantém um incremento demonstrável; o MVP termina em T011.
