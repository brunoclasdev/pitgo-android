# Data Model: Home e alerta do guincheiro

## Entidades existentes

- **Conta do prestador**: providerId, modo MOBILE, serviço Guincho, disponibilidade. A sessão determina elegibilidade e isola estado por conta.
- **Solicitação**: appointmentId, dispatchKind GUINCHO_DIRECT, revision, status, responseDeadlineEpochMillis, pagamento e projeção guincho com cliente/veículo/retirada/destino/remuneração. O backend continua autoridade; não criar entidade persistida adicional.
- **DeviceLocationFix**: coordenadas, capturedAtEpochMillis e precisão. Coordenadas finitas e dentro dos limites geográficos; somente posição precisa válida e recente é atual.

## Estado de apresentação da Home

Estado de localização: Loading, Current(fix), Stale(lastFix), PermissionDenied, LocationDisabled ou Unavailable. Estado de mapa incorpora estilo/recursos existentes, câmera e revisão, followEnabled e última posição. Gesto desativa followEnabled; Recentralizar com Current ativa e emite comando de câmera. Posição envelhecida por 35 segundos passa a Stale mesmo sem novo evento. Nenhum marcador fictício.

## Estado de apresentação do alerta

Reutilizar ProviderIncomingUiState: loading/erro/sem pedido/pedido ativo. Um card elegível exige conta autorizada, GUINCHO_DIRECT, PENDING liberado e prazo restante positivo. Id/revisão identificam o estado atual; appointmentId identifica a solicitação para deduplicação. PendingDispatchAction impede ações concorrentes; respostas usam a revisão do snapshot vigente. Erro transitório não deve permitir resposta usando pedido não validado nem reiniciar o prazo.

## Marcador mínimo de entrega sonora

DataStore guarda apenas providerId, appointmentId e prazo de expiração como indicação de primeira postagem autorizada e limite de limpeza. Sem nome, endereço, veículo, coordenadas ou valores. Uma atualização da mesma solicitação é silenciosa. A recriação do serviço consulta o marcador antes da postagem; logout remove a notificação e estado visível da conta, preservando somente o marcador mínimo por conta/pedido até encerramento ou expiração. Entrar novamente na mesma conta com o pedido vigente não gera novo som. No encerramento ou expiração, eliminar marcador obsoleto. Se notificação estiver desativada, não marcar entrega como realizada.

## Transições

- Seleção / AWAITING_PAYMENT: sem alerta operacional.
- Pagamento aprovado → PENDING: card único em primeiro plano e notificação autorizada fora dele; prazo original preservado.
- PENDING → ACCEPTED: remover alerta e navegar uma vez para acompanhamento.
- PENDING → CANCELLED/expirado: remover alerta; recusa confirmada retorna à Home e reembolso segue fluxo existente.
- ACCEPTED → IN_PROGRESS → AWAITING_CLIENT_COMPLETION_CONFIRMATION: tracking existente tem prioridade.
- Confirmação final → COMPLETED: retornar à Home, sem alertas residuais.
- Contestação: manter política existente da feature 028, sem modificar nem interromper atendimento novo.
- Logout/troca de conta: cancelar observers, notificações e posição anteriores antes de renderizar a próxima sessão.

Sem migrations, novas tabelas ou alterações de RPC.
