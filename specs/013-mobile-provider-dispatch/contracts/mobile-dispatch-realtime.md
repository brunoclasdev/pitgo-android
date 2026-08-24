# Contract: Realtime e publicação contínua

## Tópicos privados

```text
mobile-dispatch:<searchId>
provider-dispatch:<providerId>
```

RLS em `realtime.messages` permite receber apenas quando:

- `extension = 'broadcast'`;
- a conta CLIENT é proprietária da busca no primeiro tópico;
- a conta PROVIDER é proprietária do provider no segundo tópico.

Não existe policy INSERT para o APK. Triggers/funções do banco emitem eventos após transições.

## Payload de invalidação

```json
{
  "search_id": "uuid-or-null",
  "appointment_id": "uuid-or-null",
  "status": "AWAITING_PROVIDER",
  "revision": 7,
  "response_deadline": "2026-08-23T18:01:00Z"
}
```

O payload não contém coordenadas, preço, nome, contato, CNPJ, motivo de cancelamento ou ponto do
cliente. Ele apenas acorda o Repository, que busca o snapshot autorizado.

Updates da posição do provider corrente também invalidam `mobile-dispatch:<searchId>` somente para
a busca cuja proposta atual referencia aquele provider. Assim o carro pode se mover no mapa sem
expor a linha a outros clientes.

## Sequência de assinatura

1. Validar a sessão/owner esperados.
2. Criar collectors antes de subscrever.
3. Chamar `realtime.setAuth()` e assinar canal privado aguardando confirmação.
4. Consultar `get_my_active_mobile_dispatch()`/`get_my_pending_mobile_dispatch()`.
5. Em evento, buscar novo snapshot; aceitar somente `revision > acceptedRevision` ou snapshot
   semanticamente terminal da mesma revisão.
6. Em reconnect/rejoin, repetir autenticação, subscribe e snapshot.
7. Em cancelamento/logout, remover canal em `NonCancellable`, zerar estado/IDs imediatamente e
   relançar `CancellationException`.

Broadcast perdido não perde estado; Broadcast duplicado não duplica comando; evento antigo nunca
move o fluxo para trás.

## Foreground service

`ProviderLocationTrackingService` possui um stream sequencial e conflated: no máximo um publish em
voo e somente o fix mais recente aguardando. Não usa `collectLatest` para cancelar continuamente
uma RPC lenta e não mantém fila offline.

### Início

- ação visível no Dashboard;
- provider `MOBILE`, ativo, com intenção disponível;
- FINE concedida e Location Settings compatíveis;
- `startForeground()` imediato; notification permission é recomendada, mas não bloqueia legalmente
  o FGS;
- session bootstrap idempotente também funciona no processo recriado pelo service.

### Continuidade

- target aproximado 10 s, fix <=100 m e idade <=30 s;
- `START_STICKY` é best-effort, sem boot receiver/restart invisível;
- foreground notification e status in-app explicam compartilhamento;
- swipe da task não encerra publicação; user stop/force-stop/reboot deixa o heartbeat stale e exige
  retomada visível posterior;
- preferência técnica pode guardar tracker/account/sequence, nunca coordenada.

### Interrupção/degradação

- indisponível/fixo/inativo/logout/permissão revogada/location disabled: best-effort stop remoto e
  fim do service;
- offline/fix impreciso: manter apenas o fix atual em memória; após 30 s servidor exclui discovery;
- notificação “Ficar indisponível” sincroniza backend antes de parar;
- durante appointment, essa ação abre cancelamento com motivo em vez de cancelar silenciosamente;
- aceite projeta no tópico privado `appointment-location:<appointmentId>` já existente;
- terminal remove tracking privado e volta à discovery somente com novo fix elegível.

## Snapshot versus countdown

`response_deadline` e `server_now` ancoram a exibição local. O ticker de 1 segundo não é
autoritativo nem live region. Ao exibir zero, a UI mostra “Tempo esgotado. Atualizando…” e solicita
snapshot. Apenas a transição persistida pelo servidor muda o estado.
