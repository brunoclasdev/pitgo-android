# Implementation Plan: Catálogo de Serviços na Homepage

**Branch**: `001-home-service-catalog` | **Date**: 2026-08-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-home-service-catalog/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

Converter o template Android atual baseado em XML/JNI em um aplicativo Compose de módulo único e
entregar uma homepage offline com cinco categorias veiculares locais. A UI usa Material 3, grade
adaptável, tema claro/escuro, semântica acessível e ViewModel com StateFlow imutável. A seleção
envia somente o identificador estável da categoria ao destino mínimo de entrada da busca de
prestadores, com retorno padrão e proteção contra navegação duplicada.

Não serão adicionados Repository, Use Case, rede, persistência, DI, carregamento de imagens ou
testes. O plano também corrige a incompatibilidade preexistente entre `core-ktx 1.19.0` e
`compileSdk 36.1`, elevando apenas o SDK de compilação para 37, suportado pelo AGP atual.

## Technical Context

**Language/Version**: Kotlin 2.2.10 integrado ao AGP 9.1.1; bytecode Java/Kotlin 11; JDK 17 para o build

**Primary Dependencies**: Compose BOM 2026.06.01 (UI/Foundation 1.11.4, Material 3 1.4.0), Activity Compose 1.13.0, Lifecycle 2.10.0, Navigation Compose 2.9.8, Core KTX 1.19.0

**Storage**: N/A; catálogo imutável empacotado no aplicativo, sem banco, arquivo ou preferência

**Testing**: Nenhum novo teste automatizado conforme a constituição; validação por `assembleDebug`, `lintDebug` e roteiro manual do `quickstart.md`

**Target Platform**: Android, minSdk 24, targetSdk 36, compileSdk 37; smartphones, tablets e multi-window

**Project Type**: Aplicativo móvel Android nativo, módulo único `:app`

**Performance Goals**: Conteúdo útil visível em até 2 segundos; rolagem e interação sem travamentos perceptíveis; uma navegação por seleção

**Constraints**: Offline; sem autenticação, localização ou dados pessoais; Light/Dark pelo sistema; fonte ampliada; alvos de toque mínimos; sem dependência de cor; cinco categorias fixas

**Scale/Scope**: Uma homepage, cinco categorias, um destino mínimo de entrada da busca e nenhum dado de prestadores

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-design gate

- **PASS — Simplicidade/KISS/YAGNI**: apenas um módulo e as camadas necessárias; sem Repository,
  Use Case, DI, rede, persistência ou abstrações especulativas.
- **PASS — Stack e arquitetura**: Kotlin, Compose, Material 3, ViewModel, StateFlow e Navigation
  Compose; fluxo UI -> ViewModel para dados locais sem acesso externo.
- **PASS — Organização por feature**: homepage em `features/home`; tema e navegação realmente
  compartilhados em `core`.
- **PASS — Estado previsível**: `HomeUiState` imutável e sempre preenchido; Loading, Error e Empty
  não existem porque o catálogo local garante cinco itens.
- **PASS — UI/UX e acessibilidade**: componentes Material, grade adaptável, tema central, semântica,
  fonte ampliada, toque mínimo e identificação além de cor.
- **PASS — Segurança e dados**: a feature não solicita permissões, não coleta dados, não acessa
  rede e transmite somente um identificador interno de categoria.
- **PASS — Dependências**: somente bibliotecas Jetpack necessárias à stack constitucional; vetores
  locais evitam biblioteca de imagens ou pacote ampliado de ícones.
- **PASS — Testes**: nenhum teste será criado sem solicitação explícita; build, lint e validação
  manual verificam a entrega.
- **PASS — Escopo**: a fronteira de busca confirma categoria e retorno, sem simular prestadores,
  proximidade, solicitação, pagamento ou contratação.

### Post-design gate

- **PASS**: `research.md` resolve todas as decisões de stack e compatibilidade sem marcadores
  pendentes.
- **PASS**: `data-model.md` contém somente categoria, estado de UI e argumento efêmero de
  navegação; não introduz persistência.
- **PASS**: `contracts/home-navigation.md` mantém a UI stateless, rotas centralizadas, argumento
  mínimo, proteção contra toques repetidos e retorno padrão.
- **PASS**: `quickstart.md` valida requisitos funcionais, responsividade, acessibilidade, temas e
  funcionamento offline sem gerar testes.

**Gate result**: aprovado antes e depois do design; nenhuma exceção constitucional necessária.

## Project Structure

### Documentation (this feature)

```text
specs/001-home-service-catalog/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── home-navigation.md
└── tasks.md                    # Criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/trustarx/pitgo/
    │   ├── MainActivity.kt
    │   ├── core/
    │   │   ├── design/theme/
    │   │   │   ├── Color.kt
    │   │   │   ├── Theme.kt
    │   │   │   └── Type.kt
    │   │   └── navigation/
    │   │       ├── PitGoRoutes.kt
    │   │       └── PitGoNavHost.kt
    │   └── features/
    │       ├── home/
    │       │   ├── model/ServiceCategory.kt
    │       │   ├── ui/HomeScreen.kt
    │       │   └── viewmodel/HomeViewModel.kt
    │       └── provider/ui/ProviderSearchEntryScreen.kt
    └── res/
        ├── drawable/ic_service_*.xml
        ├── values/strings.xml
        ├── values/themes.xml
        └── values-night/themes.xml

gradle/libs.versions.toml
build.gradle.kts
```

**Structure Decision**: manter o único módulo `:app` e organizar código Kotlin por feature. A
implementação substitui o scaffold XML/JNI atual: remove ViewBinding, AppCompat, ConstraintLayout,
Material Views, `activity_main.xml`, `src/main/cpp`, cores XML obsoletas e testes de exemplo. Os
recursos de launcher, backup e extração de dados permanecem.

## Complexity Tracking

Nenhuma violação constitucional requer justificativa.
