# Feature Specification: Corrigir cadastro de veículos e erros por campo

**Feature Branch**: `develop`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "Corrigir o cadastro de veículos, restaurar as operações ausentes no ambiente remoto, aceitar somente Marca e Modelo como obrigatórios, apresentar erros no campo correspondente e preservar segurança, estado e navegação do fluxo."

## Clarifications

### Session 2026-08-23

- Q: Depois de salvar um veículo, para onde o aplicativo deve navegar em cada origem do formulário? → A: No onboarding, navegar para a Home; ao adicionar pela Garagem, retornar à Garagem.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cadastrar veículo com os dados mínimos (Priority: P1)

Como cliente autenticado, quero cadastrar um veículo informando apenas marca e modelo para concluir o onboarding ou adicioná-lo à minha garagem sem ser bloqueado por campos opcionais.

**Why this priority**: O cadastro está indisponível no ambiente atual. Restaurar o salvamento com os dados mínimos devolve a funcionalidade principal e desbloqueia o acesso do cliente ao restante do aplicativo.

**Independent Test**: Autenticar uma conta de cliente sem veículos, informar uma marca e um modelo válidos, manter os demais campos vazios e confirmar que o veículo é salvo como principal e aparece nas áreas da conta que exibem o veículo atual.

**Acceptance Scenarios**:

1. **Given** um cliente autenticado sem veículos, **When** informa apenas marca e modelo válidos e confirma o cadastro, **Then** o veículo é salvo para essa conta e definido automaticamente como principal.
2. **Given** um cliente autenticado com veículo existente, **When** cadastra outro veículo válido sem solicitar que ele seja principal, **Then** o novo veículo é salvo e o veículo principal anterior permanece selecionado.
3. **Given** um cliente autenticado com veículo existente, **When** cadastra outro veículo e escolhe torná-lo principal, **Then** somente o novo veículo fica marcado como principal.
4. **Given** um cadastro concluído durante o onboarding, **When** o salvamento é confirmado, **Then** o onboarding é finalizado, o cliente navega para a Home e o formulário concluído não reaparece ao voltar.
5. **Given** um cadastro iniciado pela Garagem, **When** o salvamento é confirmado, **Then** o cliente retorna à Garagem, visualiza o veículo salvo e o formulário concluído não reaparece ao voltar.

---

### User Story 2 - Corrigir dados inválidos no campo certo (Priority: P2)

Como cliente, quero saber exatamente qual dado do veículo está inválido para conseguir corrigi-lo sem interpretar uma mensagem genérica.

**Why this priority**: Mensagens genéricas impedem a correção autônoma, aumentam tentativas repetidas e fazem problemas de dados parecerem indisponibilidade do sistema.

**Independent Test**: Submeter combinações inválidas para cada campo validado e confirmar que todos os problemas conhecidos aparecem junto ao campo correspondente, com o primeiro campo inválido visível e focado.

**Acceptance Scenarios**:

1. **Given** marca ou modelo vazio, **When** o cliente tenta salvar, **Then** cada campo ausente é marcado e apresenta uma mensagem específica.
2. **Given** placa, Renavam, chassi, ano ou quilometragem inválidos, **When** o cliente tenta salvar, **Then** o campo correspondente é marcado e explica o formato ou intervalo aceito.
3. **Given** vários campos inválidos, **When** o cliente tenta salvar, **Then** todos os erros são mantidos e a tela leva o cliente ao primeiro deles na ordem visual.
4. **Given** uma placa, um Renavam ou um chassi já utilizado por outro veículo, **When** o servidor recusa o cadastro, **Then** somente o identificador correspondente é marcado como indisponível.
5. **Given** um campo marcado com erro, **When** o cliente o corrige com um valor válido, **Then** o erro daquele campo desaparece sem apagar os demais valores ou erros.

---

### User Story 3 - Receber falhas gerais sem indicação incorreta de campo (Priority: P3)

Como cliente, quero distinguir problemas nos meus dados de falhas de sessão, conexão ou serviço para saber se devo corrigir o formulário, tentar novamente ou entrar novamente.

**Why this priority**: Falhas operacionais não devem responsabilizar um campo válido nem expor informações técnicas do ambiente.

**Independent Test**: Simular sessão expirada, indisponibilidade da operação remota, ausência de conexão, demora excessiva e erro inesperado, verificando que o formulário é preservado e que a mensagem orienta a ação adequada sem marcar campos.

**Acceptance Scenarios**:

1. **Given** dados válidos e ausência de conexão, **When** o cliente tenta salvar, **Then** recebe orientação para conferir a conexão e o formulário permanece preenchido.
2. **Given** dados válidos e operação de cadastro indisponível ou incompatível, **When** o cliente tenta salvar, **Then** recebe a mensagem "O cadastro de veículos está temporariamente indisponível. Tente novamente mais tarde." e nenhum campo é marcado.
3. **Given** uma sessão expirada durante o salvamento, **When** a falha é identificada, **Then** dados sensíveis não são expostos e o fluxo solicita uma nova autenticação.
4. **Given** uma tentativa em processamento, **When** o cliente toca repetidamente em salvar, **Then** somente uma solicitação é aceita até a operação terminar.

### Edge Cases

- Campos opcionais vazios ou compostos somente por espaços devem ser tratados como não informados e não podem impedir o cadastro.
- Marca e modelo com espaços externos devem ser normalizados; valores que fiquem vazios após a normalização devem ser rejeitados.
- A placa deve aceitar tanto o padrão brasileiro anterior quanto o padrão Mercosul, sem diferenciar letras minúsculas de maiúsculas.
- Renavam e chassi visualmente válidos, mas já associados a outro veículo, devem produzir erro somente no identificador conflitante.
- Ano de fabricação e ano do modelo devem acompanhar a mudança de ano civil e aceitar, no máximo, o ano corrente mais um.
- Quilometragem zero é válida; valores negativos ou não inteiros devem ser rejeitados.
- Se duas tentativas concorrentes solicitarem veículos principais, o estado final deve conter exatamente um veículo principal para o cliente.
- Uma conta empresarial não pode cadastrar veículos pessoais pelo fluxo de cliente.
- Uma tentativa de criar ou alterar veículo de outra conta deve ser recusada sem revelar se o veículo de destino existe.
- Se o salvamento remoto for confirmado, mas a atualização da tela for interrompida, a próxima abertura da Garagem deve exibir o veículo sem criar uma duplicata automaticamente.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE disponibilizar novamente as operações de cadastrar veículo e selecionar veículo principal no ambiente utilizado pelo aplicativo.
- **FR-002**: Somente uma conta autenticada classificada como cliente PODE cadastrar ou selecionar veículos pessoais.
- **FR-003**: O proprietário do veículo DEVE ser determinado pela sessão autenticada; o formulário NÃO PODE escolher nem enviar outro proprietário.
- **FR-004**: Marca e modelo DEVEM ser os únicos campos obrigatórios do cadastro.
- **FR-005**: Versão, ano de fabricação, ano do modelo, placa, Renavam, chassi, cor, combustível, câmbio, motor e quilometragem DEVEM permanecer opcionais.
- **FR-006**: Textos preenchidos DEVEM ignorar espaços externos, e textos opcionais vazios após essa normalização DEVEM ser tratados como não informados.
- **FR-007**: A placa, quando informada, DEVE aceitar os formatos brasileiros anterior e Mercosul.
- **FR-008**: O Renavam, quando informado, DEVE conter exatamente 11 dígitos.
- **FR-009**: O chassi, quando informado, DEVE conter exatamente 17 caracteres permitidos e rejeitar letras que não fazem parte do padrão aceito.
- **FR-010**: Ano de fabricação e ano do modelo, quando informados, DEVEM estar entre 1900 e o ano corrente mais um.
- **FR-011**: A quilometragem, quando informada, DEVE ser um número inteiro maior ou igual a zero.
- **FR-012**: O sistema DEVE identificar todos os campos inválidos conhecidos em uma única tentativa de validação.
- **FR-013**: Cada erro de dado DEVE marcar o campo correspondente, apresentar uma mensagem acionável junto ao campo e permanecer até que aquele valor seja corrigido.
- **FR-014**: Após uma tentativa inválida, a tela DEVE tornar visível e focar o primeiro campo com erro na ordem visual do formulário.
- **FR-015**: Erros de identificador duplicado DEVEM ser associados especificamente à placa, ao Renavam ou ao chassi responsável pelo conflito.
- **FR-016**: Falhas de contrato relacionadas especificamente a placa, Renavam, chassi, ano de fabricação, ano do modelo ou quilometragem DEVEM ser associadas ao respectivo campo.
- **FR-017**: Falhas de sessão, conexão, demora, indisponibilidade, incompatibilidade da operação remota e erros inesperados NÃO DEVEM marcar campos do formulário.
- **FR-018**: Quando a operação de cadastro estiver ausente ou incompatível, o sistema DEVE informar: "O cadastro de veículos está temporariamente indisponível. Tente novamente mais tarde."
- **FR-019**: Mensagens apresentadas ao cliente NÃO DEVEM conter detalhes internos, códigos técnicos, nomes de regras internas, payloads ou exceções.
- **FR-020**: O formulário DEVE preservar os dados preenchidos após falhas recuperáveis.
- **FR-021**: Durante o salvamento, o sistema DEVE desabilitar novas submissões, indicar processamento e aceitar no máximo uma tentativa por vez.
- **FR-022**: O primeiro veículo ativo de uma conta DEVE ser definido automaticamente como principal.
- **FR-023**: Ao selecionar um novo veículo principal, o sistema DEVE garantir que exatamente um veículo ativo permaneça como principal para aquela conta.
- **FR-024**: Um cliente NÃO PODE criar, consultar por este fluxo, selecionar ou alterar veículos pertencentes a outra conta.
- **FR-025**: Após um cadastro bem-sucedido, o veículo DEVE aparecer na Garagem e nos resumos da conta que exibem o veículo atual.
- **FR-026**: Após sucesso no onboarding, o sistema DEVE concluir essa etapa, navegar para a Home e impedir que Voltar reabra o formulário concluído.
- **FR-027**: O sistema NÃO DEVE registrar credenciais, tokens ou valores completos de placa, Renavam e chassi em registros operacionais.
- **FR-028**: Após sucesso em um cadastro iniciado pela Garagem, o sistema DEVE retornar à Garagem, atualizar a lista de veículos e impedir que Voltar reabra o formulário concluído.

### Key Entities

- **Veículo**: Bem associado a uma conta cliente. Possui marca e modelo obrigatórios; versão, anos, identificadores, características e quilometragem opcionais; também informa se está ativo e se é o veículo principal.
- **Conta cliente**: Identidade autenticada autorizada a possuir veículos pessoais. É a única origem válida para determinar o proprietário em operações do veículo.
- **Erro de campo do veículo**: Problema acionável associado a exatamente um campo, como ausência, formato, intervalo ou identificador indisponível.
- **Falha geral de cadastro**: Problema de sessão, conectividade ou disponibilidade que não invalida nenhum campo do formulário.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários válidos, um cliente autenticado consegue cadastrar seu primeiro veículo informando apenas marca e modelo.
- **SC-002**: Em 100% dos cenários de validação conhecidos, o campo responsável é marcado com uma mensagem que informa como corrigir o valor.
- **SC-003**: Quando há vários campos inválidos, o primeiro campo na ordem visual fica visível e recebe foco em até 1 segundo após a tentativa.
- **SC-004**: Em condições normais de conectividade, 95% das tentativas válidas apresentam confirmação de sucesso ou uma falha acionável em até 5 segundos.
- **SC-005**: Após qualquer sequência de cadastro ou troca de veículo principal, cada cliente mantém exatamente um veículo ativo principal quando possui ao menos um veículo.
- **SC-006**: Em 100% das falhas gerais simuladas, nenhum campo válido é marcado indevidamente e todos os valores preenchidos permanecem disponíveis para nova tentativa.
- **SC-007**: O veículo salvo aparece na Garagem e no resumo da conta na primeira atualização após o sucesso, sem exigir novo cadastro.
- **SC-008**: Tentativas de uma conta empresarial ou de uma conta diferente de manipular veículos pessoais resultam em zero alterações de dados.
- **SC-009**: Nenhuma mensagem apresentada ou registro operacional contém token, credencial, placa completa, Renavam completo, chassi completo ou detalhe técnico interno.

## Assumptions

- O aplicativo continuará usando a autenticação e a classificação de conta já existentes para distinguir clientes de empresas.
- O cadastro atual já possui os campos e a navegação necessários; esta feature corrige disponibilidade, validação, feedback e conclusão do fluxo, sem redesenhar a tela.
- A regra brasileira de placa contempla o formato anterior e o padrão Mercosul vigentes no produto.
- Validação de dígito verificador do Renavam não foi solicitada; o requisito atual limita-se a 11 dígitos.
- O limite superior de ano é calculado dinamicamente como ano corrente mais um.
- Campos opcionais não informados não representam informação vazia persistente.
- O ambiente remoto pode registrar historicamente uma versão anterior das operações sem que elas existam no estado efetivo; a disponibilidade atual deve ser verificada pelo comportamento real.
- Consulta automática por placa, integração com órgãos de trânsito, documentos e fotos permanecem fora do escopo.
- Testes automatizados não fazem parte desta entrega; a validação da feature será proporcional ao risco por compilação e fluxo manual.
