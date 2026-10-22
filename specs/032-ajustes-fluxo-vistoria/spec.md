# Feature Specification: Aviso, recuperação de fotos e retorno ao mapa na vistoria

**Feature Branch**: `feature/refinamento-guincho` (branch atual; diretório independente)

**Created**: 2026-10-21

**Status**: Implementação em revisão — escopo de aviso corrigido pelo usuário em 2026-10-21

**Input**: User description: "Ao fazer a vistoria e o guincheiro apertar em 'Enviar Fotos para Aprovaao' Deve aparecer uma notificaao para o cliente, mesmo que ele nao esteja na tela da vistoria informando que tem vistoria para dar aceite. Quando tirar uma foto e der erro, deve ser possivel tentar salvar novamente, sem necessidade de desfazer e tirar outra. Ao finalizar a vistoria e apertar no botao de Enviar, a tela de vistoria deve fechar e ir pra tela do mapa, Igual na tela do cliente quando ele clica em aceitar."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receber aviso de vistoria aguardando aceite (Priority: P1)

Como cliente do atendimento de Guincho, quero ser avisado quando as fotos estiverem disponíveis para minha aprovação, mesmo estando em outra tela do aplicativo, para revisar a vistoria e permitir a continuidade do atendimento.

**Why this priority**: O transporte depende do aceite; uma pendência percebida apenas ao abrir a vistoria pode interromper o atendimento sem que o cliente saiba qual ação é necessária.

**Independent Test**: Manter o cliente no mapa e em outra tela; enviar uma vistoria como guincheiro e verificar o aviso dentro do app, sem redimensionar a tela. Dispensar em “Agora não”, conferir ausência de repetição por polling e abrir depois pelo atendimento. Verificar “Revisar vistoria”, a versão correta e o aceite explícito.

**Acceptance Scenarios**:

1. **Given** cliente autenticado no mapa ou em outra tela do aplicativo, **When** o envio de uma versão da vistoria for confirmado, **Then** aparecerá um aviso de que há uma vistoria aguardando seu aceite, com ação para abri-la, sem abandonar automaticamente a tela atual.
2. **Given** aplicativo em segundo plano durante o envio, **When** o cliente retornar ao aplicativo conectado e autenticado, **Then** verá o aviso interno caso a vistoria ainda aguarde aceite, sem push nem notificação do sistema.
3. **Given** cliente já na vistoria correspondente, **When** uma versão for disponibilizada para aprovação, **Then** a tela indicará a pendência e apresentará a versão vigente, sem abrir outra cópia da tela.
4. **Given** o mesmo envio já confirmado, **When** ocorrer repetição do toque ou atualização do atendimento, **Then** não haverá novo alerta para a mesma versão; um reenvio após correção gerará um novo aviso para a nova versão.
5. **Given** aviso de uma vistoria de vistoria já aprovada, corrigida, substituída ou de atendimento encerrado, **When** o cliente abrir a revisão, **Then** verá a situação vigente daquele atendimento e nenhuma ação de aceite inválida será oferecida.
6. **Given** conexão indisponível no envio, **When** o cliente retornar ao aplicativo com conexão, **Then** verá a pendência ainda existente sem precisar entrar previamente na vistoria; a ausência do aviso não será interpretada como aprovação.
7. **Given** envio recusado ou ainda não confirmado, **When** o guincheiro tentar enviar, **Then** nenhum aviso de vistoria disponível será apresentado ao cliente antes de confirmar que a versão está realmente disponível para revisão.

---

### User Story 2 - Tentar salvar novamente a foto capturada (Priority: P1)

Como guincheiro, quero repetir o salvamento de uma foto que apresentou erro mantendo a captura original, para concluir a vistoria sem desfazer o registro e fotografar novamente o veículo.

**Why this priority**: Obrigar nova captura após uma falha recuperável aumenta o trabalho e pode perder a evidência já registrada.

**Independent Test**: Capturar uma foto, provocar uma falha de salvamento com a imagem ainda disponível, corrigir a causa e usar “Tentar salvar novamente”; verificar a preservação da mesma imagem e do ponto, sem nova abertura da câmera nem duplicação.

**Acceptance Scenarios**:

1. **Given** uma captura concluída cuja imagem permanece disponível, **When** ocorrer erro na preparação ou no salvamento, **Then** o ponto manterá a foto capturada, indicará a falha e oferecerá “Tentar salvar novamente”, sem exigir desfazer ou abrir a câmera.
2. **Given** uma foto com erro recuperável e vistoria ainda editável, **When** o guincheiro escolher tentar salvar novamente, **Then** a mesma imagem será processada e salva no mesmo ponto e versão; ele verá o andamento e, após confirmação, a indicação de foto salva.
3. **Given** nova falha durante a tentativa, **When** a operação terminar, **Then** a imagem continuará preservada e a ação ficará disponível novamente, com mensagem compreensível, sem afetar as outras fotos.
4. **Given** imagem pendente preservada após saída da tela, rotação ou reabertura do aplicativo pela mesma conta, **When** a vistoria for retomada ainda editável, **Then** a captura será recuperada e poderá ser salva sem nova fotografia; a retomada automática já prevista continuará válida.
5. **Given** tentativa automática em andamento, toques repetidos ou confirmação perdida de uma foto já salva, **When** o salvamento for retomado, **Then** haverá um único resultado para aquela captura, sem fotos duplicadas ou substituição indevida de outra imagem.
6. **Given** arquivo da captura indisponível ou vistoria que deixou de permitir edição, **When** tentar recuperar a foto, **Then** o sistema explicará o impedimento; solicitará nova captura somente se a imagem estiver irrecuperável e a edição ainda for permitida, sem declarar a foto salva.

---

### User Story 3 - Voltar ao mapa após enviar a vistoria (Priority: P2)

Como guincheiro, quero que a vistoria feche depois de enviar as fotos com sucesso e que o mapa do mesmo atendimento seja exibido, para continuar acompanhando o serviço enquanto aguardo a decisão do cliente.

**Why this priority**: Completa a ação de envio com um destino claro, seguindo o comportamento já existente após o cliente aprovar as fotos.

**Independent Test**: Enviar uma vistoria completa e verificar retorno automático ao mapa do mesmo pedido; repetir com falha, após correção e após abrir a vistoria por um caminho sem mapa anterior.

**Acceptance Scenarios**:

1. **Given** vistoria completa com todas as fotos incluídas salvas, **When** o guincheiro tocar em “Enviar fotos para aprovação” e o envio for confirmado, **Then** a tela de vistoria fechará automaticamente e abrirá o mapa de acompanhamento do mesmo atendimento, mostrando “Aguardando aprovação das fotos” enquanto houver pendência ou a decisão já registrada pelo cliente.
2. **Given** reenvio após correção, **When** a nova versão for confirmada como enviada, **Then** ocorrerá o mesmo retorno automático ao mapa, mostrando a situação vigente da nova versão, inclusive a decisão do cliente caso já tenha sido registrada.
3. **Given** envio em andamento, recusado ou sem confirmação por falha de conexão, **When** o guincheiro permanecer no fluxo, **Then** a vistoria não fechará como se tivesse sido enviada; mostrará andamento ou erro com opção de recuperação, preservando as fotos.
4. **Given** retorno ao mapa após envio confirmado, inclusive sem um mapa anterior aberto, **When** o guincheiro usar o botão Voltar do aparelho, **Then** a vistoria recém-enviada não reaparecerá por causa desse retorno automático; as fotos continuarão acessíveis por consulta explícita no atendimento.
5. **Given** cliente revisando uma vistoria, **When** confirmar seu aceite, **Then** continuará retornando ao mapa do mesmo atendimento; a aprovação liberará a ação de iniciar transporte ao guincheiro, sem iniciá-lo automaticamente.

### Edge Cases

- Envio confirmado, mas resposta perdida para o guincheiro: ao recuperar a situação vigente, reconhecer o envio existente, retornar ao mapa e não criar outra versão ou novo aviso para o mesmo envio.
- Cliente desconectado durante o envio: apresentar a pendência ao retomar conectado caso ela ainda exista, sem avisar como pendente uma versão já decidida.
- Sessão expirada ou troca de conta ao abrir o aviso: exigir autenticação válida e vínculo com o pedido antes de mostrar fotos; outra conta não recebe aviso interno nem acesso às imagens do cliente anterior.
- Dispensar o aviso em “Agora não” não aprova a vistoria nem a oculta do atendimento. A mesma versão não reabre o diálogo por polling na sessão/tela restaurada; nova versão enviada pode gerar novo aviso.
- Aprovação ou cancelamento enquanto há aviso aberto: remover a indicação ativa de pendência; ao abrir uma revisão antiga, mostrar a situação real sem permitir decisão inválida.
- Falha repetida em foto obrigatória ou opcional incluída: preservar a imagem, permitir nova tentativa enquanto editável e manter o envio bloqueado até salvar ou, quando permitido, retirar explicitamente a foto opcional.
- Falta de espaço durante preparação, com original ainda disponível: manter a captura e permitir repetir após liberar espaço. Se o original não existir, informar a perda e orientar nova captura quando permitida.
- Reenvio automático e manual simultâneos: preservar uma única foto salva por captura e mostrar o andamento existente, sem operações conflitantes.
- Atendimento encerrado durante salvamento ou envio: interromper ações incompatíveis com o estado atual, informar o encerramento e seguir as regras existentes de preservação dos registros.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada versão confirmada como enviada para aprovação DEVE gerar um aviso dirigido exclusivamente ao cliente responsável pelo atendimento, informando que há uma vistoria aguardando seu aceite e oferecendo acesso à revisão. Tentativas recusadas ou sem envio confirmado NÃO DEVEM produzir esse aviso.
- **FR-002**: Com o aplicativo em uso, o aviso DEVE ser perceptível no mapa e nas demais telas autenticadas do cliente, sem navegação automática. Se a vistoria correspondente já estiver aberta, o sistema DEVE atualizar a pendência e a versão nessa tela, sem duplicá-la.
- **FR-003**: O aviso DEVE existir somente dentro do aplicativo, sem Firebase, FCM, push, permissão de notificação ou notificação do sistema para este fluxo. Ao retornar conectado ao app, o cliente DEVE visualizar a pendência vigente. O aviso DEVE respeitar as áreas do sistema, sem empurrar o conteúdo ou reduzir o mapa; oferecer “Revisar vistoria” e “Agora não”.
- **FR-004**: Abrir o aviso DEVE levar à vistoria vigente do atendimento associado, após validar a conta participante. Avisos antigos DEVEM refletir o estado atual, sem aprovar versões substituídas ou pedidos encerrados. Aprovação, correção ou encerramento DEVEM retirar a indicação ativa de aceite pendente daquela versão.
- **FR-005**: Repetições do mesmo envio ou de sua atualização NÃO DEVEM criar novos alertas para a mesma combinação de cliente, atendimento e versão. Uma nova versão enviada após correção DEVE produzir novo aviso. Nenhum aviso ou toque nele DEVE substituir o aceite explícito do cliente.
- **FR-006**: Em falha de preparação ou salvamento com a imagem capturada disponível, o sistema DEVE preservar a captura e seu vínculo com ponto, atendimento e versão e oferecer “Tentar salvar novamente” junto à foto afetada, sem exigir desfazer, descartar ou fotografar novamente.
- **FR-007**: A nova tentativa DEVE reutilizar a mesma captura, mostrar andamento, indicar sucesso somente após confirmação do salvamento e, se falhar novamente, manter imagem e possibilidade de repetição. As demais fotos DEVEM permanecer disponíveis.
- **FR-008**: A recuperação manual DEVE coexistir com a retomada automática existente, inclusive após rotação ou reabertura pela mesma conta, sem duplicar fotos, perder capturas disponíveis ou repetir salvamentos já confirmados. Durante uma tentativa ativa, a interface DEVE impedir o disparo de outra tentativa conflitante para a mesma captura.
- **FR-009**: Se a imagem não puder ser recuperada, o sistema DEVE informar o motivo e permitir nova captura quando a vistoria for editável. Se a conta ou o atendimento não permitir mais edição, DEVE informar o impedimento e apresentar a situação vigente, sem falsa confirmação de salvamento.
- **FR-010**: O envio para aprovação DEVE continuar exigindo os quatro pontos obrigatórios e confirmação de salvamento de todas as fotos incluídas. Foto pendente ou com erro NÃO DEVE ser considerada compartilhada; fotos opcionais só deixam de impedir o envio se forem salvas ou retiradas explicitamente em uma versão editável.
- **FR-011**: Após confirmar o envio inicial ou reenvio para aprovação, o sistema DEVE fechar a vistoria e exibir automaticamente o mapa de acompanhamento do mesmo atendimento ao guincheiro, sem exigir outra ação. O mapa DEVE mostrar a situação vigente: “Aguardando aprovação das fotos” enquanto houver pendência, ou a decisão já registrada pelo cliente.
- **FR-012**: Durante envio em andamento ou com falha, o sistema DEVE manter a vistoria aberta e preservar o conteúdo, indicando andamento ou erro recuperável. Se a confirmação tiver sido perdida, a recuperação DEVE reconhecer um envio já efetivado antes de repetir a ação e aplicar o retorno ao mapa após confirmar esse resultado.
- **FR-013**: O retorno automático DEVE funcionar com ou sem mapa previamente aberto e remover a tela recém-enviada do caminho imediato de Voltar, mantendo a consulta explícita às fotos pelo atendimento. O retorno existente do cliente após aprovação DEVE ser preservado.
- **FR-014**: Envio, avisos e retorno ao mapa NÃO DEVEM aprovar a vistoria, iniciar transporte ou concluir o serviço. O aceite continua exclusivo do cliente e apenas libera o início explícito pelo guincheiro, conforme as regras existentes.

### Key Entities *(include if feature involves data)*

- **Atendimento de Guincho**: Serviço existente que relaciona cliente, guincheiro, veículo e mapa de acompanhamento; delimita quem pode ser avisado, consultar ou agir.
- **Versão da vistoria**: Conjunto identificado de fotos enviado para revisão, com situação atual e decisão do cliente; distingue um primeiro envio de um reenvio após correção.
- **Captura com salvamento pendente**: Imagem já capturada, associada ao ponto e à versão, com situação de preparação ou salvamento e resultado da última tentativa; permanece recuperável enquanto estiver disponível e houver autorização para edição.
- **Aviso de aceite pendente**: Comunicação vinculada ao cliente, atendimento e versão, com acesso à situação vigente; deixa de representar pendência após decisão, substituição ou encerramento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com conexão contínua e app visível, em pelo menos 95% de 20 envios por contexto (cliente no mapa e em outra tela), o aviso interno será perceptível em até 10 segundos após a confirmação, sem exigir abertura prévia da vistoria. Conferir também a leitura das pendências ao retomar o app.
- **SC-002**: Em 100% dos cenários avaliados de abertura de aviso, repetição, nova versão, decisão anterior e troca de conta, será apresentado somente o atendimento autorizado em sua situação vigente, sem novo alerta para o mesmo envio nem possibilidade de aceite inválido.
- **SC-003**: Em 100% dos cenários de falha recuperável com imagem disponível, após corrigir a causa, o guincheiro conseguirá salvar a mesma captura com uma ação de nova tentativa, sem desfazer ou reabrir a câmera e sem foto duplicada; nova falha manterá a recuperação disponível.
- **SC-004**: Em pelo menos 95% de 20 envios iniciais ou reenvios confirmados, o guincheiro verá o mapa do mesmo atendimento em até 2 segundos após a confirmação, sem toque adicional. Em todos os envios recusados ou ainda sem confirmação avaliados, a vistoria permanecerá aberta com indicação de andamento ou falha.
- **SC-005**: Em 100% dos cenários de envio, retorno ao mapa, recuperação de fotos e aceite avaliados, serão preservados o conteúdo salvo e as etapas válidas: nenhum transporte começará por envio, notificação ou navegação, e a aprovação do cliente continuará retornando ao mapa.

## Assumptions

- Escopo: três ajustes no fluxo existente de vistoria de Guincho — aviso de aceite pendente, recuperação manual da mesma foto após falha e retorno do guincheiro ao mapa após envio confirmado. Catálogo de pontos, histórico, pagamento, correção e início explícito do transporte seguem as regras existentes.
- “Enviar” ao finalizar a vistoria significa “Enviar fotos para aprovação”, inclusive reenvio após correção; não significa concluir o serviço ou iniciar transporte. “Mapa” significa o acompanhamento do mesmo atendimento, assim como no retorno atual do cliente após aprovar.
- “Mesmo que não esteja na tela” significa outras telas do aplicativo. O usuário esclareceu explicitamente que deseja aviso simples, sem push. Não há entrega com aplicativo fechado ou em segundo plano; ao retomar conectado, consultar a pendência atual.
- A imagem original ainda disponível permite nova tentativa de preparação ou salvamento. Nova captura só é necessária quando não houver imagem recuperável; essa exceção deve ser informada ao usuário.
- Dependências: [vistoria e aprovação existentes](../030-guincho-vistoria-fotos/spec.md), [catálogo, preservação e retomada de fotos pendentes](../031-guincho-vistoria-rota/spec.md), autenticação e acompanhamento no mapa. Esta feature torna explícita a recuperação manual já prevista e complementa o aviso de pendência existente no atendimento.
- A retomada automática de fotos ao reabrir o aplicativo continua válida; não se passa a exigir salvamento contínuo com o aplicativo fechado.
- Os limites de 10 segundos para aviso e 2 segundos para retorno ao mapa são metas de aceite em condições normais de uso, contadas a partir da confirmação do envio, sem incluir o tempo de salvamento das fotos.
