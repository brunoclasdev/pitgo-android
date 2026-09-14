# Feature Specification: Chamada e acompanhamento do PitTec

**Feature Branch**: `020-pittec-call-tracking`

**Created**: 2026-09-13

**Status**: Draft

**Input**: User description: "Ao chamar um PitTec, continuar buscando até que um prestador aceite. Após o aceite, mostrar o valor ao cliente para confirmação. Com o serviço confirmado, centralizar cliente e PitTec nos mapas dos dois perfis e atualizar a distância e a posição do PitTec para acompanhamento da chegada."

## Clarifications

### Session 2026-09-13

- Q: Quem define o valor que aparece ao cliente depois que o PitTec aceita a chamada? → A: O sistema calcula o valor pelas regras comerciais existentes; o PitTec conhece o mesmo valor antes de aceitar.
- Q: Quanto tempo o cliente deve ter para confirmar o valor depois que o PitTec aceita? → A: Dois minutos; sem resposta, a solicitação é cancelada e o PitTec é liberado.
- Q: O deslocamento e o rastreamento devem começar imediatamente após o cliente confirmar o valor ou somente após a aprovação do pagamento? → A: Somente após o pagamento ser aprovado.
- Q: A localização do PitTec deve continuar sendo atualizada quando ele bloquear a tela ou colocar o aplicativo em segundo plano durante o deslocamento? → A: Sim; somente o PitTec continua enviando sua localização em segundo plano durante o deslocamento.
- Q: Depois da confirmação, o destino do PitTec deve permanecer na localização registrada na solicitação ou acompanhar os movimentos do cliente? → A: O destino permanece fixo na localização registrada na solicitação.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Buscar até um PitTec aceitar (Priority: P1)

Como cliente, quero que uma única chamada continue procurando PitTecs disponíveis até que um deles aceite, para não precisar repetir manualmente a solicitação a cada recusa, indisponibilidade ou tentativa sem resposta.

**Why this priority**: O aceite de um PitTec é a condição necessária para apresentar o preço e prosseguir com o atendimento. Interromper a busca antes disso deixa o cliente sem serviço e quebra todo o fluxo posterior.

**Independent Test**: Iniciar uma chamada, fazer dois PitTecs recusarem ou não responderem e fazer um terceiro aceitar; a mesma solicitação deve permanecer em busca e avançar automaticamente somente após o terceiro aceite.

**Acceptance Scenarios**:

1. **Given** o cliente está apto a solicitar um atendimento, **When** ele toca em "Chamar PitTec", **Then** uma única solicitação ativa entra em busca e a tela informa claramente que o app continua procurando um prestador.
2. **Given** uma solicitação está em busca, **When** um PitTec recusa, fica indisponível ou não responde dentro do prazo vigente, **Then** a busca continua para outros PitTecs sem exigir nova ação do cliente.
3. **Given** uma solicitação está em busca, **When** um PitTec aceita o atendimento, **Then** a busca para de oferecer a solicitação a outros prestadores e avança para a confirmação do valor pelo cliente.
4. **Given** uma solicitação está em busca, **When** o cliente a cancela, **Then** a busca é encerrada, nenhum novo PitTec pode aceitá-la e nenhum serviço é iniciado.

---

### User Story 2 - Conferir e confirmar o valor (Priority: P1)

Como cliente, quero ver o valor do serviço depois que um PitTec aceitar e decidir se concordo, para que o atendimento só seja iniciado com minha confirmação explícita do preço.

**Why this priority**: O aceite do valor protege o cliente contra cobranças inesperadas e impede que o PitTec se desloque antes de existir acordo entre as duas partes.

**Independent Test**: Fazer um PitTec aceitar uma chamada e verificar que o cliente recebe um card com o valor total e ações de confirmar ou recusar; o atendimento só deve avançar após a confirmação.

**Acceptance Scenarios**:

1. **Given** um PitTec aceitou a chamada conhecendo o valor calculado pelo sistema, **When** o cliente recebe a atualização, **Then** ele vê um card com a identificação do PitTec, exatamente o mesmo valor total em reais e ações claras para confirmar ou recusar.
2. **Given** o card de valor está visível, **When** o cliente confirma dentro do prazo, **Then** o acordo é registrado, o pagamento existente é iniciado e ambos os participantes veem que o atendimento aguarda aprovação do pagamento.
3. **Given** o card de valor está visível, **When** o cliente recusa, **Then** a solicitação é encerrada, o PitTec é liberado e o serviço não é iniciado nem cobrado.
4. **Given** o cliente ainda não confirmou o valor, **When** qualquer participante consulta o atendimento, **Then** ele permanece identificado como aguardando confirmação do cliente e não como serviço iniciado.
5. **Given** o card de valor está visível, **When** passam dois minutos sem confirmação, **Then** a solicitação é cancelada, o card expira, o PitTec é liberado e o cliente precisa iniciar uma nova chamada caso ainda queira o serviço.
6. **Given** o cliente confirmou o valor e o pagamento está pendente, **When** o pagamento é aprovado, **Then** o serviço passa para a etapa de deslocamento e o rastreamento é iniciado para os dois participantes.

---

### User Story 3 - Acompanhar a aproximação no mapa do cliente (Priority: P2)

Como cliente, depois de confirmar o serviço, quero visualizar o local do atendimento e a posição do PitTec juntos no mapa, com distância atualizada, para acompanhar a aproximação do prestador.

**Why this priority**: O acompanhamento reduz a incerteza durante a espera e permite que o cliente saiba se o PitTec já está chegando.

**Independent Test**: Confirmar o valor, aprovar o pagamento, simular o deslocamento do PitTec e verificar que o enquadramento do mapa contém o prestador e o local fixo do atendimento, o marcador acompanha as novas posições e a distância exibida diminui sem atualização manual da tela.

**Acceptance Scenarios**:

1. **Given** o cliente confirmou o valor, o pagamento foi aprovado e o PitTec está em deslocamento, **When** o mapa do cliente é exibido, **Then** o local fixo do atendimento e o PitTec ficam simultaneamente visíveis em um enquadramento adequado.
2. **Given** o mapa de acompanhamento está aberto, **When** uma nova posição válida do PitTec é recebida, **Then** o marcador do PitTec e a distância até o local do atendimento são atualizados sem recarregar a tela.
3. **Given** as posições mudaram e os participantes deixariam de caber no enquadramento atual, **When** o mapa processa a atualização, **Then** o enquadramento é ajustado para manter ambos visíveis sem impedir a interação manual do cliente com o mapa.

---

### User Story 4 - Acompanhar o cliente no mapa do PitTec (Priority: P2)

Como PitTec, depois que o cliente confirmar o serviço e o pagamento for aprovado, quero visualizar minha posição e o local fixo do atendimento juntos no mapa, com atualizações durante o deslocamento, para chegar ao destino correto.

**Why this priority**: O PitTec precisa de referência contínua do destino para realizar o atendimento aceito, e os dois lados devem observar um estado coerente do mesmo deslocamento.

**Independent Test**: Aceitar uma chamada, obter a confirmação do cliente e a aprovação do pagamento, então simular mudanças na posição do PitTec; o mapa deve manter o prestador e o local fixo do atendimento visíveis e atualizar a aproximação sem recarregamento manual.

**Acceptance Scenarios**:

1. **Given** o PitTec aceitou, o cliente confirmou o valor e o pagamento foi aprovado, **When** o PitTec abre ou retorna ao atendimento, **Then** o mapa mostra o local fixo do atendimento e a própria posição no mesmo enquadramento.
2. **Given** o mapa do PitTec está aberto durante o deslocamento, **When** uma nova posição válida do PitTec é disponibilizada, **Then** seu marcador e a distância até o local do atendimento são atualizados sem recarregar a tela.
3. **Given** o PitTec aceitou mas o cliente ainda não confirmou o valor, **When** o PitTec consulta a chamada, **Then** ele vê que aguarda a decisão do cliente e o deslocamento ainda não é apresentado como iniciado.
4. **Given** o deslocamento está ativo, **When** o PitTec bloqueia a tela ou coloca o aplicativo em segundo plano, **Then** sua localização continua sendo enviada e o aparelho mantém uma indicação persistente de rastreamento ativo.

### Edge Cases

- Se não houver PitTec disponível em uma tentativa, a solicitação permanece ativa e informa que a busca continua; ela não deve ser concluída como falha apenas por ausência momentânea de prestadores.
- Se dois PitTecs tentarem aceitar quase simultaneamente, apenas o primeiro aceite válido fica associado à solicitação; os demais recebem a informação de que a chamada não está mais disponível.
- Se o PitTec aceito cancelar ou ficar indisponível antes de o cliente confirmar, o card deixa de aceitar confirmação, o cliente é informado e a mesma solicitação volta automaticamente à busca.
- Se a conexão do cliente cair durante a busca ou durante a confirmação, o estado vigente deve ser recuperado ao reconectar, sem criar uma solicitação duplicada ou confirmar o valor duas vezes.
- Se a localização do PitTec ficar temporariamente indisponível após a confirmação, os mapas mantêm sua última posição válida identificada como desatualizada e informam a falha; as atualizações são retomadas automaticamente quando houver nova posição válida.
- Se o cliente ou o PitTec negar a permissão de localização necessária ao acompanhamento, a tela explica como habilitá-la e não apresenta posições inventadas.
- Se o PitTec retirar a permissão necessária ao rastreamento em segundo plano durante o deslocamento, o cliente vê a última posição como desatualizada e o PitTec recebe orientação para restabelecer a permissão.
- Se o PitTec chegar ao local fixo do atendimento, a distância exibida deve chegar a zero ou indicar chegada de modo compreensível, mesmo que os marcadores se sobreponham.
- Se o cliente se mover depois de criar a solicitação, o destino e o marcador do local do atendimento não mudam; alterar o local exige encerrar a chamada e criar uma nova solicitação.
- Se o cliente mover ou aplicar zoom manualmente no mapa, a atualização de localização não deve causar reposicionamentos bruscos contínuos; deve existir uma ação clara para reenquadrar o PitTec e o local fixo do atendimento.
- Se o valor mudar depois de exibido, a confirmação anterior não pode ser aproveitada: o novo valor deve ser apresentado e confirmado explicitamente antes do início ou continuidade do serviço.
- Se a confirmação chegar depois dos dois minutos, ela deve ser recusada como expirada e não pode iniciar o deslocamento nem reativar a solicitação cancelada.
- Se o pagamento falhar, for recusado ou permanecer pendente, o deslocamento e o compartilhamento de localização não devem começar; a recuperação ou o cancelamento seguem as regras do pagamento existente.
- Se a solicitação for cancelada ou concluída, o compartilhamento de localização específico desse atendimento e as atualizações do mapa devem ser encerrados.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir apenas uma solicitação ativa de PitTec por cliente por vez e MUST impedir que toques repetidos em "Chamar PitTec" criem chamadas duplicadas.
- **FR-002**: Ao iniciar a chamada, o sistema MUST manter a solicitação no estado de busca até ocorrer um aceite válido de PitTec ou um encerramento explícito pelo cliente.
- **FR-003**: Recusa, ausência de resposta, desconexão ou indisponibilidade de um PitTec MUST fazer a busca prosseguir automaticamente para outros prestadores elegíveis, preservando a mesma solicitação.
- **FR-004**: Enquanto nenhum PitTec aceitar, o cliente MUST receber indicação persistente de que a busca continua e MUST poder cancelá-la.
- **FR-005**: O sistema MUST garantir que somente um PitTec possa obter o aceite válido de cada solicitação.
- **FR-006**: Após o primeiro aceite válido, o sistema MUST interromper novas ofertas da solicitação e MUST vinculá-la ao PitTec que aceitou.
- **FR-007**: O sistema MUST calcular o valor pelas regras comerciais existentes e MUST mostrar esse valor ao PitTec antes que ele decida aceitar a solicitação.
- **FR-008**: Após o aceite do PitTec, o sistema MUST colocar a solicitação no estado "aguardando confirmação do cliente" e MUST apresentar ao cliente um card com identificação do PitTec, exatamente o mesmo valor total em reais e ações para confirmar ou recusar o serviço.
- **FR-009**: Uma confirmação explícita e válida do cliente MUST iniciar o fluxo de pagamento existente, e o sistema MUST manter a solicitação em "aguardando pagamento" até receber o resultado.
- **FR-010**: Ao recusar o valor, o cliente MUST encerrar a solicitação sem iniciar o serviço, o PitTec MUST ser liberado e nenhuma cobrança específica desse atendimento MUST ser efetivada.
- **FR-011**: Se o PitTec aceito ficar indisponível antes da confirmação do cliente, o sistema MUST invalidar o card vigente, informar ambos os participantes e retomar automaticamente a busca na mesma solicitação.
- **FR-012**: Uma alteração do valor após sua apresentação MUST exigir nova confirmação explícita do cliente antes do início ou continuidade do serviço.
- **FR-013**: Após a confirmação do cliente e a aprovação do pagamento, o mapa do cliente MUST exibir simultaneamente o local fixo do atendimento registrado na solicitação e a posição do PitTec associado.
- **FR-014**: Após a confirmação do cliente e a aprovação do pagamento, o mapa do PitTec MUST exibir simultaneamente sua própria posição e o local fixo do atendimento registrado na solicitação.
- **FR-015**: Os mapas de ambos os participantes MUST adotar inicialmente um enquadramento que mantenha os dois marcadores visíveis e MUST oferecer uma ação clara para restaurar esse enquadramento após interação manual.
- **FR-016**: Enquanto o atendimento estiver em deslocamento, novas posições válidas MUST atualizar os marcadores correspondentes nos mapas sem exigir recarregamento manual da tela.
- **FR-017**: Enquanto o atendimento estiver em deslocamento, o sistema MUST recalcular e exibir a distância entre a posição atual do PitTec e o local fixo do atendimento a cada nova posição válida recebida.
- **FR-018**: Uma nova posição válida disponibilizada durante o acompanhamento MUST aparecer para o outro participante em até 10 segundos em pelo menos 95% das atualizações, sob condições normais de conectividade.
- **FR-019**: Quando uma posição não puder ser atualizada, o sistema MUST manter a última posição válida, identificá-la como desatualizada e comunicar a indisponibilidade sem inventar uma localização.
- **FR-020**: O sistema MUST recuperar o estado atual da solicitação após reconexão ou reabertura da tela, sem duplicar a chamada, o aceite ou a confirmação do valor.
- **FR-021**: Cliente e PitTec MUST receber atualizações coerentes das transições entre busca, aceite, confirmação, deslocamento, cancelamento e conclusão.
- **FR-022**: O local exato do atendimento e a posição do PitTec MUST ser compartilhados entre os participantes somente depois da aprovação do pagamento e enquanto o atendimento exigir acompanhamento.
- **FR-023**: Ao cancelar ou concluir o atendimento, o sistema MUST encerrar as atualizações e o compartilhamento de localização específicos da solicitação.
- **FR-024**: As telas MUST distinguir os participantes por marcador, rótulo ou ícone compreensível e MUST apresentar estado e distância sem depender apenas de cor.
- **FR-025**: O fluxo MUST tratar falta de permissão, localização indisponível, GPS desativado e perda de conectividade com mensagens acionáveis e possibilidade de recuperação.
- **FR-026**: O card de valor MUST exibir o prazo restante; se o cliente não confirmar em até dois minutos após o aceite do PitTec, o sistema MUST cancelar a solicitação, invalidar confirmações posteriores e liberar o prestador.
- **FR-027**: O sistema MUST iniciar o deslocamento e o rastreamento somente depois que o pagamento do valor confirmado for aprovado; pagamento pendente, recusado ou falho MUST impedir essa transição.
- **FR-028**: Durante o deslocamento, o PitTec MUST continuar enviando novas posições válidas quando bloquear a tela ou colocar o aplicativo em segundo plano, com indicação persistente de rastreamento ativo; esse envio MUST terminar ao cancelar ou concluir o atendimento.
- **FR-029**: Esta feature MUST NOT exigir que o cliente compartilhe localização em segundo plano; ao retornar ao mapa, ele MUST receber o estado e a posição mais recentes do PitTec.
- **FR-030**: A localização do atendimento MUST permanecer igual à registrada na criação da solicitação durante todo o fluxo; movimentos posteriores do cliente MUST NOT alterar o destino, e qualquer mudança de local MUST exigir o encerramento da chamada e uma nova solicitação.

### Scope Boundaries

**In scope**:

- Continuidade automática da busca dentro de uma única solicitação até o aceite ou cancelamento.
- Estado intermediário entre o aceite do PitTec e a confirmação do valor pelo cliente.
- Confirmação ou recusa do valor e sincronização dessa decisão para os dois perfis.
- Enquadramento conjunto, atualização dos marcadores e distância nos mapas do cliente e do PitTec durante o deslocamento.
- Recuperação do estado da chamada após interrupções temporárias.

**Out of scope**:

- Criação de novos meios de pagamento, regras de cobrança, estorno ou repasse.
- Navegação curva a curva, sugestão de rota, previsão de chegada ou tráfego em tempo real.
- Alteração das regras de elegibilidade, prioridade, raio ou ordenação usadas para selecionar PitTecs.
- Acompanhamento de localização fora da etapa em que ele é necessário para o atendimento confirmado.
- Rastreamento da localização do cliente em segundo plano.
- Mudanças no fluxo posterior à chegada, como execução técnica, conclusão e avaliação do serviço.

### Key Entities *(include if feature involves data)*

- **Solicitação PitTec**: representa uma única chamada do cliente; relaciona cliente, localização do atendimento, estado atual, tentativas de oferta, PitTec aceito, proposta de valor e atendimento decorrente.
- **Aceite do PitTec**: registra qual prestador obteve o primeiro aceite válido, quando aceitou e se ainda está disponível enquanto aguarda a decisão do cliente.
- **Proposta de valor**: representa o valor total em reais apresentado após o aceite, sua versão e a decisão do cliente; qualquer alteração gera uma nova versão que exige nova confirmação.
- **Confirmação do cliente**: decisão explícita de confirmar ou recusar a proposta, com momento, proposta de valor correspondente e validade limitada a dois minutos após o aceite do PitTec.
- **Local do atendimento**: destino fixo registrado na criação da solicitação, exibido nos dois mapas e usado como referência para calcular a distância; não acompanha movimentos posteriores do cliente.
- **Posição do PitTec**: última localização válida do prestador, com momento da atualização e indicação de atualidade; existe no contexto do atendimento e alimenta seu marcador e a distância até o local fixo.
- **Estado da chamada**: estágio mutuamente exclusivo entre busca, aguardando confirmação do cliente, aguardando pagamento, deslocamento, cancelada e concluída, com transições válidas e visíveis aos participantes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários com recusas ou ausência de resposta antes de um aceite, a mesma solicitação continua buscando sem exigir que o cliente toque novamente em "Chamar PitTec".
- **SC-002**: Em 100% dos atendimentos, o serviço só entra em deslocamento e inicia o rastreamento depois que o cliente confirma explicitamente o valor vigente e o pagamento é aprovado.
- **SC-003**: Em 100% das recusas de valor, nenhum serviço é iniciado e nenhuma cobrança específica do atendimento recusado é efetivada.
- **SC-004**: Em pelo menos 95% das atualizações sob condições normais de conectividade, uma nova posição válida aparece no mapa do outro participante em até 10 segundos.
- **SC-005**: Em 100% dos atendimentos confirmados com localizações disponíveis, cliente e PitTec conseguem visualizar o prestador e o local fixo do atendimento no mesmo enquadramento por uma única ação ou automaticamente ao abrir o acompanhamento.
- **SC-006**: Em pelo menos 95% das sessões de acompanhamento sob condições normais, a distância exibida é atualizada sem recarregamento manual sempre que chega uma nova posição válida.
- **SC-007**: Em testes de reconexão realizados em cada estado do fluxo, 100% das sessões recuperam o estado correto sem criar solicitação, aceite ou confirmação duplicados.
- **SC-008**: Pelo menos 90% dos participantes em teste de usabilidade identificam corretamente quem é cliente, quem é PitTec, o estado atual e a distância entre eles sem assistência.
- **SC-009**: Em 100% dos cenários sem decisão do cliente por dois minutos, a solicitação é cancelada, confirmações tardias são rejeitadas e o PitTec volta a ficar disponível sem ação manual.
- **SC-010**: Em pelo menos 95% das sessões sob condições normais, o cliente continua recebendo novas posições válidas do PitTec durante o deslocamento mesmo após o prestador bloquear a tela ou colocar o aplicativo em segundo plano.

## Assumptions

- "Continuar buscando" significa manter uma única solicitação ativa apesar de recusas, falta de resposta ou ausência momentânea de PitTecs, até que um prestador aceite ou o cliente cancele; os prazos individuais de resposta e as regras atuais de distribuição continuam vigentes.
- O preço é calculado pelas regras comerciais já existentes, sem edição pelo PitTec: o prestador conhece o valor antes de aceitar e o cliente recebe exatamente o mesmo valor para confirmação após o aceite.
- A confirmação do valor inicia o fluxo de pagamento existente; somente sua aprovação autoriza o deslocamento e o rastreamento. Esta especificação não cria nem altera meios de pagamento, captura, estorno ou repasse.
- Se o cliente recusar o valor, a chamada é encerrada em vez de voltar automaticamente à busca, pois a recusa é do serviço proposto e não apenas de um prestador.
- Cliente e PitTec já estão autenticados e possuem perfis válidos para solicitar ou aceitar chamadas.
- Os critérios atuais de proximidade, disponibilidade, elegibilidade e expansão da busca são reutilizados sem alteração.
- O acompanhamento começa após a confirmação do valor, preservando a localização exata do cliente antes de existir acordo entre as partes.
- "Distância" representa a distância atual entre a última posição válida do PitTec e o local fixo registrado na solicitação; cálculo de rota, trânsito e previsão de chegada não fazem parte desta feature.
- O comportamento manual do mapa é preservado: o usuário pode mover ou ampliar o mapa e usar uma ação de reenquadramento para voltar a ver o PitTec e o local fixo do atendimento.
- Condições normais de conectividade significam que os dois dispositivos possuem sessão válida, acesso à rede e localização habilitada com uma leitura válida disponível.
