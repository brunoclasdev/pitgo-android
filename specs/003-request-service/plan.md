# Implementation Plan: Solicitar Atendimento

**Branch**: `003-request-service` | **Date**: 2026-08-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-request-service/spec.md`

## Summary

Conectar os seis cards de serviço da Home a uma nova tela de solicitação que recebe somente o
`serviceId`, resolve localmente nome, ícone e preço e apresenta veículo, endereço, descrição
opcional, estimativas, pagamento exclusivo e uma ação simulada protegida contra repetição. A
implementação mantém um único módulo Android, extrai apenas os dados e a barra inferior agora
reutilizados por duas features, usa estado imutável em `StateFlow`, navegação centralizada e uma UI
Material rolável entre top bar e bottom navigation fixas, sem Repository, backend, persistência,
permissões, novas dependências ou testes automatizados.

## Technical Context

**Language/Version**: Kotlin integrado 2.2.10, bytecode Java/Kotlin 11, JDK 17 para o build

**Primary Dependencies**: Android Gradle Plugin 9.1.1, Compose Compiler 2.2.10, Compose BOM
2026.06.01, Material 3, Compose Foundation/UI, Activity Compose 1.13.0, Lifecycle Runtime/ViewModel
Compose 2.10.0, Navigation Compose 2.9.8 e Core KTX 1.19.0

**Storage**: N/A; catálogo, veículo, endereço e estimativas são locais, temporários e não
persistidos

**Testing**: build e lint via Gradle, seguidos pelo roteiro manual de `quickstart.md`; nenhum teste
automatizado será criado

**Target Platform**: aplicativo Android nativo, minSdk 24, targetSdk 36 e compileSdk 37

**Project Type**: aplicativo mobile Android de módulo único

**Performance Goals**: conteúdo local disponível na primeira renderização do destino; rolagem,
seleção e edição visualmente fluidas a 60 fps em smartphones suportados; um toque aceito produz
feedback no mesmo ciclo de interação

**Constraints**: UI Compose-only sem XML de layout, Material Design, MVVM, tema existente,
navegação inferior fixa, conteúdo responsivo e acessível, operação offline, sem backend,
Supabase, rede, autenticação adicional, localização, persistência, cobrança, Repository, Use Case,
DI, testes ou dependências novas

**Scale/Scope**: um novo destino, seis IDs de serviço, um veículo/endereço demonstrativo, três
formas de pagamento, dois estados raiz de tela, dois estados de acionamento e cinco itens
demonstrativos de navegação inferior

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Gate evaluation before research

- **Simplicidade e YAGNI — PASS**: a solução adiciona somente a tela, estado, rota, recursos e
  extrações exigidas por reutilização real. Não adiciona backend, Repository, Use Case, DI,
  persistência, deep link, destinos placeholder ou infraestrutura futura.
- **Android nativo e MVVM — PASS**: Kotlin, Compose, Material 3, ViewModel, `StateFlow`, coleta
  lifecycle-aware e Navigation Compose já estão disponíveis. A tela renderiza estado e emite
  eventos; o ViewModel resolve dados locais e controla descrição, pagamento e acionamento.
- **Organização por feature — PASS**: `features/requestservice` contém apenas `model`, `ui` e
  `viewmodel`; catálogo/veículo e bottom navigation são movidos para áreas `core` somente porque
  passam a ter dois consumidores reais.
- **Estado previsível — PASS**: um tipo selado separa conteúdo válido de serviço inválido;
  pagamento e acionamento usam enums exclusivos. Loading e Empty não são inventados porque não há
  I/O nem coleção vazia válida.
- **Design system e Material — PASS**: `Scaffold`, `TopAppBar`, `Card`, `OutlinedTextField`,
  `Button`, `NavigationBar` e superfícies selecionáveis preservam comportamento Material. Tokens
  compartilhados existentes são reutilizados e componentes de uso único ficam privados.
- **Tema, responsividade e acessibilidade — PASS**: a tela usa os papéis Light/Dark existentes,
  respeita safe drawing/IME, permite rolagem, quebra de linha e fonte ampliada, mantém alvos
  mínimos e representa pagamento selecionado por semântica e indicador visual além da cor.
- **Navegação — PASS**: rota e argumento ficam centralizados; somente `serviceId` é transportado;
  `launchSingleTop`, guarda de navegação e back stack padrão evitam duplicação e preservam Home no
  retorno. Itens inferiores mantêm o comportamento demonstrativo atual, sem rotas novas.
- **Dados e segurança — PASS**: somente dados mockados não sensíveis são usados; não há cartão,
  permissão, rede, segredo, log sensível ou criação de atendimento. ID ausente/inválido nunca
  recebe preço de fallback.
- **Qualidade proporcional — PASS**: nenhum teste é criado por proibição explícita do usuário e da
  constituição; build, lint e roteiro manual cobrem o risco visual, de estado e de navegação.

O seletor persistido Light/Dark/System Default previsto globalmente pela constituição ainda não
existe no projeto. Esta feature preserva o tema de sistema já adotado e não amplia essa dívida; a
criação de preferências de tema permanece fora do escopo funcional atual.

## Project Structure

### Documentation (this feature)

```text
specs/003-request-service/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── request-service-navigation.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/trustarx/pitgo/
│   ├── core/
│   │   ├── common/model/
│   │   │   ├── DemoServiceCatalog.kt        # extrair ID, modelo e catálogo dos serviços
│   │   │   └── DemoVehicle.kt               # extrair veículo/endereço demonstrativo
│   │   ├── navigation/
│   │   │   ├── PitGoRoutes.kt               # padrão, argumento e builder da nova rota
│   │   │   └── PitGoNavHost.kt              # destino, callbacks, back e proteção de navegação
│   │   └── ui/
│   │       └── PitGoBottomNavigation.kt      # extrair modelo/dados/composable já reutilizados
│   └── features/
│       ├── home/
│       │   ├── model/HomeContent.kt          # manter somente conteúdo exclusivo da Home
│       │   ├── ui/HomeScreen.kt              # propagar seleção real de serviço e usar barra comum
│       │   └── viewmodel/HomeViewModel.kt    # atualizar imports dos modelos compartilhados
│       └── requestservice/
│           ├── model/RequestServiceContent.kt
│           ├── ui/RequestServiceScreen.kt
│           └── viewmodel/RequestServiceViewModel.kt
├── res/
│   ├── drawable/
│   │   ├── ic_request_back.xml
│   │   ├── ic_request_check.xml
│   │   └── ic_request_clock.xml
│   └── values/strings.xml                    # textos visíveis, erro e acessibilidade
└── ...
```

**Structure Decision**: manter o aplicativo single-module e feature-first. A nova feature possui
uma tela stateless, um ViewModel e tipos de estado locais. `DemoServiceCatalog`, `DemoVehicle` e
`PitGoBottomNavigation` deixam de pertencer exclusivamente à Home porque passam a ter dois
consumidores; a extração elimina dependência `requestservice → home` e mantém uma fonte única de
preço, veículo e barra. Não haverá `data`, Repository ou Use Case, pois não existe fonte externa,
persistência ou regra de domínio que justifique essas camadas.

### Post-design constitution re-check

- `research.md` resolve todas as decisões técnicas sem pendências de esclarecimento e sem
  dependências novas.
- `data-model.md` usa uma única fonte para catálogo/preço e modela conteúdo válido, ID inválido,
  pagamento exclusivo e acionamento único sem estados booleanos conflitantes.
- O contrato passa somente `serviceId`, mantém telas desacopladas de `NavController`, define back
  seguro e preserva explicitamente a bottom navigation demonstrativa da Home.
- O design mantém uma única rolagem entre barras fixas, adapta estimativas à largura/fonte, usa
  semântica de seleção e evita alturas rígidas que cortem texto.
- O quickstart cobre build, lint, seis serviços/preços, erro de ID, edição/IME, pagamento,
  acionamento único, back stack, barra fixa, temas, fonte ampliada, TalkBack e operação offline.
- Nenhum artefato introduz backend, pagamento, geolocalização, persistência, telas placeholder,
  teste, log sensível ou abstração especulativa.

**Resultado pós-design**: PASS; nenhuma violação constitucional ou complexidade que exija exceção
foi introduzida.
