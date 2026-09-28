# Feature Specification: Acompanhamento pós-pagamento PitTec

**Feature Branch**: `[025-pittec-payment-tracking]`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Tela pós pagamento via Pix: informar o resultado do pagamento; após sucesso, acompanhar cliente e PitTec no mapa; confirmar chegada e início do serviço para os dois lados."

## Clarifications

### Session 2026-09-23

- Q: As taxas cobradas do cliente devem vir de uma configuração de preços existente no banco, permitindo alterar seus valores sem modificar o aplicativo? → A: Sim. As taxas serão configuráveis na base de preços.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receber o resultado do pagamento Pix (Priority: P1)

Depois de pagar via Pix, o cliente precisa saber claramente se o pagamento foi aprovado, falhou ou foi recusado. Enquanto o resultado definitivo não estiver disponível, deve ver que o pagamento ainda está sendo processado e não pode ser levado a acreditar que o serviço já começou.

**Why this priority**: O resultado do pagamento autoriza ou impede o avanço do serviço e evita que cliente e PitTec recebam estados diferentes.

**Independent Test**: Simular cada resultado do pagamento após a confirmação do Pix e verificar a mensagem exibida, a ação disponível e o bloqueio do mapa quando o pagamento não estiver aprovado.

**Acceptance Scenarios**:

1. **Given** o cliente concluiu o pagamento Pix, **When** o pagamento for aprovado, **Then** o cliente verá uma confirmação de sucesso e poderá acompanhar o serviço no mapa.
2. **Given** o pagamento ainda não tem resultado definitivo, **When** o cliente consultar o pagamento, **Then** verá que ele está em processamento, sem acesso ao fluxo de serviço iniciado.
3. **Given** o pagamento falhou ou foi recusado, **When** o resultado for recebido, **Then** o cliente verá o motivo disponível e uma ação para tentar novamente, escolher outra forma de pagamento ou cancelar a solicitação.

### User Story 2 - Cliente acompanhar o PitTec e confirmar o início (Priority: P1)

Após o pagamento aprovado e a aceitação do pedido por um PitTec, o cliente verá um mapa com sua localização e a localização atual do PitTec. A posição do PitTec será atualizada durante o deslocamento. Quando o PitTec informar que chegou, o cliente verá uma confirmação e poderá reconhecer que o serviço começou.

**Why this priority**: O acompanhamento dá visibilidade ao cliente sobre a chegada do profissional e reduz incerteza entre o pagamento e o início do serviço.

**Independent Test**: Aprovar um pagamento com um PitTec aceito, movimentar a localização do PitTec e marcar a chegada; verificar o mapa, as atualizações e as confirmações apresentadas ao cliente.

**Acceptance Scenarios**:

1. **Given** o pagamento foi aprovado e existe um PitTec aceito, **When** o fluxo pós-pagamento abrir, **Then** o cliente verá o mapa com as posições do cliente e do PitTec.
2. **Given** o PitTec está a caminho, **When** sua posição mudar, **Then** o mapa do cliente será atualizado e indicará quando a posição estiver desatualizada.
3. **Given** o PitTec confirmou que chegou, **When** o cliente receber a confirmação, **Then** verá que o PitTec chegou e poderá confirmar que o serviço começou.

### User Story 3 - PitTec acompanhar o cliente e validar chegada e início (Priority: P1)

Após o pagamento aprovado, o PitTec verá um mapa com a localização do cliente e a própria localização. Durante o deslocamento, sua posição será atualizada. Ao chegar, o PitTec validará a chegada e confirmará o início do serviço.

**Why this priority**: A validação do PitTec é o marco operacional que libera o início do serviço e mantém os dois lados sincronizados.

**Independent Test**: Aprovar um pagamento para um PitTec aceito, abrir a visão do PitTec, atualizar sua localização e executar as ações de chegada e início; verificar as transições e o reflexo no cliente.

**Acceptance Scenarios**:

1. **Given** o pagamento foi aprovado e o pedido está atribuído ao PitTec, **When** o PitTec abrir o fluxo, **Then** verá o mapa com sua posição e a localização do cliente.
2. **Given** o PitTec está se deslocando, **When** sua posição for atualizada, **Then** o mapa do PitTec mostrará a posição mais recente e a do cliente.
3. **Given** o PitTec está no local do cliente, **When** validar a chegada e confirmar o início, **Then** o serviço passará para em andamento e o cliente receberá a mesma informação.

### User Story 4 - Retomar o acompanhamento com estados consistentes (Priority: P2)

Se o cliente ou o PitTec fechar a aplicação, perder conexão ou retornar ao fluxo, cada um deverá reencontrar o estado atual do pagamento, da chegada, do início do serviço e das localizações disponíveis.

**Why this priority**: O acompanhamento não pode depender de a aplicação permanecer aberta em uma única tela.

**Independent Test**: Interromper a conexão ou fechar a aplicação em cada etapa e reabrir o fluxo; verificar que o usuário retorna ao estado correto sem repetir transições.

**Acceptance Scenarios**:

1. **Given** o pagamento foi aprovado e o PitTec está a caminho, **When** o cliente reabrir a aplicação, **Then** verá novamente o mapa e o estado atual do deslocamento.
2. **Given** a chegada já foi registrada, **When** um dos usuários repetir uma ação de chegada ou início, **Then** o sistema manterá uma única transição e exibirá o estado já confirmado.
3. **Given** o serviço foi cancelado ou concluído, **When** o usuário abrir a solicitação, **Then** o acompanhamento será encerrado e as posições deixarão de ser compartilhadas nessa solicitação.

### User Story 5 - Exibir valores conforme o perfil (Priority: P1)

Ao calcular o atendimento, o sistema usará o preço do serviço e o adicional de quilometragem. O PitTec verá o valor que receberá, composto pelo serviço mais a quilometragem. O cliente verá somente o total a pagar, que inclui esse valor, o adicional de 40% e as taxas configuradas.

**Why this priority**: O cálculo correto protege a margem do serviço e mantém separados os valores apresentados ao cliente e ao PitTec.

**Independent Test**: Configurar um preço de serviço, uma tarifa por quilômetro e taxas, calcular uma solicitação e verificar os valores exibidos para cada perfil e o total cobrado do cliente.

**Acceptance Scenarios**:

1. **Given** o serviço tem preço base e a distância calculada tem um adicional, **When** o orçamento for calculado, **Then** o valor do PitTec será o preço do serviço somado ao adicional de quilometragem.
2. **Given** o valor do PitTec foi calculado, **When** o total do cliente for apresentado, **Then** o sistema somará 40% sobre o valor do serviço mais a quilometragem e acrescentará as taxas configuradas.
3. **Given** o cliente está visualizando o orçamento ou pagamento, **When** os valores forem exibidos, **Then** verá somente o total a pagar, sem detalhamento do serviço, quilometragem, percentual ou taxas.
4. **Given** uma taxa foi alterada na configuração de preços, **When** um novo orçamento for calculado, **Then** o novo valor será aplicado sem exigir alteração no aplicativo.

### Edge Cases

- O resultado do Pix demora: exibir processamento, permitir nova consulta e evitar navegação automática para o mapa antes da aprovação.
- O pagamento é recusado ou falha: manter o pedido sem início de serviço e oferecer nova tentativa, outra forma de pagamento ou cancelamento.
- O Pix é pago, mas a confirmação chega depois: reconciliar o resultado recebido e liberar o fluxo somente após a confirmação de aprovação.
- A localização do cliente ou do PitTec está ausente, negada ou desatualizada: informar qual posição não está disponível, exibir a última posição com horário quando possível e não declarar chegada automaticamente com dados inválidos.
- Um dos usuários fica sem conexão: preservar o último estado confirmado e sincronizar as mudanças quando a conexão voltar.
- O PitTec chega, mas ainda não confirma o início: mostrar chegada confirmada e manter o serviço aguardando início.
- O cliente não confirma visualmente o início: manter o registro da validação operacional do PitTec e permitir que o cliente reconheça a informação depois.
- O pedido é cancelado ou o PitTec deixa de atender após o pagamento: mostrar o estado final e impedir que o mapa continue ativo para uma solicitação encerrada.
- Eventos repetidos de pagamento, chegada ou início: processar a mudança uma vez e manter o estado consistente para os dois usuários.
- O preço do serviço, a tarifa de quilometragem ou uma taxa não estiver configurado: impedir a cobrança com valor incompleto e informar que o orçamento não está disponível.
- O cálculo resultar em frações de centavo: aplicar uma regra única de arredondamento monetário antes de exibir ou cobrar o valor.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST apresentar ao cliente um resultado distinguível para pagamento aprovado, em processamento, falho e recusado.
- **FR-002**: O sistema MUST permitir o acesso ao acompanhamento e ao início do serviço somente após a confirmação de pagamento aprovado e de um PitTec aceito para a solicitação.
- **FR-003**: O sistema MUST abrir para o cliente uma tela de mapa com a localização do cliente e a localização mais recente do PitTec atribuído após o pagamento aprovado.
- **FR-004**: O sistema MUST abrir para o PitTec uma tela de mapa com sua localização e a localização mais recente do cliente após o pagamento aprovado.
- **FR-005**: O sistema MUST atualizar as localizações dos dois usuários enquanto o PitTec estiver em deslocamento ou o serviço estiver ativo, indicando quando uma posição estiver desatualizada.
- **FR-006**: O PitTec MUST poder validar que chegou ao local do cliente, registrando a chegada uma única vez para a solicitação.
- **FR-007**: O PitTec MUST poder confirmar que o serviço começou somente depois de o pagamento estar aprovado e a chegada estar validada.
- **FR-008**: O sistema MUST informar ao cliente a chegada do PitTec e solicitar o reconhecimento de que o serviço começou, sem apagar a validação operacional já registrada pelo PitTec.
- **FR-009**: O sistema MUST refletir para os dois usuários as mudanças de pagamento, chegada e início do serviço no mesmo estado atual da solicitação.
- **FR-010**: O sistema MUST preservar e retomar o estado confirmado após fechamento da aplicação, perda de conexão ou reabertura da solicitação.
- **FR-011**: O sistema MUST impedir que posições ausentes ou desatualizadas gerem uma confirmação automática de chegada.
- **FR-012**: O sistema MUST interromper o compartilhamento das localizações quando a solicitação for cancelada ou concluída.
- **FR-013**: O sistema MUST oferecer uma ação adequada para cada pagamento falho ou recusado, incluindo nova tentativa, escolha de outra forma de pagamento ou cancelamento.
- **FR-014**: O sistema MUST tratar confirmações repetidas de pagamento, chegada e início sem criar transições ou registros duplicados.
- **FR-015**: O sistema MUST calcular o valor do PitTec como o preço do serviço somado ao adicional de quilometragem aplicável.
- **FR-016**: O sistema MUST calcular o total do cliente como o valor do serviço mais o adicional de quilometragem, acrescido de 40% sobre essa soma e das taxas configuradas.
- **FR-017**: O sistema MUST manter os preços de serviço, tarifas de quilometragem e taxas em uma configuração de preços alterável sem atualização do aplicativo.
- **FR-018**: O sistema MUST mostrar ao PitTec o valor do serviço somado ao adicional de quilometragem, sem mostrar o total cobrado do cliente.
- **FR-019**: O sistema MUST mostrar ao cliente somente o total a pagar, sem detalhar preço do serviço, quilometragem, percentual de 40% ou taxas.
- **FR-020**: O sistema MUST impedir a criação da cobrança quando qualquer componente obrigatório do cálculo estiver ausente ou inválido.
- **FR-021**: O sistema MUST aplicar o mesmo arredondamento monetário ao valor do PitTec e ao total do cliente antes da apresentação e da cobrança.

### Key Entities *(include if feature involves data)*

- **Resultado do pagamento**: Estado do pagamento Pix, incluindo processamento, aprovado, falho ou recusado, além da mensagem disponível para o cliente.
- **Solicitação de serviço**: Pedido que relaciona cliente, PitTec aceito, pagamento e ciclo do serviço.
- **Posição compartilhada**: Localização atual ou última localização conhecida do cliente e do PitTec, com horário de atualização e indicação de validade.
- **Confirmação de chegada**: Registro de que o PitTec chegou ao local do cliente, com data e responsável pela confirmação.
- **Confirmação de início**: Registro de que o serviço começou, com confirmação operacional do PitTec e reconhecimento exibido ao cliente.
- **Configuração de preços**: Valores vigentes do serviço, tarifa ou adicional por quilometragem e taxas aplicáveis ao cálculo.
- **Cálculo da cobrança**: Relação dos componentes usados para determinar o valor do PitTec e o total do cliente, com os valores protegidos de acordo com cada perfil.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos pagamentos com resultado definitivo, o cliente vê sucesso, falha ou recusa em até 5 segundos após o resultado estar disponível.
- **SC-002**: Em 100% dos casos, nenhum usuário acessa o fluxo de serviço iniciado antes da aprovação do pagamento.
- **SC-003**: Em pelo menos 95% dos atendimentos com conexão disponível, os dois mapas exibem as posições do cliente e do PitTec em até 10 segundos após o pagamento aprovado e a atribuição do PitTec.
- **SC-004**: Durante o deslocamento, pelo menos 95% das atualizações de posição aparecem para o outro usuário em até 10 segundos, ou são marcadas como desatualizadas.
- **SC-005**: Em 100% das confirmações válidas de chegada e início, o estado correspondente aparece para o outro usuário em até 10 segundos.
- **SC-006**: Em 100% das solicitações canceladas ou concluídas, o acompanhamento e o compartilhamento de localização são encerrados.
- **SC-007**: Após reabrir a aplicação, pelo menos 95% dos usuários retornam ao estado correto da solicitação em até 10 segundos.
- **SC-008**: Nenhuma solicitação apresenta mais de uma transição efetiva para chegada ou início quando a mesma ação é repetida.
- **SC-009**: Em 100% dos novos orçamentos, o valor do PitTec corresponde ao serviço mais quilometragem e o total do cliente corresponde a essa soma mais 40% e taxas configuradas.
- **SC-010**: Em 100% das telas do cliente, o detalhamento dos componentes internos da cobrança fica oculto e somente o total é apresentado.
- **SC-011**: Uma alteração válida na configuração de preços é aplicada ao próximo orçamento sem atualização do aplicativo.

## Assumptions

- O fluxo existente de solicitação, aceitação do PitTec e pagamento Pix será reutilizado; esta feature define o comportamento depois do pagamento.
- O resultado definitivo informado pelo provedor de pagamento é a fonte para liberar o acompanhamento; “em processamento” é um estado intermediário.
- Os preços do serviço, adicionais de quilometragem e taxas são mantidos em uma configuração de preços administrável e vigente no momento do orçamento.
- A fórmula do cliente é: `(serviço + quilometragem) + 40% de (serviço + quilometragem) + taxas`.
- O valor apresentado ao PitTec exclui o adicional de 40% e as taxas cobradas do cliente.
- O valor apresentado ao cliente é somente o total final, sem detalhamento dos componentes.
- O PitTec é responsável por validar a chegada e confirmar operacionalmente o início do serviço; o cliente recebe a informação e pode reconhecê-la na tela.
- O acompanhamento mostra as localizações somente enquanto a solicitação estiver ativa e encerra o compartilhamento ao cancelar ou concluir o serviço.
- O cliente autoriza o compartilhamento da localização necessária para o atendimento, e o PitTec autoriza o compartilhamento da própria localização durante o deslocamento e o serviço.
- Quando uma posição não puder ser atualizada, a aplicação preservará a última posição conhecida com seu horário e informará a limitação ao usuário.
- O escopo desta feature cobre acompanhamento de posições, chegada e início do serviço; navegação curva a curva e cálculo de rota não fazem parte desta entrega.
