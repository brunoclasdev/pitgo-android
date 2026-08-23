# Feature Specification: Cadastro de empresa prestadora

**Feature Branch**: `007-provider-registration`

**Created**: 2026-08-20

**Status**: Draft

**Input**: User description: "Montar tela para cadastro de empresa com dados do prestador,
endereço, serviço, categoria e condições comerciais vinculados à conta logada."

## Clarifications

### Session 2026-08-20

- Q: Como a empresa será vinculada à conta autenticada? → A: `provider.id` será igual ao
  identificador da conta autenticada.
- Q: O prestador cria ou escolhe um serviço? → A: Escolhe serviços já existentes no catálogo.
- Q: Quantos serviços entram no cadastro inicial? → A: Um ou vários serviços, com pelo menos um
  para concluir.
- Q: O cadastro deve aceitar somente endereços no Brasil ou também endereços internacionais? → A:
  Somente endereços no Brasil.
- Q: O e-mail da empresa deve ser igual ao e-mail da conta autenticada ou pode ser um contato
  diferente? → A: Inicia com o e-mail da conta, mas pode ser alterado.
- Q: Qual deve ser o número máximo de serviços adicionados no cadastro inicial da empresa? → A:
  Não haverá limite definido pelo aplicativo.
- Q: Qual deve ser a duração máxima permitida para uma oferta de serviço? → A: 10.080 minutos,
  equivalentes a sete dias.
- Q: Uma oferta pode ter preço zero, representando um serviço gratuito? → A: Somente quando a
  pessoa marcar explicitamente a opção `Serviço gratuito`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cadastrar a empresa prestadora (Priority: P1)

Como pessoa autenticada que deseja oferecer serviços, quero informar os dados da minha empresa,
seu endereço e ao menos uma oferta para criar uma identidade de prestador pronta para participar
do marketplace e vinculada exclusivamente à minha conta.

**Why this priority**: Empresa, endereço e ao menos uma oferta formam o conjunto mínimo necessário
para que o prestador possa participar do marketplace de forma útil e confiável.

**Independent Test**: Pode ser testado com uma conta autenticada ainda sem empresa, preenchendo os
dados empresariais, o endereço e uma oferta baseada em serviço existente e confirmando que todo o
conjunto fica associado à mesma conta, sem registros parciais ou pertencentes a outra pessoa.

**Acceptance Scenarios**:

1. **Given** uma conta autenticada sem empresa cadastrada, **When** a pessoa preenche dados
   empresariais, endereço e ao menos uma oferta válidos e confirma o cadastro, **Then** a empresa,
   o endereço e todas as ofertas são registrados com o mesmo proprietário autenticado.
2. **Given** campos empresariais ou de endereço obrigatórios ausentes ou inválidos, **When** a
   pessoa tenta continuar, **Then** o cadastro não é enviado e cada campo inválido apresenta uma
   orientação compreensível.
3. **Given** uma conta que já possui empresa, **When** tenta iniciar um novo cadastro, **Then** o
   sistema impede uma segunda empresa para a mesma conta e direciona a pessoa para os dados já
   existentes.
4. **Given** uma falha durante o salvamento de qualquer parte do cadastro, **When** a operação
   termina, **Then** nenhuma parte nova fica salva isoladamente e os dados não sensíveis continuam
   disponíveis para correção e nova tentativa.

---

### User Story 2 - Configurar serviços e condições comerciais (Priority: P2)

Como empresa prestadora em cadastro, quero escolher um ou vários serviços do catálogo, organizados
por categoria, e informar preço, duração e descrição comercial para que cada oferta possa ser
apresentada corretamente aos clientes.

**Why this priority**: Uma empresa cadastrada sem oferta não consegue participar do marketplace;
a categoria e as condições comerciais tornam o serviço localizável e contratável.

**Independent Test**: Pode ser testado com uma empresa de teste, categorias e serviços previamente
disponíveis, configurando duas ofertas e verificando que serviço, categoria, preço, duração e
descrição permanecem relacionados à empresa correta sem criar ou alterar o catálogo.

**Acceptance Scenarios**:

1. **Given** categorias e serviços disponíveis, **When** a pessoa escolhe uma categoria,
   **Then** encontra somente os serviços existentes vinculados àquela categoria e consegue escolher
   um deles para a oferta.
2. **Given** um serviço existente selecionado, **When** a pessoa informa preço, duração e descrição
   comercial e confirma, **Then** a oferta fica vinculada à empresa da sessão atual e ao serviço
   correspondente, sem alterar o serviço global.
3. **Given** uma oferta válida já preenchida, **When** a pessoa escolhe adicionar outro serviço,
   **Then** recebe um novo conjunto de categoria, serviço, preço, duração e descrição e consegue
   remover uma oferta ainda não confirmada.
4. **Given** que a pessoa marca uma oferta como gratuita, **When** confirma os dados, **Then** a
   oferta é registrada com preço zero sem exigir preenchimento monetário.
5. **Given** preço ou duração inválidos, **When** a pessoa tenta confirmar, **Then** a oferta não é
   salva e os campos recebem mensagens específicas.
6. **Given** categorias ou serviços indisponíveis ou uma falha ao carregá-los, **When** a tela é apresentada,
   **Then** o seletor informa o estado vazio ou de falha e oferece nova tentativa sem permitir uma
   categoria arbitrária.

---

### User Story 3 - Corrigir falhas sem perder o cadastro (Priority: P3)

Como pessoa preenchendo um cadastro extenso, quero entender conflitos e falhas e corrigir apenas o
necessário para não precisar recomeçar todo o processo.

**Why this priority**: O fluxo reúne dados empresariais, endereço e oferta; preservar o trabalho
válido reduz abandono sem comprometer a consistência dos registros.

**Independent Test**: Pode ser testado provocando conflito de CNPJ ou e-mail, perda de conexão e
expiração de sessão, verificando mensagens neutras, preservação apropriada do formulário e ausência
de salvamento duplicado.

**Acceptance Scenarios**:

1. **Given** um CNPJ ou e-mail já indisponível, **When** a pessoa confirma o cadastro, **Then** recebe
   uma mensagem neutra no campo correspondente, sem dados da outra empresa.
2. **Given** uma falha temporária de conexão, **When** o envio falha, **Then** os dados não sensíveis
   permanecem no formulário e a pessoa pode tentar novamente sem criar duplicidades.
3. **Given** uma operação de cadastro em andamento, **When** a pessoa toca repetidamente na ação
   principal, **Then** apenas uma solicitação é processada.
4. **Given** que a sessão expira antes da conclusão, **When** o sistema detecta a perda da sessão,
   **Then** nenhum registro é atribuído a outra conta e a pessoa retorna ao fluxo de autenticação.

### Edge Cases

- A lista de categorias está vazia, indisponível ou muda enquanto o formulário permanece aberto.
- Uma categoria existe, mas ainda não possui serviços disponíveis.
- A categoria selecionada deixa de estar disponível antes da confirmação.
- A conta já possui uma empresa ou uma operação anterior foi concluída, mas a resposta não chegou.
- O mesmo serviço é associado mais de uma vez à mesma empresa.
- A pessoa remove todas as ofertas depois de ter adicionado várias.
- A pessoa adiciona uma quantidade elevada de ofertas ao mesmo cadastro.
- CNPJ, e-mail ou telefone contêm máscara, espaços ou diferenças de caixa.
- Preço é zero sem a opção de gratuidade, negativo, possui separador decimal inesperado ou mais
  casas decimais que o aceito.
- Duração é zero, negativa, fracionada ou superior a 10.080 minutos.
- Apenas latitude ou longitude é informada, ou uma coordenada está fora dos limites terrestres.
- Número do endereço não é convencional, como `s/n`, bloco ou lote.
- A pessoa informa CEP ou estado que não corresponde a um endereço brasileiro válido.
- Uma etapa falha depois que outra parte do conjunto já foi preparada para salvamento.
- A conexão cai durante a confirmação ou a resposta se perde após o processamento.
- A sessão muda ou expira enquanto o formulário contém dados empresariais ainda não enviados.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir acesso ao cadastro de empresa apenas para uma conta com
  sessão autenticada válida.
- **FR-002**: O sistema MUST impedir que uma mesma conta crie mais de uma empresa por este fluxo.
- **FR-003**: O cadastro da empresa MUST solicitar nome, descrição, e-mail, telefone e CNPJ; nome,
  e-mail, telefone e CNPJ MUST ser obrigatórios, enquanto a descrição MAY ser opcional.
- **FR-004**: E-mail, telefone e CNPJ MUST ser normalizados e validados antes da confirmação, sem
  substituir a validação definitiva no salvamento.
- **FR-004A**: O campo de e-mail da empresa MUST iniciar preenchido com o e-mail da conta
  autenticada e MUST permitir alteração; o valor confirmado será o contato comercial da empresa.
- **FR-005**: O cadastro de endereço MUST solicitar rua, número, bairro, CEP, cidade, estado,
  complemento, latitude e longitude; rua, número, bairro, CEP, cidade e estado MUST ser
  obrigatórios, enquanto complemento e coordenadas MAY ser opcionais.
- **FR-005A**: O cadastro MUST aceitar somente endereços brasileiros, manter o país fixo como
  Brasil e validar CEP brasileiro com oito dígitos e estado como uma unidade federativa válida.
- **FR-006**: Quando uma coordenada for informada, latitude e longitude MUST ser fornecidas em
  conjunto e MUST respeitar os limites válidos de -90 a 90 e -180 a 180, respectivamente.
- **FR-007**: O endereço criado MUST pertencer à mesma empresa criada ou reconhecida para a sessão
  atual, sem permitir edição manual do identificador da empresa.
- **FR-008**: O sistema MUST carregar as categorias de serviço existentes e apresentá-las por nome
  em um seletor de escolha única, com estados explícitos de carregamento, vazio e falha.
- **FR-009**: Depois da escolha de categoria, o sistema MUST apresentar somente os serviços
  existentes relacionados à categoria escolhida, também com estados explícitos de carregamento,
  vazio e falha.
- **FR-010**: Cada oferta da empresa MUST relacionar a empresa e um serviço existente e MUST
  permitir preço, duração em minutos e descrição comercial, sem criar ou alterar o serviço global.
- **FR-011**: O identificador da empresa MUST ser exatamente o identificador da conta autenticada,
  estabelecendo uma relação de uma empresa por conta.
- **FR-012**: O cadastro MUST permitir adicionar, revisar e remover múltiplas ofertas antes da
  confirmação, mantendo pelo menos uma oferta válida para concluir e sem impor quantidade máxima
  de ofertas no aplicativo.
- **FR-013**: Cada conjunto de oferta MUST exigir a escolha de uma categoria e de um serviço dessa
  categoria, além de preço e duração válidos; a descrição comercial MAY ser opcional.
- **FR-014**: Uma oferta não gratuita MUST exigir preço monetário maior que zero com no máximo duas
  casas decimais; duração MUST ser um número inteiro entre 1 e 10.080 minutos.
- **FR-014A**: Cada oferta MUST disponibilizar a opção explícita `Serviço gratuito`; quando marcada,
  o preço MUST ser definido como zero e o preenchimento monetário MUST ficar indisponível.
- **FR-015**: A descrição comercial da oferta MAY ser diferente da descrição geral do serviço e MAY
  ser opcional.
- **FR-016**: O sistema MUST derivar todos os identificadores de proprietário e relacionamentos a
  partir da sessão e dos registros criados ou selecionados no próprio fluxo; esses identificadores
  MUST NOT ser campos editáveis.
- **FR-017**: Empresa, endereço e todas as ofertas MUST ser confirmados como uma única operação
  lógica: sucesso completo ou nenhuma criação parcial.
- **FR-018**: O sistema MUST impedir mais de uma associação equivalente entre a mesma empresa e o
  mesmo serviço.
- **FR-019**: Durante o salvamento, campos e ações que possam disparar outra confirmação MUST ficar
  indisponíveis e a tela MUST apresentar progresso compreensível.
- **FR-020**: Falhas de validação MUST identificar os campos afetados e levar o foco ao primeiro
  erro na ordem visual do formulário.
- **FR-021**: Falhas temporárias MUST preservar os dados não sensíveis preenchidos e oferecer nova
  tentativa; conflitos MUST usar mensagens neutras sem revelar outra empresa ou conta.
- **FR-022**: Após sucesso, o sistema MUST apresentar confirmação e retornar à área autenticada com
  os dados da empresa e suas ofertas disponíveis para consulta.
- **FR-023**: Perda ou troca de sessão MUST interromper o fluxo, impedir atribuição à conta errada e
  remover dados empresariais do estado visível antes de outra pessoa usar o aplicativo.
- **FR-024**: CNPJ completo, credenciais, identificadores internos e detalhes técnicos de falhas
  MUST NOT aparecer em logs ou mensagens apresentadas à pessoa.
- **FR-025**: O fluxo MUST funcionar em telas Android compactas e em modo paisagem, manter todos os
  campos e ações alcançáveis com teclado aberto e suportar ampliação de fonte sem truncar dados.

### Key Entities

- **Conta autenticada**: Identidade que inicia o cadastro e cujo identificador também identifica a
  empresa criada neste fluxo.
- **Empresa prestadora (`provider`)**: Representa a empresa que oferece serviços, com nome,
  descrição, e-mail, telefone e CNPJ; pertence a exatamente uma conta autenticada neste fluxo.
- **Endereço (`address`)**: Local da empresa, com rua, número, bairro, CEP, cidade, estado, país,
  complemento e coordenadas opcionais; pertence à empresa prestadora.
- **Categoria de serviço (`service_category`)**: Classificação previamente existente identificada
  por nome e escolhida no cadastro; uma categoria pode classificar vários serviços.
- **Serviço (`service`)**: Definição global previamente existente do que é oferecido, com nome,
  descrição e uma categoria; este fluxo apenas consulta e seleciona serviços.
- **Oferta da empresa (`provider_service`)**: Associação entre empresa e serviço contendo preço,
  duração em minutos e descrição comercial; preço zero representa gratuidade escolhida
  explicitamente e a mesma combinação empresa-serviço não pode se repetir.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Pelo menos 90% das pessoas que possuem todos os dados necessários conseguem concluir
  o cadastro válido de empresa e oferta em até 5 minutos na primeira tentativa.
- **SC-002**: Em 100% dos cenários de sucesso, empresa, endereço e ofertas ficam associados à mesma
  conta autenticada e nenhuma relação contém identificador informado manualmente pela pessoa.
- **SC-003**: Em 100% das falhas simuladas entre etapas, o resultado contém todos os registros
  esperados ou nenhum registro novo, sem cadastros parciais.
- **SC-004**: Em 100% dos testes de toque repetido ou resposta perdida, uma confirmação lógica não
  produz empresas ou associações duplicadas.
- **SC-005**: Em 100% das ofertas configuradas, o serviço existente, sua categoria, o preço e a
  duração exibidos após a conclusão correspondem aos valores confirmados no formulário.
- **SC-006**: Todos os erros de campo testados apresentam orientação específica e tornam o primeiro
  campo inválido alcançável sem a pessoa reiniciar o cadastro.
- **SC-007**: Em telefone compacto, paisagem e fonte ampliada a 200%, 100% dos campos e ações
  permanecem legíveis e alcançáveis com o teclado aberto.
- **SC-008**: Nenhum teste de sucesso ou falha expõe CNPJ completo, credenciais, identificadores
  internos ou detalhes técnicos em mensagens e registros de diagnóstico destinados ao usuário.

## Assumptions

- A autenticação e a sessão central já existentes serão reutilizadas; criar ou autenticar contas
  não faz parte desta feature.
- O e-mail da conta é apenas o valor inicial do contato comercial; alterar esse campo não altera o
  e-mail usado para autenticação.
- O cadastro inicial cria um único endereço para a empresa. Gerenciar múltiplos endereços ou editar
  endereços depois fica fora do escopo.
- As categorias já existem e são administradas fora deste fluxo; criar, renomear, ordenar ou
  excluir categorias não faz parte desta feature.
- Os serviços também já existem, pertencem a exatamente uma categoria e são administrados fora
  deste fluxo; criar ou editar serviços não faz parte desta feature.
- A empresa usa o mesmo identificador da conta autenticada. Não será criado um segundo campo de
  proprietário para a empresa neste fluxo.
- O cadastro exige pelo menos uma oferta e permite adicionar ou remover vários serviços antes da
- O aplicativo não limita a quantidade de ofertas no cadastro inicial; o planejamento deve manter
  o formulário utilizável mesmo quando a lista crescer.
- Valores de preço são expressos em reais brasileiros. Preço zero representa uma oferta gratuita
  somente após escolha explícita no formulário.
- O nome físico existente `service_categories` é a fonte das categorias. O contrato atual do campo
  de bairro usa a grafia `neighbornhood`; o planejamento deverá reconciliar essa grafia sem perder
  dados ou criar dois campos equivalentes.
- Complemento, latitude e longitude são opcionais. Não haverá geocodificação, mapa, busca de CEP ou
  solicitação de localização nesta etapa.
- O país é fixo como Brasil e não é alterável neste fluxo, coerente com CNPJ, CEP e unidades
  federativas brasileiras.
- O número do endereço aceita texto curto para comportar valores como `s/n`, bloco ou lote.
- Aprovação administrativa, verificação documental, publicação, agenda, disponibilidade, imagens,
  cobrança e edição posterior da empresa ficam fora do escopo.
- O salvamento do conjunto é atômico e protegido contra repetição, pois registros parciais ou
  duplicados impediriam uma recuperação segura após falha de conexão.
