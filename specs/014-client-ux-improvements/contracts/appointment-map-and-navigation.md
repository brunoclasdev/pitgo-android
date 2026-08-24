# Contract: Mapa do atendimento e navegação de retomada

Este contrato é comportamental (UI/navegação), não uma API de rede — documenta o que já muda de
protocolo nenhum, só a forma como os dados já existentes (posição do prestador em tempo real,
leitura pontual do cliente, revisão do atendimento) são apresentados e quando a navegação
automática pode disparar.

## Mapa do cliente (`AppointmentTrackingScreen`, US2)

- Câmera inicial enquadra a posição do próprio cliente (leitura pontual, ver
  `data-model.md` #3) e a posição do prestador (tempo real, já existente).
- Marcador do cliente é estático entre reaberturas da tela (não recentraliza sozinho a cada
  segundo); marcador do prestador se move conforme novas posições chegam pelo canal já existente
  (`appointment-location:<id>`).
- Se a posição do prestador ainda não chegou: mostrar a posição do cliente normalmente e um aviso
  textual de que a localização do guincho ainda não está disponível (não travar o mapa nem deixar
  em branco).
- Gesto manual do usuário (arrastar/zoom) suspende o recentramento automático até uma ação
  explícita retomá-lo — mesmo padrão de `followProvider` já existente, generalizado.

## Mapa do prestador (`ProviderIncomingRequestScreen`, US3 — novo)

- Câmera inicial enquadra a posição do prestador (tempo real) e a posição do cliente (leitura
  pontual repassada pelo snapshot do atendimento).
- Se o cliente ainda não tiver uma posição conhecida: mostrar aviso textual equivalente, nunca um
  marcador na origem (0,0) nem mapa vazio sem explicação.
- Mesma regra de suspensão de recentramento automático após gesto manual do prestador.

## Retomada automática de navegação (US4)

Contrato para o `LaunchedEffect` de retomada em `PitGoNavHost` (hoje reage a toda emissão de
`dispatchResumeState`, causando o botão voltar "travado"):

1. A navegação automática para a tela de um atendimento ativo (`AppointmentTracking` para cliente,
   `ProviderRequest` para prestador) só é disparada quando a `revision` do
   `snapshot.appointment`/busca observada for **maior** que a última revisão para a qual essa
   navegação já foi disparada nesta sessão do app.
2. Ao iniciar/retomar a sessão (login, cold start com app morto e reaberto), a navegação
   automática sempre é avaliada uma vez a partir da revisão restaurada — preservando o
   comportamento de retomada já coberto pelo cenário 3 de US4.
3. Um "voltar" manual do usuário **não** altera nem reseta a última revisão observada; ele apenas
   move a pilha de navegação. A próxima mudança real de revisão (novo evento, mudança de status)
   é que pode disparar a navegação automática de novo — nunca a simples repetição do mesmo
   snapshot.
4. Este contrato vale igualmente para `AppSessionState.Authenticated.Client` e
   `AppSessionState.Authenticated.ProviderReady`.
