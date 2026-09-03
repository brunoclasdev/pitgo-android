# Feature Specification: Seleção de serviços condicionada à modalidade do prestador

**Feature Branch**: `016-conditional-provider-services`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Ao cadastrar um provider, vamos seguir as seguites regras: Somente ao
cadastrar um provider fixo é que sera obrigatorio escolher um serviço. Ex.: Se é uma borracharia,
vendas e concertos de pneu e rodas. Ao salvar um provider guincho o guincho já é um serviço
explicito, não tem necessidade de escolher serviços. Ao Salvar um pittec, sera um provider on
demand. Então ele não tem um serviço fixo, a tarefa dele é ir no local solicitado e fazer a
analise."

## Clarifications

### Session 2026-09-02

- Q: Quem define o valor fixo do PitTec — cada prestador escolhe o próprio valor fixo no cadastro
  (como o Guincho), ou existe um único valor fixo definido pela plataforma para todos os
  prestadores PitTec? → A: Existe um único valor fixo definido pela plataforma/catálogo, igual
  para todos os PitTecs, sem etapa de cadastro para esse valor (Opção B).
- Q: A taxa variável por distância também é única, definida pela plataforma, ou cada prestador
  pode ter sua própria taxa por km? → A: Existe uma única taxa por km definida pela
  plataforma/catálogo, igual para todos os PitTecs (Opção B).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Prestador Fixo escolhe seus serviços (Priority: P1)

Uma borracharia se cadastra como prestador Fixo. Durante o cadastro, ela precisa escolher, no
catálogo, quais serviços presta (por exemplo, venda e conserto de pneus e rodas), informando preço
e duração de cada um, exatamente como já acontece hoje.

**Why this priority**: É o caminho mais comum de cadastro hoje e não pode regredir; qualquer
prestador com endereço fixo genuinamente oferece um conjunto específico de especialidades que o
cliente precisa poder comparar no catálogo.

**Independent Test**: Cadastrar um prestador escolhendo a modalidade Fixo e verificar que o envio
só é permitido depois de selecionar pelo menos um serviço com preço e duração válidos; nenhum
serviço é atribuído automaticamente.

**Acceptance Scenarios**:

1. **Given** um cadastro em andamento com modalidade Fixo selecionada, **When** o prestador tenta
   enviar o cadastro sem escolher nenhum serviço, **Then** o sistema impede o envio e indica que
   pelo menos um serviço é obrigatório.
2. **Given** um cadastro com modalidade Fixo, **When** o prestador escolhe uma categoria, um
   serviço dessa categoria e informa preço e duração, **Then** o cadastro é aceito com esse serviço
   associado ao prestador.
3. **Given** um cadastro com modalidade Fixo, **When** o prestador adiciona mais de um serviço,
   **Then** todos os serviços válidos são salvos como ofertas do prestador.

---

### User Story 2 - Prestador Guincho não escolhe serviço (Priority: P1)

Um prestador de guincho se cadastra como prestador Móvel. Como "Guincho" já é, por definição, o
único serviço que esse tipo de prestador oferece, o cadastro não deve pedir para ele escolher
categoria ou serviço no catálogo — apenas os dados da empresa, endereço e verificação de
localização, sem qualquer passo de seleção de serviço.

**Why this priority**: Elimina uma etapa sem sentido de negócio (escolher entre serviços quando só
existe um) e evita erro de configuração em que um guincho fica associado ao serviço errado.

**Independent Test**: Cadastrar um prestador escolhendo a modalidade Móvel e verificar que a tela
não exibe nenhuma seleção de categoria/serviço e que o cadastro é aceito normalmente; ao final, o
prestador aparece no catálogo do serviço "Guincho".

**Acceptance Scenarios**:

1. **Given** um cadastro com modalidade Móvel selecionada, **When** o prestador chega à etapa em
   que antes existia a escolha de serviços, **Then** nenhuma escolha de categoria/serviço é
   solicitada.
2. **Given** um cadastro com modalidade Móvel preenchido corretamente (empresa, endereço,
   verificação de localização), **When** o prestador envia o cadastro, **Then** o cadastro é aceito
   sem exigir nenhuma ação relacionada a serviços, e o prestador passa a oferecer "Guincho".
3. **Given** um prestador Móvel recém-cadastrado, **When** um cliente abre a Home, **Then** esse
   prestador é contabilizado no card de Guincho, sem que ele tenha escolhido esse serviço
   manualmente.

---

### User Story 3 - Prestador PitTec não escolhe serviço nem preço (Priority: P1)

Um técnico autônomo se cadastra como PitTec (sob demanda). Como o PitTec não tem uma especialidade
fixa — sua função é se deslocar até o local solicitado e fazer uma análise — o cadastro não deve
pedir escolha de categoria/serviço nem cobrar preço ou duração; o cadastro pede apenas empresa,
endereço e verificação de localização.

**Why this priority**: É a mesma simplificação da US2, mas para a modalidade sob demanda mais nova
do produto, cujo modelo de atendimento já não expõe preço ao cliente em nenhuma etapa da solicitação
automática.

**Independent Test**: Cadastrar um prestador escolhendo a modalidade PitTec e verificar que nenhuma
tela de seleção de serviço, preço ou duração é exibida, e que o cadastro é aceito normalmente,
tornando o prestador elegível a receber chamados PitTec.

**Acceptance Scenarios**:

1. **Given** um cadastro com modalidade PitTec selecionada, **When** o prestador chega à etapa em
   que antes existia a escolha de serviços, **Then** nenhuma escolha de categoria/serviço, preço ou
   duração é solicitada.
2. **Given** um cadastro com modalidade PitTec preenchido corretamente (empresa, endereço,
   verificação de localização), **When** o prestador envia o cadastro, **Then** o cadastro é aceito
   sem nenhuma etapa relacionada a serviço, preço ou duração.
3. **Given** um prestador PitTec recém-cadastrado com localização e disponibilidade válidas,
   **When** um cliente abre o card PitTec, **Then** esse prestador passa a ser elegível para
   aparecer no mapa de descoberta, sem que tenha definido preço algum.

---

### Edge Cases

- O que acontece se o prestador mudar a modalidade durante o preenchimento do cadastro (por
  exemplo, de Fixo para Móvel) depois de já ter escolhido serviços? As escolhas de serviço feitas
  até então deixam de valer e não são enviadas quando a modalidade final é Móvel ou PitTec.
- O que acontece se o catálogo não tiver, no momento do cadastro, um serviço "Guincho" ou "PitTec"
  ativo e único para atribuir automaticamente? O cadastro deve falhar de forma clara, sem criar um
  prestador sem serviço associado.
- O que acontece se o prestador tentar voltar de Móvel/PitTec para Fixo depois de já não ter
  escolhido nenhum serviço? Ao selecionar Fixo, a etapa de escolha de serviço volta a aparecer e a
  ficar obrigatória antes de permitir o envio.
- Esta regra vale apenas para o cadastro inicial do prestador; a troca de modalidade feita depois do
  cadastro, no painel do prestador, não faz parte desta funcionalidade e continua fora de escopo.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE exigir que o prestador escolha pelo menos um serviço do catálogo,
  com preço e duração válidos, quando a modalidade escolhida no cadastro for Fixo.
- **FR-002**: O sistema NÃO DEVE exibir nenhuma etapa de escolha de categoria ou serviço no
  cadastro quando a modalidade escolhida for Móvel (Guincho).
- **FR-003**: O sistema DEVE associar automaticamente o serviço "Guincho" ao prestador cadastrado
  como Móvel, sem exigir nenhuma seleção manual de categoria/serviço.
- **FR-004**: O sistema DEVE continuar exigindo preço e duração para o serviço "Guincho"
  atribuído automaticamente, já que se trata de um atendimento cobrado, preservando o
  comportamento atual de precificação por prestador.
- **FR-005**: O sistema NÃO DEVE exibir nenhuma etapa de escolha de categoria, serviço, preço ou
  duração no cadastro quando a modalidade escolhida for PitTec.
- **FR-006**: O sistema DEVE associar automaticamente o serviço "PitTec" ao prestador cadastrado
  como PitTec, sem exigir preço, duração ou qualquer seleção manual de categoria/serviço.
- **FR-007**: O sistema NÃO DEVE permitir que um prestador Móvel ou PitTec associe, durante o
  cadastro, qualquer serviço além do único serviço implícito da sua modalidade.
- **FR-008**: O sistema DEVE impedir o envio do cadastro caso a modalidade seja Fixo e nenhum
  serviço válido tenha sido escolhido, mantendo a mensagem de erro já existente para esse caso.
- **FR-009**: O sistema DEVE impedir a criação de um prestador Móvel ou PitTec caso o serviço
  implícito correspondente ("Guincho" ou "PitTec") não exista, esteja inativo ou esteja duplicado
  no catálogo no momento do cadastro, informando uma falha compreensível em vez de concluir o
  cadastro sem serviço associado.
- **FR-010**: O sistema DEVE descartar qualquer seleção manual de categoria/serviço feita antes de
  o prestador trocar a modalidade para Móvel ou PitTec, e DEVE voltar a exigir a escolha de
  serviço caso a modalidade seja trocada de volta para Fixo antes do envio.

### Key Entities *(include if feature involves data)*

- **Prestador (Provider)**: empresa ou profissional cadastrado, com uma modalidade (Fixo, Móvel ou
  PitTec) que determina se a escolha de serviço é obrigatória, automática ou inexistente.
- **Oferta de Serviço (Service Offer)**: associação entre um prestador e um serviço do catálogo,
  com preço e duração quando aplicável; para Móvel e PitTec, essa associação passa a ser criada
  automaticamente em vez de escolhida pelo prestador.
- **Serviço de Catálogo (Catalog Service)**: item do catálogo que o cliente pode solicitar;
  "Guincho" e "PitTec" são serviços especiais que representam toda a modalidade do prestador, e não
  uma especialidade entre outras.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% dos cadastros concluídos com modalidade Fixo possuem pelo menos um serviço
  escolhido manualmente pelo prestador.
- **SC-002**: 100% dos cadastros concluídos com modalidade Móvel resultam em exatamente um serviço
  associado ("Guincho"), sem que o prestador tenha realizado nenhuma seleção de categoria/serviço.
- **SC-003**: 100% dos cadastros concluídos com modalidade PitTec resultam em exatamente um
  serviço associado ("PitTec"), sem que o prestador tenha definido ou selecionado qualquer valor de
  preço, duração, categoria ou serviço (o valor fixo é único e definido pela plataforma, não pelo
  prestador).
- **SC-004**: O tempo para concluir o cadastro de um prestador Móvel ou PitTec cai em relação ao
  fluxo atual, por eliminar por completo a etapa de escolha de serviço.
- **SC-005**: Nenhum prestador Móvel ou PitTec é criado com um serviço diferente do serviço
  implícito da sua modalidade.

## Assumptions

- Esta regra se aplica ao cadastro inicial do prestador; prestadores já cadastrados antes desta
  mudança não são alterados retroativamente, e a troca de modalidade feita depois do cadastro (no
  painel do prestador) está fora do escopo desta funcionalidade.
- Os serviços "Guincho" (modalidade Móvel) e "PitTec" (modalidade PitTec) já existem, ativos e
  únicos no catálogo, e continuam sendo mantidos por quem administra o catálogo, não pelo
  prestador.
- O PitTec tem um modelo de cobrança próprio: um valor fixo somado a um valor variável conforme a
  distância até o cliente. Tanto o valor fixo quanto a taxa por distância são únicos e definidos
  centralmente pela plataforma/catálogo (ver Clarifications), não por cada prestador — por isso o
  cadastro do prestador PitTec continua sem nenhuma etapa de preço ou duração, igual a qualquer
  outro dado que hoje já é mantido pelo catálogo e não pelo prestador.
- Os detalhes de como o valor variável por distância é calculado e exibido (por exemplo, distância
  usada, momento em que o cliente vê o valor) pertencem ao funcionamento da solicitação e cobrança
  do PitTec, não ao cadastro do prestador, e por isso ficam fora do escopo desta funcionalidade.
- O preço do serviço "Guincho" continua sendo definido individualmente por cada prestador Móvel no
  momento do cadastro, como já acontece hoje, apenas sem a etapa de escolher qual serviço é esse.
- Regras já existentes de validação de endereço, verificação de localização e dados da empresa não
  mudam com esta funcionalidade.
