---
description: "Tarefas de implementação para sessão autenticada e cadastro de veículos"
---

# Tasks: Sessão autenticada e cadastro de veículos

**Input**: Documentos de design em `/specs/006-auth-vehicle-registration/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Não gerar testes automatizados nesta feature. A validação prevista é por build, lint,
Supabase local e cenários manuais documentados em `quickstart.md`.

**Organization**: As tarefas estão agrupadas por história de usuário para permitir implementação e
validação incremental. Setup e infraestrutura fundamental são compartilhados por todas as histórias.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode ser executada em paralelo porque atua em arquivos diferentes e não depende de uma
  tarefa ainda não concluída.
- **[Story]**: associa a tarefa à história correspondente (`US1`, `US2` ou `US3`).
- Cada descrição contém o caminho exato dos arquivos que devem ser criados ou alterados.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Adicionar somente as dependências necessárias para preferência de onboarding e avatar.

- [X] T001 Adicionar versões e aliases do Preferences DataStore 1.2.1, Coil Compose 3.3.0 e Coil Network Ktor3 3.3.0 em `gradle/libs.versions.toml`
- [X] T002 Habilitar as dependências do DataStore e dos dois artefatos Coil no módulo Android, preservando Supabase/Ktor/serialization atuais, em `app/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Criar o contrato seguro do banco, os modelos/repositórios compartilhados, a composition
root e a máquina central de sessão que bloqueiam todas as histórias.

**⚠️ CRITICAL**: Nenhuma tela autenticada pode ser implementada antes desta fase estar concluída.

- [X] T003 Criar a reconciliação incremental de `public.profiles`: preservar dados/tabela existentes, garantir `avatar_url` e apenas `active`, remover aliases `activity`/`activty`, consolidar um trigger `handle_new_user` com metadados `name`/`phone`/`cpf`/`cnpj`, RLS e grants SELECT-only em `supabase/migrations/20260819010000_auth_session_vehicles.sql`
- [X] T004 Completar a mesma migração com constraints/índices/RLS SELECT-only de `public.vehicles` e RPCs transacionais `create_vehicle`/`set_primary_vehicle`, usando `auth.uid()`, advisory lock por usuário, `SECURITY DEFINER SET search_path = ''` e EXECUTE apenas para `authenticated`, em `supabase/migrations/20260819010000_auth_session_vehicles.sql`
- [X] T005 [P] Criar `Profile` e `AccountSnapshot` imutáveis, derivações de iniciais e invariantes de identidade/perfil/veículo em `app/src/main/java/com/trustarx/pitgo/core/account/model/AccountModels.kt`
- [X] T006 [P] Criar DTOs serializáveis de projeção de perfil e mapeamento estrito para o domínio, usando exclusivamente a coluna `active`, em `app/src/main/java/com/trustarx/pitgo/core/account/data/AccountDtos.kt`
- [X] T007 [P] Modelar `AppSessionState`, razões não autenticadas, falha offline e perfil bloqueado como estados mutuamente exclusivos em `app/src/main/java/com/trustarx/pitgo/core/auth/model/AppSessionState.kt`
- [X] T008 [P] Criar `Vehicle`, `VehicleDraft`, origem do formulário, campos/erros e resultados de repositório sem `userId` editável em `app/src/main/java/com/trustarx/pitgo/core/vehicle/model/VehicleModels.kt`
- [X] T009 [P] Criar DTOs serializáveis de leitura e parâmetros exatos dos RPCs, sem owner/status/timestamps enviados pela UI, em `app/src/main/java/com/trustarx/pitgo/core/vehicle/data/VehicleDtos.kt`
- [X] T010 [P] Implementar normalização e validação local de marca/modelo, placas legado/Mercosul, Renavam, VIN, anos e quilometragem em `app/src/main/java/com/trustarx/pitgo/core/vehicle/validation/VehicleValidation.kt`
- [X] T011 [P] Implementar `VehicleOnboardingRepository` com Preferences DataStore e operações atômicas `markPending`, `isPending` e `resolve` por userId em `app/src/main/java/com/trustarx/pitgo/core/preferences/VehicleOnboardingRepository.kt`
- [X] T012 Implementar a fronteira única do Supabase Auth para inicialização, validação remota, login e logout local, com erros allowlisted e sem registrar Throwable/tokens/senha, em `app/src/main/java/com/trustarx/pitgo/core/auth/data/SupabaseAuthDataSource.kt`
- [X] T013 Implementar `AccountRepository` para consultar somente o perfil próprio e o veículo ativo principal, validar consistência e classificar perfil ausente/inativo/inconsistente sem reparo automático em `app/src/main/java/com/trustarx/pitgo/core/account/repository/AccountRepository.kt`
- [X] T014 Implementar `VehicleRepository` para listar veículos ativos próprios e chamar apenas `create_vehicle`/`set_primary_vehicle`, filtrando por sessão e mapeando conflitos por constraints allowlisted em `app/src/main/java/com/trustarx/pitgo/core/vehicle/repository/VehicleRepository.kt`
- [X] T015 Implementar `SessionRepository` com bootstrap único, restauração/validação remota, snapshot somente em `Authenticated`, observação de expiração, retry, refresh e logout que remove estado privado imediatamente em `app/src/main/java/com/trustarx/pitgo/core/auth/repository/SessionRepository.kt`
- [X] T016 Criar o container manual process-scoped com uma instância dos data sources/repositórios compartilhados e dependências explícitas em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`
- [X] T017 Criar `PitGoApplication` para inicializar o container uma vez por processo a partir do cliente Supabase existente em `app/src/main/java/com/trustarx/pitgo/PitGoApplication.kt`
- [X] T018 Registrar `PitGoApplication` e fornecer o container à raiz Compose/NavHost sem service locator em `app/src/main/AndroidManifest.xml` e `app/src/main/java/com/trustarx/pitgo/MainActivity.kt`
- [X] T019 [P] Excluir SharedPreferences de sessão Supabase de backup e transferência entre dispositivos em `app/src/main/res/xml/backup_rules.xml` e `app/src/main/res/xml/data_extraction_rules.xml`
- [X] T020 Implementar `SessionViewModel` como proprietário do estado raiz e das ações initialize/retry/refresh/logout, limpando snapshots ao sair de `Authenticated`, em `app/src/main/java/com/trustarx/pitgo/features/session/viewmodel/SessionViewModel.kt`
- [X] T021 Implementar `SessionGateScreen` para progresso, verificação offline com retry e perfil bloqueado com retry/logout, sem renderizar dados privados, em `app/src/main/java/com/trustarx/pitgo/features/session/ui/SessionGateScreen.kt`
- [X] T022 Centralizar as novas rotas/builders sem identificadores sensíveis e tornar `session-gate` o destino raiz que protege destinos privados em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`

**Checkpoint**: O app possui uma fronteira de sessão segura e infraestrutura compartilhada; nenhuma
Home privada é exibida antes da validação de Auth e profile.

---

## Phase 3: User Story 1 - Entrar e acessar uma Home personalizada (Priority: P1) 🎯 MVP

**Goal**: Entrar/restaurar/encerrar uma sessão real e mostrar somente o perfil e veículo principal
da conta autorizada na Home.

**Independent Test**: Entrar com uma conta existente, fechar/reabrir o app e confirmar que a Home
mostra o nome/avatar ou iniciais e o veículo principal daquela conta; logout deve voltar à Welcome
sem permitir que Back revele dados privados.

### Implementation for User Story 1

- [X] T023 [P] [US1] Criar `LoginUiState`, erros de campo e falhas remotas tipadas, mantendo senha apenas em memória, em `app/src/main/java/com/trustarx/pitgo/features/login/model/LoginModels.kt`
- [X] T024 [US1] Implementar `LoginViewModel` com normalização de e-mail, validação local, submit serializado, ativação do bootstrap central e limpeza de senha após falha remota em `app/src/main/java/com/trustarx/pitgo/features/login/viewmodel/LoginViewModel.kt`
- [X] T025 [US1] Criar `LoginRoute`/`LoginScreen` Material, scroll/IME-safe, foco no primeiro erro, senha obscurecida com toggle e CTA com progresso em `app/src/main/java/com/trustarx/pitgo/features/login/ui/LoginScreen.kt`
- [X] T026 [P] [US1] Substituir modelos/dados pessoais demonstrativos por estados `Loading`, `Content`, `NoVehicle` e `Failure` derivados de `AccountSnapshot`, preservando apenas o catálogo real de serviços, em `app/src/main/java/com/trustarx/pitgo/features/home/model/HomeContent.kt`
- [X] T027 [US1] Refatorar `HomeViewModel` para consumir o snapshot autenticado, expor estados exclusivos e recarregar a conta após mutação de veículo sem reutilizar dados anteriores em `app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt`
- [X] T028 [US1] Refatorar a Home para exibir nome real, avatar HTTPS via Coil ou iniciais, veículo principal real/estado sem veículo e endereço neutro, mantendo serviços e bottom navigation, em `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt`
- [X] T029 [P] [US1] Criar a tela Perfil com nome/e-mail autorizados, confirmação de logout e bottom navigation com Perfil selecionado em `app/src/main/java/com/trustarx/pitgo/features/profile/ui/ProfileScreen.kt`
- [X] T030 [P] [US1] Adicionar textos, descrições acessíveis e vetores estritamente necessários para session gate, login, Home personalizada e Perfil em `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/ic_login.xml` e `app/src/main/res/drawable/ic_profile_logout.xml`
- [X] T031 [US1] Ligar `Entrar` da Welcome ao Login e integrar Login/Home/Perfil/logout, limpeza de back stack, guards `dropUnlessResumed`, system bars e destinos Início/Perfil da bottom navigation em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/core/ui/PitGoBottomNavigation.kt`
- [ ] T032 [US1] Executar e registrar os cenários de sessão inicial, login válido/inválido, restauração, offline, perfil bloqueado e logout descritos nas seções 4 e 10 de `specs/006-auth-vehicle-registration/quickstart.md`

**Checkpoint**: A US1 é demonstrável de ponta a ponta e constitui o MVP: Welcome → Login → Home
personalizada → Perfil/logout, incluindo restauração e estados seguros de falha.

---

## Phase 4: User Story 2 - Decidir sobre o primeiro veículo após criar a conta (Priority: P2)

**Goal**: Persistir a decisão pós-cadastro e permitir cadastrar o primeiro veículo ou seguir para a
Home vazia sem repetir a criação da identidade.

**Independent Test**: Criar uma conta válida e verificar separadamente que `Agora não` abre a Home
sem veículo e que `Cadastrar veículo` salva o primeiro veículo como ativo/principal; fechar o app
antes da escolha deve reapresentar o convite.

### Implementation for User Story 2

- [X] T033 [US2] Refatorar o cadastro existente para consumir os DTOs compartilhados, remover qualquer upsert/recriação/reativação de profile, revalidar apenas o trigger e marcar onboarding pendente antes do sucesso em `app/src/main/java/com/trustarx/pitgo/features/signup/data/ProfileDtos.kt` e `app/src/main/java/com/trustarx/pitgo/features/signup/repository/SignUpRepository.kt`
- [X] T034 [US2] Adaptar o estado e a conclusão do cadastro para ativar o bootstrap central somente após profile confirmado e pendência persistida, sem navegar diretamente à Home, em `app/src/main/java/com/trustarx/pitgo/features/signup/viewmodel/SignUpViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/signup/ui/SignUpScreen.kt`
- [X] T035 [P] [US2] Implementar `VehicleOnboardingViewModel` para consultar a pendência e resolver atomicamente `Cadastrar veículo` ou `Agora não`, ignorando toques concorrentes em `app/src/main/java/com/trustarx/pitgo/features/onboarding/viewmodel/VehicleOnboardingViewModel.kt`
- [X] T036 [US2] Criar a tela de decisão pós-cadastro sem bottom navigation, com ações acessíveis e Back incapaz de descartar silenciosamente a pendência, em `app/src/main/java/com/trustarx/pitgo/features/onboarding/ui/VehicleOnboardingScreen.kt`
- [X] T037 [US2] Implementar `VehicleFormViewModel` com todos os campos do schema em memória, primeiro erro/foco, opção de principal quando aplicável, submit serializado via RPC e falhas recuperáveis sem duplicação em `app/src/main/java/com/trustarx/pitgo/features/garage/viewmodel/VehicleFormViewModel.kt`
- [X] T038 [US2] Criar o formulário Material completo com top bar, scroll/IME, máscaras, supporting errors, confirmação de descarte, CTA de 56dp e nenhum campo de owner/status em `app/src/main/java/com/trustarx/pitgo/features/garage/ui/VehicleFormScreen.kt`
- [X] T039 [P] [US2] Adicionar textos, mensagens neutras, descrições acessíveis e vetores necessários ao convite e formulário de veículo em `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/ic_vehicle.xml` e `app/src/main/res/drawable/ic_vehicle_plate.xml`
- [X] T040 [US2] Integrar conclusão do signup, consulta persistida de pending, destino `vehicle-onboarding` e `vehicle/new?origin=ONBOARDING`, resolvendo a escolha antes de Home/form e retornando à Home após salvar/descartar em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`
- [ ] T041 [US2] Executar e registrar os cenários de pendência persistente, `Agora não`, primeiro veículo, falha/retry e descarte descritos nas seções 5, 6 e 10 de `specs/006-auth-vehicle-registration/quickstart.md`

**Checkpoint**: A US2 funciona sobre a sessão da US1; uma conta nova sempre toma uma decisão explícita
e o primeiro veículo confirmado passa a ser o principal exibido na Home.

---

## Phase 5: User Story 3 - Cadastrar e selecionar veículos pela Garagem (Priority: P3)

**Goal**: Listar os veículos ativos da conta, adicionar outro e alterar o principal de forma
transacional para manter Home e Garagem consistentes.

**Independent Test**: Abrir a Garagem de uma conta autenticada, adicionar um segundo veículo,
selecioná-lo como principal e confirmar que apenas ele está marcado e que a Home reflete a seleção.

### Implementation for User Story 3

- [X] T042 [P] [US3] Criar estados exclusivos `Loading`, `Empty`, `Content` e `Failure`, incluindo progresso por veículo na troca de principal, em `app/src/main/java/com/trustarx/pitgo/features/garage/model/GarageUiModels.kt`
- [X] T043 [US3] Implementar `GarageViewModel` para listar somente veículos ativos próprios, recarregar, serializar `setPrimary` e atualizar a seleção para exatamente um item em `app/src/main/java/com/trustarx/pitgo/features/garage/viewmodel/GarageViewModel.kt`
- [X] T044 [US3] Criar a Garagem Material com empty state, cards de veículos, indicação textual/semântica do principal, retry, ação de adicionar e bottom navigation fixa em `app/src/main/java/com/trustarx/pitgo/features/garage/ui/GarageScreen.kt`
- [X] T045 [P] [US3] Adicionar textos, estados vazios/falhas, descrições acessíveis e vetores necessários à Garagem e seleção do principal em `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/ic_garage_add.xml` e `app/src/main/res/drawable/ic_vehicle_primary.xml`
- [X] T046 [US3] Integrar destino Garagem, `vehicle/new?origin=GARAGE`, destinos ativos Início/Garagem/Perfil e refresh da Home ao retornar de criação/troca de principal em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, `app/src/main/java/com/trustarx/pitgo/core/ui/PitGoBottomNavigation.kt` e `app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt`
- [X] T047 [US3] Observar perda/expiração da sessão no formulário para cancelar envio, zerar todos os campos, ignorar o diálogo de descarte e remover a pilha privada antes do Login em `app/src/main/java/com/trustarx/pitgo/features/garage/viewmodel/VehicleFormViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`
- [ ] T048 [US3] Executar e registrar os cenários de Garagem, identificadores duplicados, concorrência, autorização cruzada e expiração durante rascunho das seções 6, 7, 8 e 10 de `specs/006-auth-vehicle-registration/quickstart.md`

**Checkpoint**: As três histórias estão funcionais; Home e Garagem compartilham a mesma seleção real e
nenhuma mutação pode escolher owner ou produzir dois principais.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Remover resíduos demonstrativos, revisar segurança/acessibilidade e produzir evidência
de validação proporcional ao risco da feature.

- [X] T049 Remover modelos/imports demonstrativos de perfil/veículo e o DTO de profile antigo após migrar todos os consumidores, preservando apenas o catálogo de serviços, em `app/src/main/java/com/trustarx/pitgo/core/common/model/DemoVehicle.kt`, `app/src/main/java/com/trustarx/pitgo/features/home/model/HomeContent.kt` e `app/src/main/java/com/trustarx/pitgo/features/signup/data/ProfileDtos.kt`
- [X] T050 Revisar responsividade, IME, fonte 200%, headings, live regions, alvos de 48dp e estados não dependentes apenas de cor nas telas em `app/src/main/java/com/trustarx/pitgo/features/session/ui/SessionGateScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/login/ui/LoginScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/onboarding/ui/VehicleOnboardingScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/garage/ui/GarageScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/garage/ui/VehicleFormScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/profile/ui/ProfileScreen.kt`
- [X] T051 Auditar mapeamento de falhas e logs para garantir ausência de senha, token, CPF/CNPJ, Renavam, chassi, headers, URLs e detalhes crus em `app/src/main/java/com/trustarx/pitgo/core/auth/data/SupabaseAuthDataSource.kt`, `app/src/main/java/com/trustarx/pitgo/core/account/repository/AccountRepository.kt` e `app/src/main/java/com/trustarx/pitgo/core/vehicle/repository/VehicleRepository.kt`
- [ ] T052 Executar `npx supabase db reset` e `npx supabase db lint` localmente, revisar os dois estados de reconciliação e registrar o resultado sem aplicar migração remota automaticamente em `specs/006-auth-vehicle-registration/quickstart.md`
- [X] T053 Executar `./gradlew :app:assembleDebug :app:lintDebug`, corrigir qualquer erro/aviso introduzido e registrar a evidência em `specs/006-auth-vehicle-registration/quickstart.md`
- [ ] T054 Concluir a passagem manual visual, TalkBack, privacidade e fluxo ponta a ponta em aparelho compacto/landscape conforme as seções 4–10 de `specs/006-auth-vehicle-registration/quickstart.md`
- [X] T055 Revisar `git diff --check`, arquivos rastreados e artefatos/logs contra credenciais ou valores sensíveis reais e registrar a confirmação final em `specs/006-auth-vehicle-registration/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: não possui dependências e começa imediatamente.
- **Foundational (Phase 2)**: depende do Setup e bloqueia todas as histórias.
- **US1 (Phase 3)**: depende da fundação e entrega o MVP autenticado.
- **US2 (Phase 4)**: usa a sessão/Home da US1; seus componentes de onboarding e formulário podem ser
  preparados após a fundação, mas o fluxo completo depende da US1.
- **US3 (Phase 5)**: usa o formulário compartilhado entregue pela US2 e a Home/sessão da US1.
- **Polish (Phase 6)**: depende de todas as histórias desejadas concluídas.

### User Story Dependency Graph

```text
Setup -> Foundation -> US1 (MVP) -> US2 -> US3 -> Polish
                      |             |
                      +-------------+---- componentes de Garagem podem iniciar após Foundation
```

### Within Each User Story

- Modelos/estados antes dos ViewModels.
- Repositórios/RPCs antes dos ViewModels que os chamam.
- ViewModels antes das telas e integração de navegação.
- Integração completa antes da validação manual da história.
- Operações de UI só navegam ao observar estado durável; repositórios/ViewModels não recebem
  `NavController`.

### Parallel Opportunities

- Após T002, T005–T011 e T019 atuam em arquivos independentes e podem avançar em paralelo, respeitando
  as dependências de compilação indicadas pela ordem.
- Após a fundação, T023 e T026 podem avançar em paralelo; T029 e T030 também são independentes dos
  ajustes centrais de Home enquanto seus contratos permanecem estáveis.
- Na US2, T035 pode avançar em paralelo ao refactor inicial do signup; T039 pode avançar enquanto o
  formulário e o convite são implementados.
- Na US3, T042 e T045 podem avançar em paralelo antes da integração final.

---

## Parallel Example: User Story 1

```text
Task T023: Criar LoginUiState em features/login/model/LoginModels.kt
Task T026: Criar estados reais da Home em features/home/model/HomeContent.kt
Task T029: Criar ProfileScreen em features/profile/ui/ProfileScreen.kt
Task T030: Preparar recursos de sessão/login/Home/Perfil em res/
```

## Parallel Example: User Story 2

```text
Task T035: Implementar VehicleOnboardingViewModel
Task T039: Preparar recursos de onboarding e formulário
```

## Parallel Example: User Story 3

```text
Task T042: Criar GarageUiModels
Task T045: Preparar recursos da Garagem
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Concluir Setup.
2. Concluir a fundação crítica, incluindo migração local, raiz de sessão e guards privados.
3. Implementar US1.
4. Parar no checkpoint e validar login, restauração, Home real, falhas seguras e logout.
5. Demonstrar o MVP antes de ampliar onboarding/Garagem, se desejado.

### Incremental Delivery

1. Setup + Foundation → fronteira de Auth, dados e navegação segura pronta.
2. US1 → login e Home personalizada entregues como MVP.
3. US2 → novo cadastro recebe convite persistente e primeiro veículo.
4. US3 → Garagem permanente, múltiplos veículos e troca de principal.
5. Polish → validação completa de segurança, banco, build e acessibilidade.

### Safe Database Delivery

1. Criar e revisar a migração incremental sem destruir tabelas/linhas existentes.
2. Validar exclusivamente no Supabase local descartável.
3. Comparar com o schema remoto manual e obter backup/aprovação do proprietário.
4. Aplicar remotamente somente em uma ação posterior deliberada; esta lista não autoriza `db push`.

---

## Notes

- Tarefas `[P]` atuam em arquivos diferentes e não possuem dependência imediata entre si.
- Labels `[US1]`, `[US2]` e `[US3]` garantem rastreabilidade aos cenários da especificação.
- Nenhuma tarefa cria teste automatizado; `assembleDebug`, `lintDebug`, Supabase local e quickstart
  são os gates de qualidade definidos.
- Não adicionar Hilt/Koin, Room, Realtime, cache de conta/veículo ou persistência de rascunho.
- Não enviar `user_id`, `active` ou `is_primary` final pela interface; RPCs e `auth.uid()` são a
  autoridade para ownership/status.
- Não executar migração remota nem incluir credencial real em arquivo rastreado.
- Fazer commit após cada tarefa ou grupo lógico pequeno facilita revisão e rollback.
