# Feature Specification: Sessão autenticada e cadastro de veículos

**Feature Branch**: `006-auth-vehicle-registration`

**Created**: 2026-08-19

**Status**: Draft

**Input**: User description: "Criar sessão logada e cadastro de veículos; personalizar a Home com
os dados do usuário autenticado e perguntar, ao concluir o cadastro, se deseja cadastrar um
veículo usando o contrato existente de veículos."

## Clarifications

### Session 2026-08-19

- Q: Se o aplicativo encontrar uma sessão salva, mas estiver sem internet para validá-la e carregar
  o perfil, o que deve aparecer? → A: Preservar a sessão e exibir um estado de verificação com
  `Tentar novamente`, sem abrir a Home, mostrar dados antigos ou realizar logout.
- Q: Se a sessão for válida, mas o perfil correspondente estiver ausente, inativo ou inconsistente,
  como o aplicativo deve proceder? → A: Bloquear a Home, preservar a sessão e apresentar
  `Tentar novamente` e `Sair`, sem recriar, corrigir ou reativar o perfil automaticamente.
- Q: Se o aplicativo for fechado no convite pós-cadastro antes de a pessoa escolher uma opção, o
  que deve acontecer na próxima abertura? → A: Manter a decisão pendente e apresentar novamente o
  convite após restaurar a sessão, até a escolha explícita de `Cadastrar veículo` ou `Agora não`.
- Q: Se a sessão expirar enquanto o formulário de veículo contém dados ainda não enviados, o que
  deve acontecer com esse rascunho? → A: Descartar integralmente o rascunho, retornar ao login e
  exigir novo preenchimento após uma autenticação válida.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entrar e acessar uma Home personalizada (Priority: P1)

Como usuário com conta, quero entrar no aplicativo e manter minha sessão para encontrar minha
identidade e meu veículo principal corretamente representados na Home.

**Why this priority**: A sessão confiável é a fronteira que impede dados de usuários diferentes de
se misturarem e torna todos os demais fluxos personalizados possíveis.

**Independent Test**: Pode ser testado entrando com uma conta existente, fechando e reabrindo o
aplicativo e verificando que a Home apresenta o nome e o veículo principal dessa mesma conta, sem
dados demonstrativos ou de outra pessoa.

**Acceptance Scenarios**:

1. **Given** uma conta válida sem sessão ativa, **When** a pessoa informa e-mail e senha corretos
   pela ação `Entrar`, **Then** sua sessão é iniciada e a Home é aberta com seus próprios dados.
2. **Given** uma sessão válida já iniciada, **When** o aplicativo é fechado e aberto novamente,
   **Then** a sessão é restaurada e a Home é apresentada sem exigir novo login.
3. **Given** uma sessão expirada, inválida ou encerrada, **When** a pessoa tenta abrir uma área
   autenticada, **Then** ela retorna ao fluxo de entrada e nenhum dado privado anterior permanece
   visível.
4. **Given** um perfil autenticado com veículo principal ativo, **When** a Home termina de carregar,
   **Then** ela mostra o nome do perfil, avatar ou iniciais e os dados resumidos desse veículo.
5. **Given** um perfil autenticado sem veículo principal, **When** a Home termina de carregar,
   **Then** ela apresenta um estado vazio com ação para cadastrar ou selecionar um veículo, sem
   exibir o veículo mockado de outra pessoa.
6. **Given** uma pessoa autenticada, **When** encerra sua sessão, **Then** retorna à tela inicial e
   os dados personalizados são removidos da interface e do estado acessível.
7. **Given** uma sessão persistida e ausência de conexão, **When** o aplicativo não consegue
   validá-la nem carregar o perfil, **Then** preserva a sessão, não abre a Home e apresenta um
   estado de verificação com a ação `Tentar novamente`, sem mostrar dados antigos.
8. **Given** uma sessão válida cujo perfil está ausente, inativo ou inconsistente, **When** o acesso
   à área autenticada é avaliado, **Then** a Home permanece bloqueada e são apresentadas as ações
   `Tentar novamente` e `Sair`, sem alterar automaticamente o perfil.

---

### User Story 2 - Decidir sobre o primeiro veículo após criar a conta (Priority: P2)

Como novo usuário, quero decidir se cadastro meu veículo logo após criar a conta para personalizar
a Home sem ser obrigado a concluir essa etapa imediatamente.

**Why this priority**: O convite aproveita o momento de onboarding, mas mantém o cadastro da conta
rápido e não bloqueia quem ainda não possui os dados do veículo.

**Independent Test**: Pode ser testado criando uma conta válida e verificando as duas decisões:
`Cadastrar veículo` abre o formulário e `Agora não` abre a Home com o estado vazio apropriado.

**Acceptance Scenarios**:

1. **Given** uma conta e um perfil recém-criados com sessão ativa, **When** a conclusão é confirmada,
   **Then** o aplicativo pergunta se a pessoa deseja cadastrar um veículo.
2. **Given** o convite pós-cadastro, **When** a pessoa escolhe `Cadastrar veículo`, **Then** o
   formulário de veículo é aberto e permanece associado à conta recém-criada.
3. **Given** o convite pós-cadastro, **When** a pessoa escolhe `Agora não`, **Then** a Home é aberta
   com estado sem veículo e uma ação posterior disponível pela Garagem.
4. **Given** o formulário do primeiro veículo preenchido com dados válidos, **When** a pessoa
   confirma, **Then** o veículo é salvo como ativo e principal e a Home passa a exibi-lo.
5. **Given** uma falha ao cadastrar o primeiro veículo, **When** o resultado é apresentado,
   **Then** a conta e a sessão permanecem válidas, a Home não mostra dados falsos e a pessoa pode
   corrigir ou tentar novamente sem duplicar o veículo.
6. **Given** um convite pós-cadastro ainda sem resposta, **When** o aplicativo é fechado e a mesma
   sessão é restaurada, **Then** o convite é apresentado novamente antes da Home até que uma das
   duas opções seja escolhida explicitamente.

---

### User Story 3 - Cadastrar e selecionar veículos pela Garagem (Priority: P3)

Como usuário autenticado, quero consultar meus veículos, adicionar outro e escolher qual é o
principal para que os atendimentos e a Home usem o veículo correto.

**Why this priority**: O usuário pode pular o onboarding, trocar de carro ou possuir mais de um
veículo, tornando a Garagem o ponto permanente de gestão da seleção atual.

**Independent Test**: Pode ser testado acessando a Garagem com uma conta autenticada, cadastrando
um veículo e selecionando-o como principal, confirmando a atualização imediata na lista e na Home.

**Acceptance Scenarios**:

1. **Given** uma conta sem veículos, **When** abre a Garagem, **Then** encontra um estado vazio e uma
   ação clara para cadastrar o primeiro veículo.
2. **Given** uma conta com veículos, **When** abre a Garagem, **Then** vê somente seus veículos
   ativos e identifica visualmente qual deles é o principal.
3. **Given** dados mínimos e opcionais válidos, **When** confirma um novo veículo, **Then** ele é
   associado exclusivamente à sessão atual e aparece uma única vez na Garagem.
4. **Given** dois ou mais veículos ativos, **When** a pessoa escolhe outro como principal, **Then**
   apenas esse veículo permanece principal e a Home é atualizada para refletir a escolha.
5. **Given** placa, Renavam ou chassi já associado a algum veículo, **When** a pessoa tenta salvar,
   **Then** recebe uma mensagem neutra de indisponibilidade, sem identificação do proprietário.
6. **Given** um formulário de veículo com dados ainda não enviados, **When** a sessão expira,
   **Then** o rascunho é descartado integralmente, o login é apresentado e nenhum dado digitado é
   restaurado após uma nova autenticação.

### Edge Cases

- A restauração da sessão termina antes do carregamento de qualquer Home personalizada, evitando
  piscar dados mockados ou do usuário anterior.
- Perfil inexistente, inativo ou inconsistente bloqueia a Home personalizada, preserva a sessão e
  apresenta `Tentar novamente` e `Sair`, sem criar, corrigir ou reativar perfil automaticamente.
- Uma conta válida pode existir sem veículo; essa condição é um estado vazio, não um erro.
- Veículos inativos não aparecem como disponíveis e não podem ser usados como principal.
- Uma conta legada pode possuir veículos ativos sem principal; nesse caso, a Home solicita uma
  seleção e a Garagem permite definir exatamente um principal.
- Toques repetidos em login, cadastro ou seleção de principal não produzem sessões, veículos ou
  principais duplicados.
- Placas aceitam formato brasileiro legado e Mercosul e são comparadas sem diferença de caixa ou
  pontuação externa.
- Renavam preserva zeros à esquerda e não é tratado como número.
- Chassi é comparado sem diferença entre letras minúsculas e maiúsculas.
- Ano de fabricação e ano-modelo fora do intervalo permitido e quilometragem negativa são
  rejeitados antes do envio.
- Perda de conexão durante login, carregamento da Home ou cadastro mantém estado coerente e oferece
  nova tentativa sem mostrar dados antigos como atuais; quando ocorrer durante a validação de uma
  sessão persistida, a sessão é preservada sem abrir a Home ou realizar logout.
- Se a sessão expirar durante o cadastro do veículo, o envio é interrompido, o rascunho é
  descartado integralmente e a pessoa retorna ao fluxo de entrada sem associar ou restaurar seus
  dados para outra conta.
- Fechar o aplicativo durante o convite pós-cadastro não equivale a escolher `Agora não`; a decisão
  permanece pendente e é reapresentada para a mesma conta após a restauração da sessão.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST disponibilizar entrada por e-mail e senha a partir da ação `Entrar`.
- **FR-002**: O sistema MUST criar um estado central e mutuamente exclusivo para sessão em
  verificação, autenticada e não autenticada.
- **FR-003**: O sistema MUST restaurar uma sessão válida ao iniciar o aplicativo antes de decidir
  entre a Home e o fluxo não autenticado.
- **FR-004**: Áreas autenticadas MUST NOT apresentar conteúdo privado quando a sessão estiver
  ausente, inválida, expirada ou encerrada.
- **FR-005**: A pessoa autenticada MUST poder encerrar a sessão, retornando ao fluxo inicial e
  removendo dados personalizados da interface.
- **FR-006**: A Home MUST carregar o perfil pertencente ao identificador da sessão atual e MUST NOT
  usar nome, avatar, veículo ou endereço demonstrativo como se fossem dados reais.
- **FR-007**: A Home MUST representar carregamento, conteúdo, ausência de veículo e falha de forma
  explícita e mutuamente exclusiva.
- **FR-008**: Quando disponível, a Home MUST mostrar o nome do perfil e o avatar; quando não houver
  avatar, MUST mostrar iniciais derivadas do nome atual.
- **FR-009**: A Home MUST mostrar o único veículo ativo marcado como principal da conta atual.
- **FR-010**: Na ausência de veículo principal, a Home MUST apresentar ação para abrir a Garagem e
  cadastrar ou selecionar um veículo.
- **FR-011**: Após a conclusão bem-sucedida da criação da conta e do perfil, o sistema MUST
  apresentar uma decisão entre `Cadastrar veículo` e `Agora não` antes de abrir a Home.
- **FR-012**: Escolher `Agora não` MUST manter a sessão e abrir a Home sem veículo; o convite MUST
  NOT bloquear o uso posterior do aplicativo.
- **FR-013**: Escolher `Cadastrar veículo` MUST abrir um formulário associado à sessão atual.
- **FR-014**: O formulário MUST exigir marca e modelo e MUST permitir informar versão, ano de
  fabricação, ano-modelo, placa, Renavam, chassi, cor, combustível, câmbio, motor e quilometragem.
- **FR-015**: O identificador do proprietário MUST ser obtido exclusivamente da sessão autenticada
  e MUST NOT ser um campo editável ou fornecido pela interface.
- **FR-016**: Um novo veículo MUST iniciar ativo; o primeiro veículo ativo da conta MUST ser
  principal automaticamente.
- **FR-017**: Para contas com veículos existentes, o formulário MUST permitir escolher se o novo
  veículo será o principal; quando escolhido, qualquer principal anterior MUST perder essa condição
  na mesma operação lógica.
- **FR-018**: Marca e modelo MUST ser normalizados apenas quanto a espaços externos e MUST preservar
  seu conteúdo significativo.
- **FR-019**: Placa e chassi MUST ser normalizados para caixa alta e sem espaços externos; Renavam
  MUST preservar zeros à esquerda e aceitar apenas dígitos.
- **FR-020**: Placa preenchida MUST aceitar os formatos brasileiros legado e Mercosul; Renavam
  preenchido MUST possuir 11 dígitos; chassi preenchido MUST possuir 17 caracteres válidos.
- **FR-021**: Ano de fabricação e ano-modelo, quando informados, MUST estar entre 1900 e o próximo
  ano civil; quilometragem, quando informada, MUST ser igual ou maior que zero.
- **FR-022**: Placa, Renavam e chassi MUST ser únicos quando presentes, com conflitos apresentados
  por mensagens neutras que não revelem outro proprietário.
- **FR-023**: A Garagem MUST listar somente veículos ativos pertencentes à sessão atual e MUST
  indicar qual é o principal sem depender apenas de cor.
- **FR-024**: A Garagem MUST permitir cadastrar veículos e selecionar exatamente um veículo ativo
  como principal.
- **FR-025**: Depois de cadastrar ou alterar o principal, Home e Garagem MUST refletir o mesmo
  veículo sem exigir uma nova autenticação.
- **FR-026**: Login, restauração de sessão, perfil, lista de veículos, cadastro e seleção de
  principal MUST apresentar progresso, falha compreensível e ação de nova tentativa quando
  aplicável.
- **FR-027**: Durante operações em andamento, envios repetidos MUST ser ignorados para impedir
  sessões, veículos ou alterações duplicadas.
- **FR-028**: Uma sessão MUST poder consultar e alterar somente o próprio perfil e seus próprios
  veículos; tentativas de acessar dados de outra conta MUST ser negadas sem revelar conteúdo.
- **FR-029**: Senha, tokens, CPF, CNPJ, Renavam e chassi completos MUST NOT aparecer em mensagens,
  logs ou diagnósticos apresentados à pessoa.
- **FR-030**: Dados do usuário anterior MUST ser removidos do estado visível ao trocar ou encerrar
  sessão.
- **FR-031**: Quando uma sessão persistida não puder ser validada por indisponibilidade de rede, o
  sistema MUST preservá-la, MUST NOT abrir a Home nem mostrar dados personalizados armazenados e
  MUST apresentar um estado de verificação com a ação `Tentar novamente`, sem realizar logout.
- **FR-032**: Quando uma sessão válida estiver associada a um perfil ausente, inativo ou
  inconsistente, o sistema MUST bloquear a Home, preservar a sessão e apresentar as ações
  `Tentar novamente` e `Sair`; o sistema MUST NOT recriar, corrigir ou reativar o perfil
  automaticamente.
- **FR-033**: A decisão pós-cadastro MUST permanecer pendente para a conta até a escolha explícita
  de `Cadastrar veículo` ou `Agora não`; se o aplicativo for encerrado antes da resposta, o convite
  MUST ser reapresentado após a restauração da mesma sessão e antes da Home.
- **FR-034**: Se a sessão expirar durante o preenchimento ou envio de um veículo, o sistema MUST
  interromper a operação, descartar integralmente o rascunho e retornar ao login; os dados do
  rascunho MUST NOT ser restaurados após uma nova autenticação.

### Scope Boundaries

- Edição e exclusão de veículos existentes não fazem parte desta entrega.
- Consulta automática de marca, modelo, placa ou Renavam em serviços externos não faz parte desta
  entrega; os dados são informados pela pessoa.
- Upload ou edição de avatar não faz parte desta entrega; somente a imagem já existente ou as
  iniciais são apresentadas.
- Cadastro e gestão de endereços não fazem parte desta entrega; a Home deve usar um estado neutro
  como `Endereço não cadastrado` em vez de endereço mockado.
- Recuperação de senha, login social e autenticação multifator não fazem parte desta entrega.

### Key Entities

- **Sessão autenticada**: Representa a identidade atualmente válida, seu estado de verificação e a
  capacidade de acessar dados privados; nunca contém dados de outra sessão visíveis após logout.
- **Perfil**: Dados pessoais associados em relação um-para-um à identidade, incluindo nome, e-mail,
  telefone, documento, avatar opcional e status ativo.
- **Veículo**: Bem pertencente a um único perfil, identificado por marca e modelo e opcionalmente
  por versão, anos, placa, Renavam, chassi, características e quilometragem; possui estados ativo e
  principal controlados pelo sistema e pela escolha do proprietário.
- **Seleção de veículo principal**: Relação que garante no máximo um veículo principal por conta e
  define qual resumo aparece na Home e será usado nos fluxos seguintes.
- **Decisão pós-cadastro**: Escolha pendente vinculada à conta entre cadastrar o primeiro veículo
  imediatamente ou seguir para a Home e realizar a ação depois; permanece pendente entre aberturas
  do aplicativo até uma resposta explícita.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% das inicializações avaliadas, o aplicativo decide entre fluxo autenticado e
  não autenticado antes de apresentar dados personalizados.
- **SC-002**: Em 100% das trocas ou encerramentos de sessão avaliados, nenhum nome, avatar ou veículo
  da conta anterior permanece visível.
- **SC-003**: Sob conexão normal, 95% das pessoas autenticadas veem seu nome e o estado correto do
  veículo na Home em até 2 segundos após a abertura.
- **SC-004**: Uma pessoa consegue cadastrar o primeiro veículo com os dados mínimos em até 2
  minutos, partindo do convite pós-cadastro ou da Garagem.
- **SC-005**: Em 100% dos cadastros concluídos, o veículo pertence à mesma conta autenticada e
  aparece uma única vez.
- **SC-006**: Em 100% das contas com pelo menos um veículo criado por este fluxo, existe exatamente
  um veículo principal; ao trocar a seleção, a Home apresenta o novo principal na próxima
  atualização visível.
- **SC-007**: Em 100% das tentativas avaliadas de acesso cruzado, nenhum perfil ou veículo de outra
  conta é retornado ou alterado.
- **SC-008**: Todos os cenários de sessão, Home, convite e veículo permanecem operáveis em
  smartphones suportados com teclado aberto, fonte ampliada e tecnologia assistiva.
- **SC-009**: A inspeção de mensagens e diagnósticos encontra zero ocorrência de senha, token,
  documento pessoal, Renavam ou chassi completo usados durante o fluxo.

## Assumptions

- A autenticação existente por e-mail e senha será reutilizada; o botão `Entrar` passará a executar
  autenticação real em vez de navegar diretamente para a Home.
- O perfil é criado durante o cadastro da conta e possui o mesmo identificador da sessão.
- A fonte de veículos já possui os campos e regras informados pelo usuário, incluindo vínculo ao
  perfil, unicidade de placa/Renavam/chassi, status ativo e no máximo um principal por conta.
- O primeiro veículo válido é principal automaticamente; veículos seguintes só substituem o
  principal mediante escolha explícita.
- Marca, modelo e demais descrições são texto livre nesta entrega, sem catálogo automotivo externo.
- A decisão pós-cadastro permanece pendente e é reapresentada após restaurar a mesma sessão até uma
  escolha explícita; quem escolher `Agora não` pode usar a Garagem a qualquer momento.
- O avatar é opcional e possui fallback por iniciais; endereço real depende de uma feature futura.
- Uma conta pode permanecer válida sem veículo cadastrado e deve receber um estado vazio útil.
