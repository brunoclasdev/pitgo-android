# Contract: Recuperação da mesma captura e retorno após envio

**Cobre**: FR-006–FR-014, SC-003–SC-005. Complementa [contrato da feature 031](../../031-guincho-vistoria-rota/contracts/inspection-api.md); não modifica obrigatoriedade, catálogo ou aprovação.

## Recuperação por captura

`GuinchoInspectionViewModel.retryPending(captureId)` continua chamando o Repository; não abre câmera nem usa descarte como recuperação normal. ViewModel expõe `canRetrySave`, tentativa ativa e falha compreensível por captura. Habilitar somente para a conta correta, versão editável, falha recuperável e nenhuma tentativa conflitante; revalidação definitiva é do Repository. Estado remoto `READY` pode ser reconciliado mesmo depois de a edição terminar.

`GuinchoInspectionRepository.retryCapture` e `syncPending/process` compartilham o mutex atual por atendimento. O mutex e o journal são a autoridade da concorrência; enfileirar vários toques não pode resetar uma operação que outro executor já concluiu. Usar IDs de captura e operação, ponto, versão, legenda e instante originais; não converter uma nova tentativa em nova foto escolhida.

A ordem de recuperação é:

1. Reconciliar operação de reserva e confirmação com IDs/metadados persistidos; se remoto está `READY`, marcar `SAVED`, atualizar a tela e só então liberar a fonte local.
2. Conferir quais arquivos são suficientes para restaurar os assets: raw íntegro, original normalizado íntegro ou cópias remotas autorizadas. Miniatura sozinha não equivale a original recuperável.
3. Sem reserva, refazer preparo temporário a partir da fonte existente e salvar o manifesto atomicamente. `SPACE`, offline, timeout e falha de escrita/preparo transitória preservam a captura e a opção “Tentar salvar novamente”.
4. Com reserva, reutilizar os mesmos bytes e IDs. Derivado reconstruído só substitui arquivo local se tamanho e hash corresponderem ao manifesto reservado. Nunca atualizar hash ou tamanho para adaptar uma operação antiga a bytes diferentes; Storage continua `upsert=false`.
5. Se a fonte existe mas novos bytes são necessários, persistir estágio local de reparação antes de abandonar reserva. Chamar `discard_guincho_inspection_photo` com UUID antigo, confirmar `abandonedAt` e a operação própria, e somente então substituir IDs de reserva/confirmação/descarte e manifesto em uma escrita atômica. Preservar `captureId`, fonte, contexto e dados da captura; voltar ao preparo. Se o servidor ficou `READY` antes do abandono, reconciliar como salva.
6. Reparação interna não usa `retryCapture(discard=true)` nem entra em `DISCARDED`, pois esses caminhos apagam a fonte. Interrupção entre abandono e nova reserva retoma pelo estágio persistido. Sem permissão para reparar, bloquear contexto com mensagem e preservar arquivo.
7. Exigir nova fotografia somente quando não houver fonte suficiente e a versão continuar editável; oferecer ação explícita de recaptura que siga o descarte permitido da pendência antiga. Não afirmar que toda falha `IMAGE` implica perda da fonte.

Falha em uma foto não bloqueia navegação/carregamento das demais. Foto obrigatória ou opcional incluída pendente continua bloqueando envio. Retirada de opcional é ação explícita do usuário; nenhuma recuperação descarta conteúdo silenciosamente. Reabertura e rotação recuperam journal versão 1 existente com novos campos opcionais/valores padrão, sem perder a retomada automática.

## Contrato remoto de submit preservado

`public.submit_guincho_inspection` mantém parâmetros:

| Parâmetro | Regra |
|---|---|
| `p_appointment_id uuid` | Atendimento da intenção original |
| `p_expected_revision bigint` | Revisão conhecida para execução nova; conflito não é sucesso |
| `p_operation_id uuid` | UUID estável da intenção; não trocar após timeout |
| `p_version_id uuid` | Versão original explicitamente persistida; não derivar de versão posterior |

Envelope: `outcome=APPLIED|EXISTING|REVISION_CONFLICT`, documento atualizado conforme projeção vigente. O Repository deve preservar outcome/identidade da execução para o ViewModel, sem transformar conflito em sucesso ou descartar documento útil. Replay da mesma operação continua sendo reconhecido antes dos gates de revisão/rascunho; não enfraquecer validação nem imutabilidade para operações novas.

Intenção própria é criada antes da chamada e inclui conta, fingerprint, atendimento, versão e operação. Em timeout ou desconexão fica `UNCERTAIN`; a UI mantém erro/recuperação, independentemente de `canSubmit` tornar-se falso depois do polling. Ação de recuperação e retomada com intenção existente fazem replay seguro com a mesma versão. `APPLIED` ou `EXISTING` confirmam envio próprio; `REVISION_CONFLICT` exige atualizar/reconciliar sem trocar identidade. Outro resultado, sessão inválida ou encerramento sem prova de sucesso mantém erro/estado real.

Confirmação pode devolver documento já aprovado/corrigido; isso não invalida o fato de que aquela operação foi aplicada. Se retornar fechamento sem confirmação da operação, não fingir sucesso. Uma consulta comum a versão enviada nunca cria intenção.

## Estado de navegação e consumo

Após confirmar, aplicar documento retornado e expor estado de retorno pendente com identidade da intenção. UI comunica ao NavHost e reconhece consumo após o retorno tratado. O estado deve sobreviver a rotação/`SavedStateHandle` restaurado sem perder um retorno confirmado ou produzir múltiplos mapas. Não limpar operação antes de registrar confirmação; não depender de `refresh()` adicional para navegar.

Destino: `PitGoRoutes.appointmentTracking(appointmentId)` do mesmo atendimento. Se o destino anterior já for esse mapa, remover a vistoria. Caso contrário, navegar com remoção inclusiva da vistoria e `launchSingleTop`. Consumir uma única vez; Voltar não reabre a vistoria recém-enviada. Não usar `PitGoRoutes.GuinchoMap` genérico como substituto do acompanhamento do pedido.

Consulta/histórico de versão `SUBMITTED` ou `APPROVED` permanece aberta até ação explícita do usuário. Polling, push e retomada sem intenção própria não fecham a tela. Preservar `onApprovalConfirmed` do cliente e o retorno atual ao mesmo mapa; o mapa mostra a situação corrente se o aceite já tiver ocorrido. Aprovação só libera “Iniciar transporte”, que continua exigindo ação do guincheiro.
