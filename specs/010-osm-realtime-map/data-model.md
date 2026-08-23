# Data Model: Mapa OSM com MapLibre e atualização em tempo real

Esta feature não cria histórico de localização nem altera a origem dos endereços. Os modelos
definem estado de tela e a fronteira entre domínio e MapLibre Native.

## 1. Coordenada e viewport

### `MapCoordinate`

| Campo | Tipo | Regra |
|-------|------|-------|
| `latitude` | `Double` | finito, entre -90 e 90 |
| `longitude` | `Double` | finito, entre -180 e 180 |

Coordenada inválida nunca vira feature GeoJSON. Na descoberta, o item é descartado; no tracking,
o evento é rejeitado e não substitui a última posição aceita.

### `MapViewport`

| Campo | Tipo | Regra |
|-------|------|-------|
| `center` | `MapCoordinate` | centro atual |
| `zoom` | `Double` | dentro do intervalo suportado pelo estilo |
| `revision` | `Long` | cresce somente em comando programático novo |

Gestos atualizam o viewport, mas a busca só ocorre ao tocar “Buscar nesta área”. A revisão impede
que recomposição reaplique a câmera inicial ou repita comando já consumido.

## 2. Estilo e recursos do mapa

### `ManagedMapStyleConfiguration`

| Campo | Origem | Regra |
|-------|--------|-------|
| `styleUrl` | BuildConfig | HTTPS absoluta, sem user-info/fragment/placeholder |
| `providerName` | BuildConfig | público e não vazio |
| `attribution` | BuildConfig | inclui crédito OSM e do provedor |
| `attributionUrl` | BuildConfig | HTTPS absoluta |

O Style JSON referencia tiles, sprites e glyphs gerenciados. Token contido na URL é client-safe e
restrito; segredo administrativo é proibido.

### `MapStyleState`

```text
Uninitialized
Loading(revision)
Ready(revision)
Failed(revision)
```

Uma nova tentativa incrementa `revision`. Callback de revisão anterior não pode substituir estado
mais novo.

### `MapResourceState`

```text
Waiting
Ready
Degraded(TILE | GLYPH | SPRITE | RENDER)
Offline
```

`MapSurfaceState` contém exatamente um `MapStyleState` e um `MapResourceState`. Dados de providers,
seleção e posição não são apagados por falha visual.

## 3. Modelo de renderização

### `MapMarkerSpec`

| Campo | Tipo | Regra |
|-------|------|-------|
| `id` | `String` | opaco e estável |
| `coordinate` | `MapCoordinate` | validada |
| `kind` | `ADDRESS`, `LIVE`, `STALE`, `COINCIDENT_GROUP` | não inferido apenas por cor |
| `selected` | `Boolean` | altera forma/contorno e label |
| `groupKey` | `String?` | somente para coordenadas idênticas |
| `accessibilityLabel` | `String` | ação/estado localizado |

O adapter converte apenas `id`, `kind`, `selected` e `groupKey` em propriedades GeoJSON. Nome,
preço, contato e outros dados permanecem no estado de domínio. Style reload recria imagens,
sources/layers e reaplica o modelo mais recente.

### `MapFeatureSelection`

```text
Single(markerId)
CoincidentGroup(groupKey)
NearbyCluster(coordinate)
None
```

Cluster próximo aumenta zoom. Grupo coincidente resolve IDs pelo mapa de domínio e abre lista
Compose. Nenhuma seleção depende de feature antiga após atualização da source.

## 4. Descoberta

### `PublicProvider`

| Campo | Regra |
|-------|-------|
| `providerId` | UUID opaco do RPC |
| `serviceId` | serviço autoritativo da busca |
| `displayName` | público e não vazio |
| `mode` | `FIXED` ou `MOBILE` |
| `addressCoordinate` | sempre endereço empresarial válido |
| `distanceMeters` | opcional, finito e não negativo |
| `estimatedPrice` | opcional e já normalizado |

CNPJ, e-mail, telefone e localização móvel privada não pertencem ao modelo.

### `ServiceMapUiState`

```text
InvalidService
Content(
  serviceId,
  viewport,
  displayMode: MAP | LIST,
  dataState,
  surfaceState,
  clientLocationState,
  providers,
  coincidentGroups,
  selectedProviderId?,
  selectedGroupKey?,
  searchGeneration,
  styleReloadRevision
)
```

`DiscoveryDataState`: `Loading`, `Ready`, `Empty`, `Offline`, `Failure`, `SessionExpired`.

`ClientLocationState`: `NotRequested`, `Resolving`, `InUse`, `Denied`, `Unavailable`.

#### Invariantes

- provider selecionado pertence à lista atual;
- cada provider aparece uma vez na lista e em exatamente um marker/grupo;
- nova geração ignora respostas antigas;
- Mapa e Lista usam a mesma coleção/seleção;
- FIXED e MOBILE usam endereço empresarial na descoberta.

## 5. Tracking privado

### `LocationRevision`

| Campo | Origem | Ordenação |
|-------|--------|----------|
| `publishedAtEpochMillis` | servidor | chave primária |
| `version` | linha corrente | desempate |

Comparação lexicográfica. `capturedAt` do dispositivo nunca ordena o stream. Reinserção com version
1 é aceita quando publicada depois do tombstone.

### `AppointmentProviderLocation`

| Campo | Regra |
|-------|-------|
| `coordinate` | válida e privada |
| `accuracyMeters` | finita e positiva |
| `capturedAt` | informativa |
| `publishedAt` | server-side, exibida ao usuário |
| `revision` | última revisão aceita |

### `AppointmentLocationEvent`

```text
Snapshot(location?)
Upsert(location)
Removed(revision)
Ended(COMPLETED | CANCELLED)
SessionExpired
Offline
Failure
```

`Removed` grava tombstone. Upsert só é aplicado se sua revisão for estritamente posterior.
Snapshot(null) explícito não apaga broadcast mais novo recebido durante a consulta.

### `TrackingDataState`

```text
Loading
Empty
Live(location)
Stale(location, lastUpdatedLabel)
Ended
Offline(lastLocation?)
SessionExpired
Failure(lastLocation?)
```

#### Transições

- posição nova com idade `<=30 s` -> Live;
- relógio ultrapassa 30 s -> Stale, mantendo marker e horário;
- Removed -> Empty com tombstone interno;
- erro/offline com última posição -> Stale/degradado;
- Ended/SessionExpired -> limpa coordenada antes de callback tardio do estilo;
- tela oculta -> cancela collector/ticker e limpa posição privada da UI;
- tela visível -> uma assinatura nova seguida de snapshot.

## 6. Solicitação e relações

`RequestServiceSelection` contém apenas `serviceId` e `providerId`; serviço é resolvido no catálogo.
O callback permanece mockado nesta feature.

```text
ManagedMapStyleConfiguration ──> 1 MapLibreMapHost ──> discovery + tracking
Service ── discovery RPC ──> 0..200 PublicProvider ──> address GeoJSON source
Appointment ── current location ──> 0..1 tracking GeoJSON feature
```

Viewport, seleção, style state e última posição visível são memória de tela/processo. MapLibre usa
cache ambiente interno; não há OfflineRegion. PostgreSQL mantém no máximo uma posição corrente por
atendimento e nenhuma trilha.
