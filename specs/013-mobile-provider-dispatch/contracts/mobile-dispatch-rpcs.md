# Contract: RPCs de despacho móvel

## Regras comuns

- Todas as funções públicas são `SECURITY DEFINER SET search_path = ''` e usam nomes totalmente
  qualificados.
- `EXECUTE` é revogado de `public`/`anon` e concedido somente a `authenticated`.
- Identidade, papel, client e provider são derivados de `auth.uid()` e `account_roles`; nenhum RPC
  aceita owner confiável do aplicativo.
- Inputs são validados antes do primeiro write. Locks seguem busca → provider →
  appointment/candidato.
- Toda função de estado reconcilia appointment `PENDING` cujo deadline venceu antes de responder.
- Resultado esperado é JSON sanitizado. Exceções ficam reservadas para sessão/papel, input inválido
  e indisponibilidade inesperada; nunca devolvem coordenada alheia, CNPJ, contato ou detalhe SQL.

## Projeção comum

```json
{
  "outcome": "UPDATED",
  "server_now": "2026-08-23T18:00:00Z",
  "search_id": "uuid",
  "service_id": "uuid",
  "status": "OPTION_FOUND",
  "revision": 4,
  "radius_meters": 10000,
  "candidate": null,
  "appointment": null,
  "allowed_actions": ["NEXT", "REQUEST", "CANCEL"]
}
```

Outcomes fechados incluem `UPDATED`, `ACTIVE_SEARCH_RETURNED`, `ALREADY_APPLIED`,
`STALE_REVISION`, `CANDIDATE_INVALIDATED`, `EXHAUSTED` e `NO_ACTIVE_FLOW`. A UI nunca interpreta
mensagem livre.

## Publicação de discovery

### `publish_mobile_provider_location`

Inputs: latitude, longitude, accuracy, captured timestamp, tracker UUID e sequência.

Valida provider próprio ativo, `MOBILE`, com intenção disponível; precisão `<=100 m`, captura
`<=30 s` e não mais de `10 s` no futuro. Um tracker novo não assume uma lease recente de outro
tracker. Update antigo é ignorado; update aceito incrementa versão. Quando existe appointment
`ACCEPTED/IN_PROGRESS`, o servidor projeta o fix somente no tracking privado e não torna o provider
descobrível.

### `stop_mobile_provider_location`

Recebe apenas tracker UUID. Apaga a linha somente quando owner e tracker correspondem. Alteração
para indisponível/fixo/inativo também invalida a linha por trigger/RPC.

## Busca do cliente

### `start_mobile_dispatch`

Inputs: `p_search_id`, `p_service_id`, latitude, longitude, accuracy e captured timestamp.

- exige conta `CLIENT` e serviço `MOBILE_SEQUENTIAL` ativo;
- `p_search_id` é gerado uma vez por tentativa lógica e reutilizado após resposta perdida;
- busca ativa do mesmo `(client, service)` é retornada, mesmo com outra chave;
- cria raio 10 km e escolhe, na mesma transação, o primeiro candidato elegível;
- expande 10 → 25 → 50 km quando necessário; só então retorna `EXHAUSTED`.

### `get_my_active_mobile_dispatch`

Sem owner/ID arbitrário. Para CLIENT retorna sua busca não terminal mais recente; para PROVIDER
retorna sua solicitação pendente ou appointment reservado; caso contrário `NO_ACTIVE_FLOW`.
Inclui `server_now` e ações permitidas. É chamado no bootstrap, subscribe e reconnect.

### `next_mobile_dispatch_candidate`

Inputs: search ID, candidate ID e expected revision.

Marca somente o candidato corrente como `CLIENT_DISMISSED` e seleciona o próximo. Replay da mesma
revisão/candidato retorna o snapshot já avançado e nunca descarta dois providers.

### `request_mobile_dispatch_candidate`

Inputs: search ID, candidate ID e expected revision.

Revalida posição recente, oferta, provider e ausência de lease concorrente. Cria no máximo um
appointment por candidato, copia `price_snapshot`, define `PENDING` e deadline server-side de 60 s.
Replay retorna o mesmo appointment/cotação.

## Decisão e ciclo do provider

### `get_my_pending_mobile_dispatch`

Retorna somente a solicitação `PENDING` destinada ao provider autenticado, já reconciliada com o
deadline. Não aceita provider ID.

### `respond_to_mobile_dispatch`

Inputs: appointment ID, `p_accept` e expected revision.

Somente o provider owner responde. Aceite depois do deadline é impossível mesmo se o cron atrasar.
Aceite confirma reserva e remove discovery; recusa registra `PROVIDER_REJECTED`, libera a lease e
avança a busca na mesma transação. Repetição da mesma decisão retorna snapshot; decisão oposta
após terminal retorna `STALE_REVISION`.

### `start_mobile_appointment`

Somente participante autorizado, conforme regra do produto adotada na implementação, transforma
`ACCEPTED` em `IN_PROGRESS`. Não altera preço nem cria outro appointment.

### `cancel_mobile_appointment`

Inputs: appointment ID, motivo e expected revision. O servidor deriva `cancelled_by` do papel.
Motivo sofre trim, é obrigatório e tem limite documentado pelo schema.

- CLIENT: appointment/search terminam e não há nova busca.
- PROVIDER: appointment termina, candidato vira `PROVIDER_CANCELLED` e a mesma busca avança sem
  repetir candidatos anteriores.

### `complete_mobile_appointment`

Conclui `IN_PROGRESS`, encerra a busca, remove tracking privado e libera a reserva. O provider só
volta à discovery após publicar um fix corrente e continuar ativo/móvel/disponível.

## Expiração

`private.expire_due_mobile_dispatch_requests(p_limit)` usa `FOR UPDATE SKIP LOCKED`, marca
appointments vencidos como `EXPIRED`, candidatos como `PROPOSAL_EXPIRED` e avança suas buscas.
Um job Supabase Cron executa a função em intervalo de um segundo, condicionado à versão suportada
do Postgres. Todas as RPCs repetem a reconciliação para garantir que aceite tardio nunca passe.

## Segurança e grants

- RLS habilitada em todas as tabelas públicas novas.
- `anon` não possui SELECT/DML/EXECUTE.
- `authenticated` não possui DML direto nas tabelas de despacho/localização.
- `private.dispatch_candidates` e helpers não têm grants para o APK.
- `provider_discovery_locations` não possui SELECT direto; somente o snapshot do candidato corrente
  pode revelar sua posição à conta CLIENT proprietária da busca.
- índices parciais protegem uma busca ativa por `(client, service)` e um provider ocupado por vez.
- `discover_service_providers` mantém providers FIXED por endereço; MOBILE usa somente dispatch.
