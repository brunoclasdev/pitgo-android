# Implementation Plan: Busca e despacho de prestadores móveis

**Branch**: `013-mobile-provider-dispatch` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-mobile-provider-dispatch/spec.md`

## Summary

Implementar um fluxo dedicado para serviços de despacho móvel, começando pelo guincho, sem
reaproveitar a descoberta genérica por endereço. Uma empresa `MOBILE` marcada como disponível
publicará somente sua posição corrente por meio do foreground service já existente; uma busca de
cliente selecionará no servidor um candidato por vez, por distância, expandindo o raio de 10 km
para 25 km e 50 km sem repetir candidatos.

O PostgreSQL/Supabase será a autoridade de disponibilidade efetiva, ordenação, preço congelado,
prazo de 60 segundos, exclusividade, cancelamento e retomada. RPCs transacionais e idempotentes
retornarão um snapshot versionado; Broadcast privado será somente um sinal para recarregar esse
snapshot. O Android manterá estados imutáveis distintos para cliente e prestador, reutilizará
MapLibre, Fused Location, Realtime e o foreground service atuais, e restaurará o destino correto
após reinício ou nova autenticação da mesma conta.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11, Gradle Kotlin DSL e PostgreSQL/PL/pgSQL do Supabase

**Primary Dependencies**: Android SDK, Jetpack Compose BOM 2026.06.01, Material 3,
Lifecycle/ViewModel 2.10.0, Navigation Compose 2.9.8, Coroutines, supabase-kt Auth/Postgrest/Realtime
3.2.6, Ktor OkHttp 3.3.1, kotlinx.serialization JSON 1.9.0, MapLibre Native OpenGL 13.5.0 e Google
Play Services Location 21.4.0; nenhuma dependência Android nova

**Storage**: Supabase PostgreSQL/PostGIS, tabelas de busca/candidatos/appointments e duas projeções
de posição corrente; sem banco local, fila offline ou histórico de localização

**Testing**: sem testes automatizados, conforme escopo e constituição; Supabase CLI local,
inspeção SQL/RLS/cron, `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug` e matriz manual
em dispositivos Android

**Target Platform**: Android minSdk 24, targetSdk 36 e compileSdk 37; smartphones em primeiro e
segundo plano, retrato/paisagem e fonte ampliada a 200%

**Project Type**: aplicativo Android nativo de módulo único com backend declarativo por migrations
Supabase, organizado por feature/core

**Performance Goals**: primeira proposta ou vazio em até 5 segundos em 95% das buscas; posição
válida publicada aproximadamente a cada 10 segundos e refletida em até 15 segundos; posição stale
após 30 segundos; expiração sem resposta aos 60 segundos; seleção correta com 100 providers
elegíveis numa região

**Constraints**: raios fixos 10/25/50 km; uma busca ativa por cliente/serviço e uma solicitação ativa
por provider; preço congelado; sem `service_role` no APK, DML direto, relógio Android autoritativo,
histórico de coordenadas, FCM, WorkManager, `ACCESS_BACKGROUND_LOCATION`, Room/DataStore para o
fluxo ou alteração de migrations aplicadas

**Scale/Scope**: uma migration incremental, três entidades persistentes novas, extensão de
`appointments`, cerca de dez RPCs, dois tópicos Broadcast privados, um foreground service
generalizado, duas telas dedicadas e integração com Home/Dashboard/MapLibre/navegação

## Constitution Check

*GATE inicial e pós-design: aprovado sem exceções.*

- **I — Simplicidade/KISS/YAGNI**: reutiliza Auth, Postgrest, Realtime, MapLibre, Fused Location,
  foreground service, container manual e tabelas de appointments. Não adiciona FCM, Room,
  WorkManager, Use Case, DataSource ou framework de DI. As três entidades novas materializam
  somente estado que precisa sobreviver a processo, concorrência e resposta perdida.
- **II — Android/MVVM**: telas Compose permanecem stateless; ViewModels separados por papel possuem
  estado e coordenação; um Repository encapsula Supabase; Navigation Compose recebe somente IDs
  opacos; o foreground service é o proprietário processual do loop de localização.
- **III — Estado/UX**: busca, proposta, espera, aceite, atendimento, cancelamento, término, erros,
  localização e ações pendentes são tipos explícitos em `StateFlow`. Mapa é complementar e toda
  informação/ação possui representação textual acessível.
- **IV — Dados/segurança**: RPCs `SECURITY DEFINER` usam `search_path=''`, derivam identidade de
  `auth.uid()`, validam `account_roles`, serializam transições e não aceitam owner do cliente.
  Broadcast privado é autorizado por RLS e carrega apenas IDs/status/revisão; snapshots filtram
  dados públicos e participantes. Coordenadas correntes nunca formam histórico.
- **V — Qualidade proporcional**: migration forward-only, constraints, índices espaciais/parciais,
  versão monotônica e reconciliação server-side cobrem os riscos. Build, lint, banco local e matriz
  manual verificam o fluxo sem gerar testes automatizados não solicitados.

O design pós-Phase 1 mantém todos os gates aprovados. O job de expiração no banco é necessário
para cumprir o prazo mesmo sem uma UI ativa; ele não substitui a validação de deadline em cada RPC.

## Project Structure

### Documentation (this feature)

```text
specs/013-mobile-provider-dispatch/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── mobile-dispatch-rpcs.md
│   ├── mobile-dispatch-realtime.md
│   └── mobile-dispatch-ui-navigation.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/
│   ├── app/PitGoAppContainer.kt
│   ├── auth/repository/SessionRepository.kt
│   ├── catalog/
│   │   ├── model/ServiceCatalogModels.kt
│   │   └── repository/ServiceCatalogRepository.kt
│   ├── location/
│   │   ├── data/FusedLocationDataSource.kt
│   │   ├── model/DeviceLocationModels.kt
│   │   └── repository/DeviceLocationRepository.kt
│   ├── map/model/MapSurfaceModels.kt
│   ├── navigation/
│       ├── PitGoNavHost.kt
│       └── PitGoRoutes.kt
│   └── preferences/ProviderTrackingPreferences.kt
└── features/
    ├── appointmenttracking/{model,repository,ui,viewmodel}/
    ├── home/{model,ui,viewmodel}/
    ├── mobiledispatch/
    │   ├── data/MobileDispatchDtos.kt
    │   ├── model/MobileDispatchModels.kt
    │   ├── repository/MobileDispatchRepository.kt
    │   ├── ui/
    │   │   ├── ClientMobileDispatchScreen.kt
    │   │   ├── MobileDispatchMapStyleController.kt
    │   │   └── ProviderIncomingRequestScreen.kt
    │   └── viewmodel/
    │       ├── ClientMobileDispatchViewModel.kt
    │       ├── DispatchResumeViewModel.kt
    │       └── ProviderIncomingRequestViewModel.kt
    ├── providerdashboard/{model,repository,ui,viewmodel}/
    ├── providermap/{model,repository,ui,viewmodel}/
    ├── session/viewmodel/SessionViewModel.kt
    └── providertracking/
        ├── data/ProviderLocationDtos.kt
        ├── model/ProviderTrackingModels.kt
        ├── repository/
        │   ├── ProviderLocationRepository.kt
        │   └── ProviderTrackingStateRepository.kt
        └── service/ProviderLocationTrackingService.kt

app/src/main/AndroidManifest.xml

app/src/main/res/
├── drawable/
│   ├── ic_map_mobile_provider.xml
│   └── ic_map_mobile_provider_selected.xml
└── values/strings.xml

supabase/migrations/
└── 20260823020000_mobile_provider_dispatch.sql
```

**Structure Decision**: manter o módulo Android único e a arquitetura feature-first. O novo domínio
fica em `features/mobiledispatch`; catálogo, mapa e navegação recebem apenas extensões compartilhadas
que já têm mais de um consumidor. O foreground service atual é generalizado para descoberta e
atendimento, evitando dois loops de sensor. Toda evolução do banco entra numa migration posterior;
nenhuma migration histórica será editada.

## Complexity Tracking

Nenhuma violação constitucional foi identificada.
