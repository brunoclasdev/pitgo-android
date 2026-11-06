# Feature Specification: Cadastro em duas etapas e validação documental

**Feature Branch**: `develop` (branch existente; sem hook de criação de branch)

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "No cadastro de cliente e provider, validar fotos da habilitação frente e verso, selfie e selfie com documento na mão. Primeiro cadastro simples com nome, sobrenome, email, telefone, CPF, senha e confirmação; permitir login sem uso das funcionalidades. Segunda etapa com informações restantes; manter cadastro inativo e em análise até validação e ativação pelo time PitGo. Disponibilizar informações e ativação pelo dashboard e preparar a base de dados."

## Clarifications

### Session 2026-11-05

- Q: Um cliente já aprovado que depois se cadastrar como prestador deverá passar por nova análise? → A: Sim. Exigir análise como prestador, mantendo o acesso já aprovado como cliente.
- Q: A foto do documento do veículo deve ser solicitada a quem? → A: Aos prestadores que cadastram veículos de atendimento, com uma foto do documento para cada veículo cadastrado; clientes não precisam enviá-la.
- Q: As contas de clientes e prestadores que já existem também deverão enviar documentos e passar pela aprovação? → A: Não. A validação documental será obrigatória somente para novas contas; as existentes manterão sua situação atual.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar conta e entrar com acesso restrito (Priority: P1)

Como novo cliente ou prestador, quero criar minha conta com poucos dados e entrar para completar meu cadastro, sabendo que o uso do marketplace depende de aprovação.

**Why this priority**: Permite iniciar e retomar o cadastro sem liberar contas não verificadas.

**Independent Test**: Criar uma conta de cada perfil, sair e entrar novamente; verificar persistência dos dados e acesso apenas ao cadastro e ao acompanhamento da aprovação.

**Acceptance Scenarios**:

1. **Given** um novo cliente ou prestador, **When** inicia a primeira etapa, **Then** são solicitados apenas nome, sobrenome, e-mail, telefone, documento (CNPJ para prestador; CPF ou CNPJ para cliente), senha e confirmação de senha como dados de cadastro.
2. **Given** dados válidos e senhas iguais, **When** conclui essa etapa, **Then** uma única conta é criada, permanece inativa para o marketplace e permite login para continuar o cadastro.
3. **Given** dados inválidos, CPF/CNPJ ou e-mail já cadastrado ou senhas diferentes, **When** tenta concluir, **Then** recebe orientação para corrigir ou recuperar o acesso, sem criar conta duplicada nem expor dados de terceiros.
4. **Given** uma conta ainda não aprovada, **When** entra, reabre o aplicativo, usa uma sessão anterior ou tenta abrir diretamente uma funcionalidade, **Then** encontra o estado do cadastro e não consegue contratar, pagar, atender pedidos ou operar o marketplace.

5. **Given** conta incompleta, em análise ou aprovada, **When** solicita recuperação de senha por e-mail e conclui a troca pelo retorno no aplicativo, **Then** consegue entrar com a nova senha mantendo cadastro, documentos, aprovação e bloqueios anteriores; link inválido, expirado ou reutilizado não altera a senha nem libera funcionalidades.

---

### User Story 2 - Completar dados e enviar documentos (Priority: P1)

Como cliente ou prestador, quero completar as informações da minha modalidade e enviar minhas fotos para análise pelo time PitGo.

**Why this priority**: Reúne as evidências e os dados necessários para decidir a ativação.

**Independent Test**: Completar separadamente um cadastro de cliente e um de prestador, enviar todas as fotos obrigatórias de cada modalidade e confirmar que ambos permanecem inativos e em análise após novo login.

**Acceptance Scenarios**:

1. **Given** a primeira etapa concluída, **When** acessa a segunda, **Then** informa os demais dados obrigatórios do fluxo vigente de cliente ou prestador, reaproveitando os dados iniciais sem solicitar nova senha.
2. **Given** o envio documental, **When** completa as evidências, **Then** cada foto tem posição identificada: CNH frente, CNH verso, selfie e selfie segurando o documento com rosto e documento visíveis; prestadores com veículos de atendimento também enviam uma foto do documento de cada veículo cadastrado.
3. **Given** informação obrigatória ou foto ausente ou envio com falha, **When** tenta submeter, **Then** o cadastro não entra em análise, indica a pendência e permite tentar novamente sem duplicar a conta nem perder dados já confirmados.
4. **Given** todos os dados e fotos obrigatórias recebidos, **When** confirma o envio, **Then** o cadastro passa para em análise, permanece inativo e informa que a liberação depende da equipe PitGo.
5. **Given** cadastro interrompido, **When** entra em outro momento ou dispositivo, **Then** retoma os dados e fotos já confirmados e identifica o que falta enviar.

---

### User Story 3 - Revisar e ativar pelo dashboard (Priority: P1)

Como integrante autorizado do time PitGo, quero consultar cadastros, conferir os documentos e decidir a aprovação para liberar somente usuários validados.

**Why this priority**: A revisão humana é a condição obrigatória para ativar o cadastro.

**Independent Test**: Com um cadastro em análise, consultar suas informações como revisor, conferir todas as fotos obrigatórias e aprovar; verificar a liberação no próximo acesso e o registro da decisão. Repetir uma tentativa com usuário sem permissão e confirmar a recusa.

**Acceptance Scenarios**:

1. **Given** cadastros submetidos, **When** o dashboard consulta a fila como revisor autorizado, **Then** recebe identificação, modalidade, situação, data de envio, informações completas e acesso às evidências pessoais e, quando aplicável, aos documentos dos veículos do cadastro selecionado.
2. **Given** um cadastro em análise com todos os dados e documentos conferidos, **When** o revisor aprova e confirma a ativação, **Then** aprovação e ativação são registradas juntas com responsável e data, liberando as funcionalidades da modalidade.
3. **Given** um cadastro incompleto ou evidências ainda não validadas, **When** alguém tenta ativá-lo, **Then** a operação é recusada e o cadastro permanece inativo.
4. **Given** usuário comum ou integrante sem permissão de revisão, **When** tenta consultar documentos alheios, registrar decisão ou alterar a própria ativação, **Then** o acesso é negado e nenhum estado é alterado.
5. **Given** decisão anterior ou análise feita sobre uma versão antiga, **When** outro revisor tenta decidir sobre o mesmo envio, **Then** não sobrescreve silenciosamente a decisão ou os documentos atuais e deve atualizar os dados antes de prosseguir.

6. **Given** cliente já aprovado iniciando cadastro como prestador, **When** a modalidade de prestador está incompleta, em análise ou com correção solicitada, **Then** ele continua contratando como cliente, mas não pode oferecer ou atender serviços até aprovação específica como prestador.

---

### User Story 4 - Corrigir pendências e acompanhar o resultado (Priority: P2)

Como pessoa com documentos recusados, quero entender o motivo e corrigir o cadastro para solicitar nova análise sem criar outra conta.

**Why this priority**: Evita cadastros abandonados por fotos ilegíveis ou informações divergentes.

**Independent Test**: Devolver um cadastro com motivo, consultar a pendência no aplicativo, substituir a foto indicada e reenviar; confirmar novo envio vinculado à mesma conta e histórico preservado.

**Acceptance Scenarios**:

1. **Given** documento ilegível, divergente ou inadequado, **When** o revisor solicita correção, **Then** informa um motivo e os itens pendentes; o usuário vê a orientação e permanece inativo.
2. **Given** pendências corrigidas, **When** o usuário reenvia o cadastro completo, **Then** um novo envio entra em análise sem reutilizar aprovação do envio anterior.
3. **Given** um cadastro em análise, **When** o usuário consulta o andamento, **Then** vê que deve aguardar e não pode substituir evidências sem passar por um novo envio controlado.
4. **Given** aprovação concluída, **When** o usuário atualiza o estado ou entra novamente com conexão disponível, **Then** acessa o aplicativo conforme a modalidade aprovada sem repetir o cadastro.

### Edge Cases

- Falha entre criação da identidade e salvamento do cadastro: recuperar a mesma conta com segurança, mantendo acesso restrito até consistência dos dados.
- Upload interrompido, arquivo corrompido ou formato não aceito: indicar qual foto falhou; permitir novo envio sem considerar a evidência recebida antes de sua confirmação.
- Duplo toque, repetição após timeout e pedidos concorrentes: não duplicar conta, submissão, decisão ou ativação.
- Documento da conta divergente dos dados cadastrais ou documento de outra pessoa: devolver para correção com motivo; nunca aprovar automaticamente por existir uma foto.
- Permissão de câmera negada: explicar como habilitar e permitir retomar a etapa sem perder o progresso confirmado.
- Ausência de conexão ou falha ao consultar a situação: não presumir aprovação nem liberar ações; apresentar erro e opção de tentar novamente.
- Cadastro de prestador aprovado em análise documental, mas com outras restrições operacionais: a aprovação não remove bloqueios independentes já existentes.
- Contas existentes antes da implantação: preservar sua situação atual; novas contas seguem obrigatoriamente as duas etapas, sem aprovação retroativa implícita de pendências existentes.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A primeira etapa DEVE solicitar somente nome, sobrenome, e-mail, telefone, documento (CNPJ para prestador; CPF ou CNPJ para cliente), senha e confirmação, tanto para cliente quanto para prestador; seleção de modalidade não deve exigir dados complementares nessa etapa.
- **FR-002**: O sistema DEVE validar preenchimento, formato dos contatos, validade do documento conforme a modalidade, unicidade de CPF/CNPJ e e-mail e igualdade das senhas, mantendo a política vigente de senha mínima de oito caracteres. A confirmação da senha não deve integrar os dados cadastrais persistentes.
- **FR-003**: Concluir a primeira etapa DEVE criar uma conta autenticável vinculada ao próprio cadastro, com estado cadastro incompleto e sem ativação para o marketplace.
- **FR-004**: Contas sem nenhuma modalidade aprovada DEVEM poder entrar, sair, recuperar acesso, completar cadastro e consultar resultado ou pendências; as demais funcionalidades de negócio DEVEM permanecer bloqueadas. Quando houver modalidade já aprovada, o bloqueio DEVE atingir somente as funcionalidades da modalidade ainda não aprovada.
- **FR-005**: O bloqueio DEVE valer para todas as formas de acesso às operações de negócio, incluindo sessões já abertas, navegação direta e tentativas fora da interface normal. Estar autenticado não equivale a estar aprovado.
- **FR-006**: A segunda etapa DEVE manter os dados complementares obrigatórios vigentes de cada modalidade, incluindo endereço e, quando aplicável, informações empresariais, de serviços e veículos; dados da primeira etapa devem ser reaproveitados.
- **FR-007**: Clientes e prestadores DEVEM enviar quatro evidências pessoais obrigatórias distintas: foto da frente da CNH, foto do verso da CNH, selfie e selfie com o documento na mão. A interface DEVE orientar enquadramento e legibilidade e permitir revisar cada imagem antes de confirmar.
- **FR-008**: O sistema DEVE permitir salvar e retomar dados e evidências já recebidos, substituir fotos antes da submissão e identificar envios em andamento ou com erro. Somente recebimentos confirmados contam como documentos entregues.
- **FR-009**: Submissão para análise DEVE exigir dados completos e quatro evidências pessoais confirmadas vinculadas ao titular e, para prestadores com veículos de atendimento, uma foto confirmada do documento de cada veículo cadastrado. Receber uma imagem não representa validar sua identidade ou autenticidade.
- **FR-010**: O cadastro DEVE representar, separadamente para cada modalidade, os estados cadastro incompleto, em análise, correção solicitada e aprovado. Somente aprovado permite ativação para uso, sem dispensar outras condições operacionais existentes.
- **FR-011**: As transições permitidas DEVEM ser: cadastro incompleto para em análise; em análise para correção solicitada ou aprovado; correção solicitada para em análise mediante reenvio completo. Login, conclusão de formulário e envio de fotos nunca devem ativar automaticamente a conta.
- **FR-012**: A preparação da base de dados DEVE persistir titular, modalidade, dados básicos e complementares, estado e ativação por modalidade, evidências por tipo e versão, veículo correspondente quando aplicável, datas de envio, análise e ativação, pendências, motivos e responsável pela decisão, com vínculos que impeçam associação a outra conta.
- **FR-013**: As informações para o dashboard DEVEM permitir listar e filtrar cadastros por situação e modalidade, consultar o detalhe completo e acessar as imagens de cada envio sob autorização de revisão.
- **FR-014**: O time PitGo autorizado DEVE poder conferir cada evidência e registrar aprovação ou solicitação de correção. Aprovar DEVE exigir validação explícita de todas as evidências obrigatórias, incluindo documentos dos veículos quando aplicável, e dos dados completos; solicitar correção DEVE exigir motivo e identificação dos itens pendentes.
- **FR-015**: Aprovação e ativação DEVEM ocorrer como uma decisão consistente: nenhuma modalidade sujeita ao novo fluxo pode ficar ativa sem aprovação válida do seu envio atual. A análise de uma modalidade não deve alterar a ativação já aprovada de outra. O usuário não pode editar sua aprovação, ativação, resultado da revisão ou identidade do revisor.
- **FR-016**: O sistema DEVE preservar histórico de submissões e decisões com responsável, data, resultado e motivo quando houver, distinguindo as evidências avaliadas de suas substituições posteriores. Uma decisão sobre envio antigo não pode aprovar novas evidências.
- **FR-017**: Evidências e dados pessoais DEVEM ser acessíveis somente ao titular para o seu cadastro e aos revisores autorizados para análise; outros clientes e prestadores não podem consultar imagens, obter acesso público a elas ou alterá-las. Senhas e documentos não devem aparecer em registros de diagnóstico.
- **FR-018**: O usuário DEVE consultar no aplicativo a situação atual e os motivos de correção, reenviar os itens pendentes mantendo a mesma conta e continuar bloqueado na modalidade em análise até aprovação do novo envio, preservando o acesso de outra modalidade já aprovada.
- **FR-019**: Reenvios de operações e decisões concorrentes DEVEM preservar uma única situação atual por modalidade, sem duplicar cadastros ou substituir silenciosamente revisão mais recente.
- **FR-020**: A validação documental DEVE ser obrigatória somente para contas criadas após a entrada em vigor do novo fluxo. Contas de clientes e prestadores já existentes DEVEM manter seus vínculos e sua situação atual nas modalidades já cadastradas, sem exigência retroativa de envio documental ou nova aprovação por esta feature; uma nova modalidade de prestador solicitada após a entrada em vigor do fluxo DEVE passar pela análise prevista em FR-022; uma conta anteriormente inativa não deve ser ativada automaticamente.
- **FR-021**: Prestadores que cadastram veículos de atendimento DEVEM enviar uma foto legível do documento de cada veículo na segunda etapa. Cada evidência DEVE estar vinculada ao veículo correspondente e integrar a submissão para análise. A ausência ou recusa de qualquer documento veicular obrigatório DEVE impedir a aprovação e a ativação como prestador; clientes não estão sujeitos a essa exigência.
- **FR-022**: Um cliente já aprovado que solicitar cadastro como prestador DEVE completar os dados e evidências obrigatórios dessa modalidade e passar por nova análise do time PitGo antes de oferecer serviços. Durante cadastro incompleto, análise ou correção como prestador, o acesso já aprovado como cliente DEVE permanecer disponível; aprovação como cliente não substitui aprovação como prestador.

### Key Entities *(include if feature involves data)*

- **Conta e cadastro**: Titular, nome, sobrenome, e-mail, telefone, CPF ou CNPJ conforme modalidade, modalidade, dados complementares, etapa atual, situação documental e ativação separadas por modalidade; credenciais são tratadas separadamente das informações exibidas ao revisor.
- **Submissão de verificação**: Conjunto versionado de dados e evidências enviado pelo titular, com data de submissão e situação; pertence a uma única conta e identifica a modalidade avaliada.
- **Evidência documental**: Arquivo privado recebido, tipo de foto, titular, veículo correspondente quando aplicável e confirmação de recebimento; vinculado explicitamente a cada versão que o utiliza. Fotos mantidas numa correção podem integrar uma nova submissão sem alterar o arquivo nem os vínculos do envio anterior.
- **Decisão de análise**: Submissão analisada, revisor autorizado, conferência de todas as evidências obrigatórias, resultado, motivos e itens de correção, data da decisão e ativação quando aprovada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nos cenários de cliente e prestador, 100% das contas criadas com os sete campos solicitados na primeira etapa conseguem entrar e retomar a segunda sem preencher dados complementares antecipadamente.
- **SC-002**: 100% das tentativas de uso das funcionalidades de uma modalidade sujeita ao novo fluxo ainda incompleta, em análise ou com correção solicitada são bloqueadas, inclusive por acesso direto e sessão anterior, preservando o acesso de outra modalidade já aprovada.
- **SC-003**: 100% dos cadastros submetidos apresentam dados obrigatórios completos e todas as evidências obrigatórias correspondentes disponíveis para o revisor autorizado.
- **SC-004**: 100% das ativações de novas contas têm aprovação explícita do envio atual, todas as evidências obrigatórias conferidas, responsável e data rastreáveis; nenhuma é provocada apenas pelo login ou upload.
- **SC-005**: Em uma rodada de validação com dez participantes distribuídos entre cliente e prestador, pelo menos nove completam a primeira etapa em até três minutos e identificam corretamente a próxima ação e o motivo do bloqueio sem auxílio.
- **SC-006**: Em todos os cenários de correção, o usuário identifica os itens solicitados e reenvia usando a mesma conta; dados já confirmados são preservados e a análise anterior permanece consultável pelo revisor.
- **SC-007**: Com conexão disponível, o usuário aprovado visualiza a liberação ao atualizar a situação ou realizar o próximo login; todos os cenários de acesso a documentos de terceiros por pessoas não autorizadas são negados.
- **SC-008**: Em todos os cenários de cliente aprovado iniciando cadastro de prestador, contratar como cliente permanece disponível e atender como prestador fica bloqueado até a aprovação específica, inclusive após novo login e solicitação de correção.

## Assumptions

- A CNH frente e verso é o documento obrigatório para ambos os perfis conforme solicitado; alternativas de identificação pessoal para pessoas sem CNH não fazem parte desta entrega. Prestadores com veículos de atendimento também devem apresentar o documento de cada veículo. A selfie com documento usa a mesma CNH.
- Prestadores são cadastrados exclusivamente com CNPJ, sem solicitar CPF. Clientes podem usar CPF ou CNPJ. O documento é informado na primeira etapa; os demais dados empresariais pertencem à segunda etapa.
- A revisão é humana, feita pelo time PitGo; reconhecimento facial automático, leitura automática de documentos e consultas externas de identidade não fazem parte do escopo.
- A conta sem modalidade aprovada mantém acesso somente às funções de cadastro e acesso listadas em FR-004; modalidade já aprovada permanece acessível durante análise de outra. Recuperação de acesso não altera a aprovação.
- O dashboard é uma interface administrativa separada: esta feature contempla preparação dos dados, permissões e operações para consulta, análise e ativação; a construção de suas telas será tratada no projeto responsável pelo dashboard.
- Os requisitos de dados e operações de revisão integram o escopo de implementação posterior. Este documento especifica o comportamento e não executa alterações na base.
- Os demais campos obrigatórios da segunda etapa seguem as regras vigentes de cada modalidade; esta feature não adiciona novas condições comerciais ou serviços.
- Contas existentes não passam por recadastramento obrigatório nas modalidades já cadastradas nesta entrega; o ingresso posterior como prestador exige análise específica. Revogação de aprovação, mudanças de identidade após aprovação, prazos de análise, notificações externas e política nova de retenção documental ficam fora do escopo; aplicam-se as políticas de dados já adotadas pelo produto.
- A implementação depende do acesso à gestão de contas, aos cadastros atuais e às permissões administrativas do time PitGo. A disponibilidade das telas do dashboard depende do projeto administrativo.
