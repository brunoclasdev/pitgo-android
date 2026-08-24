# Implementation Plan: Melhoria de UX das telas do cliente

**Branch**: `014-client-ux-improvements` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-client-ux-improvements/spec.md`

## Summary

Seis melhorias de UX no lado cliente/prestador do PitGo, priorizadas pelo atrito real observado
em uso: (1) feedback claro (contagem regressiva viva, botões com estado de carregamento, sem
"0,0 km") durante a busca/solicitação de guincho; (2) mapa do cliente centralizado na própria
posição com o guincho se movendo em tempo real; (3) mapa do prestador, hoje inexistente, mostrando
sua posição e a do cliente enquadradas e atualizando em tempo real; (4) correção do botão voltar,
que hoje é sobrescrito pela navegação automática de retomada de atendimento ativo; (5) Home
acionável (endereço e veículo viram ações diretas); (6) Perfil completo (endereço, veículo padrão,
seleção de aparência Claro/Escuro/Sistema). Abordagem técnica: reaproveitar a infraestrutura de
realtime e tracking já existente (features 013 e correções de sessão já aplicadas), adicionar um
mapa na tela de atendimento do prestador seguindo o mesmo padrão de `AppointmentTrackingScreen`,
capturar a posição do cliente via leitura pontual de GPS (sem novo serviço em segundo plano, por
decisão de `/speckit-clarify`), corrigir a condição da navegação automática para não reagir a um
"voltar" manual, e completar Home/Perfil com ações e uma nova preferência de aparência em
DataStore. A pesquisa (Phase 0) corrigiu uma suposição do spec: "endereço do cliente" **não**
existe hoje como dado persistido (a Home só renderiza um texto estático) — esta feature inclui uma
nova tabela mínima `client_addresses` + duas RPCs, reaproveitando o padrão de validação e a busca
de CEP já usados no cadastro de endereço do prestador. Em compensação, a pesquisa também confirmou
que parte do escopo já está implementada (contagem regressiva, busca esgotada, Home sem veículo),
reduzindo o trabalho real de código nessas frentes a apenas verificação de não regressão.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (Android Gradle Plugin 9.1.1, compileSdk 37)

**Primary Dependencies**: Jetpack Compose (BOM 2026.06.01) + Material 3, Navigation Compose
2.9.8, Lifecycle/ViewModel/StateFlow 2.10.0, Supabase-kt 3.2.6 (Auth, Postgrest, Realtime,
Functions), MapLibre Android SDK OpenGL (estilo gerenciado já configurado), DataStore
Preferences (já usado para outras preferências simples do app)

**Storage**: Supabase Postgres hospedado — reaproveita RPCs `SECURITY DEFINER`/RLS/Realtime
broadcast já existentes das features 009/013; adiciona uma tabela mínima nova,
`public.client_addresses` (uma linha por cliente), com duas RPCs próprias, no mesmo padrão de
autorização já estabelecido. DataStore local para a nova preferência de aparência e para
preferências técnicas já existentes (ex. `ProviderTrackingPreferences`)

**Testing**: Nenhum teste automatizado será criado nesta feature, salvo pedido explícito
(constituição do produto, princípio V)

**Target Platform**: Android nativo, minSdk 24, targetSdk 36

**Project Type**: Mobile app single-module (Android), organizado por feature conforme constituição

**Performance Goals**: Contagem regressiva do FR-001 atualizada a cada 1s (já existe um ticker
local, precisa só corrigir o binding visual); posição do guincho nos mapas refletindo a cadência
já estabelecida pela infraestrutura existente (~10s de publicação, visível em até ~15s, conforme
`specs/013-mobile-provider-dispatch/quickstart.md`); ações de Home/Perfil (endereço, veículo,
tema) em no máximo 2 toques (SC-003, SC-004)

**Constraints**: Nenhuma nova infraestrutura de rastreamento em segundo plano no dispositivo do
cliente (decisão de clarificação — FR-012); nenhuma mudança de identidade visual (paleta,
tipografia); reaproveitar componentes/telas existentes (`AppointmentTrackingScreen`,
`ProviderIncomingRequestScreen`, `MapLibreMapHost`, `DeviceLocationRepository`) em vez de recriar;
preservar comportamento funcional já existente de autenticação, logout, cadastro e despacho
(FR-010)

**Scale/Scope**: 1 app Android, ~7 telas tocadas (Home, Perfil, tela de acompanhamento do
cliente, tela de atendimento do prestador, mais os ViewModels/rotas associados), 6 user stories,
18 requisitos funcionais

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Simplicidade e entrega orientada ao requisito | PASS — reaproveita infraestrutura/telas existentes (mapa, realtime, DeviceLocationRepository, DataStore); a única estrutura nova (tabela `client_addresses` + feature `clientaddress`) tem uso atual demonstrável (endereço do cliente não existe hoje, per research.md #1) e reaproveita validação/CEP lookup/padrão de RPC já estabelecidos em vez de criar um subsistema novo |
| II. Android nativo moderno com MVVM | PASS — Compose + ViewModel + StateFlow + Navigation Compose já são o padrão do projeto; nenhuma nova dependência externa necessária |
| III. Estado previsível e experiência consistente | PASS — todos os FRs reforçam estados explícitos (loading/erro/vazio/sucesso), hierarquia clara e feedback imediato, alinhados ao princípio; nenhuma nova cor/token fora do design system |
| IV. Dados, segurança e falhas explícitas | PASS — nenhuma nova exposição de dado sensível; posição do cliente ao prestador só ocorre em atendimento aceito (mesmo modelo de autorização já usado por `provider_current_locations`); localização segue padrão de solicitar no momento de uso e tratar recusa/indisponibilidade |
| V. Qualidade proporcional e evolução sustentável | PASS — sem testes automatizados não solicitados; mudanças cirúrgicas nos arquivos existentes, sem refatoração ampla fora de escopo |

Nenhuma violação identificada. Complexity Tracking não se aplica (tabela deixada vazia).

**Re-check pós-Phase 1 (design)**: `data-model.md` e `contracts/` confirmam que a única estrutura
nova é `client_addresses` (1 tabela, 2 RPCs, RLS no mesmo padrão já estabelecido) e uma preferência
local (`AppearanceMode`); nenhuma tabela/RLS/RPC das features 009/011/013 é alterada, só lida
(posição do prestador). Os contratos de mapa/navegação (`contracts/appointment-map-and-navigation.md`)
descrevem apenas comportamento (câmera, comparação de `revision` já existente), sem novo protocolo
de rede. Nenhuma violação nova surgiu do desenho; gate segue PASS.

## Project Structure

### Documentation (this feature)

```text
specs/014-client-ux-improvements/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)

```text
supabase/migrations/
├── 20260823090000_client_addresses.sql         # NOVO: tabela + get/upsert_my_client_address RPCs
└── 20260823100000_client_current_locations.sql # NOVO: tabela + publish/get_appointment_client_location

app/src/main/java/com/trustarx/pitgo/
├── core/
│   ├── address/
│   │   └── repository/ViaCepAddressLookupRepository.kt  # MOVIDO de providerregistration (compartilhado)
│   ├── preferences/
│   │   └── AppearancePreferences.kt          # NOVO: DataStore Light/Dark/System
│   ├── design/theme/Theme.kt                  # sem mudança de tokens; MainActivity passa o override
│   ├── location/repository/DeviceLocationRepository.kt   # reaproveitado (leitura pontual)
│   ├── map/ui/MapLibreMapHost.kt              # reaproveitado (mapa do prestador)
│   └── navigation/PitGoNavHost.kt             # ajuste: condição do redirecionamento automático
├── MainActivity.kt                             # ajuste: lê AppearancePreferences, repassa a PitGoTheme
├── features/
│   ├── clientaddress/                          # NOVO: form de endereço do cliente (reaproveita CEP lookup)
│   │   ├── repository/ClientAddressRepository.kt
│   │   ├── viewmodel/ClientAddressViewModel.kt
│   │   └── ui/ClientAddressScreen.kt
│   ├── home/
│   │   └── ui/HomeScreen.kt                   # ajuste: linha de endereço vira ação tocável
│   ├── profile/
│   │   ├── ui/ProfileScreen.kt                 # ajuste: seções de endereço, veículo, aparência
│   │   └── viewmodel/ProfileViewModel.kt       # NOVO: agrega endereço/veículo/aparência
│   ├── appointmenttracking/
│   │   ├── repository/AppointmentTrackingRepository.kt # ajuste: publica fix do cliente, snapshot combinado
│   │   ├── ui/AppointmentTrackingScreen.kt    # ajuste: centralizar cliente + guincho
│   │   └── viewmodel/AppointmentTrackingViewModel.kt  # ajuste: câmera dupla, fix pontual
│   └── mobiledispatch/
│       ├── ui/ClientMobileDispatchScreen.kt   # ajuste: loading inline (FR-002) e distância (FR-003)
│       ├── ui/ProviderIncomingRequestScreen.kt # ajuste: loading inline + NOVO mapa (FR-013..015)
│       └── viewmodel/ProviderIncomingRequestViewModel.kt # ajuste: consome AppointmentTrackingRepository p/ posição do cliente
```

**Correção de desenho (Phase 1)**: a leitura pontual de GPS do cliente não pode viver só em
memória do dispositivo do cliente — para o prestador vê-la (US3), ela precisa atravessar o
backend. `AppointmentTrackingRepository` (já existente, hoje só lê `provider_current_locations`
via `appointment-location:<id>`) passa a também publicar (`publish_client_location`) e ler
(`get_appointment_client_location`) a posição do cliente pelo mesmo canal privado já existente,
sem nova policy de `realtime.messages`. `ProviderIncomingRequestViewModel` reaproveita esse mesmo
repositório em vez de duplicar lógica de canal/reconexão. Ver `contracts/client-location-rpcs.md`
e `data-model.md` #3.

**Structure Decision**: Projeto único Android por feature (já estabelecido). A maior parte dos
ajustes ocorre dentro das features já existentes (`home`, `profile`, `appointmenttracking`,
`mobiledispatch`) e em componentes compartilhados de `core` (`preferences`, `location`, `map`,
`navigation`). Uma única feature nova, `clientaddress`, é criada por ter responsabilidade própria
e testável isoladamente (cadastro/edição de endereço do cliente); `ViaCepAddressLookupRepository`
é promovido de `providerregistration` para `core/address` para ser reaproveitado por ela sem
acoplamento entre features, seguindo a organização e os limites de responsabilidade exigidos pela
constituição.

## Complexity Tracking

*Sem violações da constituição a justificar.*
