# Feature Specification: Refinamento do fluxo de Guincho

**Feature Branch**: `feature/refinamento-guincho`

**Created**: 2026-09-30

**Status**: Draft — validada e pronta para planejamento

**Input**: User description: "Guincho com escolha no mapa e mini card de nome, valor e contratar; pagamento após contratar; cliente vê apenas total com preço, 10% e taxas; guincheiro vê apenas seu valor e aceita a solicitação; considerar R$ 3/km do cliente ao destino; liberar nome, carro e endereço após aprovação do pagamento; conclusão pelo guincheiro confirmada pelo cliente e retorno de ambos ao estado inicial."

## Clarifications

### Session 2026-09-30

- Q: Como cobrar o percurso da retirada até o destino? → A: R$ 3/km como tarifa inicial configurável; aplicar 10% sobre preço-base mais adicional por km e depois somar as taxas (opção A).
- Q: Em que momento o cliente deve informar o destino do veículo para calcular o valor mostrado no mini card? → A: Confirmar retirada, destino e veículo ao abrir o mapa, antes de selecionar um guincheiro (opção A).
- Q: Como o guincheiro deve confirmar a chegada ao cliente e o início do transporte? → A: O guincheiro toca em “Cheguei” e depois em “Iniciar transporte”; o cliente acompanha os estados, sem precisar confirmar o início (opção A).
- Q: O que deve acontecer se o cliente disser que o serviço não deu certo ao receber o card de conclusão? → A: Oferecer Informar problema com motivo obrigatório e colocar o atendimento em contestação para resolução pelo suporte (opção A).
- Q: Quais decisões o suporte poderá tomar para resolver uma contestação do Guincho? → A: Suporte autorizado resolve no dashboard, concluindo sem reembolso ou cancelando com reembolso integral, com justificativa obrigatória (opção A).
- Q: O guincheiro poderá receber novos pedidos enquanto um atendimento estiver em contestação? → A: Sim. Liberar novos pedidos durante a contestação, mantendo o caso acessível para acompanhamento (opção A).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Escolher um guincheiro no mapa (Priority: P1)

O cliente abre a categoria Guincho na Home, confirma retirada, destino e veículo no contexto do mapa e então seleciona entre os guinchos disponíveis ao redor. O mapa mantém espaço suficiente para comparar os prestadores mesmo com o mini card aberto.

**Why this priority**: A escolha explícita do profissional substitui uma contratação sem visibilidade sobre quem atenderá o cliente.

**Independent Test**: Abrir Guincho com prestadores elegíveis próximos e tocar em dois ícones diferentes, verificando o mapa e o mini card de cada profissional, sem efetuar pagamento.

**Acceptance Scenarios**:

1. **Given** cliente autenticado e guincheiros disponíveis na região, **When** tocar no card Guincho, **Then** verá um mapa com ícones dos prestadores elegíveis ao redor de sua localização.
2. **Given** o mapa aberto, **When** tocar em um ícone, **Then** verá um mini card com nome do prestador, valor total ao cliente e botão Contratar.
3. **Given** um prestador selecionado, **When** tocar em outro ícone, **Then** o mini card passará a representar exclusivamente o novo prestador.
4. **Given** ausência de guincheiros elegíveis, **When** abrir o mapa, **Then** verá uma mensagem de indisponibilidade e poderá atualizar a busca ou voltar à Home.
5. **Given** retirada, destino ou veículo ainda não confirmados, **When** abrir o mapa, **Then** deverá confirmar esses dados antes de selecionar um guincheiro; após confirmação, cada mini card mostrará o total final calculado para o percurso informado.

---

### User Story 2 - Pagar o valor apresentado para o guincheiro escolhido (Priority: P1)

Após confirmar veículo, retirada e destino ao abrir o mapa, o cliente seleciona um guincheiro e segue para pagamento ao contratar. Ele vê somente o total final, enquanto a remuneração do guincheiro permanece separada do acréscimo de 10% e das taxas do cliente.

**Why this priority**: A contratação precisa respeitar o valor apresentado e o prestador escolhido, sem cobrar ou divulgar dados antes da etapa correta.

**Independent Test**: Utilizar uma cotação conhecida e verificar o total em seleção e pagamento; reproduzir pagamento pendente, aprovado e recusado, conferindo a ausência de contratação duplicada.

**Acceptance Scenarios**:

1. **Given** uma seleção válida e dados de transporte completos, **When** tocar em Contratar, **Then** abrirá o pagamento vinculado ao guincheiro selecionado e ao total apresentado.
2. **Given** falta de veículo, retirada ou destino, **When** abrir o mapa, **Then** deverá completar e confirmar os dados antes de selecionar um guincheiro ou contratar.
3. **Given** preço-base do prestador de R$ 100, percurso de 10 km, tarifa de R$ 3/km e taxas configuradas de R$ 5, **When** visualizar a cotação, **Then** verá apenas R$ 148,00 como total (R$ 130 + R$ 13 + R$ 5), sem detalhamento de acréscimos ou taxas.
4. **Given** pagamento pendente ou recusado, **When** o prestador consultar solicitações, **Then** não receberá um atendimento liberado nem os dados identificáveis desse cliente.
5. **Given** pagamento aprovado, **When** o cliente retomar o aplicativo, **Then** verá a contratação já paga e aguardando aceite, sem novo pagamento.

---

### User Story 3 - Aceitar o serviço pago com dados e valor do guincheiro (Priority: P1)

O guincheiro selecionado recebe a solicitação paga, com nome do cliente, veículo, endereço de retirada e destino. A tela de aceite apresenta somente o valor que será pago ao guincheiro, sem os 10% e as taxas cobradas do cliente.

**Why this priority**: O profissional precisa saber onde atender, qual veículo transportar e quanto receberá para decidir se aceita o serviço.

**Independent Test**: Preparar uma solicitação paga para um prestador específico e conferir valores, dados liberados, aceite, recusa e impossibilidade de outro profissional aceitar o mesmo pedido.

**Acceptance Scenarios**:

1. **Given** pagamento aprovado para um guincheiro, **When** ele abrir a solicitação, **Then** verá seu valor a receber, nome do cliente, veículo, retirada, destino e ações de aceite e recusa.
2. **Given** a cotação de R$ 148,00 do cenário anterior, **When** o guincheiro consultar a solicitação, **Then** verá R$ 130,00 como remuneração (preço-base mais quilometragem), sem os R$ 13,00 de acréscimo e os R$ 5,00 de taxas.
3. **Given** o guincheiro aceita, **When** o cliente consultar o pedido, **Then** verá o aceite do profissional escolhido e poderá acompanhar o andamento.
4. **Given** recusa, expiração do aceite ou indisponibilidade definitiva após pagamento, **When** o pedido não puder ser atendido, **Then** o cliente será informado e o reembolso será iniciado conforme a política existente, sem transferência automática para outro guincheiro.
5. **Given** pedido aceito e pago, **When** o guincheiro tocar em Cheguei, **Then** ambos verão que ele chegou à retirada e o guincheiro poderá iniciar o transporte.
6. **Given** chegada registrada, **When** o guincheiro tocar em Iniciar transporte, **Then** ambos verão o transporte em andamento, sem exigir confirmação de início pelo cliente; só nessa etapa será possível solicitar a conclusão.

---

### User Story 4 - Encerrar o serviço com confirmação do cliente (Priority: P1)

O guincheiro informa que concluiu o transporte. O cliente recebe um card para confirmar que deu tudo certo ou informar um problema. No fluxo normal, enquanto a confirmação não ocorrer, ambos retomam o atendimento pendente; após a confirmação, voltam à tela inicial de seus respectivos perfis. Se houver contestação, o suporte decide o encerramento e o guincheiro pode atender novos pedidos enquanto acompanha o caso separadamente.

**Why this priority**: Evita encerrar o atendimento sem a confirmação solicitada pelo cliente e mantém os dois lados consistentes.

**Independent Test**: Partir de um serviço em andamento, solicitar conclusão pelo guincheiro, fechar e reabrir ambos os aplicativos e confirmar pelo cliente, verificando o retorno às telas iniciais.

**Acceptance Scenarios**:

1. **Given** transporte em andamento, **When** o guincheiro tocar em Concluir atendimento, **Then** o pedido aguardará confirmação do cliente e o guincheiro verá esse estado na tela do atendimento.
2. **Given** conclusão solicitada, **When** o cliente visualizar o atendimento, **Then** verá automaticamente um card com a ação para confirmar que deu tudo certo, mesmo com os detalhes do mapa recolhidos.
3. **Given** confirmação ainda pendente, **When** qualquer participante sair e retornar ao aplicativo, **Then** retomará o atendimento pendente, sem repetir cobrança ou conclusão.
4. **Given** o cliente confirmar a conclusão, **When** ambos receberem o resultado, **Then** o pedido será concluído e os dois retornarão ao estado inicial, liberando o guincheiro para novos pedidos conforme sua disponibilidade.
5. **Given** repetição do toque ou perda de conexão, **When** a ação for reenviada, **Then** o pedido manterá um único resultado e apresentará progresso ou falha acionável.
6. **Given** conclusão solicitada e cliente insatisfeito, **When** tocar em Informar problema e enviar um motivo não vazio, **Then** o pedido entrará em contestação, o suporte receberá o caso e ambos verão que aguardam resolução, sem conclusão nem reembolso automático.
7. **Given** contestação aberta, **When** o suporte autorizado escolher Concluir sem reembolso no dashboard e registrar justificativa não vazia, **Then** a contestação e o atendimento serão encerrados com decisão, responsável e momento registrados, e os participantes verão o resultado.
8. **Given** contestação aberta, **When** o suporte autorizado escolher Cancelar com reembolso integral e registrar justificativa não vazia, **Then** o atendimento será cancelado, o reembolso integral será solicitado uma única vez e o cliente poderá acompanhar seu resultado.
9. **Given** usuário sem autorização ou justificativa vazia, **When** tentar resolver uma contestação, **Then** nenhuma decisão ou alteração de pagamento será efetuada.
10. **Given** atendimento em contestação e guincheiro disponível, **When** surgir outro pedido elegível, **Then** o guincheiro poderá recebê-lo e aceitá-lo; a contestação anterior continuará acessível separadamente, sem substituir o novo atendimento ativo.

### Edge Cases

- Localização recusada, desativada ou imprecisa: orientar o cliente a corrigir ou confirmar um ponto de retirada válido; não bloquear áreas que dispensam localização.
- Prestador deixa de estar disponível ou muda o preço antes da cobrança: atualizar a seleção ou cotação e exigir nova confirmação do cliente antes de pagar.
- Destino não informado ou rota impossível de calcular: impedir cobrança por quilometragem sem um valor válido e explicar como corrigir os dados.
- Alteração de retirada, destino ou veículo após cotação: exigir recotação antes do pagamento; alterações após pagamento ficam fora deste refinamento.
- Pagamento aprovado após o cliente sair do aplicativo: restaurar o pedido existente; não criar uma segunda cobrança.
- Aprovação duplicada ou toque simultâneo: criar no máximo uma solicitação para o pagamento e permitir somente um aceite.
- Guincheiro recusa ou não responde após pagamento: informar o cliente e acompanhar o reembolso, mantendo a seleção de outro prestador como nova contratação explícita.
- Cliente não confirma a conclusão: manter o atendimento pendente; nenhuma aprovação automática será introduzida neste escopo.
- Cliente informa que algo não deu certo: exigir motivo não vazio, registrar a contestação e encaminhar ao suporte; ambos acompanham o estado de contestação, sem conclusão ou reembolso automático, e o guincheiro fica livre para novos pedidos conforme sua disponibilidade.
- Contestação antiga resolvida durante um novo atendimento: atualizar apenas o caso resolvido, sem interromper, substituir ou fechar a tela do novo atendimento.
- Tentativa de informar problema sem motivo: manter o card de conclusão e pedir o preenchimento, sem criar contestação vazia.
- Falha na atualização de estado: apresentar erro recuperável e permitir retomar a ação, sem mostrar sucesso falso.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A Home MUST apresentar a categoria Guincho quando houver prestador ativo cadastrado nessa categoria; a abertura MUST mostrar o mapa, antes do pagamento.
- **FR-002**: O mapa MUST exibir guincheiros ativos, disponíveis e elegíveis na região da retirada, sem misturar profissionais de outras modalidades.
- **FR-003**: Selecionar um ícone MUST abrir um mini card com nome, valor total ao cliente e botão Contratar, mantendo o mapa utilizável.
- **FR-004**: A contratação MUST permanecer vinculada ao prestador selecionado; nenhum despacho automático para outro prestador será iniciado.
- **FR-005**: Ao abrir o mapa, a retirada MUST ser preenchida automaticamente a partir da localização atual, sem campo de endereço de retirada editável. O cliente MUST informar o destino do veículo e confirmar retirada, destino e veículo transportado antes de selecionar um guincheiro. Se a localização estiver indisponível, poderá marcar a retirada no mapa; o endereço será obtido automaticamente ou o ponto será identificado por suas coordenadas. Dados válidos existentes poderão ser reutilizados mediante confirmação. Só depois dessa etapa o mini card MUST apresentar o total final e permitir contratar; dados incompletos MUST impedir seleção e pagamento.
- **FR-006**: A cotação MUST distinguir preço-base do prestador, eventual adicional por km, acréscimo de 10%, taxas, remuneração do guincheiro e total do cliente, mantendo esses valores fixos para a contratação após o pagamento.
- **FR-007**: As telas de seleção e pagamento do cliente MUST apresentar somente o total final a pagar; o aplicativo do guincheiro MUST apresentar somente sua remuneração, sem os 10% nem as taxas do cliente.
- **FR-008**: As taxas MUST seguir as regras de preços configuráveis existentes; nenhuma nova taxa fixa será presumida. Valores finais MUST ser expressos em reais com duas casas decimais.
- **FR-009**: O adicional por distância MUST estar habilitado inicialmente a R$ 3/km, com tarifa configurável, considerando o percurso viário da retirada até o destino do veículo, sem incluir o deslocamento do guincheiro até o cliente. A remuneração do guincheiro MUST ser preço-base + (distância em km × tarifa). O total do cliente MUST ser essa remuneração acrescida de 10% e das taxas configuradas. A distância MUST admitir frações de km, sem arredondamento para km inteiro; o adicional e o acréscimo de 10% MUST ser arredondados ao centavo mais próximo antes da soma, com meio centavo arredondado para cima. A tarifa e os valores cotados MUST permanecer fixos para o pedido pago, mesmo que a configuração mude depois.
- **FR-010**: Contratar MUST levar ao pagamento somente após validar seleção, dados e cotação; qualquer alteração no total anteriormente exibido MUST exigir nova confirmação antes de cobrar.
- **FR-011**: Pagamento pendente, falho ou recusado MUST ter estado e ações recuperáveis explícitos; não pode liberar a execução ou divulgar nome, veículo e endereço ao prestador.
- **FR-012**: Após aprovação do pagamento, o guincheiro selecionado MUST receber uma solicitação para aceitar ou recusar com nome do cliente, veículo, retirada, destino e remuneração contratada. No aplicativo, somente ele e o cliente participante poderão consultar esses dados. Como exceção, suporte ativo autorizado pelas permissões existentes do dashboard poderá consultar o contexto necessário à resolução de contestações prevista em FR-022, mediante projeção própria e auditoria, sem conceder acesso a outros clientes ou prestadores.
- **FR-013**: O aceite MUST liberar o acompanhamento do atendimento para ambos; recusa ou expiração MUST cancelar a prestação e iniciar o reembolso conforme as regras existentes, sem cobrança duplicada.
- **FR-014**: Somente o guincheiro responsável MUST poder solicitar a conclusão de um serviço em andamento; a solicitação MUST mudar o pedido para aguardando confirmação do cliente.
- **FR-015**: Somente o cliente responsável MUST poder confirmar a conclusão; o card de confirmação MUST ficar visível sem depender de expandir detalhes.
- **FR-016**: Enquanto aguarda confirmação, o guincheiro MUST permanecer ou voltar à tela de conclusão com mensagem de espera; o cliente MUST retomar o card de confirmação, inclusive após reabrir o aplicativo.
- **FR-017**: No fluxo normal, somente após a confirmação do cliente o atendimento MUST ser concluído e ambos MUST retornar às telas iniciais, sem atendimento ativo residual ou confirmação automática. Em contestação, a decisão do suporte prevista em FR-022 MUST substituir a confirmação para encerrar o pedido, informando o resultado aos participantes.
- **FR-018**: Pagamento, aceite, conclusão e confirmação MUST tolerar repetição de eventos e ações sem duplicar cobrança, atendimento ou transição; falhas MUST gerar feedback acionável.
- **FR-019**: As mudanças MUST preservar os demais fluxos de serviço e as regras atuais dos ambientes de teste e produção.
- **FR-020**: Após o aceite, o atendimento MUST indicar guincheiro a caminho. Somente o guincheiro responsável MUST poder registrar Cheguei e, após essa chegada, Iniciar transporte. Ambos os participantes MUST acompanhar esses estados; o início MUST dispensar confirmação do cliente e habilitar a solicitação de conclusão somente durante o transporte em andamento. Repetir qualquer ação MUST preservar a transição já registrada.
- **FR-021**: O card de conclusão MUST oferecer Informar problema ao cliente responsável, exigir motivo não vazio e registrar uma única contestação por pedido. O atendimento MUST entrar em contestação e ser encaminhado ao suporte; ambos os participantes MUST poder acessar e acompanhar esse caso, sem conclusão ou reembolso automático. O cliente não poderá confirmar a conclusão enquanto a contestação estiver aberta; o guincheiro não poderá encerrá-la unilateralmente.
- **FR-022**: O dashboard MUST permitir ao suporte autorizado consultar contestações e resolver cada uma por Concluir sem reembolso ou Cancelar com reembolso integral. Ambas as decisões MUST exigir justificativa não vazia e registrar responsável, momento e resultado. Concluir MUST encerrar o atendimento preservando o pagamento; cancelar MUST encerrar a prestação e iniciar o reembolso de todo o total pago pelo cliente, incluindo os 10% e as taxas. Decisões repetidas MUST preservar a primeira resolução válida e não duplicar reembolso. Ambos os participantes MUST receber o resultado; o encerramento MUST remover apenas o pedido resolvido do fluxo pendente, retornando à tela inicial quando estiverem nesse pedido, sem interromper outro atendimento. Falhas de reembolso MUST permanecer visíveis e seguir as regras existentes de recuperação.
- **FR-023**: Entrar em contestação MUST liberar o guincheiro para receber e aceitar novos pedidos conforme sua disponibilidade, sem tratar o caso contestado como atendimento operacional ativo. A contestação MUST permanecer acessível no histórico de atendimentos para ambos os participantes e na fila do suporte. Na reabertura do aplicativo, um novo atendimento operacional ativo MUST ter prioridade sobre a contestação antiga; a resolução desta MUST preservar integralmente o novo atendimento.

### Key Entities *(include if feature involves data)*

- **Guincheiro elegível**: Prestador vinculado à categoria Guincho, com preço-base, disponibilidade e localização de descoberta.
- **Pedido de transporte**: Cliente, guincheiro escolhido, veículo, retirada, destino, estado do atendimento e momentos de aceite, chegada à retirada e início do transporte.
- **Cotação de Guincho**: Preço-base, distância cotada, tarifa por km quando aplicável, adicional, 10%, taxas, total do cliente, remuneração do guincheiro e validade da oferta.
- **Pagamento**: Valor total, pedido associado e resultado de aprovação, falha, cancelamento ou reembolso.
- **Confirmação de conclusão**: Solicitação do guincheiro e confirmação do cliente, com momento e participante de cada ação.
- **Contestação**: Pedido associado, cliente responsável, motivo informado, momento de abertura, estado de análise, decisão, justificativa, responsável autorizado e momento da resolução pelo suporte.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de seleção validados, o cliente acessa o mapa antes de pagar e o card mostra o prestador correspondente ao ícone selecionado.
- **SC-002**: Em 100% das cotações validadas, o total cobrado corresponde ao último total confirmado pelo cliente e a remuneração exibida ao guincheiro exclui os 10% e as taxas.
- **SC-003**: Em 100% dos cenários de pagamento pendente, recusado ou falho, os dados identificáveis do cliente permanecem indisponíveis ao guincheiro.
- **SC-004**: Com ambos os aplicativos em primeiro plano no atendimento e conexão contínua ao backend, sem modo offline ou falha de requisição durante a amostra, a aprovação do pagamento e a confirmação de conclusão são refletidas nos dois aplicativos em até 10 segundos em pelo menos 95% das verificações. Medir desde a persistência da transição correspondente no backend até sua renderização no último dos dois aplicativos; o tempo de processamento externo anterior à persistência da aprovação não integra essa medida. Registrar rede, versões e timestamps em relógios sincronizados, com pelo menos 20 mudanças observadas, incluindo os dois tipos de evento; falhas de rede devem ser registradas separadamente como cenários de recuperação.
- **SC-005**: Em 100% dos cenários de reabertura e repetição de ações, há no máximo uma contratação e uma cobrança por pedido; a etapa pendente é retomada corretamente.
- **SC-006**: Em 100% dos cenários normais de conclusão validados, ambos retornam ao estado inicial somente após a confirmação do cliente; antes disso, o prestador retoma a tela de conclusão e o cliente vê seu card. Em contestação, o encerramento exige decisão registrada do suporte autorizado.
- **SC-007**: Em 100% dos cenários de contestação validados, a resolução exige justificativa e autorização; cancelar solicita no máximo um reembolso integral por pagamento e concluir preserva o pagamento.
- **SC-008**: Em 100% dos cenários com contestação e nova contratação, o guincheiro disponível consegue receber um novo pedido, consultar o caso anterior e continuar o novo atendimento após a resolução do anterior.

## Assumptions

- Escopo: refinar o Guincho já existente com seleção explícita, pagamento, aceite, informações operacionais, conclusão bilateral e resolução de contestação pelo suporte no dashboard; não inclui mudanças de destino após pagamento, múltiplas paradas, negociação de preço ou reembolso parcial.
- O valor do mini card é o total final do cliente. Retirada, destino e veículo serão confirmados ao abrir o mapa, antes da seleção do guincheiro; informações incompletas não autorizam seleção ou cobrança.
- As taxas reutilizam a política configurável vigente. Os 10% incidem sobre preço-base mais adicional por km; as taxas são somadas em seguida, conforme FR-009.
- O prestador recebe seu preço-base e, quando habilitado, o adicional de transporte por km; os acréscimos do cliente não diminuem essa remuneração apresentada.
- A seleção permanece exclusiva ao guincheiro escolhido, com reembolso em caso de não atendimento, em vez de contratar automaticamente outro profissional.
- A política vigente de prazo para aceite e de cancelamento/reembolso será reutilizada. Não será criado prazo para aprovar automaticamente a conclusão.
- A resolução de contestação é uma exceção explícita à confirmação do cliente; somente suporte autorizado conforme as permissões existentes do dashboard poderá decidir, com justificativa e registro da ação.
- Dependências: cadastros de clientes, veículos e prestadores, localização e descoberta, regras de preços, processamento de pagamento e acompanhamento existentes.
- O fluxo segue as regras existentes de simulação em Dev e pagamentos integrados em Homol/Prod.

### Ajuste confirmado pelo usuário — retirada automática

Em 30/09/2026, o usuário confirmou preencher a retirada pela localização atual e deixar apenas o destino para informar. Solicitar a permissão ao abrir a tela, usar posição recente, consultar endereço sem bloquear por falha de geocodificação e manter confirmação do trajeto/veículo antes da cotação. Sem GPS, seleção da retirada pelo mapa sem digitação de endereço.
