# Feature Specification: Alerta de solicitação e Home do guincheiro

**Feature Branch**: `feature/refinamento-guincho` (branch atual; diretório independente)

**Created**: 2026-09-30

**Status**: Draft

**Input**: User description: "Quando um guincho for escolhido deve aparecer um alerta para ele, parecido com o PitTec. A tela Home quando logado como guincho deve ter um mapa com a localização atual do guincho centralizado."

## Clarifications

### Alteração de fluxo solicitada em 30/09/2026

O alerta de seleção deve chegar antes do pagamento. O guincheiro responde ao convite em até 60 segundos; ao aceitar, aguarda o pagamento e o cliente abre o checkout. Aprovação financeira libera dados pessoais/endereços e tracking; não há segundo aceite. "Cheguei" inicia o serviço. Esta orientação substitui condições anteriores de alerta apenas após pagamento. O mapa da Home recentraliza automaticamente após 30 segundos sem gesto manual, sem botão Recentralizar, conforme orientação posterior do usuário.

### Session 2026-09-30

- Q: Se o guincheiro estiver no Perfil ou no Histórico quando chegar uma solicitação, como o alerta deve aparecer? → A: Mostrar o card com Aceitar e Recusar sobre qualquer tela do guincheiro, preservando a navegação atual.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Perceber e responder à solicitação (Priority: P1)

Como guincheiro escolhido pelo cliente, quero perceber uma nova solicitação com um alerta semelhante ao do PitTec, para aceitar ou recusar dentro do prazo existente.

**Why this priority**: Uma solicitação paga precisa ser percebida a tempo para que o atendimento aconteça.

**Independent Test**: Disponibilizar uma solicitação com pagamento aprovado para um guincheiro e conferir o alerta, seus dados e as ações de resposta.

**Acceptance Scenarios**:

1. **Given** um guincheiro escolhido e pagamento aprovado, **When** a solicitação estiver disponível para resposta com o aplicativo aberto, **Then** um card destacado de novo atendimento aparecerá sobre a tela atual do guincheiro, inclusive Perfil ou Histórico, sem navegar automaticamente para a Home, com nome do cliente, veículo, retirada, destino, remuneração do guincheiro e ações Aceitar e Recusar.
2. **Given** uma solicitação liberada, conexão contínua, notificações autorizadas e serviço de acompanhamento ativo, **When** o aplicativo estiver em segundo plano ou o aparelho bloqueado, **Then** o guincheiro receberá uma notificação com destaque equivalente ao alerta do PitTec; tocar nela abrirá a solicitação vigente.
3. **Given** apenas seleção do guincho ou pagamento pendente, recusado ou falho, **When** o guincheiro consultar a Home, **Then** nenhum alerta de atendimento liberado nem dados pessoais desse cliente serão apresentados.
4. **Given** um alerta vigente, **When** o guincheiro aceitar, **Then** seguirá para o acompanhamento desse atendimento; ao recusar, seguirá o cancelamento e reembolso já definidos e retornará à Home.
5. **Given** uma solicitação repetidamente atualizada, **When** não houver uma nova solicitação, **Then** não haverá novos alertas sonoros ou notificações duplicadas para o mesmo atendimento.
6. **Given** uma solicitação cancelada, expirada ou respondida, **When** o alerta for consultado, **Then** as ações deixarão de estar disponíveis e a notificação correspondente será removida; uma notificação antiga não permitirá responder a um pedido encerrado.

---

### User Story 2 - Visualizar a posição atual na Home (Priority: P2)

Como guincheiro autenticado, quero abrir minha Home e ver um mapa centrado na minha localização atual, para reconhecer minha posição enquanto aguardo solicitações.

**Why this priority**: O mapa oferece contexto operacional à tela inicial e acompanha o deslocamento do prestador.

**Independent Test**: Abrir a Home de um guincheiro sem atendimento ativo com localização disponível, deslocar o mapa e usar a ação de recentralizar.

**Acceptance Scenarios**:

1. **Given** um guincheiro autenticado sem atendimento ativo e localização disponível, **When** abrir a Home, **Then** verá um mapa centrado em sua posição atual e um marcador próprio identificável.
2. **Given** o mapa centrado e novas posições válidas, **When** o guincheiro se deslocar, **Then** o marcador acompanhará a posição e o mapa continuará seguindo o guincheiro até ele mover o mapa manualmente.
3. **Given** o mapa movido manualmente, **When** chegar uma nova posição, **Then** o marcador será atualizado preservando o enquadramento escolhido; ao tocar em Recentralizar, o mapa voltará à posição atual e retomará o acompanhamento.
4. **Given** localização negada, desligada ou indisponível, **When** abrir a Home, **Then** verá uma explicação e uma ação de recuperação adequada, sem posição fictícia e mantendo disponíveis perfil, disponibilidade e solicitações elegíveis.
5. **Given** um alerta sobre a Home, **When** visualizar o mapa, **Then** o marcador atual e a ação de recentralizar permanecerão visíveis e utilizáveis.

---

### User Story 3 - Retomar o estado correto (Priority: P3)

Como guincheiro, quero retornar ao aplicativo e encontrar o pedido pendente ou atendimento em andamento, para continuar de onde parei.

**Why this priority**: O mapa inicial não deve ocultar uma solicitação nem interromper um serviço existente.

**Independent Test**: Sair e voltar ao aplicativo com solicitação pendente, atendimento aceito e conclusão aguardando confirmação do cliente.

**Acceptance Scenarios**:

1. **Given** um pedido ainda aguardando resposta, **When** retomar o aplicativo, **Then** verá o alerta desse pedido e o prazo restante, sem reiniciar a contagem.
2. **Given** um atendimento aceito, em execução ou aguardando confirmação do cliente, **When** retomar o aplicativo, **Then** será apresentado o acompanhamento correspondente ao estágio existente.
3. **Given** conclusão confirmada pelo cliente, **When** o atendimento terminar, **Then** o guincheiro voltará à Home com mapa e posição atual, sem alerta residual.

### Edge Cases

- Notificações recusadas ou silenciadas: explicar como habilitá-las e manter o alerta dentro do aplicativo; respeitar as preferências do aparelho para som e vibração.
- Conexão interrompida: indicar a falha; ao reconectar, recuperar o pedido vigente com o prazo original e descartar alertas encerrados.
- Toques repetidos em Aceitar ou Recusar: preservar uma única resposta válida e mostrar o resultado confirmado.
- Expiração durante a resposta: impedir aceite tardio e informar que o pedido não está mais disponível.
- Localização antiga: identificar a última posição conhecida como desatualizada, sem apresentá-la como atual; recentralizar somente com posição atual disponível.
- Troca de conta ou saída: remover alertas e localização da sessão anterior, preservando somente o marcador mínimo de deduplicação sonora até encerramento ou expiração para impedir novo som ao retornar à mesma conta.
- Fonte ampliada e tela pequena: manter legíveis as informações do alerta e acessíveis ações e mapa.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O guincheiro escolhido MUST receber o alerta quando o pagamento estiver aprovado e o pedido liberado para resposta; a seleção isolada MUST NOT gerar uma solicitação operacional.
- **FR-002**: Em primeiro plano, o alerta MUST apresentar um card destacado com identificação de novo atendimento, informações operacionais, remuneração do guincheiro e ações Aceitar e Recusar, usando a linguagem visual do alerta do PitTec. O card MUST aparecer sobre qualquer tela do guincheiro, inclusive Perfil e Histórico, preservando a navegação atual até a resposta. Aceitar MUST abrir o acompanhamento; recusar MUST retornar à Home conforme o fluxo existente.
- **FR-003**: Em segundo plano ou com aparelho bloqueado, o alerta MUST gerar uma notificação quando houver conexão, serviço de acompanhamento ativo e permissões/configurações que permitam, seguindo o destaque, som e vibração existentes do PitTec. Na tela bloqueada, MUST usar mensagem genérica sem nome, veículo, endereços ou valores. Após interrupção do serviço, o aplicativo MUST recuperar o pedido vigente ao ser reaberto; não há garantia de entrega durante a interrupção.
- **FR-004**: Tocar na notificação MUST abrir o pedido vigente e validar seu estado; pedido encerrado MUST apresentar feedback compreensível e impedir ações antigas.
- **FR-005**: Cada pedido MUST manter no máximo um card e uma notificação ativos por aparelho; atualizações e retomadas, inclusive sair e entrar novamente na mesma conta com pedido vigente, MUST NOT repetir som ou vibração para o mesmo pedido.
- **FR-006**: Aceite, recusa, cancelamento ou expiração MUST desativar o alerta correspondente. O prazo de resposta existente de 60 segundos MUST ser preservado e exibido com o tempo restante, sem reinício na retomada.
- **FR-007**: O alerta MUST preservar as regras existentes de remuneração sem acréscimos do cliente, privacidade, resposta única e cancelamento/reembolso.
- **FR-008**: A Home do guincheiro sem atendimento ativo MUST exibir um mapa centrado inicialmente na localização atual com marcador próprio, independentemente de estar disponível ou indisponível para novas solicitações.
- **FR-009**: Novas posições atuais MUST atualizar o marcador e acompanhar o guincheiro enquanto o acompanhamento estiver ativo; interação manual com o mapa MUST suspender apenas o acompanhamento do enquadramento.
- **FR-010**: A Home MUST oferecer Recentralizar, que reposiciona o mapa na localização atual e retoma seu acompanhamento; sem posição atual, MUST informar a indisponibilidade e oferecer recuperação.
- **FR-011**: Localização em obtenção, negada, desligada, desatualizada ou indisponível MUST ter feedback explícito, sem inventar coordenadas ou bloquear funcionalidades que independem dela.
- **FR-012**: Quando estiver sobre a Home com mapa, o card de alerta MUST manter visíveis o marcador próprio e a ação Recentralizar, com layout adaptado a fontes ampliadas e tamanhos de tela suportados. No Perfil e Histórico, MUST preservar a tela atual com o card sobreposto, sem exigir mapa ou Recentralizar nessas telas.
- **FR-013**: Perfil, disponibilidade e histórico existentes MUST continuar acessíveis na Home; receber um alerta MUST NOT aceitar o pedido automaticamente nem alterar a disponibilidade por conta própria.
- **FR-014**: Retomar o aplicativo MUST recuperar o pedido ou atendimento vigente, incluindo a etapa de confirmação de conclusão pelo cliente; ao encerrar o fluxo, MUST voltar à Home e remover alertas residuais.
- **FR-015**: Alertas e localização MUST corresponder exclusivamente à conta autenticada; sair ou trocar de conta MUST remover os dados e notificações da conta anterior.
- **FR-016**: Home do cliente, prestadores fixos e PitTec MUST preservar seus comportamentos existentes.

### Key Entities *(include if feature involves data)*

- **Guincheiro**: Prestador móvel autenticado que oferece Guincho, com disponibilidade e posição própria.
- **Solicitação de guincho**: Pedido do cliente direcionado a esse prestador, com pagamento, prazo de resposta, estado e informações de transporte existentes.
- **Alerta de solicitação**: Apresentação do pedido liberado, vinculada à solicitação e ao prestador; acompanha sua validade sem criar outro pedido.
- **Posição do guincheiro**: Localização e momento de obtenção que permitem distinguir posição atual e última posição conhecida.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com conexão contínua e aplicativo aberto, em pelo menos 95% de 20 solicitações liberadas, o card será visível ao guincheiro em até 10 segundos após a liberação do pedido pago.
- **SC-002**: Com conexão contínua, notificações autorizadas e serviço de acompanhamento ativo e sem restrições de execução do aparelho, em pelo menos 95% de 20 solicitações liberadas em segundo plano, a notificação será visível em até 10 segundos após a liberação.
- **SC-003**: Em 100% dos cenários de repetição, retomada e encerramento verificados, haverá no máximo um alerta ativo por pedido, nenhum novo som por atualização e nenhuma ação disponível para pedido encerrado.
- **SC-004**: Com permissão concedida e localização disponível, em pelo menos 95% de 20 aberturas da Home, o guincheiro verá sua posição centrada em até 10 segundos; após mover o mapa, poderá recentralizar com uma ação.
- **SC-005**: Em 100% dos cenários de localização indisponível, conta trocada e pagamento não aprovado, não será exibida posição fictícia, informação de outra conta ou alerta operacional indevido.
- **SC-006**: Em todos os tamanhos de tela suportados verificados, incluindo fonte ampliada, o alerta permitirá responder; sobre a Home com mapa, manterá visíveis o marcador próprio e Recentralizar, e no Perfil e Histórico preservará a tela atual.

## Assumptions

- “Guincho escolhido” significa pedido liberado após aprovação do pagamento, preservando a decisão da feature 028 de solicitar aceite somente após pagamento. Não haverá alerta de pré-reserva.
- “Parecido com PitTec” inclui card destacado no aplicativo e notificação fora dele, respeitando permissões, silêncio e preferências do aparelho.
- O perfil de guincheiro corresponde ao prestador móvel que oferece Guincho; não cria nova modalidade de cadastro.
- O prazo de resposta, valores, pagamentos reais ou simulados conforme ambiente, reembolso e conclusão bilateral continuam conforme o fluxo existente.
- A Home com mapa cobre o estágio inicial; um atendimento ativo tem prioridade e mantém sua tela de acompanhamento.
- Dependências: localização do prestador, permissões do aparelho, autenticação, solicitação direcionada e confirmação de pagamento existentes.
- Fora do escopo: alterações de preços, alertas anteriores ao pagamento, despacho para vários prestadores, novo cadastro e alterações na Home de outros perfis.
