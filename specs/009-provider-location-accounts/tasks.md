---

description: "Tarefas da feature de contas independentes e localização de prestadores"
---

# Tasks: Contas independentes e localização de prestadores

**Input**: Design documents from `/specs/009-provider-location-accounts/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Nenhuma tarefa de teste automatizado foi incluída, conforme a constituição e o escopo.
A validação executável/manual está concentrada na fase final e em `quickstart.md`.

**Organization**: As tarefas estão agrupadas por história para permitir implementação e validação
incrementais. US1 e US2 são ambas P1; juntas formam o fluxo empresarial completo.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode executar em paralelo porque trabalha em arquivos distintos sem depender de tarefa incompleta
- **[Story]**: história atendida (`US1` a `US5`)
- Toda tarefa inclui o caminho exato do arquivo principal

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configurar as dependências e declarações compartilhadas exigidas por mapas,
geocodificação, localização e Realtime.

- [X] T001 Adicionar aliases/versionamento de Maps Compose 8.4.0, Maps Utils 8.4.0, Play Services Location 21.4.0, Supabase Functions/Realtime 3.2.6 e Ktor OkHttp 3.3.1, removendo o engine Ktor Android, em `gradle/libs.versions.toml`
- [X] T002 Atualizar a validação segura de propriedades para exigir `MAPS_API_KEY`, injetar somente o manifest placeholder e declarar as novas dependências em `app/build.gradle.kts`
- [X] T003 [P] Declarar COARSE/FINE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION, POST_NOTIFICATIONS, metadata da Maps API e o service não exportado de localização em `app/src/main/AndroidManifest.xml`
- [X] T004 [P] Instalar `Functions` e `Realtime` junto de Auth/Postgrest no cliente único em `app/src/main/java/com/trustarx/pitgo/core/network/SupabaseClientProvider.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Corrigir o modelo de identidade/sessão e criar a primitiva Android de localização que
todos os fluxos protegidos usarão.

**⚠️ CRITICAL**: Nenhuma história deve ser integrada antes desta fase; a sessão atual exige Profile
para qualquer Auth user e não consegue autorizar uma empresa independente.

- [X] T005 Criar a migration reconciliável de `account_roles`, backfill CLIENT, desacoplamento `providers.id`/`providers.account_id`, preflights, RLS/grants e `get_my_account_context()` em `supabase/migrations/20260821000000_account_roles_provider_ownership.sql`
- [X] T006 Substituir o trigger geral de Profile pelo Auth Hook/trigger CLIENT-versus-PROVIDER e registrar o Before User Created Hook em `supabase/config.toml`
- [X] T007 [P] Criar os tipos fechados de papel, contexto CLIENT/PROVIDER e snapshots de sessão em `app/src/main/java/com/trustarx/pitgo/core/account/model/AccountContext.kt` e DTOs em `app/src/main/java/com/trustarx/pitgo/core/account/data/AccountContextDtos.kt`
- [X] T008 Implementar a leitura sanitizada de `get_my_account_context()` e o carregamento específico de Profile ou Provider em `app/src/main/java/com/trustarx/pitgo/core/account/repository/AccountContextRepository.kt`
- [X] T009 Remodelar `AppSessionState` e o bootstrap/login/refresh para `Authenticated.Client`, `Authenticated.Provider.Onboarding` e `Authenticated.Provider.Ready`, bloqueando contexto ausente/ambíguo, em `app/src/main/java/com/trustarx/pitgo/core/auth/model/AppSessionState.kt` e `app/src/main/java/com/trustarx/pitgo/core/auth/repository/SessionRepository.kt`
- [X] T010 Trocar `AccountRepository` pelo contexto autoritativo na composição manual e preservar onboarding/vehicle apenas para CLIENT em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`
- [X] T011 [P] Criar modelos de permissão, settings, qualidade e fix corrente sem tipos de UI em `app/src/main/java/com/trustarx/pitgo/core/location/model/DeviceLocationModels.kt`
- [X] T012 Implementar fix preciso one-shot, Flow de updates, cancelamento e limpeza sem persistência usando FLP em `app/src/main/java/com/trustarx/pitgo/core/location/data/FusedLocationDataSource.kt` e `app/src/main/java/com/trustarx/pitgo/core/location/repository/DeviceLocationRepository.kt`

**Checkpoint**: sessão classificada por papel e localização de dispositivo disponível sem histórico.

---

## Phase 3: User Story 1 - Cadastrar e entrar como empresa (Priority: P1) 🎯 MVP

**Goal**: O CTA empresarial cria uma identidade PROVIDER independente, o login escolhe a área pelo
papel autoritativo e providers legados podem transferir ownership sem converter/apagar o Profile.

**Independent Test**: Criar uma conta pelo CTA empresarial e confirmar estado Provider.Onboarding
sem Profile; criar cliente e confirmar somente CLIENT/Profile; logar ambos e observar destinos
distintos; transferir um provider legado completo para uma nova conta provider e preservar o Profile.

### Implementation for User Story 1

- [X] T013 [US1] Fazer o signup comum enviar `account_type=CLIENT` e rejeitar contexto resultante diferente de CLIENT em `app/src/main/java/com/trustarx/pitgo/features/signup/repository/SignUpRepository.kt`
- [X] T014 [P] [US1] Criar draft, validação, estados e falhas do cadastro de credenciais empresariais em `app/src/main/java/com/trustarx/pitgo/features/providersignup/model/ProviderSignUpModels.kt`
- [X] T015 [US1] Implementar signup Auth com metadata mínima `account_type=PROVIDER`, sessão imediata e erros sanitizados em `app/src/main/java/com/trustarx/pitgo/features/providersignup/repository/ProviderSignUpRepository.kt`
- [X] T016 [US1] Implementar StateFlow, validação, bloqueio de duplo submit e limpeza de senha no ViewModel em `app/src/main/java/com/trustarx/pitgo/features/providersignup/viewmodel/ProviderSignUpViewModel.kt`
- [X] T017 [US1] Criar a tela pública acessível de e-mail/senha/confirmar senha, descarte e feedback em `app/src/main/java/com/trustarx/pitgo/features/providersignup/ui/ProviderSignUpScreen.kt`
- [X] T018 [P] [US1] Criar snapshot, falhas e Repository mínimo da área empresarial pronta/onboarding em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/model/ProviderDashboardModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/repository/ProviderDashboardRepository.kt`
- [X] T019 [US1] Implementar o estado da área empresarial e logout coerente em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/viewmodel/ProviderDashboardViewModel.kt`
- [X] T020 [US1] Criar o dashboard Material responsivo com resumo da empresa, modalidade, disponibilidade e logout em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/ui/ProviderDashboardScreen.kt`
- [X] T021 [US1] Adicionar `ProviderSignUp`, `ProviderRegistration` e `ProviderDashboard`, ligar diretamente o CTA da Welcome e aplicar a matriz de destinos por papel em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`
- [X] T022 [P] [US1] Adicionar textos de cadastro empresarial, contexto bloqueado, área empresarial e erros neutros em `app/src/main/res/values/strings.xml`
- [X] T023 [P] [US1] Adicionar tabela privada de transferências e RPCs idempotentes `begin_provider_ownership_migration`/`claim_provider_ownership_migration` à migration `supabase/migrations/20260821000000_account_roles_provider_ownership.sql`
- [X] T024 [P] [US1] Criar modelos de desafio, requisitos, estados e um coordinator process-scoped sem persistência do token em `app/src/main/java/com/trustarx/pitgo/features/providermigration/model/ProviderMigrationModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/providermigration/repository/ProviderMigrationCoordinator.kt`
- [X] T025 [US1] Implementar begin/claim, timeout, idempotência e classificação sanitizada das RPCs em `app/src/main/java/com/trustarx/pitgo/features/providermigration/repository/ProviderMigrationRepository.kt`
- [X] T026 [US1] Implementar as transições entre prova CLIENT, troca intencional de sessão e claim PROVIDER em `app/src/main/java/com/trustarx/pitgo/features/providermigration/viewmodel/ProviderMigrationViewModel.kt`
- [X] T027 [US1] Criar telas acessíveis de início, instrução de troca de conta, requisitos e confirmação da migração em `app/src/main/java/com/trustarx/pitgo/features/providermigration/ui/ProviderMigrationScreen.kt`
- [X] T028 [US1] Exibir a ação de migração somente para CLIENT com flag legada e preservar o Profile em `app/src/main/java/com/trustarx/pitgo/features/profile/ui/ProfileScreen.kt`
- [X] T029 [US1] Integrar a pilha Profile→migração→logout→Provider SignUp/Login→claim, retirar `SignUpOrigin.PROVIDER` e nunca colocar token em rota/SavedState em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`

**Checkpoint**: contas e áreas são independentes; provider legado muda de owner sem mudar de ID.

---

## Phase 4: User Story 2 - Cadastrar empresa, categorias e endereço localizado (Priority: P1)

**Goal**: Uma conta Provider.Onboarding salva empresa, modalidade, categorias/serviços e um endereço
geocodificado/verificado como um agregado atômico e retomável.

**Independent Test**: Partindo de uma conta PROVIDER Onboarding, selecionar múltiplas categorias e
serviços, verificar endereço com localização precisa a 499 m/501 m e confirmar exatamente um
Provider, Address e associações; negar permissão ou falhar qualquer etapa não produz agregado parcial.

### Implementation for User Story 2

- [X] T030 [US2] Criar PostGIS, `provider_categories`, modalidade/active/available, geografia e prova privada, constraints/índices/RLS e reescrever `create_provider_registration` atômica/idempotente em `supabase/migrations/20260821010000_provider_geography_discovery.sql`
- [X] T031 [P] [US2] Implementar a Edge Function autenticada que valida endereço/fix, chama Google Geocoding, calcula 500 m e grava prova efêmera sem device point em `supabase/functions/geocode-provider-address/index.ts`
- [X] T032 [US2] Implementar renovação a partir do dia 25, expiração segura e detecção de mudança material de coordenada em `supabase/functions/refresh-provider-geocodes/index.ts`
- [X] T033 [US2] Configurar as duas Functions, secrets esperados e agendamento diário sem chave no repositório em `supabase/config.toml` e `supabase/migrations/20260821010000_provider_geography_discovery.sql`
- [X] T034 [P] [US2] Criar modelos/DTOs e Repository paginado compartilhado de categorias/serviços ativos em `app/src/main/java/com/trustarx/pitgo/core/catalog/model/ServiceCatalogModels.kt` e `app/src/main/java/com/trustarx/pitgo/core/catalog/repository/ServiceCatalogRepository.kt`
- [X] T035 [P] [US2] Evoluir draft/estado para modalidade obrigatória, categorias com ofertas, verificação selada e prova expirável em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt`
- [X] T036 [US2] Validar empresa/endereço BR, categoria-serviço, duplicidades, preço/duração, qualidade <=100 m e prova atual em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt`
- [X] T037 [US2] Implementar chamada Functions, aquisição one-shot, settings, distância/override e invalidação ao editar endereço em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderAddressVerificationRepository.kt`
- [X] T038 [US2] Atualizar DTOs e Repository para carregar catálogo real e submeter prova/aggregate sem owner/category adulterável em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/data/ProviderRegistrationDtos.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`
- [X] T039 [US2] Orquestrar catálogo, grupos ilimitados, proof lifecycle, 500 m, submit idempotente e sessão esperada em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
- [X] T040 [US2] Conectar Activity Result de COARSE+FINE, rationale/settings/resolution e limpar fix ao sair/trocar sessão em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T041 [US2] Substituir lat/lng manual por “Verificar endereço”, adicionar FIXED/MOBILE, categorias e serviços dinâmicos, diálogo >500 m e estados loading/empty/error em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T042 [US2] Implementar RPCs e eventos para alterar modalidade/disponibilidade, removendo current location quando inelegível, em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/repository/ProviderDashboardRepository.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/viewmodel/ProviderDashboardViewModel.kt`
- [X] T043 [US2] Integrar seletores acessíveis de modalidade/disponibilidade e feedback no dashboard em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/ui/ProviderDashboardScreen.kt`
- [X] T044 [US2] Registrar catálogo, localização, geocodificação e repositories do cadastro na DI manual em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`
- [X] T045 [P] [US2] Adicionar textos de permissão precisa, settings, prova, divergência, catálogo, modalidade e renovação em `app/src/main/res/values/strings.xml`
- [X] T046 [US2] Limpar o back stack após sucesso, retomar onboarding no login e aplicar requisitos de geocodificação ao claim legado em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`

**Checkpoint**: fluxo P1 completo; empresa cadastrada sem Profile e descoberta futura possui endereço confiável.

---

## Phase 5: User Story 3 - Publicar localização de prestador móvel (Priority: P2)

**Goal**: Provider MOBILE publica somente a posição atual durante atendimento ativo, com FGS
visível, arbitragem de múltiplos dispositivos e atualização privada para o cliente participante.

**Independent Test**: Usar fixture administrativa ACCEPTED, iniciar tracking numa sessão PROVIDER
MOBILE e observar atualizações no CLIENT participante; FIXED/terceiro/terminal são bloqueados,
segundo device conflita e ausência de update fica stale após 30 s.

### Implementation for User Story 3

- [X] T047 [US3] Criar `appointments`, estados/checks, uma ativa por provider, `provider_current_locations` sem histórico e RPCs publish/stop/snapshot monotônicas em `supabase/migrations/20260821020000_appointment_current_locations.sql`
- [X] T048 [US3] Adicionar triggers de limpeza, Database Broadcast privado, topic participant-only e RLS de `realtime.messages` à migration `supabase/migrations/20260821020000_appointment_current_locations.sql`
- [X] T049 [P] [US3] Criar estados de tracking, qualidade, lease, outcomes e DTOs de RPC/Broadcast em `app/src/main/java/com/trustarx/pitgo/features/providertracking/model/ProviderTrackingModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/providertracking/data/ProviderLocationDtos.kt`
- [X] T050 [US3] Implementar snapshot, publish/stop sanitizados e classificação de conflito/sessão/offline em `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderLocationRepository.kt`
- [X] T051 [US3] Implementar StateFlow process-scoped `Stopped|Starting|Active|Degraded|Stopping` e controller start/stop em `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderTrackingStateRepository.kt`
- [X] T052 [US3] Implementar FGS high-accuracy a cada ~10 s, newest-only offline, observação de sessão/eligibilidade e todos os stop conditions em `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt`
- [X] T053 [P] [US3] Criar ícone vetorial e textos/canal da notificação persistente com ação de retorno/stop em `app/src/main/res/drawable/ic_notification_location.xml` e `app/src/main/res/values/strings.xml`
- [X] T054 [US3] Implementar ViewModel que valida appointment/provider, permission/notification e solicita FGS sem possuir o loop em `app/src/main/java/com/trustarx/pitgo/features/providertracking/viewmodel/ProviderTrackingViewModel.kt`
- [X] T055 [US3] Criar controles de tracking, indicador persistente, falhas e ação visível de início/stop em `app/src/main/java/com/trustarx/pitgo/features/providertracking/ui/ProviderTrackingScreen.kt`
- [X] T056 [US3] Criar DTOs/modelos e Repository participante com subscribe privado→snapshot→merge por version→removeChannel em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/repository/AppointmentTrackingRepository.kt`
- [X] T057 [US3] Implementar timer Live/Stale de 30 s, reconnect snapshot e limpeza por lifecycle/sessão em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`
- [X] T058 [US3] Criar tela privada com marcador móvel atualizado, estado textual Live/Stale/Ended e sem bottom nav em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`
- [X] T059 [US3] Registrar repositories/service e rotas participant-only de provider/cliente em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`
- [X] T060 [US3] Adicionar textos acessíveis de tracking, notification, conflito, Live/Stale e stop reasons em `app/src/main/res/values/strings.xml`

**Checkpoint**: publicação atual, privada, monotônica e interrompível funciona sem histórico.

---

## Phase 6: User Story 4 - Encontrar providers no mapa pelo serviço (Priority: P2)

**Goal**: O clique de um serviço real da Home abre mapa/lista com somente providers compatíveis,
ativos e disponíveis, sempre pela coordenada pública do endereço.

**Independent Test**: Com fixtures por serviço/categoria/modo, tocar no card e confirmar UUID real,
filtro exato, endereço para FIXED/MOBILE, busca nesta área, empty/offline e ausência de dados privados.

### Implementation for User Story 4

- [X] T061 [US4] Adicionar `list_home_services()` e `discover_service_providers()` PostGIS com limites, projeção mínima, filtro exato e geocode vigente em `supabase/migrations/20260821010000_provider_geography_discovery.sql`
- [X] T062 [P] [US4] Criar estados de mapa/viewport/permissão, resultado público e DTOs sem dados privados em `app/src/main/java/com/trustarx/pitgo/features/providermap/model/ServiceMapModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/providermap/data/ServiceMapDtos.kt`
- [X] T063 [US4] Implementar consulta por service UUID/raio ou viewport, paginação limitada e erros sanitizados em `app/src/main/java/com/trustarx/pitgo/features/providermap/repository/ServiceMapRepository.kt`
- [X] T064 [US4] Implementar geração/collectLatest contra respostas antigas, câmera, refresh/“Buscar nesta área”, one-shot opcional e estados completos em `app/src/main/java/com/trustarx/pitgo/features/providermap/viewmodel/ServiceMapViewModel.kt`
- [X] T065 [US4] Criar Scaffold de mapa com controles seguros, permissão opcional, fallback manual de região e marcadores de endereço em `app/src/main/java/com/trustarx/pitgo/features/providermap/ui/ServiceMapScreen.kt`
- [X] T066 [US4] Integrar clustering estável, seleção textual e modo `Mapa | Lista` equivalente/responsivo em `app/src/main/java/com/trustarx/pitgo/features/providermap/ui/ServiceMapScreen.kt`
- [X] T067 [US4] Trocar `DemoServices` por catálogo real/UUID e representar loading/empty/error/min price no estado da Home em `app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt`
- [X] T068 [US4] Renderizar cards do catálogo real e emitir service UUID para navegação ao mapa em `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt`
- [X] T069 [US4] Adicionar builder `service-map/{serviceId}`, proteger por CLIENT e substituir Home→RequestService por Home→ServiceMap em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`
- [X] T070 [US4] Adicionar textos de mapa/lista, busca na área, região manual, permissão opcional e estados vazios/falha em `app/src/main/res/values/strings.xml`

**Checkpoint**: descoberta pública por serviço funciona sem revelar posição móvel ou dados sensíveis.

---

## Phase 7: User Story 5 - Controlar localização e privacidade do cliente (Priority: P3)

**Goal**: O cliente entende e controla o uso de sua posição; coleta/canais existem somente enquanto
a experiência visível necessita e são limpos ao sair/trocar sessão.

**Independent Test**: Conceder/negar/revogar em mapa e acompanhamento, sair/minimizar/trocar conta e
confirmar indicador explícito, fallback compatível, callbacks/channels removidos e nenhum dado salvo.

### Implementation for User Story 5

- [X] T071 [US5] Criar estado explícito de finalidade/uso visível do cliente e cleanup idempotente no lifecycle em `app/src/main/java/com/trustarx/pitgo/core/location/model/ClientLocationUsage.kt` e `app/src/main/java/com/trustarx/pitgo/core/location/repository/DeviceLocationRepository.kt`
- [X] T072 [US5] Adicionar explicação antes da permissão, indicador em uso, approximate/denial/settings e cancelamento ao sair no mapa em `app/src/main/java/com/trustarx/pitgo/features/providermap/ui/ServiceMapScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/providermap/viewmodel/ServiceMapViewModel.kt`
- [X] T073 [US5] Exibir finalidade/recência e remover callback/channel ao ocultar a experiência participante em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`
- [X] T074 [US5] Coordenar stop best-effort do FGS/RPC, channel/callback cleanup e descarte de resultados tardios antes de logout/troca de conta em `app/src/main/java/com/trustarx/pitgo/core/auth/repository/SessionRepository.kt` e `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`
- [X] T075 [US5] Adicionar textos acessíveis de finalidade, localização em uso, revogação, approximate, settings e fallback em `app/src/main/res/values/strings.xml`

**Checkpoint**: localização do cliente é transitória, visível, revogável e não bloqueia o aplicativo.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Integrar, validar segurança, compatibilidade, acessibilidade e metas mensuráveis de todas
as histórias sem criar suíte automatizada.

- [X] T076 [P] Executar `./gradlew :app:assembleDebug :app:lintDebug` e corrigir incompatibilidades/depreciações somente nos arquivos alterados desta feature, registrando o resultado em `specs/009-provider-location-accounts/quickstart.md`
- [ ] T077 [P] Executar `supabase db reset`, lint das migrations e Functions locais, validando hooks/RLS/grants/Broadcast sem aplicar produção, e registrar o resultado em `specs/009-provider-location-accounts/quickstart.md`
- [ ] T078 Validar manualmente CLIENT/PROVIDER, retomada Onboarding, cadastro 499/501 m, idempotência e migração assistida conforme `specs/009-provider-location-accounts/quickstart.md`
- [ ] T079 Validar mapa/Realtime em API 31, 33 e 34–36, dois aparelhos, offline, process kill, stale 30 s e todos os stop conditions conforme `specs/009-provider-location-accounts/quickstart.md`
- [X] T080 Auditar RLS/RPC/Realtime, logs, APK/configuração e banco para ausência de service-role, API key web, tokens, CNPJ, device point e histórico em `supabase/migrations/` e `app/src/main/java/com/trustarx/pitgo/`
- [ ] T081 Revisar clustering/consulta <=200, resposta de mapa <=3 s, fonte 200%, paisagem, TalkBack, touch targets e diferenciação não baseada só em cor em `app/src/main/java/com/trustarx/pitgo/features/providermap/ui/ServiceMapScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: inicia imediatamente.
- **Phase 2 — Foundational**: depende de Setup e bloqueia todas as histórias.
- **US1**: depende de Foundational; estabelece contas, sessão e rotas.
- **US2**: depende de US1 para existir Provider.Onboarding e conclui o baseline P1.
- **US3**: depende de US1+US2 e da âncora `appointments`; não depende do mapa público.
- **US4**: depende de US1+US2 e pode avançar em paralelo com US3.
- **US5**: depende de US3+US4 porque governa os dois usos visíveis de localização do cliente.
- **Polish**: depende das histórias escolhidas para a entrega.

### User Story Dependency Graph

```text
Setup -> Foundation -> US1 -> US2 -> US3 ----\
                              \-> US4 -------> US5 -> Polish
```

### Within Each User Story

- migration/contrato de backend antes do Repository que o consome;
- modelos/DTOs antes de Repository/ViewModel;
- Repository antes de ViewModel;
- ViewModel antes da integração final da UI/navegação;
- cada chamada assíncrona revalida o user ID esperado antes de publicar estado;
- validação manual do checkpoint antes da próxima história prioritária.

### Parallel Opportunities

- Setup: T003 e T004 podem avançar enquanto T001/T002 configuram Gradle.
- Foundational: T005, T007 e T011 trabalham em SQL, contexto e localização distintos.
- US1: T014, T018, T022, T023 e T024 podem iniciar após Foundation.
- US2: T030, T031, T034, T035 e T045 trabalham em fronteiras distintas; integração ocorre em T038–T046.
- US3: T047, T049 e T053 podem avançar em paralelo; US4 inteiro pode avançar em outra trilha após US2.
- US4: T061 e T062 podem iniciar juntos; Home T067 pode avançar enquanto mapa/repository são implementados.
- Polish: build/lint T076 e backend T077 podem rodar em paralelo.

---

## Parallel Examples

### User Story 1

```text
T014 Provider SignUp models
T018 Provider Dashboard model/repository
T023 ownership-transfer SQL
T024 migration models/coordinator
```

### User Story 2

```text
T030 provider/geography migration
T031 geocoding Edge Function
T034 shared catalog repository
T035 provider registration UI state
```

### User Story 3

```text
T047 appointment/current-location SQL
T049 tracking models/DTOs
T053 notification resources
```

### User Story 4

```text
T061 discovery RPC
T062 map models/DTOs
T067 Home catalog state
```

### User Story 5

```text
Após US3+US4, T071 pode definir o lifecycle/uso compartilhado enquanto T075 prepara os recursos
textuais; T072–T074 integram sequencialmente mapa, acompanhamento e sessão.
```

---

## Implementation Strategy

### MVP First

1. Concluir Setup e Foundational.
2. Entregar US1 e validar contas CLIENT/PROVIDER e destinos independentes.
3. Esse é o menor incremento demonstrável, mas deixa Provider em Onboarding.
4. Para um MVP empresarial operacional, concluir também US2 (ambas são P1).

### Incremental Delivery

1. **US1**: corrige o bug de empresa abrindo Profile e separa autorização.
2. **US2**: conclui cadastro empresarial localizado e catálogo; baseline P1 implantável.
3. **US4** e **US3**: mapa público e tracking privado podem ser construídos em paralelo.
4. **US5**: fecha controles e privacidade transversal do cliente.
5. Executar Polish e o quickstart completo antes de promover o ambiente.

### Safe Backend Rollout

1. Fazer backup/inventário do schema remoto antes de qualquer migration.
2. Aplicar primeiro account roles/ownership e validar preflights sem auto-converter legado.
3. Publicar app compatível com contexto por papel e onboarding retomável.
4. Aplicar geografia/cadastro e depois appointments/current location.
5. Configurar keys, Auth Hook, private Broadcast e cron por ambiente.
6. Nunca executar `db push` de produção como parte automática de uma tarefa Android.

## Notes

- `[P]` pressupõe Foundation concluída e contratos estáveis.
- Não adicionar testes, Room, WorkManager, Hilt, background-location permission ou histórico.
- Não registrar throwables crus do Supabase/Google; algumas mensagens podem conter headers/tokens/PII.
- Commits devem representar uma mudança lógica e usar mensagens descritivas.
- Pare em qualquer checkpoint para validar a história de forma isolada.
