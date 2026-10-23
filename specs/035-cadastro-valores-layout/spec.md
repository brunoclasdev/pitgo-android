# Feature Specification: Cadastro, valores e layout

**Feature Branch**: `feature/ajuste-cadastro-layout`

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "No cadastro, remover a opção de localizar endereço no mapa e usar o endereço informado pela pessoa; centralizar os valores de PitTec e guincho sob gestão da PitGo, incluindo valor do guincho, percentual da PitGo, taxas diversas, a coluna de preço do guincheiro e duração do serviço; permitir escolher PitTec, guincho ou loja ao criar cadastro; exigir que o guincheiro cadastre placa, marca, modelo e cor do veículo; simplificar a área da empresa removendo os botões Fixo, Móvel e Pittec; e exibir os campos de endereço do mapa de guincho em um card flutuante de borda roxa, aplicando o padrão a telas de mapa semelhantes."

## Clarifications

### Session 2026-10-22

- Q: O preço e a duração definidos pela PitGo para PitTec devem ser cadastrados por serviço do catálogo ou compartilhados entre todos os serviços PitTec? → A: Cadastrar no dashboard por serviço do catálogo, aplicando os mesmos valores a todos os providers daquele serviço.
- Q: Ao escolher “Loja” no cadastro, quais dados e fluxo devem ser solicitados para concluir esse cadastro? → A: Solicitar dados comerciais, endereço e permitir o cadastro de produtos.
- Q: Quem deve definir o preço de cada produto cadastrado por uma loja? → A: O responsável pela loja define o preço dos próprios produtos.
- Q: Quais informações devem ser obrigatórias ao cadastrar um produto da loja? → A: Nome, descrição e preço obrigatórios; foto opcional; sem controle de estoque.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Escolher o tipo de cadastro (Priority: P1)

Como pessoa criando uma conta para atuar no PitGo, quero escolher entre PitTec, guincho e loja para receber o cadastro adequado ao meu trabalho.

**Why this priority**: A escolha define os dados e as etapas necessárias para concluir cada modalidade de cadastro.

**Independent Test**: Iniciar um cadastro e escolher cada modalidade, verificando que a opção selecionada abre o fluxo correspondente e que a pessoa consegue voltar e escolher outra opção.

**Acceptance Scenarios**:

1. **Given** que a pessoa iniciou a criação de cadastro, **When** chega à escolha da modalidade, **Then** vê as opções PitTec, guincho e loja antes de preencher os dados específicos.
2. **Given** que a pessoa escolheu PitTec, **When** continua, **Then** o cadastro apresenta os campos pertinentes a essa modalidade.
3. **Given** que a pessoa escolheu loja, **When** informa os dados comerciais e o endereço, **Then** pode cadastrar produtos para compor o catálogo da loja.
4. **Given** que a pessoa escolheu guincho, **When** continua, **Then** o cadastro solicita também os dados de pelo menos um veículo de atendimento.
5. **Given** que a pessoa ainda não concluiu o cadastro, **When** retorna à escolha da modalidade, **Then** pode selecionar outra opção sem manter campos exclusivos da modalidade anterior.

---

### User Story 2 - Informar endereço e veículo do guincho (Priority: P1)

Como pessoa preenchendo o cadastro de guincho, quero digitar o endereço e os dados do veículo para registrar as informações corretas sem escolher um ponto no mapa.

**Why this priority**: O cadastro precisa guardar o endereço declarado pela pessoa e identificar o veículo usado no atendimento.

**Independent Test**: Preencher manualmente um endereço e placa, marca, modelo e cor do veículo; confirmar que os dados são validados e apresentados para revisão antes do envio.

**Acceptance Scenarios**:

1. **Given** um formulário de cadastro com endereço, **When** a pessoa informa o endereço, **Then** não é apresentada uma ação para localizar ou selecionar o endereço no mapa e o valor digitado é usado no cadastro.
2. **Given** um cadastro de guincho sem veículo informado, **When** a pessoa tenta concluir, **Then** são solicitados placa, marca, modelo e cor.
3. **Given** dados obrigatórios ausentes ou inválidos, **When** a pessoa tenta avançar, **Then** os campos correspondentes recebem orientação para correção e os demais dados permanecem preenchidos.
4. **Given** todos os campos obrigatórios válidos, **When** a pessoa revisa o cadastro, **Then** o endereço informado e os dados do veículo aparecem corretamente antes da confirmação.

---

### User Story 3 - Usar valores definidos pela PitGo (Priority: P1)

Como responsável pelo aplicativo, quero manter as condições comerciais de PitTec e guincho em configurações separadas para definir os valores e a duração dos serviços oferecidos.

**Why this priority**: A centralização evita que cada prestador determine preços e permite que a PitGo mantenha as condições comerciais do marketplace.

**Independent Test**: Como responsável autorizado, alterar uma configuração de PitTec e outra de guincho e conferir que novas solicitações de cada modalidade usam os valores e a duração correspondentes.

**Acceptance Scenarios**:

1. **Given** que não há configuração vigente para uma modalidade, **When** uma nova solicitação tenta obter preço, **Then** não é exibido um valor inventado e a pessoa recebe uma indicação de indisponibilidade da tarifa.
2. **Given** uma configuração vigente de PitTec para um serviço do catálogo, **When** qualquer provider que oferece esse serviço o apresenta para contratação, **Then** todos usam o mesmo preço e duração definidos no dashboard para aquele serviço.
3. **Given** uma configuração vigente de guincho, **When** a solicitação é cotada, **Then** são usados o valor do guincho, o percentual da PitGo e as taxas adicionais configuradas; o preço do guincheiro permanece no registro, mas não compõe o cálculo atual.
4. **Given** uma configuração comercial atualizada, **When** uma nova solicitação é criada, **Then** ela usa a configuração vigente; solicitações já confirmadas mantêm os valores e a duração apresentados quando foram confirmadas.
5. **Given** alguém sem permissão de responsável pelo aplicativo, **When** tenta alterar valores ou duração, **Then** a alteração é recusada e as configurações vigentes permanecem intactas.
6. **Given** valores negativos, percentuais fora do intervalo válido ou duração ausente/inválida, **When** uma pessoa autorizada tenta salvar a configuração, **Then** ela não é ativada e os campos inválidos são indicados.

---

### User Story 4 - Simplificar as áreas e os mapas (Priority: P2)

Como prestador ou pessoa usando um mapa para informar endereços, quero uma interface mais limpa e consistente para reconhecer os dados importantes e manter os campos acessíveis enquanto consulto o mapa.

**Why this priority**: Uma hierarquia visual mais simples reduz ruído na área da empresa e mantém os endereços fáceis de localizar nos fluxos de mapa.

**Independent Test**: Abrir a área da empresa e os mapas semelhantes que coletam endereços; conferir a remoção dos três botões de modalidade e a permanência do card flutuante durante a interação com o mapa.

**Acceptance Scenarios**:

1. **Given** que a pessoa abriu a área da empresa, **When** a tela é exibida, **Then** os botões Fixo, Móvel e Pittec não aparecem e as informações e ações essenciais continuam identificáveis.
2. **Given** que a pessoa abriu o mapa de guincho para informar endereços, **When** interage ou move o mapa, **Then** os campos de endereço permanecem em um card flutuante fixo com borda roxa.
3. **Given** outra tela semelhante que combina mapa e entrada de endereço, **When** é exibida, **Then** usa o mesmo tratamento visual do card sem ocultar controles essenciais do mapa.
4. **Given** fonte ampliada ou tela compacta, **When** a pessoa consulta o card ou a área da empresa, **Then** os campos e ações essenciais continuam legíveis e alcançáveis.

---

### User Story 5 - Cadastrar produtos da loja (Priority: P2)

Como responsável por uma loja, quero cadastrar produtos no catálogo da loja para que eles possam ser apresentados aos clientes.

**Why this priority**: Um perfil de loja precisa conseguir registrar os itens que oferece para que a opção de loja tenha conteúdo útil.

**Independent Test**: Concluir o cadastro de uma loja, adicionar um produto e confirmar que ele aparece relacionado à loja correta.

**Acceptance Scenarios**:

1. **Given** uma loja cadastrada, **When** seu responsável informa nome, descrição e preço e adiciona um produto, **Then** o produto fica associado àquela loja e aparece em seu catálogo com o preço informado; a foto pode ser omitida.
2. **Given** uma pessoa sem permissão para administrar uma loja, **When** tenta alterar o catálogo dela, **Then** a alteração é recusada.

### Edge Cases

- O endereço digitado é incompleto, contém espaços externos ou não pode ser geocodificado; o texto original permanece no formulário, a pessoa recebe uma orientação para corrigir ou tentar novamente e o cadastro não é concluído sem coordenadas necessárias à descoberta de prestadores.
- A pessoa troca a modalidade depois de preencher dados específicos; nenhum dado exclusivo da modalidade anterior é enviado junto ao novo cadastro.
- A placa já está associada a outro veículo ou foi digitada em formatos com e sem máscara.
- Não existe tarifa vigente, a configuração expira ou duas alterações administrativas ocorrem ao mesmo tempo.
- Uma tarifa muda enquanto um orçamento está aberto; a proposta já apresentada preserva seus valores até expirar ou ser confirmada conforme as regras atuais.
- Os campos do card flutuante ocupam muito espaço em telas pequenas, orientação horizontal ou com teclado aberto.
- A área da empresa precisa manter ações importantes disponíveis após a remoção dos seletores de modalidade.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O início do fluxo de criação de cadastro MUST apresentar a escolha entre PitTec, guincho e loja antes dos campos específicos da modalidade.
- **FR-002**: Cada escolha MUST direcionar a pessoa ao cadastro correspondente e MUST impedir que campos exclusivos de outra modalidade sejam enviados.
- **FR-002A**: O cadastro de loja MUST solicitar dados comerciais e endereço e MUST permitir que a pessoa responsável cadastre produtos associados à loja.
- **FR-003**: Os formulários de cadastro MUST permitir que a pessoa digite o endereço e MUST usar o endereço informado como dado do cadastro.
- **FR-004**: O fluxo de cadastro MUST NOT oferecer localização, seleção ou confirmação de endereço em mapa.
- **FR-005**: O cadastro de guincho MUST exigir pelo menos um veículo com placa, marca, modelo e cor.
- **FR-006**: O sistema MUST validar os campos obrigatórios do endereço e do veículo antes da confirmação e MUST preservar os demais dados digitados quando indicar correções.
- **FR-007**: O sistema MUST manter configurações comerciais distintas para PitTec e guincho, gerenciadas no dashboard por responsáveis autorizados pelo aplicativo.
- **FR-008**: A configuração de PitTec MUST permitir informar preço e duração por serviço do catálogo; esses valores MUST ser iguais para todos os providers que oferecem o mesmo serviço.
- **FR-009**: A configuração de guincho MUST incluir o valor do guincho, o percentual da PitGo, taxas adicionais e uma coluna de preço do guincheiro, mesmo que esse último valor não componha o preço aplicado nesta entrega.
- **FR-010**: As configurações MUST ser a fonte dos valores e da duração exibidos em novas cotações e solicitações da modalidade correspondente; o prestador MUST NOT informar ou substituir esses valores no próprio cadastro ou contratação.
- **FR-011**: Para guincho, o valor base MUST ser composto pelo preço do guincho configurado pela PitGo e pelo deslocamento, quando aplicável. O percentual da PitGo MUST incidir sobre esse valor base; as taxas diversas MUST possuir componentes percentual e fixo, calculados sobre o mesmo valor base. O total do cliente MUST ser a soma do valor base, do percentual da PitGo e das taxas diversas. O preço do guincheiro MUST permanecer armazenado, mas não compor o cálculo atual.
- **FR-012**: A alteração de uma configuração MUST aplicar-se a novas solicitações e MUST preservar valores e duração já apresentados em cotações confirmadas ou ainda válidas.
- **FR-013**: Somente responsáveis autorizados pelo aplicativo MUST poder criar, editar ou ativar configurações de valores e duração.
- **FR-014**: Valores, percentuais e duração MUST ser validados antes da ativação; configurações inválidas ou sem os dados obrigatórios MUST NOT ser usadas em novas cotações.
- **FR-015**: A área da empresa MUST remover os botões Fixo, Móvel e Pittec e MUST continuar mostrando as informações e ações necessárias à gestão da empresa em uma hierarquia visual simples.
- **FR-016**: A área de endereço no mapa de guincho MUST aparecer em um card flutuante fixo com borda roxa, permanecendo visível durante a interação com o mapa.
- **FR-017**: Outras telas semelhantes que combinam mapa e entrada de endereço MUST aplicar o mesmo padrão visual, preservando os controles essenciais e a interação com o mapa.
- **FR-018**: A área da empresa e os cards de endereço MUST permanecer legíveis e utilizáveis em telas compactas, com fonte ampliada, teclado visível e orientação suportada.
- **FR-019**: O catálogo de produtos MUST permitir que o responsável autorizado da loja cadastre produtos vinculados à própria loja com nome, descrição e preço obrigatórios e foto opcional; esse responsável MUST definir o preço e o sistema MUST impedir alterações por pessoas sem autorização. O catálogo MUST NOT exigir nem controlar quantidade em estoque nesta entrega.

### Key Entities

- **Modalidade de cadastro**: Tipo de atuação escolhido entre PitTec, guincho e loja e que determina os dados solicitados.
- **Endereço informado**: Texto de endereço fornecido pela pessoa durante o cadastro e usado como fonte do dado cadastral.
- **Veículo de guincho**: Veículo de atendimento associado ao prestador, identificado por placa, marca, modelo e cor.
- **Produto de loja**: Item associado ao catálogo de uma loja, com nome, descrição e preço obrigatórios, foto opcional e preço definido pelo responsável autorizado daquela loja.
- **Configuração de preço PitTec**: Preço e duração definidos pela PitGo no dashboard para um serviço do catálogo e aplicados igualmente aos providers que oferecem esse serviço.
- **Configuração de preço de guincho**: Valor do guincho, percentual da PitGo, componentes percentual e fixo das taxas adicionais, duração e preço do guincheiro preservado para possível uso futuro.
- **Cotação**: Valores e duração apresentados para uma solicitação, relacionados à configuração vigente no momento da cotação e preservados conforme sua validade.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cadastros iniciados, a modalidade PitTec, guincho ou loja é escolhida antes de preencher campos específicos.
- **SC-002**: Em 100% dos cadastros, o endereço utilizado corresponde ao texto informado pela pessoa, sem exigir seleção de um ponto no mapa.
- **SC-003**: Em 100% dos cadastros de guincho concluídos, placa, marca, modelo e cor estão associados ao prestador.
- **SC-004**: Em 100% das novas cotações avaliadas, os valores e a duração correspondem à configuração vigente da modalidade; cotações válidas anteriores conservam os dados apresentados.
- **SC-005**: Em 100% das tentativas feitas por perfis sem autorização, alterações de valores e duração são recusadas.
- **SC-006**: Na área da empresa, nenhum dos botões Fixo, Móvel ou Pittec é exibido e todas as ações essenciais continuam acessíveis.
- **SC-007**: Nos mapas cobertos pelo padrão, os campos de endereço permanecem visíveis em um card com borda roxa enquanto a pessoa move ou consulta o mapa.
- **SC-008**: Os fluxos de cadastro e mapa podem ser concluídos em dispositivos suportados com fonte ampliada e teclado aberto, sem campos ou ações essenciais inacessíveis.
- **SC-009**: Em 100% dos testes, produtos cadastrados por uma loja aparecem associados à loja correta e tentativas de alteração por outra conta são recusadas.

## Assumptions

- A escolha PitTec, guincho ou loja ocorre no início do cadastro e abre os formulários existentes adequados a cada modalidade.
- O cadastro de guincho exige pelo menos um veículo e os quatro dados citados são obrigatórios; esta entrega não exige fotos ou documentos do veículo.
- Responsáveis pelo aplicativo são os únicos autorizados a alterar valores e duração no dashboard; prestadores consultam somente os valores aplicáveis às solicitações.
- O preço e a duração de PitTec são definidos por serviço do catálogo e compartilhados por todos os providers daquele serviço.
- O cadastro da loja inclui dados comerciais, endereço digitado e um catálogo de produtos associado à loja.
- O responsável da loja define o preço dos produtos; as regras de preço centralizadas pela PitGo aplicam-se a PitTec e guincho.
- Produtos da loja exigem nome, descrição e preço; foto é opcional e não há controle de estoque nesta entrega.
- Alterações de preço não reescrevem propostas já apresentadas, seguindo o comportamento atual de cotações com valores congelados.
- O endereço manual é obrigatório e permanece como o endereço canônico informado pela pessoa; o backend geocodifica esse texto automaticamente para gerar as coordenadas exigidas pela descoberta de prestadores, sem exigir localização ou seleção no mapa.
- A configuração de guincho mantém o preço do guincheiro disponível no registro, mas não o usa para determinar o valor desta entrega.
- O cálculo de guincho usa o valor administrado pela PitGo mais deslocamento aplicável como base para o percentual PitGo e para os componentes percentual e fixo das taxas diversas; o preço do guincheiro fica reservado e fora do cálculo atual.

## Scope Boundaries

- **In scope**: escolha da modalidade de cadastro, endereço digitado, veículo de guincho, configuração administrativa de valores e duração para PitTec e guincho, simplificação da área da empresa e card de endereço em mapas semelhantes.
- **Out of scope**: localização de endereços em mapa durante cadastro, alteração de tarifas para cotações já confirmadas, retorno do preço do guincheiro como componente ativo, inclusão de dados adicionais do veículo, controle de estoque de produtos ou mudanças em mapas sem coleta de endereço.
