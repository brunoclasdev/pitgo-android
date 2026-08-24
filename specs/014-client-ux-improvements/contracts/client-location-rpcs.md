# Contract: RPCs de localização pontual do cliente durante o atendimento

Corrige uma lacuna do desenho inicial (Phase 1): a leitura pontual de GPS do cliente (decisão de
`/speckit-clarify`) precisa atravessar o backend para que o **prestador** também a veja no próprio
mapa (US3) — não pode viver só em memória no dispositivo do cliente. Este contrato espelha o
padrão já usado por `publish_provider_location`/`provider_current_locations` (feature 013), sem os
campos de lease entre trackers (aqui é uma leitura única por abertura de tela, não um stream
contínuo disputado por múltiplos publicadores).

## Regras comuns

- `publish_client_location` e `get_appointment_client_location` são `SECURITY DEFINER
  SET search_path = ''`, com `EXECUTE` revogado de `public`/`anon` e concedido só a `authenticated`.
- Participante é derivado de `auth.uid()` contra `appointments.client_id`/`provider.account_id` —
  mesma verificação já usada por `appointments_select_participant` (feature 013).
- Só é possível publicar/ler enquanto o atendimento estiver `ACCEPTED` ou `IN_PROGRESS`; fora
  disso, a linha é removida (mesmo padrão de limpeza de `provider_current_locations`).
- `public.client_current_locations` tem RLS habilitada e nenhum grant direto de tabela ao APK.

## `publish_client_location(p_appointment_id, p_latitude, p_longitude, p_accuracy_m, p_captured_at)`

Chamável apenas pelo cliente dono do atendimento. Valida faixas de latitude/longitude, precisão
`<=100 m` e captura recente (mesma janela de tolerância já usada em `publish_provider_location`).
Faz upsert da única linha do atendimento e dispara o mesmo evento de broadcast (`CHANGED`) já usado
pelo canal privado `appointment-location:<id>` (feature 013) — nenhuma nova policy de
`realtime.messages` é necessária; o canal já autoriza os dois participantes do atendimento.

Chamado pelo app do cliente ao abrir a tela de acompanhamento e, se o usuário sair e voltar, a cada
reabertura (nunca em background/serviço contínuo — ver `data-model.md` #3 e a decisão de
clarificação).

## `get_appointment_client_location(p_appointment_id)`

Retorna a posição pontual mais recente do cliente para o atendimento, ou `NOT_FOUND` quando ainda
não houve nenhuma publicação. Chamável pelos dois participantes (cliente e prestador) — é a RPC que
o novo mapa do prestador (US3) usa para saber onde posicionar o marcador de destino.

```json
{
  "outcome": "FOUND",
  "location": {
    "latitude": -23.55,
    "longitude": -46.63,
    "accuracy_m": 15.0,
    "captured_at": "2026-08-23T18:00:00Z",
    "published_at": "2026-08-23T18:00:01Z"
  }
}
```

Outcomes fechados: `FOUND`, `NOT_FOUND` (ver Edge Case "mapa do guincho sem localização do cliente"
no spec — deve gerar aviso textual, nunca marcador na origem).

## Autorização

Só os dois participantes do atendimento (o cliente dono e o prestador aceito) podem ler a posição;
nenhum outro cliente, prestador ou conta anônima tem acesso — mesmo modelo de autorização já
validado para `provider_current_locations`.
