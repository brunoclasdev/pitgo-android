# Contract: Realtime Appointment Marker

## Authorization

- Topic privado `appointment-location:<appointmentId>`.
- Somente cliente/provider participantes assinam ou obtêm snapshot.
- Publish continua por RPC e exige provider MOBILE elegível em `ACCEPTED`/`IN_PROGRESS`.
- Discovery pública nunca lê o stream.
- Nenhuma service-role/admin key entra no APK.

## Repository events

```kotlin
sealed interface AppointmentLocationEvent {
    data class Snapshot(val location: AppointmentProviderLocation?)
    data class Upsert(val location: AppointmentProviderLocation)
    data class Removed(val revision: LocationRevision)
    data class Ended(val status: TerminalAppointmentStatus)
    data object SessionExpired
    data object Offline
    data object Failure
}
```

Throwable, headers, tokens, IDs em logs e payload bruto não chegam à UI.

## Subscription sequence

1. Validar sessão/appointmentId.
2. Criar canal privado e iniciar collectors INSERT/UPDATE/DELETE/ENDED.
3. Autenticar e assinar aguardando confirmação.
4. Buscar snapshot.
5. Reduzir snapshot e broadcast pela mesma ordenação.
6. Em reconnect, repetir auth + subscribe + snapshot.
7. Em finally, remover canal em NonCancellable.

Collectors antecedem snapshot. Snapshot(null) é explícito; snapshot atrasado não apaga evento novo.

## Ordering/tombstone

Revisão é `(publishedAtEpochMillis, version)` lexicográfica.

- Upsert aplica somente se maior que acceptedRevision.
- DELETE lê `old`, remove marker e conserva tombstone.
- Evento igual/antigo é ignorado.
- Reinsert version 1 é aceito se publishedAt posterior.
- capturedAt não ordena.
- Ended é terminal e ignora evento posterior.

## SQL hardening

Migration deve:

1. autenticar participante antes de considerar status;
2. retornar `ENDED` para COMPLETED/CANCELLED;
3. retornar `EMPTY` para ativo sem row;
4. retornar LIVE/STALE com revisão server-side quando houver row;
5. permitir topic join somente a participantes também após terminal;
6. transmitir ENDED privado e remover row ao terminal;
7. manter publish proibido fora de status ativo.

Não cria histórico, grant direto ou acesso a terceiro.

## ViewModel

Mantém acceptedRevision/tombstone, última posição não terminal, TrackingDataState, MapSurfaceState,
um collector visível e ticker somente com posição.

- idade <=30s live; >30s stale;
- offline/failure conserva última posição stale;
- session change/Ended limpa posição antes de refresh/navegação;
- CancellationException é relançada;
- onHidden cancela collector/ticker e limpa UI;
- onVisible cria uma coleta.

## MapLibre boundary

ViewModel entrega `MapMarkerSpec`, nunca GeoJSON/Style/MapView. Controller converte:

- LIVE -> feature com ícone/label ao vivo;
- STALE -> ícone/label desatualizado;
- Empty/Ended/SessionExpired -> FeatureCollection vazia.

Atualização aceita chama `GeoJsonSource.setGeoJson` no source já instalado. Style reload reaplica o
modelo mais recente; callback iniciado antes de Ended não pode restaurar a coordenada. Não há
interpolação, rota ou histórico.
