# Implementation Plan: Experiência mockada de UI do PitTec

**Branch**: `017-pittec-mocked-ui` | **Date**: 2026-09-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/017-pittec-mocked-ui/spec.md`

## Summary

Entregar um protótipo navegável e inteiramente local da jornada PitTec. No perfil prestador, a
Home passa a ter mapa com a localização real do aparelho, solicitação destacada por três pulsos
roxos, aceite/recusa, acompanhamento e navegação inferior com Mapa, Dados da corrida, Valores e
Perfil. No perfil cliente, a tela existente permanece igual até “Buscar PitTec”; depois exibe uma
estimativa de R$ 50,00 mais R$ 10,00 por faixa de 5 km, confirmação, busca sequencial simulada,
acompanhamento e confirmações bilaterais simuladas.

A implementação mantém autenticação, catálogo e mapa existentes, mas isola o protótipo dos RPCs,
Realtime, tracking publicado, rotas externas, notificações e pagamentos. Dois ViewModels
independentes, escopados ao host de navegação e identificados pela sessão autenticada, mantêm as
máquinas de estado somente em memória. Coroutines controlam as respostas automáticas de 3 s, a
posição simulada de 30 s e a indicação de desatualização após 35 s. O mapa reutiliza MapLibre e os
modelos atuais; a câmera recebe comando apenas na abertura do acompanhamento e no botão
“Centralizar”, preservando qualquer exploração manual.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11 bytecode, Android Gradle Plugin 9.1.1

**Primary Dependencies**: Jetpack Compose BOM 2026.06.01, Material 3, Navigation Compose 2.9.8,
Lifecycle/ViewModel/StateFlow 2.10.0, Kotlin Coroutines e MapLibre Native Android 13.5.0; nenhuma
dependência nova

**Storage**: nenhum armazenamento novo; estado mockado exclusivamente em memória nos ViewModels,
sem Supabase, Room, DataStore ou `SavedStateHandle`

**Testing**: validação manual orientada pelos cenários, `:app:assembleDebug` e `:app:lintDebug`;
nenhum teste automatizado será criado porque não foi solicitado

**Target Platform**: Android nativo, minSdk 24, compileSdk 37, targetSdk 36

**Project Type**: aplicativo Android single-module organizado por feature

**Performance Goals**: Home PitTec pronta em até 3 s quando houver fix recente; aceite abre o
acompanhamento em até 2 s; exatamente uma atualização simulada por intervalo de 28–32 s enquanto
o mapa do atendimento estiver ativo; resposta automática bilateral em aproximadamente 3 s; mapa
permanece responsivo a 60 fps nos aparelhos suportados

**Constraints**: posição inicial do prestador deve ser real e precisa; sem fallback de localização
mockada na Home do prestador; três pulsos em no máximo 3 s com variante estática quando a escala
de animação for zero; preço em centavos inteiros; perímetros múltiplos de 5 km; stale em 35 s;
fluxos cliente/prestador independentes; nenhum efeito real de despacho, publicação de posição,
notificação, rota, pagamento ou persistência após morte do processo

**Scale/Scope**: duas jornadas mockadas por perfil, um shell PitTec com quatro abas, uma máquina de
estado de busca, uma máquina de atendimento compartilhável na UI, fixtures determinísticas para
5/10/15 km e busca vazia, sem alterações em banco ou serviços externos

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação pré-pesquisa | Verificação pós-design |
|---|---|---|
| I. Simplicidade e entrega orientada ao requisito | PASS — protótipo local, sem backend novo, feature flag remota ou persistência especulativa. | PASS — dois ViewModels independentes e componentes stateless cobrem apenas os fluxos solicitados; fixtures e temporizadores têm uso atual. |
| II. Android nativo moderno com MVVM | PASS — Kotlin, Compose, Material 3, Navigation Compose, ViewModel, StateFlow e Coroutines já são o padrão do app. | PASS — UI emite eventos, ViewModels controlam estado; não há Repository porque não existe acesso a dados externo/persistente nesta feature. |
| III. Estado previsível e experiência consistente | PASS — estados exclusivos, navegação inferior Material e feedback textual estão previstos. | PASS — contratos definem máquinas de estado, idempotência, live regions, touch targets, rolagem e alternativa sem movimento. |
| IV. Dados, segurança e falhas explícitas | PASS — nenhum segredo ou dado externo novo; permissão, GPS, timeout e ausência de fix são explícitos. | PASS — o desenho desconecta restauração, tracking, alertas e despacho reais; valores usam centavos e localização não é registrada nem publicada. |
| V. Qualidade proporcional e evolução sustentável | PASS — reutiliza mapa, localização e design system; sem dependência ou teste não solicitado. | PASS — o fluxo real da feature 015 fica preservado, porém sem wiring no protótipo; quickstart cobre build, lint, acessibilidade e não regressão. |

Não há violação constitucional a justificar.

## Design Decisions

1. O protótipo usa `PitTecClientMockViewModel` e `PitTecProviderMockViewModel` independentes. Eles
   são escopados ao owner do `PitGoNavHost`, com chave derivada da sessão, para sobreviver à
   navegação interna e serem descartados na morte do processo. Não há sincronização entre perfis.
2. A rota cliente `pittec/{serviceId}` deixa de criar o ViewModel/repositório real e não navega
   para `AppointmentTracking`. O ViewModel de retomada PitTec e seu redirecionamento global deixam
   de ser compostos durante o protótipo; as classes da feature 015 permanecem no código.
3. Para prestadores cujo modo efetivo é `PITTEC`, `ProviderDashboard` renderiza um shell mockado e
   não cria `ProviderDashboardViewModel`. FIXED e MOBILE continuam no dashboard real atual.
4. O watchdog global passa a iniciar tracking somente para MOBILE. Uma parada exclusivamente local
   encerra eventual serviço PitTec legado e seus alertas sem chamar Supabase nem mudar a
   disponibilidade remota.
5. A localização real do prestador é lida por `DeviceLocationRepository` somente enquanto a Home
   mockada está visível. O mapa não é composto antes do estado `Ready`; permissão, precisão
   insuficiente, serviços desligados e timeout têm telas e ações próprias.
6. A busca do cliente usa fixtures determinísticas com candidatos ordenados. A fixture principal
   recusa candidatos em 5 e 10 km e aceita em 15 km; uma tentativa posterior cobre esgotamento.
   Cada expansão atualiza imediatamente a composição do valor sem criar nova busca ou exigir nova
   confirmação, pois a spec exige continuidade automática da mesma busca.
7. Dinheiro é representado em centavos: `5000 + 1000 × (perimeterKm / 5)`. Distância do mapa é uma
   estimativa local entre pontos simulados; não existe cálculo de rota, cobrança ou saldo.
8. Após o aceite, a primeira câmera enquadra os dois participantes. Ticks de posição alteram apenas
   marcador, distância e timestamp; somente “Centralizar” gera novo `MapViewportCommand`.
9. A animação é um efeito visual finito, consumido uma única vez por revisão da solicitação. Se
   `MotionDurationScale.scaleFactor == 0`, a UI pula o efeito e mostra diretamente o card roxo
   persistente, sempre com texto e botões acessíveis.

Detalhes, fontes e alternativas estão em [research.md](research.md).

## Project Structure

### Documentation (this feature)

```text
specs/017-pittec-mocked-ui/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── client-pittec-ui.md
│   ├── mock-appointment-state.md
│   └── provider-pittec-ui.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/trustarx/pitgo/
│   ├── core/
│   │   ├── map/
│   │   │   ├── model/MapSurfaceModels.kt      # reutiliza framing/markers/commands
│   │   │   └── ui/
│   │   │       ├── MapLibreMapHost.kt         # reutilizado sem nova câmera automática
│   │   │       └── ParticipantMapStyleController.kt # promove controller já usado no atendimento
│   │   ├── navigation/PitGoNavHost.kt          # wiring mock e isolamento do fluxo real
│   │   └── ui/PitGoBottomNavigation.kt         # itens específicos do PitTec
│   └── features/
│       ├── appointmenttracking/ui/
│       │   └── AppointmentTrackingScreen.kt    # troca somente o import do controller promovido
│       ├── pittec/
│       │   ├── model/
│       │   │   ├── PitTecModels.kt             # contratos reais preservados
│       │   │   └── PitTecMockModels.kt         # estados/fixtures/valor/atendimento locais
│       │   ├── ui/
│       │   │   ├── PitTecScreen.kt             # apresentação inicial preservada + fluxo mock cliente
│       │   │   ├── PitTecMockComponents.kt     # estimativa, busca e confirmações stateless
│       │   │   ├── PitTecMockTrackingContent.kt# mapa/card compartilhado pelos perfis
│       │   │   └── PitTecProviderDashboardScreen.kt # shell de quatro abas
│       │   └── viewmodel/
│       │       ├── PitTecClientMockViewModel.kt
│       │       └── PitTecProviderMockViewModel.kt
│       └── providertracking/
│           ├── repository/ProviderTrackingStateRepository.kt # parada local sem backend
│           └── service/ProviderLocationTrackingService.kt     # nova ação local-only
└── res/
    └── values/strings.xml                     # textos de estados, preços, abas e acessibilidade
```

**Structure Decision**: manter o módulo único e concentrar as duas experiências na feature
`pittec`, que representa o mesmo produto nos dois perfis. O dashboard legado continua responsável
por FIXED/MOBILE. O controller de mapa de participantes é promovido para `core/map/ui` porque terá
dois consumidores reais e uma responsabilidade transversal clara. Nenhuma pasta `data` ou
`repository` é criada para o mock; as fixtures são valores de domínio locais e o estado permanece
nos ViewModels.

## Delivery Phases

### Phase 0 — Research

- Confirmar como impedir qualquer chamada real sem apagar a implementação da feature 015.
- Definir escopo de ViewModel que preserve estado em navegação, mas não após process death.
- Definir localização precisa, retorno dos Ajustes e bloqueio seguro do mapa.
- Definir reduced motion, jobs de 3/30/35 s e política de câmera.
- Registrar decisões e alternativas em `research.md`.

### Phase 1 — Data and UI contracts

- Descrever modelos, invariantes, fixtures e transições em `data-model.md`.
- Contratar a jornada do cliente em `contracts/client-pittec-ui.md`.
- Contratar Home, alerta e abas do prestador em `contracts/provider-pittec-ui.md`.
- Contratar acompanhamento, câmera, atualidade e confirmação bilateral em
  `contracts/mock-appointment-state.md`.
- Documentar build, cenários manuais, acessibilidade e prova de ausência de efeitos reais em
  `quickstart.md`.

### Phase 2 — Execution preparation

- Executar `$speckit-tasks` para decompor este desenho em tarefas pequenas e ordenadas.
- Implementar primeiro isolamento/wiring, depois modelos/ViewModels e por fim telas e validação.

## Implementation Sequence

1. Isolar no `PitGoNavHost` a experiência mockada, remover a retomada real do PitTec e criar a
   parada local-only do tracking/alerta legado para modo PITTEC.
2. Adicionar modelos imutáveis, cálculo em centavos, fixtures e máquinas de estado mockadas.
3. Criar ViewModels host-scoped com guards idempotentes e jobs canceláveis de 3/30/35 s.
4. Adaptar a tela cliente mantendo o estado inicial, e adicionar estimativa, confirmação, busca,
   recusa/expansão, vazio e acompanhamento.
5. Criar shell prestador com localização real bloqueante, alerta roxo, aceite/recusa e as quatro
   áreas da navegação inferior.
6. Reutilizar/promover o mapa de participantes, implementar câmera inicial/recentralização e
   conteúdo textual equivalente.
7. Validar light/dark, escala de fonte, leitor de tela, animações desligadas, idempotência, timers,
   ausência de chamadas de negócio e não regressão de FIXED/MOBILE.

## Rollout and rollback

- Não há migração, deploy de função, segredo, configuração remota ou ordem de rollout de backend.
- A entrega pode ser revertida apenas pelo APK: o wiring volta ao fluxo real da feature 015 e os
  modelos/repositórios preservados continuam disponíveis.
- Antes de liberar o protótipo, confirmar no inspetor de rede que suas ações não chamam RPCs de
  descoberta/despacho, Realtime, publicação de localização, rota ou pagamento.

## Complexity Tracking

*Sem violações da constituição a justificar.*
