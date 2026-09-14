# Implementation Plan: Aplicar identidade visual do app

**Branch**: `021-app-branding-assets` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

Adicionar a identidade visual fornecida ao aplicativo Android: substituir o artefato atual do
launcher pela arte de `pitgo-icon.png` dentro da área segura de ícones adaptativos e configurar
`pitgo-image1200.png` como imagem da splash. A splash será exibida até a inicialização estar
pronta, com duração mínima de 500 ms, preservando a imagem inteira centralizada e mantendo um
fallback seguro caso o recurso não possa ser carregado.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Kotlin/JVM 11

**Primary Dependencies**: Android SDK 37, Jetpack Compose, AndroidX Activity/Core; adicionar
AndroidX Core Splashscreen somente se necessário para compatibilidade do mesmo fluxo entre API 24+

**Storage**: N/A; os arquivos são recursos empacotados no APK

**Testing**: Compilação Gradle, instalação limpa/atualização e inspeção visual em dispositivos
Android conectados com pelo menos três densidades/tamanhos

**Target Platform**: Android API 24+, target SDK 36

**Project Type**: mobile-app Android nativo

**Performance Goals**: A splash deve sair assim que a inicialização estiver pronta, sem trabalho de
rede ou decodificação pesada adicional; a transição deve ocorrer em até 2 segundos após o app estar
pronto, respeitando 500 ms mínimos

**Constraints**: Preservar proporção e área segura do ícone; não distorcer nem cortar a splash;
preservar a inicialização, tema e navegação existentes; os dois PNGs fornecidos são dependências
obrigatórias de entrada

**Scale/Scope**: Um aplicativo, um ícone de launcher e uma splash compartilhados por cliente e
Pittec; sem alteração de backend ou dados

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

*GATE: PASS*

- A mudança é limitada ao requisito visual e reutiliza a estrutura Android/Compose existente.
- Não cria armazenamento, API, banco ou camadas de negócio novas.
- Os recursos permanecem no diretório de recursos do app e a UI continuará renderizando apenas
  estado de inicialização, sem acessar serviços diretamente.
- A solução preserva acessibilidade visual, proporção, fallback e compatibilidade com API 24+.
- A dependência AndroidX Core Splashscreen é a única adição considerada e só será usada porque o
  mecanismo nativo de splash varia entre versões Android; nenhuma dependência será adicionada se o
  projeto já oferecer equivalente funcional.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
app/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/trustarx/pitgo/MainActivity.kt
    └── res/
        ├── drawable*/              # imagem de splash e camadas da marca
        ├── mipmap*/                 # ícone do launcher por densidade
        ├── values/themes.xml        # tema de inicialização
        └── values-v31/              # atributos de splash para Android 12+
```

**Structure Decision**: Manter a organização do único módulo Android existente. Os artefatos de
launcher ficam em `res/mipmap*`/`mipmap-anydpi-v26`, a imagem de splash em `res/drawable*`, e a
integração de inicialização permanece em `MainActivity` e nos temas/manifesto existentes.

## Complexity Tracking

Nenhuma violação da constituição identificada. A dependência de splash compatível só será incluída
se a API nativa não cobrir o requisito de API 24+ sem duplicar o fluxo.
