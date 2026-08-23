# Feature Specification: Mapa OSM com MapLibre e atualização em tempo real

**Feature Branch**: `[010-osm-realtime-map]`

**Created**: 2026-08-21

**Status**: Draft

**Input**: User description: "Revisar a feature 010: o SDK osmdroid está depreciado e
descontinuado; utilizar MapLibre Native para o mapa OSM, mantendo a marcação de endereço e a
atualização em tempo real."

## Clarifications

### Session 2026-08-21

- Q: Durante o cadastro da empresa, como o endereço deve ser apresentado no mapa antes de ser salvo? → A: Não mostrar mapa no cadastro; usar marcadores apenas na descoberta e no acompanhamento.
- Q: O que deve acontecer quando o cliente tocar no marcador de endereço de um prestador? → A: Abrir resumo no mapa com ação “Solicitar atendimento”.
- Q: Quando a posição em tempo real ficar desatualizada durante um atendimento ainda ativo, o marcador deve permanecer visível? → A: Manter o último marcador com estado “Localização desatualizada” e horário da última atualização.
- Q: Qual renderizador deve substituir a alternativa descontinuada definida anteriormente? → A:
  Usar MapLibre Native como único renderizador dos mapas da feature; osmdroid não deve ser usado.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Visualizar prestadores pelo endereço (Priority: P1)

Como cliente, quero abrir o mapa de um serviço e visualizar os prestadores compatíveis marcados
pelas coordenadas de seus endereços empresariais, para identificar as opções disponíveis na região.

**Why this priority**: A marcação confiável dos endereços é a entrega principal da substituição do
mapa e permite continuar o fluxo de descoberta de prestadores.

**Independent Test**: Abrir o mapa a partir de um serviço com prestadores compatíveis e confirmar
que cada resultado aparece na coordenada do endereço cadastrado, com identificação compreensível.

**Acceptance Scenarios**:

1. **Given** um serviço com prestadores compatíveis e endereços localizados, **When** o cliente abre
   o mapa, **Then** cada prestador elegível aparece em um marcador correspondente ao seu endereço.
2. **Given** marcadores próximos ou sobrepostos, **When** o cliente navega pelo mapa, **Then** ele
   consegue distinguir, selecionar e consultar cada prestador sem perder resultados.
3. **Given** um marcador selecionado, **When** o cliente toca nele, **Then** o sistema apresenta a
   identificação pública do prestador, sua modalidade, a estimativa disponível e a ação
   “Solicitar atendimento”, sem sair imediatamente do mapa.

---

### User Story 2 - Acompanhar posição atualizada (Priority: P1)

Como cliente participante de um atendimento ativo, quero ver a posição recente do prestador móvel
ser atualizada no mapa sem reabrir a tela, para acompanhar sua aproximação.

**Why this priority**: A atualização automática é necessária para que o mapa represente um
atendimento em andamento e não apenas um endereço estático.

**Independent Test**: Durante um atendimento ativo autorizado, publicar posições sucessivas do
prestador e confirmar que o marcador se desloca na tela aberta, indicando também quando o dado fica
desatualizado.

**Acceptance Scenarios**:

1. **Given** um atendimento ativo entre cliente e prestador móvel, **When** uma posição válida mais
   recente é recebida, **Then** o marcador é atualizado sem recarregar ou reabrir o mapa.
2. **Given** uma posição sem atualização válida por mais de 30 segundos, **When** o cliente observa
   o mapa, **Then** o último marcador permanece visível com o estado “Localização desatualizada” e
   o horário da última atualização, sem ser apresentado como tempo real.
3. **Given** um usuário que não participa do atendimento, **When** tenta obter a posição móvel,
   **Then** o acesso é negado e nenhuma coordenada privada é exibida.
4. **Given** o encerramento ou cancelamento do atendimento, **When** o estado final é confirmado,
   **Then** o mapa deixa de receber e apresentar novas posições móveis daquele atendimento.

---

### User Story 3 - Usar o mapa em condições adversas (Priority: P2)

Como cliente, quero receber estados claros quando o mapa, os tiles ou a localização estiverem
indisponíveis, para tentar novamente ou consultar os prestadores por uma alternativa acessível.

**Why this priority**: O mapa depende de rede e serviços externos; uma falha não pode deixar o
fluxo sem explicação ou tornar os resultados inacessíveis.

**Independent Test**: Simular indisponibilidade de tiles, ausência de rede, localização negada e
lista vazia, confirmando que cada caso apresenta mensagem e recuperação apropriadas.

**Acceptance Scenarios**:

1. **Given** falha temporária ao carregar o mapa, **When** a tela permanece aberta, **Then** o
   cliente recebe uma mensagem compreensível e pode tentar novamente.
2. **Given** permissão de localização recusada, **When** o cliente abre o mapa de descoberta,
   **Then** os prestadores ainda podem ser consultados por uma região alternativa sem publicar a
   posição do cliente.
3. **Given** uma pessoa que não consegue interpretar ou operar apenas o mapa, **When** consulta os
   resultados, **Then** encontra uma representação textual equivalente e ações acessíveis.

### Edge Cases

- O provedor de tiles está indisponível, limita requisições ou devolve imagens incompletas.
- O estilo do mapa está ausente, inválido, incompatível ou referencia uma fonte indisponível.
- O dispositivo perde e recupera conectividade enquanto o mapa está aberto.
- O mapa recebe uma posição antiga depois de uma posição mais recente.
- Vários prestadores ocupam a mesma coordenada ou uma área muito pequena.
- Um endereço cadastrado não possui coordenadas válidas ou foi desativado.
- O atendimento termina enquanto uma atualização de posição está em trânsito.
- A sessão expira ou troca de usuário com o acompanhamento aberto.
- O usuário amplia, reduz ou desloca o mapa rapidamente e respostas anteriores chegam depois.
- A permissão de localização é revogada enquanto a tela está visível.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST renderizar o mapa baseado em dados OpenStreetMap com MapLibre Native em
  todos os fluxos de descoberta e acompanhamento abrangidos por esta feature.
- **FR-002**: O sistema MUST remover o uso do Google Maps Compose e do osmdroid nesses fluxos, sem
  manter renderizadores concorrentes ou fallback entre SDKs para a mesma tela.
- **FR-003**: O mapa MUST exibir atribuição visível e adequada aos dados cartográficos e ao provedor
  de tiles utilizado.
- **FR-004**: Em produção, o estilo e as fontes de tiles compatíveis com MapLibre MUST ser fornecidos
  por um provedor OSM gerenciado e configurável por ambiente; os servidores públicos padrão do
  OpenStreetMap MUST NOT ser usados como infraestrutura de produção.
- **FR-005**: Cada prestador elegível MUST ser marcado pela latitude e longitude válidas de seu
  endereço empresarial nos mapas públicos de descoberta.
- **FR-006**: Prestadores sem endereço ativo ou sem coordenadas válidas MUST NOT ser apresentados
  como resultados posicionados no mapa.
- **FR-007**: A seleção de um marcador MUST apresentar informações públicas suficientes para
  identificar o prestador em um resumo no próprio mapa, incluindo nome, modalidade, estimativa
  disponível e ação “Solicitar atendimento”, sem expor CNPJ, contato privado ou localização móvel
  não autorizada.
- **FR-008**: Marcadores próximos ou sobrepostos MUST permanecer consultáveis por agrupamento ou
  interação equivalente que não descarte resultados.
- **FR-009**: Durante um atendimento ativo, o marcador móvel MUST reagir a posições válidas mais
  recentes sem exigir que o usuário reabra ou atualize manualmente a tela.
- **FR-010**: Atualizações fora de ordem MUST NOT fazer o marcador regredir para uma posição mais
  antiga.
- **FR-011**: Uma posição móvel sem atualização válida por mais de 30 segundos MUST ser identificada
  como desatualizada e MUST NOT ser apresentada como tempo real; enquanto o atendimento continuar
  ativo, o último marcador MUST permanecer visível com o horário da última atualização.
- **FR-012**: A posição móvel MUST ser acessível somente às contas participantes do atendimento
  ativo correspondente e MUST deixar de ser atualizada após conclusão, cancelamento ou perda da
  sessão autorizada.
- **FR-013**: O mapa público de descoberta MUST continuar usando o endereço empresarial para
  prestadores fixos e móveis; a localização móvel MUST NOT ser publicada nesse mapa.
- **FR-014**: A tela MUST representar carregamento, vazio, falha de estilo ou tiles, falta de
  conexão, localização recusada e sessão expirada com mensagens e ações de recuperação
  compreensíveis.
- **FR-015**: A ausência da localização do cliente MUST NOT bloquear o restante do aplicativo nem a
  consulta por uma região alternativa.
- **FR-016**: Os resultados e ações essenciais MUST possuir representação textual acessível, pois o
  mapa e seus marcadores MUST NOT ser a única forma de obter as informações.
- **FR-017**: A troca do renderizador MUST preservar o filtro por serviço, a privacidade da
  localização, os limites de acesso e a ausência de histórico de deslocamento já definidos.
- **FR-018**: O cadastro da empresa MUST NOT exibir mapa nem marcador para confirmação ou ajuste do
  endereço; a marcação de endereço desta feature MUST existir somente na descoberta e no
  acompanhamento autorizado.
- **FR-019**: Uma falha ao carregar ou interpretar o estilo configurado MUST NOT remover os
  resultados textuais, a seleção atual ou a última posição autorizada já recebida; o usuário MUST
  poder tentar carregar o mapa novamente.

### Key Entities *(include if feature involves data)*

- **Address Marker**: Representação visual de um prestador elegível na coordenada de seu endereço
  empresarial, associada somente às informações públicas necessárias à descoberta.
- **Live Provider Marker**: Representação temporária da última posição válida de um prestador móvel
  durante um atendimento autorizado, incluindo recência suficiente para distinguir posição atual
  de posição desatualizada.
- **Map Viewport**: Região geográfica visível usada para consultar e organizar resultados sem
  alterar a autorização ou a fonte das posições.
- **Map Style Configuration**: Configuração por ambiente que identifica o estilo compatível com
  MapLibre, suas fontes gerenciadas e as atribuições obrigatórias, sem armazenar segredos
  administrativos no aplicativo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos fluxos abrangidos, nenhum renderizador anterior é exibido depois da
  migração e os usuários continuam conseguindo visualizar os mesmos prestadores elegíveis.
- **SC-002**: Em 95% das aberturas com conectividade adequada, o cliente visualiza o mapa e o
  primeiro conjunto de marcadores ou um estado vazio em até 3 segundos.
- **SC-003**: Em 95% das atualizações válidas durante atendimentos ativos, a nova posição aparece
  para o cliente participante em até 10 segundos.
- **SC-004**: Em 100% dos casos sem atualização válida por mais de 30 segundos, a posição deixa de
  ser identificada como tempo real.
- **SC-005**: Em testes de autorização, nenhuma conta não participante consegue visualizar a
  posição móvel de um atendimento.
- **SC-006**: Em testes com marcadores coincidentes, 100% dos prestadores retornados permanecem
  selecionáveis ou acessíveis pela representação textual.
- **SC-007**: Os fluxos principais permanecem utilizáveis em smartphones suportados, paisagem e
  fonte ampliada a 200%, sem ações obrigatórias acessíveis somente pelo mapa.

## Assumptions

- A geocodificação de endereço já existente permanece fora desta substituição; esta feature troca
  a renderização do mapa e o consumo de tiles, reutilizando coordenadas válidas já produzidas.
- O cadastro da empresa continua validando e salvando o endereço sem apresentar mapa; esta feature
  não adiciona confirmação nem ajuste manual de coordenadas durante o cadastro.
- Um estilo compatível com MapLibre e suas fontes OSM gerenciadas serão escolhidos e configurados
  por ambiente antes da liberação em produção; indisponibilidade ou incompatibilidade devem ser
  tratadas como falha recuperável.
- A política vigente de descoberta continua exibindo o endereço empresarial de prestadores fixos e
  móveis; posição móvel em tempo real permanece restrita ao atendimento ativo e aos participantes.
- O intervalo-alvo de atualização é de aproximadamente 10 segundos e a posição passa a ser
  considerada desatualizada após 30 segundos, conforme o fluxo de localização existente.
- A feature não cria histórico de deslocamento nem suporte offline completo de mapas.

## Technical Constraints

- A renderização Android MUST usar MapLibre Native com estilo e fontes baseados em OpenStreetMap,
  integrada às telas Compose existentes.
- Google Maps Compose e osmdroid MUST ser removidos dos fluxos substituídos e não devem permanecer
  como fallback.

## Out of Scope

- Troca ou redesenho do serviço de geocodificação de endereços.
- Histórico, replay de rotas, navegação curva a curva ou cálculo de rotas.
- Disponibilização pública da localização móvel de prestadores.
- Hospedagem própria de tiles OSM nesta entrega.
- Download de mapas para uso offline.
