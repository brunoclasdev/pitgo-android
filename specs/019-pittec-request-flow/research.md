# Phase 0 Research: Fluxo correto de solicitação do PitTec

## 0. Fluxo real hoje, confirmado lendo o código e o backend ao vivo

**Decision**: Documentar o comportamento REAL verificado (não o que a spec `015`/`018` descreviam
como intenção), como base para as decisões abaixo.

**Verificado**:

1. `HomeRoute` navega diretamente para `PitGoRoutes.Payment` quando `dispatchKind ==
   PITTEC_AUTO_SEQUENTIAL` (`PitGoNavHost.kt`) — o cliente paga a tarifa fixa de plataforma **antes**
   de ver qualquer mapa ou botão de solicitar.
2. Ao aprovar o pagamento, `PaymentCheckoutRoute.onApproved` navega para `PitGoRoutes.pitTec(serviceId,
   requestId)` com `popUpTo(Home)` — o `requestId` já pago vira `initialRequestId` de `PitTecViewModel`.
3. Com `initialRequestId != null`, `PitTecViewModel.init` chama `startDispatch()` imediatamente — a
   busca começa sozinha, sem qualquer toque do cliente na tela do PitTec.
4. `private.advance_pittec_dispatch` (função real, lida via `pg_get_functiondef` no projeto Supabase
   compartilhado) escolhe o candidato mais próximo ainda não tentado e cria a `appointment` `PENDING`
   **no mesmo passo**, notificando o prestador imediatamente — não existe hoje nenhuma etapa
   intermediária em que o cliente veja ou aprove um candidato específico antes do prestador ser
   avisado.
5. `private.pittec_dispatch_snapshot` (idem) só preenche o campo `provider` (identidade do
   prestador) quando `search.status in ('ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED')` — antes
   disso, o cliente não recebe nome nem distância de nenhum candidato. Distância aproximada
   (`offer.approximate_distance_meters`) só é enviada ao **prestador**, nunca ao cliente.
6. `public.start_pittec_dispatch` exige `payments.request_id = p_search_id` com `status = 'APPROVED'`
   antes de sequer criar a busca — é este o gate real de pagamento hoje, não uma tela separada de
   "valores" depois de encontrar alguém.
7. O card de estimativa fictícia (`EstimateGatedDispatchOverlay`/`EstimateCard`, resquício de
   `017-pittec-mocked-ui`) só aparece quando `dispatch == PitTecDispatchPhase.None` — na prática,
   isso nunca acontece no caminho feliz atual (que já chega com `initialRequestId` e pula direto para
   `Locating`/`Active`); é código morto no fluxo real, alcançável só por uma inconsistência de
   restore.

**Conclusão prática**: o problema relatado não é "o card de estimativa aparece errado" — é que **o
pagamento acontece cedo demais (antes de qualquer busca) e o cliente nunca vê quem foi encontrado
antes do prestador já ter sido acionado**. As duas correções desta spec atacam exatamente isso.

## 1. Onde mover o gate de pagamento

**Decision**: Remover a guarda `PAYMENT_REQUIRED` de `start_pittec_dispatch` (a busca passa a ser
gratuita para iniciar, como a spec pede) e adicioná-la a `arrive_pittec_appointment` — a transição
`ACCEPTED → IN_PROGRESS`, hoje disparada pelo prestador ao "chegar".

**Rationale**: É o ponto real mais próximo de "o serviço começa" que já existe como uma função SQL
`SECURITY DEFINER` dedicada, sem exigir uma nova RPC nem um novo status de `appointments`/
`service_searches`. Na prática, o cliente confirma e paga a tarifa (card de valores) assim que o
prestador aceita — segundos depois — muito antes de o prestador chegar fisicamente; o gate em
`arrive_pittec_appointment` funciona como rede de segurança server-side (nunca confiar só na UI do
cliente), sem bloquear a experiência percebida pelo cliente, que já avança da tela de valores para o
acompanhamento assim que o próprio pagamento (chamado pelo client) é aprovado.

**Alternatives considered**:
- Manter o gate em `start_pittec_dispatch`: rejeitado — é exatamente o comportamento que a spec pede
  para corrigir (cobra antes de saber quem vai atender).
- Criar uma nova RPC `confirm_pittec_service`/um novo status intermediário entre `ACCEPTED` e
  `IN_PROGRESS`: rejeitado por YAGNI — `arrive_pittec_appointment` já é o único ponto de transição
  controlado pelo prestador depois do aceite; reaproveitá-lo evita uma tabela de estados nova.

## 2. Como o cliente vê o candidato encontrado antes do aceite

**Decision**: Estender `private.pittec_dispatch_snapshot` com um novo campo `candidate` (nome do
prestador + distância aproximada arredondada, mesma técnica de arredondamento de 500m já usada em
`offer` para o prestador), visível ao **cliente** enquanto `appointment.status = 'PENDING'` — hoje
esse mesmo dado (nome do prestador via `providers.name`, distância via
`private.dispatch_candidates.distance_meters`) já é lido pela própria função para montar `offer`
(destinado ao prestador); o novo campo só espelha isso para o outro participante.

**Rationale**: Reaproveita dados que a função já consulta na mesma linha (`candidate_row`), evitando
uma nova consulta ou uma RPC adicional. Distância arredondada a 500m preserva o mesmo nível de
precisão já considerado aceitável para o prestador ver a posição aproximada do cliente — simetria,
não um padrão novo de privacidade.

**Alternatives considered**:
- Uma RPC nova só para "buscar o candidato atual": rejeitado — o snapshot já é observado via
  Realtime/polling pelo cliente; duplicar a fonte de dado violaria DRY sem necessidade.
- Expor a posição exata do prestador antes do aceite: rejeitado — a spec só pede nome e distância, e
  a posição exata do prestador continua reservada para depois do aceite (`route`/tracking, já
  existente), sem mudança de escopo de privacidade além do que foi pedido.

## 3. Recusar o candidato encontrado

**Decision**: "Recusar" no card de candidato encontrado reaproveita a RPC de cancelamento antes do
aceite já existente (`repository.cancel(searchId, revision)` / `cancel_pittec_search` no backend).
Cancelar a busca com um candidato `PENDING` associado já cancela esse `appointment` e libera
`advance_pittec_dispatch` para tentar o próximo candidato — nenhuma RPC nova é necessária.

**Rationale**: É exatamente a mesma ação que o cliente já tem disponível hoje durante
`SEARCHING`/`AWAITING_PROVIDER` (botão "Cancelar chamada"); a única mudança é o **contexto** em que
ela é oferecida (agora com um candidato nomeado visível, não uma busca anônima).

## 4. Ordem final dos dois cards e o pagamento

**Decision**: A ordem é: (1) card "PitTec encontrado" (nome + distância) assim que
`appointment.status = PENDING` e `search.status = AWAITING_PROVIDER` — confirmar aqui só mantém a
espera pelo prestador, sem cobrar nada; (2) quando o prestador aceita (`status = ACCEPTED`), o card
de "Valores" substitui a navegação automática para o acompanhamento — confirmar aqui aciona
`mp-create-payment` (`018`, já existente, mesmo `request_id` = `search_id`) com a tarifa fixa de
plataforma; (3) só após o pagamento aprovado o cliente navega para `AppointmentTracking`, como já
acontece hoje.

**Rationale**: Corresponde exatamente à ordem descrita pelo usuário ("ao confirmar o pittec... card
de valores... ao confirmar tudo o serviço começa") e ao dado real disponível em cada momento — o
valor cobrado não muda entre um candidato e outro (tarifa fixa, `016`), então não há necessidade de
mostrá-lo antes de haver um prestador de fato interessado.

**Alternatives considered**:
- Pedir pagamento junto com a confirmação do candidato encontrado (antes do aceite do prestador):
  rejeitado — o prestador ainda pode recusar/expirar, e o dinheiro do cliente ficaria retido por um
  aceite que nunca aconteceu, exigindo estorno automático imediatamente depois de cobrar; adiar a
  cobrança para depois do aceite evita esse ciclo cobra→estorna desnecessário.

## 5. Remoção do código do protótipo mockado

**Decision**: Remover `EstimateGatedDispatchOverlay`/`EstimateCard` (e o `ServiceEstimate`/
`DistancePerimeter` que só existiam para alimentá-lo) de `PitTecScreen.kt`, já que passam a ser
substituídos pelos dois cards reais desta spec.

**Rationale**: Constituição V — "código obsoleto diretamente relacionado" deve ser removido quando
for seguro; este código é resquício do protótipo `017` e não tem mais nenhum consumidor depois desta
mudança. Os demais artefatos do mock (`PitTecMockModels`, `PitTecClientMockViewModel`, etc.,
mantidos intencionalmente desde o revert de `017`) não são tocados — não têm relação com esta spec.
