# Contract: UI, navegação e mapa da chamada PitTec

## Fonte de estado

Composables renderizam uma fase imutável produzida pelo ViewModel; ações chamam o ViewModel e nunca
mudam o fluxo somente na UI. O snapshot server-side é a autoridade para busca, proposta, confirmação,
pagamento e autorização de tracking.

## Estados do cliente

| Fase Kotlin | Estado server-side | Conteúdo e ação principal |
|---|---|---|
| `None` | nenhuma chamada | botão `Chamar PitTec` |
| `Restoring` | desconhecido | loading não interativo |
| `Locating` | criação pendente | progresso e tratamento de permissão/GPS |
| `StartingSearch` | start em voo | evita novo toque/duplicata |
| `Searching` | `SEARCHING`/`AWAITING_PROVIDER` | mensagem persistente de busca e `Cancelar chamada` |
| `AwaitingClientConfirmation` | estado homônimo | card com PitTec, valor BRL, contador e `Confirmar`/`Recusar` |
| `AwaitingPayment` | estado homônimo | status de pagamento e ação para abrir/retomar checkout |
| `TrackingReady` | `ACCEPTED`/`IN_PROGRESS` | navega/retoma acompanhamento do appointment |
| `Terminal` | `CANCELLED`/`COMPLETED` | resultado compreensível e retorno seguro |
| `Failure` | falha recuperável | mensagem acionável e retry/restore |

Regras:

- remover/bypassar `CandidateFound` e as ações `confirmCandidate`/`declineCandidate` da feature `019`;
  antes do aceite o cliente apenas acompanha a busca;
- polling e Realtime continuam em todas as fases não terminais, não apenas em `Searching`;
- o card usa `quote.amount`, `currency`, `version` e `client_confirmation_deadline` do snapshot;
- o contador é calculado com a diferença entre deadline e `server_now`, segue atualizando visualmente
  e desabilita ações ao chegar a zero, mas a RPC decide validade;
- confirmar desabilita os botões enquanto a mutation está em voo e trata repetição idempotente;
- recusar exige apenas a confirmação de intenção já adotada no produto e encerra a chamada;
- card stale por mudança de revisão/versão é removido imediatamente e não aceita toque tardio.

## Estados do PitTec

| Estado | Experiência |
|---|---|
| oferta `PENDING` | card real com distância/região aproximadas, preço autoritativo em BRL, prazo e aceitar/recusar |
| `AWAITING_CLIENT_CONFIRMATION` | identifica que o aceite foi registrado e aguarda decisão do cliente; não mostra deslocamento iniciado |
| `AWAITING_PAYMENT` | informa que o cliente confirmou e o pagamento está pendente; não abre mapa de deslocamento |
| `ACCEPTED` | pagamento aprovado; ativa lease de appointment e abre/permite abrir acompanhamento |
| `IN_PROGRESS` | mantém fluxo pós-chegada existente |
| terminal | interrompe lease/observação específicos e volta ao dashboard adequado |

O dashboard real deixa de usar `ServiceEstimate` ou faixas fictícias para a oferta. O observer do
prestador restaura `PITTEC_AUTO_SEQUENTIAL`, não somente mobile dispatch.

## Navegação

```text
Home
  └── PitTec
       ├── busca/confirmacão na mesma rota
       ├── confirmar valor
       │    └── PaymentCheckout(requestId, appointmentId, quoteVersion, displayAmount)
       │          ├── PENDING/REJECTED -> permanece/retorna a AwaitingPayment
       │          └── APPROVED -> snapshot ACCEPTED
       └── AppointmentTracking(appointmentId)
```

- `displayAmount` serve somente para renderizar; a Edge Function resolve o valor novamente no
  servidor.
- O checkout PitTec não carrega `services.minimum_price` como preço do appointment.
- `APPROVED` não navega diretamente confiando no callback local: primeiro restaura o snapshot que deve
  estar `ACCEPTED`/`tracking_authorized=true`.
- Ao pressionar Voltar no checkout, o request continua em `AWAITING_PAYMENT` segundo o fluxo de
  pagamento existente e pode ser retomado.
- Ao reabrir o app, restore escolhe a tela/card pelo snapshot; não cria outra chamada.
- O PitTec só navega automaticamente para tracking depois de `ACCEPTED`; aceite da oferta, por si só,
  leva ao estado de espera.

## Contrato do mapa compartilhado

`AppointmentTrackingScreen` atende os dois perfis com o mesmo estado:

- `serviceLocation`: ponto fixo capturado na criação da chamada;
- `providerLocation`: última posição válida do PitTec, versão e timestamp;
- `distanceMeters`: Haversine entre as duas posições vigentes, arredondada apenas para exibição;
- `isProviderLocationStale`: calculado pelo limiar de freshness existente;
- `viewportMode`: `FOLLOW_BOTH` ou `MANUAL`.

### Enquadramento

1. No primeiro par válido, `FOLLOW_BOTH` aplica bounds com padding para manter os dois marcadores
   visíveis.
2. Enquanto não houver interação manual, uma posição que sair do enquadramento pode disparar novo
   ajuste suave; não há salto em toda atualização se ambos já estão visíveis.
3. Pan/zoom do usuário muda para `MANUAL` e atualizações passam a mover somente o marcador.
4. A ação visível `Reenquadrar` volta a `FOLLOW_BOTH` e enquadra imediatamente os dois pontos.
5. Rotação/recriação preserva o estado de tracking e reenquadra somente quando ainda não existe uma
   viewport válida restaurável.

### Marcadores, distância e falhas

- Marcadores têm ícones/rótulos distintos: `PitTec` e `Local do atendimento`; significado não depende
  apenas de cor.
- Se os pontos se sobrepuserem, mostrar `Chegou` ou distância zero sem ocultar a identificação.
- A cada versão válida do PitTec, mover marcador e recalcular distância sem reload.
- Distância de rota só é usada se a revisão da rota coincidir com a localização atual; caso contrário,
  mostrar distância direta.
- Sem posição inicial, mostrar o local fixo e estado `Aguardando localização do PitTec`, não marcador
  inventado.
- Em stale/erro, manter o último marcador, sinalizar horário/indisponibilidade e retomar
  automaticamente na próxima versão válida.
- Falta de permissão/GPS mostra ação compatível com o papel: configuração/restabelecimento ao PitTec;
  cliente não recebe pedido de background location.

## Publicação do cliente e destino

Ao abrir tracking PitTec como cliente, `AppointmentTrackingViewModel` não chama
`publish_client_location`. A localização exibida vem do ponto original materializado pelo backend após
pagamento. Esse comportamento é condicionado a `dispatch_kind == PITTEC_AUTO_SEQUENTIAL`; mobile e
guincho mantêm o comportamento atual.

## Foreground tracking do PitTec

- A entrada em `ACCEPTED` ativa/retoma `ProviderLocationTrackingService` com target do appointment.
- A notificação persistente explica que a localização está sendo compartilhada durante o
  deslocamento e oferece o caminho vigente de retorno ao atendimento.
- Tela bloqueada ou app em segundo plano não encerra o lease.
- Permissão revogada, GPS desligado ou restrição de início do foreground service vira erro acionável e
  deixa a posição anterior stale no cliente.
- `CANCELLED`/`COMPLETED` encerra target, publicação e notificação específicas do atendimento.

## Mensagens mínimas de estado

- busca: "Continuamos procurando um PitTec";
- confirmação: "Confirme o valor em mm:ss";
- prestador aguardando cliente: "Aguardando confirmação do cliente";
- ambos aguardando pagamento: "Aguardando aprovação do pagamento";
- localização stale: "Última localização recebida há …";
- permissão/GPS/rede: mensagem específica com `Tentar novamente` ou `Abrir configurações` quando
  aplicável.

Textos finais devem vir de resources Android e respeitar fonte ampliada, contraste, áreas de toque e
content descriptions.
