# Contract: RPCs e snapshot da chamada PitTec

## Convenções

- Todas as funções públicas derivam o ator de `auth.uid()` e rejeitam usuário anônimo.
- IDs e revisões recebidos são seletores/controles de concorrência, não autorização.
- Funções `SECURITY DEFINER` usam `search_path = ''`, têm execução pública revogada e grants mínimos.
- Toda mutation relevante trava primeiro `service_searches` e depois `appointments`.
- `revision` cresce monotonamente; respostas antigas nunca substituem snapshot mais novo.
- Datas são ISO-8601 UTC; valor monetário é decimal serializado sem ponto flutuante binário e moeda
  `BRL`.
- Erros de domínio retornam códigos estáveis; mensagens de UI ficam no Android.

## Snapshot canônico

`start_pittec_dispatch`, `get_my_active_pittec_dispatch`, `respond_to_mobile_dispatch`,
`respond_to_pittec_quote` e `cancel_pittec_search` devolvem o mesmo formato compatível:

```json
{
  "request_id": "uuid",
  "service_id": "uuid",
  "dispatch_kind": "PITTEC_AUTO_SEQUENTIAL",
  "status": "AWAITING_CLIENT_CONFIRMATION",
  "revision": 14,
  "server_now": "2026-09-13T15:00:15Z",
  "next_dispatch_at": null,
  "appointment": {
    "id": "uuid",
    "status": "AWAITING_CLIENT_CONFIRMATION",
    "revision": 4,
    "provider": {
      "id": "uuid",
      "display_name": "PitTec José"
    },
    "offer": null,
    "quote": {
      "amount": "89.90",
      "currency": "BRL",
      "version": 1,
      "client_confirmation_deadline": "2026-09-13T15:02:00Z",
      "client_confirmed_at": null
    }
  },
  "payment": {
    "status": "NOT_STARTED"
  },
  "tracking_authorized": false,
  "allowed_actions": ["CONFIRM_QUOTE", "DECLINE_QUOTE", "CANCEL"]
}
```

### Visibilidade por participante

- Cliente em busca/oferta `PENDING`: vê o estado da busca, mas não identidade, posição exata ou preço
  de candidato ainda não aceito. O campo `candidate` introduzido na feature `019` deixa de ser
  produzido/consumido.
- PitTec com oferta `PENDING`: recebe `offer.response_deadline`, distância/região aproximadas e
  `quote.amount/currency/version`, mas não o ponto exato.
- Depois do aceite: cliente e PitTec recebem identidade do PitTec, proposta e estado de confirmação;
  nenhum recebe posições exatas pelo snapshot.
- `tracking_authorized` só é `true` em `ACCEPTED`/`IN_PROGRESS`, depois de uma aprovação de pagamento
  válida. Coordenadas usam RPC/tópico próprio.
- Dados financeiros do meio de pagamento permanecem visíveis somente ao cliente.

### Status e ações

| Status | Cliente | PitTec |
|---|---|---|
| `SEARCHING` | `CANCEL` | nenhuma |
| `AWAITING_PROVIDER` | `CANCEL` | `ACCEPT_OFFER`, `DECLINE_OFFER` apenas ao candidato |
| `AWAITING_CLIENT_CONFIRMATION` | `CONFIRM_QUOTE`, `DECLINE_QUOTE`, `CANCEL` | `CANCEL` |
| `AWAITING_PAYMENT` | `OPEN_PAYMENT`, `CANCEL` conforme regra existente | `CANCEL` conforme regra existente |
| `ACCEPTED` | `OPEN_TRACKING`, ações existentes | `OPEN_TRACKING`, `ARRIVE`, ações existentes |
| `IN_PROGRESS` | ações existentes | ações existentes |
| terminal | nenhuma mutation de fluxo | nenhuma mutation de fluxo |

## `start_pittec_dispatch`

Assinatura existente, preservada:

```text
start_pittec_dispatch(
  p_service_id uuid,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy_meters double precision,
  p_captured_at timestamptz
) -> jsonb snapshot
```

Regras:

1. valida cliente, serviço e localização segundo as regras existentes;
2. retorna a chamada ativa do mesmo cliente de forma idempotente em vez de duplicá-la;
3. não exige pagamento prévio;
4. cria `SEARCHING`, preserva o ponto fixo e tenta a distribuição vigente;
5. se não houver candidato, mantém `SEARCHING` e define `next_dispatch_at`;
6. nunca usa `EXHAUSTED` por ausência momentânea.

Erros estáveis preservados/adicionados: `UNAUTHENTICATED`, `FORBIDDEN`, `INVALID_LOCATION`,
`SERVICE_UNAVAILABLE`, `ACTIVE_REQUEST_CONFLICT`.

## `get_my_active_pittec_dispatch`

Assinatura existente, preservada:

```text
get_my_active_pittec_dispatch() -> jsonb snapshot | null
```

Regras:

- expira lazy oferta/confirmacão cujo deadline do servidor venceu;
- avança lazy uma busca due como fallback do worker;
- retorna qualquer estado não terminal, inclusive `AWAITING_CLIENT_CONFIRMATION` e
  `AWAITING_PAYMENT`;
- não cria nova solicitação nem confirma proposta;
- snapshot terminal pode ser devolvido uma vez conforme a semântica existente de fechamento.

## `respond_to_mobile_dispatch` — branch PitTec

Assinatura pública existente é preservada para o card de oferta do prestador.

Mudanças contratuais para `PITTEC_AUTO_SEQUENTIAL`:

- a oferta inclui a proposta autoritativa antes da decisão;
- recusa/timeout encerra somente o appointment candidato e retorna a solicitação a `SEARCHING`;
- aceite válido sob lock muda appointment e solicitação para `AWAITING_CLIENT_CONFIRMATION`, define
  `accepted_at` e `client_confirmation_deadline = now() + interval '2 minutes'` e impede novas
  ofertas;
- o aceite não cria destino compartilhado, não inicia tracking e não marca `ACCEPTED`;
- aceite concorrente/stale retorna `OFFER_UNAVAILABLE` ou `REVISION_CONFLICT` sem reservar o segundo
  PitTec.

Mobile/guincho preservam a semântica vigente.

## `respond_to_pittec_quote`

Nova RPC:

```text
respond_to_pittec_quote(
  p_request_id uuid,
  p_appointment_id uuid,
  p_quote_version bigint,
  p_accept boolean,
  p_expected_revision bigint
) -> jsonb
```

Resposta:

```json
{
  "outcome": "CONFIRMED",
  "snapshot": {}
}
```

Outcomes:

- `CONFIRMED`: decisão gravada uma vez; transição para `AWAITING_PAYMENT`;
- `DECLINED`: solicitação e appointment cancelados; PitTec liberado; nenhuma cobrança criada;
- `CONFIRMATION_EXPIRED`: deadline venceu; cancelamento terminal aplicado; confirmação rejeitada;
- `REVISION_CONFLICT`: snapshot/proposta mudou; nenhuma decisão aplicada;
- `INVALID_TRANSITION`: solicitação não está aguardando decisão;
- `NOT_FOUND`: entidade não pertence ao cliente ou não é visível.

Idempotência:

- repetição da mesma confirmação para a mesma versão retorna `CONFIRMED` com snapshot atual sem
  duplicar efeitos;
- decisão oposta depois de uma decisão aplicada retorna `INVALID_TRANSITION`;
- confirmação com versão anterior nunca é reaproveitada;
- a validade é decidida por `now()` do banco, mesmo que o contador Android ainda mostre tempo.

## `cancel_pittec_search`

Assinatura existente com `request_id` e `expected_revision` é preservada.

Semântica:

- cliente em `SEARCHING`/`AWAITING_PROVIDER` cancela a chamada e a oferta corrente;
- cliente que recusa o preço usa `respond_to_pittec_quote(false)`, preservando motivo explícito;
- cancelamento do PitTec em `AWAITING_CLIENT_CONFIRMATION` invalida o card e recoloca a mesma
  solicitação em `SEARCHING`;
- após confirmação/pagamento, são aplicadas as regras de cancelamento financeiro existentes;
- qualquer estado terminal permanece monotônico diante de confirmação ou webhook tardio.

## Workers privados

### `advance_due_pittec_searches(p_limit integer)`

- seleciona `SEARCHING AND next_dispatch_at <= now()` com `FOR UPDATE SKIP LOCKED`;
- reutiliza `private.advance_pittec_dispatch` sem alterar raio, prioridade ou ordenação;
- cria no máximo uma oferta ativa por busca;
- reagenda quando não encontra candidato;
- limite default operacional pequeno (por exemplo, 100) e execução via pg_cron existente.

### `expire_due_pittec_confirmations(p_limit integer)`

- seleciona `AWAITING_CLIENT_CONFIRMATION` vencidos com `FOR UPDATE SKIP LOCKED`;
- marca appointment expirado/cancelado por `CLIENT_CONFIRMATION_TIMEOUT` e solicitação `CANCELLED`;
- libera o prestador, incrementa revisões e emite invalidações para ambos;
- é idempotente e não alcança proposta já confirmada.

O deploy deve verificar que pg_cron e os schedules foram criados; falha de agendamento não pode ser
silenciosamente aceita sem manter a expiração lazy como fallback.

## Reconciliação do pagamento

Qualquer caminho que persista `payments.status = APPROVED` chama a mesma rotina transacional privada:

1. trava solicitação e appointment na ordem canônica;
2. valida `dispatch_kind`, cliente, `request_id`, `appointment_id`, `amount`, moeda, `quote_version`,
   versão confirmada e estado `AWAITING_PAYMENT`;
3. se válido, muda appointment e solicitação para `ACCEPTED`, incrementa revisões e copia o ponto fixo
   original para `client_current_locations`;
4. em repetição, retorna o estado já reconciliado sem efeitos duplicados;
5. se a chamada estiver terminal ou a proposta estiver obsoleta, não reativa o atendimento e segue o
   tratamento de recuperação financeira existente.

`arrive_pittec_appointment` continua permitido somente em `ACCEPTED` e revalida que existe pagamento
`APPROVED` do mesmo cliente, appointment, valor e versão antes de mudar para `IN_PROGRESS`.
