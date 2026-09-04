# Feature Specification: Experiência mockada de UI do PitTec

**Feature Branch**: `[017-pittec-mocked-ui]`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "Ajustar as telas do PitTec para que o prestador tenha mapa na Home, alerta roxo com aceite ou recusa, navegação inferior específica, acompanhamento do cliente e valor do serviço; o cliente deve confirmar uma estimativa baseada em R$ 50 mais distância, acompanhar a busca e, após o aceite, ver ambos no mapa com atualização a cada 30 segundos. Encontro e conclusão exigem confirmação dos dois participantes. Por enquanto, todo o fluxo será mockado."

## Clarifications

### Session 2026-09-04

- Q: No protótipo mockado, como você quer demonstrar as ações do cliente e do PitTec sem comunicação real entre dois aparelhos? → A: Cada perfil terá um fluxo independente; a resposta da outra pessoa será simulada automaticamente após 3 segundos.
- Q: Na Home do PitTec, o mapa deve usar a localização real do aparelho ou uma posição totalmente simulada? → A: A localização real é obrigatória; sem ela, o mapa permanece bloqueado até o usuário conceder permissão e ativar a localização.
- Q: Qual valor por quilômetro deve ser somado aos R$ 50,00 fixos na estimativa mockada? → A: R$ 50,00 fixos mais R$ 10,00 por cada faixa de 5 km.
- Q: Quando um PitTec recusar a solicitação simulada, como a busca do cliente deve continuar? → A: A busca consulta automaticamente o próximo PitTec e amplia o perímetro em 5 km quando não restar candidato na faixa atual.
- Q: O que as opções “Dados da corrida” e “Valores” devem mostrar no protótipo do PitTec? → A: Dados da corrida mostra o atendimento atual ou mais recente; Valores mostra a composição e o total desse atendimento.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receber e responder a uma chamada na Home PitTec (Priority: P1)

Como prestador PitTec, quero iniciar na Home vendo minha localização no mapa e receber um destaque
visual claro quando surgir uma solicitação, para decidir rapidamente se aceito ou recuso o serviço.

**Why this priority**: A Home com mapa e a resposta à solicitação formam a entrada principal do
fluxo do prestador e são necessárias para demonstrar todas as etapas seguintes.

**Independent Test**: Pode ser testado abrindo a Home do PitTec em um cenário simulado, disparando
uma solicitação e verificando mapa, alerta roxo, informações essenciais e as ações “Aceitar” e
“Recusar”, sem depender do fluxo do cliente.

**Acceptance Scenarios**:

1. **Given** o prestador PitTec abriu sua Home e existe uma localização disponível, **When** a tela
   termina de carregar, **Then** o mapa mostra a posição atual do prestador e uma indicação textual
   de que ele está disponível para receber solicitações simuladas.
2. **Given** a permissão foi recusada ou a localização está desativada, **When** o prestador abre a
   Home, **Then** o mapa fica bloqueado, nenhuma posição simulada é exibida e a tela oferece ações
   para conceder a permissão ou ativar a localização.
3. **Given** o prestador está na Home, **When** uma nova solicitação simulada é apresentada,
   **Then** a tela realiza três pulsações roxas em até 3 segundos e mantém um card roxo visível com
   as ações “Aceitar” e “Recusar”.
4. **Given** uma solicitação está visível, **When** o prestador toca em “Aceitar”, **Then** a
   solicitação passa uma única vez para o estado aceito e o mapa do atendimento é apresentado.
5. **Given** uma solicitação está visível, **When** o prestador toca em “Recusar”, **Then** o alerta
   termina, a Home volta ao estado disponível e nenhum atendimento é iniciado.
6. **Given** a redução de movimento está ativa ou animações não podem ser exibidas, **When** chega
   uma solicitação, **Then** o card roxo permanece destacado de forma estática e contém a mesma
   informação e as mesmas ações.

---

### User Story 2 - Conferir e confirmar a estimativa antes da busca (Priority: P1)

Como cliente, quero ver uma estimativa de distância e valor antes de confirmar a busca por um
PitTec, para entender o custo simulado e decidir conscientemente se desejo continuar.

**Why this priority**: A confirmação evita iniciar a busca sem apresentar ao cliente as condições
principais do atendimento.

**Independent Test**: Pode ser testado a partir do card PitTec atual, abrindo a tela existente,
tocando em “Buscar PitTec” e validando estimativas nos perímetros simulados de 5 km, 10 km e 15 km.

**Acceptance Scenarios**:

1. **Given** o cliente abriu o card PitTec, **When** ainda não tocou em “Buscar PitTec”, **Then** a
   tela preserva a apresentação e o comportamento visual existentes.
2. **Given** o cliente toca em “Buscar PitTec”, **When** o cenário simulado encontra um prestador
   no perímetro de 5 km, **Then** a tela mostra o perímetro, o valor fixo de R$ 50,00, o adicional
   de R$ 10,00, o total de R$ 60,00 e o botão “Confirmar”.
3. **Given** o cenário encontra o prestador no perímetro de 10 km, **When** a estimativa é exibida,
   **Then** o adicional é R$ 20,00 e o total é R$ 70,00.
4. **Given** a estimativa está visível, **When** o cliente toca em “Confirmar”, **Then** a ação não
   pode ser repetida e um feedback explícito informa que o aplicativo está buscando um PitTec.
5. **Given** a estimativa está visível, **When** o cliente volta ou cancela antes de confirmar,
   **Then** nenhuma busca simulada é iniciada.
6. **Given** o PitTec consultado recusa, **When** a recusa simulada é confirmada, **Then** o cliente
   é informado e a mesma busca consulta automaticamente o próximo candidato, ampliando o
   perímetro em 5 km apenas quando não restar candidato na faixa atual.

---

### User Story 3 - Acompanhar o encontro no mapa (Priority: P1)

Como cliente ou prestador PitTec em uma solicitação aceita, quero ver os dois participantes
centralizados no mapa, com distância e valor total, para acompanhar a aproximação e entender o
estado atual do atendimento.

**Why this priority**: O acompanhamento compartilhado é o principal retorno visual após o aceite e
prepara a transição para o início do serviço.

**Independent Test**: Pode ser testado iniciando diretamente um cenário aceito com posições
simuladas e observando o enquadramento, as informações do card e pelo menos duas atualizações da
posição do PitTec.

**Acceptance Scenarios**:

1. **Given** o PitTec aceitou a solicitação, **When** o acompanhamento é aberto pelo cliente ou pelo
   prestador, **Then** o mapa enquadra inicialmente os dois marcadores, identifica cada participante
   e apresenta a distância estimada e o valor total do serviço.
2. **Given** o atendimento está no estado de deslocamento, **When** passam 30 segundos, **Then** a
   posição simulada do PitTec, o marcador e a distância restante são atualizados sem recarregar a
   tela.
3. **Given** o usuário movimentou o mapa manualmente, **When** chega uma atualização de posição,
   **Then** a câmera não interrompe sua exploração e uma ação permite centralizar novamente os dois
   participantes.
4. **Given** uma atualização simulada atrasa, **When** a última posição completa mais de 35
   segundos, **Then** a interface informa que a posição está desatualizada e mantém o último ponto
   conhecido.

---

### User Story 4 - Confirmar encontro e conclusão pelos dois participantes (Priority: P1)

Como participante de um atendimento PitTec, quero confirmar o encontro e a conclusão junto com a
outra pessoa, para que o fluxo não avance ou termine por uma ação unilateral.

**Why this priority**: A confirmação bilateral representa o acordo entre cliente e prestador e
define as transições essenciais do serviço simulado.

**Independent Test**: Pode ser testado em qualquer um dos perfis, confirmando a ação local e
verificando que o estado aguarda 3 segundos pela resposta automática simulada da outra pessoa
antes de avançar.

**Acceptance Scenarios**:

1. **Given** cliente e PitTec estão no estado de deslocamento, **When** somente um deles informa
   “Já nos encontramos”, **Then** o serviço não começa e ambos veem qual confirmação ainda falta.
2. **Given** um participante já confirmou o encontro, **When** o outro também confirma, **Then** o
   atendimento passa uma única vez para “Serviço em andamento”; no fluxo independente, essa
   segunda confirmação é simulada automaticamente após 3 segundos.
3. **Given** o serviço está em andamento, **When** somente um participante informa “Finalizar
   serviço”, **Then** o atendimento continua aberto e a interface informa que aguarda a outra
   confirmação.
4. **Given** ambos confirmaram a finalização, **When** a segunda confirmação é registrada, **Then**
   o atendimento passa para “Concluído”, interrompe as atualizações simuladas e oferece retorno à
   tela principal correspondente; no fluxo independente, essa segunda confirmação é simulada
   automaticamente após 3 segundos.
5. **Given** uma ação de encontro ou finalização já foi confirmada por aquele participante,
   **When** ele toca novamente ou a tela é recomposta, **Then** não é criada uma confirmação
   duplicada nem ocorre uma transição adicional.

---

### User Story 5 - Navegar pelas áreas do PitTec (Priority: P2)

Como prestador PitTec, quero usar a mesma navegação inferior visual do restante do aplicativo, com
opções adequadas ao meu perfil, para acessar rapidamente as informações relevantes.

**Why this priority**: A navegação consistente melhora orientação e acesso às áreas do prestador,
mas o fluxo central de solicitação ainda pode ser demonstrado pela Home.

**Independent Test**: Pode ser testado acessando cada opção da navegação inferior como PitTec e
comparando sua estrutura visual com a navegação dos demais perfis.

**Acceptance Scenarios**:

1. **Given** o usuário autenticado é PitTec, **When** visualiza uma tela principal do seu perfil,
   **Then** vê uma navegação inferior com as opções “Mapa”, “Dados da corrida”, “Valores” e
   “Perfil”.
2. **Given** o PitTec toca em qualquer opção, **When** a área correspondente abre, **Then** a opção
   atual fica identificada por texto e destaque visual, sem depender apenas de cor.
3. **Given** outro perfil utiliza a navegação inferior, **When** alterna entre suas áreas,
   **Then** a estrutura e o comportamento visual permanecem padronizados, embora as opções possam
   ser diferentes.
4. **Given** existe um atendimento atual ou concluído na sessão, **When** o PitTec abre “Dados da
   corrida”, **Then** vê os participantes, perímetro, distância, estado e momentos relevantes do
   atendimento atual ou mais recente.
5. **Given** existe um atendimento atual ou concluído na sessão, **When** o PitTec abre “Valores”,
   **Then** vê os R$ 50,00 fixos, o adicional de R$ 10,00 por faixa de 5 km e o total daquele
   atendimento, sem saldo ou ganhos acumulados.
6. **Given** ainda não existe atendimento na sessão, **When** o PitTec abre “Dados da corrida” ou
   “Valores”, **Then** vê um estado vazio que explica que as informações aparecerão após uma
   solicitação ser aceita.

### Edge Cases

- A localização atual está recusada, desativada ou indisponível: a Home bloqueia o mapa, não usa
  posição simulada e oferece ações para conceder a permissão, ativar a localização ou tentar
  novamente.
- O cenário não encontra PitTec no primeiro perímetro: a busca amplia em blocos de 5 km e atualiza
  a estimativa antes da confirmação.
- O cenário não possui nenhum PitTec: o cliente recebe um estado vazio com ações para tentar
  novamente ou voltar, sem busca infinita.
- O cliente toca repetidamente em “Confirmar” ou o PitTec toca repetidamente em “Aceitar”: apenas
  uma busca ou um atendimento é criado na sessão simulada.
- O PitTec recusa: o cliente é informado, o candidato recusado não volta a ser consultado e a busca
  segue automaticamente para o próximo PitTec, ampliando o perímetro em 5 km quando necessário.
- Uma posição simulada não chega no intervalo esperado: o último ponto permanece visível com aviso
  de desatualização.
- Cliente e PitTec ocupam o mesmo ponto: a distância é exibida como “menos de 100 m” e os
  marcadores continuam identificáveis.
- Apenas um participante confirma o encontro ou a conclusão: o estado permanece pendente até a
  confirmação do outro.
- O usuário sai da tela e retorna durante o fluxo: o estado atual da sessão simulada é reapresentado
  sem repetir a notificação ou a confirmação.
- Texto ampliado, leitor de tela ou redução de movimento estão ativos: ações, estados e valores
  permanecem compreensíveis e utilizáveis sem depender do piscar roxo, do mapa ou de ícones.

## Requirements *(mandatory)*

### Scope Boundaries

- Esta entrega é um protótipo navegável com dados e eventos de atendimento simulados; a posição
  inicial do prestador é obtida obrigatoriamente da localização real do aparelho.
- Não fazem parte deste escopo: despacho real, comunicação entre dispositivos, notificações em
  segundo plano, persistência após encerramento do aplicativo, cálculo de rota real, cobrança,
  pagamento, saldo financeiro ou integração com serviços externos.
- O card e a apresentação inicial atuais do PitTec para o cliente devem ser preservados; o novo
  fluxo começa na ação “Buscar PitTec”.
- A padronização da navegação inferior cobre aparência e comportamento compartilhados, sem exigir
  que todos os perfis tenham as mesmas opções.

### Functional Requirements

- **FR-001**: A Home do prestador PitTec DEVE ter o mapa como conteúdo principal e mostrar sua
  localização atual quando disponível.
- **FR-002**: Na ausência de permissão ou localização real disponível, a Home DEVE bloquear o mapa,
  NÃO DEVE exibir uma posição simulada e DEVE oferecer ações para conceder a permissão, ativar a
  localização ou tentar novamente.
- **FR-003**: A Home DEVE indicar textualmente o estado atual do PitTec: disponível, nova
  solicitação, deslocando-se, serviço em andamento ou concluído.
- **FR-004**: Uma nova solicitação simulada DEVE produzir três pulsações roxas em até 3 segundos e,
  depois, manter um card roxo persistente até aceite, recusa ou expiração do cenário.
- **FR-005**: O alerta DEVE oferecer botões distintos e acessíveis com os rótulos “Aceitar” e
  “Recusar”.
- **FR-006**: A compreensão e a resposta à solicitação NÃO DEVEM depender da animação ou somente da
  cor; com redução de movimento, o destaque DEVE ser estático.
- **FR-007**: Aceitar DEVE abrir o acompanhamento simulado e impedir uma segunda resposta à mesma
  solicitação; recusar DEVE encerrar o alerta e retornar ao estado disponível.
- **FR-008**: A navegação inferior DEVE seguir o mesmo padrão visual e de interação nas áreas
  principais do aplicativo, admitindo opções diferentes por perfil.
- **FR-009**: Para o perfil PitTec, a navegação inferior DEVE conter “Mapa”, “Dados da corrida”,
  “Valores” e “Perfil”.
- **FR-010**: A opção selecionada na navegação inferior DEVE ser identificada por texto e
  destaque visual; “Dados da corrida” DEVE mostrar participantes, perímetro, distância, estado e
  momentos relevantes do atendimento atual ou mais recente da sessão, enquanto “Valores” DEVE
  mostrar a parcela fixa, o adicional por faixa e o total desse mesmo atendimento.
- **FR-010A**: “Dados da corrida” e “Valores” DEVEM apresentar um estado vazio antes do primeiro
  atendimento da sessão e NÃO DEVEM exibir lista de corridas, saldo, extrato ou ganhos acumulados.
- **FR-011**: A tela inicial do cliente ao abrir o card PitTec DEVE manter o layout e o conteúdo
  atuais até que ele acione “Buscar PitTec”.
- **FR-012**: Após “Buscar PitTec”, a tela DEVE mostrar uma etapa de estimativa antes de iniciar a
  busca, contendo perímetro simulado, parcela fixa, parcela por distância, total e ação
  “Confirmar”.
- **FR-013**: A busca simulada DEVE trabalhar com perímetros progressivos de 5 km em 5 km e
  identificar em qual perímetro o prestador foi encontrado.
- **FR-014**: O valor estimado DEVE ser calculado como R$ 50,00 fixos mais R$ 10,00 por cada faixa
  completa de 5 km do perímetro simulado em que o PitTec foi encontrado; a fórmula é R$ 50,00 +
  R$ 10,00 × (perímetro ÷ 5 km).
- **FR-015**: A decomposição do valor e o total DEVEM ser exibidos em reais com duas casas
  decimais; para 5 km o total DEVE ser R$ 60,00 e para 10 km, R$ 70,00.
- **FR-016**: A busca simulada só DEVE começar após a confirmação explícita do cliente.
- **FR-017**: Durante a busca, o cliente DEVE receber feedback contínuo e textual que diferencie
  pelo menos procurando, aguardando resposta, recusado/continuando, encontrado e não encontrado;
  após uma recusa, a mesma busca DEVE descartar aquele candidato, consultar automaticamente o
  próximo PitTec da faixa atual e ampliar o perímetro em 5 km somente quando não restar candidato.
- **FR-018**: Toques repetidos em confirmação, aceite ou recusa NÃO DEVEM duplicar busca,
  solicitação ou atendimento dentro da sessão simulada.
- **FR-019**: Após o aceite, as telas do cliente e do PitTec DEVERÃO mostrar os dois participantes
  no mapa, com marcadores identificados, distância restante, valor total e estado do atendimento.
- **FR-020**: Ao abrir o acompanhamento, o mapa DEVE enquadrar cliente e PitTec simultaneamente e
  oferecer ação explícita para reenquadrá-los após movimentação manual.
- **FR-021**: A posição simulada do PitTec DEVE ser atualizada a cada 30 segundos durante o
  deslocamento, junto com o marcador e a distância restante.
- **FR-022**: Após 35 segundos sem uma nova posição, a interface DEVE identificar o último ponto
  como desatualizado até a próxima atualização.
- **FR-023**: Cliente e PitTec DEVEM poder confirmar individualmente “Já nos encontramos”, e o
  serviço só DEVE iniciar após a confirmação local e a confirmação automática simulada da outra
  pessoa, apresentada 3 segundos depois.
- **FR-024**: Enquanto faltar uma confirmação de encontro, ambos DEVEM ver que a outra parte ainda
  não confirmou e o estado DEVE permanecer “Aguardando encontro”.
- **FR-025**: Durante o serviço, cliente e PitTec DEVEM poder confirmar individualmente
  “Finalizar serviço”, e o atendimento só DEVE ser concluído após a confirmação local e a
  confirmação automática simulada da outra pessoa, apresentada 3 segundos depois.
- **FR-026**: Enquanto faltar uma confirmação de conclusão, ambos DEVEM ver que a outra parte ainda
  não confirmou e o estado DEVE permanecer “Serviço em andamento”.
- **FR-027**: Ao concluir, as atualizações de localização DEVEM parar e cada perfil DEVE receber
  uma ação para voltar à sua tela principal.
- **FR-028**: O fluxo mockado DEVE representar estados mutuamente exclusivos equivalentes a:
  disponível, estimando, aguardando confirmação do cliente, buscando, aguardando resposta do
  PitTec, recusado, aceito/deslocando-se, aguardando encontro, em andamento e concluído.
- **FR-029**: O estado corrente DEVE ser preservado enquanto a sessão simulada permanecer ativa e
  reapresentado quando o usuário navegar para fora e voltar.
- **FR-030**: Todos os estados de espera, vazio e indisponibilidade DEVEM apresentar mensagens
  compreensíveis e ao menos uma ação adequada quando houver recuperação possível.
- **FR-031**: Informações essenciais — participantes, distância, preço, estado e ações — DEVEM ser
  acessíveis também em texto e não apenas por mapa, cor, movimento ou ícones.
- **FR-032**: As telas DEVEM permanecer utilizáveis nos tamanhos suportados, com texto ampliado,
  áreas de toque adequadas e rolagem quando o conteúdo não couber.
- **FR-033**: Nenhuma ação do protótipo DEVE gerar cobrança, pagamento, saldo, notificação externa
  ou solicitação real.

### Key Entities

- **Mock PitTec Provider**: Prestador simulado com identificação, posição atual, disponibilidade e
  opção de resposta à solicitação.
- **Mock Client**: Cliente simulado com identificação e posição usada para estimativa e
  acompanhamento.
- **Distance Perimeter**: Faixa simulada em múltiplos de 5 km na qual o prestador é encontrado e
  que serve de base para a estimativa.
- **Service Estimate**: Composição do valor fixo de R$ 50,00, adicional de R$ 10,00 por faixa de
  5 km do perímetro e total apresentado antes da confirmação.
- **Mock Search**: Jornada iniciada após a confirmação do cliente, com perímetro atual, candidatos
  simulados já recusados, estados de procura, resposta do prestador e resultado.
- **Mock Service Request**: Solicitação apresentada ao PitTec, com possibilidade de aceite ou
  recusa e vínculo com a busca correspondente.
- **Mock Appointment**: Atendimento aceito que reúne participantes, valor, distância, posições,
  estado e confirmações bilaterais.
- **Participant Confirmation**: Registro de encontro ou conclusão iniciado pelo usuário do perfil
  atual e completado pela resposta automática simulada da outra pessoa após 3 segundos.
- **Mock Location Snapshot**: Posição vigente de um participante, horário da última atualização e
  indicação de atualidade.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 95% das aberturas com permissão e localização real disponíveis, a Home PitTec
  mostra mapa, posição atual e estado do prestador em até 3 segundos.
- **SC-002**: Em 100% das solicitações simuladas, o alerta apresenta destaque roxo e as ações
  “Aceitar” e “Recusar”, permanecendo acionável após o fim da animação.
- **SC-003**: Em 100% dos cenários de 5 km, 10 km e 15 km, o valor exibido corresponde a R$ 50,00
  mais R$ 10,00 por cada faixa de 5 km, resultando respectivamente em R$ 60,00, R$ 70,00 e
  R$ 80,00.
- **SC-004**: Em 100% das recusas com outro candidato disponível, o feedback informa a continuidade
  e a mesma busca consulta automaticamente o próximo PitTec sem repetir o candidato recusado;
  quando a faixa atual termina, o perímetro aumenta em 5 km.
- **SC-005**: Em 95% dos aceites simulados, os dois participantes, a distância, o valor e o estado
  aparecem no mapa de acompanhamento em até 2 segundos.
- **SC-006**: Durante o deslocamento simulado, 100% das atualizações programadas ocorrem no
  intervalo de 28 a 32 segundos e atualizam posição e distância sem reabrir a tela.
- **SC-007**: Em 100% dos testes de transição, a confirmação local não avança imediatamente o
  serviço; a resposta automática simulada, apresentada após 3 segundos, avança o estado uma única
  vez.
- **SC-008**: Em 100% das telas principais do PitTec, a navegação inferior apresenta “Mapa”,
  “Dados da corrida”, “Valores” e “Perfil” com seleção identificável; após um aceite, as duas áreas
  informativas exibem respectivamente os dados e a composição do valor do mesmo atendimento.
- **SC-009**: Pelo menos 90% dos participantes de um teste de usabilidade conseguem confirmar uma
  estimativa, identificar o estado da busca e concluir o fluxo bilateral na primeira tentativa,
  sem orientação externa.
- **SC-010**: Em 100% dos cenários testados com localização ausente, atraso de atualização, busca
  vazia ou redução de movimento, a interface mantém informação textual e uma resposta segura.
- **SC-011**: Nenhum cenário de validação produz solicitação, notificação externa, cobrança ou
  movimentação financeira real.

## Assumptions

- Esta especificação complementa a experiência visual descrita em
  `specs/015-pittec-on-demand`; durante o protótipo, os cenários simulados não alteram nem dependem
  do despacho real definido naquela feature.
- Os dados de prestadores, clientes, solicitações, respostas, deslocamento, valores e confirmações
  são simulados e existem apenas para demonstrar a experiência durante a sessão.
- A Home do PitTec exige permissão e localização real ativa; não existe fallback de posição
  simulada quando esses requisitos não forem atendidos.
- Os perímetros simulados crescem em blocos de 5 km; o cenário determina em qual faixa um
  prestador é encontrado.
- O adicional provisório é R$ 10,00 por faixa de 5 km do perímetro simulado, sem representar preço
  comercial definitivo.
- O alerta roxo acontece com o aplicativo aberto; notificações de sistema em segundo plano ou tela
  bloqueada ficam fora deste protótipo.
- A sessão simulada preserva o estado durante a navegação interna, mas pode reiniciar quando o
  aplicativo é encerrado.
- “Dados da corrida” e “Valores” mostram somente o atendimento atual ou mais recente da sessão:
  a primeira área reúne seus dados operacionais e a segunda sua composição de preço, sem lista de
  corridas, extrato, saldo, ganhos acumulados, pagamento ou dados financeiros reais.
- Cliente e PitTec possuem fluxos mockados independentes; após uma confirmação local, a resposta
  da outra pessoa é simulada automaticamente em 3 segundos, sem alternância de perfil nem
  sincronização entre aparelhos.
- O fluxo existente de autenticação e a aparência inicial atual do card PitTec permanecem fora do
  escopo de alteração.
