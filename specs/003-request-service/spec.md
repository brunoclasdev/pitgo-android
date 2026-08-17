# Feature Specification: Solicitar Atendimento

**Feature Branch**: `develop`

**Created**: 2026-08-17

**Status**: Draft

**Input**: User description: "Criar a tela de detalhes e solicitação do serviço selecionado na Home, com resumo do serviço, veículo, local, descrição opcional, estimativas, escolha de pagamento, ação simulada e navegação inferior persistente."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Revisar o atendimento selecionado (Priority: P1)

Como usuário que escolheu um serviço na Home, quero abrir a solicitação com o serviço, o veículo,
o local e as estimativas já preenchidos para confirmar rapidamente que o atendimento corresponde à
minha necessidade.

**Why this priority**: A continuidade correta entre a Home e o resumo do atendimento é o valor
central da feature e evita que o usuário solicite o serviço errado.

**Independent Test**: Pode ser testado selecionando cada serviço suportado na Home e confirmando
que a tela de solicitação abre com o título, o nome, o ícone e o preço correspondentes, além dos
dados de veículo, local e chegada estimada.

**Acceptance Scenarios**:

1. **Given** que o usuário visualiza os serviços na Home, **When** seleciona um card de serviço,
   **Then** a tela "Solicitar atendimento" é aberta com o serviço escolhido.
2. **Given** que o usuário selecionou Bateria com preço inicial de R$ 320, **When** a tela é
   apresentada, **Then** o resumo identifica Bateria e a estimativa exibe R$ 320.
3. **Given** que a tela foi aberta com um serviço suportado, **When** o usuário revisa o conteúdo,
   **Then** encontra o veículo selecionado, o endereço do atendimento e a chegada estimada em
   seções visualmente distintas.
4. **Given** que o conteúdo excede a altura disponível, **When** o usuário percorre a tela,
   **Then** consegue acessar todas as informações sem que a navegação inferior cubra o conteúdo.

---

### User Story 2 - Informar o problema e o pagamento (Priority: P2)

Como usuário que revisou o serviço, quero descrever opcionalmente o problema e escolher a forma de
pagamento para personalizar a solicitação antes de continuar.

**Why this priority**: Essas escolhas complementam os dados essenciais e dão ao usuário controle
sobre as informações que acompanharão a intenção de atendimento.

**Independent Test**: Pode ser testado digitando e alterando uma descrição com várias linhas e
selecionando PIX, Crédito e Débito sucessivamente, verificando que o texto é preservado durante a
interação e somente uma opção permanece selecionada.

**Acceptance Scenarios**:

1. **Given** que a descrição está vazia, **When** o usuário acessa o campo, **Then** encontra a
   indicação de que ele é opcional e o exemplo "Ex.: Carro não está ligando".
2. **Given** que o campo está disponível, **When** o usuário insere uma descrição com várias
   linhas, **Then** o conteúdo digitado permanece visível e editável enquanto a tela estiver
   aberta.
3. **Given** que a tela acabou de abrir, **When** as formas de pagamento são exibidas, **Then** PIX
   é a única opção selecionada.
4. **Given** que PIX está selecionado, **When** o usuário escolhe Crédito ou Débito, **Then** a nova
   opção passa a ser a única selecionada e recebe identificação visual e textual distinta.

---

### User Story 3 - Acionar a solicitação simulada (Priority: P3)

Como usuário que conferiu os dados, quero tocar na ação principal para concluir a simulação desta
etapa e perceber imediatamente que meu toque foi reconhecido.

**Why this priority**: A ação completa a jornada visual e permite validar o fluxo de interação sem
antecipar criação real, busca de prestador ou cobrança.

**Independent Test**: Pode ser testado tocando em "SOLICITAR PITTECH" e verificando uma única
execução da ação simulada, sem criar atendimento, cobrar ou iniciar integrações externas.

**Acceptance Scenarios**:

1. **Given** que os dados da solicitação estão visíveis, **When** o usuário toca em "SOLICITAR
   PITTECH", **Then** a ação simulada configurada é executada uma única vez e o toque recebe
   feedback imediato.
2. **Given** que a ação principal está disponível, **When** o usuário lê a área abaixo dela,
   **Then** encontra o aviso de que o valor final pode variar conforme diagnóstico e peças usadas.
3. **Given** que o usuário toca repetidamente na ação principal em curto intervalo, **When** a
   primeira ação simulada ainda está sendo processada, **Then** não são registradas execuções
   concorrentes duplicadas.

---

### User Story 4 - Sair ou mudar de área (Priority: P4)

Como usuário, quero voltar à tela anterior ou usar a navegação principal sem perder a orientação
dentro do aplicativo.

**Why this priority**: Uma saída previsível evita aprisionar o usuário no fluxo e preserva o padrão
de navegação já conhecido na Home.

**Independent Test**: Pode ser testado abrindo a solicitação a partir da Home, usando a ação de
voltar e confirmando que a navegação inferior permanece visível, acessível e com o mesmo
comportamento demonstrativo da Home.

**Acceptance Scenarios**:

1. **Given** que a solicitação foi aberta a partir da Home, **When** o usuário aciona Voltar,
   **Then** retorna à Home no estado de navegação anterior.
2. **Given** que a tela de solicitação está aberta, **When** o usuário observa a navegação inferior,
   **Then** encontra Início, Serviços, Garagem, Atendimentos e Perfil, com Início identificado como
   a área atual.
3. **Given** que a navegação inferior está visível, **When** o usuário escolhe outro item,
   **Then** recebe feedback visual, permanece na solicitação e Início continua selecionado, como
   ocorre na Home nesta etapa demonstrativa.

### Edge Cases

- Se o identificador do serviço estiver ausente ou não corresponder ao catálogo suportado, a tela
  informa que o serviço não pôde ser carregado e permite voltar, sem exibir preço de outro serviço.
- Nomes extensos de serviço ou veículo e endereços longos permanecem compreensíveis, sem
  sobreposição ou truncamento que elimine informação essencial.
- Com fonte ampliada ou em tela estreita, cards, opções de pagamento e estimativas permanecem
  legíveis e alcançáveis; informações não dependem somente de cor.
- A abertura e o fechamento do teclado não escondem permanentemente o campo, a ação principal nem
  o aviso; o usuário pode alcançá-los por rolagem.
- Uma descrição vazia não impede a ação principal, pois o campo é opcional.
- A troca rápida entre formas de pagamento termina com exatamente uma opção selecionada.
- O preço apresentado no resumo e na estimativa permanece associado ao serviço selecionado, sem
  reutilizar o valor de uma seleção anterior.
- A navegação inferior permanece fixa enquanto somente o conteúdo principal rola.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST abrir a tela "Solicitar atendimento" quando o usuário selecionar um
  card de serviço suportado na Home.
- **FR-002**: O contexto recebido pela tela MUST identificar o serviço por `serviceId` e MUST
  disponibilizar o nome e o preço inicial correspondentes, sem exigir o transporte de todo o
  catálogo ou de um objeto completo de serviço.
- **FR-003**: O sistema MUST preservar a correspondência entre identificador, nome, ícone e preço
  para Bateria, Troca de óleo, Pneus, Luz no painel, Pequenos reparos e Carro não liga.
- **FR-004**: A tela MUST apresentar o título "Solicitar atendimento" e uma ação de retorno à tela
  anterior.
- **FR-005**: A tela MUST apresentar um resumo do serviço em card, identificado pelo rótulo
  "Serviço", contendo o nome e o ícone correspondente ao serviço selecionado.
- **FR-006**: A tela MUST apresentar em card o veículo atualmente selecionado pelo usuário,
  acompanhado de identificação visual relacionada a veículo ou manutenção.
- **FR-007**: A tela MUST apresentar em card o local do atendimento, acompanhado de identificação
  visual de localização e do endereço disponível no estado da solicitação.
- **FR-008**: A tela MUST oferecer um campo opcional identificado por "Descreva o problema
  (opcional)" e exemplificado por "Ex.: Carro não está ligando".
- **FR-009**: O campo de descrição MUST aceitar, exibir e permitir editar conteúdo em múltiplas
  linhas, com área inicial suficiente para aproximadamente quatro linhas de texto.
- **FR-010**: A ausência de uma descrição MUST NOT impedir o acionamento da solicitação simulada.
- **FR-011**: A tela MUST apresentar a chegada estimada em um card identificado por "Chegada
  estimada" e acompanhado de identificação visual de horário.
- **FR-012**: A tela MUST apresentar o preço estimado em um card identificado por "Estimativa",
  usando o valor associado ao serviço selecionado.
- **FR-013**: Os cards de chegada e preço MUST aparecer lado a lado quando couberem de forma
  legível e MUST adaptar sua apresentação em telas estreitas ou com fonte ampliada sem ocultar
  informação.
- **FR-014**: A tela MUST apresentar uma seção "PAGAMENTO" com as opções PIX, Crédito e Débito
  dispostas horizontalmente e alcançáveis em smartphones suportados.
- **FR-015**: PIX MUST ser a única forma de pagamento selecionada na abertura da tela.
- **FR-016**: Usuários MUST conseguir selecionar qualquer uma das três formas de pagamento.
- **FR-017**: Selecionar uma forma de pagamento MUST desmarcar a opção anterior, mantendo
  exatamente uma opção selecionada.
- **FR-018**: A forma selecionada MUST ser distinguível por borda roxa, fundo roxo claro, texto
  roxo e indicação que não dependa exclusivamente da cor; as demais MUST usar fundo branco e
  borda cinza.
- **FR-019**: A tela MUST apresentar a ação principal "SOLICITAR PITTECH" em largura disponível,
  com destaque roxo, texto branco em ênfase e formato arredondado consistente com a identidade do
  aplicativo.
- **FR-020**: Acionar "SOLICITAR PITTECH" MUST produzir feedback imediato e executar somente a
  ação simulada fornecida para esta etapa.
- **FR-021**: A ação simulada MUST NOT criar atendimento real, buscar PitTech, iniciar pagamento,
  cobrar, consultar localização, calcular distância ou chegada, enviar notificação ou acessar um
  serviço remoto.
- **FR-022**: A tela MUST impedir execuções concorrentes duplicadas da ação simulada causadas por
  toques repetidos em curto intervalo.
- **FR-023**: Abaixo da ação principal, a tela MUST exibir de forma centralizada o aviso "Valor
  final pode variar conforme diagnóstico e peças usadas.".
- **FR-024**: A tela MUST manter fixa na parte inferior a mesma navegação principal da Home, com
  os destinos Início, Serviços, Garagem, Atendimentos e Perfil.
- **FR-025**: A navegação inferior MUST identificar Início como área atual ao abrir a solicitação a
  partir da Home; selecionar outro item MUST fornecer feedback visual, manter o usuário na tela e
  preservar Início como seleção, sem criar destinos adicionais nesta etapa.
- **FR-026**: O conteúdo principal MUST permitir rolagem vertical independente da navegação
  inferior e MUST reservar espaço para que nenhum conteúdo fique escondido atrás dela.
- **FR-027**: A apresentação MUST usar fundo claro, superfícies brancas, bordas suaves, cantos
  arredondados, elevação discreta, roxo como cor principal, textos principais escuros e textos
  secundários cinza, mantendo consistência visual com a Home.
- **FR-028**: Controles, ícones e textos MUST permanecer compreensíveis em diferentes tamanhos de
  smartphone e com ampliação de fonte, com áreas de interação adequadas e descrições acessíveis
  quando o significado visual não for suficiente.
- **FR-029**: O estado da tela MUST representar um único serviço selecionado, veículo, endereço,
  descrição do problema, chegada estimada, preço estimado e forma de pagamento.
- **FR-030**: Alterações na descrição e na forma de pagamento MUST ser refletidas imediatamente no
  estado apresentado enquanto a tela permanecer aberta.
- **FR-031**: Nesta etapa, veículo, endereço e chegada estimada MUST poder ser fornecidos por dados
  locais simulados, sem dependência de backend ou geolocalização.
- **FR-032**: Se o serviço não puder ser identificado, o sistema MUST exibir uma mensagem
  compreensível, MUST NOT mostrar uma estimativa de outro serviço e MUST permitir o retorno.

### Key Entities *(include if feature involves data)*

- **Serviço Selecionado**: Serviço escolhido na Home; possui identificador, nome, ícone e preço
  inicial usado como estimativa.
- **Solicitação em Preparação**: Estado temporário da tela; reúne serviço selecionado, veículo,
  endereço, descrição opcional, chegada estimada, preço estimado e forma de pagamento. Não
  representa um atendimento criado.
- **Veículo Selecionado**: Veículo associado ao contexto do usuário, identificado por descrição
  legível que pode incluir modelo, versão e ano.
- **Local do Atendimento**: Endereço exibido como referência para o atendimento simulado.
- **Forma de Pagamento**: Escolha exclusiva entre PIX, Crédito e Débito; inicia em PIX e pode ser
  alterada enquanto a tela estiver aberta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos serviços suportados selecionados na Home, a tela apresenta o nome, o
  ícone e o preço correspondentes, sem mistura de dados entre serviços.
- **SC-002**: Pelo menos 90% dos participantes conseguem revisar serviço, veículo, local, chegada e
  preço em até 15 segundos após selecionar um serviço.
- **SC-003**: Pelo menos 95% dos participantes conseguem inserir uma descrição opcional, trocar a
  forma de pagamento e acionar a solicitação simulada na primeira tentativa, sem orientação.
- **SC-004**: Em 100% das trocas entre PIX, Crédito e Débito, exatamente uma opção permanece
  selecionada.
- **SC-005**: Em 100% dos smartphones suportados e cenários de fonte ampliada avaliados, todo o
  conteúdo pode ser alcançado e lido sem ficar escondido atrás da navegação inferior.
- **SC-006**: O usuário consegue retornar à Home com uma única ação e, em 100% das telas
  suportadas avaliadas, identificar e acionar os cinco itens da navegação inferior sem mudança de
  destino ou de seleção nesta etapa demonstrativa.
- **SC-007**: Em 100% das execuções de validação desta etapa, acionar "SOLICITAR PITTECH" não cria
  atendimento real, não inicia cobrança e não acessa integrações externas.
- **SC-008**: Em avaliação visual comparativa, 100% dos elementos essenciais solicitados estão
  presentes e pelo menos 90% dos avaliadores reconhecem a tela como consistente com a Home.

## Assumptions

- A Home e sua navegação inferior já existem e oferecem os seis serviços citados como entradas
  válidas para esta tela; integrar os cards existentes à nova continuidade faz parte da feature.
- Ao entrar pela Home, Início continua sendo o destino ativo porque a solicitação é uma etapa
  interna da jornada iniciada nessa área.
- Os demais itens da navegação inferior permanecem demonstrativos, como na Home atual; telas de
  Serviços, Garagem, Atendimentos e Perfil não fazem parte desta feature.
- O veículo local simulado inicial é "BMW 320i M Sport · 2020".
- O endereço local simulado inicial é "Av. Paulista, 1500 · São Paulo".
- A chegada estimada local inicial é "20-40 min".
- O preço estimado é o preço inicial já associado ao serviço selecionado; não há diagnóstico,
  recálculo ou consulta externa nesta etapa.
- O botão principal executa uma ação simulada e fornece feedback de interação, sem exigir tela de
  confirmação ou navegação adicional.
- Os dados editados são temporários e só precisam existir enquanto a tela estiver aberta; não há
  persistência da descrição ou da forma de pagamento nesta feature.
- A referência visual é interpretada em conjunto com o design já presente na Home; na ausência de
  um arquivo de imagem anexado, os requisitos visuais textuais desta especificação são a fonte de
  validação.
- Criação real do atendimento, integração com backend, pagamento, busca de prestador,
  geolocalização, cálculo de distância ou chegada, cobrança, notificações e atualizações em tempo
  real permanecem fora do escopo.
