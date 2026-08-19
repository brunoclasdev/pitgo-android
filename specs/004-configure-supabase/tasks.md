---

description: "Tarefas de implementação para configurar o Supabase no Android"
---

# Tasks: Configurar Supabase

**Input**: Documentos de design em `/specs/004-configure-supabase/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/supabase-configuration.md` e `quickstart.md`

**Tests**: Nenhum teste automatizado será criado. A validação usa builds positivos/negativos,
lint, inspeção de segurança e regressão manual, conforme solicitado na especificação.

**Organization**: As tarefas são agrupadas por história para permitir implementação e validação
incrementais. Todas as fixtures usadas nos comandos devem ser sintéticas; valores reais nunca
devem aparecer em tarefas, argumentos, logs ou arquivos rastreados.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode executar em paralelo por atuar em arquivo diferente ou por ser uma validação
  independente após o mesmo pré-requisito
- **[Story]**: história atendida (`US1`, `US2` ou `US3`)
- Cada tarefa informa os caminhos exatos que devem ser alterados ou usados na validação

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Declarar os artefatos mínimos necessários sem instalar módulos funcionais do
Supabase.

- [X] T001 Adicionar versões e aliases para `supabase-kt` 3.2.6, `ktor-client-android` 3.3.1 e `desugar_jdk_libs` 2.1.5 em `gradle/libs.versions.toml`
- [X] T002 [P] Declarar somente `android.permission.INTERNET`, fora de `<application>`, em `app/src/main/AndroidManifest.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Preparar dependências e a fronteira comum de configuração que bloqueiam todas as
histórias.

**⚠️ CRITICAL**: Nenhuma história pode ser concluída antes desta fase.

- [X] T003 Habilitar core library desugaring e `BuildConfig`, adicionar as dependências catalogadas de Supabase core, Ktor Android e desugaring, sem Auth/PostgREST/Storage/Realtime/Functions, em `app/build.gradle.kts`
- [X] T004 Implementar a leitura canônica case-sensitive de `supabaseUrl` e `supabaseKey` por `providers.gradleProperty`, `trim`, falha segura para valores ausentes/blank, escape completo para literal Java e geração de `BuildConfig.SUPABASE_URL`/`SUPABASE_KEY` em `defaultConfig` de `app/build.gradle.kts`

**Checkpoint**: O módulo possui dependências mínimas e uma fronteira de configuração comum para
todas as variantes.

---

## Phase 3: User Story 1 - Disponibilizar a conexão configurada (Priority: P1) 🎯 MVP

**Goal**: Disponibilizar um único `SupabaseClient` pronto para reutilização interna, sem request,
plugin funcional ou alteração visual.

**Independent Test**: Com URL HTTPS e chave publishable sintéticas fornecidas externamente, o app
compila e abre; acessos repetidos ao provider usam a mesma instância e Welcome, Home e
RequestService permanecem inalteradas.

### Implementation for User Story 1

- [X] T005 [US1] Criar o `internal object SupabaseClientProvider` com propriedade `client` lazy e read-only, inicializada por `createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)` sem plugins ou chamadas remotas, em `app/src/main/java/com/trustarx/pitgo/core/network/SupabaseClientProvider.kt`
- [X] T006 [US1] Executar o cenário positivo com inputs sintéticos, conferir compilação e identidade única do provider por inspeção, instalar o APK e percorrer Welcome → Home → RequestService sem regressão, seguindo `specs/004-configure-supabase/quickstart.md`

**Checkpoint**: A conexão central está configurada e pode ser demonstrada independentemente como
o MVP da feature.

---

## Phase 4: User Story 2 - Detectar configuração ausente ou inválida (Priority: P2)

**Goal**: Interromper qualquer build antes do artefato quando URL ou chave não forem apropriadas,
com diagnóstico acionável que não revele valores.

**Independent Test**: Ausência, blank, URL sem HTTPS/host ou com user-info, chave secret,
service-role, JWT malformado e formato desconhecido falham em debug e release; publishable e JWT
legado com `role=anon` passam sem acesso à rede.

### Implementation for User Story 2

- [X] T007 [US2] Adicionar validação local antecipada e agregada de URI HTTPS absoluta sem user-info e allowlist de chave publishable/JWT legado `role=anon`, rejeitando secret, service-role, roles duplicados e formatos inválidos sem ecoar entrada ou payload, em `app/build.gradle.kts`
- [X] T008 [US2] Executar a matriz negativa e os casos válidos de debug/release com fixtures sintéticas, confirmando falha antes de APK/AAB e mensagens apenas com campo/correção, conforme `specs/004-configure-supabase/quickstart.md` e `specs/004-configure-supabase/contracts/supabase-configuration.md`

**Checkpoint**: Configurações ausentes, inválidas ou elevadas são recusadas de forma explícita em
todas as variantes.

---

## Phase 5: User Story 3 - Preservar credenciais e ambientes (Priority: P3)

**Goal**: Suportar o mesmo contrato em ambiente local e CI, com precedência determinística e zero
credencial real em arquivos rastreados ou diagnósticos.

**Independent Test**: Propriedades Gradle/CI vencem o fallback ignorado, uma fonte superior blank
falha sem fallback, espaços externos são removidos e a busca pela chave real não encontra arquivos
rastreados nem saída de erro.

### Implementation for User Story 3

- [X] T009 [US3] Adicionar fallback declarado por `providers.fileContents` para `local.properties`, usando `java.util.Properties` e preservando precedência da propriedade Gradle inclusive quando ela estiver blank, em `app/build.gradle.kts`
- [X] T010 [P] [US3] Validar com fixtures sintéticas as fontes local e `ORG_GRADLE_PROJECT_supabaseUrl`/`ORG_GRADLE_PROJECT_supabaseKey`, a precedência, o caso blank superior e o `trim`, seguindo `specs/004-configure-supabase/quickstart.md`
- [X] T011 [P] [US3] Inspecionar Git e saídas de falha sem imprimir credenciais, confirmando que `local.properties` continua ignorado/não rastreado e que nenhuma chave real foi adicionada, conforme `specs/004-configure-supabase/quickstart.md` e `.gitignore`

**Checkpoint**: Configuração local e automatizada funciona sem alteração de código e sem vazamento
de credencial real.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Confirmar o escopo mínimo, compatibilidade e ausência de regressões ao juntar as três
histórias.

- [X] T012 [P] Revisar o grafo final e o código para confirmar somente Supabase core, Ktor Android, desugaring e o provider compartilhado, sem módulos funcionais, Repository, DI, logging ou health check, em `gradle/libs.versions.toml`, `app/build.gradle.kts` e `app/src/main/java/com/trustarx/pitgo/core/network/SupabaseClientProvider.kt`
- [X] T013 Executar `clean`, `assembleDebug`, `assembleRelease` e `lintDebug` com configuração externa válida, repetir a abertura offline e a regressão dos fluxos existentes e finalizar com inspeção do diff conforme `specs/004-configure-supabase/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa imediatamente; T001 e T002 podem ocorrer em paralelo.
- **Foundational (Phase 2)**: T003 depende de T001; T004 depende de T003 e bloqueia as histórias.
- **User Story 1 (Phase 3)**: depende da fase foundational e da permissão T002.
- **User Story 2 (Phase 4)**: depende da fase foundational; não depende do provider de US1.
- **User Story 3 (Phase 5)**: depende da fase foundational; T010 e T011 podem executar em paralelo
  depois de T009.
- **Polish (Phase 6)**: depende de todas as histórias incluídas na entrega.

### User Story Dependencies

```text
Setup ──> Foundational ──┬──> US1 (cliente compartilhado / MVP)
                        ├──> US2 (validação estrita)
                        └──> US3 (fontes e proteção)

US1 + US2 + US3 ──> Polish
```

- **US1 (P1)**: começa após a fundação e não depende de US2/US3 para seu cenário positivo.
- **US2 (P2)**: começa após a fundação e é verificável com a matriz de configuração; não usa US1.
- **US3 (P3)**: começa após a fundação e é verificável pelas fontes/inspeção; não usa o provider de
  US1. T007 e T009 alteram o mesmo arquivo, portanto devem ser sequenciadas ou coordenadas apesar
  de as histórias serem conceitualmente independentes.

### Within Each User Story

- Implementação precede sua validação manual.
- A validação usa somente fixtures sintéticas ou valores fornecidos externamente.
- Nenhum artefato deve prosseguir após falha de configuração.
- A história pode ser validada antes de avançar à prioridade seguinte.

### Parallel Opportunities

- T001 e T002 atuam em arquivos diferentes e podem executar em paralelo.
- Após T009, T010 e T011 são verificações independentes e podem executar em paralelo.
- T012 pode ocorrer em paralelo com preparativos externos para T013.
- US1, US2 e US3 têm fronteiras próprias após a fundação, mas alterações simultâneas em
  `app/build.gradle.kts` devem ser evitadas.

---

## Parallel Examples

### Setup

```text
Task T001: Atualizar gradle/libs.versions.toml
Task T002: Atualizar app/src/main/AndroidManifest.xml
```

### User Story 1

```text
T005 cria o provider; T006 começa somente quando o provider e os campos BuildConfig compilarem.
Não há paralelismo seguro dentro desta história.
```

### User Story 2

```text
T007 implementa a validação; T008 executa a matriz somente após T007.
Não há paralelismo seguro dentro desta história.
```

### User Story 3

```text
Após T009:
Task T010: Validar fontes, precedência e normalização
Task T011: Inspecionar rastreamento e vazamentos
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Concluir T001–T004 para preparar a fundação.
2. Concluir T005 para disponibilizar o cliente compartilhado.
3. Executar T006 e confirmar o cenário positivo sem mudança visual ou request.
4. Parar e demonstrar o cliente central antes das regras adicionais, se necessário.

### Incremental Delivery

1. **Setup + Foundational** → dependências mínimas e contrato gerado.
2. **US1** → cliente compartilhado pronto (MVP).
3. **US2** → configuração inválida/elevada bloqueada em todas as variantes.
4. **US3** → local e CI com precedência segura e inspeção de vazamento.
5. **Polish** → build/lint/regressão final do conjunto.

### Recommended Sequential Order

```text
T001 + T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009
                                                     └───────> T010 + T011
T010 + T011 → T012 + T013
```

## Notes

- `[P]` indica paralelismo real sem edição concorrente do mesmo arquivo.
- Nenhum task adiciona testes, fixtures persistidas ou credenciais reais.
- `local.properties` é entrada externa ignorada; não deve ser criado com valores sintéticos ou
  reais como mudança rastreada.
- Commits devem representar grupos lógicos pequenos, como `chore: add Supabase build setup` e
  `feat: expose shared Supabase client`.
