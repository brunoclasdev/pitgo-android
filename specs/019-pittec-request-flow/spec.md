# Feature Specification: Fluxo correto de solicitação do PitTec

**Feature Branch**: `019-pittec-request-flow`

**Created**: 2026-09-13

**Status**: Draft

**Input**: User description: "Fluxo do bittec: Ao clicar na opção de pitrec, aparecera um mapa com a
localização do cliente centralizado e um botão para solicitar o serviço. Ao clicar em solicitar ira
buscar os pitec (providers) proximos e ao achar aparecera um card falando o nome do pittec e
distancia. Quando eu confirmar o pittec que eu quero, aparecera um card com os dados dos valores, e
ao confirmar tudo ai o serviço começara. Algumas dessas funcionalidades já existem, mas o fluxo está
errado. Preciso que seja ajustado o que já existe e criado o que não existe."

## Contexto: o que já existe hoje vs. o que está errado

Hoje, ao abrir o PitTec, o cliente já vê um mapa centralizado em sua própria localização com
marcadores aproximados de PitTecs na região (`015-pittec-on-demand`) — essa parte já existe e
funciona. A partir daí, porém, o comportamento atual diverge do fluxo desejado:

- O botão de solicitar hoje abre um card de **estimativa de preço fixo/fictício** (um resquício do
  protótipo mockado, `017-pittec-mocked-ui`) que, ao ser confirmado, já dispara a busca automática
  **sem cobrar nada de verdade** e **sem nunca mostrar ao cliente qual PitTec foi encontrado** — o
  sistema escolhe e tenta prestadores em sequência de forma totalmente automática e anônima até um
  aceitar, sem qualquer card intermediário de "encontramos este PitTec" nem uma etapa de confirmação
  de valores reais.
- O pagamento real (Pix/cartão, `018-mercadopago-payments`) existe como uma tela separada, mas hoje
  só é alcançado por um caminho alternativo (a partir da Home, antes mesmo de abrir o mapa do
  PitTec) — nunca a partir do card de valores descrito nesta especificação.

Esta especificação define o fluxo correto: o cliente deve ver **qual PitTec específico foi
encontrado** (nome e distância) antes de decidir, e só depois ver e confirmar os **valores reais**
que serão cobrados — substituindo o card de estimativa fictícia e conectando corretamente ao
pagamento real já existente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver o mapa e solicitar o serviço (Priority: P1)

Como cliente, ao entrar na tela do PitTec eu quero ver um mapa centralizado na minha localização
atual e um botão claro para solicitar o serviço, para confirmar que o app sabe onde eu estou antes
de iniciar a busca.

**Why this priority**: É o ponto de entrada de todo o fluxo — sem ele nenhuma das etapas seguintes é
alcançável. Boa parte já existe; falta apenas garantir que o botão de solicitar não abra mais o card
de estimativa fictícia.

**Independent Test**: Abrir a tela do PitTec com localização disponível e confirmar que o mapa
centraliza na posição do cliente e que existe um único botão claro para solicitar o serviço, sem
nenhuma etapa de "estimativa" fictícia no caminho.

**Acceptance Scenarios**:

1. **Given** o cliente abre a tela do PitTec com permissão de localização concedida, **When** a tela
   termina de carregar, **Then** o mapa mostra a posição atual do cliente centralizada e um botão de
   "Solicitar" fica visível e habilitado.
2. **Given** o cliente ainda não concedeu permissão de localização, **When** ele abre a tela do
   PitTec, **Then** o app pede a permissão antes de centralizar o mapa ou habilitar o botão de
   solicitar.

---

### User Story 2 - Ver o PitTec encontrado antes de decidir (Priority: P1)

Como cliente, depois de tocar em "Solicitar" eu quero que o app busque os PitTecs próximos e, ao
encontrar um, me mostre um card com o nome dele e a distância até mim, para eu decidir se quero
seguir com aquele prestador específico antes de qualquer cobrança.

**Why this priority**: É a mudança central pedida — hoje o cliente nunca vê quem foi encontrado.
Sem isso, as etapas de confirmação de valores e início do serviço não fazem sentido, porque o
cliente estaria "confirmando" algo que nunca viu.

**Independent Test**: Tocar em "Solicitar" com pelo menos um PitTec disponível na região e confirmar
que aparece um card com nome e distância antes de qualquer cobrança ou início de atendimento.

**Acceptance Scenarios**:

1. **Given** o cliente tocou em "Solicitar" e existe ao menos um PitTec disponível na região,
   **When** a busca encontra um PitTec, **Then** um card aparece mostrando o nome do PitTec e a
   distância até o cliente, com uma ação clara para confirmar ou não seguir com ele.
2. **Given** o card do PitTec encontrado está visível, **When** o cliente não quer seguir com aquele
   prestador, **Then** ele pode recusar sem que nenhuma cobrança tenha ocorrido.
3. **Given** o cliente tocou em "Solicitar", **When** a busca não encontra nenhum PitTec disponível
   na região (ou nenhum aceita), **Then** o app mostra uma mensagem clara de que não há PitTec
   disponível no momento, sem travar a tela.

---

### User Story 3 - Confirmar valores e iniciar o serviço (Priority: P1)

Como cliente, depois de confirmar o PitTec que eu quero, eu quero ver um card com os valores reais
que serão cobrados e, ao confirmar, ver o serviço começar — sem surpresas de preço e sem o serviço
começar antes de eu aceitar o valor.

**Why this priority**: Fecha o fluxo — sem isso o cliente teria escolhido um PitTec mas nunca saberia
quanto vai pagar nem teria uma ação explícita de início. É o ponto onde este fluxo se conecta ao
pagamento real (Pix/cartão) já existente no app.

**Independent Test**: Confirmar um PitTec encontrado e verificar que aparece um card com os valores
reais a cobrar; confirmar esse card e verificar que o serviço passa a "em andamento" só depois disso.

**Acceptance Scenarios**:

1. **Given** o cliente confirmou o PitTec encontrado, **When** o card de valores aparece, **Then**
   ele mostra o valor real que será cobrado (não mais um valor fictício).
2. **Given** o card de valores está visível, **When** o cliente confirma, **Then** o serviço muda de
   "aguardando confirmação" para "em andamento" e o cliente pode acompanhar o PitTec normalmente
   (como já acontece hoje após um atendimento aceito).
3. **Given** o card de valores está visível, **When** o cliente desiste em vez de confirmar,
   **Then** nada é cobrado e nenhum atendimento é iniciado.

### Edge Cases

- O que acontece se, entre o card "PitTec encontrado" e a confirmação, o próprio PitTec deixar de
  estar disponível (ex.: aceitou outro cliente, ficou offline)? O cliente deve ser avisado e a busca
  deve continuar/reiniciar automaticamente, sem exigir que ele toque em "Solicitar" de novo.
- O que acontece se o pagamento (etapa de valores) falhar ou for recusado? O cliente deve ver o
  motivo de forma compreensível e poder tentar de novo ou escolher outra forma de pagamento, sem que
  o PitTec já escolhido seja perdido nem cobrado duas vezes.
- O que acontece se o cliente sair da tela (voltar) entre o card de "PitTec encontrado" e a
  confirmação de valores? Nenhuma cobrança deve ocorrer e nenhum atendimento deve começar.
- O que acontece se o cliente cancelar depois que o serviço já começou (após confirmar os valores)?
  Deve seguir a mesma regra de cancelamento/estorno já existente para atendimentos pagos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A tela do PitTec MUST mostrar um mapa centralizado na localização atual do cliente
  assim que aberta (com a permissão de localização concedida).
- **FR-002**: A tela do PitTec MUST oferecer um único botão claro para o cliente solicitar o
  serviço; nenhum card de estimativa de preço fictício MUST aparecer antes da busca.
- **FR-003**: Ao tocar em solicitar, o sistema MUST buscar PitTecs disponíveis próximos ao cliente.
- **FR-004**: Ao encontrar um PitTec disponível, o sistema MUST apresentar ao cliente um card com o
  nome do PitTec e a distância até ele, antes de qualquer cobrança ou início de atendimento.
- **FR-005**: O cliente MUST poder confirmar ou recusar o PitTec encontrado a partir desse card.
- **FR-006**: Se o cliente recusar o PitTec encontrado, o sistema MUST continuar a busca por outro
  PitTec disponível, sem cobrar nada.
- **FR-007**: Ao confirmar o PitTec encontrado, o sistema MUST apresentar um card com os valores
  reais a serem cobrados pelo serviço, reaproveitando a forma de pagamento (Pix ou cartão salvo) já
  existente no app.
- **FR-008**: O serviço MUST só começar depois que o cliente confirmar o card de valores e o
  pagamento correspondente for aprovado.
- **FR-009**: Se o cliente não confirmar os valores (desistir), nenhuma cobrança MUST ocorrer e
  nenhum atendimento MUST ser criado.
- **FR-010**: Se nenhum PitTec estiver disponível na região (ou nenhum aceitar), o sistema MUST
  informar isso claramente ao cliente, sem travar a tela e sem cobrar nada.
- **FR-011**: Se o PitTec confirmado deixar de estar disponível antes do início efetivo do serviço, o
  sistema MUST avisar o cliente e retomar a busca automaticamente por outro PitTec.
- **FR-012**: Cancelamentos depois do início do serviço MUST seguir a mesma regra de estorno já
  existente para atendimentos pagos.

### Key Entities *(include if feature involves data)*

- **PitTec encontrado**: representa um prestador específico localizado pela busca antes da
  confirmação — nome, distância até o cliente, e a possibilidade de ser confirmado ou recusado.
- **Valores do serviço**: o preço real a ser cobrado pelo atendimento, mostrado ao cliente antes de
  qualquer cobrança e ligado à forma de pagamento escolhida.
- **Solicitação de PitTec**: o pedido em andamento do cliente, que passa pelos estados de busca →
  PitTec encontrado (aguardando confirmação) → valores confirmados/pagos → serviço em andamento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das solicitações de PitTec mostram ao cliente o nome e a distância do prestador
  encontrado antes de qualquer cobrança.
- **SC-002**: 100% das solicitações de PitTec mostram o valor real a ser cobrado antes de o serviço
  começar — nenhuma cobrança ou início de atendimento ocorre sem essa confirmação explícita.
- **SC-003**: Nenhuma solicitação recusada ou abandonada pelo cliente (antes da confirmação de
  valores) resulta em cobrança.
- **SC-004**: Um cliente consegue ir da tela inicial do PitTec até confirmar um prestador específico
  em até 2 minutos em condições normais de rede e disponibilidade de PitTecs na região.

## Assumptions

- O card de "PitTec encontrado" (nome + distância) é uma mudança de comportamento em relação ao
  desenho anterior do PitTec, que escondia a identidade do prestador do cliente até o aceite por
  privacidade. Esta especificação assume que o produto agora prioriza transparência para o cliente
  nesta etapa — a mesma identidade só era exposta ao prestador de forma aproximada antes do aceite;
  aqui passa a valer o inverso também para o cliente.
- O "card de valores" reaproveita a tela/fluxo de pagamento (Pix e cartão salvo) que já existe no
  app — esta especificação não descreve uma nova forma de pagamento, apenas o ponto correto do fluxo
  em que ela deve aparecer.
- Um cliente só pode ter uma solicitação de PitTec ativa por vez (mesma regra já aplicada às demais
  modalidades de atendimento do app).
- A busca continua automaticamente entre prestadores (sem exigir nova ação do cliente) até encontrar
  um que aceite, um limite de tempo/raio ser atingido, ou o cliente cancelar — mesma mecânica de
  expansão de raio já existente.
