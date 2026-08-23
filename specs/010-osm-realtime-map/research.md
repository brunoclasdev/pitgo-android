# Phase 0 Research: Mapa OSM com MapLibre e atualização em tempo real

## 1. SDK, versão e backend gráfico

**Decision**: usar `org.maplibre.gl:android-sdk-opengl:13.5.0` e não o alias `android-sdk`.

**Rationale**: 13.5.0 é a versão Android estável atual e o artefato publicado depende de Kotlin
stdlib 2.2.10, igual ao projeto. O SDK exige minSdk 23 e Java 11; o app usa minSdk 24 e Java 11.
OpenGL é classificado como estável e de maior compatibilidade. Desde 13.x, o alias sem sufixo aponta
para Vulkan; a escolha explícita evita mudar o renderer por convenção e reduz risco em aparelhos e
emuladores antigos. AAB continuará entregando somente a ABI necessária ao dispositivo.

**Alternatives considered**: Vulkan é estável e pode ser mais rápido em aparelhos modernos, mas
exige uma matriz GPU maior e há relato aberto de inicialização no emulador API 37; multi-backend é
experimental e aumenta tamanho/complexidade; MapLibre Compose adicionaria outra camada e usa
toolchain mais novo que o app; versão antiga perde correções de estabilidade recentes.

Sources: [release 13.5.0](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.5.0),
[rendering backends](https://maplibre.org/maplibre-native/docs/book/platforms/android/android-rendering-backends.html),
[build configuration](https://raw.githubusercontent.com/maplibre/maplibre-native/android-v13.5.0/platform/android/MapLibreAndroid/build.gradle.kts),
[published artifact](https://repo.maven.apache.org/maven2/org/maplibre/gl/android-sdk-opengl/13.5.0/android-sdk-opengl-13.5.0.pom).

## 2. Integração Compose e lifecycle nativo

**Decision**: inicializar `MapLibre.getInstance(applicationContext)` uma vez em `PitGoApplication` e
criar `MapLibreMapHost` com `AndroidView<MapView>`.

**Rationale**: `MapView` possui renderer nativo, listeners e recursos de estilo. O `factory` chama
`onCreate(null)`, registra callbacks e solicita o mapa uma vez; `update` somente encaminha o último
modelo e comandos de câmera novos. Um observer pareia `onStart/onResume/onPause/onStop`; callback de
low-memory chama `onLowMemory`; `onRelease` remove listeners e chama `onDestroy` exatamente uma vez.
Viewport é estado do ViewModel, evitando depender do Bundle da Activity e preservando Mapa/Lista.

**Alternatives considered**: XML/Fragment contraria o projeto Compose; guardar MapView no ViewModel
vaza lifecycle; recriar a View ou reaplicar câmera em recomposição produz flicker; MapLibre Compose
não é necessário para a integração e ampliaria a migração de toolchain.

Sources: [MapLibre quickstart/lifecycle](https://maplibre.org/maplibre-native/android/examples/getting-started/),
[Views in Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose).

## 3. Estilo gerenciado e fronteira de configuração

**Decision**: substituir o contrato de template XYZ por `mapStyleUrl`, `mapStyleProviderName`,
`mapStyleAttribution` e `mapStyleAttributionUrl`, obrigatórios e injetados por BuildConfig.

**Rationale**: MapLibre carrega um Style JSON que referencia tiles, sprites e glyphs; um template
XYZ isolado não descreve esse grafo. O build valida campos locais e HTTPS sem ecoar valores. Não faz
request na configuração Gradle: a inspeção das URLs internas do estilo é gate operacional/CI.
Endpoint demo/público é rejeitado. Token presente na URL é extraível e só pode ser publicável,
restrito por aplicativo/cota; segredo administrativo continua fora do APK.

**Alternatives considered**: fixar fornecedor inventaria decisão não dada; baixar/reescrever Style
no Gradle adicionaria I/O instável e poderia vazar token; proxy de tiles em Edge Function adiciona
latência/custo e não é necessário para credencial móvel publicável; fallback demo viola a spec.

Sources: [MapLibre Style Specification](https://maplibre.org/maplibre-style-spec/),
[style sources](https://maplibre.org/maplibre-style-spec/sources/),
[OSM tile policy](https://operations.osmfoundation.org/policies/tiles/).

## 4. Carregamento, recursos, cache e atribuição

**Decision**: modelar estilo e recursos separadamente; usar callbacks do MapView para estilo/mapa,
TileOperation para degradação e ConnectivityObserver para offline. Retry reaplica o mesmo estilo no
mesmo MapView e reinstala sources/layers/images com o modelo mais recente.

**Rationale**: `setStyle` é assíncrono e uma troca destrói as camadas runtime. `onStyleLoaded`
precisa ser idempotente e ler `latestRenderModel`, impedindo callback antigo de ressuscitar marker
privado após Ended/session loss. Um tile `Error` isolado não derruba o mapa; erros repetidos sem
sucesso são debounced. Cache ambiente do SDK pode permanecer, mas nenhuma OfflineRegion, download ou
limpeza no retry será criada. Atribuição nativa permanece habilitada e uma ação Compose acessível
expõe OSM + provedor sem ficar coberta.

**Alternatives considered**: recriar MapView perde câmera e recursos; limpar cache destrói fallback;
um Boolean mistura estilo, tiles, dados e rede; raw error pode conter URL/token; confiar somente em
attribution desenhada dentro do mapa reduz acessibilidade.

Sources: [setStyle failure contract](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/set-style.html),
[MapView event API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-view/index.html),
[TileOperation](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.tile/-tile-operation/index.html).

## 5. Providers, clustering e seleção

**Decision**: converter providers válidos em uma `GeoJsonSource` em memória com clustering nativo,
camadas de cluster/contagem/provider e seleção data-driven. Coordenadas exatamente iguais são
pré-agrupadas por chave estável antes do GeoJSON.

**Rationale**: uma source atualizável atende até 200 resultados sem annotations individuais.
Toque consulta somente layers próprias com `queryRenderedFeatures`. Cluster próximo recentraliza e
aumenta o zoom; acima do clusterMaxZoom, pontos se separam. Grupos de coordenadas iguais abrem sheet
Compose com todos os IDs. Isso preserva 100% dos resultados e evita `getClusterLeaves`/
`getClusterExpansionZoom` enquanto a source pode mudar, cenário com crash nativo aberto. A lista
textual continua completa e autoritativa para acessibilidade.

**Alternatives considered**: annotations/markers individuais escalam pior e não resolvem overlap;
grid custom duplica clustering do SDK; APIs de cluster assíncronas podem referenciar cluster já
substituído; jitter falseia endereço; detalhes públicos dentro do GeoJSON duplicam estado e ampliam
exposição.

Sources: [GeoJSON and runtime updates](https://maplibre.org/maplibre-native/android/examples/geojson-guide/),
[native clustering](https://maplibre.org/maplibre-native/android/examples/styling/circle-layer/),
[rendered feature query](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/query-rendered-features.html),
[cluster race](https://github.com/maplibre/maplibre-native/issues/3519).

## 6. Marcador Realtime

**Decision**: uma source GeoJSON separada, sem clustering, contém no máximo um Feature. Cada posição
aceita executa apenas `setGeoJson`; propriedade `kind=LIVE|STALE` escolhe imagem/semântica.

**Rationale**: atualização incremental mantém MapView, Style, source e layer. Ordenação e freshness
continuam no ViewModel, antes da UI; o renderer não decide qual posição é nova. Style reload repõe a
source usando o estado atual. Falha visual mantém a última posição como stale; Ended/session loss
produz coleção vazia imediatamente.

**Alternatives considered**: recriar marker/style causa flicker; animation/interpolação sugere rota
não observada; usar LocationComponent publicaria um conceito de localização do próprio dispositivo,
não do provider remoto; guardar histórico viola escopo.

Sources: [live realtime data](https://maplibre.org/maplibre-native/android/examples/styling/live-realtime-data/),
[GeoJsonSource.setGeoJson](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.sources/-geo-json-source/index.html).

## 7. UI acessível e navegação

**Decision**: manter alternância Mapa/Lista, resumo Compose e CTA por IDs. O mapa é uma região
semântica; controles, lista e sheet são Compose/Material. `ServiceMapRoute` recebe `onBack` e
`onRequestService(providerId, serviceId)`.

**Rationale**: features dentro do renderer não formam uma árvore TalkBack confiável. Lista oferece
paridade integral; sheet compacto ou painel expandido mostra nome, modalidade, estimativa e ação.
RequestService precisa aceitar UUID real do catálogo e providerId, pois o contrato demo atual não
representa o provider selecionado.

**Alternatives considered**: InfoWindow nativa como única UI prejudica acessibilidade; passar
nome/preço pela rota duplica fonte de verdade; CTA sem destino viola aceitação; criar booking real
permanece fora do escopo.

Source: [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility).

## 8. Stream Realtime, ordenação e encerramento

**Decision**: manter eventos explícitos e revisão `(publishedAtEpochMillis, version)`, iniciar
collectors antes do snapshot, emitir Snapshot(null), interpretar DELETE como tombstone e remover
canal em NonCancellable. Criar migration de hardening para `EMPTY`/`ENDED` e Broadcast terminal.

**Rationale**: a troca de renderer não corrige os bugs atuais: snapshot vazio fica Loading,
subscribe possui janela de perda, versão regride e reinicia após reinsert, e reconnect terminal é
indistinguível de erro. Ended/session loss precisa limpar o último modelo antes de qualquer callback
de estilo tardio.

**Alternatives considered**: ordenar por capturedAt confia no relógio do aparelho; somente version
falha após reinsert; DELETE não distingue parada temporária de encerramento; tabela de histórico ou
sequence global é mais invasiva.

Sources: [Supabase Broadcast](https://supabase.com/docs/guides/realtime/broadcast),
[database changes via Broadcast](https://supabase.com/docs/guides/realtime/subscribing-to-database-changes),
[remove channel](https://supabase.com/docs/reference/kotlin/v1/removechannel).

## 9. Remoções e limites de escopo

**Decision**: remover Maps Compose/Utils, `MAPS_API_KEY`, metadata Google e imports de renderização;
manter Play Services Location, Functions/geocodificação e backend de descoberta. Não adicionar
osmdroid nem MapLibre Compose.

**Rationale**: FLP continua responsável pela posição do dispositivo e não renderiza mapa. A
geocodificação é server-side e explicitamente fora do escopo. Uma única dependência MapLibre Native
evita renderizadores concorrentes e cumpre a revisão da spec.

**Alternatives considered**: trocar FLP/geocoder amplia escopo; manter chave Maps sem consumidor
perpetua configuração obsoleta; fallback renderer contradiz FR-002.
