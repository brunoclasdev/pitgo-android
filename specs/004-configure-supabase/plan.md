# Implementation Plan: Configurar Supabase

**Branch**: `004-configure-supabase` | **Date**: 2026-08-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-configure-supabase/spec.md`

## Summary

Configurar um único `SupabaseClient` compartilhado no aplicativo a partir de `supabaseUrl` e
`supabaseKey` fornecidos fora do repositório. O Gradle resolve os mesmos nomes em propriedades de
projeto/CI e, como fallback local, em `local.properties`; remove espaços externos, valida URL e
tipo de chave sem rede e interrompe qualquer build inválido sem revelar valores. As entradas
validadas são geradas em `BuildConfig` e consumidas por um provider lazy em `core/network` usando
somente o módulo central Supabase e o engine Ktor Android. A entrega não instala plugins de
produto, não faz requisições e não altera UI, navegação ou estado de tela.

## Technical Context

**Language/Version**: Kotlin integrado 2.2.10 no projeto; dependência Supabase compilada com Kotlin
2.2.21, na mesma linha de metadata; bytecode Java/Kotlin 11; JDK 17 para o build

**Primary Dependencies**: Android Gradle Plugin 9.1.1, Gradle 9.3.1,
`supabase-kt` 3.2.6, `ktor-client-android` 3.3.1 e `desugar_jdk_libs` 2.1.5; dependências Compose e
Jetpack existentes permanecem inalteradas

**Storage**: somente propriedades externas efêmeras e campos gerados por variante; sem banco,
arquivo persistente do app, cache ou preferência

**Testing**: build positivo e negativo, lint, inspeção de vazamento e regressão manual conforme
`quickstart.md`; nenhum teste automatizado será criado

**Target Platform**: aplicativo Android nativo, minSdk 24 preservado por core library desugaring,
targetSdk 36 e compileSdk 37

**Project Type**: aplicativo mobile Android single-module

**Performance Goals**: validação inteiramente local durante configuração do build; criação lazy de
um único cliente por processo sem request; nenhuma diferença perceptível no tempo de abertura ou
fluidez dos fluxos existentes

**Constraints**: qualquer variante falha antes de APK/AAB quando a configuração é ausente ou
inválida; somente chave `sb_publishable_...` ou JWT legado com papel `anon`; nunca incorporar chave
`sb_secret_...` ou `service_role`; valores reais fora de arquivos rastreados e logs; nenhum health
check, autenticação, consulta, Storage, Realtime, Repository, DI, UI ou permissão sensível

**Scale/Scope**: dois inputs obrigatórios, uma configuração validada por ambiente, dois campos
gerados, um cliente Supabase por processo, uma permissão normal e três dependências técnicas
diretas/configuradas

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Gate evaluation before research

- **Simplicidade e YAGNI — PASS**: são adicionados apenas resolução/validação de dois valores, o
  núcleo do cliente solicitado, um engine obrigatório e desugaring necessário para minSdk 24.
  Nenhum módulo funcional Supabase, wrapper genérico, AppContainer, DI ou camada futura é criado.
- **Android nativo moderno — PASS**: o projeto mantém Kotlin, AGP 9.1, Gradle Kotlin DSL e sua
  estrutura single-module. `BuildConfig` é o mecanismo oficial Android para campos customizados e
  `core/network` é a responsabilidade compartilhada prevista pela constituição.
- **Dependências justificadas — PASS**: Android/Jetpack não oferecem cliente Supabase. O SDK
  comunitário referenciado pela própria documentação Supabase cria o cliente solicitado; Ktor é
  seu engine obrigatório e desugaring mantém o suporte Android existente. Versões estáveis e
  compatíveis foram confirmadas em fontes primárias.
- **MVVM e dados — PASS**: não há UI, estado de tela nem operação de dados nesta etapa, portanto
  ViewModel, Repository e Use Case seriam artificiais. Futuras operações remotas deverão entrar
  por Repository e receber o cliente compartilhado, sem acesso direto de Composables.
- **Segurança e falhas explícitas — PASS**: uma allowlist aceita somente chaves de cliente, detecta
  o papel do JWT legado localmente e rejeita credenciais elevadas antes da geração do app.
  Diagnósticos citam apenas nomes/regras; a chave não é registrada. A chave publicável permanece
  extraível do APK por definição e RLS continua sendo a fronteira de segurança.
- **Rede e permissões — PASS**: `INTERNET` é permissão normal sem prompt e torna o cliente apto a
  uso futuro. Construir o cliente não realiza chamada; estados de rede só serão modelados quando
  uma feature executar I/O.
- **Qualidade proporcional — PASS**: a constituição proíbe geração automática de testes não
  solicitados. Build/lint, matrizes negativas, inspeção de logs/repositório e regressão manual são
  proporcionais ao risco de configuração.

## Project Structure

### Documentation (this feature)

```text
specs/004-configure-supabase/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── supabase-configuration.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
gradle/
└── libs.versions.toml                       # versões e aliases Supabase/Ktor/desugaring

app/
├── build.gradle.kts                         # resolução, validação, BuildConfig e dependências
└── src/main/
    ├── AndroidManifest.xml                  # permissão normal INTERNET
    └── java/com/trustarx/pitgo/core/network/
        └── SupabaseClientProvider.kt        # único cliente lazy por processo

local.properties                             # valores locais reais; já ignorado, nunca versionado
```

**Structure Decision**: manter o único módulo Android e criar apenas `core/network`, pois o cliente
será infraestrutura compartilhada por futuras features. O Gradle permanece a única fronteira que
conhece a origem dos valores; o runtime recebe campos já normalizados e validados. Um `object`
interno com `client` lazy garante identidade única por processo sem nova `Application`, framework
de DI ou inicialização artificial. Não haverá pacote de feature, Repository, modelo de API, UI ou
alteração de navegação porque nenhum fluxo de negócio acessa dados nesta entrega.

### Post-design constitution re-check

- `research.md` resolve versões, compatibilidade Android/Kotlin, origem e precedência dos valores,
  validação de chave/URL, escape dos campos, ownership do cliente e ausência deliberada de I/O.
- `data-model.md` separa input bruto, configuração validada e cliente runtime sem criar classes
  redundantes; entradas inválidas terminam no estado de build rejeitado e nunca chegam ao APK.
- O contrato fixa dois nomes case-sensitive, fontes local/CI, precedência, mensagens sem valores,
  outputs `BuildConfig` e invariantes de um cliente sem plugins.
- O quickstart cobre build debug/release, URL/chaves válidas e inválidas, precedência, trim,
  inspeção de vazamento, lançamento offline e regressão de Welcome/Home/RequestService.
- O design não adiciona Auth, PostgREST, Storage, Realtime, Functions, serialização no app,
  health check, Repository, DI, persistência, UI, logs de credencial ou permissão sensível.

**Resultado pós-design**: PASS; nenhuma violação constitucional ou complexidade que exija exceção
foi introduzida.

## Complexity Tracking

Nenhuma violação constitucional requer justificativa.
