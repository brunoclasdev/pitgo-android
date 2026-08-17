# Implementation Plan: Layouts iniciais do PitGo

**Branch**: `002-initial-layouts` | **Date**: 2026-08-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-initial-layouts/spec.md`

## Summary

Substituir a homepage da feature anterior por uma experiência local de duas telas: uma Welcome
rolável com identidade roxa fixa e uma Home responsiva com dados demonstrativos, seis serviços em
duas colunas e barra inferior Material fixa. O fluxo ativo será apenas Welcome → Home; todos os
demais controles fornecerão feedback Material sem mudar de destino. A implementação reutiliza o
módulo Compose, o design system e as dependências atuais, mantém MVVM com estados imutáveis em
`StateFlow` e remove o fluxo de prestadores que se tornou incompatível com a nova especificação.

## Technical Context

**Language/Version**: Kotlin integrado 2.2.10, bytecode Java/Kotlin 11, JDK 17 para o build

**Primary Dependencies**: Android Gradle Plugin 9.1.1, Compose Compiler 2.2.10, Compose BOM
2026.06.01, Material 3, Activity Compose 1.13.0, Lifecycle Compose 2.10.0, Navigation Compose
2.9.8 e Core KTX 1.19.0

**Storage**: N/A; todo o conteúdo é local, fixo e não persistido

**Testing**: build e lint via Gradle, seguidos pelo roteiro manual de `quickstart.md`; nenhum teste
automatizado será criado

**Target Platform**: aplicativo Android nativo, minSdk 24, targetSdk 36 e compileSdk 37

**Project Type**: aplicativo mobile Android de módulo único

**Performance Goals**: conteúdo útil da Welcome em até 2 segundos em condições normais e
interações/rolagem visualmente fluidas em smartphones suportados

**Constraints**: telas Compose-only sem XML de layout, Material Design, MVVM, offline, sem backend,
Supabase, rede, autenticação, localização, persistência, DI, imagens remotas, telas adicionais ou
novas dependências

**Scale/Scope**: dois destinos, três benefícios, um perfil/veículo/endereço demonstrativo, um card
de diagnóstico, seis serviços e cinco itens de navegação inferior

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Gate evaluation before research

- **Simplicidade/YAGNI — PASS**: a solução mantém um único módulo e não adiciona Repository, Use
  Case, DI, rede, persistência, backend ou destinos sem comportamento solicitado.
- **Android nativo e MVVM — PASS**: Kotlin, Compose, Material 3, ViewModel, `StateFlow`, coleta
  lifecycle-aware e Navigation Compose já estão disponíveis. Welcome e Home expõem estado
  imutável e telas stateless; nenhuma UI acessa dados externos.
- **Organização por feature — PASS**: Welcome recebe seu próprio pacote; Home reutiliza o pacote
  existente; design e navegação continuam em `core`.
- **Estado previsível — PASS**: dados fixos entram imediatamente em estados de sucesso. Loading,
  Error e Empty não são modelados porque não podem ocorrer sem fonte externa.
- **Design system e Material — PASS**: cores, tipografia, formas, espaçamento e elevação são
  centralizados; Cards, Buttons, Scaffold e NavigationBar oficiais fornecem interação e feedback.
- **Temas e acessibilidade — PASS**: Welcome conserva o gradiente nos dois modos; Home usa os
  esquemas claro/escuro do sistema. Conteúdo rola, aceita fonte ampliada, respeita insets, possui
  alvos mínimos e não comunica estado somente por cor.
- **Navegação — PASS**: rotas ficam centralizadas; `dropUnlessResumed`, proteção local reiniciada
  ao retomar Welcome e `launchSingleTop` evitam duplicação sem bloquear uma entrada posterior; o
  back stack padrão implementa Home → Welcome → saída.
- **Dados e segurança — PASS**: somente dados demonstrativos não sensíveis; sem permissões,
  credenciais, logs sensíveis ou integrações.
- **Qualidade proporcional — PASS**: não serão criados testes por proibição explícita do usuário e
  da constituição; build, lint e validação manual são proporcionais ao risco visual.

O app já seleciona Light/Dark conforme o sistema. A feature não cria seletor manual ou DataStore,
pois a especificação explicitamente limita o escopo e não remove nenhum comportamento existente;
essa dívida global não é ampliada por este plano.

## Project Structure

### Documentation (this feature)

```text
specs/002-initial-layouts/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── initial-ui-navigation.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/trustarx/pitgo/
│   ├── MainActivity.kt                       # reutiliza edge-to-edge existente
│   ├── core/
│   │   ├── design/theme/
│   │   │   ├── Color.kt                      # atualizar identidade e esquemas
│   │   │   ├── Dimensions.kt                 # estender somente tokens repetidos
│   │   │   ├── Shape.kt                      # reutilizar/ajustar raios necessários
│   │   │   ├── Theme.kt                      # Home claro/escuro do sistema
│   │   │   └── Type.kt                       # estender estilos realmente usados
│   │   └── navigation/
│   │       ├── PitGoRoutes.kt                 # somente Welcome e Home
│   │       └── PitGoNavHost.kt                # Welcome inicial e fluxo Entrar
│   └── features/
│       ├── welcome/
│       │   ├── model/WelcomeBenefit.kt
│       │   ├── ui/WelcomeScreen.kt
│       │   └── viewmodel/WelcomeViewModel.kt
│       └── home/
│           ├── model/HomeContent.kt           # substitui ServiceCategory.kt
│           ├── ui/HomeScreen.kt               # substitui o layout anterior
│           └── viewmodel/HomeViewModel.kt
├── res/
│   ├── drawable/ic_*.xml                      # vetores locais necessários
│   └── values/strings.xml                     # todo texto visível/acessível
└── ...

# Removido por ter sido supersedido:
app/src/main/java/com/trustarx/pitgo/features/provider/ui/ProviderSearchEntryScreen.kt
app/src/main/java/com/trustarx/pitgo/features/home/model/ServiceCategory.kt
```

**Structure Decision**: manter o aplicativo single-module e feature-first. `WelcomeViewModel`
representa a lista ordenada de benefícios exigida pelo fluxo e `HomeViewModel` agrega todo o
conteúdo demonstrativo da Home; ambos expõem um único estado imutável. Não haverá camadas `data`,
`repository` ou `usecase`, pois não existe fonte de dados nem regra de negócio. Componentes como
FeatureCard, VehicleCard, DiagnosticCard, ServiceCard e a barra inferior permanecem privados à
tela responsável até existir reutilização real.

### Post-design constitution re-check

- `research.md` resolve todas as escolhas técnicas sem novas dependências ou clarificações.
- `data-model.md` contém somente entidades demonstrativas e invariantes exigidos.
- O contrato de UI mantém telas desacopladas do `NavController`, rotas centralizadas e argumentos
  inexistentes.
- O quickstart cobre build, lint, conteúdo offline, responsividade, temas, acessibilidade,
  feedback, barras do sistema, proteção contra duplicação/reentrada e back stack.
- Nenhum artefato adiciona backend, Repository, persistência, autenticação, localização, testes,
  placeholder ou funcionalidade futura.

**Resultado pós-design**: PASS; nenhuma violação constitucional nova ou complexidade que exija
exceção foi introduzida.
