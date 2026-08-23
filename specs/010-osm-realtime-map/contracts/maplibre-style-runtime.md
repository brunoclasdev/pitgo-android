# Contract: MapLibre Style Runtime

## Build boundary

Todos os variants exigem:

| Propriedade Gradle | Validação |
|--------------------|-----------|
| `mapStyleUrl` | HTTPS absoluta; sem user-info, fragment ou placeholder |
| `mapStyleProviderName` | texto público não vazio |
| `mapStyleAttribution` | crédito OSM e do provedor, não vazio |
| `mapStyleAttributionUrl` | HTTPS absoluta |

Fontes aceitas são as já usadas pelo projeto: Gradle properties, `ORG_GRADLE_PROJECT_*` e
`local.properties` ignorado. Exemplo sem valor real:

```properties
mapStyleUrl=https://<managed-provider>/<style>.json?key=<client-token>
mapStyleProviderName=<provider-name>
mapStyleAttribution=© OpenStreetMap contributors · <provider-credit>
mapStyleAttributionUrl=https://<provider-attribution-page>
```

### Falhas obrigatórias

Build falha sem ecoar valores quando campo estiver ausente/blank, URL não for HTTPS absoluta,
existir user-info/fragment/placeholder ou host conhecido for demo/público não gerenciado. Uma fonte
de maior precedência inválida não cai silenciosamente para outra.

O Gradle não baixa o Style JSON. CI/pre-release deve inspecionar o estilo escolhido e confirmar:

- sources, TileJSON, sprites e glyphs HTTPS e autorizados pelo provedor;
- attribution correta em cada source;
- token móvel publicável/restrito;
- ausência de `tile.openstreetmap.org`, demotiles e credencial administrativa.

BuildConfig gera `MAP_STYLE_URL`, `MAP_PROVIDER_NAME`, `MAP_PROVIDER_ATTRIBUTION` e
`MAP_PROVIDER_ATTRIBUTION_URL` com escaping seguro. Somente `ManagedMapStyleConfiguration` lê esses
campos.

## Dependency boundary

- Adicionar `org.maplibre.gl:android-sdk-opengl:13.5.0`.
- Não usar alias Vulkan, multi-backend ou MapLibre Compose nesta feature.
- Remover Maps Compose/Utils, `MAPS_API_KEY`, metadata Google e imports renderer.
- Não adicionar osmdroid.
- Manter Play Services Location, Supabase e geocodificação existentes.
- `dependencyInsight` deve provar um único backend MapLibre OpenGL.

## Runtime initialization and lifecycle

`PitGoApplication` chama `MapLibre.getInstance(applicationContext)` uma vez.

Cada `MapLibreMapHost`:

1. cria um MapView no `factory` e chama `onCreate(null)` uma vez;
2. registra listeners antes de `setStyle`;
3. encaminha `onStart/onResume/onPause/onStop` ao lifecycle;
4. encaminha low-memory enquanto estiver vivo;
5. aplica update somente quando map/style estiverem prontos;
6. remove listeners e chama `onDestroy` exatamente uma vez no release;
7. nunca expõe MapView/Style/LatLng a ViewModel ou Repository.

Camera programática só aplica revisão nova. Gestos atualizam viewport sem serem sobrescritos por
recomposição.

## Style and layer lifecycle

- `setStyle(MAP_STYLE_URL)` é assíncrono.
- Ao carregar, o controller reinstala idempotentemente imagens, GeoJSON sources e layers.
- O controller sempre publica `latestRenderModel`, não o modelo capturado ao iniciar o request.
- Retry incrementa revisão e reaplica o estilo no mesmo MapView.
- Retry não limpa providers, seleção, câmera, posição autorizada ou cache ambiente.
- Callback antigo/atrasado não pode vencer revisão mais nova nem restaurar marker após
  Ended/SessionExpired.

## Resource state

- Style começou: `Loading`.
- Style carregou: `Ready` e instala layers.
- Falha antes de ready: `Failed`.
- Tile `Error` repetido, sprite/glyph/render failure: `Degraded(kind)`.
- Tile isolado ou `Cancelled` durante pan não gera outage.
- Conectividade ausente domina como `Offline`.
- Sucesso posterior recupera `Ready`.

Mensagens e logs do app nunca incluem URL/token/raw native message. Em produção, logging de URL de
request do SDK deve ficar desabilitado.

## Cache and attribution

Usar somente cache ambiente padrão/limitado do SDK; não criar OfflineRegion, prefetch manual,
download em massa ou limpeza no retry. Não adicionar storage permission.

Manter controles nativos de logo/attribution habilitados e exibir ação Compose acessível com
`© OpenStreetMap contributors` e crédito/link do provedor. Cards, sheets e insets não podem ocultar
permanentemente essa atribuição.

## GeoJSON renderer contract

- IDs de source/layer/image são estáveis por tela.
- Discovery usa source clusterizada e layers de cluster/count/unclustered/selected.
- Tracking usa source não clusterizada com zero ou um Feature.
- Updates chamam `setGeoJson`; não recriam View, style ou layer.
- Somente propriedades opacas `id`, `kind`, `selected` e `groupKey` entram no GeoJSON.
- Style reload reinstala recursos e reaplica a coleção atual.
