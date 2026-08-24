# Data Model: Busca e despacho de prestadores móveis

O PostgreSQL é a fonte autoritativa do fluxo. O Android mantém apenas snapshots imutáveis,
revisões e estado transitório de interação. Coordenadas são correntes e substituídas; nenhuma
entidade desta feature representa uma trilha.

## 1. Extensão de catálogo

### `ServiceDispatchKind`

```text
BROWSE
MOBILE_SEQUENTIAL
```

`services.dispatch_kind` é `BROWSE` por padrão. O serviço de guincho é configurado como
`MOBILE_SEQUENTIAL` por uma migration que exige encontrar exatamente um registro confirmado. O
Android nunca decide pelo texto do nome; ambiguidade/ausência durante o backfill interrompe a
migration em vez de escolher silenciosamente.

### Regras

- somente serviço e categoria ativos podem iniciar busca;
- `provider_services` ganha `active boolean not null default true`, preservando ofertas legadas;
- `MOBILE_SEQUENTIAL` aceita apenas ofertas ativas de providers `MOBILE`;
- a oferta precisa ter preço válido ou gratuidade explícita;
- mudança posterior do preço não altera um appointment já criado.

## 2. Posição corrente de discovery

### `provider_discovery_locations`

| Campo | Tipo | Regra |
|-------|------|-------|
| `provider_id` | UUID PK/FK | provider derivado de `auth.uid()` |
| `position` | `geography(Point,4326)` | longitude X, latitude Y, faixas válidas |
| `accuracy_m` | numeric | `> 0` e `<= 100` |
| `captured_at` | timestamptz | entre server now - 30 s e server now + 10 s ao publicar |
| `published_at` | timestamptz | definido pelo servidor |
| `version` | bigint | positivo e crescente por provider |
| `tracker_instance_id` | UUID | lease da instância publicadora |
| `tracker_sequence` | bigint | positivo e estritamente crescente por tracker |

Índice GiST em `position`. Há no máximo uma linha por provider. Upsert substitui a posição anterior;
stop/inelegibilidade apaga a linha quando possível e a regra de 30 segundos protege falha offline.

### Elegibilidade efetiva

```text
provider.active
AND provider.available                 // intenção do prestador
AND provider.location_mode = MOBILE
AND service/category/offer ativos
AND published_at >= server_now - 30s
AND captured_at >= server_now - 30s
AND nenhum appointment PENDING/ACCEPTED/IN_PROGRESS
```

Aceite remove a linha de discovery e o mesmo FGS passa a projetar o fix em
`provider_current_locations`, já restrita aos participantes do appointment.

## 3. Busca persistente

### `service_searches`

| Campo | Tipo | Regra |
|-------|------|-------|
| `id` | UUID PK | gerado uma vez no cliente; chave idempotente do start |
| `client_id` | UUID FK | sempre `auth.uid()` de conta CLIENT |
| `service_id` | UUID FK | `MOBILE_SEQUENTIAL`, ativo |
| `status` | enum/check | estado corrente abaixo |
| `search_point` | `geography(Point,4326)` nullable | uma posição corrente do cliente enquanto a busca está ativa |
| `search_accuracy_m` | numeric nullable | `>0` e `<=100` para start |
| `current_radius_m` | integer | `10000`, `25000` ou `50000` |
| `current_candidate_id` | UUID nullable | candidato corrente da própria busca |
| `current_appointment_id` | UUID nullable | solicitação corrente da própria busca |
| `revision` | bigint | cresce uma vez por transição confirmada |
| `created_at`, `updated_at` | timestamptz | server-side |
| `ended_at` | timestamptz nullable | obrigatório em estado terminal |

### `ServiceSearchStatus`

```text
SEARCHING
OPTION_FOUND
AWAITING_PROVIDER
ACCEPTED
IN_PROGRESS
CANCELLED
COMPLETED
EXHAUSTED
```

Índice parcial único `(client_id, service_id)` para `SEARCHING`, `OPTION_FOUND`,
`AWAITING_PROVIDER`, `ACCEPTED` e `IN_PROGRESS`. Outra tentativa retorna a busca ativa.

`search_point` é apagado ao chegar a `CANCELLED`, `COMPLETED` ou `EXHAUSTED`. Ele não é copiado
para candidatos ou eventos Realtime.

## 4. Candidato/proposta

### `private.dispatch_candidates`

| Campo | Tipo | Regra |
|-------|------|-------|
| `id` | UUID PK | gerado no servidor |
| `search_id` | UUID FK | busca proprietária |
| `provider_id` | UUID FK | provider selecionado |
| `provider_service_id` | UUID FK | oferta exata selecionada |
| `sequence` | integer | positivo e crescente na busca |
| `outcome` | enum/check | decisão registrada abaixo |
| `provider_name_snapshot` | text | nome público no momento da proposta |
| `locality_snapshot` | text | cidade/UF pública do cadastro empresarial; nunca usada no cálculo |
| `distance_meters` | numeric | não negativo; calculado no servidor |
| `price_snapshot` | numeric nullable | >=0, duas casas; 0 quando gratuito |
| `is_free_snapshot` | boolean | coerente com preço |
| `presented_at` | timestamptz | obrigatório |
| `requested_at`, `responded_at` | timestamptz nullable | conforme outcome |

### `CandidateOutcome`

```text
PRESENTED
CLIENT_DISMISSED
REQUESTED
PROVIDER_REJECTED
PROPOSAL_EXPIRED
INVALIDATED
ACCEPTED
PROVIDER_CANCELLED
```

Constraints `UNIQUE(search_id, provider_id)`, `UNIQUE(search_id, sequence)` e, no appointment,
`UNIQUE(dispatch_candidate_id)` impedem repetição e duplicação. O candidato não armazena a
coordenada do provider; o snapshot pode retorná-la somente enquanto ele for a proposta corrente,
permanecer recente e a conta solicitante for owner da busca.

## 5. Solicitação/appointment estendido

Campos atuais de `appointments` permanecem compatíveis. Para appointments do dispatch adicionar:

| Campo | Regra |
|-------|-------|
| `dispatch_search_id` | busca correspondente |
| `dispatch_candidate_id` | candidato único |
| `service_id` | serviço exato |
| `provider_service_id` | oferta de origem |
| `price_snapshot` | preço garantido e imutável |
| `is_free_snapshot` | coerente com preço |
| `response_deadline` | criação server-side + 60 s |
| `revision` | monotônica |
| `cancelled_by` | `CLIENT` ou `PROVIDER`, somente em cancelamento |
| `cancellation_reason` | trim, obrigatório e limitado quando cancelado |
| `cancelled_at` | timestamp server-side |

### `AppointmentStatus`

```text
PENDING
ACCEPTED
IN_PROGRESS
PROVIDER_REJECTED
EXPIRED
COMPLETED
CANCELLED
```

Appointment legado pode ter campos de dispatch nulos; quando `dispatch_search_id` existe, todos os
vínculos/snapshots obrigatórios devem existir. Um índice parcial único por `provider_id` para
`PENDING`, `ACCEPTED` e `IN_PROGRESS` cria o lease exclusivo.

## 6. Transições autoritativas

```text
SEARCHING
  ├─ candidato elegível ─> OPTION_FOUND / PRESENTED
  └─ esgotou 10/25/50 km ─> EXHAUSTED

OPTION_FOUND
  ├─ Próximo ─> CLIENT_DISMISSED ─> SEARCHING
  ├─ candidato ficou inválido ─> INVALIDATED ─> SEARCHING
  └─ Solicitar ─> AWAITING_PROVIDER / PENDING

AWAITING_PROVIDER
  ├─ aceitar antes do deadline ─> ACCEPTED
  ├─ recusar ─> PROVIDER_REJECTED ─> SEARCHING
  └─ deadline ─> EXPIRED ─> SEARCHING

ACCEPTED
  ├─ iniciar ─> IN_PROGRESS
  ├─ cliente cancela + motivo ─> CANCELLED terminal
  └─ provider cancela + motivo ─> PROVIDER_CANCELLED ─> SEARCHING

IN_PROGRESS
  ├─ concluir ─> COMPLETED terminal
  ├─ cliente cancela + motivo ─> CANCELLED terminal
  └─ provider cancela + motivo ─> PROVIDER_CANCELLED ─> SEARCHING
```

Cada mutation trava busca, provider e appointment em ordem estável, compara `expected_revision` e
incrementa a revisão uma vez. Replay que já produziu o mesmo resultado retorna o snapshot atual;
comando stale que significaria outra decisão retorna `STALE_REVISION` sem alterar dados.

## 7. Snapshot de domínio Android

### `MobileDispatchSnapshot`

| Campo | Regra |
|-------|-------|
| `serverNow` | ancora do countdown |
| `searchId`, `serviceId` | IDs opacos e validados |
| `serviceName` | texto público |
| `status`, `revision`, `radiusMeters` | estado confirmado |
| `candidate` | somente em proposta/espera |
| `appointment` | somente após request |
| `allowedActions` | allowlist renderizável |
| `notice` | recusa/expiração/skip anterior, sem dados privados |

### `CandidateProposal`

Contém `candidateId`, nome público, localidade pública aproximada, coordenada corrente autorizada,
distância, preço mostrado e modalidade `MOBILE`. Coordenada inválida/stale invalida a proposta.

### `DispatchAppointment`

Contém `appointmentId`, status, preço congelado, deadline, revisão e cancelamento sanitizado. O
countdown deriva de deadline + diferença entre `serverNow` e relógio local; zero dispara resnapshot,
não transição local.

### Estados de tela do cliente

```text
Restoring
LocationRequired(reason)
Content(snapshot, pendingAction?, connectionState, mapSurfaceState)
Empty(snapshot)
Terminal(snapshot)
SessionExpired
```

### Estados do provider

```text
NoIncoming
Incoming(snapshot, remainingDisplay, pendingAction?)
Reserved(snapshot)
SessionExpired
Failure(lastConfirmed?)
```

`PendingAction` contém tipo, candidato/appointment/revisão esperada e bloqueia toques duplicados.
Falha recuperável preserva o último snapshot confirmado.

## 8. Publicação processual Android

### `ProviderTrackingState`

```text
Stopped(reason?)
Starting
Publishing(lastPublishedAt)
Degraded(reason, lastPublishedAt?)
Stopping
```

### `ProviderTrackingTarget`

```text
DISCOVERY
APPOINTMENT(appointmentId)
```

O backend, não o Intent, decide o appointment vigente do provider. O target serve para UI e
notificação; ownership continua derivada da sessão.

Uma preferência técnica pode reter apenas `accountId`, `trackerInstanceId` e sequência para um
restart sticky do mesmo processo/conta. Nunca guarda coordenada, busca, proposta, appointment ou
token. Troca de conta/logout limpa a lease.

## 9. Relações

```text
Service 1 ── dispatchKind
Provider 1 ── 0..1 ProviderDiscoveryLocation
Client 1 ── 0..N ServiceSearch (máx. 1 ativa por Service)
ServiceSearch 1 ── N DispatchCandidate
DispatchCandidate 1 ── 0..1 Appointment
Provider 1 ── máx. 1 Appointment PENDING|ACCEPTED|IN_PROGRESS
Appointment 1 ── 0..1 ProviderCurrentLocation (tracking privado existente)
```

Todas as tabelas novas têm RLS ligada e nenhum DML direto para o APK. Candidatos ficam em
`private`; localização e busca públicas são acessadas somente por RPCs sanitizadas.
