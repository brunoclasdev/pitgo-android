# Feature Specification: Melhoria de UX das telas do cliente

**Feature Branch**: `014-client-ux-improvements`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "feature: melhorar a UX da view do cliente e usuario."

## Clarifications

### Session 2026-08-23

- Q: De onde deve vir a posição do cliente exibida centralizada no mapa dele e como destino no
  mapa do prestador? → A: Pontual — uma leitura de GPS ao abrir (e reabrir) a tela de
  acompanhamento, sem serviço de rastreamento em segundo plano no dispositivo do cliente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Feedback claro durante a solicitação de serviço (Priority: P1)

Um cliente que acabou de pedir um guincho (ou outro serviço sob demanda) precisa entender, a
qualquer momento, em que estágio o pedido está, quanto tempo falta para a resposta do prestador,
e como cancelar se mudar de ideia — sem telas com números travados, rótulos ambíguos ou botões
desabilitados sem explicação.

**Why this priority**: Esta é a etapa de maior ansiedade do fluxo (o cliente já tem um problema
real com o carro e está esperando ajuda). Confusão aqui gera desistência, pedidos duplicados ou
contato com suporte, e é o ponto de maior atrito hoje: o texto de contagem regressiva pode
parecer parado, a distância exibida pode aparecer como "0,0 km" sem explicação, e não há indicação
textual de por que um botão está temporariamente desabilitado.

**Independent Test**: Pode ser testado isoladamente solicitando um serviço e observando cada
estado (buscando, proposta encontrada, aguardando prestador, aceito, recusado/expirado, sem
prestador disponível) sem alterar nenhuma outra tela do app.

**Acceptance Scenarios**:

1. **Given** o cliente está aguardando a resposta do prestador, **When** o tempo restante muda,
   **Then** o valor exibido na tela é atualizado visivelmente a cada segundo até chegar a zero.
2. **Given** uma ação (solicitar, próximo, cancelar) está sendo processada, **When** o botão fica
   desabilitado, **Then** a tela mostra um indicador de carregamento ou texto explicando que a
   ação está em andamento, nunca um botão inerte sem explicação.
3. **Given** a distância até o prestador não pôde ser calculada com precisão, **When** a proposta é
   exibida, **Then** o app mostra uma indicação compreensível (ex.: "a caminho", "próximo de você")
   em vez de um valor numérico que pareça um placeholder (ex.: "0,0 km").
4. **Given** nenhum prestador foi encontrado após esgotar o raio de busca, **When** a busca
   termina, **Then** o cliente vê uma explicação clara do que aconteceu e uma ação óbvia para
   tentar novamente ou escolher outro serviço.

---

### User Story 2 - Cliente acompanha o guincho se movimentando no mapa (Priority: P1)

Um cliente com atendimento aceito precisa ver, na tela de acompanhamento, a própria posição
centralizada no mapa e o guincho se movendo em tempo real até chegar, em vez de um mapa que só
segue o prestador e não mostra onde o próprio cliente está.

**Why this priority**: Saber "onde eu estou" em relação a "onde o guincho está e se está se
aproximando" é o valor central de um atendimento sob demanda; hoje a câmera do mapa de
acompanhamento centraliza apenas a posição do prestador (`AppointmentTrackingViewModel.moveCameraTo`
usa somente a coordenada do guincho), então o cliente não tem uma referência visual da própria
posição nem confirmação de que o guincho está realmente se aproximando dele.

**Independent Test**: Pode ser testado isoladamente aceitando um atendimento com um prestador
`MOBILE` publicando localização e observando, na tela de acompanhamento do cliente, se a própria
posição aparece centralizada e se o marcador do guincho se desloca conforme a posição é
atualizada, sem depender de nenhuma outra história desta spec.

**Acceptance Scenarios**:

1. **Given** o cliente tem um atendimento aceito, **When** ele abre a tela de acompanhamento,
   **Then** o mapa mostra sua própria posição centralizada e um marcador distinto para o guincho.
2. **Given** a posição do guincho muda no backend, **When** a atualização chega, **Then** o
   marcador do guincho se desloca para a nova posição no mapa do cliente em poucos segundos, sem
   recarregar a tela.
3. **Given** a localização do guincho ainda não chegou, **When** a tela abre, **Then** o cliente vê
   uma mensagem explicando que está aguardando a localização, com sua própria posição já visível
   no mapa.

---

### User Story 3 - Guincho vê o cliente e a si mesmo no mapa em tempo real (Priority: P1)

Um prestador (guincho) com atendimento aceito precisa ver, na própria tela de atendimento, um
mapa com sua posição e a posição do cliente, os dois enquadrados desde a abertura da tela, e
atualizando em tempo real conforme ele se desloca até o cliente.

**Why this priority**: Hoje a tela de solicitação/atendimento do prestador
(`ProviderIncomingRequestScreen`) não tem nenhum mapa; o guincho aceita o atendimento sem
nenhuma referência visual de para onde ir, dependendo apenas de texto (nome/localidade), o que é
insuficiente para um serviço de deslocamento até o cliente.

**Independent Test**: Pode ser testado isoladamente aceitando um atendimento do lado do
prestador e verificando se a tela de atendimento passa a exibir um mapa com os dois marcadores
(guincho e cliente) enquadrados e atualizando com o deslocamento do prestador, sem depender de
nenhuma outra história desta spec.

**Acceptance Scenarios**:

1. **Given** o guincho aceitou um atendimento, **When** ele abre a tela do atendimento, **Then**
   vê um mapa com sua posição e a posição do cliente, ambas visíveis na visão inicial (sem precisar
   mover o mapa manualmente para encontrar uma das duas).
2. **Given** o guincho está se deslocando, **When** sua posição é atualizada, **Then** o marcador
   do guincho se move no mapa em tempo real, mantendo o marcador do cliente visível.
3. **Given** o prestador arrasta ou dá zoom no mapa manualmente, **When** uma nova posição chega,
   **Then** o app não força a câmera de volta ao enquadramento automático sem uma ação explícita do
   prestador para retomar o acompanhamento.

---

### User Story 4 - Voltar sem travar entre as telas do atendimento (Priority: P1)

Um cliente ou prestador com um atendimento em andamento precisa conseguir tocar em voltar e
efetivamente sair da tela atual (por exemplo, para a Home ou para a Dashboard), em vez de ser
redirecionado automaticamente de volta para a mesma tela.

**Why this priority**: É um bloqueio de navegação, não apenas um ajuste estético: hoje, sempre que
existe um atendimento ativo, o app redireciona automaticamente para a tela desse atendimento
sempre que o usuário chega a uma tela fora desse fluxo — inclusive logo após um "voltar" manual —
dando a impressão de que o botão de voltar está travado.

**Independent Test**: Pode ser testado isoladamente tendo um atendimento ativo (aceito ou em
andamento) e tocando em voltar repetidamente a partir da tela de acompanhamento/atendimento, tanto
como cliente quanto como prestador, confirmando que o app permanece na tela anterior.

**Acceptance Scenarios**:

1. **Given** o cliente está na tela de acompanhamento de um atendimento aceito, **When** ele toca
   em voltar, **Then** ele retorna à tela anterior (ex.: Home) e permanece lá.
2. **Given** o guincho está na tela de um atendimento aceito, **When** ele toca em voltar,
   **Then** ele retorna à Dashboard e permanece lá, podendo reabrir o atendimento manualmente
   depois se quiser.
3. **Given** o app é reaberto do zero (cold start) com um atendimento ainda ativo, **When** a
   sessão termina de carregar, **Then** o app leva automaticamente à tela desse atendimento — a
   retomada automática só deve ocorrer nesse tipo de situação (início de sessão ou novo evento
   relevante), nunca como reação a uma navegação manual de voltar.

---

### User Story 5 - Início (Home) acionável (Priority: P2)

Um cliente que abre o app precisa ver, de forma imediata e acionável, o que falta preencher no
cadastro (endereço, veículo) e o que pode fazer agora, sem informações estáticas que parecem
editáveis mas não são.

**Why this priority**: A Home é a primeira tela após o login e a porta de entrada para solicitar
qualquer serviço; hoje ela mostra "Endereço não cadastrado" como texto informativo sem nenhuma
ação associada, o que obriga o cliente a procurar em outro lugar do app para corrigir isso.

**Independent Test**: Pode ser testado isoladamente logando com uma conta cliente sem endereço
cadastrado e verificando se a Home oferece um caminho direto para cadastrá-lo, sem depender de
nenhuma mudança nas telas de solicitação de serviço.

**Acceptance Scenarios**:

1. **Given** o cliente não tem endereço cadastrado, **When** ele toca no aviso de endereço na
   Home, **Then** o app abre diretamente o formulário de cadastro de endereço.
2. **Given** o cliente já tem endereço cadastrado, **When** ele toca no mesmo local, **Then** o
   app permite ver ou editar o endereço atual.
3. **Given** o cliente não tem nenhum veículo cadastrado, **When** ele abre a Home, **Then** a
   tela deixa claro que um veículo é necessário para solicitar serviços e oferece a ação de
   cadastrar um, no lugar do card de veículo.

---

### User Story 6 - Perfil completo e autoexplicativo (Priority: P3)

Um cliente que abre a aba Perfil precisa conseguir gerenciar as informações e preferências
básicas da própria conta (endereço, veículo padrão, aparência do app) sem precisar sair da aba
Perfil para outra parte do app nem contatar o suporte para tarefas simples.

**Why this priority**: O Perfil hoje mostra apenas nome, e-mail e o botão de sair; é a tela mais
incompleta do fluxo do cliente e não cumpre o papel esperado de uma tela de conta (gerenciar
dados e preferências), mas o impacto é menor que os dois fluxos acima porque não bloqueia uma
tarefa em andamento.

**Independent Test**: Pode ser testado isoladamente abrindo a aba Perfil e confirmando que
endereço, veículo padrão e tema (Claro/Escuro/Sistema) podem ser vistos e alterados a partir dali,
sem depender das mudanças das outras histórias.

**Acceptance Scenarios**:

1. **Given** o cliente está na aba Perfil, **When** a tela carrega, **Then** ele vê o endereço
   cadastrado (ou aviso de ausência) e o veículo padrão (ou aviso de ausência), cada um com uma
   ação direta para cadastrar/editar.
2. **Given** o cliente está na aba Perfil, **When** ele escolhe Claro, Escuro ou Padrão do
   sistema, **Then** a preferência é aplicada imediatamente e mantida nas próximas aberturas do
   app.
3. **Given** o cliente quer encerrar a sessão, **When** ele confirma a saída, **Then** o
   comportamento atual de logout é preservado sem regressão.

---

### Edge Cases

- O que acontece quando a conexão cai durante a espera pela resposta do prestador? A tela DEVE
  distinguir esse caso ("sem conexão, tentando reconectar") de "aguardando resposta" e de "nenhum
  prestador encontrado", sem travar o botão indefinidamente.
- Como o app se comporta quando o prestador recusa ou o prazo de resposta expira? O cliente DEVE
  ser informado do motivo (recusado/expirado) antes de ser levado de volta à busca por outro
  prestador, e não apenas ver a tela "resetar" silenciosamente.
- O que acontece quando o cliente muda o tema enquanto uma tela com mapa está aberta? O mapa e o
  restante da tela DEVEM refletir o novo tema sem exigir reinício do app.
- Como a Home se comporta quando o cliente tem veículo mas não tem endereço, ou vice-versa? Cada
  aviso DEVE ser independente e não esconder o outro.
- O que acontece se o cliente tocar novamente numa ação (solicitar, cancelar, salvar endereço) que
  já está em andamento? O app NÃO DEVE disparar uma segunda chamada nem exibir estados
  inconsistentes.
- O que acontece quando a localização do guincho fica desatualizada (prestador ficou offline ou
  perdeu sinal)? O mapa DEVE indicar que a posição pode estar desatualizada em vez de mover o
  marcador silenciosamente para uma posição antiga sem aviso.
- O que acontece se o cliente ou o guincho tocarem em voltar diversas vezes seguidas enquanto uma
  nova atualização de status chega (ex.: o outro lado aceitou/cancelou nesse exato momento)? O app
  NÃO DEVE entrar em loop de navegação nem exibir duas telas conflitantes.
- Como o mapa do guincho se comporta se o cliente não tiver uma localização válida ainda (ex.:
  atendimento aceito antes de qualquer posição do cliente ser conhecida)? A tela DEVE indicar isso
  textualmente em vez de mostrar um mapa vazio ou um marcador na origem (0,0).
- O que acontece quando o cliente sai da tela de acompanhamento (app em segundo plano ou troca de
  tela) e volta depois? Como a posição do cliente é apenas pontual (sem rastreamento em segundo
  plano), o app DEVE capturar uma nova leitura ao reabrir a tela em vez de mostrar uma posição
  antiga sem indicar que pode estar desatualizada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A tela de acompanhamento de solicitação de serviço DEVE atualizar visivelmente o
  tempo restante de resposta do prestador a cada segundo enquanto a solicitação estiver pendente.
- **FR-002**: O sistema DEVE exibir um indicador de carregamento ou mensagem de "processando"
  sempre que um botão de ação (solicitar, próximo, cancelar, aceitar, recusar) estiver
  temporariamente desabilitado por uma ação em andamento.
- **FR-003**: O sistema NÃO DEVE exibir valores de distância que pareçam dados ausentes ou de
  teste (ex.: "0,0 km"); quando a distância não puder ser expressa com confiança, o app DEVE usar
  uma indicação textual compreensível no lugar do número.
- **FR-004**: Ao esgotar a busca por prestadores sem encontrar nenhum disponível, o sistema DEVE
  apresentar uma mensagem explicando o que aconteceu e uma ação clara para tentar novamente ou
  escolher outro serviço.
- **FR-005**: A Home DEVE tornar o aviso de "endereço não cadastrado" uma ação tocável que leva
  diretamente ao cadastro de endereço; quando o endereço já existir, o mesmo elemento DEVE
  permitir visualizá-lo/editá-lo.
- **FR-006**: A Home DEVE oferecer uma ação direta de cadastro de veículo quando o cliente não
  tiver nenhum veículo, no lugar do card que hoje pressupõe um veículo já selecionado.
- **FR-007**: A aba Perfil DEVE exibir e permitir editar o endereço cadastrado e o veículo padrão
  do cliente sem sair da aba Perfil.
- **FR-008**: A aba Perfil DEVE oferecer a seleção de aparência (Claro, Escuro, Padrão do
  sistema), aplicando a escolha imediatamente e persistindo-a entre sessões.
- **FR-009**: Toda transição de estado nos fluxos do cliente (busca de serviço, cadastro de
  endereço/veículo, alteração de perfil) DEVE preservar a distinção entre carregando, sucesso,
  erro e vazio, com mensagens compreensíveis e sem detalhes técnicos.
- **FR-010**: As mudanças de UX desta feature NÃO DEVEM alterar o comportamento funcional já
  existente de autenticação, logout, cadastro de veículo/endereço e do fluxo de despacho de
  serviço — apenas a clareza, feedback e capacidade de ação nas telas do cliente.
- **FR-011**: A tela de acompanhamento do cliente DEVE exibir a posição atual do próprio cliente
  centralizada no mapa, além do marcador do guincho, distinguindo visualmente os dois.
- **FR-012**: A posição do cliente usada nos mapas (do próprio cliente e do prestador) DEVE ser
  obtida por uma leitura pontual do GPS ao abrir e ao reabrir a tela de acompanhamento; o sistema
  NÃO DEVE exigir nem manter um serviço de rastreamento contínuo em segundo plano no dispositivo do
  cliente para esse fim.
- **FR-013**: O marcador do guincho no mapa do cliente DEVE se atualizar em tempo real conforme
  novas posições chegam, sem exigir que o cliente saia e reabra a tela.
- **FR-014**: A tela de atendimento do prestador (guincho) DEVE exibir um mapa mostrando sua
  própria posição e a posição do cliente, enquadrando as duas na visão inicial da tela.
- **FR-015**: O marcador do guincho no mapa do prestador DEVE se atualizar em tempo real conforme
  o prestador se desloca, sem exigir recarregar a tela.
- **FR-016**: Ao ajustar manualmente o mapa (arrastar/zoom), o app NÃO DEVE forçar a câmera de
  volta ao enquadramento automático sem uma ação explícita da pessoa para retomar o
  acompanhamento.
- **FR-017**: A ação de voltar em qualquer tela do fluxo de despacho (busca, solicitação recebida,
  acompanhamento) DEVE retornar efetivamente à tela anterior e manter a pessoa nela.
- **FR-018**: A retomada automática de navegação para a tela de um atendimento ativo DEVE ocorrer
  apenas ao iniciar/retomar a sessão do app ou ao chegar um novo evento relevante (nova
  solicitação, mudança de status), nunca como reação a uma navegação manual de voltar.

### Key Entities

- **Endereço do cliente**: dado que esta feature passa a persistir pela primeira vez (hoje a Home
  só mostra um texto estático, sem nenhum cadastro real por trás); expõe um caminho de
  cadastro/edição a partir da Home e do Perfil.
- **Veículo do cliente**: dado já existente (veículo padrão/selecionado); esta feature passa a
  oferecer atalhos de cadastro/edição a partir da Home e do Perfil.
- **Preferência de aparência**: preferência já prevista pela constituição do produto (Claro,
  Escuro, Sistema); esta feature adiciona a superfície de UI em Perfil para o cliente controlá-la.
- **Localização corrente do guincho**: posição em tempo real do prestador durante um atendimento
  aceito, já publicada pela infraestrutura existente; esta feature passa a exibi-la centralizada
  e atualizada em tempo real nos mapas do cliente e do prestador.
- **Localização corrente do cliente**: leitura pontual do GPS do dispositivo do cliente, obtida ao
  abrir/reabrir a tela de acompanhamento (sem rastreamento contínuo em segundo plano); esta feature
  passa a exibi-la no mapa do cliente (centralizada) e no mapa do prestador (como destino).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ao aguardar resposta de um prestador, o cliente vê o tempo restante mudar a cada
  segundo em 100% das vezes, sem valores travados.
- **SC-002**: Em pelo menos 95% das transições de estado do fluxo de solicitação de serviço
  (buscando, proposta, aguardando, aceito, recusado, expirado, esgotado), o cliente consegue
  descrever corretamente o que está acontecendo sem precisar perguntar ao suporte.
- **SC-003**: O cliente consegue cadastrar ou editar endereço a partir da Home ou do Perfil em no
  máximo 2 toques a partir da tela inicial.
- **SC-004**: O cliente consegue trocar o tema do app (Claro/Escuro/Sistema) em no máximo 2 toques
  a partir da aba Perfil, com a mudança refletida imediatamente em toda a tela.
- **SC-005**: Tickets de suporte relacionados a "não sei o que está acontecendo com meu pedido" ou
  "não consigo editar meus dados" caem em relação ao período anterior à mudança.
- **SC-006**: Durante um atendimento aceito, a posição do guincho exibida no mapa (do cliente e do
  prestador) reflete a posição real com no máximo alguns segundos de atraso, sem ação manual da
  pessoa para atualizar.
- **SC-007**: Em 100% das vezes em que o cliente ou o prestador tocam em voltar durante um
  atendimento ativo, eles permanecem na tela anterior, sem retorno automático indesejado.
- **SC-008**: Em 100% dos atendimentos aceitos, o prestador consegue visualizar, na mesma tela, sua
  própria posição e a posição do cliente.

## Assumptions

- "Cliente" e "usuário" no pedido se referem ao mesmo tipo de conta: o consumidor final que
  solicita serviços (perfil `CLIENT`), em oposição à conta de empresa/prestador (`PROVIDER`), que
  já tem sua própria dashboard e não faz parte deste escopo.
- O escopo cobre as telas hoje voltadas ao cliente: Início (Home), Garagem/veículos, solicitação e
  acompanhamento de serviço (despacho de guincho e afins) e Perfil. Do lado do prestador, o escopo
  se limita à tela de atendimento ativo (mapa e navegação durante uma solicitação aceita — User
  Stories 3 e 4); a Dashboard geral, o cadastro/migração de prestador e demais telas exclusivas de
  empresa permanecem fora do escopo. Telas de autenticação (Login/Cadastro) também permanecem fora
  do escopo, pois o pedido é sobre a experiência de quem já está usando o atendimento (cliente e
  prestador em campo), não sobre onboarding ou administração da conta de empresa.
- Nenhuma mudança de identidade visual (paleta, tipografia) é esperada; a melhoria é de clareza,
  feedback e capacidade de ação dentro do design system e dos princípios já definidos na
  constituição do produto (hierarquia clara, feedback imediato, menor número de interações).
- A funcionalidade de trocar tema (Claro/Escuro/Sistema) já é um requisito da constituição do
  produto; esta feature apenas garante que ela fique acessível e visível a partir do Perfil do
  cliente, caso ainda não esteja.
- Histórico de atendimentos já existe como aba própria ("Atendimentos") na navegação do cliente e
  não precisa ser recriado dentro do Perfil.
- A infraestrutura de rastreamento em tempo real do prestador (publicação contínua de posição,
  canal privado por atendimento) já existe; esta feature foca em exibir essa informação de forma
  clara nos mapas do cliente e do prestador. Do lado do cliente, nenhuma infraestrutura nova de
  rastreamento contínuo é criada: a posição do cliente é uma leitura pontual de GPS ao abrir/reabrir
  a tela de acompanhamento, reaproveitando a captura de localização pontual já usada em outras
  telas do app.
- "Guincho" nas histórias de mapa se refere à mesma conta de prestador (`PROVIDER`) já usada no
  restante deste documento; a tela do guincho é a tela do atendimento ativo do prestador (após
  aceitar), não o Dashboard geral.
- Enquadrar "cliente e guincho" no mapa do prestador significa ajustar a visão inicial para que os
  dois pontos fiquem visíveis simultaneamente; a pessoa pode mover o mapa manualmente depois sem
  que o app force o enquadramento automático de volta.
- O comportamento de retomada automática de navegação (levar a pessoa de volta à tela de um
  atendimento ativo) deve continuar existindo para os casos de início/retomada de sessão; esta
  feature apenas corrige quando esse redirecionamento não deve ocorrer (reação a um "voltar"
  manual).
