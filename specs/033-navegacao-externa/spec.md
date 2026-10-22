# Feature Specification: Navegação externa e indicação de localização

**Feature Branch**: `feature/rota-externa`

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "Colocar uma opção para o guincheiro e o PitTec abrir o Waze ou o Google Maps para seguir a rota. Ao abrir uma das opções, o caminho é mostrado. Tirar o ícone que representa um relógio mostrando erro na detecção da localização."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Guincheiro segue a rota no aplicativo escolhido (Priority: P1)

Como guincheiro com deslocamento autorizado em um atendimento, quero escolher Waze ou Google Maps e visualizar o caminho até o próximo ponto do serviço, sem digitar o endereço novamente.

**Why this priority**: Permite ao guincheiro usar seu navegador habitual tanto para buscar o veículo quanto para transportá-lo ao destino confirmado.

**Independent Test**: Em um atendimento de guincho autorizado, abrir cada navegador durante o deslocamento à retirada e durante o transporte. Conferir o ponto de chegada e retornar ao PitGo. A validação pode ser manual, sem criar testes automatizados.

**Acceptance Scenarios**:

1. **Given** um guincheiro indo buscar o veículo, com deslocamento autorizado e ponto de retirada válido, **When** escolhe Waze, **Then** o Waze abre com o trajeto até a retirada preparada no atendimento, sem redigitação do endereço.
2. **Given** a mesma etapa, **When** escolhe Google Maps, **Then** o Google Maps apresenta o caminho até o mesmo ponto de retirada.
3. **Given** que o transporte já foi iniciado pelas regras do atendimento, **When** escolhe qualquer um dos dois navegadores, **Then** o caminho apresentado termina no destino final confirmado pelo cliente, incluindo o ponto correspondente ao número do imóvel quando informado.
4. **Given** que o guincheiro abriu o navegador, **When** retorna ao PitGo, **Then** o atendimento e a vistoria permanecem disponíveis no estado atual, e nenhuma chegada, aprovação, início ou conclusão foi registrada apenas pela abertura do navegador.
5. **Given** um destino válido e o cálculo do percurso interno indisponível, **When** escolhe um navegador instalado, **Then** consegue abrir o aplicativo escolhido com o ponto de chegada definido, sem aguardar o percurso interno.
6. **Given** um atendimento aguardando liberação do deslocamento, cancelado, concluído ou aberto apenas pelo histórico, **When** o guincheiro consulta a tela, **Then** não encontra uma ação habilitada para navegar como parte daquele atendimento.
7. **Given** um guincheiro compartilhando a localização com autorização vigente, **When** abre o navegador e continua o deslocamento, **Then** o cliente continua recebendo as atualizações previstas no acompanhamento.

---

### User Story 2 - PitTec navega até o cliente (Priority: P1)

Como profissional PitTec com deslocamento autorizado, quero escolher Waze ou Google Maps para seguir até o local do atendimento do cliente.

**Why this priority**: Oferece a mesma facilidade ao profissional PitTec e reduz a necessidade de copiar ou pesquisar endereços durante o atendimento.

**Independent Test**: Em um atendimento PitTec liberado para deslocamento, abrir Waze e Google Maps separadamente e conferir o local de chegada. Repetir com apenas um navegador disponível e com nenhum disponível.

**Acceptance Scenarios**:

1. **Given** um PitTec em deslocamento autorizado até um cliente com local válido, **When** escolhe Waze ou Google Maps, **Then** o aplicativo escolhido apresenta o caminho até o local do atendimento, sem solicitar a digitação do endereço.
2. **Given** que apenas um dos navegadores está disponível, **When** abre as opções de navegação, **Then** identifica qual pode ser usado e consegue escolhê-lo; a opção indisponível é identificada como tal.
3. **Given** que nenhum dos navegadores pode ser aberto, **When** tenta usar a navegação externa, **Then** recebe uma mensagem compreensível e consegue permanecer no acompanhamento do PitGo, sem perder o atendimento.
4. **Given** que a chegada do PitTec já foi registrada, o serviço está em execução, terminou ou foi cancelado, **When** consulta o acompanhamento, **Then** não recebe uma ação habilitada de deslocamento até o cliente para essa etapa.
5. **Given** um PitTec compartilhando a localização com autorização vigente, **When** abre o navegador e continua o deslocamento, **Then** o cliente continua recebendo as atualizações previstas no acompanhamento.

---

### User Story 3 - Mapa mantém os marcadores padrão sem o relógio de localização (Priority: P2)

Como usuário acompanhando um atendimento de guincho ou PitTec, quero visualizar os marcadores padrão do projeto, sem o símbolo de relógio associado à localização desatualizada ou indisponível.

**Why this priority**: Remove a indicação visual solicitada sem fazer uma posição antiga parecer atual nem deixar o usuário sem orientação sobre a localização.

**Independent Test**: Conferir os mapas do atendimento nas visões de cliente e prestador, com posição atualizada, última posição conhecida e ausência de posição válida. Verificar os marcadores e a informação textual apresentada.

**Acceptance Scenarios**:

1. **Given** uma posição atualizada de cliente ou prestador, **When** o mapa é apresentado, **Then** os marcadores padrão permanecem identificáveis e não há relógio de erro de localização.
2. **Given** apenas uma última posição conhecida, **When** o mapa é apresentado, **Then** o relógio não aparece, o marcador padrão permanece nesse ponto e um aviso textual informa que a posição está desatualizada ou aguarda atualização.
3. **Given** que não existe posição válida, **When** o mapa é apresentado, **Then** nenhum ponto arbitrário é mostrado como localização da pessoa; uma mensagem informa a indisponibilidade e as ações que independem dessa posição continuam acessíveis.
4. **Given** que uma posição recente volta a ficar disponível, **When** o acompanhamento é atualizado, **Then** o marcador padrão acompanha a nova posição e o aviso de posição desatualizada deixa de ser apresentado.

### Edge Cases

- Aplicativo escolhido não instalado, desativado ou indisponível no momento da abertura: informar a situação, permitir escolher outra opção disponível ou voltar ao acompanhamento.
- Destino ausente ou inválido: impedir a abertura da rota e informar que o ponto do atendimento está indisponível; não substituir o destino por um ponto padrão ou somente pelo centro da rua.
- Localização atual do prestador indisponível: permitir abrir o navegador com o destino válido; a obtenção da origem e as orientações para habilitar localização ficam a cargo do aplicativo escolhido.
- Atendimento cancelado, etapa alterada ou destino atualizado enquanto a escolha está aberta: usar o contexto vigente ao abrir o navegador; se não houver mais deslocamento permitido, interromper a abertura e informar a mudança.
- Navegador sem conectividade, sem permissões ou em sua primeira configuração: preservar o destino enviado e respeitar as orientações do aplicativo externo; a apresentação do percurso depende de ele estar pronto para calcular rotas.
- Usuário fecha a escolha ou retorna do navegador sem iniciar a viagem: nenhuma ação operacional é executada automaticamente no atendimento.
- Posição antiga continua visível no mapa: sua idade não é ocultada pela remoção do relógio e ela não é apresentada como atualização em tempo real.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O PitGo DEVE oferecer ao guincheiro e ao profissional PitTec uma ação identificável para abrir a navegação externa no acompanhamento de um deslocamento autorizado pelas regras atuais de aceite e pagamento.
- **FR-002**: A ação DEVE permitir escolher explicitamente entre Waze e Google Maps e informar quais opções estão indisponíveis. A escolha de um aplicativo DEVE abrir esse aplicativo, sem substituição silenciosa por outro.
- **FR-003**: Para o guincheiro, o destino da navegação DEVE ser o ponto de retirada até o início autorizado do transporte e o destino final confirmado após esse início.
- **FR-004**: Para o profissional PitTec, o destino DEVE ser o local do atendimento do cliente enquanto houver deslocamento autorizado e a chegada ainda não tiver sido registrada. Esta funcionalidade não cria uma segunda etapa de viagem para PitTec.
- **FR-005**: O navegador escolhido DEVE receber o ponto geográfico válido do destino vigente e abrir a experiência de rotas ou navegação com o caminho preparado, em vez de apresentar apenas uma pesquisa ou um marcador isolado. O profissional não precisa digitar o endereço novamente.
- **FR-006**: A origem do trajeto DEVE ser a posição atual obtida pelo navegador escolhido. O PitGo não deve inventar uma origem nem exigir o cálculo de seu percurso interno para permitir a abertura quando o destino já é válido.
- **FR-007**: Na ausência de destino válido, o PitGo DEVE impedir a abertura da rota e exibir uma mensagem compreensível, preservando o atendimento e permitindo nova tentativa quando o ponto ficar disponível.
- **FR-008**: Se o aplicativo escolhido não puder ser aberto, o PitGo DEVE comunicar a indisponibilidade e permitir escolher outra opção disponível ou permanecer no acompanhamento, sem fechar o PitGo inesperadamente.
- **FR-009**: A abertura DEVE considerar o atendimento, o destino e a etapa vigentes no momento da escolha. Atendimentos cancelados, concluídos, apenas históricos ou sem liberação para deslocamento não podem oferecer uma ação habilitada de navegação operacional.
- **FR-010**: Abrir ou fechar o navegador DEVE preservar as regras e os dados do atendimento, incluindo vistoria, aceite, chegada, início do serviço ou transporte, pagamento e conclusão. Nenhuma dessas transições pode ocorrer automaticamente como consequência da navegação externa.
- **FR-011**: A troca para o navegador DEVE preservar o compartilhamento de localização já autorizado e exigido pelo atendimento, conforme as regras existentes. Ao retornar ao PitGo, o prestador DEVE visualizar o estado atual do atendimento.
- **FR-012**: Nos mapas de acompanhamento de guincho e PitTec, nas visões de cliente e prestador, o símbolo de relógio associado à localização desatualizada ou indisponível DEVE ser removido. A última posição válida, quando exibida, DEVE usar o marcador padrão correspondente ao participante.
- **FR-013**: A remoção do relógio DEVE preservar uma indicação textual acessível quando a posição estiver desatualizada ou indisponível. Uma atualização válida DEVE atualizar o marcador e retirar o aviso correspondente; sem posição válida, nenhum marcador fictício pode representar o participante.
- **FR-014**: A escolha do navegador DEVE compartilhar apenas o necessário para apresentar a rota, como ponto de chegada e sua identificação. Nome do cliente, telefone, placa, fotos da vistoria e valores do atendimento não devem ser enviados ao navegador por esta funcionalidade.
- **FR-015**: As opções, mensagens de indisponibilidade e avisos de localização DEVEM ser compreensíveis com leitura por acessibilidade e permanecer utilizáveis com aumento de fonte, sem depender apenas de ícones ou cores.

### Key Entities

- **Contexto de navegação do atendimento**: Atendimento vigente, papel do prestador, etapa de deslocamento, autorização e ponto de chegada correspondente. Reutiliza as informações já confirmadas no serviço.
- **Opção de navegador**: Waze ou Google Maps, sua disponibilidade para abertura e a escolha explícita do profissional. Não exige guardar uma preferência entre atendimentos.
- **Estado da posição exibida**: Posição atualizada, última posição conhecida ou ausência de posição válida; define o marcador padrão e a necessidade de orientação textual.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nos 6 casos de destino — guincho até a retirada, guincho até o destino final e PitTec até o cliente, cada um com Waze e Google Maps —, 100% das aberturas apresentam o destino correto, sem redigitar o endereço, com o navegador previamente configurado e apto a calcular a rota.
- **SC-002**: Com destino válido e navegador disponível, o profissional consegue escolhê-lo e abri-lo a partir do acompanhamento em no máximo 2 ações dentro do PitGo. Em pelo menos 95% das tentativas de validação com o navegador instalado e previamente configurado, sua tela fica visível em até 5 segundos após a escolha.
- **SC-003**: Em 100% dos cenários de aplicativo ou destino indisponível, o profissional recebe orientação e consegue permanecer no acompanhamento sem perder dados do atendimento.
- **SC-004**: Há zero ocorrências do relógio de erro de localização nos mapas de acompanhamento de guincho e PitTec, nas duas visões de participante, cobrindo posição atualizada, antiga e ausente. Os cenários de posição antiga ou ausente mantêm orientação textual acessível.
- **SC-005**: Em 100% dos cenários de abertura, cancelamento da escolha e retorno do navegador, nenhuma transição de atendimento ocorre pela navegação externa, e o acompanhamento reapresenta o estado vigente.
- **SC-006**: Nos dois perfis de prestador com compartilhamento de localização autorizado, a troca para o navegador mantém o acompanhamento pelo cliente conforme o intervalo e as condições já previstos no atendimento.

## Assumptions

- “Pitec” refere-se ao profissional PitTec já existente no projeto. A escolha de navegação externa é destinada aos prestadores; a remoção do relógio abrange os mapas do atendimento visualizados por clientes e prestadores.
- O relógio mencionado é o símbolo de última localização conhecida/desatualizada utilizado nos mapas do atendimento. Ícones de agendamento, histórico e horários estimados não fazem parte desta alteração.
- Pontos de retirada, local do cliente e destino final são os já confirmados no atendimento. A funcionalidade não redefine endereços nem substitui pontos válidos por uma nova busca textual.
- A opção aparece no acompanhamento do deslocamento: guincheiro até a retirada e, após início autorizado, até o destino final; PitTec até o cliente antes da chegada. As restrições atuais de aceite, pagamento, vistoria e início continuam valendo.
- O navegador externo calcula seu próprio percurso e pode apresentar um caminho diferente do exibido no PitGo. “Caminho mostrado” significa abrir a experiência de rota preparada; eventuais confirmações para começar a orientação ficam a cargo do aplicativo escolhido.
- A validação da apresentação do caminho pressupõe navegador instalado e configurado, destino válido e condições de conectividade e localização exigidas por ele. Não exige instalação automática de aplicativos nem alteração de suas permissões pelo PitGo.
- A seleção é feita a cada abertura. Preferência persistente de navegador, integração com outras opções e reprodução idêntica do percurso interno ficam fora do escopo.
