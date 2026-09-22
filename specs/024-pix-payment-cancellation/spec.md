# Feature Specification: Pix e cancelamento do pagamento

**Feature Branch**: `[024-pix-payment-cancellation]`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Tela de pagamento: quando o usuário escolher Pix, renderizar o QR Code e o Pix Copia e Cola; ajustar os fluxos de cancelamento para voltar à tela inicial de solicitação de PitTec e atualizar o status no banco; manter todos os valores em R$ 1,00 por enquanto."

## Clarifications

### Session 2026-09-22

- Q: Se o cliente cancelar depois que o Pix já foi aprovado, o sistema deve iniciar o reembolso antes de retornar à tela inicial? → A: Sim. Iniciar o reembolso e retornar à tela inicial após registrar o cancelamento, preservando o pagamento aprovado.
- Q: Quando o cliente cancelar antes de o pagamento ser aprovado, quais estados finais devem ser gravados para a solicitação e para o pagamento? → A: Solicitação `CANCELLED`; pagamento `CANCELLED` se existir.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Exibir pagamento Pix completo (Priority: P1)

Como cliente, quero escolher Pix na tela de pagamento e visualizar o QR Code e o código Copia e Cola para concluir o pagamento.

**Why this priority**: Sem os dados visuais e copiáveis do Pix, o cliente não consegue pagar e o atendimento não pode avançar.

**Independent Test**: Abrir uma proposta válida, escolher Pix e verificar que a tela exibe o QR Code, o código Copia e Cola, o valor de R$ 1,00 e a validade do pagamento quando disponível.

**Acceptance Scenarios**:

1. **Given** uma proposta válida e a tela de escolha de pagamento aberta, **When** o cliente escolher Pix, **Then** o sistema cria ou recupera uma cobrança Pix de R$ 1,00.
2. **Given** a cobrança Pix retornou os dados de pagamento, **When** a tela for exibida, **Then** o QR Code e o código Copia e Cola ficam visíveis ao cliente.
3. **Given** o código Copia e Cola está visível, **When** o cliente tocar na ação de copiar, **Then** o código é copiado e a tela informa que a cópia foi realizada.
4. **Given** o QR Code não foi disponibilizado ou a cobrança falhou, **When** o cliente visualizar o resultado, **Then** a tela apresenta uma mensagem compreensível e uma ação para tentar novamente ou cancelar.
5. **Given** a cobrança Pix já existe para a mesma proposta, **When** o cliente retornar à tela de pagamento, **Then** o sistema reutiliza o estado existente e não cria uma cobrança duplicada.

### User Story 2 - Cancelar e retornar à solicitação PitTec (Priority: P1)

Como cliente, quero cancelar o pagamento ou a solicitação em andamento e voltar à tela inicial de solicitação de PitTec, com o estado do atendimento encerrado corretamente.

**Why this priority**: Um cancelamento incompleto deixa a conta bloqueada, mantém a solicitação ativa indevidamente e impede novas solicitações.

**Independent Test**: Iniciar uma solicitação PitTec, chegar à tela de pagamento, cancelar em cada ponto disponível e verificar que o cliente retorna à tela inicial e que a solicitação deixa de estar ativa.

**Acceptance Scenarios**:

1. **Given** o cliente está na tela de pagamento sem pagamento aprovado, **When** tocar em cancelar e confirmar, **Then** a tentativa de pagamento é encerrada conforme seu estado e o cliente retorna à tela inicial de solicitação PitTec.
2. **Given** uma solicitação PitTec está aguardando pagamento, **When** o cliente cancelar, **Then** a solicitação é persistida como `CANCELLED` e a tentativa de pagamento é persistida como `CANCELLED` quando existir.
3. **Given** o cancelamento foi concluído, **When** o cliente abrir novamente a tela inicial, **Then** não aparece uma solicitação antiga como ativa nem o banner de indisponibilidade causado por esse fluxo.
4. **Given** o pagamento já foi aprovado, **When** o cliente tentar cancelar, **Then** o sistema registra o cancelamento, inicia o reembolso, preserva o pagamento aprovado no histórico e retorna o cliente à tela inicial.
5. **Given** a rede falhou durante o cancelamento, **When** o cliente retornar ao aplicativo, **Then** o sistema recupera o estado persistido e apresenta uma ação segura para concluir ou acompanhar o cancelamento sem duplicar operações.

### User Story 3 - Usar valor temporário de teste (Priority: P1)

Como responsável pelo teste do fluxo, quero que todos os valores usados nesta versão sejam de R$ 1,00 para validar o pagamento sem depender da tabela comercial definitiva.

**Why this priority**: Um valor único reduz a variabilidade durante a validação do fluxo Pix e do cancelamento.

**Independent Test**: Criar uma solicitação, avançar até o Pix e conferir que o valor exibido e o valor enviado para cobrança são sempre R$ 1,00.

**Acceptance Scenarios**:

1. **Given** qualquer solicitação coberta por esta versão, **When** o valor for exibido ao cliente, **Then** ele aparece como R$ 1,00.
2. **Given** o cliente escolher Pix ou cartão cadastrado, **When** a cobrança for criada, **Then** o valor cobrado é R$ 1,00.
3. **Given** o cliente cancelar antes da aprovação, **When** o estado for persistido, **Then** nenhuma cobrança acima de R$ 1,00 é criada para a solicitação.

### Edge Cases

- A resposta do pagamento chega sem QR Code, sem Copia e Cola ou com dados incompletos: a tela deve informar a indisponibilidade e impedir que o cliente veja um pagamento como pronto.
- O cliente toca várias vezes em Pix, copiar ou cancelar: somente uma cobrança e uma operação de cancelamento podem ser efetivadas.
- O aplicativo é fechado depois de gerar o Pix: ao retornar, deve recuperar o QR Code, o Copia e Cola e o status atual da cobrança.
- O código Pix expira antes do pagamento: a tela deve informar a expiração e oferecer nova tentativa ou cancelamento.
- O pagamento é aprovado enquanto o cliente está cancelando: o sistema deve preservar o estado financeiro real e aplicar a regra correspondente, sem sobrescrever aprovação com cancelamento indevido.
- A solicitação já foi cancelada ou expirada em outro dispositivo: o cliente deve voltar ao estado inicial sem criar nova cobrança.
- O valor recebido do servidor é diferente de R$ 1,00: a operação deve ser rejeitada ou tratada como erro de consistência, sem cobrar valor diferente do limite temporário.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir que o cliente escolha Pix na tela de seleção de pagamento.
- **FR-002**: O sistema MUST criar ou recuperar uma única cobrança Pix associada à proposta e ao cliente autenticado.
- **FR-003**: O sistema MUST exibir simultaneamente o QR Code e o código Pix Copia e Cola quando ambos forem retornados e válidos.
- **FR-004**: O sistema MUST permitir copiar o código Pix Copia e Cola e informar visualmente o resultado da cópia.
- **FR-005**: O sistema MUST exibir o valor temporário de R$ 1,00 na tela de pagamento e usar esse mesmo valor na cobrança.
- **FR-006**: O sistema MUST apresentar estado de carregamento enquanto aguarda a criação ou recuperação do Pix.
- **FR-007**: O sistema MUST apresentar erro acionável quando o QR Code ou o Copia e Cola não forem retornados, sem deixar a tela em carregamento infinito.
- **FR-008**: O sistema MUST recuperar a cobrança Pix existente ao reabrir a tela, evitando duplicidade para a mesma proposta.
- **FR-009**: O sistema MUST permitir cancelar o pagamento antes da aprovação.
- **FR-010**: O sistema MUST atualizar o status persistido da tentativa de pagamento quando o cliente cancelar, expirar ou concluir a operação; antes da aprovação, o cancelamento deve gravar `CANCELLED` quando houver tentativa.
- **FR-011**: O sistema MUST atualizar o status persistido da solicitação PitTec para `CANCELLED` quando o cancelamento for confirmado antes da aprovação.
- **FR-012**: O sistema MUST retornar o cliente à tela inicial de solicitação PitTec após um cancelamento confirmado.
- **FR-013**: O sistema MUST remover ou encerrar a solicitação cancelada da lista de solicitações ativas do cliente.
- **FR-014**: O sistema MUST impedir que uma solicitação cancelada continue exibindo indisponibilidade, pagamento pendente ou cobrança ativa.
- **FR-015**: O sistema MUST manter a aprovação de um pagamento quando ela ocorrer antes ou durante uma solicitação de cancelamento, registrar o cancelamento e iniciar o reembolso correspondente antes de concluir o retorno à tela inicial.
- **FR-016**: O sistema MUST tornar confirmação de pagamento, cancelamento e retorno à tela inicial operações idempotentes.
- **FR-017**: O sistema MUST recuperar o estado persistido após perda de conexão, fechamento do aplicativo ou uso simultâneo em mais de um dispositivo.
- **FR-018**: O sistema MUST rejeitar qualquer valor diferente de R$ 1,00 enquanto a restrição temporária desta feature estiver ativa.
- **FR-019**: O sistema MUST manter o atendimento sem início enquanto o pagamento Pix estiver pendente, falho, cancelado ou expirado.
- **FR-020**: O sistema MUST iniciar ou liberar o atendimento somente após confirmação de pagamento aprovado.

### Key Entities

- **Cobrança Pix**: Pagamento associado à proposta, contendo valor, status, QR Code, código Copia e Cola e validade.
- **Solicitação PitTec**: Solicitação do cliente cujo status deve ser encerrado ou atualizado após cancelamento.
- **Proposta de pagamento**: Contexto que vincula a solicitação, o valor temporário de R$ 1,00 e a tentativa de pagamento.
- **Estado de cancelamento**: Resultado persistido do cancelamento, incluindo o momento da operação e o estado financeiro observado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% das cobranças Pix válidas que retornarem os dados completos, o cliente visualiza QR Code e Copia e Cola na mesma tela.
- **SC-002**: Em 100% das tentativas de copiar o código, o cliente recebe confirmação visual em até 1 segundo.
- **SC-003**: Em 100% dos testes desta feature, o valor exibido e cobrado é exatamente R$ 1,00.
- **SC-004**: Em 95% das tentativas, o cliente consegue sair do fluxo por cancelamento e chegar à tela inicial em até 5 segundos após a confirmação.
- **SC-005**: Em 100% dos cancelamentos confirmados, a solicitação deixa de aparecer como ativa após a próxima atualização de estado.
- **SC-006**: Em 100% dos cancelamentos repetidos ou enviados por mais de um dispositivo, no máximo uma operação efetiva é registrada.
- **SC-007**: Em 100% dos casos de QR ausente, erro ou timeout, a tela deixa o carregamento e apresenta uma ação compreensível em até 20 segundos.
- **SC-008**: Em 100% dos casos de pagamento aprovado, o retorno ao fluxo de atendimento preserva o estado aprovado e não o substitui por cancelado.

## Assumptions

- O cliente está autenticado e possui uma solicitação PitTec válida antes de abrir a tela de pagamento.
- O Mercado Pago pode retornar QR Code, Copia e Cola e validade somente após a cobrança Pix ser criada e processada.
- O valor de R$ 1,00 é uma restrição temporária de validação e substitui os valores comerciais atuais em todos os métodos de pagamento cobertos por esta feature.
- A regra definitiva de preços, adicional e taxas será reativada em uma feature posterior; ela não faz parte desta entrega.
- A tela inicial de solicitação PitTec já existe e pode ser usada como destino após o cancelamento.
- Os estados persistidos atuais de pagamento e solicitação serão reutilizados; antes da aprovação, o cancelamento usa `CANCELLED` para ambos quando houver pagamento.
- Cancelamentos após pagamento aprovado iniciam o reembolso, preservam o registro do pagamento aprovado e retornam o cliente à tela inicial após o cancelamento ser registrado.
