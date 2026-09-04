# Data Model: Experiência mockada de UI do PitTec

## Overview

Todos os tipos abaixo são modelos Kotlin imutáveis mantidos somente em memória. Não há DTO,
tabela, migration, arquivo, cache ou mapeamento de rede. Cliente e prestador possuem instâncias
independentes; nomes iguais nas fixtures não representam pessoas sincronizadas.

## Value objects

### `MockParticipant`

| Campo | Tipo | Regra |
|---|---|---|
| `id` | `String` | ID estável e não vazio dentro da fixture |
| `displayName` | `String` | Texto não vazio e próprio para exibição |
| `role` | `MockParticipantRole` | `CLIENT` ou `PITTEC` |

### `DistancePerimeter`

| Campo | Tipo | Regra |
|---|---|---|
| `kilometers` | `Int` | Positivo e múltiplo de 5 |

Derivados:

- `bandCount = kilometers / 5`
- Próxima faixa: `kilometers + 5`

### `ServiceEstimate`

| Campo | Tipo | Regra |
|---|---|---|
| `perimeter` | `DistancePerimeter` | Faixa atual em que o candidato foi encontrado |
| `fixedCents` | `Long` | Sempre `5000` nesta feature |
| `bandUnitCents` | `Long` | Sempre `1000` nesta feature |
| `bandCount` | `Int` | Igual a `perimeter.kilometers / 5` |
| `distanceAdditionalCents` | `Long` | `bandUnitCents * bandCount` |
| `totalCents` | `Long` | `fixedCents + distanceAdditionalCents` |

Invariantes:

- Todos os valores são não negativos e calculados, nunca recebidos como texto.
- Formatação em reais e duas casas ocorre somente na UI com locale `pt-BR`.
- 5 km = 6.000 centavos; 10 km = 7.000; 15 km = 8.000.

### `MockLocationSnapshot`

| Campo | Tipo | Regra |
|---|---|---|
| `coordinate` | `MapCoordinate` | Latitude/longitude válidas |
| `capturedAtEpochMillis` | `Long` | Usado para horário legível |
| `capturedAtElapsedRealtimeMillis` | `Long` | Usado para idade/stale monotônica |
| `source` | `MockLocationSource` | `DEVICE` para a Home prestador; `FIXTURE` ou `SIMULATED_STEP` nos demais casos |
| `revision` | `Long` | Cresce a cada nova posição |

Derivado: `isStale = elapsedRealtimeNow - capturedAtElapsedRealtimeMillis > 35_000`.

## Fixture entities

### `MockPitTecCandidate`

| Campo | Tipo | Regra |
|---|---|---|
| `participant` | `MockParticipant` | Role obrigatoriamente `PITTEC` |
| `perimeter` | `DistancePerimeter` | Faixa de 5 km na qual será consultado |
| `initialCoordinate` | `MapCoordinate` | Ponto válido derivado da âncora do cenário |
| `response` | `MockCandidateResponse` | `ACCEPT` ou `DECLINE` |
| `responseDelayMillis` | `Long` | Positivo, finito e determinístico |

### `PitTecMockScenario`

| Campo | Tipo | Regra |
|---|---|---|
| `id` | `String` | Estável e não vazio |
| `client` | `MockParticipant` | Role `CLIENT` |
| `clientCoordinate` | `MapCoordinate` | Válida |
| `candidates` | `List<MockPitTecCandidate>` | Ordenada por faixa e ordem de tentativa; IDs únicos |
| `movementPath` | `List<MapCoordinate>` | Pelo menos dois pontos quando houver aceite |

Fixtures mínimas:

1. `progressive-success`: candidato recusado em 5 km, candidato recusado em 10 km e candidato
   aceito em 15 km; demonstra 60/70/80 reais e expansão automática.
2. `no-provider`: candidatos recusam e a lista termina; demonstra estado vazio e retry.
3. `provider-incoming`: uma solicitação em 10 km, usada apenas pelo fluxo independente do
   prestador; total de R$ 70,00.

As coordenadas são deslocamentos determinísticos ao redor da âncora da tela. A fixture cliente
pode usar sua localização atual já exibida pela tela como âncora; a fixture prestador usa o fix
real inicial somente como origem e simula o cliente/percurso a partir daí.

## Runtime entities

### `MockSearch`

| Campo | Tipo | Regra |
|---|---|---|
| `id` | `String` | ID local único na sessão |
| `scenarioId` | `String` | Referência a uma fixture conhecida |
| `currentPerimeter` | `DistancePerimeter` | Nunca diminui |
| `candidateIndex` | `Int` | Índice válido ou fim da lista |
| `rejectedCandidateIds` | `Set<String>` | Sem duplicatas; candidato rejeitado nunca retorna |
| `estimate` | `ServiceEstimate` | Recalculada ao mudar de faixa |
| `feedback` | `MockSearchFeedback` | `SEARCHING`, `AWAITING_RESPONSE`, `DECLINED_CONTINUING`, `FOUND`, `NOT_FOUND` |
| `revision` | `Long` | Cresce em cada transição aceita |
| `confirmed` | `Boolean` | Torna-se true uma única vez antes da busca |

Invariantes:

- Só existe uma busca ativa por ViewModel.
- `confirmed == true` antes de qualquer consulta simulada.
- Um candidato em `rejectedCandidateIds` não pode ser candidato atual.
- A faixa só aumenta quando não existe outro candidato não tentado na faixa atual.
- `NOT_FOUND` e `FOUND` são terminais para a busca.

### `MockServiceRequest`

| Campo | Tipo | Regra |
|---|---|---|
| `id` | `String` | Estável durante a exibição |
| `client` | `MockParticipant` | Cliente da fixture provider |
| `clientCoordinate` | `MapCoordinate` | Ponto simulado válido |
| `estimate` | `ServiceEstimate` | Composição mostrada no card |
| `approximateDistanceMeters` | `Int` | Não negativo |
| `createdAtEpochMillis` | `Long` | Momento relevante para Dados da corrida |
| `revision` | `Long` | Chave idempotente do alerta/resposta |
| `pulseConsumed` | `Boolean` | Impede replay por recomposição/navegação |

Uma solicitação admite exatamente uma resposta local: `ACCEPT` ou `DECLINE`.

### `ParticipantConfirmation`

| Campo | Tipo | Regra |
|---|---|---|
| `kind` | `MockConfirmationKind` | `MEETING` ou `COMPLETION` |
| `localConfirmedAtEpochMillis` | `Long?` | Preenchido uma vez pela ação do perfil atual |
| `counterpartConfirmedAtEpochMillis` | `Long?` | Preenchido uma vez, 3 s depois, pela simulação |
| `revision` | `Long` | Impede aplicação de job obsoleto |

Derivados: `waitingForCounterpart` quando apenas a confirmação local existe; `complete` quando
ambas existem.

### `MockAppointment`

| Campo | Tipo | Regra |
|---|---|---|
| `id` | `String` | ID local único na sessão |
| `client` / `provider` | `MockParticipant` | Roles distintos e válidos |
| `estimate` | `ServiceEstimate` | Congelada na faixa do candidato aceito |
| `clientLocation` / `providerLocation` | `MockLocationSnapshot` | Últimos pontos conhecidos |
| `movementPath` / `nextStepIndex` | lista/índice | Avanço determinístico a cada 30 s |
| `remainingDistanceMeters` | `Int` | Recalculada localmente; mínimo zero |
| `status` | `MockAppointmentStatus` | Estado único descrito abaixo |
| `meetingConfirmation` | `ParticipantConfirmation?` | Criada na primeira ação “Já nos encontramos” |
| `completionConfirmation` | `ParticipantConfirmation?` | Criada na primeira ação “Finalizar serviço” |
| `acceptedAtEpochMillis` | `Long` | Obrigatório |
| `startedAtEpochMillis` | `Long?` | Só após confirmação bilateral de encontro |
| `completedAtEpochMillis` | `Long?` | Só após confirmação bilateral de conclusão |
| `revision` | `Long` | Cresce em posição e transições |

### `MockAppointmentStatus`

- `TRAVELLING`: aceito e aproximando-se.
- `AWAITING_MEETING_CONFIRMATION`: confirmação local registrada; aguarda contraparte.
- `IN_PROGRESS`: encontro confirmado pelos dois; serviço iniciado.
- `AWAITING_COMPLETION_CONFIRMATION`: confirmação local de conclusão registrada.
- `COMPLETED`: contraparte confirmou conclusão; estado terminal e sem novos ticks.

## UI state roots

### `PitTecClientMockUiState`

Estado base contém `serviceId`, mapa inicial existente, estado de localização/map surface e uma
única `stage`:

- `DISCOVERY`: apresentação inicial preservada e CTA “Buscar PitTec”.
- `ESTIMATING`: feedback curto enquanto seleciona a primeira faixa da fixture.
- `AWAITING_CLIENT_CONFIRMATION(estimate)`: composição e Confirmar/Voltar.
- `SEARCHING(search)`: feedback contínuo e idempotente.
- `TRACKING(appointment)`: mapa/estado/ações de encontro ou conclusão.
- `COMPLETED(appointment)`: resumo e retorno à Home.
- `NO_PROVIDER(lastSearch)`: vazio com Tentar novamente/Voltar.

### `ProviderLocationGate`

- `CHECKING_PERMISSION`
- `PERMISSION_REQUIRED`
- `APPROXIMATE_ONLY`
- `SERVICES_DISABLED`
- `LOCATING`
- `READY(deviceLocation)`
- `UNAVAILABLE`

Somente `READY` permite compor o mapa e apresentar solicitações.

### `PitTecProviderMockUiState`

Contém `provider`, `selectedTab`, `locationGate`, `mapSurface`, `currentOrLastAppointment` e uma
única `stage`:

- `AVAILABLE`: pronto para solicitação e ação de demonstração.
- `INCOMING_REQUEST(request)`: card roxo persistente e resposta única.
- `TRACKING(appointment)`: deslocamento/confirmações.
- `COMPLETED(appointment)`: atendimento mais recente preservado.

`selectedTab` é `MAP`, `RIDE_DATA`, `VALUES` ou `PROFILE`. Trocar aba não altera `stage` nem cria
outro atendimento.

## State transitions

### Client

```text
DISCOVERY
  -> ESTIMATING
  -> AWAITING_CLIENT_CONFIRMATION
     -> DISCOVERY                         (voltar/cancelar)
     -> SEARCHING                         (confirmar uma vez)
        -> SEARCHING/DECLINED_CONTINUING  (recusa; próximo candidato/faixa)
        -> NO_PROVIDER                    (fixtures esgotadas)
        -> TRACKING/TRAVELLING             (aceite)
           -> AWAITING_MEETING_CONFIRMATION
           -> IN_PROGRESS                  (+3 s contraparte)
           -> AWAITING_COMPLETION_CONFIRMATION
           -> COMPLETED                    (+3 s contraparte)
```

### Provider

```text
LOCATION BLOCKED -> AVAILABLE             (permissão + serviços + fix real)
AVAILABLE -> INCOMING_REQUEST             (evento mock único/replay explícito)
INCOMING_REQUEST -> AVAILABLE             (recusar uma vez)
INCOMING_REQUEST -> TRACKING/TRAVELLING   (aceitar uma vez)
TRACKING -> AWAITING_MEETING_CONFIRMATION -> IN_PROGRESS
IN_PROGRESS -> AWAITING_COMPLETION_CONFIRMATION -> COMPLETED
COMPLETED -> AVAILABLE                    (voltar ao mapa; último atendimento preservado)
```

## Idempotency and cancellation rules

- Toda ação compara estado atual e revisão; ação inválida ou repetida é no-op.
- Confirmar busca desabilita imediatamente o botão antes de iniciar qualquer delay.
- Aceitar/recusar remove a possibilidade da outra resposta na mesma atualização atômica de estado.
- Cada confirmação local registra timestamp uma única vez e agenda no máximo um job de contraparte.
- Jobs capturam `appointmentId + revision`; antes de aplicar, revalidam ID, status e ausência da
  confirmação esperada.
- Finalização cancela jobs de movimento/stale e impede novas revisões de localização.
- `onCleared` cancela todos os jobs; nenhum dado é salvo.
