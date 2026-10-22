# Feature Specification: Vistoria ampliada e rota de transporte do Guincho

**Feature Branch**: `feature/refinamento-guincho` (branch atual; diretório independente)

**Created**: 2026-10-14

**Status**: Draft — validada e pronta para planejamento

**Input**: User description: "Inclua nas fotos das fotos de revisao: Lateral Direita, Lateral Esquerda, Interior Porta Malas, Painel instrumentos, Bancos Dianteiros, Bancos Traseiros, Painel Completo, Kit Ferramenta, Estepe, Dianteira, traseira, pneu dianteiro esquerdo e direito, pneu traseiro direito e esquerdo, motor, bateria. Nem todos sao obrigatorios, apenas as 4 opcoes principais. No fluxo do guincho, quando o guincheiro iniciar o servio, deve abrir no mapa o percurso ate o destino final do guincho. Verifique a sincronizaao das fotos esta dando bug quando tira a foto com o celular deitado o app muda com a rotacao do celular. Melhore o carregamento e o salvamento das imagens, para que nnao fique travando ou quebrando a tela."

## Clarifications

### Session 2026-10-14

- Q: Quando o guincho sair do percurso indicado, o PitGo deve recalcular a rota automaticamente? → A: Opção A — recalcular automaticamente ao detectar um desvio, mantendo o destino final contratado.
- Q: Se o PitGo for fechado com fotos pendentes, o envio deve continuar em segundo plano ou retomar quando o aplicativo for reaberto? → A: Opção A — preservar as fotos pendentes e retomar automaticamente seu envio ao reabrir o PitGo; não é exigida continuidade do envio com o aplicativo fechado.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Documentar os pontos do veículo sem aumentar o mínimo obrigatório (Priority: P1)

Como guincheiro, quero encontrar os pontos principais e os detalhes do veículo em um álbum de vistoria, para registrar sua condição antes do transporte e complementar as evidências quando necessário.

**Why this priority**: Amplia o registro do veículo mantendo o esforço mínimo de quatro fotos já exigido no atendimento.

**Independent Test**: Abrir a vistoria de um atendimento ao registrar “Cheguei”, conferir os 17 pontos, fotografar somente os quatro obrigatórios e enviar ao cliente; repetir deixando um obrigatório vazio e preenchendo opcionais.

**Acceptance Scenarios**:

1. **Given** uma vistoria editável, **When** o guincheiro abrir o álbum, **Then** verá os 17 pontos do catálogo abaixo, identificados individualmente, com obrigatoriedade e situação de cada foto.
2. **Given** fotos salvas da dianteira, traseira e duas laterais, sem fotos opcionais, **When** enviar para validação, **Then** o envio será permitido e o cliente receberá exatamente esse conjunto.
3. **Given** um dos quatro pontos obrigatórios vazio e todos os opcionais preenchidos, **When** tentar enviar, **Then** o sistema informará o ponto faltante e impedirá o envio.
4. **Given** um ponto opcional acessível, **When** o guincheiro fotografá-lo, **Then** verá a miniatura no quadro correspondente e o ponto integrará a versão enviada e o histórico quando salvo.
5. **Given** motor, porta-malas ou outro ponto opcional inacessível, **When** o guincheiro deixar de fotografá-lo, **Then** esse ponto permanecerá não registrado sem impedir o envio dos quatro principais.
6. **Given** uma avaria que não se enquadra nos pontos nomeados, **When** adicionar uma foto de detalhe, **Then** poderá incluí-la com descrição opcional, preservando a possibilidade existente de registrar evidências adicionais.

---

### User Story 2 - Fotografar e salvar sem perder a etapa nem bloquear o álbum (Priority: P1)

Como guincheiro, quero fotografar com o aparelho em pé ou deitado e voltar ao álbum com minha imagem identificada, para continuar a vistoria mesmo enquanto outras fotos são preparadas ou salvas.

**Why this priority**: Evita perda de evidências, repetição de trabalho e interrupções no atendimento pelos problemas relatados de rotação, carregamento e salvamento.

**Independent Test**: Capturar fotos verticais e horizontais, girar o aparelho durante captura, retorno e salvamento, interromper a conexão e retomar a vistoria; conferir miniaturas, pontos, quantidade de fotos e resultado recebido pelo cliente.

**Acceptance Scenarios**:

1. **Given** um ponto selecionado, **When** a câmera concluir uma captura aceita, **Then** o aplicativo retornará automaticamente ao álbum, mostrará a miniatura no mesmo ponto e indicará a preparação ou o salvamento pendente.
2. **Given** captura horizontal ou mudança de posição do aparelho durante o processo, **When** retornar à vistoria, **Then** o álbum manterá sua apresentação vertical e a etapa atual; a foto será exibida na orientação correta, sem inversão, deformação, perda ou duplicação.
3. **Given** uma foto sendo salva, **When** o guincheiro percorrer o álbum, consultar outra imagem ou iniciar a captura de outro ponto, **Then** essas ações continuarão disponíveis, sem aguardar o salvamento anterior nem abrir câmeras simultâneas.
4. **Given** uma foto aceita pela câmera com salvamento ainda pendente, **When** houver perda de conexão, fechamento ou interrupção do aplicativo e posterior reabertura pela mesma conta, **Then** o ponto, a imagem disponível e a pendência serão recuperados e seu envio será retomado automaticamente quando houver conexão e permissão para editar a vistoria, sem nova captura nem toque adicional para iniciar a retomada.
5. **Given** conexão interrompida após o salvamento ter sido efetivado, mas antes de sua confirmação na tela, **When** houver reconexão ou nova tentativa, **Then** o álbum reconhecerá a foto já salva, sem criar cópias ou perder sua associação ao ponto.
6. **Given** falha ao salvar ou carregar uma imagem, **When** o álbum for exibido, **Then** somente o quadro afetado mostrará a falha e a ação para tentar novamente; as demais imagens e a navegação continuarão utilizáveis.
7. **Given** uma substituição em andamento, **When** seu salvamento falhar, **Then** a foto anterior já salva continuará preservada e a nova tentativa será identificada como pendente, sem substituir silenciosamente o registro confirmado.
8. **Given** uma câmera cancelada sem foto, **When** retornar, **Then** o álbum manterá as imagens e a etapa anterior, sem criar foto vazia nem marcar o ponto como concluído.
9. **Given** fotos pendentes recuperadas ao reabrir o PitGo sem conexão, **When** a conexão voltar enquanto o aplicativo estiver em uso pela mesma conta autorizada, **Then** o envio será retomado automaticamente, mantendo cada situação individual e sem duplicar fotos cujo salvamento já tenha sido efetivado.

---

### User Story 3 - Ver o percurso até o destino ao iniciar o transporte (Priority: P1)

Como guincheiro responsável, quero que o início confirmado do transporte abra o percurso até o destino final contratado, para acompanhar o deslocamento do veículo durante a execução.

**Why this priority**: O mapa deve corresponder à etapa real do serviço e orientar o transporte após a vistoria aprovada.

**Independent Test**: Em um atendimento com vistoria aprovada, tocar em “Iniciar transporte”, conferir o destino e o percurso no mapa, avançar a posição e reabrir o atendimento; repetir com falha no início, localização indisponível e erro de carregamento da rota.

**Acceptance Scenarios**:

1. **Given** vistoria aprovada e transporte aguardando início, **When** o guincheiro tocar em “Iniciar transporte” e a ação for confirmada, **Then** o mapa do mesmo atendimento será aberto automaticamente com o percurso viário da posição atual do guincho até o destino final contratado, identificado no mapa.
2. **Given** aprovação das fotos sem ação de início do guincheiro, **When** o atendimento for atualizado ou reaberto, **Then** o transporte continuará aguardando início e a rota de transporte não será ativada como se o veículo já estivesse a caminho do destino.
3. **Given** uma tentativa de início recusada ou não confirmada, **When** o guincheiro consultar o atendimento, **Then** verá a situação real e a possibilidade de recuperação, sem uma falsa indicação de transporte iniciado.
4. **Given** transporte em andamento, **When** o guincho se deslocar ou o guincheiro reabrir o acompanhamento, **Then** a posição e o percurso disponível corresponderão ao deslocamento até o mesmo destino final, sem voltar a orientar a chegada ao local de retirada.
5. **Given** início já confirmado e falha de conexão, de localização ou de carregamento da rota, **When** abrir o mapa, **Then** verá o destino, o impedimento e uma ação para recuperar o percurso; uma rota anterior disponível será preservada com sua situação indicada, sem desfazer o início ou a aprovação.
6. **Given** transporte concluído ou atendimento cancelado, **When** a situação for confirmada, **Then** o mapa deixará de apresentar esse transporte como ativo e as fotos já salvas continuarão consultáveis conforme o histórico existente.
7. **Given** transporte em andamento e posições atuais suficientemente precisas que confirmem saída do percurso, **When** o desvio for detectado, **Then** o mapa recalculará e exibirá automaticamente o caminho até o mesmo destino final contratado, sem exigir uma ação do guincheiro nem reiniciar o transporte.
8. **Given** um percurso válido e leituras antigas, imprecisas ou com oscilações sem desvio confirmado, **When** essas leituras forem recebidas, **Then** o mapa manterá a rota de referência com a indicação de localização aplicável, sem recalcular repetidamente por um falso desvio.

---

### User Story 4 - Revisar e consultar o registro completo sem alterar aprovações anteriores (Priority: P2)

Como cliente, quero revisar as fotos principais e opcionais com seus nomes e consultar o mesmo registro posteriormente, para validar a condição inicial do meu veículo com confiança.

**Why this priority**: Os novos pontos só têm valor se o cliente receber o conjunto correto e seu histórico continuar confiável.

**Independent Test**: Revisar uma versão com quatro fotos principais e fotos opcionais, aprová-la e consultar o histórico como cada participante; repetir com uma vistoria anterior à ampliação do catálogo.

**Acceptance Scenarios**:

1. **Given** uma versão enviada com fotos opcionais, **When** o cliente abrir a revisão, **Then** verá todas as fotos incluídas, com nomes e orientações corretos, e poderá ampliá-las sem que uma falha isolada impeça consultar as demais.
2. **Given** uma foto incluída ainda não carregada corretamente para revisão, **When** tentar aprovar, **Then** a aprovação permanecerá indisponível até a recuperação da imagem; pontos opcionais que não integram a versão não serão exigidos.
3. **Given** aprovação confirmada pelo cliente, **When** a ação terminar, **Then** o cliente retornará ao acompanhamento da mesma transação; o guincheiro continuará responsável por iniciar o transporte explicitamente.
4. **Given** uma vistoria anterior já enviada ou aprovada, **When** qualquer participante consultá-la após a atualização, **Then** suas fotos, nomes, decisões e versões permanecerão preservados, sem exigência retroativa dos novos pontos opcionais.

### Edge Cases

- Aparelho girado várias vezes durante captura, retorno ou salvamento: manter álbum, ponto e imagem; preservar uma única operação válida por captura aceita.
- Aplicativo fechado ou interrompido após captura, durante preparação, durante envio ou após confirmação: preservar as pendências e, ao reabrir pela mesma conta, recuperar o resultado real e retomar automaticamente o envio permitido das imagens disponíveis; não exigir envio contínuo com o PitGo fechado. Sem conexão ou autorização, manter a situação real e aguardar a condição necessária.
- Arquivo de captura removido pelo sistema, sem conteúdo legível ou sem espaço para armazenamento: informar a impossibilidade de recuperar aquela imagem e permitir refazê-la, preservando as demais fotos e sem confirmar um salvamento inexistente.
- Câmera sem permissão, indisponível ou cancelada: informar o impedimento quando aplicável e voltar ao álbum sem perder o progresso existente.
- Várias fotos pendentes e conexão oscilando: manter estado individual e permitir retomar, sem duplicação nem envio prematuro para aprovação.
- Álbum com os 17 pontos fotografados e imagens de alta resolução: manter navegação e carregamento utilizáveis, com indicação individual das imagens ainda em preparação ou carregamento.
- Foto opcional incluída cujo salvamento falhou: permitir tentar novamente ou retirá-la explicitamente da versão editável; fotos obrigatórias continuam exigidas.
- Substituição de foto com resultado incerto: conferir o que foi efetivamente salvo antes de informar sucesso, repetir o envio ou abandonar a substituição.
- Cliente recebe atualização enquanto consulta outra imagem: preservar a consulta e indicar a versão vigente; não misturar conjuntos ou aprovar versão antiga.
- Atendimento encerrado, sessão expirada ou troca de conta durante uma operação: interromper ações não permitidas e impedir que imagens pendentes sejam exibidas ou enviadas no contexto de outra conta ou atendimento.
- Início repetido ou retomado após perda da resposta: reconhecer a mesma ação confirmada, sem duplicar o início nem ativar a etapa errada do mapa.
- Posição indisponível ou antiga: indicar a limitação; não apresentar uma posição antiga como atual nem um percurso baseado nela como atualizado.
- Oscilação ou imprecisão de localização sem saída confirmada do percurso: não iniciar recálculos repetidos nem descartar a rota válida; o recálculo automático depende de posições atuais suficientemente precisas para confirmar o desvio.
- Percurso indisponível para o destino contratado: apresentar o destino e a falha com opção de tentar novamente, sem substituir o destino por outro local nem desenhar uma ligação direta como se fosse rota viária.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O álbum DEVE oferecer os 17 pontos nomeados no catálogo abaixo, além da possibilidade existente de incluir fotos adicionais de detalhes ou avarias com descrição opcional.
- **FR-002**: Somente dianteira, traseira, lateral esquerda e lateral direita DEVEM ser obrigatórias. O progresso obrigatório DEVE permanecer separado do total de fotos e variar de zero a quatro; opcionais vazias NÃO DEVEM impedir o envio nem a aprovação.
- **FR-003**: Cada ponto DEVE ser apresentado em um mini quadro identificado, com indicação de obrigatório ou opcional, miniatura quando houver captura e situação individual da imagem. O guincheiro DEVE poder abrir a foto e refazê-la enquanto a versão for editável.
- **FR-004**: Toda foto DEVE permanecer associada ao atendimento, veículo, ponto, autoria, momento de captura e versão corretos. Cada ponto nomeado DEVE ter uma foto vigente por versão; substituir uma imagem NÃO DEVE criar dois resultados vigentes no mesmo ponto. Fotos adicionais de detalhes podem coexistir.
- **FR-005**: Após uma captura aceita pela câmera, o sistema DEVE retornar automaticamente ao álbum e iniciar sua preparação e salvamento sem exigir confirmação adicional para simplesmente voltar ao álbum. Cancelar a câmera NÃO DEVE criar uma foto nem alterar as fotos já existentes. A captura continua sendo feita no contexto da vistoria, conforme as regras existentes.
- **FR-006**: O álbum da vistoria DEVE manter apresentação vertical estável durante a captura e seu retorno, mesmo quando o aparelho for deitado. A imagem DEVE preservar sua orientação horizontal ou vertical correta na miniatura, ampliação, revisão do cliente e histórico, sem inversão ou deformação.
- **FR-007**: Rotação, retorno da câmera, ida a outro aplicativo e interrupção pelo sistema NÃO DEVEM reiniciar a vistoria, trocar o ponto selecionado, duplicar a captura ou descartar silenciosamente imagens aceitas e ainda pendentes. A retomada pela mesma conta DEVE recuperar as imagens ainda disponíveis e seu estado real; indisponibilidade do arquivo DEVE exigir aviso explícito e possibilidade de recaptura.
- **FR-008**: O sistema DEVE distinguir, por imagem, preparação, salvamento pendente ou em andamento, salvamento confirmado e falha. Uma miniatura local NÃO DEVE ser apresentada como confirmação de que a imagem já foi salva para consulta pelos participantes.
- **FR-009**: Preparar, salvar ou carregar uma foto NÃO DEVE bloquear a navegação, a rolagem do álbum, a consulta de outras imagens ou a captura do próximo ponto. Somente uma captura de câmera pode estar aberta por vez, e as ações dependentes de salvamento confirmado DEVEM continuar protegidas.
- **FR-010**: Fotos já aceitas com salvamento interrompido DEVEM permanecer preservadas e identificadas como pendentes. Ao reabrir o PitGo pela mesma conta autenticada, o envio das imagens disponíveis DEVE ser retomado automaticamente, sem nova captura nem ação adicional, quando houver conexão e autorização para editar a vistoria. Se a conexão voltar enquanto o aplicativo estiver em uso, a retomada também DEVE ser automática. Não se exige continuidade do envio com o PitGo fechado. O resultado real de cada foto DEVE ser respeitado, sem duplicar salvamentos já efetivados. A imagem disponível NÃO DEVE ser descartada antes de o salvamento ser confirmado ou de o usuário descartá-la explicitamente em uma versão editável.
- **FR-011**: Repetições de captura concluída, retorno da câmera, tentativa de salvamento e sincronização DEVEM reconhecer o resultado já existente para aquela captura, sem criar cópias, alterar seu ponto nem sobrescrever uma versão enviada ou aprovada.
- **FR-012**: Falhas de preparação, salvamento ou carregamento DEVEM aparecer no quadro afetado com ação de recuperação. O guincheiro DEVE poder repetir a operação, refazer a captura ou excluir explicitamente uma inclusão opcional enquanto a versão for editável; a ausência de um obrigatório continua impedindo envio.
- **FR-013**: Ao refazer uma foto, a imagem anterior confirmada DEVE permanecer preservada até a nova ser salva. Uma substituição pendente DEVE ser identificada e impedir o envio do conjunto até sua confirmação ou abandono explícito, evitando divergência entre o que aparece no álbum e o que o cliente receberá.
- **FR-014**: Enviar para aprovação DEVE exigir os quatro pontos obrigatórios salvos e todas as fotos incluídas na versão com salvamento confirmado. Fotos opcionais ainda pendentes só deixam de impedir o envio após recuperação ou retirada explícita; nenhuma foto apenas local DEVE ser tratada como parte já compartilhada da versão.
- **FR-015**: Cliente e guincheiro DEVEM consultar o mesmo conjunto enviado, com pontos identificados e imagens ampliáveis. Aprovação, correção e preservação das versões DEVEM seguir a vistoria existente, incluindo impedimento de aprovação quando uma imagem incluída não puder ser apresentada corretamente ao cliente.
- **FR-016**: Fotos e operações pendentes DEVEM respeitar as permissões existentes do atendimento. Encerramento, expiração da sessão ou troca de conta NÃO DEVEM permitir consulta indevida, novos envios ou decisões no contexto errado.
- **FR-017**: Salvamentos confirmados e versões enviadas DEVEM ser recuperáveis na retomada e sincronizados para os participantes conforme a etapa de revisão. Fotos pendentes em um aparelho NÃO DEVEM gerar falsas confirmações no outro.
- **FR-018**: Vistorias antigas DEVEM continuar legíveis e válidas, com seu conjunto e aprovação originais. A ampliação do catálogo NÃO DEVE exigir fotos retroativas nem reclassificar ou invalidar registros já enviados ou aprovados.
- **FR-019**: Quando o início de transporte acionado pelo guincheiro responsável for confirmado, o sistema DEVE abrir automaticamente o mapa do mesmo atendimento com o percurso até o destino final contratado, com enquadramento que permita identificar o traçado e o destino, sem exigir uma nova ação para exibir a rota.
- **FR-020**: O percurso DEVE representar o caminho pelas vias entre a posição disponível do guincho e o destino final do veículo, identificado no mapa. O destino final NÃO DEVE ser confundido com a retirada ou a base do prestador. Posição antiga ou indisponível DEVE ser identificada e NÃO DEVE sustentar uma falsa indicação de rota atualizada.
- **FR-021**: A aprovação das fotos DEVE continuar liberando somente a ação “Iniciar transporte”. Início não confirmado NÃO DEVE ativar o transporte no mapa; repetições após confirmação DEVEM reconhecer o início existente.
- **FR-022**: Durante o transporte, o mapa DEVE acompanhar a posição disponível do guincho e manter o destino final identificado. Quando posições atuais suficientemente precisas confirmarem saída do percurso, o sistema DEVE recalcular e exibir automaticamente a rota até o mesmo destino contratado, sem exigir interação do guincheiro ou novo início do transporte. Leituras antigas, imprecisas ou com oscilações sem desvio confirmado NÃO DEVEM provocar recálculos repetidos. Ao retomar o acompanhamento, o percurso DEVE corresponder à posição atual disponível e à etapa vigente, com atualização automática quando necessária e indicação de eventual indisponibilidade.
- **FR-023**: Falha no carregamento do percurso ou indisponibilidade de conexão/localização após o início confirmado DEVE apresentar mensagem e ação para recuperação, preservar uma rota anterior quando disponível com a situação indicada e manter a aprovação e o início registrados. Uma ligação direta entre pontos NÃO DEVE ser apresentada como percurso viário válido.
- **FR-024**: Ao reabrir um transporte em andamento, o guincheiro DEVE recuperar o mapa da etapa de transporte sem nova aprovação, novo início ou retorno à orientação de chegada para retirada. Após encerramento confirmado, esse transporte NÃO DEVE continuar indicado como ativo.
- **FR-025**: Após aprovação confirmada, o cliente DEVE retornar ao acompanhamento da mesma transação e continuar recebendo a situação do serviço, preservando a consulta posterior às fotos aprovadas.
- **FR-026**: A feature DEVE preservar as condições vigentes de chegada, pagamento, aprovação, início pelo guincheiro, cancelamento e confirmação final pelo cliente. Falha de foto ou rota NÃO DEVE produzir aprovação, início, cobrança ou conclusão fictícios.

#### Catálogo de pontos da vistoria

| Ponto | Obrigatoriedade |
| --- | --- |
| Dianteira | Obrigatória |
| Traseira | Obrigatória |
| Lateral esquerda | Obrigatória |
| Lateral direita | Obrigatória |
| Interior do porta-malas | Opcional |
| Painel de instrumentos | Opcional |
| Bancos dianteiros | Opcional |
| Bancos traseiros | Opcional |
| Painel completo | Opcional |
| Kit ferramenta | Opcional |
| Estepe | Opcional |
| Pneu dianteiro esquerdo | Opcional |
| Pneu dianteiro direito | Opcional |
| Pneu traseiro direito | Opcional |
| Pneu traseiro esquerdo | Opcional |
| Motor | Opcional |
| Bateria | Opcional |

### Key Entities *(include if feature involves data)*

- **Ponto de vistoria**: Parte identificada do veículo, com nome, indicação de obrigatoriedade e foto vigente na versão.
- **Foto da vistoria**: Evidência capturada, vinculada ao atendimento, veículo, ponto, autor e momento de captura; preserva a orientação correta da imagem e sua associação à versão.
- **Pendência de salvamento**: Foto aceita cuja preparação ou salvamento ainda não foi confirmado, com ponto, imagem disponível, situação e possibilidade de recuperação no contexto da mesma conta e atendimento.
- **Versão e decisão da vistoria**: Conjunto enviado para aprovação ou correção do cliente, com seus pontos e fotos preservados para consulta posterior.
- **Transporte do Guincho**: Etapa do atendimento com início confirmado pelo responsável, posição disponível e destino final contratado.
- **Percurso de transporte**: Caminho pelas vias entre uma posição de referência do guincho e o destino final, com indicação de disponibilidade e necessidade de atualização.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de conferência, o álbum oferece os 17 pontos, com exatamente quatro obrigatórios e 13 opcionais. Quatro principais salvos permitem enviar sem opcionais; a falta de qualquer principal impede o envio mesmo quando os 13 opcionais estão preenchidos.
- **SC-002**: Em pelo menos 40 capturas, distribuídas entre os aparelhos de homologação e incluindo fotos verticais e horizontais, ao menos 95% dos retornos da câmera apresentam o álbum e a miniatura no ponto correto em até 2 segundos após o aplicativo receber a captura, sem esperar a conclusão do salvamento.
- **SC-003**: Em pelo menos 20 sequências de rotação e interrupção durante captura, retorno e salvamento, 100% preservam a etapa e a associação correta das imagens disponíveis, sem fotos duplicadas ou confirmadas falsamente. Uma imagem que realmente tenha se tornado indisponível é identificada, sem perda silenciosa do progresso restante.
- **SC-004**: Com fotos sendo preparadas, salvas ou carregadas, ao menos 95% de 40 ações de navegar no álbum, abrir outro ponto ou iniciar outra captura apresentam resposta visual em até 500 milissegundos, incluindo álbuns com os 17 pontos fotografados; nenhuma avaliação encerra o aplicativo ou torna o álbum inutilizável. Nas condições de referência, ao menos 95% de 40 imagens têm salvamento confirmado em até 15 segundos após o retorno da câmera.
- **SC-005**: Em pelo menos 20 tentativas com desconexão, repetição ou perda de confirmação, incluindo pelo menos cinco fechamentos e reaberturas do PitGo, 100% dos resultados preservam uma única foto vigente por ponto e versão e recuperam fotos já salvas. Com a mesma conta autenticada, conexão disponível, imagens recuperáveis e autorização para editar a vistoria, a retomada automática do envio começa em até 5 segundos após a reabertura ou recuperação da conexão durante o uso, sem nova captura nem toque para iniciar a retomada. Nenhuma aprovação é liberada com foto incluída ainda sem salvamento confirmado ou indisponível para revisão.
- **SC-006**: Nas condições de referência, ao menos 95% de 20 inícios confirmados de transporte exibem no mapa o percurso viário até o destino contratado em até 5 segundos após a confirmação. Nas mesmas condições, ao menos 95% de 20 desvios confirmados exibem o novo percurso até o mesmo destino em até 5 segundos após a detecção, sem interação do guincheiro. Em 100% dos cenários de falha, a tela informa o impedimento e oferece recuperação sem alterar o início ou a aprovação registrados.
- **SC-007**: Com os participantes no atendimento e conexão contínua, ao menos 95% de 20 envios, correções e aprovações ficam visíveis ao outro participante em até 10 segundos após a confirmação da ação. Em 100% das revisões e consultas avaliadas, ponto, orientação e conjunto de fotos correspondem à versão salva.
- **SC-008**: Em 100% dos cenários de retomada, compatibilidade e permissão, vistorias antigas mantêm suas decisões; transporte já iniciado retoma a etapa e o destino corretos; aprovação isolada não inicia transporte; contas alheias ao atendimento não acessam as fotos nem suas pendências.
- **SC-009**: Nas condições de referência, ao menos 95% de 20 aberturas de álbuns com os 17 pontos salvos mostram as miniaturas inicialmente visíveis em até 5 segundos; ao menos 95% de 20 aberturas de imagens para ampliação mostram a imagem em até 5 segundos. O carregamento de uma imagem não impede consultar os outros pontos, e falhas são identificadas individualmente.

## Assumptions

- As quatro opções principais são as já exigidas na vistoria atual: dianteira, traseira, lateral esquerda e lateral direita. Os outros 13 pontos são opcionais; “painel de instrumentos” e “painel completo” representam registros distintos.
- A referência de esquerda e direita é a do próprio veículo. Pontos opcionais inacessíveis ou cuja abertura não seja segura podem permanecer sem foto; não se exige exposição a risco nem desmontagem.
- A solicitação de estabilidade durante rotação é interpretada como manter o álbum da vistoria na vertical, permitindo capturar fotos horizontais corretamente. Essa decisão se limita à vistoria e não altera a orientação das outras telas.
- “Iniciar o serviço” significa a ação existente “Iniciar transporte”, executada pelo guincheiro após a aprovação da vistoria. A aprovação isolada continua sem iniciar o deslocamento.
- O percurso solicitado aparece no mapa do próprio atendimento do guincheiro e inclui recálculo automático quando um desvio for confirmado por posições atuais suficientemente precisas, conforme decisão de 14/10/2026. O cliente mantém seu acompanhamento e a sincronização da situação do transporte; navegação por voz, instruções curva a curva, abertura obrigatória de aplicativo externo e mudança do destino contratado ficam fora do escopo.
- A recuperação de salvamento inclui fotos já capturadas no contexto de uma vistoria existente. Ao fechar o PitGo, as pendências são preservadas para retomada automática ao reabrir pela mesma conta autorizada, conforme decisão de 14/10/2026; não se exige continuidade de envio com o aplicativo fechado. Não amplia o fluxo para criação integral de atendimento e aprovação sem conexão; envio confirmado e decisões do cliente continuam exigindo conexão.
- Os arquivos pendentes precisam estar disponíveis no aparelho para recuperação. Limpeza de dados, remoção do aplicativo ou descarte de arquivos pelo sistema podem exigir nova captura de imagens ainda não salvas, com aviso explícito; imagens cujo salvamento foi confirmado permanecem no registro do serviço.
- Condições de referência para os tempos de salvamento, carregamento e rota: aparelhos usados pela equipe na homologação, câmera em configuração padrão com foto de até 16 megapixels, conexão de pelo menos 10 Mbps, atraso de rede de até 200 milissegundos, espaço disponível e serviços de imagem e percurso operantes; para a rota, posição atual válida do guincho e destino previamente confirmado.
- Dependências: vistoria e validação do cliente da feature 030, atendimento e destino do Guincho existentes, captura de câmera, salvamento e consulta de imagens, autenticação dos participantes, acompanhamento de posição e disponibilidade de percursos pelas vias. O planejamento técnico deverá verificar a disponibilidade dessas capacidades nos ambientes em que a feature será entregue.
- Escopo restrito à vistoria de retirada e ao percurso da etapa de transporte do Guincho. Avaliação automática de danos, vistoria na entrega, exportação de laudo, novos processos de disputa e alterações nas regras financeiras não fazem parte desta feature.
