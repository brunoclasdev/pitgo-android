# Feature Specification: Guincho por porte, retomada da busca e ajustes visuais

**Feature Branch**: `feature/ajuste-cadastro-layout`

**Created**: 2026-11-03

**Status**: Draft

**Input**: User description: "Quando um cliente solicitar um guincho e o guincheiro recusar, a tela do cliente deve voltar para as buscas de guincho. O card do endereço de destino deve ficar no topo. Na home do guincheiro, o card de status deve ficar no topo e todos os cards devem ter bordas roxas. O cadastro deve informar tipo/porte do guincho para oferecer somente opções compatíveis com o veículo do cliente, incluindo micro-ônibus. O valor deve depender do tipo/porte e ser definido no painel/dashboard."

## Clarifications

### Session 2026-11-03

- Q: Os tipos/portes de guincho e os tipos de veículo que cada um pode transportar devem ser configuráveis no dashboard? → A: Sim. O dashboard gerencia tipos/portes, veículos compatíveis e preços.

- Q: Após a recusa, o guincheiro que recusou deve aparecer novamente na busca retomada pelo cliente? → A: Não. Ocultar na busca retomada enquanto veículo, retirada e destino forem os mesmos; a exclusão não é um bloqueio permanente para futuras buscas com dados diferentes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Retomar a busca após recusa (Priority: P1)

Como cliente, quero voltar à busca quando o guincheiro recusar para escolher outro prestador sem preencher novamente os dados do transporte.

**Why this priority**: Evita que o cliente fique preso a um atendimento que não acontecerá.

**Independent Test**: Recusar uma solicitação e acompanhar o retorno do cliente à busca com seus dados preservados.

**Acceptance Scenarios**:

1. **Given** uma solicitação aguardando aceite, **When** o guincheiro recusar, **Then** o cliente será informado e retornará automaticamente à busca com veículo, retirada e destino preservados, sem solicitação recusada ativa.
2. **Given** recusa de um pedido pago, **When** a busca reaparecer, **Then** o cancelamento e o reembolso seguirão a política vigente, com acompanhamento acessível, sem contratação nem cobrança automática de outro prestador.
3. **Given** recusa ocorrida com o aplicativo fechado, **When** o cliente retomar o aplicativo, **Then** verá a busca, sem reabrir a espera pelo guincheiro que recusou.
4. **Given** uma busca retomada após recusa, **When** os resultados forem atualizados ou o aplicativo for reaberto mantendo veículo, retirada e destino, **Then** o guincheiro que recusou não aparecerá nessa busca; ao mudar qualquer um desses dados, a exclusão específica deixará de valer, respeitados os demais critérios de elegibilidade.
5. **Given** busca retomada sem opções compatíveis disponíveis, **When** os resultados forem apresentados, **Then** o cliente verá uma mensagem de indisponibilidade e poderá tentar novamente mantendo seus dados.
6. **Given** solicitação criada antes da atualização sem contexto de busca, **When** o guincheiro recusar com o aplicativo do cliente fechado, **Then** a reabertura recuperará veículo, retirada e destino do pedido e excluirá o recusante da busca, sem duplicar a recuperação nem interromper viagem mais recente.
7. **Given** cancelamento voluntário, expiração ou conclusão de um atendimento, **When** esse encerramento for confirmado, **Then** apenas o contexto desse pedido será encerrado conforme o comportamento existente, sem exclusão de guincheiro ou retorno automático por recusa; eventos antigos não alterarão outro atendimento.

---

### User Story 2 - Encontrar guincho compatível com o veículo (Priority: P1)

Como guincheiro, quero cadastrar o tipo/porte do meu guincho; como cliente, quero visualizar somente guinchos capazes de transportar meu veículo.

**Why this priority**: Impede contratações incompatíveis com a capacidade de transporte.

**Independent Test**: Cadastrar guinchos com capacidades distintas e pesquisar usando automóvel e micro-ônibus.

**Acceptance Scenarios**:

1. **Given** um cadastro novo ou a edição de um guincho, **When** salvar, **Then** será obrigatório selecionar um tipo/porte reconhecido, mantendo os demais dados obrigatórios existentes.
2. **Given** cliente com micro-ônibus e guinchos com e sem capacidade compatível, **When** pesquisar, **Then** somente os compatíveis aparecerão no mapa, nos cards e nas opções de contratação.
3. **Given** veículo sem classificação ou guincho legado sem porte, **When** pesquisar, **Then** a classificação faltante não será presumida e nenhuma opção de compatibilidade desconhecida será oferecida; o responsável será orientado a completar o cadastro correspondente.
4. **Given** resultados para um automóvel, **When** o cliente trocar para micro-ônibus, **Then** os resultados e a seleção serão reavaliados antes de permitir contratação.
5. **Given** opção anteriormente compatível, **When** sua capacidade mudar antes da contratação, **Then** a opção incompatível será removida e o cliente deverá escolher novamente.

---

### User Story 3 - Administrar o preço por tipo/porte (Priority: P1)

Como responsável autorizado no dashboard, quero definir o valor do guincho por tipo/porte para que novas cotações usem a categoria do guincho escolhido.

**Why this priority**: Faz a cobrança refletir a capacidade do serviço contratado.

**Independent Test**: Configurar dois portes com valores diferentes e conferir as cotações e a preservação de propostas vigentes.

**Acceptance Scenarios**:

1. **Given** um responsável autorizado no dashboard, **When** cadastrar ou editar um tipo/porte, suas categorias de veículos compatíveis e seu preço, **Then** essas definições ficarão disponíveis de forma consistente no cadastro, na busca e nas novas cotações.
2. **Given** dois tipos/portes com valores distintos, **When** um responsável autorizado salvar valores válidos no dashboard, **Then** novas cotações usarão o valor correspondente ao porte do guincho selecionado.
3. **Given** o valor base do porte e os componentes vigentes de distância, percentual PitGo e taxas, **When** cotar, **Then** o total seguirá a composição atual, substituindo somente o valor do guincho pelo valor do porte selecionado.
4. **Given** cotação ainda válida ou pedido confirmado, **When** o valor do porte mudar, **Then** os valores já apresentados serão preservados conforme a validade existente; novas cotações usarão a configuração atualizada.
5. **Given** porte sem valor vigente ou configuração inválida, **When** tentar cotar ou ativar essa configuração, **Then** não será usado valor genérico nem zero implícito e a pendência será informada.
6. **Given** cliente ou prestador sem autorização administrativa, **When** tentar alterar o valor por porte, **Then** a alteração será recusada.

---

### User Story 4 - Priorizar destino e status com identidade visual roxa (Priority: P2)

Como cliente, quero o card de destino no topo; como guincheiro, quero o card de status no topo da home, com cards visualmente consistentes.

**Why this priority**: Facilita localizar a entrada e a informação principais de cada tela.

**Independent Test**: Abrir a busca do cliente e a home do guincheiro em telas suportadas, incluindo fonte ampliada e teclado aberto.

**Acceptance Scenarios**:

1. **Given** a tela de busca de guincho do cliente, **When** aberta, **Then** o card de endereço de destino aparecerá no topo da área de conteúdo, abaixo do cabeçalho e respeitando as áreas reservadas do aparelho, sem exigir rolagem inicial.
2. **Given** a home do guincheiro, **When** aberta, **Then** o card de status do guincho será o primeiro card de conteúdo, visível sem rolagem inicial.
3. **Given** as telas de busca de guincho do cliente e home do guincheiro, **When** seus cards forem exibidos, **Then** todos terão bordas roxas, incluindo destino e status, em tema claro e escuro.
4. **Given** tela compacta, fonte ampliada ou teclado aberto, **When** editar destino ou usar a home, **Then** textos e ações essenciais continuarão acessíveis, com rolagem quando necessária e sem sobreposição impeditiva dos controles do mapa.

### Edge Cases

- Recusas sucessivas na mesma busca: acumular os guincheiros excluídos enquanto veículo, retirada e destino permanecerem iguais; se não restarem opções, apresentar indisponibilidade.
- Recusa repetida ou recebida com atraso: aplicar somente ao pedido correspondente, sem duplicar reembolso nem interromper uma nova contratação.
- Pedido anterior à atualização sem contexto de busca: recuperar a viagem do próprio cliente uma única vez na recusa, sem substituir viagem mais recente.
- Encerramento diferente de recusa: cancelamento voluntário, expiração e conclusão encerram apenas o contexto do pedido correspondente, preservando regras existentes.
- Falha de conexão ao receber a recusa: reconciliar o estado ao reconectar e não manter um pedido já recusado como ativo.
- Reembolso pendente ou falho: permitir retorno à busca e manter a situação financeira acessível conforme a política existente.
- Frota com vários guinchos: considerar o porte do guincho efetivamente oferecido, sem atribuir a toda a frota a capacidade do maior veículo.
- Veículo ou porte desconhecido: exigir regularização, sem oferecer transporte por compatibilidade presumida.
- Guincho compatível sem preço vigente: não permitir contratação sem cotação válida; informar indisponibilidade quando não restarem opções.
- Alteração de veículo, porte ou disponibilidade durante a busca: revalidar a opção antes da contratação.
- Topo com cabeçalho, recortes de tela ou teclado: preservar áreas seguras, leitura e acesso às ações; posição no topo não exige bloquear a rolagem da home.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST informar a recusa e retornar automaticamente o cliente à busca de guincho, preservando veículo, retirada e destino e removendo a seleção recusada do atendimento ativo (US1.1, US1.3).
- **FR-002**: A retomada MUST atualizar as opções compatíveis e disponíveis, excluindo os guincheiros que recusaram enquanto veículo, retirada e destino permanecerem os mesmos, inclusive ao atualizar resultados ou reabrir o aplicativo. Alterar qualquer um desses dados MUST encerrar essa exclusão específica. Quando não restarem opções, MUST apresentar estado vazio com nova tentativa (US1.4–5).
- **FR-003**: A recusa MUST preservar cancelamento, reembolso e acompanhamento financeiro existentes, sem repetir cobrança ou reembolso; contratar outro prestador MUST exigir nova escolha e confirmação explícitas (US1.2).
- **FR-004**: A reabertura do aplicativo MUST respeitar a recusa já registrada; eventos de pedidos antigos MUST NOT interromper uma nova contratação (US1.3 e Edge Cases).
- **FR-005**: Cadastro e edição de cada guincho MUST exigir tipo/porte de um catálogo único compartilhado com compatibilidade e preços, mantendo placa, marca, modelo e cor obrigatórios (US2.1).
- **FR-006**: Cada tipo/porte MUST possuir uma relação explícita de categorias de veículos que comporta, incluindo a classificação de micro-ônibus. A capacidade MUST NOT ser inferida apenas do nome ou de uma ordenação entre portes (US2.2).
- **FR-007**: Busca, mapa, cards e contratação MUST oferecer somente guinchos compatíveis com a categoria do veículo solicitado, além de atender aos critérios existentes de disponibilidade (US2.2).
- **FR-008**: Classificação ausente no veículo ou porte ausente no guincho MUST impedir oferta de compatibilidade desconhecida e orientar o responsável a completar os dados (US2.3).
- **FR-009**: Trocas de veículo ou mudanças de capacidade MUST invalidar seleções incompatíveis; a compatibilidade MUST ser reavaliada antes da contratação (US2.4–5).
- **FR-010**: O dashboard MUST permitir a responsáveis autorizados cadastrar, consultar e editar tipos/portes, suas categorias de veículos compatíveis e seus preços. Essas definições MUST ser compartilhadas pelo cadastro, busca e cotação; clientes e prestadores sem essa autorização MUST NOT alterá-las (US3.1–2, US3.6).
- **FR-011**: Novas cotações MUST usar o valor vigente do porte do guincho escolhido como valor do guincho, preservando os demais componentes e regras comerciais vigentes da especificação 035, inclusive o preço do guincheiro fora do cálculo atual (US3.3).
- **FR-012**: Valores por porte MUST ser obrigatórios, monetários, positivos e expressos em reais com duas casas decimais; configuração inválida ou sem valor vigente MUST NOT gerar oferta contratável nem recorrer silenciosamente a preço genérico (US3.5).
- **FR-013**: Alterações administrativas MUST afetar novas cotações, preservando os valores de cotações ainda válidas e pedidos confirmados segundo as regras existentes (US3.4).
- **FR-014**: O card de endereço de destino do guincho MUST ocupar o topo da área de conteúdo da busca do cliente, abaixo do cabeçalho, visível sem rolagem inicial (US4.1).
- **FR-015**: Na home do guincheiro, o card de status do guincho MUST ser o primeiro card de conteúdo, visível sem rolagem inicial, mantendo seus estados e ações existentes (US4.2).
- **FR-016**: Todos os cards da busca de guincho do cliente e da home do guincheiro MUST ter bordas roxas consistentes com a identidade do produto em ambos os temas (US4.3).
- **FR-017**: Os ajustes visuais MUST preservar legibilidade, áreas seguras, identificação textual de status e acesso aos campos, ações e controles do mapa em tamanhos e fontes suportados, inclusive com teclado aberto (US4.4).

### Key Entities

- **Tipo/porte de guincho**: Classificação identificável e compartilhada que relaciona capacidade de transporte e valor administrativo.
- **Compatibilidade de transporte**: Relação explícita entre um tipo/porte e as categorias de veículos transportáveis, incluindo micro-ônibus.
- **Guincho cadastrado**: Veículo do prestador com identificação existente e tipo/porte obrigatório; determina a capacidade da oferta específica.
- **Veículo do cliente**: Veículo selecionado para transporte, com categoria suficiente para avaliar compatibilidade.
- **Configuração comercial por porte**: Valor do guincho por tipo/porte e vigência, associado às regras comerciais existentes.
- **Solicitação e cotação de guincho**: Cliente, guincho escolhido, veículo, retirada, destino, estado e valores preservados segundo a validade; recusa permite nova busca sem apagar histórico financeiro.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de recusa validados com conexão ativa, o cliente retorna à busca em até 3 segundos após receber a confirmação da recusa, sem redigitar veículo ou endereços e sem reapresentar guincheiros que recusaram enquanto esses dados forem mantidos.
- **SC-002**: Em 100% dos cenários de recusa paga, o cliente consegue consultar a situação financeira e não ocorre contratação automática nem duplicação de cobrança ou reembolso.
- **SC-003**: Em 100% das combinações validadas de categoria e porte, somente guinchos explicitamente compatíveis são oferecidos; nenhum incapaz de transportar micro-ônibus aparece para esse veículo.
- **SC-004**: Em 100% dos cadastros novos ou editados concluídos, o guincho possui tipo/porte válido; nenhum cadastro legado sem porte gera oferta contratável.
- **SC-005**: Em 100% das novas cotações avaliadas, o valor do guincho corresponde ao porte escolhido e à configuração vigente; propostas ainda válidas e pedidos confirmados preservam seus valores.
- **SC-006**: Em 100% das telas e temas validados no escopo, destino e status ocupam as posições definidas e todos os cards possuem bordas roxas, sem ações essenciais inacessíveis.
- **SC-007**: Em validação de usabilidade com pelo menos cinco participantes por perfil, ao menos 80% dos clientes localizam e preenchem o destino e 80% dos guincheiros identificam seu status sem ajuda, em até 10 segundos após abrir a tela.

## Assumptions

- “Bordas rochas” significa bordas roxas; “todos os cards” abrange a busca de guincho do cliente e a home do guincheiro, sem redesenho global das demais modalidades.
- “Voltar para as buscas” significa retomar a descoberta para escolha explícita de outro prestador, sem redistribuição automática do pedido recusado. Os guincheiros que recusaram ficam excluídos da busca retomada enquanto veículo, retirada e destino forem os mesmos; alterar qualquer um desses dados encerra essa exclusão específica. Não há bloqueio permanente.
- A relação de compatibilidade é explícita: um porte maior só atende outra categoria quando essa capacidade estiver declarada. A nomenclatura e a matriz efetiva devem ser aprovadas pela operação antes da liberação; esta especificação não presume limites de peso nem certificação técnica.
- A operação cadastrará no dashboard, antes da liberação, o catálogo inicial de portes, as categorias compatíveis e os respectivos valores; poderá manter essas definições pelo mesmo painel. O exemplo de micro-ônibus é obrigatório na validação dessa matriz.
- O valor por porte substitui o valor administrativo geral do guincho da especificação 035; deslocamento, percentual PitGo, taxas, arredondamento, remuneração e validade seguem as regras vigentes, sem novos componentes comerciais.
- As dependências são cadastro de veículos e prestadores, descoberta, estados de atendimento, pagamentos/reembolsos e dashboard com permissões existentes. Cadastros antigos precisarão de classificação para voltar a oferecer guincho.
- A entrega abrange retomada após recusa, cadastro/classificação, filtragem, valores por porte no dashboard e os ajustes visuais descritos. Não inclui novas regras de expiração, negociação de preço, transferência automática, reembolso parcial ou alterações de PitTec e loja.
