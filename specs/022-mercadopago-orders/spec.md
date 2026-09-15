# Feature Specification: Migração de pagamentos para Mercado Pago Orders

**Feature Branch**: `feature/implementacao-mercadopago`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "alterar a integração da api do mercado pago. - Atualizar para a implementação de API Orders"

## Clarifications

### Session 2026-09-15

- Q: O sistema deve concluir a verificação de pagamentos e as tentativas de reembolso mesmo quando o cliente fechar o app? → A: Sim. Continuar automaticamente com o app fechado e mostrar o resultado quando o cliente retornar.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pagar via Pix na integração atualizada (Priority: P1)

Como cliente, quero continuar pagando meu serviço por Pix, com valor e resultado confiáveis, após a atualização da integração de pagamentos.

**Why this priority**: Mantém disponível o pagamento sem cadastro de cartão e protege a continuidade da contratação.

**Independent Test**: Executar uma solicitação padrão e uma chamada PitTec até o pagamento Pix, conferir valor, código, vencimento e confirmação, e verificar a continuidade correta de cada atendimento.

**Acceptance Scenarios**:

1. **Given** uma solicitação elegível para cobrança, **When** o cliente escolhe Pix, **Then** recebe QR Code e código copia e cola com valor e vencimento, sem cadastrar cartão.
2. **Given** um pagamento pendente, **When** o pagamento integral é confirmado, **Then** o cliente vê a aprovação e o atendimento avança uma única vez à etapa permitida pelo fluxo.
3. **Given** um Pix vencido sem pagamento, **When** o cliente retorna à cobrança, **Then** vê a expiração e pode iniciar nova tentativa sem reutilizar o código vencido.
4. **Given** uma chamada PitTec com proposta de valor, **When** o cliente ainda não confirmou essa proposta, **Then** nenhuma cobrança é iniciada; após a confirmação, somente o valor e a versão aceitos podem ser cobrados.

---

### User Story 2 - Continuar usando cartões salvos (Priority: P1)

Como cliente, quero continuar cadastrando, selecionando e removendo meus cartões, e pagar com um cartão salvo sem perder minhas formas de pagamento na migração.

**Why this priority**: Cartão é uma forma de pagamento já oferecida e precisa permanecer funcional.

**Independent Test**: Usar um cartão cadastrado antes da migração e outro cadastrado depois, efetuar pagamentos aprovados e recusados e remover um cartão.

**Acceptance Scenarios**:

1. **Given** um cartão válido cadastrado antes da migração, **When** o cliente o seleciona e fornece eventual confirmação de segurança exigida, **Then** consegue pagar sem recadastrar o cartão por causa da migração.
2. **Given** uma cobrança recusada, **When** o resultado chega, **Then** o cliente vê uma mensagem compreensível e pode escolher outro cartão ou Pix, sem liberar a etapa dependente de aprovação.
3. **Given** a área de formas de pagamento, **When** o cliente cadastra ou remove um cartão, **Then** a lista reflete a operação; o cadastro continua opcional e fora da etapa de pagamento.

---

### User Story 3 - Recuperar o resultado sem pagar duas vezes (Priority: P1)

Como cliente, quero retomar um pagamento após perder a conexão ou fechar o app, sem sofrer nova cobrança ou ver uma confirmação incorreta.

**Why this priority**: Resultados incertos e repetições não podem gerar prejuízo financeiro nem liberar atendimento indevidamente.

**Independent Test**: Interromper a conexão após iniciar uma cobrança, repetir a confirmação e reabrir o app, incluindo um pagamento iniciado antes da migração.

**Acceptance Scenarios**:

1. **Given** uma cobrança cujo resultado ainda é desconhecido, **When** o cliente repete a ação ou reabre o app, **Then** o sistema recupera a tentativa existente antes de permitir outra cobrança.
2. **Given** uma aprovação já registrada, **When** chegam confirmações repetidas ou informações antigas de pendência, **Then** não há nova cobrança, regressão indevida do estado nem repetição da liberação do atendimento.
3. **Given** um pagamento iniciado antes da migração, **When** seu resultado é consultado ou confirmado depois dela, **Then** ele permanece vinculado ao mesmo cliente e serviço, sem recriação da cobrança.
4. **Given** uma informação de pagamento inválida, de outro cliente ou com valor divergente, **When** ela é recebida, **Then** não confirma a solicitação nem expõe dados de terceiros.
5. **Given** uma cobrança em verificação e o app fechado, **When** o resultado se torna disponível, **Then** o sistema resolve a pendência automaticamente, sem aguardar o retorno do cliente, e apresenta o resultado atualizado na próxima abertura.

---

### User Story 4 - Cancelar e acompanhar o reembolso (Priority: P1)

Como cliente, quero que cancelamentos elegíveis continuem devolvendo integralmente meu pagamento e que o andamento do reembolso seja informado.

**Why this priority**: A migração precisa preservar as garantias financeiras existentes.

**Independent Test**: Cancelar solicitações com pagamento pendente e aprovado, antes e depois da migração, e verificar sucesso, pendência e falha persistente do reembolso.

**Acceptance Scenarios**:

1. **Given** um pagamento aprovado e cancelamento elegível antes da conclusão do serviço, **When** o cancelamento é confirmado, **Then** o reembolso integral é solicitado automaticamente, inclusive para cobranças anteriores à migração.
2. **Given** um pagamento pendente em solicitação cancelada, **When** uma aprovação tardia é identificada, **Then** o atendimento não é reativado e o valor recebido segue para reembolso integral.
3. **Given** um reembolso com falha transitória, **When** a recuperação automática é executada, **Then** o sistema realiza até três tentativas automáticas e, persistindo a falha, sinaliza acompanhamento pelo suporte e informa o cliente.
4. **Given** um reembolso solicitado mas ainda não confirmado, **When** o cliente consulta o pagamento, **Then** vê que está em andamento; somente a confirmação permite exibir reembolsado.
5. **Given** uma busca paga encerrada sem atendimento, **When** as regras vigentes determinam devolução, **Then** o valor é integralmente reembolsado; o encerramento após conclusão não passa a permitir estorno automático.
6. **Given** um reembolso pendente e o app fechado, **When** há necessidade de verificar o resultado ou repetir uma tentativa elegível, **Then** o processamento continua automaticamente, respeitando o limite de tentativas e o encaminhamento ao suporte, e o cliente vê o andamento atualizado ao retornar.

### Edge Cases

- Confirmação de pagamento recebida antes de terminar o registro da tentativa: recuperar a associação sem perder a cobrança.
- Cliques simultâneos ou uso de dois dispositivos: manter uma única cobrança efetiva por obrigação de pagamento.
- Troca de meio de pagamento com tentativa anterior incerta: resolver a tentativa anterior antes de permitir uma nova cobrança.
- Aprovação tardia de uma tentativa substituída: não liberar outro atendimento e devolver eventual cobrança excedente.
- Mudança da proposta PitTec: pagamento de versão antiga não aprova uma proposta diferente.
- Estado desconhecido ou divergência entre a ordem e sua transação: manter o resultado em verificação, sem assumir aprovação.
- Indisponibilidade do processador: informar falha ou verificação pendente com orientação de recuperação, sem simular aprovação.
- Remoção de cartão após pagamento: preservar histórico e possibilidade de reembolso.
- Atendimento gratuito previsto nas regras atuais: continuar sem gerar cobrança de valor zero.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Novas cobranças de Pix e cartão DEVEM utilizar a API Orders do Mercado Pago, conforme solicitado, abrangendo criação, acompanhamento, confirmação, cancelamento e reembolso aplicáveis. Esta é a restrição de integração da feature.
- **FR-002**: O sistema DEVE preservar Pix e cartões salvos nos fluxos padrão e PitTec, sem criar novos meios de pagamento ou alterar preços e moeda.
- **FR-003**: O Pix DEVE apresentar QR Code, copia e cola e vencimento; códigos vencidos não podem ser apresentados como pagáveis em uma nova tentativa.
- **FR-004**: O cadastro de cartão DEVE permanecer opcional, na área dedicada, com listagem e remoção; cartões válidos anteriores à migração DEVEM continuar utilizáveis sem recadastro motivado pela migração.
- **FR-005**: O sistema DEVE distinguir pagamento pendente, aprovado, recusado, expirado, cancelado, em verificação e reembolso em andamento, concluído ou com falha, apresentando orientação de recuperação quando aplicável.
- **FR-006**: Somente confirmação confiável de pagamento integral do valor devido DEVE liberar a etapa dependente do pagamento. No fluxo padrão, preservar a regra de despacho; no PitTec, preservar aceite do prestador e confirmação do valor antes da cobrança, e aprovação antes do deslocamento e rastreamento.
- **FR-007**: Cada cobrança DEVE estar vinculada ao cliente, solicitação, valor e moeda corretos e, no PitTec, ao atendimento e à versão da proposta confirmada. Dados divergentes ou manipulação desses vínculos DEVEM ser rejeitados.
- **FR-008**: Repetições, concorrência e recuperação após falhas NÃO DEVEM gerar cobranças efetivas duplicadas ou liberar o mesmo atendimento mais de uma vez. Uma tentativa de resultado incerto DEVE ser reconciliada antes de outra cobrança; eventual recebimento excedente DEVE ser devolvido integralmente.
- **FR-009**: Ao retornar ao app, o cliente DEVE recuperar o resultado da tentativa existente. Confirmações repetidas ou fora de ordem NÃO DEVEM desfazer um resultado confirmado indevidamente.
- **FR-010**: Pagamentos anteriores à migração DEVEM continuar consultáveis e aptos a receber confirmação, cancelamento ou reembolso conforme seu estado, sem serem recriados como novas cobranças.
- **FR-011**: Cancelamentos elegíveis antes da conclusão e buscas pagas encerradas sem atendimento DEVEM manter reembolso automático integral. Cancelamento de pagamento pendente DEVE impedir continuidade do atendimento; recebimento posterior DEVE gerar devolução.
- **FR-012**: Reembolsos DEVEM ser protegidos contra duplicação, ter até três tentativas automáticas em caso de falha e encaminhamento ao suporte após falha persistente. O cliente DEVE distinguir solicitação de reembolso de devolução confirmada.
- **FR-013**: O histórico DEVE preservar valor, meio, datas, resultado e referências de cobrança e devolução, distinguindo a ordem de pagamento de suas transações e mantendo rastreabilidade dos registros anteriores.
- **FR-014**: Somente o cliente autorizado DEVE acessar ou operar seus pagamentos e cartões; confirmações externas DEVEM ter origem e vínculo verificados. Credenciais privadas e dados completos de cartão ou código de segurança NÃO DEVEM ser expostos em telas, históricos ou registros operacionais.
- **FR-015**: A migração DEVE preservar as regras atuais de serviços gratuitos e de cancelamento pós-conclusão, sem introduzir cobrança nesses casos ou novo direito a estorno automático.
- **FR-016**: A verificação de pagamentos com resultado incerto e o processamento de reembolsos, incluindo suas tentativas automáticas e encaminhamento ao suporte, DEVEM continuar com o app fechado, sem depender de nova ação ou sessão ativa do cliente. Ao retornar, o cliente DEVE visualizar o resultado ou andamento atualizado, respeitando as regras de autorização de acesso.

### Key Entities *(include if feature involves data)*

- **Ordem de pagamento**: Agrupa a cobrança de um serviço, com referência externa, cliente, valor, moeda e situação consolidada.
- **Tentativa/transação de pagamento**: Registra meio escolhido, resultado, datas e vínculo com a ordem e o serviço; distingue recuperação da mesma tentativa de uma nova tentativa autorizada.
- **Pagamento anterior à migração**: Cobrança existente com identificação e histórico preservados, ainda sujeita a confirmação e devolução.
- **Cartão salvo**: Forma de pagamento do cliente, representada por referência segura, bandeira e últimos dígitos.
- **Reembolso**: Devolução vinculada à cobrança original, com valor, motivo, andamento, tentativas e necessidade de acompanhamento.
- **Contexto do atendimento**: Solicitação e, quando aplicável, proposta de valor confirmada que determina a elegibilidade da cobrança e da continuidade do serviço.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Na validação dos dois fluxos de serviço, 100% dos cenários de pagamento Pix e cartão aprovado, recusado e pendente apresentam o resultado correto e a ação seguinte correspondente.
- **SC-002**: Em 100% dos cenários de repetição, concorrência, perda de conexão e retorno ao app, não há cobrança líquida duplicada nem liberação duplicada do atendimento.
- **SC-003**: Nenhum atendimento avança à etapa dependente de pagamento sem confirmação integral do valor correto e, no PitTec, da proposta aceita pelo cliente.
- **SC-004**: 100% dos pagamentos e cartões válidos selecionados para validação da transição mantêm histórico e operações aplicáveis, sem recobrança ou recadastro causado pela migração.
- **SC-005**: 100% dos cancelamentos elegíveis validados resultam em devolução integral confirmada ou acompanhamento explícito de reembolso pendente, com sinalização ao suporte após falha persistente.
- **SC-006**: Com conectividade disponível, em pelo menos 95% dos cenários de validação o cliente vê aprovação, recusa ou indicação de processamento em até 10 segundos após a ação de pagar; espera pela ação do pagador no banco não integra esse prazo.
- **SC-007**: Em todos os cenários de revisão de telas, histórico e registros operacionais, há zero exposição de credenciais privadas, número completo de cartão ou código de segurança.
- **SC-008**: Em 100% dos cenários de validação com o app fechado, verificações de pagamentos e tentativas de reembolso continuam sem reabertura ou ação do cliente; quando o processador disponibiliza um resultado conclusivo, ele é registrado automaticamente e exibido no retorno ao app. Falhas persistentes de reembolso geram encaminhamento ao suporte após o limite previsto.

## Assumptions

- O pedido significa migrar o Checkout API para Orders, mantendo a experiência atual de pagamento no app. A referência oficial distingue essa integração e contempla Pix, cartões, notificações e gestão de pagamentos: [Checkout API](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api/overview) e [Pix com Orders](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/pix), consultadas em 2026-09-15.
- A base funcional é a spec 018, com as alterações posteriores das specs 019 e 020 no momento da cobrança PitTec. A migração não antecipa pagamento para antes do aceite do prestador e da confirmação da proposta.
- O escopo é o aplicativo Android e os serviços de pagamento compartilhados presentes neste repositório; não inclui implementação de outro aplicativo.
- Preservar as referências existentes de cartões e cobranças é requisito; a compatibilidade concreta, os estados do processador e a estratégia de transição serão verificados no planejamento com documentação oficial e ambiente de homologação.
- Dependências: conta Mercado Pago habilitada para os meios utilizados, credenciais de homologação e produção, recebimento de confirmações e acesso aos registros existentes. Esta especificação não exige divulgar credenciais.
- Não estão incluídos novos parcelamentos, assinaturas, divisão de pagamentos, repasses, reembolso parcial, alteração de preços, redesign de telas ou mudança nas regras de negócio do atendimento.
- Os cenários descrevem critérios de aceitação e podem ser verificados manualmente em homologação; não solicitam geração automática de testes, conforme a constituição do projeto.
