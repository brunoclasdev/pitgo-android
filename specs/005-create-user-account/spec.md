# Feature Specification: Criar conta de usuário

**Feature Branch**: `005-create-user-account`

**Created**: 2026-08-19

**Status**: Draft

**Input**: User description: "Implementar a criação de conta de user com tela de cadastro, conta
autenticada vinculada à tabela profiles e os campos nome, e-mail, telefone, CPF, CNPJ e active;
nome, e-mail e telefone obrigatórios, com CPF ou CNPJ obrigatório sem exigir ambos."

## Clarifications

### Session 2026-08-19

- Q: Qual política mínima de senha deve ser aplicada no cadastro? → A: Mínimo de 8 caracteres,
  sem exigir maiúscula, número ou símbolo.
- Q: Se a identidade de autenticação for criada, mas o perfil em `profiles` falhar, como o
  cadastro deve se recuperar? → A: Recuperar o perfil automaticamente e de forma idempotente,
  bloqueando a Home até a consistência e permitindo nova tentativa segura.
- Q: Quem poderá criar e consultar um registro da tabela `profiles` pelo aplicativo? → A: Cada
  usuário autenticado pode criar e consultar somente o próprio perfil, vinculado ao seu
  identificador.
- Q: O cadastro deve aceitar CPF e CNPJ preenchidos ao mesmo tempo? → A: Exigir uma escolha prévia
  entre pessoa física e jurídica e mostrar somente o documento correspondente.
- Q: Quando a pessoa escolher “pessoa jurídica”, o que o campo `Name` deve representar? → A: O
  campo varia: nome completo para pessoa física e razão social/nome da empresa para pessoa
  jurídica.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar uma conta com perfil (Priority: P1)

Como novo usuário, quero informar meus dados de identificação e acesso em uma tela de cadastro
para criar minha conta e possuir um perfil associado no PitGo.

**Why this priority**: A criação de conta é a entrada necessária para identificar o usuário e
habilitar os fluxos personalizados do aplicativo.

**Independent Test**: Pode ser testado abrindo o cadastro a partir da tela inicial, escolhendo
pessoa física ou jurídica, preenchendo nome, e-mail, telefone, senha e o documento correspondente
e confirmando que uma única conta e um único perfil associado são criados.

**Acceptance Scenarios**:

1. **Given** que a pessoa está na tela inicial e ainda não possui conta, **When** toca em `Criar
   conta`, **Then** a tela de cadastro é aberta com a escolha entre pessoa física e jurídica, os
   demais campos e uma ação clara de envio.
2. **Given** que a pessoa escolheu pessoa física e informou nome completo, e-mail, telefone, senha
   e CPF válidos, **When** envia o cadastro,
   **Then** a conta é criada com um perfil associado contendo os dados informados.
3. **Given** que a pessoa escolheu pessoa jurídica e informou razão social ou nome da empresa,
   e-mail, telefone, senha e CNPJ válidos, **When** envia o cadastro, **Then** a conta é criada
   normalmente com um perfil associado.
4. **Given** que a conta foi criada, **When** seus dados internos são consultados, **Then** existe
   exatamente um perfil em `profiles` vinculado pelo mesmo identificador único da conta
   autenticada.
5. **Given** que o cadastro foi concluído com sucesso, **When** o resultado é apresentado,
   **Then** a sessão da nova conta é iniciada e a pessoa é direcionada imediatamente para a Home,
   sem precisar confirmar o e-mail antes desse primeiro acesso.

---

### User Story 2 - Corrigir dados inválidos antes do envio (Priority: P2)

Como pessoa em cadastro, quero entender quais dados estão ausentes ou inválidos para corrigi-los
sem criar uma conta incompleta.

**Why this priority**: Validação clara reduz frustração, evita dados inconsistentes e impede que
identificadores pessoais inválidos sejam persistidos.

**Independent Test**: Pode ser testado tentando enviar combinações ausentes ou inválidas e
confirmando mensagens junto aos campos, preservação do conteúdo editável e ausência de criação de
conta ou perfil.

**Acceptance Scenarios**:

1. **Given** que nome, e-mail ou telefone está vazio, **When** a pessoa tenta enviar, **Then** cada
   campo obrigatório pendente é indicado e nenhum cadastro é criado.
2. **Given** que o tipo de pessoa não foi escolhido, **When** a pessoa tenta enviar, **Then** é
   solicitado que escolha pessoa física ou jurídica e nenhum cadastro é criado.
3. **Given** que pessoa física ou jurídica foi escolhida, mas o documento correspondente está
   vazio, **When** a pessoa tenta enviar, **Then** CPF ou CNPJ, respectivamente, é indicado como
   obrigatório.
4. **Given** que um documento preenchido, e-mail, telefone, senha ou confirmação está inválido,
   **When** a pessoa tenta enviar, **Then** o campo é destacado com orientação compreensível e o
   cadastro não é iniciado.
5. **Given** que e-mail, CPF ou CNPJ já pertence a outra conta, **When** a pessoa envia dados
   válidos, **Then** recebe uma mensagem acionável sem exposição de dados da conta existente.

---

### User Story 3 - Receber feedback seguro do cadastro (Priority: P3)

Como pessoa em cadastro, quero acompanhar o processamento e receber um resultado claro para saber
se devo prosseguir, aguardar ou tentar novamente.

**Why this priority**: Feedback e consistência evitam envios repetidos, contas duplicadas e estados
em que existe uma identidade sem perfil utilizável.

**Independent Test**: Pode ser testado simulando processamento, toque repetido, falta de internet,
falha do serviço e falha ao criar o perfil, confirmando estado visual coerente e integridade entre
conta e perfil.

**Acceptance Scenarios**:

1. **Given** que o cadastro válido está sendo processado, **When** a pessoa toca novamente na ação
   principal, **Then** nenhum segundo envio é aceito e o progresso permanece visível.
2. **Given** que não há internet ou o serviço está indisponível, **When** a criação falha, **Then**
   a pessoa recebe uma mensagem adequada, mantém os campos não sensíveis e pode tentar novamente.
3. **Given** que a identidade é aceita mas o perfil não pode ser concluído, **When** a operação
   termina, **Then** o sistema bloqueia o acesso à Home, tenta recuperar o perfil automaticamente
   e permite repetir a operação sem criar outra identidade ou outro perfil.
4. **Given** que o cadastro termina com sucesso, **When** o resultado é apresentado, **Then** a
   sessão autenticada é iniciada e a Home é aberta sem uma confirmação prévia de e-mail.
5. **Given** duas contas autenticadas distintas, **When** uma delas tenta criar ou consultar o
   perfil vinculado à outra, **Then** a operação é negada sem revelar os dados do outro perfil.

### Edge Cases

- Nome, e-mail, telefone, CPF e CNPJ com espaços externos devem ser normalizados antes da
  validação, sem alterar conteúdo significativo.
- CPF e CNPJ com ou sem máscara devem representar o mesmo documento para validação e unicidade.
- Ao alternar de pessoa física para jurídica, ou vice-versa, somente o documento correspondente ao
  tipo escolhido pode ser enviado ou armazenado; o outro documento deve permanecer ausente.
- Ao alternar o tipo de pessoa, o campo `Name` deve adaptar seu rótulo e sua orientação sem criar
  um campo adicional no perfil.
- E-mail deve ser comparado sem diferenciar maiúsculas de minúsculas para evitar duplicidade.
- Toques rápidos repetidos, recomposição ou rotação durante o envio não podem criar duas contas.
- Ao perder conectividade durante o envio, o resultado deve ser determinado antes de permitir nova
  tentativa, evitando duplicidade silenciosa.
- Senha, CPF e CNPJ completos não podem aparecer em logs, diagnósticos técnicos ou mensagens.
- Falha entre a criação da identidade e do perfil não pode produzir perfil sem identidade nem
  conta autenticável sem perfil válido.
- Uma nova tentativa após falha parcial deve retomar a criação do perfil da identidade existente,
  sem rejeitar o e-mail como duplicado nem criar registros adicionais.
- Uma sessão autenticada não pode criar ou consultar um perfil cujo identificador pertença a outra
  conta, mesmo que tente informar esse identificador diretamente.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST disponibilizar uma tela de criação de conta acessível pela ação
  `Criar conta` da tela inicial.
- **FR-002**: A tela MUST permitir escolher entre pessoa física e pessoa jurídica e informar
  `Name`, e-mail, telefone, o documento correspondente, senha e confirmação de senha; `Name` MUST
  ser apresentado como nome completo para pessoa física e razão social/nome da empresa para pessoa
  jurídica.
- **FR-003**: Tipo de pessoa, nome, e-mail, telefone, senha e confirmação MUST ser obrigatórios.
- **FR-004**: Ao escolher pessoa física, a tela MUST mostrar e exigir somente CPF; ao escolher
  pessoa jurídica, MUST mostrar e exigir somente CNPJ.
- **FR-005**: Cada cadastro MUST enviar e armazenar exatamente um documento correspondente ao tipo
  escolhido, mantendo o outro documento ausente.
- **FR-006**: E-mail, telefone, CPF e CNPJ preenchidos MUST ser validados localmente quanto a
  formato e, para documentos, dígitos verificadores aplicáveis.
- **FR-007**: A senha MUST possuir no mínimo 8 caracteres, sem exigir maiúscula, número ou símbolo;
  essa regra MUST ser apresentada na tela e a confirmação MUST ser idêntica antes do envio.
- **FR-008**: O sistema MUST normalizar espaços externos, máscaras de telefone/documento e caixa do
  e-mail sem alterar o valor lógico informado.
- **FR-009**: A tela MUST mostrar erros junto aos campos relevantes, preservar dados não sensíveis
  e direcionar foco ao primeiro erro ao tentar enviar um formulário inválido.
- **FR-010**: Formulários inválidos MUST NOT criar identidade autenticada nem perfil.
- **FR-011**: Cada cadastro bem-sucedido MUST criar exatamente uma identidade autenticada e
  exatamente um registro correspondente no conjunto `profiles`.
- **FR-012**: O perfil MUST usar como identificador o mesmo identificador único da identidade
  autenticada, mantendo uma relação obrigatória de um para um.
- **FR-013**: O perfil MUST armazenar nome, e-mail, telefone, exatamente um entre CPF e CNPJ e
  `active`; `active` MUST representar o status ativo em formato booleano, ser definido pelo
  sistema como verdadeiro na criação e não ser editável pela pessoa no formulário.
- **FR-014**: Após um cadastro bem-sucedido, o sistema MUST iniciar a sessão da nova conta e abrir
  a Home imediatamente, sem exigir confirmação prévia do e-mail.
- **FR-015**: E-mail, CPF e CNPJ, quando presentes, MUST ser únicos entre contas, considerando
  valores normalizados.
- **FR-016**: O envio MUST possuir estados explícitos de edição, processamento, sucesso e falha;
  durante o processamento, novos envios MUST ser bloqueados.
- **FR-017**: O sistema MUST distinguir e apresentar mensagens apropriadas para formulário
  inválido, e-mail/documento já utilizado, ausência de internet, falha do serviço, falha ao criar o
  perfil e erro desconhecido, sem expor detalhes técnicos.
- **FR-018**: A criação MUST preservar integridade entre identidade e perfil; se a identidade for
  criada antes de uma falha no perfil, o sistema MUST bloquear o acesso à Home, recuperar o perfil
  automaticamente e permitir nova tentativa idempotente, sem criar identidade ou perfil duplicado.
- **FR-019**: Pelo aplicativo, uma sessão autenticada MUST poder criar e consultar somente o perfil
  cujo identificador corresponda à própria identidade autenticada; tentativas sobre perfis de
  outras contas MUST ser negadas sem revelar seus dados.
- **FR-020**: Senhas, tokens, CPF e CNPJ completos MUST NOT ser registrados em logs nem incluídos
  em mensagens técnicas ou analíticas.
- **FR-021**: A interface MUST permitir voltar sem criar conta e MUST solicitar confirmação antes
  de descartar dados já preenchidos.
- **FR-022**: A tela MUST funcionar com teclado, rolagem, diferentes tamanhos de smartphone,
  aumento de fonte e tecnologias assistivas sem esconder campos, erros ou a ação principal.
- **FR-023**: A feature MUST NOT incluir login, recuperação de senha, login social, edição de
  perfil, onboarding de prestador/empresa além da escolha do tipo de pessoa ou coleta de documentos
  anexos.

### Key Entities

- **Identidade autenticada**: Conta usada para autenticação; possui identificador único, e-mail e
  credencial de acesso. Sua senha não pertence ao perfil.
- **Perfil (`profiles`)**: Dados cadastrais vinculados em relação um-para-um à identidade pelo mesmo
  identificador; contém nome, e-mail, telefone, exatamente um entre CPF e CNPJ e `active`, um
  status booleano administrado pelo sistema e iniciado como verdadeiro. O valor de nome representa
  nome completo em perfis de pessoa física e razão social/nome da empresa em perfis de pessoa
  jurídica.
- **Tipo de pessoa**: Classificação obrigatória entre pessoa física e pessoa jurídica que determina
  se o formulário solicita CPF ou CNPJ; não cria um campo adicional no perfil, pois o tipo é
  representado pelo documento preenchido.
- **Documento fiscal**: CPF para pessoa física ou CNPJ para pessoa jurídica, normalizado, válido e
  único entre contas; cada perfil contém exatamente um documento.
- **Rascunho de cadastro**: Estado temporário editável do formulário; inclui valores, erros e estado
  de envio, mas não persiste senha nem cria conta antes da validação.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pessoa com dados válidos consegue concluir o formulário de criação de conta em
  até 3 minutos em um smartphone suportado.
- **SC-002**: Em 100% dos cenários avaliados, formulários sem tipo de pessoa, nome, e-mail,
  telefone, senha ou sem o documento correspondente são bloqueados antes da criação de qualquer
  registro.
- **SC-003**: Em 100% dos cadastros concluídos, existe exatamente um perfil associado ao mesmo
  identificador da identidade autenticada.
- **SC-004**: Em 100% das falhas simuladas entre identidade e perfil, a Home permanece bloqueada e
  uma nova tentativa completa o mesmo cadastro sem deixar conta utilizável incompleta, perfil
  órfão ou registros duplicados.
- **SC-005**: A interface apresenta feedback visual de processamento em até 1 segundo após um envio
  válido e aceita no máximo uma criação por tentativa, mesmo com toques repetidos.
- **SC-006**: Mensagens de validação permitem identificar todos os campos que precisam de correção
  sem revelar se outro usuário possui dados pessoais além da informação de indisponibilidade.
- **SC-007**: A inspeção de logs e mensagens encontra zero ocorrência de senha, token, CPF completo
  ou CNPJ completo usados durante o cadastro.
- **SC-008**: O fluxo completo permanece operável em smartphones Android suportados com fonte em
  200%, teclado aberto e leitor de tela ativo.
- **SC-009**: Em 100% dos cadastros concluídos com sucesso, a nova sessão está autenticada e a Home
  é apresentada sem uma etapa prévia de confirmação de e-mail.
- **SC-010**: Em 100% das tentativas avaliadas de criar ou consultar um perfil pertencente a outra
  identidade, o acesso é negado e nenhum dado pessoal do outro perfil é retornado.

## Assumptions

- O cadastro usa e-mail e senha, com confirmação de senha na tela; login por código ou provedor
  social permanece fora desta feature.
- CPF e CNPJ seguem regras brasileiras, aceitam entrada com ou sem máscara e são armazenados em
  forma normalizada.
- O requisito “CPF ou CNPJ” significa exatamente um documento, determinado pela escolha obrigatória
  entre pessoa física e pessoa jurídica.
- Telefone segue formato brasileiro com DDD e é armazenado normalizado, mas não é necessariamente
  único entre contas.
- A infraestrutura de autenticação e conexão remota configurada na feature anterior está
  disponível; esta feature adicionará somente as capacidades necessárias para cadastro e perfil.
- A configuração de autenticação do ambiente permitirá iniciar a sessão imediatamente após o
  cadastro, pois a confirmação prévia do e-mail não faz parte deste fluxo.
- O nome `active` representa exclusivamente
  o status ativo da conta, começa como verdadeiro e não é um campo visível do formulário.
- A configuração do serviço de autenticação será alinhada ao mínimo de 8 caracteres definido
  nesta feature, sem acrescentar regras de composição não apresentadas na tela.
- Nenhum teste automatizado será criado sem solicitação explícita, conforme a constituição do
  projeto; os cenários desta especificação continuam sendo critérios verificáveis.
