# Feature Specification: PitTec sob demanda

**Feature Branch**: `[015-pittec-on-demand]`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Criar o serviço PitTec (Pit, do nome do app, mais Tec, de Técnico). Ao tocar no card PitTec, o cliente vê no mapa os prestadores PitTec próximos e pode usar o botão 'Chamar PitTec'. O app aciona o prestador mais próximo e, se ele recusar, tenta o próximo até alguém aceitar. Após o aceite, o mapa mostra cliente e PitTec, a distância em quilômetros e uma linha ou rastro do percurso até o cliente. Durante a busca, um card inferior informa que o app está procurando; após o aceite, a mesma experiência acompanha o deslocamento. No cadastro de empresa, PitTec passa a ser uma terceira modalidade além de móvel e fixo."

## Clarifications

### Session 2026-09-02

- Q: Depois que um PitTec aceitar, quem pode cancelar o atendimento e o que deve acontecer em seguida? → A: Cliente e PitTec podem cancelar com motivo obrigatório; se o PitTec cancelar, a busca recomeça, e se o cliente cancelar, o fluxo termina.
- Q: Antes do despacho, o cliente deve informar qual problema técnico precisa resolver? → A: Não; a chamada é genérica e o despacho começa imediatamente.
- Q: Um PitTec marcado como disponível deve receber novas chamadas quando o aplicativo estiver em segundo plano ou a tela estiver bloqueada? → A: Sim; recebe alerta visível e sonoro em primeiro plano, segundo plano e com a tela bloqueada.
- Q: Antes de uma chamada ser aceita, o mapa do cliente deve mostrar a posição exata ou apenas aproximada dos PitTecs disponíveis? → A: Posição aproximada antes do aceite e exata somente durante o atendimento aceito.
- Q: Como devem ser confirmadas a chegada do PitTec e a conclusão do atendimento? → A: O PitTec confirma manualmente a chegada e a conclusão; o cliente acompanha os estados.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Encontrar PitTecs próximos no mapa (Priority: P1)

Como cliente que precisa de ajuda técnica no local, quero tocar no card PitTec e visualizar os
prestadores disponíveis nas proximidades, para saber que há atendimento na minha região antes de
fazer uma chamada.

**Why this priority**: A descoberta geográfica é a porta de entrada do novo serviço e permite ao
cliente entender imediatamente a cobertura disponível.

**Independent Test**: Pode ser testado com prestadores PitTec disponíveis e indisponíveis em
distâncias conhecidas, abrindo o card PitTec e conferindo quais marcadores aparecem no mapa e como
os estados sem localização ou sem prestadores são explicados.

**Acceptance Scenarios**:

1. **Given** o cliente autorizou uma localização válida e existem PitTecs disponíveis próximos,
   **When** toca no card PitTec, **Then** o app abre um mapa centrado na região do cliente, mostra
   sua posição e os PitTecs elegíveis próximos com marcadores aproximados e distintos.
2. **Given** há empresas fixas, móveis e PitTec na mesma região, **When** o mapa PitTec é aberto,
   **Then** somente empresas da modalidade PitTec que estejam ativas, disponíveis e com posição
   recente aparecem como prestadores.
3. **Given** não há PitTec elegível na área coberta, **When** a descoberta termina, **Then** o
   cliente vê uma mensagem clara, pode tentar novamente e continua podendo usar as demais áreas do
   aplicativo.
4. **Given** a localização do cliente está desativada, recusada ou indisponível, **When** tenta
   abrir a experiência PitTec, **Then** o app explica por que não pode ordenar prestadores por
   proximidade e oferece uma ação adequada para tentar novamente ou conceder acesso.

---

### User Story 2 - Chamar automaticamente o PitTec mais próximo (Priority: P1)

Como cliente, quero tocar em “Chamar PitTec” e deixar que o aplicativo consulte os prestadores em
ordem de proximidade, para conseguir atendimento sem escolher e chamar cada técnico manualmente.

**Why this priority**: O despacho sequencial é o valor central do serviço sob demanda e reduz o
tempo e o esforço necessários para localizar alguém disposto a atender.

**Independent Test**: Pode ser testado com três PitTecs elegíveis em distâncias conhecidas,
configurando o primeiro para recusar, o segundo para não responder e o terceiro para aceitar, e
verificando a ordem e os estados mostrados ao cliente.

**Acceptance Scenarios**:

1. **Given** existem dois ou mais PitTecs elegíveis, **When** o cliente toca uma vez em “Chamar
   PitTec”, **Then** o app cria uma única solicitação, mostra um card inferior de busca e envia a
   oportunidade genérica primeiro ao prestador mais próximo, sem exigir especialidade ou descrição
   do problema.
2. **Given** o PitTec consultado recusa ou não responde dentro de 60 segundos, **When** esse
   resultado é confirmado, **Then** o cliente é informado e o próximo prestador elegível é
   consultado automaticamente, sem repetir os já recusados ou expirados.
3. **Given** um PitTec aceita a solicitação, **When** o aceite é confirmado, **Then** a busca para
   imediatamente, nenhum outro prestador pode aceitar aquela solicitação e o cliente entra no
   acompanhamento do atendimento.
4. **Given** todos os candidatos até o limite da área de cobertura recusaram, expiraram ou se
   tornaram indisponíveis, **When** não resta candidato elegível, **Then** a busca termina com uma
   explicação clara e ações para tentar novamente ou voltar.

---

### User Story 3 - Acompanhar a chegada do PitTec (Priority: P1)

Como cliente com uma chamada aceita, quero ver minha posição, a posição atual do PitTec, a
distância e o percurso entre nós, para acompanhar sua aproximação com confiança.

**Why this priority**: Após o aceite, a visibilidade do deslocamento reduz incerteza e confirma que
o prestador está a caminho do local correto.

**Independent Test**: Pode ser testado aceitando uma chamada e simulando mudanças válidas na
posição do PitTec, verificando marcadores, distância, percurso, atualização do card e tratamentos
para posição antiga ou rota indisponível.

**Acceptance Scenarios**:

1. **Given** um PitTec aceitou a chamada e ambas as posições são válidas, **When** o acompanhamento
   abre, **Then** o mapa enquadra cliente e prestador, distingue os dois marcadores e o card inferior
   informa que o PitTec está a caminho.
2. **Given** existe um percurso disponível entre o PitTec e o cliente, **When** o mapa é exibido ou
   a posição do prestador muda de forma relevante, **Then** uma linha representa o percurso e a
   distância restante é mostrada em quilômetros.
3. **Given** não é possível obter o percurso viário, mas existem duas posições válidas, **When** o
   acompanhamento é exibido, **Then** o app mostra uma ligação direta claramente identificada como
   estimativa e informa a distância aproximada, sem fingir que ela é uma rota.
4. **Given** a posição do PitTec é atualizada durante o deslocamento, **When** a atualização chega,
   **Then** seu marcador, a distância e o percurso são atualizados sem o cliente reabrir a tela.
5. **Given** a posição do PitTec está desatualizada há mais de 30 segundos, **When** o cliente vê o
   acompanhamento, **Then** a tela mantém o último ponto conhecido com aviso de desatualização e
   não o apresenta como posição em tempo real.
6. **Given** o PitTec confirma “Cheguei” e depois “Concluir atendimento”, **When** cada confirmação
   é registrada, **Then** o cliente vê o novo estado e o acompanhamento só termina após a conclusão
   ou um cancelamento válido.

---

### User Story 4 - Receber e responder a uma chamada PitTec (Priority: P1)

Como prestador cadastrado como PitTec, quero receber uma chamada próxima com as informações
necessárias e aceitar ou recusar dentro do prazo, para controlar os atendimentos que assumo.

**Why this priority**: Sem uma resposta explícita e reservada do prestador, o despacho não pode
confirmar atendimento nem avançar com segurança para o acompanhamento.

**Independent Test**: Pode ser testado enviando uma oportunidade a uma conta PitTec disponível,
confirmando a exibição dos dados essenciais e exercitando aceite, recusa e expiração.

**Acceptance Scenarios**:

1. **Given** um PitTec elegível é o candidato atual, **When** recebe a oportunidade, **Then** vê que
   se trata de uma chamada PitTec, a distância aproximada até o cliente, o prazo restante e as
   ações de aceitar e recusar.
2. **Given** o prestador aceita dentro do prazo e ainda está elegível, **When** o aceite é
   confirmado, **Then** ele fica reservado para a chamada, deixa de receber novas oportunidades e
   passa a ver o cliente e o percurso no mapa do atendimento.
3. **Given** o prestador recusa, **When** a recusa é confirmada, **Then** sua participação naquela
   busca termina e o despacho segue para o próximo candidato sem criar dois atendimentos.
4. **Given** o prazo de 60 segundos termina sem resposta confirmada, **When** o prestador tenta
   aceitar depois da expiração, **Then** o aceite tardio é rejeitado e a chamada não é retirada do
   candidato que estiver sendo consultado naquele momento.
5. **Given** o PitTec está disponível com sessão ativa e o aplicativo está em segundo plano ou com
   a tela bloqueada, **When** recebe uma oportunidade, **Then** é alertado de forma visível e sonora
   e pode abrir diretamente a chamada com o prazo restante.

---

### User Story 5 - Cadastrar empresa como PitTec (Priority: P1)

Como responsável por uma empresa técnica, quero escolher a modalidade PitTec no cadastro, além de
fixa ou móvel, para poder oferecer o novo atendimento sob demanda e aparecer no fluxo correto.

**Why this priority**: A modalidade identifica quem está apto a receber chamadas PitTec e impede
que empresas de outros modelos operacionais entrem indevidamente no despacho.

**Independent Test**: Pode ser testado cadastrando empresas em cada uma das três modalidades e
confirmando que apenas a opção PitTec, com os demais requisitos válidos, participa da descoberta e
do despacho PitTec.

**Acceptance Scenarios**:

1. **Given** uma conta empresarial está no cadastro, **When** chega à escolha de modalidade,
   **Then** vê três opções mutuamente exclusivas: fixa, móvel e PitTec, com descrição compreensível
   de cada uma.
2. **Given** a empresa escolheu PitTec e preencheu os demais dados obrigatórios, **When** conclui o
   cadastro, **Then** fica identificada como prestador PitTec sem também ser classificada como fixa
   ou móvel.
3. **Given** uma empresa PitTec quer receber chamadas, **When** oferece o serviço PitTec, autoriza
   localização e se marca disponível, **Then** passa a ser elegível para descoberta e despacho.
4. **Given** uma empresa altera sua modalidade de PitTec para fixa ou móvel, **When** a mudança é
   confirmada, **Then** deixa de participar de novas buscas PitTec, sem interromper silenciosamente
   um atendimento já aceito.

### Edge Cases

- O cliente toca repetidamente em “Chamar PitTec” enquanto a primeira ação ainda está sendo
  processada: deve existir apenas uma busca ativa e o botão deve indicar o processamento.
- Dois clientes tentam reservar o mesmo PitTec ao mesmo tempo: somente uma chamada pode obter o
  aceite; a outra deve seguir automaticamente para o próximo candidato.
- Um prestador fica indisponível, perde a permissão de localização ou passa a ter posição antiga
  enquanto aguarda sua vez: ele deve ser ignorado sem interromper toda a busca.
- A conexão cai durante a busca ou o acompanhamento: o último estado confirmado deve permanecer
  visível, com indicação de reconexão e sem avanço fictício do atendimento.
- O cliente fecha e reabre o aplicativo durante uma busca ou atendimento aceito: deve retomar a
  chamada ativa e não criar outra.
- O prestador aceita exatamente quando o cliente cancela: um único resultado definitivo deve
  prevalecer, sem deixar prestador reservado para uma chamada cancelada.
- A rota atravessa uma área sem percurso calculável: a tela deve usar a estimativa direta e
  informar sua natureza aproximada.
- Cliente e PitTec estão no mesmo ponto ou muito próximos: a distância deve ser exibida de forma
  compreensível, sem valor negativo ou percurso inválido.
- O cliente move ou amplia o mapa manualmente: atualizações de localização não devem disputar
  continuamente o controle da câmera; deve haver uma ação clara para reenquadrar os participantes.
- O serviço PitTec está temporariamente indisponível, embora outros serviços estejam ativos: o
  estado vazio deve se referir especificamente ao PitTec e não bloquear os demais serviços.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O catálogo do cliente DEVE disponibilizar um serviço chamado “PitTec”, cujo nome seja
  apresentado de modo consistente e cuja descrição deixe claro que “Tec” significa “Técnico”.
- **FR-002**: O card PitTec DEVE abrir uma experiência de mapa específica desse serviço.
- **FR-003**: O mapa inicial DEVE mostrar a posição válida do cliente e todos os prestadores PitTec
  elegíveis dentro da área de cobertura vigente, usando marcadores visualmente distintos do
  cliente e posições aproximadas que não revelem o local exato de cada prestador.
- **FR-004**: Somente empresas da modalidade PitTec, ativas, disponíveis, vinculadas ao serviço
  PitTec e com posição válida atualizada há no máximo 30 segundos DEVEM participar da descoberta e
  do despacho.
- **FR-005**: O mapa e seus controles DEVEM possuir alternativas textuais; a compreensão de
  prestador, cliente, status e ação NÃO DEVE depender apenas de cor, ícone, linha ou movimento.
- **FR-006**: A tela PitTec DEVE apresentar a ação principal “Chamar PitTec” enquanto não houver
  uma busca ou atendimento ativo para aquele cliente.
- **FR-007**: Ao acionar “Chamar PitTec”, o sistema DEVE registrar uma única busca usando a posição
  atual autorizada do cliente e DEVE apresentar imediatamente um card inferior de estado de busca.
- **FR-007A**: A chamada PitTec DEVE ser genérica e seu despacho DEVE começar imediatamente após a
  ação do cliente, sem exigir seleção de especialidade ou descrição do problema.
- **FR-008**: O despacho DEVE ordenar os candidatos elegíveis pela menor distância estimada até o
  cliente e consultar apenas um candidato por vez.
- **FR-009**: A busca DEVE começar em um raio de 10 km, ampliar para 25 km e depois para o limite de
  50 km quando não restarem candidatos na faixa atual, sem repetir candidatos já recusados,
  expirados ou invalidados naquela busca.
- **FR-010**: Cada PitTec consultado DEVE ter 60 segundos para aceitar ou recusar a oportunidade.
- **FR-011**: A recusa confirmada ou o fim do prazo sem resposta DEVE encerrar a oportunidade atual,
  informar brevemente o cliente e consultar automaticamente o próximo candidato elegível.
- **FR-012**: O primeiro aceite válido DEVE encerrar o despacho, impedir aceites posteriores e
  reservar o PitTec exclusivamente para aquele atendimento.
- **FR-013**: Um PitTec reservado DEVE deixar de aparecer como disponível e NÃO DEVE receber novas
  chamadas até o atendimento ser concluído ou cancelado.
- **FR-014**: Se nenhum candidato aceitar até o limite de 50 km, a busca DEVE terminar e apresentar
  estado vazio com opções de tentar novamente e voltar.
- **FR-015**: O cliente DEVE poder cancelar enquanto o app estiver buscando; o cancelamento DEVE
  impedir novas ofertas e invalidar qualquer resposta posterior daquela busca.
- **FR-015A**: Após o aceite e antes da conclusão, cliente e PitTec DEVEM poder cancelar somente
  após informar um motivo; todo cancelamento DEVE registrar o autor, encerrar o atendimento atual e
  liberar o prestador, sendo que o cancelamento pelo PitTec DEVE retomar automaticamente a busca do
  cliente pelos candidatos ainda elegíveis, enquanto o cancelamento pelo cliente DEVE encerrar o
  fluxo sem iniciar outra busca.
- **FR-016**: Durante a busca, o card inferior DEVE diferenciar pelo menos: iniciando, procurando,
  aguardando resposta, candidato recusou, candidato expirou, reconectando, cancelada e nenhum
  PitTec encontrado.
- **FR-017**: Toques repetidos, novas tentativas após resposta perdida e eventos concorrentes NÃO
  DEVEM duplicar buscas, oportunidades, aceites ou atendimentos.
- **FR-018**: O PitTec consultado DEVE receber somente os dados necessários para decidir a chamada,
  incluindo identificação do serviço, distância aproximada, região do atendimento e prazo; a
  posição exata do cliente DEVE ficar disponível apenas após o aceite.
- **FR-018A**: Enquanto estiver disponível e com sessão ativa, o PitTec DEVE receber um alerta
  visível e sonoro para cada nova oportunidade em primeiro plano, segundo plano e com a tela
  bloqueada; a interação com o alerta DEVE abrir diretamente a oportunidade e mostrar o prazo
  restante para resposta.
- **FR-019**: O prestador DEVE poder aceitar ou recusar explicitamente e DEVE receber confirmação
  do resultado de sua ação.
- **FR-020**: Um aceite após expiração, cancelamento, perda de elegibilidade ou reserva por outro
  atendimento DEVE ser rejeitado com explicação compreensível.
- **FR-021**: Após o aceite, os mapas do cliente e do PitTec DEVERÃO mostrar simultaneamente a
  posição do cliente e a última posição válida do prestador, inicialmente enquadrando ambos.
- **FR-022**: Quando houver percurso disponível, o mapa DEVE traçar uma linha entre a posição do
  PitTec e o cliente seguindo o percurso recomendado e DEVE mostrar a distância restante em
  quilômetros.
- **FR-023**: Quando o percurso recomendado não estiver disponível, o mapa DEVE mostrar uma ligação
  direta e a distância aproximada, identificando ambas como estimativas e sem apresentá-las como
  orientação viária.
- **FR-024**: A posição do PitTec em atendimento DEVE ter como alvo uma atualização a cada 10
  segundos; mudanças válidas DEVEM atualizar marcador, distância e percurso sem reabrir a tela.
- **FR-025**: Após 30 segundos sem posição válida do PitTec, a tela DEVE identificar o último ponto
  como desatualizado e NÃO DEVE prometer acompanhamento em tempo real até receber nova posição.
- **FR-026**: O card inferior após o aceite DEVE identificar o PitTec, informar que ele está a
  caminho e apresentar a distância restante ou explicar por que ela ainda não está disponível.
- **FR-026A**: O PitTec DEVE confirmar manualmente e em sequência as ações “Cheguei” e “Concluir
  atendimento”; o cliente DEVE ver cada estado confirmado, e a conclusão DEVE encerrar o
  acompanhamento, interromper o compartilhamento da posição do atendimento e liberar o prestador
  para novas chamadas se ele continuar elegível.
- **FR-027**: O acompanhamento DEVE preservar o ajuste manual do mapa e oferecer ação explícita para
  reenquadrar cliente, PitTec e percurso.
- **FR-028**: A chamada DEVE possuir estados persistidos e mutuamente exclusivos equivalentes a:
  buscando, aguardando resposta, aceita, a caminho, chegou, concluída e cancelada, além dos
  resultados transitórios de recusa e expiração necessários ao despacho.
- **FR-029**: Ao reabrir o aplicativo ou autenticar novamente com a mesma conta, cliente e PitTec
  DEVEM retomar a busca ou o atendimento ainda ativo no último estado confirmado.
- **FR-030**: A troca ou saída da conta DEVE remover os dados da chamada da interface e impedir que
  outra conta acesse posições ou detalhes do atendimento.
- **FR-031**: A posição exata do cliente e a posição do PitTec reservado DEVEM ser visíveis apenas
  aos participantes da chamada aceita; antes do aceite, clientes podem ver somente posições
  aproximadas dos PitTecs disponíveis no mapa do serviço, embora a ordenação do despacho DEVA usar
  a distância válida sem expor essa precisão ao cliente.
- **FR-032**: O sistema NÃO DEVE criar um histórico público de deslocamento; posições devem ser
  usadas apenas para descoberta vigente, despacho e acompanhamento do atendimento correspondente.
- **FR-033**: Falta de conexão, localização recusada ou desativada, posição imprecisa, sessão
  expirada e falha de percurso DEVEM ter estados e ações de recuperação compreensíveis, sem deixar
  a tela indefinidamente carregando.
- **FR-034**: O cadastro empresarial DEVE apresentar fixa, móvel e PitTec como três modalidades
  mutuamente exclusivas e explicar a finalidade operacional de cada opção.
- **FR-035**: Uma empresa cadastrada como PitTec DEVE continuar sujeita aos demais dados,
  validações e vínculos de serviço já obrigatórios no cadastro empresarial.
- **FR-036**: Para se tornar disponível, uma empresa PitTec DEVE possuir oferta ativa do serviço
  PitTec, autorização de localização e posição recente; sua localização DEVE continuar sendo
  atualizada enquanto estiver disponível, inclusive durante o deslocamento de uma chamada aceita.
- **FR-037**: Alterar a modalidade para fixa ou móvel DEVE retirar a empresa de novas buscas PitTec;
  uma mudança durante atendimento aceito só DEVE produzir efeito operacional após seu encerramento.
- **FR-038**: A inclusão de PitTec DEVE preservar o comportamento e a classificação atuais das
  modalidades fixa e móvel, bem como dos outros cards e serviços.
- **FR-039**: Somente clientes autenticados DEVEM iniciar ou cancelar buscas PitTec, e somente a
  conta empresarial PitTec destinatária DEVE responder à oportunidade e acessar o atendimento.
- **FR-040**: A interface DEVE funcionar nos tamanhos de tela móvel suportados, com texto ampliado,
  alvos de toque adequados e conteúdo essencial do card acessível mesmo quando o mapa não puder ser
  interpretado visualmente.

### Key Entities

- **Service**: Item de catálogo “PitTec”, com nome, descrição e situação de disponibilidade para os
  clientes e para as ofertas empresariais.
- **Provider Modality**: Classificação operacional mutuamente exclusiva da empresa: fixa, móvel ou
  PitTec; determina em quais experiências de descoberta e despacho ela pode participar.
- **PitTec Availability**: Estado que reúne empresa ativa, modalidade PitTec, oferta ativa,
  disponibilidade declarada e recência da posição para decidir se o prestador é elegível.
- **Current Location**: Última posição válida de cliente ou PitTec, com momento e qualidade
  suficientes para descoberta, ordenação e acompanhamento; não representa histórico de trajeto.
- **PitTec Search**: Jornada única iniciada pelo cliente, contendo sua posição de referência, faixa
  de busca vigente, candidatos já avaliados e estado atual.
- **Dispatch Opportunity**: Convite temporário enviado a um PitTec por vez, com início, prazo e
  resultado de aceite, recusa, expiração ou invalidação.
- **PitTec Appointment**: Atendimento confirmado pelo primeiro aceite válido, relacionando cliente,
  prestador, serviço e estados de deslocamento até conclusão ou cancelamento.
- **Route Snapshot**: Representação vigente do percurso ou estimativa entre o PitTec e o cliente,
  com distância restante, momento da atualização e indicação do tipo de cálculo apresentado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em testes com localização válida, 95% dos clientes visualizam o mapa PitTec e seu
  estado inicial em até 3 segundos após tocar no card.
- **SC-002**: Em 100% das buscas controladas, o primeiro convite é destinado ao PitTec elegível
  mais próximo e os seguintes respeitam a ordem de proximidade sem repetir candidatos descartados.
- **SC-003**: Em 100% das recusas e expirações com outro candidato elegível, a busca continua
  automaticamente sem exigir novo toque em “Chamar PitTec”.
- **SC-004**: Em 100% das buscas, no máximo um PitTec possui oportunidade válida por vez e no máximo
  um atendimento é confirmado.
- **SC-005**: Em 95% dos aceites válidos, o cliente passa do estado de busca ao mapa de
  acompanhamento em até 3 segundos após a confirmação.
- **SC-006**: Em 95% das atualizações válidas durante um atendimento, cliente e PitTec veem a nova
  posição, distância e percurso aplicáveis em até 15 segundos.
- **SC-007**: Em 100% das telas com percurso disponível, ambos os participantes, a linha e a
  distância em quilômetros podem ser identificados sem mover o mapa inicialmente.
- **SC-008**: Em 100% das falhas de rota, localização, conexão ou disponibilidade testadas, o
  usuário recebe uma explicação e uma ação possível, sem permanecer em busca infinita.
- **SC-009**: Pelo menos 90% dos participantes em teste de usabilidade conseguem iniciar uma
  chamada e identificar seu estado atual na primeira tentativa, sem orientação externa.
- **SC-010**: Em 100% dos cadastros testados, a modalidade salva é exatamente uma entre fixa,
  móvel e PitTec; somente PitTec elegível aparece na descoberta e recebe chamadas do novo serviço.
- **SC-011**: Em 100% das retomadas testadas após fechamento ou perda temporária de conexão, a mesma
  conta retorna ao último estado confirmado sem criar uma solicitação duplicada.
- **SC-012**: Nenhuma conta não participante acessa a posição exata do cliente ou os detalhes de
  acompanhamento de uma chamada aceita nos cenários de autorização testados.
- **SC-013**: Em 100% dos atendimentos testados, somente o PitTec pode confirmar “Cheguei” e
  “Concluir atendimento”, nessa ordem, e cada confirmação fica visível ao cliente sem encerrar o
  fluxo prematuramente.

## Assumptions

- PitTec é um único serviço inicial do catálogo para atendimento técnico geral no local; a
  definição de especialidades, diagnóstico prévio e subtipos técnicos não faz parte desta entrega.
- A modalidade PitTec representa um prestador que se desloca até o cliente e é distinta das
  modalidades empresariais fixa e móvel já existentes.
- O despacho PitTec adota as regras já estabelecidas no produto para atendimento sob demanda:
  resposta em 60 segundos, faixas de 10 km, 25 km e 50 km, uma oferta por vez e reserva no aceite.
- O cliente fornece uma posição pontual válida ao iniciar a busca; não é necessário publicar seu
  deslocamento continuamente para descobrir prestadores.
- PitTecs disponíveis mantêm sua posição recente enquanto participam da descoberta e durante uma
  chamada aceita, com indicação compreensível desse uso ao prestador.
- “Distância em KM” significa prioritariamente a distância restante do percurso recomendado; a
  distância direta é usada somente como estimativa identificada quando o percurso não puder ser
  obtido.
- O acompanhamento desta entrega termina na conclusão confirmada pelo PitTec ou no cancelamento
  válido de um participante; pagamento, avaliação e suporte pós-serviço permanecem fluxos
  separados.
- Regras cadastrais existentes de identidade empresarial, endereço, oferta e validação continuam
  válidas salvo a inclusão explícita da terceira modalidade.

## Out of Scope

- Pagamento, cobrança, orçamento, negociação de preço, reembolso ou repasse ao PitTec.
- Escolha manual de um PitTec específico pelo cliente; o botão usa despacho automático por
  proximidade.
- Envio simultâneo da mesma chamada a vários prestadores.
- Navegação curva a curva, instruções de direção por voz ou promessa de horário exato de chegada.
- Chat, ligação telefônica, compartilhamento público de contato ou histórico de localização.
- Cadastro de especialidades técnicas, peças, ferramentas, veículo do prestador ou múltiplos
  profissionais vinculados à mesma empresa.
- Mudança das regras funcionais dos serviços existentes ou das empresas classificadas como fixa ou
  móvel, além de preservar sua compatibilidade com a nova terceira opção.
