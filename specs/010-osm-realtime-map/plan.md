# Implementation Plan: Mapa OSM com MapLibre e atualização em tempo real

**Branch**: `[010-osm-realtime-map]` | **Date**: 2026-08-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-osm-realtime-map/spec.md`

## Summary

Substituir Google Maps Compose/Utils por MapLibre Native Android 13.5.0, usando explicitamente o
artefato OpenGL estável e um único `MapView` hospedado por `AndroidView`. A descoberta continuará
consultando o RPC espacial existente e exibirá somente endereços empresariais por uma fonte GeoJSON
clusterizada. A seleção abrirá resumo Compose com “Solicitar atendimento”. O acompanhamento
reutilizará o mesmo host e atualizará uma fonte GeoJSON de um único marcador, sem recriar mapa,
estilo ou camada.

O estilo MapLibre e seus tiles/sprites/glyphs virão obrigatoriamente de um provedor OSM gerenciado
configurado por ambiente, com atribuição acessível e sem fallback público. Maps Compose, sua chave
Android e qualquer referência ao osmdroid serão removidos; Play Services Location e geocodificação
server-side permanecem. O hardening Supabase já identificado continua necessário para ordenar
eventos, representar `EMPTY`/`ENDED` e encerrar o stream privado de modo determinístico.

## Technical Context

**Language/Version**: Kotlin 2.2.10; SQL/PLpgSQL PostgreSQL/Supabase somente para hardening do stream

**Primary Dependencies**: Jetpack Compose BOM 2026.06.01, Material 3, Lifecycle 2.10.0,
Navigation Compose 2.9.8, MapLibre Native Android OpenGL 13.5.0, Supabase Kotlin 3.2.6
Realtime/Postgrest, Ktor OkHttp 3.3.1 e Google Play Services Location 21.4.0; Google Maps
Compose/Utils serão removidos

**Storage**: PostgreSQL/Supabase existente; uma posição corrente por atendimento; cache ambiente
gerenciado pelo SDK sem OfflineRegion/download; nenhum histórico ou mapa offline

**Testing**: sem testes automatizados, conforme constituição; validação manual com `assembleDebug`,
`assembleRelease`, `lintDebug`, `dependencyInsight`, Supabase CLI e dispositivos/emuladores API 24,
36 e smoke em 37

**Target Platform**: Android minSdk 24, targetSdk 36, compileSdk 37; smartphones, paisagem e fonte
até 200%

**Project Type**: aplicativo Android nativo com backend Supabase versionado no mesmo repositório

**Performance Goals**: primeiro mapa/resultados ou estado vazio em até 3 s p95; até 200 resultados
por busca; nova posição refletida em até 10 s p95; stale após 30 s; uma atualização GeoJSON por
mudança aceita, sem reconstrução do mapa

**Constraints**: MapLibre Native obrigatório; backend OpenGL explícito; estilo OSM gerenciado HTTPS
sem demo/fallback público; atribuição acessível; sem MapLibre Compose wrapper, Google Maps,
osmdroid, XML, OfflineRegion, histórico ou mapa no cadastro empresarial; posição móvel visível
somente a participantes

**Scale/Scope**: dois destinos de mapa existentes, no máximo 200 providers públicos por viewport,
um marcador móvel por atendimento e um host MapLibre compartilhado

## Constitution Check

*GATE inicial e pós-design: aprovado sem exceções.*

- **I — Simplicidade/KISS/YAGNI**: uma dependência Native e um host atendem os dois mapas. GeoJSON
  nativo fornece layers, clustering e atualização; não haverá wrapper Compose adicional, plugin de
  annotations, cache próprio ou backend paralelo.
- **II — Android/MVVM**: telas Compose continuam stateless, ViewModels expõem `StateFlow` e os
  Repositories permanecem donos de Supabase. `AndroidView` encapsula a View nativa; ViewModels não
  conhecem `MapView`, `Style`, `LatLng` ou GeoJSON.
- **III — Estado/UX**: dados, estilo/recursos, permissão, viewport, seleção e recência têm tipos
  explícitos. Lista e resumo Compose são a superfície acessível equivalente ao mapa.
- **IV — Dados/segurança**: somente estilo/token client-safe entra no APK. Descoberta usa endereço
  público mínimo; localização móvel continua privada. Revisões e tombstones impedem regressão ou
  ressurreição de posição.
- **V — Qualidade proporcional**: MapLibre Native tem manutenção ativa e benefício atual. Build,
  lint, matriz de API/GPU, lifecycle, estilo, acessibilidade, RLS e Realtime serão verificados
  manualmente, sem criar testes não solicitados.

O design pós-Phase 1 mantém o gate aprovado. O hardening SQL não cria histórico ou nova entidade de
negócio; somente corrige observabilidade e encerramento da posição corrente já existente.

## Project Structure

### Documentation (this feature)

```text
specs/010-osm-realtime-map/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── map-ui-navigation.md
│   ├── maplibre-style-runtime.md
│   └── realtime-marker.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/
│   ├── map/
│   │   ├── config/ManagedMapStyleConfiguration.kt
│   │   ├── model/MapSurfaceModels.kt
│   │   └── ui/MapLibreMapHost.kt
│   ├── network/ConnectivityObserver.kt
│   ├── navigation/{PitGoNavHost.kt,PitGoRoutes.kt}
│   ├── catalog/repository/ServiceCatalogRepository.kt
│   └── app/PitGoAppContainer.kt
├── features/
│   ├── providermap/
│   │   ├── data/ServiceMapDtos.kt
│   │   ├── model/ServiceMapModels.kt
│   │   ├── repository/ServiceMapRepository.kt
│   │   ├── ui/{ServiceMapScreen.kt,ProviderMapStyleController.kt}
│   │   └── viewmodel/ServiceMapViewModel.kt
│   ├── appointmenttracking/
│   │   ├── model/AppointmentTrackingModels.kt
│   │   ├── repository/AppointmentTrackingRepository.kt
│   │   ├── ui/{AppointmentTrackingScreen.kt,AppointmentMapStyleController.kt}
│   │   └── viewmodel/AppointmentTrackingViewModel.kt
│   └── requestservice/
│       ├── model/RequestServiceContent.kt
│       ├── ui/RequestServiceScreen.kt
│       └── viewmodel/RequestServiceViewModel.kt
└── PitGoApplication.kt

app/src/main/res/
├── drawable/{ic_map_provider_address.xml,ic_map_provider_selected.xml,ic_map_live.xml,ic_map_stale.xml}
└── values/strings.xml

supabase/migrations/
└── 20260821030000_appointment_location_stream_hardening.sql

gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
```

**Structure Decision**: manter o módulo Android único e a organização por feature.
`core/map/ui/MapLibreMapHost` contém lifecycle, câmera, listeners e fronteira genérica do estilo;
os pequenos controllers de layer transformam estado já validado em GeoJSON sem regra de negócio.
Repositories existentes são corrigidos, não duplicados. Descoberta e geocodificação não mudam.

## Complexity Tracking

Nenhuma violação constitucional permanece. `AndroidView` é a integração oficial necessária para
uma `MapView` nativa dentro do aplicativo Compose e evita adicionar um wrapper MapLibre Compose
com toolchain e superfície adicionais.
