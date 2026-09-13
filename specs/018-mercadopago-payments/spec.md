# Feature Specification: Pagamento de serviços (Pix e cartão)

**Feature Branch**: `[018-mercadopago-payments]`

**Created**: 2026-09-12

**Status**: Draft

**Input**: User description: "Implementação mercado pago: quero que o cliente cadastre um cartao como forma de pagamento de forma opcional. implementar o pix tambem. Crie as edge function do supabase necessarias e demais configurações necessarias (webhook, access token...). Usar o SDK nativo e tokenizar cartao dentro do app. Como tera outras formas de pagamento, o cadastro de cartão não é obrigatorio."

## Clarifications

### Session 2026-09-12

- Q: Este pagamento se aplica a quais fluxos de solicitação de serviço do app? → A: Aplica-se tanto ao fluxo padrão de solicitação de serviço (requestservice) quanto ao PitTec sob demanda (spec 015); em ambos, o despacho do prestador passa a depender de pagamento confirmado.
- Q: O que deve acontecer financeiramente quando um cliente cancela uma solicitação que já teve o pagamento aprovado? → A: Reembolso automático total, desde que o cancelamento ocorra antes da conclusão do atendimento pelo prestador.
- Q: O que o sistema deve fazer quando a própria tentativa de estorno automático falhar (ex.: erro do Mercado Pago, banco recusa a reversão)? → A: O sistema tenta novamente automaticamente algumas vezes; se continuar falhando, marca o caso para acompanhamento manual do suporte e avisa o cliente que o reembolso está em andamento.
- Q: Onde o cliente poderá cadastrar um cartão pela primeira vez? → A: Somente numa área dedicada de formas de pagamento (ex.: no Perfil); a etapa de pagamento da solicitação de serviço só permite escolher entre Pix ou um cartão já cadastrado antes, sem cadastro inline no checkout.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pagar a solicitação de serviço via Pix (Priority: P1)

Como cliente, quero pagar pelo serviço solicitado usando Pix ao finalizar minha solicitação, para
concluir o pagamento rapidamente sem precisar ter ou cadastrar um cartão.

**Why this priority**: Pix não exige cadastro prévio e é o caminho mais simples para o cliente
pagar, por isso é a forma de pagamento essencial que sozinha já entrega valor completo ao MVP.

**Independent Test**: Pode ser testado finalizando uma solicitação de serviço, escolhendo Pix,
gerando o código/QR, simulando a confirmação do pagamento e verificando que o prestador só é
acionado depois da confirmação.

**Acceptance Scenarios**:

1. **Given** o cliente concluiu os dados da solicitação de serviço, **When** chega à etapa de
   pagamento e escolhe Pix, **Then** o app exibe um código Pix (copia e cola e/ou QR) válido, sem
   exigir cadastro de cartão.
2. **Given** o cliente gerou um código Pix, **When** o pagamento é confirmado como aprovado,
   **Then** o app informa a confirmação ao cliente e a solicitação segue para o despacho do
   prestador.
3. **Given** o cliente gerou um código Pix e não realiza o pagamento dentro do prazo de validade,
   **When** o código expira, **Then** o app informa que o prazo expirou e oferece gerar um novo
   código ou escolher outra forma de pagamento, sem acionar nenhum prestador.
4. **Given** o cliente está na etapa de pagamento, **When** cancela antes de o pagamento ser
   aprovado, **Then** a solicitação não é enviada a nenhum prestador e nenhuma cobrança é
   efetivada.

---

### User Story 2 - Cadastrar um cartão como forma de pagamento (Priority: P2)

Como cliente, quero cadastrar opcionalmente um cartão de crédito ou débito, para poder pagar
solicitações futuras mais rapidamente sem digitar os dados do cartão a cada vez.

**Why this priority**: É uma conveniência que melhora a experiência de pagamentos recorrentes, mas
não é indispensável, já que o Pix (User Story 1) já cobre o caso essencial de pagar pelo serviço.

**Independent Test**: Pode ser testado acessando a área de formas de pagamento, cadastrando um
cartão válido e um cartão inválido, e verificando que apenas dados não sensíveis do cartão (ex.:
bandeira e últimos dígitos) ficam visíveis depois do cadastro.

**Acceptance Scenarios**:

1. **Given** o cliente está na área de formas de pagamento, **When** opta por cadastrar um cartão e
   informa dados válidos, **Then** o cartão é salvo com sucesso e passa a aparecer na lista de
   formas de pagamento com bandeira e últimos dígitos, sem exibir o número completo, CVV ou dados
   sensíveis novamente.
2. **Given** o cliente informa dados de cartão inválidos ou o cartão é recusado no cadastro,
   **When** a tentativa de cadastro é processada, **Then** o app explica o motivo de forma
   compreensível e permite tentar novamente ou seguir sem cadastrar cartão.
3. **Given** o cliente não deseja cadastrar nenhum cartão, **When** navega pelo app e finaliza
   solicitações de serviço, **Then** todas as funcionalidades continuam disponíveis normalmente
   usando Pix, sem qualquer bloqueio ou insistência obrigatória para cadastrar cartão.
4. **Given** o cliente tem um ou mais cartões cadastrados, **When** acessa a área de formas de
   pagamento, **Then** pode visualizar os cartões salvos e remover qualquer um deles.

---

### User Story 3 - Pagar a solicitação de serviço com cartão salvo (Priority: P3)

Como cliente que já cadastrou um cartão, quero selecioná-lo na etapa de pagamento da solicitação de
serviço, para concluir o pagamento sem redigitar os dados do cartão.

**Why this priority**: Só faz sentido depois que o cadastro de cartão (User Story 2) existe; é um
refinamento de conveniência sobre o pagamento essencial já coberto pelo Pix.

**Independent Test**: Pode ser testado com um cliente que já possui um cartão salvo, escolhendo esse
cartão na etapa de pagamento de uma solicitação e confirmando a cobrança.

**Acceptance Scenarios**:

1. **Given** o cliente tem ao menos um cartão salvo, **When** chega à etapa de pagamento de uma
   solicitação, **Then** o app oferece esse cartão como opção junto com o Pix.
2. **Given** o cliente escolhe um cartão salvo e confirma o pagamento, **When** a cobrança é
   aprovada, **Then** o app confirma o pagamento e a solicitação segue para o despacho do
   prestador, da mesma forma que ocorre com Pix aprovado.
3. **Given** o cliente escolhe um cartão salvo, **When** a cobrança é recusada pela operadora/banco,
   **Then** o app informa a recusa de forma compreensível e oferece tentar outro cartão ou pagar via
   Pix, sem acionar nenhum prestador.
4. **Given** o cliente ainda não tem nenhum cartão cadastrado, **When** chega à etapa de pagamento de
   uma solicitação, **Then** o app oferece apenas Pix, sem permitir cadastrar um cartão novo naquele
   momento, e indica que o cadastro de cartão fica disponível na área de formas de pagamento.

---

### User Story 4 - Reembolso automático quando o atendimento pago não se concretiza (Priority: P2)

Como cliente que já pagou por uma solicitação, quero receber automaticamente o valor de volta caso
eu cancele antes da conclusão do atendimento ou caso nenhum prestador aceite o chamado, para não
ficar com dinheiro pago por um serviço que não aconteceu.

**Why this priority**: É indispensável para a confiança do cliente em pagar antecipadamente, tanto
no fluxo padrão quanto no PitTec sob demanda, mas depende dos pagamentos (User Stories 1 e 3) já
existirem.

**Independent Test**: Pode ser testado pagando uma solicitação, cancelando-a antes da conclusão do
atendimento (ou simulando uma busca PitTec em que todos os prestadores recusam), e conferindo que o
valor é estornado automaticamente sem intervenção manual.

**Acceptance Scenarios**:

1. **Given** o cliente pagou por uma solicitação (padrão ou PitTec) e o pagamento foi aprovado,
   **When** o cliente cancela antes de o prestador confirmar a conclusão do atendimento, **Then** o
   sistema estorna automaticamente 100% do valor pago.
2. **Given** o cliente pagou pela chamada PitTec sob demanda, **When** a busca termina sem nenhum
   prestador disponível ou todos recusarem o chamado, **Then** o sistema estorna automaticamente
   100% do valor pago, já que nenhum atendimento ocorreu.
3. **Given** o prestador já confirmou a conclusão do atendimento, **When** o cliente tenta cancelar
   depois disso, **Then** o sistema não aplica o estorno automático desta feature e segue as regras
   de cancelamento pós-conclusão já existentes no app.

---

### Edge Cases

- O que acontece se o cliente perder a conexão com a internet durante a geração do código Pix ou a
  confirmação da cobrança no cartão? O app deve informar o problema e permitir tentar novamente sem
  duplicar cobrança.
- O que acontece se o cliente tentar cadastrar um cartão já cadastrado anteriormente (por ele ou por
  outra conta)? O app deve tratar isso sem duplicar o cartão na lista do cliente.
- O que acontece se o cliente fechar o app durante o pagamento (Pix pendente ou cartão em
  processamento) e retornar depois? O app deve mostrar o estado real e atualizado do pagamento
  daquela solicitação.
- O que acontece se dois pagamentos forem confirmados para a mesma solicitação (ex.: falha de rede
  seguida de nova tentativa bem-sucedida)? O sistema não deve cobrar o cliente duas vezes pela mesma
  solicitação.
- O que acontece se o cliente remover um cartão que está selecionado como forma de pagamento em uma
  solicitação ainda em andamento? A remoção não deve afetar pagamentos já aprovados.
- O que acontece se a própria tentativa de estorno automático falhar? O sistema tenta novamente
  algumas vezes automaticamente; se continuar falhando, o caso é marcado para acompanhamento manual
  do suporte e o cliente é avisado de que o reembolso está em andamento, sem ficar sem retorno algum.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE permitir que o cliente pague uma solicitação de serviço via Pix ao
  finalizá-la, sem exigir cadastro prévio de cartão.
- **FR-002**: O sistema DEVE gerar um código Pix válido (copia e cola e/ou QR) com prazo de validade
  definido e informar claramente ao cliente quando esse código expira.
- **FR-003**: O sistema DEVE notificar o cliente assim que um pagamento (Pix ou cartão) for
  confirmado como aprovado, pendente, recusado ou expirado.
- **FR-004**: O sistema DEVE permitir que o cliente cadastre, de forma totalmente opcional, um ou
  mais cartões como forma de pagamento.
- **FR-005**: O cadastro de cartão NÃO DEVE ser obrigatório para usar nenhuma outra funcionalidade
  do app, incluindo finalizar solicitações de serviço via Pix.
- **FR-006**: O sistema NÃO DEVE armazenar número completo do cartão, CVV ou data de validade em
  texto simples; apenas uma representação segura reutilizável para cobranças futuras e dados não
  sensíveis (bandeira, últimos dígitos) PODEM ser mantidos.
- **FR-007**: O sistema DEVE permitir que o cliente visualize e remova cada cartão cadastrado.
- **FR-008**: O sistema DEVE permitir que o cliente selecione um cartão salvo como forma de
  pagamento ao finalizar uma solicitação de serviço.
- **FR-009**: O despacho do prestador para atender a solicitação SÓ DEVE ocorrer depois que o
  pagamento correspondente for confirmado como aprovado.
- **FR-010**: O sistema DEVE informar o cliente de forma clara e compreensível quando um pagamento
  for recusado, ficar pendente ou expirar, oferecendo tentar novamente ou escolher outra forma de
  pagamento.
- **FR-011**: O sistema NÃO DEVE permitir cobrança duplicada para a mesma solicitação de serviço.
- **FR-012**: O sistema DEVE registrar o resultado de cada pagamento (forma, status, valor e data)
  vinculado à solicitação de serviço correspondente, para consulta posterior pelo cliente.
- **FR-013**: O cliente DEVE poder cancelar uma solicitação enquanto o pagamento ainda está
  pendente, sem que nenhum prestador seja acionado.
- **FR-014**: A etapa de pagamento (Pix e cartão salvo) DEVE se aplicar tanto ao fluxo padrão de
  solicitação de serviço quanto ao fluxo de chamada PitTec sob demanda (spec 015); em ambos, o
  despacho do prestador SÓ DEVE ocorrer após pagamento confirmado como aprovado (FR-009).
- **FR-015**: Quando o cliente cancelar uma solicitação com pagamento já aprovado antes da
  conclusão do atendimento pelo prestador, o sistema DEVE estornar automaticamente 100% do valor
  pago ao cliente.
- **FR-016**: Quando o cancelamento ocorrer depois de o prestador confirmar a conclusão do
  atendimento, o sistema NÃO DEVE aplicar o estorno automático da FR-015; esse caso segue as regras
  de cancelamento pós-conclusão já praticadas pelo app.
- **FR-017**: Na chamada PitTec sob demanda, se a busca terminar sem nenhum prestador aceitar (todos
  recusaram, expiraram ou não havia candidato elegível), o sistema DEVE estornar automaticamente
  100% do valor já pago, pois nenhum atendimento ocorreu.
- **FR-018**: Se uma tentativa de estorno automático (FR-015 ou FR-017) falhar, o sistema DEVE
  tentar novamente automaticamente algumas vezes; se continuar falhando após essas tentativas, o
  sistema DEVE marcar o caso para acompanhamento manual do suporte e informar o cliente de que o
  reembolso está em andamento, sem deixá-lo sem retorno sobre o dinheiro pago.
- **FR-019**: O cadastro de um novo cartão SÓ DEVE ficar disponível numa área dedicada de formas de
  pagamento; a etapa de pagamento de uma solicitação de serviço NÃO DEVE oferecer cadastro de cartão
  inline, apresentando apenas Pix e os cartões já cadastrados anteriormente.

### Key Entities *(include if feature involves data)*

- **Pagamento**: Representa uma cobrança vinculada a uma solicitação de serviço. Atributos:
  solicitação relacionada, forma de pagamento usada (Pix ou cartão salvo), valor, status (pendente,
  aprovado, recusado, expirado, cancelado, reembolsado), datas de criação e de confirmação.
- **Cartão salvo**: Representa um cartão cadastrado opcionalmente pelo cliente. Atributos: bandeira,
  últimos dígitos, referência segura reutilizável para cobrança futura (sem número completo, CVV ou
  validade em texto simples), data de cadastro.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: O cliente consegue concluir o pagamento via Pix de uma solicitação de serviço em até
  2 minutos, do início da etapa de pagamento até ver a confirmação na tela.
- **SC-002**: 100% das solicitações de serviço continuam podendo ser finalizadas e pagas via Pix por
  clientes que nunca cadastraram um cartão.
- **SC-003**: Nenhum dado sensível de cartão (número completo ou CVV) fica visível ou acessível ao
  cliente ou a qualquer pessoa após o momento do cadastro.
- **SC-004**: Nenhum prestador é despachado para uma solicitação cujo pagamento não esteja
  confirmado como aprovado.
- **SC-005**: Em pelo menos 95% das tentativas de pagamento (Pix ou cartão), o cliente recebe um
  retorno claro de sucesso, recusa, pendência ou expiração em poucos segundos.
- **SC-006**: Nenhuma solicitação de serviço é cobrada mais de uma vez pelo mesmo atendimento.
- **SC-007**: 100% dos cancelamentos ou buscas sem prestador disponível, ocorridos antes da
  conclusão do atendimento, resultam em estorno automático de 100% do valor pago ou, no caso raro de
  falha persistente do estorno, em um caso sinalizado para o suporte com o cliente já avisado do
  andamento — sem necessidade de o cliente abrir chamado por conta própria.
- **SC-008**: A etapa de pagamento funciona da mesma forma, com as mesmas garantias de reembolso,
  tanto no fluxo padrão de solicitação de serviço quanto na chamada PitTec sob demanda.

## Assumptions

- O pagamento acontece na etapa final da solicitação de serviço, antes do despacho ao prestador
  (conforme decisão já tomada com o cliente), e não após a conclusão do atendimento.
- O cliente pode cadastrar mais de um cartão e escolher entre eles no momento do pagamento; não há
  limite de quantidade de cartões salvos definido nesta versão.
- O prazo de validade do código Pix segue o padrão usual de mercado (na ordem de minutos) e, ao
  expirar, a solicitação correspondente não é enviada a nenhum prestador e pode ser reiniciada pelo
  cliente.
- A remoção de um cartão salvo não afeta pagamentos já aprovados anteriormente com esse cartão.
- Valores, moeda e regras fiscais seguem o já praticado hoje pelo app para o valor do serviço
  solicitado; esta feature não altera como o valor do serviço é calculado, apenas como ele é
  cobrado.
