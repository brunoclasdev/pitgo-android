# State Contract: Atendimento mockado compartilhado na UI

## Purpose

Este contrato define comportamento idêntico nas UIs cliente e prestador. Cada perfil executa sua
própria instância; “a outra pessoa” é sempre uma resposta automática local, não outro aparelho.

## Creation

Um atendimento só pode ser criado por:

- aceite do candidato da fixture cliente após busca confirmada; ou
- aceite explícito do card de solicitação no fluxo prestador.

Na criação:

- `status = TRAVELLING`;
- preço é congelado conforme o perímetro aceito;
- cliente e PitTec possuem marcadores identificados e resumo textual;
- `acceptedAtEpochMillis` é registrado uma vez;
- o primeiro `MapViewportCommand` usa `framingViewport` para os dois pontos;
- nenhum ID, posição ou estado é enviado para fora do processo.

## Position timeline

1. Ao aceitar, emitir o snapshot inicial. Enquanto a experiência está STARTED e o status é
   `TRAVELLING`, manter exatamente um job de movimento.
2. Aguardar 30.000 ms entre passos; cada passo avança no máximo um índice do `movementPath`,
   incrementa revisão e recalcula distância local.
3. Não emitir comando de câmera em passos periódicos.
4. Se o usuário toca “Centralizar”, enquadrar os dois pontos atuais e incrementar a revisão do
   comando de câmera.
5. Se a experiência sai de STARTED, cancelar o job preservando o último snapshot. Ao voltar,
   reavaliar stale imediatamente e então reiniciar a cadência de 30 s; não criar uma posição nova
   apenas para limpar o aviso.
6. Se não há novo snapshot por mais de 35.000 ms monotônicos, usar marcador stale e mensagem
   “Última posição desatualizada”; manter o último ponto.
7. Ao atingir menos de 100 m, mostrar “menos de 100 m”. Marcadores continuam distintos mesmo em
   coordenadas iguais; o resumo textual identifica ambos.
8. Em `IN_PROGRESS`, `AWAITING_COMPLETION_CONFIRMATION` ou `COMPLETED`, não avançar o percurso.

## Meeting confirmation

Evento local: `onMeetingConfirmed()`.

- Válido apenas em `TRAVELLING` e somente se ainda não houver confirmação local.
- Atualiza atomicamente para `AWAITING_MEETING_CONFIRMATION`, registra timestamp local, desabilita
  a ação e mostra “Aguardando confirmação da outra pessoa”.
- Agenda exatamente um job de 3.000 ms com token `appointmentId + confirmationRevision`.
- Ao terminar, o job revalida ID, revisão e estado. Se válidos, registra contraparte, muda para
  `IN_PROGRESS` e registra `startedAtEpochMillis` uma única vez.
- Repetição, recomposição ou job obsoleto não altera estado nem agenda outro job.

## Completion confirmation

Evento local: `onCompletionConfirmed()`.

- Válido apenas em `IN_PROGRESS` e somente sem confirmação local anterior.
- Atualiza atomicamente para `AWAITING_COMPLETION_CONFIRMATION`, registra timestamp local,
  desabilita a ação e mostra “Aguardando confirmação da outra pessoa”.
- Agenda exatamente um job de 3.000 ms com token revisionado.
- Após revalidação, registra contraparte, muda para `COMPLETED`, registra
  `completedAtEpochMillis` e cancela jobs de movimento/stale.
- Nenhuma atualização de posição ou confirmação é aceita depois de `COMPLETED`.

## Rendering contract

Em qualquer estado, o card textual apresenta:

- nomes/papéis de cliente e PitTec;
- estado em linguagem natural;
- distância restante ou última distância conhecida;
- perímetro e total do serviço;
- atualidade/horário da posição;
- confirmação que já ocorreu e qual falta, quando aplicável.

O mapa apresenta dois marcadores e uma linha direta tracejada identificada como estimativa. A
indisponibilidade do estilo/tiles produz aviso e retry, mas não remove o card nem bloqueia as ações
de confirmação.

## Event and job ownership

- UI apenas emite eventos e renderiza `StateFlow` com `collectAsStateWithLifecycle()`.
- ViewModel é a única fonte de verdade e mantém referências dos jobs.
- `onVisible()`/`onHidden()` são idempotentes.
- Cada job é cancelado antes de ser substituído; `onCleared()` cancela todos.
- Delays não são representados por Threads, Handler, WorkManager ou callbacks aninhados.

## External-effect invariant

Todo evento deste contrato modifica somente objetos Kotlin em memória e, quando aplicável, a
câmera MapLibre local. Não existe repository, API, banco, DataStore, notificação, cobrança,
analytics com coordenadas ou comunicação entre as instâncias cliente/prestador.
