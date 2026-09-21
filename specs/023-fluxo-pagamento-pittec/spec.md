# Feature Specification: Fluxo de pagamento do PitTec

**Feature Branch**: `[023-fluxo-pagamento-pittec]`

**Created**: 2026-09-18

**Status**: Draft

**Input**: User description: "O usuário solicita o PitTec; o prestador recebe e aceita; o sistema calcula o valor com base no serviço, exibindo ao PitTec apenas seu valor e cobrando do cliente o valor do serviço mais 40% e taxas; cada parte não vê o valor da outra; o cliente valida o valor e paga com cartão cadastrado; o serviço começa após o pagamento."

## Clarifications

### Session 2026-09-18

- Q: O adicional de 40% deve ser calculado sobre o valor base do serviço e, depois, as taxas devem ser adicionadas separadamente ao total do cliente? → A: Sim. Aplicar 40% sobre o valor base e adicionar as taxas separadamente.
- Q: Por quanto tempo a proposta aceita deve permanecer válida para o cliente confirmar e pagar? → A: 15 minutos.
- Q: A tela de pagamento deve permitir somente a escolha de cartões já cadastrados ou também oferecer cadastro de um novo cartão nesse momento? → A: Permitir escolher cartão cadastrado ou cadastrar um novo.
- Q: As taxas devem ser obtidas de uma regra comercial vigente e configurável para cada atendimento, ou devem usar um valor fixo definido nesta feature? → A: Taxas vêm de regra comercial vigente e configurável.
- Q: Quando a proposta expirar após 15 minutos sem pagamento aprovado, o prestador deve ser liberado e a solicitação deve ser encerrada para o cliente solicitar outro PitTec? → A: Sim. Liberar o prestador e encerrar a solicitação.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Aceitar solicitação e apresentar valores separados (Priority: P1)

Como cliente, quero solicitar um PitTec e receber o valor total somente depois que um prestador aceitar. Como prestador, quero aceitar a solicitação e ver apenas o valor do serviço que receberei, para que cada parte tenha acesso somente ao valor que lhe diz respeito.

**Why this priority**: Sem o aceite e a apresentação correta dos valores, não existe uma cobrança válida nem um serviço que possa começar.

**Independent Test**: Criar uma solicitação, aceitar como prestador e conferir que o prestador vê apenas seu valor, enquanto o cliente vê o total calculado com o adicional e as taxas, sem acesso ao valor reservado ao prestador.

**Acceptance Scenarios**:

1. **Given** uma solicitação PitTec elegível e um prestador disponível, **When** o prestador aceita, **Then** o sistema calcula o valor do serviço a partir dos dados vigentes e prepara os valores individuais para cada participante.
2. **Given** o prestador aceitou a solicitação, **When** o prestador visualiza a proposta, **Then** ele vê o valor do serviço que receberá e não vê o adicional de 40%, as taxas ou o total cobrado do cliente.
3. **Given** o prestador aceitou a solicitação, **When** o cliente visualiza a proposta, **Then** ele vê o adicional de 40%, as taxas e o total que será cobrado, sem ver o valor base destinado ao prestador.
4. **Given** dois participantes com papéis diferentes na mesma solicitação, **When** qualquer um consulta a proposta, **Then** cada participante recebe somente os campos de valor correspondentes ao seu papel.

### User Story 2 - Validar a cobrança antes de pagar (Priority: P1)

Como cliente, quero revisar e confirmar o valor total antes de abrir o pagamento, para evitar pagar por uma proposta que não reconheço.

**Why this priority**: A confirmação explícita do cliente protege contra cobranças inesperadas e delimita o momento em que ele autoriza o pagamento.

**Independent Test**: Após o aceite do prestador, abrir a proposta como cliente, confirmar o valor e verificar que a tela de pagamento só fica disponível depois da confirmação.

**Acceptance Scenarios**:

1. **Given** uma proposta aceita e ainda não confirmada pelo cliente, **When** o cliente visualiza a tela de valores, **Then** a ação de pagamento permanece indisponível até que ele confirme o valor.
2. **Given** uma proposta com valor total, adicional e taxas apresentados, **When** o cliente confirma, **Then** o sistema registra a confirmação e apresenta a etapa de pagamento com o mesmo total confirmado.
3. **Given** o valor da proposta mudou ou deixou de ser válido antes da confirmação, **When** o cliente tenta confirmar, **Then** a confirmação é recusada e o novo valor é apresentado para revisão.
4. **Given** o cliente recusa a proposta, **When** ele encerra a confirmação, **Then** nenhuma cobrança é iniciada e a solicitação é encerrada conforme o estado permitido para o atendimento.

### User Story 3 - Pagar com cartão cadastrado e iniciar o serviço (Priority: P1)

Como cliente, quero escolher um cartão cadastrado e concluir o pagamento para que o serviço comece somente quando o pagamento for aprovado.

**Why this priority**: O pagamento aprovado é a condição de segurança para iniciar o atendimento e liberar a execução para as partes.

**Independent Test**: Confirmar uma proposta, escolher um cartão salvo, concluir um pagamento aprovado e verificar que cliente e prestador veem o serviço como iniciado.

**Acceptance Scenarios**:

1. **Given** uma proposta confirmada pelo cliente, **When** a etapa de pagamento é aberta, **Then** o cliente vê o valor total confirmado e os cartões cadastrados disponíveis para escolha.
2. **Given** um cartão cadastrado selecionado, **When** o cliente conclui o pagamento, **Then** o sistema envia a cobrança pelo total confirmado sem expor ao cliente o valor interno destinado ao prestador.
3. **Given** o pagamento foi aprovado, **When** o resultado é confirmado, **Then** o serviço pode começar e ambos os participantes recebem o estado atualizado do atendimento.
4. **Given** o pagamento está pendente, recusado ou apresentou erro, **When** o cliente retorna da tentativa de pagamento, **Then** o serviço não começa, o valor continua aguardando resolução e o cliente recebe uma ação clara para tentar novamente ou escolher outro cartão.
5. **Given** o cliente abandona ou fecha o pagamento antes da aprovação, **When** o atendimento é retomado, **Then** a proposta confirmada permanece disponível sem iniciar o serviço nem criar uma cobrança duplicada.
6. **Given** o cliente não possui um cartão cadastrado elegível, **When** a etapa de pagamento é aberta, **Then** ele pode cadastrar um novo cartão e usá-lo na tentativa de pagamento sem sair da proposta confirmada.

### Edge Cases

- O prestador recusa ou não responde: a solicitação deve seguir o fluxo de busca de outro prestador, sem exibir ao cliente uma cobrança antes de um aceite válido.
- Não existe valor de serviço vigente para o prestador: o atendimento não deve avançar para confirmação ou pagamento e deve apresentar uma mensagem acionável.
- O valor ou as taxas mudam entre o aceite e a confirmação: a confirmação anterior não deve ser reutilizada; o cliente deve revisar o novo total.
- A proposta aceita expira após 15 minutos se o cliente não confirmar e pagar; depois da expiração, nenhuma tentativa deve iniciar o serviço e uma nova proposta deve ser calculada antes de qualquer pagamento.
- Quando a proposta expira sem pagamento aprovado, o prestador deve ser liberado e a solicitação deve ser encerrada; o cliente precisa iniciar uma nova solicitação para buscar outro PitTec.
- O cliente tenta confirmar ou pagar duas vezes, inclusive em dispositivos diferentes: somente uma confirmação e uma cobrança podem liberar o atendimento.
- O cartão selecionado é removido, expirou ou é recusado: o cliente deve poder escolher outro cartão cadastrado ou cadastrar um novo, sem iniciar o serviço.
- A resposta do pagamento demora ou o aplicativo é fechado: a retomada deve mostrar o estado verdadeiro da cobrança sem criar outra cobrança para a mesma proposta.
- O cliente cancela depois de pagar e antes do início: o atendimento deve seguir as regras de cancelamento e devolução aplicáveis, sem liberar o serviço por engano.
- Dados de preço, cartão, credenciais e identificadores de cobrança não devem aparecer em mensagens, logs ou telas destinadas ao outro participante.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir que o cliente solicite um PitTec e mantenha a solicitação sem cobrança até um prestador aceitar.
- **FR-002**: O sistema MUST permitir que um prestador elegível aceite a solicitação antes de calcular e apresentar a cobrança ao cliente.
- **FR-003**: O sistema MUST obter o valor base do serviço a partir dos dados vigentes associados ao prestador e ao atendimento aceito.
- **FR-004**: O sistema MUST calcular o adicional de 40% exclusivamente sobre o valor base do serviço e adicionar separadamente as taxas da regra comercial vigente, identificando para o cliente somente os componentes que não revelem o valor base do prestador.
- **FR-005**: O sistema MUST apresentar ao prestador somente o valor do serviço que lhe corresponde.
- **FR-006**: O sistema MUST apresentar ao cliente somente o total que ele deve pagar e seus componentes, sem revelar o valor destinado ao prestador.
- **FR-007**: O sistema MUST impedir que qualquer participante obtenha, por consulta ou pela interface, os valores exclusivos do outro participante.
- **FR-008**: O sistema MUST exigir que o cliente revise e confirme o total antes de disponibilizar a etapa de pagamento.
- **FR-009**: O sistema MUST invalidar a confirmação quando o valor, as taxas, o serviço ou a validade da proposta tiverem mudado.
- **FR-010**: O sistema MUST apresentar ao cliente os cartões cadastrados elegíveis para pagar o total confirmado e oferecer o cadastro de um novo cartão quando nenhum cartão adequado estiver disponível ou quando o cliente escolher essa opção.
- **FR-011**: O sistema MUST permitir que o cliente escolha um cartão cadastrado ou cadastre um novo cartão e envie uma única tentativa de pagamento para a proposta confirmada.
- **FR-012**: O sistema MUST iniciar o serviço somente após confirmação de pagamento aprovado para o total confirmado.
- **FR-013**: O sistema MUST manter o serviço sem início enquanto o pagamento estiver pendente, recusado, cancelado, expirado ou com erro.
- **FR-014**: O sistema MUST permitir que o cliente tente novamente um pagamento não aprovado ou escolha outro cartão sem criar uma cobrança duplicada para a mesma proposta.
- **FR-015**: O sistema MUST recuperar o estado da proposta e do pagamento quando o aplicativo for fechado, perder conexão ou for reaberto.
- **FR-016**: O sistema MUST comunicar ao cliente e ao prestador mudanças relevantes de estado sem revelar dados financeiros exclusivos do outro participante.
- **FR-017**: O sistema MUST tratar confirmação, pagamento e início do serviço como transições únicas e válidas, rejeitando ações repetidas ou fora de ordem.
- **FR-018**: O sistema MUST proteger valores, dados de pagamento e identificadores financeiros contra exposição em telas, mensagens e registros destinados a participantes sem autorização.
- **FR-019**: O sistema MUST liberar o prestador e encerrar a solicitação quando a proposta expirar após 15 minutos sem pagamento aprovado, sem permitir que o cliente pague a proposta expirada.

### Key Entities

- **Solicitação PitTec**: Pedido inicial do cliente, com localização, estado de busca e vínculo ao prestador selecionado.
- **Proposta aceita**: Oferta criada após o aceite do prestador, com serviço, validade de 15 minutos, valor base, adicional, taxas obtidas da regra comercial vigente, total do cliente e confirmações necessárias por papel.
- **Valor do prestador**: Valor base do serviço destinado ao prestador, visível somente a ele.
- **Total do cliente**: Valor cobrado do cliente, formado pelo valor do serviço, adicional de 40% e taxas aplicáveis.
- **Cartão cadastrado**: Meio de pagamento previamente associado ao cliente e elegível para uma nova cobrança.
- **Pagamento**: Tentativa de cobrança vinculada a uma proposta confirmada, com estado de pendência, aprovação, recusa, cancelamento ou devolução.
- **Atendimento PitTec**: Execução do serviço, liberada somente depois de um pagamento aprovado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% das solicitações sem aceite de prestador, nenhuma cobrança é iniciada.
- **SC-002**: Em 100% das propostas apresentadas, o cliente vê o total correto com adicional de 40% e taxas, sem o valor base do prestador, e o prestador vê apenas seu valor.
- **SC-003**: Em 100% dos casos de pagamento pendente, recusado ou com erro, o atendimento permanece sem início.
- **SC-004**: Pelo menos 95% dos clientes de teste conseguem revisar, confirmar e selecionar um cartão cadastrado sem assistência.
- **SC-005**: Pelo menos 99% das tentativas repetidas da mesma proposta resultam em no máximo uma cobrança que possa liberar o atendimento.
- **SC-006**: Após um pagamento aprovado, cliente e prestador visualizam o início do atendimento em até 10 segundos em condições normais de conexão.
- **SC-007**: Em uma retomada após fechamento do aplicativo ou perda de conexão, 100% dos casos exibem o estado persistido da proposta e do pagamento sem solicitar uma nova cobrança indevida.
- **SC-008**: Em testes de autorização, 100% das consultas de cada participante omitem os valores exclusivos do outro participante.

## Assumptions

- O prestador já possui elegibilidade para receber solicitações PitTec e o cliente possui uma conta autenticada.
- O valor base do serviço e as taxas aplicáveis estão disponíveis no momento do aceite; as taxas são obtidas da regra comercial vigente para o atendimento.
- O adicional de 40% é aplicado ao valor do serviço antes das taxas, salvo regra comercial vigente explicitamente configurada.
- As taxas podem variar conforme a regra vigente; a versão dos valores aplicados deve permanecer associada à proposta e o total apresentado ao cliente é o valor final que será cobrado.
- O cliente pode cadastrar um cartão antes do fluxo ou durante a etapa de pagamento; o cadastro e a remoção de cartões existentes permanecem disponíveis conforme as regras atuais do produto.
- Uma proposta aceita permanece válida por 15 minutos; após expirar sem pagamento aprovado, o prestador é liberado, a solicitação é encerrada e o cliente deve iniciar uma nova solicitação.
- Cancelamento, devolução e tratamento de pagamentos aprovados antes do cancelamento seguem as regras financeiras já definidas para o produto.
- O início do serviço depende de conexão suficiente para confirmar o resultado do pagamento; indisponibilidade temporária mantém o atendimento bloqueado até a resolução.
