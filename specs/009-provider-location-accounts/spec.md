# Feature Specification: Contas independentes e localização de prestadores

**Feature Branch**: `[009-provider-location-accounts]`

**Created**: 2026-08-21

**Status**: Draft

**Input**: User description: "Separar cliente e empresa em contas independentes; cadastrar providers com categorias, endereço e geolocalização obrigatória; atualizar localização dinâmica de prestadores móveis e clientes; abrir mapa de providers ao selecionar um serviço na Home."

## Clarifications

### Session 2026-08-21

- Q: Se a mesma pessoa quiser usar o PitGo como cliente e como empresa, como a identidade de acesso deve funcionar? → A: Cada login possui somente um papel; para atuar nos dois, deve usar contas e e-mails diferentes.
- Q: Como um provider será definido como fixo ou móvel? → A: A empresa escolhe “fixo” ou “móvel” no cadastro e pode alterar posteriormente.
- Q: O que deve acontecer quando a localização do dispositivo estiver distante da coordenada obtida pelo endereço empresarial? → A: Até 500 metros, aceitar normalmente; acima disso, alertar e exigir confirmação explícita.
- Q: Com que frequência a posição de um provider móvel deve ser atualizada e quando ela deve ser considerada desatualizada durante um atendimento? → A: Atualizar aproximadamente a cada 10 segundos e considerar desatualizada após 30 segundos.
- Q: Como tratar providers já cadastrados pelo modelo antigo, no qual a empresa estava vinculada a um profile de cliente? → A: Manter o profile como cliente e oferecer migração assistida da empresa para uma nova conta empresarial.
- Q: Qual infraestrutura de tiles OSM deve ser usada em produção? → A: Provedor gerenciado de tiles OSM, configurável por ambiente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cadastrar e entrar como empresa (Priority: P1)

Como representante de uma empresa, quero usar a ação “Sou uma empresa · Cadastrar” para criar
uma conta de prestador, sem passar pelo cadastro de perfil de cliente, e entrar em uma área própria
da empresa.

**Why this priority**: A separação entre cliente e empresa corrige o fluxo de entrada atual e é a
base de autorização para cadastro, localização e oferta de serviços.

**Independent Test**: Partindo da Welcome sem sessão, tocar em “Sou uma empresa · Cadastrar”,
concluir um cadastro empresarial e confirmar que a sessão resultante é identificada como empresa,
sem criar nem abrir um perfil de cliente.

**Acceptance Scenarios**:

1. **Given** uma pessoa sem sessão, **When** toca em “Sou uma empresa · Cadastrar”, **Then** o
   sistema abre diretamente o cadastro empresarial, sem exibir o formulário de profile.
2. **Given** um cadastro empresarial válido, **When** ele é concluído, **Then** a conta entra como
   prestador e acessa a experiência da empresa.
3. **Given** uma conta de cliente autenticada, **When** acessa o aplicativo, **Then** ela continua
   reconhecida como cliente e não recebe automaticamente um cadastro de empresa.
4. **Given** uma conta de empresa autenticada, **When** acessa o aplicativo, **Then** ela não recebe
   automaticamente um profile de cliente nem a Home destinada a clientes.
5. **Given** credenciais inválidas ou sessão expirada, **When** a empresa tenta entrar, **Then** o
   sistema não abre nenhuma área autenticada e apresenta recuperação compreensível.
6. **Given** um profile que possui empresa pelo modelo anterior, **When** o proprietário inicia a
   migração assistida e comprova controle da conta cliente e da nova conta empresarial, **Then** os
   dados da empresa são transferidos integralmente, enquanto o profile permanece cliente.

---

### User Story 2 - Cadastrar empresa, categorias e endereço localizado (Priority: P1)

Como empresa autenticada, quero informar meus dados, selecionar todas as categorias em que atuo e
cadastrar meu endereço com coordenadas verificadas, para que clientes possam me encontrar pelos
serviços relevantes.

**Why this priority**: Uma empresa sem categorias ou localização não pode ser descoberta de forma
útil no marketplace.

**Independent Test**: Autenticar uma empresa nova, selecionar duas categorias existentes, informar
o endereço, conceder a permissão de localização e confirmar que empresa, categorias e endereço
ficam associados à mesma conta empresarial.

**Acceptance Scenarios**:

1. **Given** uma empresa autenticada sem cadastro concluído, **When** abre o formulário, **Then** vê
   as categorias existentes em um seletor e pode adicionar ou remover qualquer quantidade delas.
2. **Given** uma empresa com dados, ao menos uma categoria e endereço válidos, **When** autoriza a
   localização e confirma, **Then** o cadastro é salvo por inteiro com latitude e longitude.
3. **Given** uma empresa que não concedeu a permissão obrigatória, **When** tenta concluir o
   cadastro, **Then** o sistema explica por que a localização é necessária e não salva um cadastro
   incompleto.
4. **Given** uma categoria já selecionada, **When** a empresa tenta adicioná-la novamente, **Then**
   o sistema impede a duplicidade sem apagar as demais escolhas.
5. **Given** uma falha ao obter coordenadas ou salvar qualquer parte do cadastro, **When** a
   confirmação termina, **Then** nenhuma parte inconsistente é tratada como cadastro concluído.
6. **Given** o endereço digitado e a posição atual do dispositivo, **When** a empresa tenta
   confirmar, **Then** o sistema aceita normalmente uma distância de até 500 metros; acima disso,
   exibe a divergência e exige confirmação explícita antes de salvar a coordenada do endereço.
7. **Given** uma empresa no cadastro, **When** informa sua modalidade, **Then** deve escolher
   explicitamente entre provider fixo e provider móvel antes de concluir.

---

### User Story 3 - Publicar localização de prestador móvel (Priority: P2)

Como prestador que trabalha em um veículo, quero publicar minha posição atual durante um atendimento
aceito, para que o cliente daquele atendimento acompanhe minha localização recente.

**Why this priority**: Prestadores móveis não podem ser representados corretamente apenas pelo
endereço fixo da empresa.

**Independent Test**: Marcar uma empresa elegível como móvel, aceitar um atendimento e deslocar o
dispositivo; o cliente daquele atendimento deve ver apenas a posição recente, e a posição deve
deixar de ser atualizada ao encerrar ou cancelar o atendimento.

**Acceptance Scenarios**:

1. **Given** um prestador móvel com permissão válida, **When** aceita um atendimento, **Then** sua
   posição publicada é atualizada e identificada como localização móvel para o cliente relacionado.
2. **Given** um prestador fixo, **When** aparece no mapa, **Then** usa as coordenadas do endereço
   cadastrado e não inicia rastreamento contínuo.
3. **Given** localização desativada, permissão revogada ou posição antiga, **When** o prestador está
   marcado como móvel, **Then** ele não é apresentado como localização atual confiável.
4. **Given** um atendimento ativo sem atualização válida há mais de 30 segundos, **When** o cliente
   observa o mapa, **Then** a posição deixa de ser indicada como tempo real e o estado de atualização
   fica explícito.
5. **Given** a empresa conclui/cancela o atendimento ou sai da conta, **When** o encerramento é
   confirmado, **Then** novas atualizações deixam de ser publicadas.
6. **Given** o aplicativo deixa de estar visível, **When** existe um atendimento aceito e ainda
   ativo, **Then** o rastreamento pode continuar em segundo plano com indicação persistente e deve
   parar obrigatoriamente no encerramento do atendimento.

---

### User Story 4 - Encontrar providers no mapa pelo serviço (Priority: P2)

Como cliente autenticado, quero tocar em um serviço da Home e ver no mapa os providers compatíveis,
com distância baseada no endereço empresarial e, durante meu atendimento aceito, acompanhar a
posição móvel ou seu estado desatualizado, para escolher e acompanhar uma opção adequada.

**Why this priority**: Esse é o principal valor de marketplace criado pelas categorias e pela
localização dos prestadores.

**Independent Test**: Com providers fixos e móveis vinculados a categorias diferentes, tocar em um
card da Home e confirmar que o mapa mostra somente os providers compatíveis e autorizados, usando
a localização mais recente aplicável.

**Acceptance Scenarios**:

1. **Given** um cliente autenticado e com localização autorizada, **When** toca em um card de
   serviço, **Then** abre um mapa centralizado na região do cliente e filtrado pelo serviço.
2. **Given** providers compatíveis fixos e móveis sem atendimento com o cliente, **When** o mapa de
   descoberta carrega, **Then** cada provider aparece pelas coordenadas do endereço empresarial.
3. **Given** nenhum provider compatível ou próximo, **When** a busca termina, **Then** o mapa mostra
   um estado vazio e permite ampliar a área ou tentar novamente.
4. **Given** o cliente recusa a localização, **When** abre o mapa, **Then** pode visualizar a região
   do serviço/endereço informado sem publicar sua posição, salvo se a localização for indispensável
   para a ação seguinte.
5. **Given** um cliente participante de atendimento aceito com provider móvel, **When** uma
   atualização válida é recebida, **Then** o marcador muda sem exigir reabrir a tela.
6. **Given** um card da Home e categorias/ofertas existentes, **When** o mapa filtra providers,
   **Then** aparecem somente providers que selecionaram a categoria correspondente e também o
   serviço específico representado pelo card.

---

### User Story 5 - Controlar localização e privacidade do cliente (Priority: P3)

Como cliente, quero entender quando minha localização está sendo usada e interromper seu
compartilhamento, para utilizar o marketplace sem rastreamento invisível.

**Why this priority**: Localização é dado sensível e precisa de controle claro, mesmo quando melhora
busca, distância e atendimento.

**Independent Test**: Conceder e revogar localização em diferentes estados do aplicativo,
confirmando que a posição do cliente só é usada para finalidades visíveis e deixa de ser atualizada
quando não é necessária.

**Acceptance Scenarios**:

1. **Given** um cliente sem permissão, **When** uma funcionalidade precisa de localização, **Then**
   o sistema explica a finalidade antes de solicitar autorização.
2. **Given** permissão concedida, **When** o cliente usa mapa ou atendimento, **Then** vê uma
   indicação de que sua posição está em uso.
3. **Given** permissão revogada ou localização desligada, **When** o sistema tenta atualizar a
   posição, **Then** interrompe a atualização e oferece uma alternativa compatível com o fluxo.
4. **Given** uma posição anterior do cliente, **When** ela deixa de ser necessária, **Then** não é
   mantido um histórico de deslocamento por esta feature.

### Edge Cases

- A mesma pessoa tenta usar um e-mail já vinculado a uma conta cliente para cadastrar uma empresa,
  ou vice-versa; o sistema rejeita sem revelar detalhes adicionais da conta existente e orienta o
  uso de outro e-mail.
- Uma sessão antiga não contém informação confiável do tipo de conta.
- O cadastro empresarial já existe parcialmente por causa do fluxo anterior vinculado a profile.
- A lista de categorias está vazia, indisponível ou muda durante o cadastro.
- Uma categoria selecionada é desativada antes da confirmação.
- O dispositivo concede localização aproximada quando a precisão necessária é maior.
- A localização é obtida, mas está antiga, com precisão insuficiente ou fora do endereço informado.
- O sistema operacional encerra a atualização de localização de um provider móvel.
- O provider perde conexão durante deslocamento e volta a ficar online depois.
- Dois dispositivos publicam localização para a mesma conta empresarial.
- A localização móvel fica antiga enquanto o provider ainda aparece disponível.
- O cliente muda rapidamente de serviço ou área do mapa e respostas antigas chegam depois.
- O mapa contém muitos providers próximos ou vários marcadores na mesma coordenada.
- O usuário revoga permissão nas configurações enquanto a tela está aberta.
- A conta sai, troca de usuário ou é desativada enquanto localização está sendo publicada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST tratar conta cliente e conta empresa como tipos autenticados
  independentes, com sessão, autorização e destino inicial próprios.
- **FR-002**: A ação “Sou uma empresa · Cadastrar” MUST abrir diretamente o fluxo de cadastro de
  empresa e MUST NOT criar ou exigir um profile de cliente.
- **FR-003**: O cadastro comum de cliente MUST continuar criando/abrindo apenas o profile de cliente
  e MUST NOT criar automaticamente um provider.
- **FR-004**: Uma sessão autenticada MUST possuir exatamente um tipo efetivo de conta para fins de
  autorização: cliente ou empresa; estados ausentes/ambíguos MUST bloquear a área autenticada.
- **FR-004A**: Um e-mail MUST pertencer a apenas um tipo de conta; para atuar como cliente e empresa,
  a pessoa MUST usar contas com e-mails diferentes, sem troca de papel dentro da mesma sessão.
- **FR-005**: Rotas, dados e ações da empresa MUST ser acessíveis somente por uma sessão empresarial;
  rotas e dados privados do cliente MUST ser acessíveis somente pela conta cliente correspondente.
- **FR-006**: O sistema MUST oferecer login e logout coerentes para ambos os tipos e MUST limpar
  dados visíveis/localização ao encerrar ou trocar a sessão.
- **FR-007**: Cadastros do modelo anterior MUST permanecer associados ao profile cliente até uma
  migração assistida ser iniciada pelo proprietário; o sistema MUST NOT converter a conta
  automaticamente nem apagar dados empresariais.
- **FR-007A**: A migração assistida MUST exigir comprovação de controle do profile cliente original
  e de uma nova conta empresarial com outro e-mail.
- **FR-007B**: A migração MUST transferir provider, endereço, categorias e serviços como uma única
  operação consistente e repetível, preservando o profile original exclusivamente como cliente.
- **FR-007C**: Antes da migração ser concluída, a associação empresarial legada MUST NOT conceder ao
  profile cliente acesso às novas ações privadas de empresa.
- **FR-008**: O cadastro de provider MUST solicitar os dados empresariais obrigatórios definidos
  pelo produto e MUST associá-los exclusivamente à conta empresarial autenticada.
- **FR-009**: O formulário MUST listar somente categorias existentes e disponíveis no catálogo.
- **FR-010**: A empresa MUST poder adicionar e remover categorias sem um limite artificial de
  quantidade e MUST selecionar ao menos uma antes de concluir.
- **FR-011**: A mesma categoria MUST NOT ser associada mais de uma vez ao mesmo provider.
- **FR-012**: Categoria digitada livremente ou criada pelo provider MUST NOT ser aceita neste fluxo.
- **FR-012A**: Para cada categoria selecionada, a empresa MUST escolher um ou mais serviços
  específicos existentes naquela categoria.
- **FR-012B**: O mesmo serviço MUST NOT ser associado mais de uma vez ao mesmo provider, e um
  serviço MUST NOT ser associado fora de sua categoria de origem.
- **FR-013**: O provider MUST informar um endereço com rua, número, bairro, CEP, cidade, estado,
  país e complemento opcional.
- **FR-014**: O endereço empresarial salvo MUST pertencer ao provider autenticado e conter latitude
  e longitude válidas.
- **FR-015**: O cadastro inicial MUST exigir autorização de localização no momento em que ela se
  torna necessária e MUST explicar finalidade, precisão e consequência da recusa.
- **FR-015A**: A coordenada inicial MUST considerar tanto o endereço convertido em coordenadas
  quanto a posição atual autorizada do dispositivo, apresentando divergência relevante para
  confirmação/correção em vez de escolher silenciosamente uma fonte.
- **FR-015B**: Distância de até 500 metros entre endereço e dispositivo MUST ser aceita normalmente;
  distância superior MUST exigir alerta e confirmação explícita, mantendo a coordenada do endereço
  como referência empresarial.
- **FR-016**: Sem a localização obrigatória válida, o cadastro empresarial MUST permanecer
  incompleto e MUST oferecer tentativa novamente ou acesso às configurações apropriadas.
- **FR-017**: Provider, categorias e endereço MUST ser confirmados como um único cadastro
  consistente; falha em qualquer parte MUST NOT produzir sucesso parcial.
- **FR-018**: Repetir a confirmação após timeout ou resposta perdida MUST NOT duplicar provider,
  endereço ou categorias.
- **FR-019**: O sistema MUST distinguir provider fixo de provider móvel e MUST usar o endereço
  cadastrado como posição de providers fixos.
- **FR-019B**: A empresa MUST escolher explicitamente a modalidade fixa ou móvel no cadastro e MUST
  poder alterá-la posteriormente; somente a modalidade móvel pode publicar posição durante um
  atendimento aceito.
- **FR-019A**: Antes de existir atendimento aceito, a descoberta de um provider móvel MUST usar a
  localização do endereço empresarial e MUST NOT expor sua posição corrente.
- **FR-020**: Provider móvel MUST publicar somente a posição mais recente necessária durante um
  atendimento aceito e ativo, acompanhada de horário de atualização e qualidade suficiente para
  acompanhamento.
- **FR-020A**: Durante atendimento ativo, a atualização MUST ter alvo aproximado de 10 segundos;
  após 30 segundos sem nova posição válida, a coordenada MUST ser marcada como desatualizada e MUST
  NOT ser apresentada como tempo real.
- **FR-021**: Uma posição móvel vencida, imprecisa ou obtida sem autorização MUST NOT ser
  apresentada como localização atual.
- **FR-022**: O provider MUST conseguir identificar quando está publicando localização; a publicação
  MUST começar apenas após aceite do atendimento e parar em conclusão, cancelamento ou saída.
- **FR-023**: O sistema MUST interromper publicação ao sair da conta, trocar usuário, perder a
  autorização necessária ou desativar a empresa.
- **FR-024**: O sistema MUST resolver concorrência de múltiplos dispositivos sem regredir para uma
  posição mais antiga.
- **FR-025**: O cliente MUST ter sua posição atualizada somente quando uma experiência visível a
  exigir, como busca no mapa ou atendimento ativo, respeitando autorização e finalidade.
- **FR-025A**: A localização móvel durante atendimento MUST ser visível somente às contas
  participantes daquele atendimento e não ao público geral do mapa.
- **FR-026**: Esta feature MUST NOT criar histórico de deslocamento de clientes ou providers; apenas
  a posição atual aplicável e seus metadados de validade podem ser mantidos.
- **FR-027**: Ao tocar em um serviço da Home, o cliente MUST abrir um mapa filtrado por
  compatibilidade com aquele serviço.
- **FR-028**: O mapa de descoberta MUST mostrar providers compatíveis pelo endereço empresarial; o
  acompanhamento de atendimento MUST usar posição móvel válida e diferenciar visualmente o tipo e
  a recência sem depender somente de cor.
- **FR-029**: O mapa MUST reagir a novas posições válidas sem exigir que o cliente feche e reabra a
  tela.
- **FR-030**: Providers indisponíveis, inativos, incompatíveis ou com posição móvel inválida MUST NOT
  aparecer como opções disponíveis.
- **FR-031**: O cliente MUST poder atualizar a busca, ampliar/reduzir a área e compreender estados de
  carregamento, vazio, falta de permissão, offline, localização desativada e falha temporária.
- **FR-032**: A ausência de permissão do cliente MUST NOT bloquear o restante do aplicativo; o mapa
  MUST oferecer uma região/endereço alternativo quando possível.
- **FR-033**: O sistema MUST aplicar o menor acesso necessário: uma empresa altera somente seus
  dados/localização; clientes leem apenas dados públicos indispensáveis à descoberta.
- **FR-034**: Dados empresariais sensíveis, posição de cliente, identificadores internos e posição
  móvel fora de disponibilidade MUST NOT ser expostos no mapa.
- **FR-035**: Toda tela desta feature MUST ser responsiva, acessível, compatível com texto ampliado e
  apresentar controles/erros de permissão e localização de forma compreensível.
- **FR-036**: Os mapas MUST ser renderizados com OpenStreetMap por meio do osmdroid e MUST NOT usar
  Google Maps Compose. Em produção, os tiles MUST vir de um provedor OSM gerenciado, configurável
  por ambiente, com atribuição visível e sem depender dos servidores públicos padrão do
  OpenStreetMap.

### Key Entities

- **Client Account**: Identidade autenticada de cliente; possui autorização e experiência próprias,
  podendo ter um profile sem relação de propriedade com provider.
- **Provider Account**: Identidade autenticada de empresa; possui autorização e experiência de
  prestador e é proprietária do cadastro empresarial.
- **Profile**: Dados pessoais do cliente. Não é requisito nem proprietário de uma empresa.
- **Provider**: Empresa prestadora, independente de Profile, com dados empresariais, modalidade
  fixa/móvel escolhida pela própria empresa, disponibilidade e relação com uma conta empresarial.
- **Service Category**: Classificação existente do catálogo que uma empresa pode selecionar para
  declarar sua área de atuação.
- **Provider Category**: Associação única entre provider e categoria selecionada, usada para
  organizar as ofertas da empresa.
- **Provider Service**: Associação entre provider e serviço específico pertencente a uma das
  categorias selecionadas; define a compatibilidade com os cards e buscas do cliente.
- **Address**: Endereço inicial do provider, com coordenadas verificadas e vínculo exclusivo à
  empresa.
- **Current Location**: Última posição aplicável de cliente ou provider móvel, com horário,
  precisão, origem e validade; não representa histórico.
- **Provider Availability**: Indica se o provider pode aparecer como disponível e, quando móvel, se
  está autorizado a publicar localização.
- **Provider Ownership Migration**: Processo assistido e auditável que transfere uma empresa legada
  do vínculo com um profile cliente para uma nova conta empresarial, sem copiar o papel de cliente.
- **Service Map Result**: Visão pública mínima de um provider compatível, contendo informações para
  exibição, posição aplicável e recência, sem dados privados.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% das tentativas pela ação “Sou uma empresa · Cadastrar”, o primeiro formulário
  autenticável exibido é o empresarial, nunca o profile de cliente.
- **SC-002**: Pelo menos 90% das empresas com dados e permissão válidos concluem cadastro,
  categorias e endereço em até 5 minutos na primeira tentativa.
- **SC-003**: Em 100% dos cadastros empresariais concluídos, existe exatamente uma conta empresarial
  proprietária, um endereço localizado e ao menos uma categoria, sem profile criado por efeito
  colateral.
- **SC-004**: Repetições e confirmações concorrentes não produzem duplicidade em 100% dos cenários de
  timeout, resposta perdida e toque repetido validados.
- **SC-004A**: Em 100% das validações com divergência superior a 500 metros, a empresa vê a distância
  e precisa confirmar explicitamente antes de concluir.
- **SC-004B**: Em 100% das migrações legadas concluídas, a empresa passa a ter uma única conta
  empresarial proprietária e o profile original permanece somente cliente, sem perda de endereço
  ou ofertas.
- **SC-005**: Durante atendimentos aceitos e ativos, providers móveis têm sua nova posição refletida
  para o cliente participante em até 10 segundos em 95% das atualizações com conectividade adequada.
- **SC-006**: Nenhuma posição móvel sem atualização válida há mais de 30 segundos é apresentada
  como “em tempo real”.
- **SC-007**: Clientes visualizam o primeiro conjunto de providers compatíveis ou um estado vazio
  compreensível em até 3 segundos em 95% das buscas com conectividade adequada.
- **SC-008**: Em testes de autorização, 100% das tentativas de uma conta acessar ou alterar dados
  privados de outra conta são bloqueadas.
- **SC-009**: Em testes de privacidade, nenhuma sessão encerrada continua publicando posição e
  nenhuma tela expõe histórico de deslocamento.
- **SC-010**: Todos os fluxos principais permanecem utilizáveis em smartphones suportados, paisagem
  e fonte ampliada a 200%, sem controles obrigatórios inacessíveis.

## Assumptions

- Cada identidade autenticada possui um único tipo de conta efetivo. Uma pessoa que precise atuar
  como cliente e empresa usa identidades e e-mails diferentes; troca de papel não faz parte do
  modelo atual.
- A localização inicial é requisito essencial do cadastro empresarial; a recusa impede somente a
  conclusão desse cadastro, não o acesso às telas públicas de entrada e ajuda.
- Providers fixos utilizam as coordenadas verificadas do endereço. Providers móveis usam a última
  localização válida somente durante um atendimento aceito e ativo; fora dele, a descoberta usa o
  endereço empresarial.
- A coordenada inicial do endereço é comparada com a posição atual do dispositivo. Divergências
  não são resolvidas silenciosamente e exigem confirmação ou correção pela empresa.
- “Tempo real” representa atualizações recentes, não rastreamento contínuo sem finalidade nem
  garantia de intervalo exato sob restrições do dispositivo.
- O catálogo de categorias é administrado fora deste fluxo; empresas apenas selecionam registros
  existentes e, dentro deles, os serviços específicos que oferecem.
- A feature mantém somente a última posição necessária e não implementa relatório, rota percorrida
  ou histórico de localização.
- Geocodificação, mapas e localização dependem de serviços externos disponíveis e de configuração
  válida do ambiente, a ser definida no planejamento.
- O provedor gerenciado de tiles OSM e suas credenciais serão configurados por ambiente; os
  servidores públicos padrão do OpenStreetMap não serão usados como infraestrutura de produção.

## Out of Scope

- Criação ou edição do catálogo de categorias por empresas.
- Histórico de localização, replay de rotas ou monitoramento invisível.
- Despacho, aceite, pagamento e acompanhamento completo de um atendimento.
- Definição de frota com múltiplos dispositivos/funcionários por empresa além da resolução mínima
  de concorrência da posição atual.
- Conversão automática e silenciosa de profiles existentes em providers.
