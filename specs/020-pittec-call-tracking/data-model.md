# Data Model: Chamada e acompanhamento do PitTec

## Visão geral

A feature evolui entidades existentes e mantém `service_searches` como raiz da solicitação. A busca,
a oferta, a confirmação, o pagamento e o deslocamento são estados server-side; o Android consome um
snapshot e nunca infere uma transição apenas a partir da tela.

```text
service_searches (1)
  ├── (N) private.dispatch_candidates
  │         └── (1) appointments
  │                    ├── (N) payments
  │                    ├── (0..1) client_current_locations   # destino fixo após pagamento
  │                    └── (0..1) provider_current_locations # posição do PitTec após pagamento
  └── ponto original do atendimento
```

## Entidades e mudanças

### `service_searches` — solicitação PitTec

Tabela existente e fonte de identidade da chamada.

Campos relevantes existentes:

- `id`, `client_id`, `service_id`, `dispatch_kind`;
- `search_lat`, `search_long`, `search_accuracy_meters`, `search_captured_at`;
- `status`, `revision`, `created_at`, `updated_at`, `ended_at`;
- vínculo com o appointment/oferta ativa conforme a implementação atual.

Mudanças:

- ampliar o check de `status` com `AWAITING_CLIENT_CONFIRMATION` e `AWAITING_PAYMENT`;
- acrescentar `next_dispatch_at timestamptz`, nulo fora de `SEARCHING`, para reagendamento sem loop;
- preservar `search_lat`/`search_long` durante toda a solicitação, inclusive quando não há candidato;
- atualizar o índice único de chamada ativa para considerar `SEARCHING`, `AWAITING_PROVIDER`,
  `AWAITING_CLIENT_CONFIRMATION`, `AWAITING_PAYMENT`, `ACCEPTED` e `IN_PROGRESS`;
- incrementar `revision` em toda transição observável.

Invariantes:

- um cliente possui no máximo uma chamada PitTec ativa;
- ausência momentânea de candidato nunca muda a busca para `EXHAUSTED`;
- somente um appointment pode ocupar o papel de oferta/aceite corrente;
- o ponto original não é alterado por novas localizações do cliente;
- `next_dispatch_at` existe somente quando não há oferta ativa e a busca deve ser reavaliada.

### `private.dispatch_candidates` — histórico de ofertas

Tabela existente, mantida como auditoria da seleção e do preço apresentado ao PitTec.

Campos relevantes:

- `search_id`, `provider_id`, `appointment_id`;
- `distance_meters`, `quoted_price`, `status`;
- `offered_at`, `decided_at`.

Mudanças:

- o snapshot expõe `quoted_price` ao prestador enquanto sua oferta está `PENDING`;
- recusa, indisponibilidade e timeout encerram somente o candidato atual e reativam a busca;
- a unicidade `(search_id, provider_id)` continua impedindo reoferta ao mesmo prestador na mesma
  solicitação;
- statuses existentes são reutilizados; razões de encerramento ficam no appointment/cancelamento,
  evitando enum adicional sem consumidor atual.

Invariantes:

- o preço de uma oferta não é recalculado pelo aplicativo;
- apenas um candidato pode vencer, garantido por lock da solicitação e update condicionado à revisão;
- candidatos já decididos não voltam a `PENDING`.

### `appointments` — oferta, acordo e atendimento

Tabela existente que reserva o prestador e guarda a proposta aceita.

Campos relevantes existentes:

- `id`, `search_id`, `client_id`, `provider_id`, `service_id`, `dispatch_kind`;
- `status`, `quoted_price`, `response_deadline`, `revision`;
- `accepted_at`, `started_at`, `completed_at`, `cancelled_at`, `cancelled_by`.

Novos campos:

| Campo | Tipo | Regra |
|---|---|---|
| `quote_version` | `bigint NOT NULL DEFAULT 1` | Positivo; identifica o valor vigente. |
| `client_confirmation_deadline` | `timestamptz` | Definido como `accepted_at + interval '2 minutes'`. |
| `client_confirmed_at` | `timestamptz` | Preenchido somente por confirmação válida. |
| `client_confirmed_quote_version` | `bigint` | Deve ser igual a `quote_version` para habilitar pagamento. |

Mudanças de domínio:

- ampliar `status` com `AWAITING_CLIENT_CONFIRMATION` e `AWAITING_PAYMENT` para appointments PitTec;
- `response_deadline` continua exclusivo da resposta do prestador e não é reutilizado;
- `quoted_price` deve ser positivo para o fluxo PitTec pago e permanece imutável dentro da versão;
- atualizar `one_active_appointment_per_provider` e toda consulta de reserva/disponibilidade para
  incluir os dois estados novos.

Checks de coerência:

- `AWAITING_CLIENT_CONFIRMATION` exige `accepted_at`, `client_confirmation_deadline`, preço e versão;
- `AWAITING_PAYMENT` exige `client_confirmed_at` e versão confirmada igual à vigente;
- `ACCEPTED` PitTec exige confirmação vigente e pagamento vinculado `APPROVED` pelo mesmo valor;
- uma mudança de preço incrementa `quote_version`, limpa os campos de confirmação e retorna para
  `AWAITING_CLIENT_CONFIRMATION` com novo deadline;
- estados terminais não podem ser reativados por confirmação ou webhook tardio.

### `payments` — tentativa de pagamento

Tabela existente; meios de pagamento e estados permanecem os definidos na feature `018`.

Novos vínculos:

| Campo | Tipo | Regra |
|---|---|---|
| `appointment_id` | `uuid NULL REFERENCES appointments(id)` | Obrigatório logicamente para pagamento PitTec novo. |
| `quote_version` | `bigint` | Obrigatório para PitTec e igual à versão confirmada. |

Invariantes para `PITTEC_AUTO_SEQUENTIAL`:

- `request_id`, `client_id`, `appointment_id`, `amount` e `quote_version` são resolvidos/validados no
  servidor;
- `amount = appointments.quoted_price` e o pagamento usa `BRL`;
- somente tentativa associada ao appointment e versão vigentes pode promover o fluxo;
- `PENDING`, `REJECTED` ou falha mantém a solicitação em `AWAITING_PAYMENT` ou segue o cancelamento
  existente; nunca abre tracking;
- uma aprovação tardia após cancelamento não reativa entidades terminais e é encaminhada para a
  recuperação financeira já existente.

### `client_current_locations` — local fixo do atendimento

Tabela existente, mas com semântica específica para PitTec:

- nenhum registro compartilhável é criado durante oferta, aceite ou confirmação;
- ao reconciliar o pagamento aprovado, o backend copia uma vez o ponto original de
  `service_searches` para o appointment;
- `publish_client_location` rejeita updates para appointments PitTec, impedindo movimento do destino;
- o registro é legível por cliente e PitTec somente em `ACCEPTED`/`IN_PROGRESS`;
- o ciclo de vida/limpeza terminal permanece o existente.

### `provider_current_locations` — posição do PitTec

Tabela existente e atualizada pelo foreground service do prestador.

- antes do pagamento, publicações permanecem no contexto de discovery, sem exposição exata ao
  cliente;
- em `ACCEPTED`/`IN_PROGRESS`, a publicação é vinculada ao appointment;
- conserva latitude, longitude, precisão, captura, versão e freshness já existentes;
- após cancelamento/conclusão, a posição específica deixa de ser publicada/legível.

### Snapshots de rota existentes

`appointment_route_snapshots` e a Edge Function `pittec-route` permanecem opcionais e não ganham
responsabilidade nova. A distância direta entre posição vigente e destino fixo é autoritativa para a
feature; um valor de rota só pode ser usado se sua revisão corresponder à posição atual.

## Máquina de estados

| Estado da solicitação/appointment | Entrada | Ação permitida | Próximo estado |
|---|---|---|---|
| `SEARCHING` | criação, recusa/timeout de oferta ou cancelamento do PitTec antes da decisão do cliente | cliente cancela; worker procura candidato | `AWAITING_PROVIDER` ou `CANCELLED` |
| `AWAITING_PROVIDER` / appointment `PENDING` | oferta criada | PitTec aceita/recusa; cliente cancela; oferta expira | `AWAITING_CLIENT_CONFIRMATION`, `SEARCHING` ou `CANCELLED` |
| `AWAITING_CLIENT_CONFIRMATION` | primeiro aceite válido do PitTec | cliente confirma/recusa; PitTec cancela; prazo expira | `AWAITING_PAYMENT`, `SEARCHING` ou `CANCELLED` |
| `AWAITING_PAYMENT` | cliente confirmou a versão vigente | pagamento é tentado/retomado; participantes cancelam conforme regra existente | `ACCEPTED` ou `CANCELLED` |
| `ACCEPTED` | pagamento válido aprovado e destino materializado | PitTec desloca/publica posição e marca chegada | `IN_PROGRESS` ou `CANCELLED` |
| `IN_PROGRESS` | PitTec chegou/iniciou | fluxo pós-chegada existente | `COMPLETED` ou `CANCELLED` |
| `COMPLETED` / `CANCELLED` | encerramento | nenhuma transição reativa | terminal |

Notas:

- não encontrar candidato em uma execução mantém `SEARCHING` e apenas move `next_dispatch_at`;
- se o PitTec cancelar em `AWAITING_CLIENT_CONFIRMATION`, o card fica inválido e a mesma solicitação
  retorna a `SEARCHING`; depois da confirmação, aplicam-se as regras existentes de cancelamento e
  pagamento, sem troca silenciosa de proposta;
- confirmação e expiração concorrentes são serializadas pelos locks; o instante do servidor decide;
- dois aceites concorrentes são serializados pelo lock de `service_searches`; somente o appointment
  ainda corrente pode avançar.

## Índices e autorização afetados

- reconstruir checks/índices parciais que enumeram estados ativos para incluir os intermediários;
- indexar buscas due por `(status, next_dispatch_at)` para o worker;
- manter unicidade ativa por cliente e por prestador;
- atualizar helpers de disponibilidade, start/restore/cancel e snapshots para os novos estados;
- manter Realtime de localização inacessível nos estados intermediários;
- corrigir autorização do tópico `payment:<request_id>` por helper privado `SECURITY DEFINER`, com
  `search_path = ''`, execução revogada do público e grants mínimos;
- todas as RPCs derivam identidade de `auth.uid()`; IDs fornecidos pelo APK são seletores, nunca prova
  de autorização.

## Retenção e limpeza

- tentativas/candidatos e payments continuam como histórico auditável segundo a política existente;
- confirmação expirada encerra appointment e solicitação, mas não apaga a evidência da decisão;
- cancelamento/conclusão removem ou tornam inacessível o contexto de localização conforme as rotinas
  existentes;
- a migration é forward-only e não reescreve migrations `015`, `018` ou `019`.
