# Feature Specification: Busca e despacho de prestadores móveis

**Feature Branch**: `[013-mobile-provider-dispatch]`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "Implementar regras do cadastro de empresa móvel, manter sua localização atualizada no mapa, representar a empresa por um ícone de carro, buscar o guincho mais próximo, apresentar nome, preço e localidade, permitir consultar o próximo resultado e persistir o último status do fluxo para retomada após fechar o aplicativo."

## Clarifications

### Session 2026-08-23

- Q: Quando a localização da empresa móvel deve ser publicada? → A: Enquanto a empresa estiver marcada como “Disponível”, inclusive em segundo plano, com indicação persistente e interrupção ao ficar indisponível, sair da conta ou perder a autorização necessária.
- Q: Quem confirma definitivamente o atendimento? → A: O cliente solicita a opção apresentada e o prestador ainda precisa aceitar ou recusar; uma recusa retoma a busca.
- Q: O que acontece ao usar “Próximo” e esgotar os candidatos da região atual? → A: O sistema amplia automaticamente a região de busca e continua procurando opções mais distantes, sem repetir as já descartadas naquela busca.
- Q: Quanto tempo o prestador deve ter para aceitar ou recusar uma solicitação antes que ela expire automaticamente? → A: 60 segundos; depois a proposta expira e a busca continua para o próximo prestador.
- Q: Quando o prestador aceitar um atendimento, ele deve ficar automaticamente indisponível para receber novas solicitações até esse atendimento terminar ou ser cancelado? → A: Sim; o aceite reserva o prestador e sua disponibilidade retorna automaticamente após conclusão ou cancelamento.
- Q: O preço mostrado ao cliente deve ficar garantido para aquele atendimento quando ele tocar em “Solicitar”? → A: Sim; o preço apresentado fica garantido até o encerramento daquela solicitação.
- Q: Até que distância a busca deve ampliar automaticamente quando não encontrar nenhum guincho próximo? → A: Buscar primeiro em 10 km, ampliar para 25 km e finalizar em 50 km.
- Q: Depois que o prestador aceitar, quem pode cancelar o atendimento e o que deve acontecer em seguida? → A: Cliente e prestador podem cancelar com motivo obrigatório; cancelamento do prestador retoma a busca e o do cliente encerra o fluxo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Disponibilizar empresa móvel no mapa (Priority: P1)

Como representante de uma empresa que atende em um automóvel, quero cadastrar a modalidade móvel e publicar uma posição recente enquanto estiver disponível, para que clientes encontrem o veículo na região em que ele realmente está circulando.

**Why this priority**: Sem localização recente e modalidade correta, a busca pelo prestador móvel mais próximo não representa a operação real e pode encaminhar o cliente para um veículo distante ou indisponível.

**Independent Test**: Cadastrar uma empresa móvel, torná-la disponível, deslocar o dispositivo autorizado e confirmar que sua posição elegível muda no mapa e é identificada visualmente como veículo.

**Acceptance Scenarios**:

1. **Given** uma conta empresarial em cadastro, **When** seleciona a modalidade móvel e conclui os demais dados obrigatórios, **Then** o cadastro identifica que o atendimento é realizado por um veículo em circulação.
2. **Given** uma empresa móvel elegível e com autorização de localização, **When** sua posição válida muda, **Then** o mapa passa a representar a posição mais recente sem criar um histórico público do trajeto.
3. **Given** uma empresa móvel apresentada no mapa, **When** o cliente visualiza seu marcador, **Then** ele vê um ícone de carro coerente com o padrão visual do aplicativo e uma alternativa textual acessível.
4. **Given** uma posição antiga, imprecisa ou inválida, **When** uma busca é executada, **Then** a empresa não é apresentada como se estivesse naquela posição em tempo real.
5. **Given** uma empresa fixa, **When** clientes pesquisam serviços, **Then** ela continua usando o endereço empresarial e não é tratada como veículo em circulação.

---

### User Story 2 - Encontrar e comparar guinchos próximos (Priority: P1)

Como cliente que precisa de um guincho, quero que o aplicativo procure primeiro o prestador móvel elegível mais próximo e apresente suas condições, para decidir rapidamente se desejo solicitar aquele atendimento ou consultar outra opção.

**Why this priority**: Essa jornada transforma a localização dos prestadores móveis em valor direto para o cliente em uma situação potencialmente urgente.

**Independent Test**: Disponibilizar ao menos três guinchos móveis com posições e preços diferentes, iniciar a busca pelo card de guincho e confirmar que o primeiro resultado é o elegível mais próximo e que a ação “Próximo” apresenta outra opção sem repetir imediatamente a anterior.

**Acceptance Scenarios**:

1. **Given** um cliente autenticado e guinchos móveis elegíveis, **When** toca no card do serviço de guincho, **Then** o sistema inicia uma busca ordenada pela proximidade da posição válida do cliente.
2. **Given** um guincho encontrado, **When** sua proposta é apresentada, **Then** o cliente vê ao menos nome da empresa, preço aplicável, localidade e distância estimada, sem exposição de dados privados.
3. **Given** uma proposta apresentada, **When** o cliente toca em “Próximo”, **Then** aquela opção é descartada na busca corrente e o sistema apresenta o próximo guincho elegível por proximidade.
4. **Given** que todos os candidatos até 10 km já foram consultados, **When** não houver opção elegível restante, **Then** o sistema amplia automaticamente a busca para 25 km sem repetir as opções descartadas.
5. **Given** que todos os candidatos até 25 km já foram consultados, **When** não houver opção elegível restante, **Then** o sistema amplia automaticamente a busca para o limite final de 50 km.
6. **Given** que nenhum guincho atende aos critérios até 50 km, **When** a procura termina, **Then** o cliente recebe um estado vazio compreensível e uma ação para iniciar uma nova busca.
7. **Given** uma resposta antiga de busca chegando depois de uma mais recente, **When** a tela é atualizada, **Then** o resultado antigo não substitui o candidato atual.
8. **Given** uma proposta com preço apresentado, **When** o cliente toca em “Solicitar”, **Then** aquele preço fica associado à solicitação e não muda por alterações posteriores na oferta do prestador.

---

### User Story 3 - Acompanhar e retomar o estado da solicitação (Priority: P1)

Como cliente, quero acompanhar um estado claro desde a busca até a decisão sobre o atendimento e retomar esse mesmo ponto ao reabrir o aplicativo, para não perder uma solicitação em andamento nem iniciar outra por engano.

**Why this priority**: Um fluxo urgente precisa sobreviver ao fechamento do aplicativo, perda temporária de conexão e retorno posterior sem estados contraditórios.

**Independent Test**: Iniciar uma busca, avançar por propostas e fechar o aplicativo em cada estado persistido; ao entrar novamente com a mesma conta, confirmar que a tela correta e o último estado válido são restaurados.

**Acceptance Scenarios**:

1. **Given** uma busca recém-iniciada, **When** ainda não há candidato elegível confirmado, **Then** a tela apresenta o estado “Buscando prestador”.
2. **Given** um candidato selecionado pelo sistema, **When** seus dados estão prontos, **Then** a tela apresenta o estado “Opção encontrada” junto da proposta atual.
3. **Given** uma opção encontrada, **When** o cliente solicita o atendimento, **Then** o estado passa para “Aguardando prestador” e nenhuma confirmação final é apresentada antes da resposta da empresa.
4. **Given** uma solicitação aguardando resposta, **When** o prestador aceita, **Then** o estado passa para “Atendimento aceito” e o fluxo de acompanhamento autorizado fica disponível.
5. **Given** uma solicitação aguardando resposta, **When** o prestador recusa ou deixa a proposta expirar, **Then** a ocorrência é registrada e o sistema retoma a busca pelo próximo candidato elegível.
6. **Given** uma solicitação aguardando resposta por 60 segundos sem aceite ou recusa, **When** o prazo termina, **Then** a proposta passa para “Expirada” e a busca continua automaticamente.
7. **Given** uma solicitação ativa persistida, **When** o cliente fecha e reabre o aplicativo com a mesma conta, **Then** ele é direcionado para a etapa correspondente ao último estado válido.
8. **Given** uma solicitação pertencente a outra conta, **When** ocorre troca de sessão, **Then** nenhum dado, proposta ou status da solicitação anterior permanece visível.
9. **Given** perda de conexão durante uma mudança de estado, **When** o resultado não puder ser confirmado, **Then** o aplicativo mantém o último estado confirmado, informa a incerteza e permite tentar novamente sem duplicar a solicitação.
10. **Given** um atendimento aceito ainda não concluído, **When** o prestador cancela e informa o motivo, **Then** o atendimento é encerrado, o guincho é liberado e o cliente retoma a busca pelo próximo candidato elegível.
11. **Given** um atendimento aceito ainda não concluído, **When** o cliente cancela e informa o motivo, **Then** o atendimento é encerrado, o guincho é liberado e nenhuma nova busca começa automaticamente.

---

### User Story 4 - Controlar disponibilidade e publicação da empresa móvel (Priority: P2)

Como prestador móvel, quero compreender quando minha localização está sendo usada e controlar minha disponibilidade, para não aparecer em buscas quando não puder atender.

**Why this priority**: A atualização frequente da posição exige transparência, controle e encerramento confiável para proteger o prestador e evitar ofertas inválidas.

**Independent Test**: Alternar a disponibilidade de uma empresa móvel e encerrar/trocar a sessão, confirmando que o indicador acompanha o estado e que a empresa deixa de participar de novas buscas quando inelegível.

**Acceptance Scenarios**:

1. **Given** uma empresa móvel com publicação ativa, **When** usa o aplicativo, **Then** recebe indicação persistente e compreensível de que sua localização está sendo atualizada.
2. **Given** uma empresa móvel indisponível, inativa, sem autorização adequada ou com sessão encerrada, **When** uma busca é realizada, **Then** ela não é oferecida como candidata.
3. **Given** perda de autorização ou localização desativada, **When** a publicação não puder continuar, **Then** o prestador é informado e sua posição deixa de ser considerada recente após o limite definido.
4. **Given** uma empresa móvel disponível, **When** aceita um atendimento, **Then** fica reservada para aquele cliente e deixa de aparecer em novas buscas até o atendimento ser concluído ou cancelado.
5. **Given** um atendimento reservado concluído ou cancelado, **When** o encerramento é confirmado, **Then** a empresa volta automaticamente a ficar disponível, desde que continue ativa, móvel e apta a publicar localização válida.

### Edge Cases

- Dois ou mais guinchos estão à mesma distância estimada do cliente.
- O guincho mais próximo fica indisponível entre a busca e a apresentação da proposta.
- O preço ou a oferta muda enquanto a proposta está aberta.
- O cliente toca repetidamente em “Próximo” ou na ação de solicitar.
- Todos os candidatos da busca já foram consultados ou recusados.
- A localização do cliente é recusada, imprecisa, desativada ou fica indisponível durante a busca.
- A localização do prestador deixa de ser atualizada enquanto sua proposta está aberta.
- O aplicativo é encerrado durante a busca, durante uma proposta ou enquanto aguarda uma decisão.
- A sessão expira ou outra conta entra no aparelho durante um fluxo persistido.
- Uma atualização antiga de posição ou status chega após uma atualização mais recente.
- O prestador alterna de móvel para fixo enquanto aparece em uma busca.
- Não há preço válido configurado para o serviço solicitado.
- O cliente tenta manter mais de uma busca ou solicitação ativa para o mesmo tipo de necessidade.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O cadastro empresarial MUST exigir uma escolha explícita entre modalidade fixa e móvel.
- **FR-002**: A modalidade móvel MUST representar uma empresa cujo atendimento é realizado por um automóvel em circulação e MUST ser distinguida da modalidade fixa em cadastro, sessão e descoberta.
- **FR-003**: A alteração posterior entre modalidade fixa e móvel MUST reavaliar imediatamente a elegibilidade de localização e de participação em novas buscas.
- **FR-004**: Uma empresa móvel MUST possuir uma posição válida e recente para participar da ordenação por proximidade.
- **FR-005**: Enquanto estiver marcada como “Disponível”, uma empresa móvel MUST publicar sua posição recente inclusive quando o aplicativo não estiver em primeiro plano; a posição MUST ficar visível somente a clientes autenticados em uma busca compatível e aos participantes de um atendimento correspondente.
- **FR-006**: O prestador MUST receber indicação clara enquanto sua localização estiver sendo atualizada e MUST poder entender como interromper a publicação ao ficar indisponível.
- **FR-007**: A publicação MUST parar ou perder validade quando a empresa ficar indisponível/inativa, trocar para modalidade fixa, encerrar a sessão, perder autorização de localização ou deixar de fornecer posições válidas.
- **FR-008**: O sistema MUST manter apenas a posição atual necessária para descoberta ou acompanhamento e MUST NOT criar histórico público do deslocamento.
- **FR-009**: A posição de uma empresa móvel MUST ser marcada como desatualizada após 30 segundos sem atualização válida e MUST NOT ser usada para classificá-la como opção em tempo real.
- **FR-010**: Enquanto elegível para publicação, a empresa móvel MUST ter como alvo atualizar sua posição aproximadamente a cada 10 segundos, respeitando limitações do dispositivo e da rede.
- **FR-011**: O mapa MUST representar empresas móveis com um ícone de carro coerente com o design do projeto, diferenciável por forma e significado e acompanhado de alternativa textual acessível.
- **FR-012**: Empresas fixas MUST continuar representadas pelo endereço empresarial e MUST NOT usar o ícone ou a posição dinâmica destinados à modalidade móvel.
- **FR-013**: Ao tocar no card de guincho, o sistema MUST iniciar uma busca por empresas móveis que ofereçam exatamente esse serviço e estejam ativas, disponíveis e com localização recente.
- **FR-014**: A busca MUST usar uma posição atual autorizada do cliente para ordenar candidatos por distância; se ela não estiver disponível, MUST explicar o impedimento e permitir nova tentativa, sem inventar proximidade.
- **FR-015**: O primeiro candidato apresentado MUST ser o elegível de menor distância estimada entre os ainda disponíveis para aquela busca.
- **FR-016**: A proposta de candidato MUST apresentar nome da empresa, preço aplicável, localidade e distância estimada, além das ações correspondentes à decisão do cliente.
- **FR-017**: Informações privadas como CNPJ, contato não autorizado, posição histórica e dados de outras solicitações MUST NOT ser apresentadas na proposta.
- **FR-018**: O preço mostrado MUST corresponder à oferta vigente do serviço de guincho no momento da solicitação e MUST ser congelado como valor garantido até seu encerramento; alterações posteriores na oferta do prestador MUST NOT modificar uma solicitação existente.
- **FR-019**: Ao escolher uma proposta, o cliente MUST criar uma solicitação para aquele prestador e aguardar sua resposta; somente o aceite do prestador MUST confirmar o atendimento, enquanto a recusa ou expiração MUST registrar o resultado e retomar a busca por outra opção elegível.
- **FR-019A**: O prestador MUST ter 60 segundos, contados a partir da confirmação da solicitação, para aceitar ou recusar; sem resposta confirmada nesse prazo, a proposta MUST expirar de forma definitiva e a busca MUST continuar automaticamente.
- **FR-019B**: Ao aceitar uma solicitação, o prestador MUST ser reservado exclusivamente para aquele atendimento e MUST deixar imediatamente de participar de novas buscas ou receber solicitações concorrentes.
- **FR-019C**: Após conclusão ou cancelamento confirmado, o prestador MUST voltar automaticamente ao estado disponível se continuar ativo, na modalidade móvel e com autorização e posição válidas; durante o atendimento reservado, sua localização móvel MUST permanecer restrita aos participantes.
- **FR-019D**: Após o aceite e antes da conclusão, cliente e prestador MUST poder cancelar o atendimento somente após informar um motivo, e o cancelamento MUST registrar qual participante tomou a decisão.
- **FR-019E**: O cancelamento pelo prestador MUST encerrar a solicitação atual e retomar automaticamente a busca do cliente pelo próximo candidato elegível; o cancelamento pelo cliente MUST encerrar o fluxo sem iniciar outra busca automaticamente.
- **FR-020**: A ação “Próximo” MUST ser disponibilizada enquanto houver alternativas elegíveis e MUST preservar a busca corrente sem criar uma nova solicitação por toque.
- **FR-021**: A ação “Próximo” MUST excluir a proposta atual da busca corrente; quando não restarem candidatos na região pesquisada, o sistema MUST ampliar automaticamente o raio de 10 km para 25 km e depois para o limite final de 50 km, sem repetir opções já descartadas, apresentando estado vazio somente após esgotar os candidatos elegíveis até 50 km.
- **FR-022**: Toques repetidos ou respostas perdidas MUST NOT duplicar buscas, propostas, solicitações ou mudanças de estado.
- **FR-023**: O fluxo MUST utilizar estados explícitos e persistidos, com no mínimo equivalentes a: buscando prestador, opção encontrada, aguardando prestador, atendimento aceito, recusado pelo prestador, opção dispensada pelo cliente, proposta expirada, cancelado, em atendimento e concluído.
- **FR-024**: Transições de estado MUST seguir uma ordem válida e MUST impedir regressão causada por eventos antigos ou concorrentes.
- **FR-025**: O estado “recusada/dispensada” MUST distinguir a recusa do prestador da decisão do cliente de consultar o próximo candidato quando ambos fizerem parte do fluxo final.
- **FR-026**: Cada busca MUST manter a ordem e o conjunto de candidatos já apresentados para que a retomada não repita silenciosamente uma opção descartada.
- **FR-027**: Ao reabrir o aplicativo com a mesma conta, o cliente MUST ser direcionado ao fluxo ativo mais recente e ver o último estado confirmado, sua proposta atual e as ações ainda permitidas.
- **FR-028**: A retomada MUST funcionar após fechamento do aplicativo, reinício do processo, perda temporária de rede e nova autenticação da mesma conta.
- **FR-029**: Ao trocar ou encerrar a sessão, o sistema MUST limpar da interface os dados da solicitação anterior e MUST NOT permitir acesso por outra conta.
- **FR-030**: Quando uma mudança não puder ser confirmada, a UI MUST manter o último estado confirmado, indicar a falha recuperável e permitir repetição segura.
- **FR-031**: O sistema MUST impedir mais de uma solicitação ativa conflitante para o mesmo cliente e necessidade; a tentativa adicional MUST levar ao fluxo já ativo.
- **FR-032**: Um candidato que se torne indisponível, perca localização recente ou deixe de oferecer o serviço antes da confirmação MUST ser invalidado e MUST NOT ser aceito como se ainda estivesse elegível.
- **FR-033**: Quando não houver candidatos, o sistema MUST apresentar um estado vazio com opção de tentar novamente e MUST NOT deixar a tela indefinidamente em “Buscando”.
- **FR-034**: Estados de carregamento, busca vazia, falta de conexão, localização recusada/desativada, sessão expirada e serviço indisponível MUST possuir mensagens e ações de recuperação compreensíveis.
- **FR-035**: O fluxo MUST ser acessível sem depender apenas de cor, movimento do mapa ou ícone; proposta, status e ações MUST possuir representação textual e alvos adequados para smartphones.
- **FR-036**: A busca e a retomada MUST respeitar o papel da conta: somente clientes podem iniciar/decidir solicitações e somente a empresa móvel proprietária pode atuar sobre uma solicitação destinada a ela.

### Key Entities *(include if feature involves data)*

- **Mobile Provider Availability**: Estado operacional da empresa móvel, incluindo modalidade, disponibilidade, recência da posição e elegibilidade para receber buscas.
- **Current Provider Location**: Última posição válida de uma empresa móvel, com horário e qualidade suficientes para decidir se ainda pode ser usada; não representa histórico de trajeto.
- **Service Search**: Jornada persistente iniciada por um cliente para um serviço específico, contendo estado atual, posição de referência, candidatos já apresentados e proposta corrente.
- **Candidate Proposal**: Fotografia das condições apresentadas para um prestador elegível, incluindo identidade pública, serviço, preço, localidade, distância estimada e validade.
- **Service Request**: Solicitação criada quando a proposta avança para confirmação, relacionando exclusivamente o cliente, a empresa, o serviço e o estado vigente.
- **Request Status**: Estado explícito da jornada, com transições auditáveis entre busca, proposta, decisão, aceite, execução e encerramento; um cancelamento registra o autor e o motivo correspondente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 95% das buscas com localização válida e candidatos elegíveis, o cliente visualiza a primeira proposta ou um estado vazio em até 5 segundos.
- **SC-002**: Em 100% dos testes com múltiplos candidatos, a primeira proposta corresponde ao prestador elegível de menor distância e nenhuma opção descartada é repetida antes da regra definida para reinício.
- **SC-003**: Em 95% das atualizações válidas de uma empresa móvel elegível, sua nova posição fica disponível para a experiência autorizada em até 15 segundos.
- **SC-004**: Em 100% dos casos sem atualização válida por mais de 30 segundos, a empresa deixa de ser classificada como localizada em tempo real.
- **SC-005**: Em 100% dos testes de retomada, fechar e reabrir o aplicativo com a mesma conta leva o cliente ao último estado confirmado sem duplicar busca ou solicitação.
- **SC-006**: Em 100% dos testes de autorização, outra conta não consegue visualizar nem alterar a solicitação ou localização privada que não lhe pertence.
- **SC-007**: Em testes com toques repetidos e perda de resposta, cada decisão do cliente produz no máximo uma transição válida e uma solicitação correspondente.
- **SC-008**: Os fluxos de disponibilidade, busca, proposta e retomada permanecem utilizáveis em smartphones Android suportados, em paisagem e com fonte ampliada a 200%, sem informação essencial disponível somente no mapa.
- **SC-009**: Em teste moderado com 100 empresas móveis elegíveis numa região, a busca mantém ordenação correta e apresenta uma proposta ou estado vazio dentro do limite de 5 segundos.
- **SC-010**: Em 100% das solicitações sem resposta do prestador, a proposta é marcada como expirada ao completar 60 segundos e o cliente avança automaticamente sem precisar cancelar manualmente.
- **SC-011**: Em 100% dos testes concorrentes, um prestador que aceita um atendimento deixa de receber novas solicitações até o encerramento e volta automaticamente à busca após conclusão ou cancelamento quando continuar elegível.
- **SC-012**: Em 100% das solicitações criadas, o preço exibido no momento de “Solicitar” permanece inalterado durante todo o fluxo, inclusive após mudança posterior na oferta do prestador.
- **SC-013**: Em 100% das buscas sem candidatos restantes na faixa atual, a cobertura avança na sequência 10 km, 25 km e 50 km e termina com estado vazio após esgotar o limite final.
- **SC-014**: Em 100% dos cancelamentos após aceite, o autor e o motivo são preservados, o prestador é liberado e somente o cancelamento iniciado pelo prestador retoma automaticamente a busca do cliente.

## Assumptions

- A conta cliente e a conta empresarial permanecem independentes e cada sessão possui apenas um papel efetivo.
- O serviço de guincho já existe no catálogo, e cada empresa elegível possui uma oferta ativa com preço válido para esse serviço.
- “Requisitos do cliente” significam, nesta entrega, serviço exato, empresa móvel ativa e disponível, localização recente e proximidade; filtros adicionais de tipo de veículo, capacidade ou cobertura não foram solicitados.
- A localidade exibida é uma descrição pública aproximada da posição vigente, suficiente para orientar o cliente sem expor endereço residencial ou histórico.
- O cálculo de distância representa proximidade geográfica estimada e não promete tempo de chegada nem rota viária.
- A busca começa em 10 km, amplia para 25 km e termina em 50 km; somente depois desse limite sem candidatos elegíveis apresenta estado vazio.
- O preço apresentado não inclui negociação, pagamento ou cobrança nesta feature.
- A posição do cliente é usada para a busca corrente e não cria histórico de deslocamento.
- O estado persistido é a fonte para retomada; estado visual temporário em memória não é suficiente.

## Out of Scope

- Pagamento, cobrança, repasse ou estorno.
- Cálculo de rota, navegação curva a curva ou promessa de tempo exato de chegada.
- Histórico e replay de deslocamento de clientes ou prestadores.
- Avaliação, chat, chamada telefônica ou negociação de preço.
- Despacho simultâneo para vários prestadores como leilão.
- Filtros adicionais não informados, como capacidade do guincho, tipo de veículo atendido ou equipamentos especiais.
- Alteração do fluxo de empresas fixas além da preservação de seu comportamento atual.
