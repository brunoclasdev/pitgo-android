---

description: "Tarefas executáveis para migrar os mapas para MapLibre Native e endurecer o tracking Realtime"
---

# Tasks: Mapa OSM com MapLibre e atualização em tempo real

**Input**: Design documents from `/specs/010-osm-realtime-map/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: A especificação e a constituição não solicitam testes automatizados. As tarefas abaixo incluem somente compilação, lint e validação manual proporcional ao risco.

**Organization**: As tarefas estão agrupadas por história para que descoberta, tracking e recuperação em condições adversas possam ser implementados e validados como incrementos claros.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode executar em paralelo por atuar em arquivo independente e não depender de tarefa incompleta
- **[Story]**: história de usuário correspondente (`US1`, `US2`, `US3`)
- Cada tarefa informa o arquivo exato que deve ser criado ou alterado

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Substituir as dependências e o contrato de ambiente do renderer sem manter Google Maps ou osmdroid.

- [X] T001 Substituir os aliases Maps Compose/Utils por `org.maplibre.gl:android-sdk-opengl:13.5.0` e manter Play Services Location em `gradle/libs.versions.toml`
- [X] T002 Remover `MAPS_API_KEY`, adicionar validação sem vazamento para `mapStyleUrl`, `mapStyleProviderName`, `mapStyleAttribution` e `mapStyleAttributionUrl`, gerar os quatro campos BuildConfig e trocar as dependências do renderer em `app/build.gradle.kts`
- [X] T003 Remover o metadata `com.google.android.geo.API_KEY` e declarar `ACCESS_NETWORK_STATE` sem alterar as permissões de localização existentes em `app/src/main/AndroidManifest.xml`
- [X] T004 [P] Criar os vetores distintos para endereço, endereço selecionado, grupo coincidente, posição ao vivo e posição desatualizada em `app/src/main/res/drawable/ic_map_provider_address.xml`, `app/src/main/res/drawable/ic_map_provider_selected.xml`, `app/src/main/res/drawable/ic_map_provider_group.xml`, `app/src/main/res/drawable/ic_map_live.xml` e `app/src/main/res/drawable/ic_map_stale.xml`
- [X] T005 [P] Adicionar textos localizados de atribuição, controles, resumo, estados de estilo/recursos, offline, localização recusada, live/stale/ended e acessibilidade em `app/src/main/res/values/strings.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Criar a fronteira MapLibre compartilhada e os tipos de superfície exigidos por todas as histórias.

**⚠️ CRITICAL**: Nenhuma história deve integrar o mapa antes de esta fase estar concluída.

- [X] T006 Criar a leitura central e imutável dos quatro campos BuildConfig do estilo gerenciado, sem expor token ou URL em logs, em `app/src/main/java/com/trustarx/pitgo/core/map/config/ManagedMapStyleConfiguration.kt`
- [X] T007 [P] Criar `MapCoordinate`, `MapViewport`, comandos de câmera revisionados, `MapMarkerSpec`, `MapFeatureSelection`, `MapStyleState`, `MapResourceState` e `MapSurfaceState` em `app/src/main/java/com/trustarx/pitgo/core/map/model/MapSurfaceModels.kt`
- [X] T008 [P] Implementar observação lifecycle-aware de conectividade com estado `Online|Offline`, sem reter Activity, em `app/src/main/java/com/trustarx/pitgo/core/network/ConnectivityObserver.kt`
- [X] T009 Inicializar `MapLibre.getInstance(applicationContext)` uma única vez e disponibilizar o observador compartilhado pelo container manual em `app/src/main/java/com/trustarx/pitgo/PitGoApplication.kt` e `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`
- [X] T010 Implementar `MapLibreMapHost` com um único `MapView` por composição, lifecycle completo, cleanup idempotente, estilo assíncrono revisionado, câmera não repetida e atualização pelo modelo mais recente em `app/src/main/java/com/trustarx/pitgo/core/map/ui/MapLibreMapHost.kt`

**Checkpoint**: MapLibre está configurado, inicializado e encapsulado sem dependência do renderer em ViewModels ou Repositories.

---

## Phase 3: User Story 1 - Visualizar prestadores pelo endereço (Priority: P1) 🎯 MVP

**Goal**: Abrir um serviço, mostrar todos os prestadores elegíveis nas coordenadas do endereço empresarial, selecionar qualquer resultado e seguir para “Solicitar atendimento”.

**Independent Test**: Com uma sessão cliente e um serviço válido, abrir o mapa, confirmar endereços FIXED/MOBILE, clusters e pontos coincidentes, selecionar o mesmo provider no mapa e na lista e navegar com `serviceId + providerId` para o resumo de solicitação.

### Implementation for User Story 1

- [X] T011 [P] [US1] Substituir os booleans atuais por viewport, coleção pública validada, modo Mapa/Lista, seleção e grupos coincidentes no estado imutável em `app/src/main/java/com/trustarx/pitgo/features/providermap/model/ServiceMapModels.kt`
- [X] T012 [P] [US1] Validar UUID/centro e descartar coordenadas não finitas ou fora da faixa ao mapear o RPC `discover_service_providers`, preservando limite e privacidade dos campos públicos em `app/src/main/java/com/trustarx/pitgo/features/providermap/data/ServiceMapDtos.kt` e `app/src/main/java/com/trustarx/pitgo/features/providermap/repository/ServiceMapRepository.kt`
- [X] T013 [US1] Implementar busca por viewport com generation token, seleção estável, pré-agrupamento de coordenadas idênticas e comandos de câmera revisionados em `app/src/main/java/com/trustarx/pitgo/features/providermap/viewmodel/ServiceMapViewModel.kt`
- [X] T014 [US1] Criar controller idempotente que instala imagens, source GeoJSON clusterizada e layers de cluster/contagem/endereço/seleção, atualizando somente a source no mesmo estilo em `app/src/main/java/com/trustarx/pitgo/features/providermap/ui/ProviderMapStyleController.kt`
- [X] T015 [US1] Substituir `GoogleMap`/`Clustering` pelo `MapLibreMapHost`, adicionar TopAppBar, Mapa/Lista, controles externos, busca nesta área, atribuição, seleção acessível, sheet/painel responsivo e CTA em `app/src/main/java/com/trustarx/pitgo/features/providermap/ui/ServiceMapScreen.kt`
- [X] T016 [P] [US1] Adicionar resolução autoritativa de serviço por UUID e sua estimativa sem recorrer ao catálogo demo em `app/src/main/java/com/trustarx/pitgo/core/catalog/repository/ServiceCatalogRepository.kt` e `app/src/main/java/com/trustarx/pitgo/core/catalog/model/ServiceCatalogModels.kt`
- [X] T017 [US1] Trocar o estado de solicitação de `DemoService` para resumo real do catálogo e preservar `providerId` opaco no callback mockado em `app/src/main/java/com/trustarx/pitgo/features/requestservice/model/RequestServiceContent.kt`
- [X] T018 [US1] Carregar o serviço por `serviceId`, representar loading/falha/inválido e impedir fallback para Bateria ou preço demo em `app/src/main/java/com/trustarx/pitgo/features/requestservice/viewmodel/RequestServiceViewModel.kt`
- [X] T019 [US1] Adaptar a Route/tela de solicitação aos novos estados e manter descrição, pagamento e botão mockado funcionais sem expor IDs na interface em `app/src/main/java/com/trustarx/pitgo/features/requestservice/ui/RequestServiceScreen.kt`
- [X] T020 [US1] Centralizar `request-service?serviceId={serviceId}&providerId={providerId}`, conectar voltar/seleção/CTA/factory do catálogo e manter somente IDs URI-encoded em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`

**Checkpoint**: A descoberta funciona integralmente com MapLibre e a lista permanece uma alternativa completa ao mapa.

---

## Phase 4: User Story 2 - Acompanhar posição atualizada (Priority: P1)

**Goal**: Mostrar somente ao participante a posição móvel mais recente, rejeitar regressões, manter stale após 30 segundos e encerrar deterministicamente o stream.

**Independent Test**: Em atendimento ativo, receber posições sucessivas e fora de ordem; confirmar movimento sem recriar mapa, tombstone sem ressurreição, stale com horário, reconexão com snapshot e remoção imediata ao concluir/cancelar ou perder sessão.

### Implementation for User Story 2

- [X] T021 [P] [US2] Criar hardening SQL para snapshot `EMPTY|LIVE|STALE|ENDED`, autorização participante independente do estado terminal, Broadcast privado `ENDED` e remoção da posição corrente sem histórico em `supabase/migrations/20260821030000_appointment_location_stream_hardening.sql`
- [X] T022 [P] [US2] Modelar `LocationRevision(publishedAt, version)`, eventos `Snapshot|Upsert|Removed|Ended|SessionExpired|Offline|Failure`, tombstone e estados de tracking com última posição em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/model/AppointmentTrackingModels.kt`
- [X] T023 [US2] Iniciar collectors antes do snapshot, emitir `Snapshot(null)`, interpretar `old` no DELETE, coletar ENDED, classificar sessão/rede e remover canal em `NonCancellable` em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/repository/AppointmentTrackingRepository.kt`
- [X] T024 [US2] Aplicar ordenação lexicográfica e tombstone, relançar `CancellationException`, manter ticker de 30 segundos e limpar coordenada privada em hidden/Ended/session change em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`
- [X] T025 [US2] Criar controller de source GeoJSON não clusterizada e layers LIVE/STALE que atualiza apenas `setGeoJson` e nunca restaura marker removido após reload tardio em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentMapStyleController.kt`
- [X] T026 [US2] Substituir Google Maps pelo host MapLibre, adicionar TopAppBar/voltar, controle “Seguir prestador”, horário localizado e estados Live/Stale/Empty/Ended sem bottom navigation em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`
- [X] T027 [US2] Conectar `onBack` e `onSessionExpired` da rota participante, mantendo `appointmentId` como único argumento e o gate central de sessão em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`

**Checkpoint**: O tracking privado é ordenado, lifecycle-aware, não cria histórico e não deixa coordenada visível após término ou perda de autorização.

---

## Phase 5: User Story 3 - Usar o mapa em condições adversas (Priority: P2)

**Goal**: Preservar dados e ações acessíveis diante de estilo inválido, recurso degradado, offline, localização negada, lista vazia ou sessão expirada.

**Independent Test**: Simular style inválido/401, tiles 429, sprite/glyph indisponível, modo avião, permissão recusada e fonte 200%; confirmar banners acionáveis, retry no mesmo MapView, lista funcional e nenhuma perda ou ressurreição de posição privada.

### Implementation for User Story 3

- [X] T028 [US3] Agregar callbacks de style/tile/glyph/sprite/render, ignorar cancelamento ou erro isolado, aplicar debounce de degradação e recarregar o mesmo estilo sem limpar cache/dados/câmera em `app/src/main/java/com/trustarx/pitgo/core/map/ui/MapLibreMapHost.kt`
- [X] T029 [P] [US3] Classificar offline, timeout, sessão expirada, serviço indisponível e falha desconhecida sem encaminhar Throwable bruto em `app/src/main/java/com/trustarx/pitgo/features/providermap/repository/ServiceMapRepository.kt`
- [X] T030 [US3] Integrar conectividade, estados de dados/superfície/localização, retry revisionado e preservação de resultados/seleção em falha no `StateFlow` em `app/src/main/java/com/trustarx/pitgo/features/providermap/viewmodel/ServiceMapViewModel.kt`
- [X] T031 [US3] Renderizar loading, vazio, offline, falha do RPC, style failed, resource degraded, localização recusada e sessão expirada com live regions, retry e lista equivalente em 320dp/paisagem/fonte 200% em `app/src/main/java/com/trustarx/pitgo/features/providermap/ui/ServiceMapScreen.kt`
- [X] T032 [P] [US3] Preservar última posição como stale em offline/falha visual, separar `MapSurfaceState` do stream e impedir anúncio repetitivo a cada tick em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`
- [X] T033 [US3] Manter marker e horário em degradação, oferecer retry/listagem textual de estado e garantir atribuição/controles acessíveis com fonte 200% em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`

**Checkpoint**: Falhas do renderer, recursos, rede e permissão não bloqueiam informações essenciais nem apagam estado autorizado válido.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Remover resíduos, validar segurança/configuração e executar o quickstart completo.

- [X] T034 Remover resíduos de Maps Compose, `MAPS_API_KEY`, `play-services-maps` e implementação osmdroid, preservando somente Play Services Location, em `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/trustarx/pitgo/features/providermap/ui/ServiceMapScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`
- [X] T035 [P] Revisar mensagens, logs e callbacks para não registrar URL/token, payload Realtime, coordenada privada ou erro nativo bruto em `app/src/main/java/com/trustarx/pitgo/core/map/ui/MapLibreMapHost.kt`, `app/src/main/java/com/trustarx/pitgo/features/providermap/repository/ServiceMapRepository.kt` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/repository/AppointmentTrackingRepository.kt`
- [X] T036 Executar `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleRelease` e `dependencyInsight` para confirmar MapLibre OpenGL 13.5.0 como único renderer conforme `specs/010-osm-realtime-map/quickstart.md`
- [ ] T037 [P] Executar `supabase db reset` e `supabase db lint`, validando participante, EMPTY, ENDED, tombstone e ausência de histórico conforme `specs/010-osm-realtime-map/quickstart.md`
- [ ] T038 Executar a matriz manual de descoberta, clusters coincidentes, tracking fora de ordem, stale, término, estilo/tiles indisponíveis, offline, permissão recusada, lifecycle, TalkBack, 320dp, paisagem, fonte 200% e APIs 24/36/37 descrita em `specs/010-osm-realtime-map/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: inicia imediatamente.
- **Foundational (Phase 2)**: depende de T001–T005 e bloqueia todas as histórias.
- **US1 (Phase 3)**: depende da fundação; é o MVP e estabelece a descoberta e o contrato de navegação por IDs.
- **US2 (Phase 4)**: depende da fundação; pode começar em paralelo com US1, exceto T027, que deve ser aplicado após T020 por ambos alterarem `PitGoNavHost.kt`.
- **US3 (Phase 5)**: depende dos caminhos felizes de US1 e US2 porque endurece os mesmos estados e telas.
- **Polish (Phase 6)**: depende das histórias selecionadas para entrega; validação completa depende de US1–US3.

### User Story Dependencies

```text
Setup -> Foundation -> US1 (MVP) --------┐
                    \-> US2 -------------+-> US3 -> Polish
```

- **US1** não depende de US2 e pode ser demonstrada sem um atendimento ativo.
- **US2** não depende da descoberta para seu domínio, mas compartilha o host MapLibre e integra navegação depois de US1.
- **US3** é deliberadamente incremental: aplica recuperação sobre as telas funcionais de US1/US2.

### Within Each User Story

- Modelos/contratos antes de ViewModels e controllers.
- Repositories antes dos ViewModels que consomem seus resultados.
- ViewModels/controllers antes das telas.
- Rotas somente depois que destino e factory estiverem prontos.
- Nenhum trabalho de rede ou regra de ordenação entra em Composable/controller MapLibre.

### Parallel Opportunities

- T004 e T005 podem executar em paralelo com a configuração Gradle/Manifest.
- T007 e T008 podem executar em paralelo depois da definição dos nomes de pacote.
- T011, T012 e T016 podem iniciar em paralelo após a fundação.
- T021 e T022 podem iniciar em paralelo; a migration e os modelos Kotlin têm contratos documentados independentes.
- T029 e T032 podem executar em paralelo por atuarem em histórias/telas distintas.
- T035 e T037 podem executar em paralelo depois da implementação funcional.

---

## Parallel Example: User Story 1

```text
Task T011: modelar estado/viewport/seleção em features/providermap/model/ServiceMapModels.kt
Task T012: validar/marcar resultados RPC em features/providermap/data e repository
Task T016: resolver serviço autoritativo em core/catalog
```

## Parallel Example: User Story 2

```text
Task T021: criar a migration de hardening do stream
Task T022: criar revisão/eventos/estados Kotlin do tracking
```

## Parallel Example: User Story 3

```text
Task T029: classificar falhas de descoberta no Repository
Task T032: preservar tracking stale e surface state no ViewModel
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Concluir Setup e Foundational.
2. Implementar T011–T020.
3. Validar descoberta MapLibre, lista, clusters, seleção e CTA por IDs.
4. Parar e demonstrar o MVP antes de habilitar o tracking privado.

### Incremental Delivery

1. **Foundation**: MapLibre OpenGL configurado e host compartilhado compilando.
2. **US1**: descoberta pública e solicitação por IDs.
3. **US2**: tracking privado ordenado e terminal.
4. **US3**: estados adversos, retry e acessibilidade completa.
5. **Polish**: remoção de resíduos, segurança e matriz manual.

### Parallel Team Strategy

1. Equipe conclui T001–T010 em conjunto.
2. Após a fundação, uma frente executa US1 e outra executa T021–T026 de US2.
3. Integrar `PitGoNavHost.kt` sequencialmente em T020 e T027 para evitar conflito.
4. Aplicar US3 sobre os dois fluxos estabilizados e finalizar com o quickstart.

---

## Notes

- `[P]` identifica arquivos independentes, não autorização para editar o mesmo arquivo simultaneamente.
- Não adicionar MapLibre Compose, osmdroid, OfflineRegion, histórico ou fallback público.
- Não mover autorização, ordenação ou classificação de falha para a camada de renderização.
- Commits devem ser pequenos e descritivos conforme a constituição.
