# Feature Specification: Vistoria fotográfica do veículo no Guincho

**Feature Branch**: `feature/refinamento-guincho` (branch atual; diretório independente)

**Created**: 2026-10-13

**Status**: Draft — validada e pronta para planejamento

**Input**: User description: "No fluxo da contratacao de guincho, quando o guincheiro clicar me 'cheguei' deve ter um fluxo para tirar fotos do veiculo que solicitou. Deve ser tirado fotos de pontos do carro pra ficar no historico do servico. um dos objetivos e ter prova de como o veiculo estava antes do servio. Deve ter uma validacao do lado do cliente. Ele deve dar um ok para as fotos."

## Clarifications

### Session 2026-10-13

- Q: Depois que o cliente aprovar as fotos, o transporte deve começar automaticamente no aplicativo ou o guincheiro deve tocar em “Iniciar transporte”? → A: Opção B — a aprovação libera o botão “Iniciar transporte”; o guincheiro toca nele para avançar.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fotografar o veículo ao chegar (Priority: P1)

Como guincheiro responsável pelo atendimento, quero registrar fotos guiadas do veículo contratado ao tocar em “Cheguei”, antes de carregá-lo ou transportá-lo, para documentar sua condição na retirada.

**Why this priority**: As imagens anteriores à movimentação são o registro necessário para consultar a condição inicial do veículo.

**Independent Test**: Em um pedido aceito e pago, tocar em “Cheguei”, fotografar os quatro pontos obrigatórios e enviar a vistoria, verificando o vínculo com pedido e veículo e a pendência de aprovação.

**Acceptance Scenarios**:

1. **Given** um atendimento de Guincho aceito e pago, com guincheiro a caminho, **When** o responsável tocar em “Cheguei”, **Then** a chegada será registrada e o fluxo de fotos do veículo desse pedido será aberto, sem liberar o transporte.
2. **Given** uma vistoria em preenchimento, **When** o guincheiro consultar os pontos solicitados, **Then** verá frente, traseira, lateral esquerda e lateral direita, cada uma identificada em relação ao veículo e com indicação de pendente ou fotografada.
3. **Given** um ponto pendente, **When** o guincheiro tirar e confirmar uma foto, **Then** poderá visualizá-la, refazê-la antes do envio e acompanhar o progresso; também poderá incluir fotos adicionais de avarias ou detalhes com descrição opcional.
4. **Given** pelo menos um ponto obrigatório sem foto confirmada, **When** tentar enviar a vistoria, **Then** o envio será impedido e os pontos faltantes serão informados.
5. **Given** os quatro pontos preenchidos e todas as fotos confirmadas como salvas, **When** o guincheiro enviar a vistoria, **Then** o conjunto será disponibilizado ao cliente para revisão e ambos verão “Aguardando aprovação das fotos”.

---

### User Story 2 - Validar as fotos antes do transporte (Priority: P1)

Como cliente que contratou o Guincho, quero revisar as fotos do meu veículo e dar um “OK” explícito ou solicitar correções, para que o registro corresponda à condição do veículo antes do transporte.

**Why this priority**: O registro precisa da validação solicitada pelo cliente e de um caminho para corrigir fotos inadequadas.

**Independent Test**: Disponibilizar uma vistoria enviada, abrir cada foto como cliente, solicitar correção e revisar uma nova versão; aprovar e verificar que o transporte continua aguardando início até o guincheiro tocar em “Iniciar transporte”.

**Acceptance Scenarios**:

1. **Given** uma vistoria enviada, **When** o cliente abrir ou retomar o atendimento, **Then** verá a pendência de revisão sem precisar expandir os detalhes do mapa, poderá abrir as fotos em tamanho ampliado e identificar o ponto de cada imagem.
2. **Given** a versão vigente aguardando aprovação, **When** o cliente responsável tocar em “Aprovar fotos” e a confirmação for registrada, **Then** seu “OK” ficará associado àquela versão, com identificação e data/hora, ambos verão a aprovação e o botão “Iniciar transporte” ficará disponível ao guincheiro, mantendo o transporte aguardando início.
3. **Given** fotos inadequadas ou incompletas na percepção do cliente, **When** escolher “Solicitar correção” e informar um motivo não vazio, **Then** ambos verão “Correção solicitada”, o guincheiro verá o motivo e o transporte continuará bloqueado.
4. **Given** correção solicitada, **When** o guincheiro refizer ou acrescentar fotos e reenviar o conjunto completo, **Then** uma nova versão aguardará aprovação e a anterior ficará preservada com a solicitação de correção.
5. **Given** cliente ausente, sem conexão ou que ainda não decidiu, **When** houver espera ou reabertura do atendimento, **Then** a pendência permanecerá e nenhum “OK” será presumido pelo tempo decorrido.
6. **Given** aprovação registrada, **When** o serviço chegar à etapa de conclusão, **Then** a confirmação de conclusão pelo cliente continuará sendo uma ação separada da aprovação das fotos.
7. **Given** fotos aprovadas e transporte aguardando início, **When** o guincheiro responsável tocar em “Iniciar transporte” e a ação for registrada, **Then** ambos verão “Transporte em andamento” e a solicitação de conclusão ficará disponível ao guincheiro.
8. **Given** fotos aprovadas e transporte aguardando início, **When** qualquer participante fechar e reabrir o atendimento, **Then** a aprovação será preservada e o transporte continuará aguardando a ação do guincheiro, sem início automático nem nova aprovação das fotos.

---

### User Story 3 - Consultar a vistoria no histórico do serviço (Priority: P2)

Como participante do atendimento, quero consultar no histórico as fotos, as correções e a aprovação, para recuperar o registro da condição do veículo antes do serviço.

**Why this priority**: Preservar e recuperar o registro permite esclarecer divergências após a execução do transporte.

**Independent Test**: Consultar um serviço concluído com uma versão corrigida e outra aprovada, como cliente e guincheiro, verificando imagens, sequência e autoria; repetir em serviço cancelado que já possua fotos salvas.

**Acceptance Scenarios**:

1. **Given** um serviço concluído, **When** qualquer participante abrir sua vistoria no histórico, **Then** poderá consultar as fotos agrupadas por versão e ponto, veículo, guincheiro responsável, datas/horários de captura e decisões do cliente, com a versão aprovada destacada.
2. **Given** uma vistoria aprovada, **When** qualquer participante consultá-la ou tentar alterá-la, **Then** o conjunto aprovado e o registro de aprovação permanecerão preservados, sem substituição ou exclusão pelos participantes.
3. **Given** cancelamento ou contestação de um serviço com fotos já salvas, **When** os participantes consultarem seu histórico, **Then** as fotos permanecerão vinculadas ao pedido, com o estado real da vistoria, inclusive “Não aprovada” quando aplicável.
4. **Given** uma conta que não participa do pedido, **When** tentar consultar suas fotos, **Then** o acesso será negado; suporte autorizado poderá consultar o registro somente no contexto de atendimento permitido pelas regras existentes.

### Edge Cases

- Câmera sem permissão ou indisponível: explicar o impedimento e permitir tentar novamente após corrigi-lo; nenhum ponto será marcado como fotografado sem uma imagem confirmada.
- Ponto do veículo inacessível ou perigoso de fotografar: orientar que o guincheiro não se exponha a risco e procure o suporte existente; manter a vistoria incompleta, sem dispensa ou aprovação fictícia.
- Perda de conexão durante o envio: identificar fotos salvas e fotos ainda pendentes, permitir repetir o envio e manter o transporte bloqueado até a aprovação registrada.
- Aplicativo fechado durante captura ou revisão: recuperar chegada, fotos já confirmadas como salvas e etapa vigente; imagem ainda não confirmada poderá exigir nova captura, com aviso explícito.
- Toques repetidos em “Cheguei”, enviar, corrigir ou aprovar: retomar a vistoria existente e preservar um único resultado para cada ação, sem versões ou aprovações duplicadas.
- Cliente tenta aprovar versão antiga: informar que existe uma versão mais recente e abrir a vigente, sem aprovar silenciosamente outras imagens.
- Correção enviada por um aparelho enquanto outro tenta aprovar: aceitar apenas uma decisão válida para a versão vigente e atualizar os dois participantes antes de nova ação.
- Foto não carrega durante a revisão: mostrar falha recuperável e impedir aprovação enquanto alguma foto da versão não puder ser apresentada ao cliente.
- Cancelamento durante captura ou revisão: seguir o cancelamento existente, encerrar as ações de vistoria e preservar o material já salvo sem marcar aprovação.
- Serviço anterior à adoção da vistoria, já em transporte ou concluído: manter seu fluxo e indicar “Vistoria não registrada” no histórico, sem exigir fotos retroativas ou inventar um aceite.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE abrir a vistoria ao guincheiro responsável tocar em “Cheguei” em um atendimento de Guincho aceito e pago, registrar a chegada e associar o registro exclusivamente ao pedido e ao veículo contratado.
- **FR-002**: A vistoria DEVE exigir ao menos uma foto confirmada de cada ponto: frente, traseira, lateral esquerda e lateral direita, com orientações de enquadramento que identifiquem o ponto do veículo. Fotos adicionais de avarias ou detalhes DEVEM ser permitidas, com descrição opcional.
- **FR-003**: O guincheiro DEVE capturar as fotos no contexto da vistoria, visualizar o resultado e poder refazer imagens antes do envio. A seleção de imagens preexistentes da galeria NÃO DEVE substituir a captura solicitada.
- **FR-004**: O sistema DEVE mostrar o progresso por ponto e impedir o envio para aprovação enquanto faltar ponto obrigatório ou houver foto incluída cujo salvamento não tenha sido confirmado.
- **FR-005**: Cada foto salva DEVE identificar pedido, veículo, ponto ou detalhe adicional, autor e data/hora de captura. O envio DEVE registrar uma versão identificável do conjunto, com data/hora e imagens que o compõem.
- **FR-006**: O cliente responsável DEVE receber no atendimento uma indicação visível de revisão pendente e poder consultar todas as fotos da versão vigente em tamanho ampliado, com identificação dos pontos e opção explícita “Aprovar fotos”. Falha de apresentação de qualquer foto DEVE impedir a aprovação até sua recuperação. Após o servidor confirmar a aprovação acionada pelo cliente, a tela DEVE retornar ao acompanhamento da mesma transação, aguardando o início pelo guincheiro; a consulta posterior das fotos aprovadas permanece disponível.
- **FR-007**: Somente o cliente responsável DEVE poder aprovar a versão vigente aguardando revisão. O “OK” DEVE registrar cliente, data/hora e versão exata aprovada; silêncio, tempo decorrido e ações do guincheiro NÃO DEVEM substituir essa decisão.
- **FR-008**: O cliente DEVE poder solicitar correção da versão vigente com motivo obrigatório não vazio. O guincheiro DEVE visualizar o motivo, ajustar o conjunto e reenviá-lo completo como nova versão, preservando as fotos e decisões das versões anteriores.
- **FR-009**: O envio DEVE fixar as fotos daquela versão. Alterações pelo guincheiro somente DEVEM ocorrer em preparação de uma nova versão após solicitação de correção; conjuntos enviados e aprovados NÃO DEVEM permitir substituição ou exclusão pelos participantes.
- **FR-010**: O sistema DEVE distinguir “Fotos pendentes”, “Aguardando aprovação das fotos”, “Correção solicitada” e “Fotos aprovadas”, exibindo a etapa e a próxima ação ao participante responsável. A chegada DEVE permanecer registrada durante todas essas etapas.
- **FR-011**: O início do transporte e a solicitação de conclusão DEVEM permanecer bloqueados enquanto a vistoria obrigatória não estiver aprovada. A aprovação DEVE liberar o botão “Iniciar transporte” exclusivamente ao guincheiro responsável, mantendo o transporte aguardando início. Somente o registro dessa ação DEVE mudar o atendimento para “Transporte em andamento” e habilitar a solicitação de conclusão, preservando a confirmação bilateral de conclusão. Retomar o atendimento após aprovação DEVE preservar a espera por essa ação, sem iniciar automaticamente nem exigir nova aprovação.
- **FR-012**: Chegada, fotos confirmadas como salvas, versões e decisões DEVEM ser recuperáveis ao retomar o atendimento em qualquer conta participante. Falhas DEVEM apresentar ação para tentar novamente e NÃO DEVEM produzir confirmação de salvamento ou aprovação sem registro efetivo.
- **FR-013**: Repetições e decisões simultâneas DEVEM preservar uma única chegada, envio e decisão válida por versão, recusando decisões sobre versões antigas ou sobre pedidos encerrados.
- **FR-014**: O histórico do serviço DEVE permitir aos participantes consultar as fotos salvas, os pontos, autoria, datas/horários, versões, motivos de correção e aprovação, destacando o conjunto aprovado. Cancelamento ou contestação NÃO DEVE apagar o material já salvo nem produzir um “OK” inexistente.
- **FR-015**: Fotos e decisões DEVEM estar restritas ao cliente e guincheiro do pedido, com consulta excepcional pelo suporte autorizado conforme as permissões e registros de acesso existentes. Outros clientes e prestadores NÃO DEVEM acessá-las.
- **FR-016**: O sistema DEVE tratar recusa de permissão, câmera indisponível, falha de salvamento e falha de carregamento com mensagens compreensíveis e recuperação, sem liberar transporte por falha da vistoria.
- **FR-017**: A obrigatoriedade DEVE aplicar-se aos atendimentos que registrarem chegada após a disponibilização deste fluxo. Atendimentos que já tenham registrado chegada anteriormente DEVEM continuar pelo fluxo anterior; quando não houver vistoria, o histórico DEVE indicar sua ausência.
- **FR-018**: Cancelamento durante a vistoria DEVE seguir as regras existentes, interromper novas capturas/envios/decisões para esse pedido e manter o registro já salvo acessível no histórico. A aprovação das fotos NÃO DEVE concluir o serviço nem alterar pagamento ou reembolso.

### Key Entities *(include if feature involves data)*

- **Atendimento de Guincho**: Pedido existente com cliente, guincheiro, veículo, chegada e estado de execução; indica se a vistoria é obrigatória.
- **Vistoria de retirada**: Registro da condição inicial do veículo vinculado ao atendimento, com etapa vigente e versão atual.
- **Foto da vistoria**: Imagem capturada pelo guincheiro, ponto ou detalhe documentado, descrição opcional, autoria, veículo e data/hora de captura.
- **Versão da vistoria**: Conjunto de fotos enviado em um momento, identificado e preservado para revisão e consulta posterior.
- **Decisão do cliente**: Aprovação ou solicitação de correção de uma versão, com responsável, data/hora e motivo obrigatório quando houver correção.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários com vistoria obrigatória, o botão “Iniciar transporte” só é liberado após quatro pontos completos e aprovação explícita do cliente da versão vigente; o atendimento só passa para “Transporte em andamento” após a ação do guincheiro responsável. Pendência, correção, ausência do cliente e falhas não liberam a execução; aprovação isolada e reabertura do atendimento não iniciam automaticamente o transporte.
- **SC-002**: Em uma avaliação com pelo menos 10 guincheiros e 10 clientes representativos, ao menos 90% de cada grupo concluem sua etapa sem ajuda; com veículo acessível, iluminação adequada e conexão contínua, a captura/envio dos quatro pontos leva até 5 minutos e a revisão/aprovação até 2 minutos, medidos separadamente.
- **SC-003**: Com ambos os participantes no atendimento e conexão contínua, envio, solicitação de correção e aprovação ficam visíveis ao outro participante em até 10 segundos em pelo menos 95% de 20 transições observadas, contando desde a confirmação da ação para quem a realizou.
- **SC-004**: Em 100% dos cenários de consulta após conclusão, cancelamento ou contestação, os participantes recuperam todas as fotos confirmadas como salvas e identificam corretamente versão, veículo, autoria e decisão do cliente; nenhuma versão não aprovada aparece como aprovada.
- **SC-005**: Em 100% dos cenários de reabertura, repetição e decisões simultâneas, a etapa vigente e as fotos já salvas são preservadas, sem duplicar versões ou aprovações nem aceitar versão desatualizada.
- **SC-006**: Em 100% das tentativas avaliadas com contas alheias ao pedido, o acesso às fotos e as decisões são impedidos; participantes também não conseguem alterar o conjunto enviado ou aprovado.

## Assumptions

- Escopo: vistoria fotográfica na retirada para o fluxo de Guincho, revisão pelo cliente e consulta no histórico. Avaliação automática de danos, fotos na entrega, assinatura formal, exportação de laudo e novos processos de disputa ficam fora desta feature.
- Adota-se como conjunto mínimo frente, traseira e duas laterais do veículo. Fotos de rodas, interior, acessórios e avarias podem complementar o registro, sem obrigatoriedade adicional nesta versão.
- O “OK” do cliente é condição para liberar o botão “Iniciar transporte”; o início exige ação posterior do guincheiro responsável, conforme esclarecimento de 13/10/2026. Esta feature substitui, para os pedidos sujeitos à vistoria, a orientação das features 028/029 de que “Cheguei” permitiria início imediato do serviço: a ação registra chegada e inicia a vistoria; movimentação para carregamento/transporte deve aguardar a aprovação.
- O “OK” confirma o conjunto de fotos como registro da condição inicial percebida pelo cliente; não representa confirmação da conclusão nem decisão sobre eventual contestação futura.
- Não há aprovação automática ou dispensa pelo guincheiro. Indisponibilidade do cliente ou impossibilidade de fotografar mantém o atendimento pendente, com acesso ao suporte e ao cancelamento já existentes; não se cria uma nova exceção de suporte para autorizar transporte sem vistoria.
- Dependências: pedido aceito e pago, veículo identificado, autenticação e permissões dos participantes, câmera do guincheiro, salvamento de imagens, acompanhamento do atendimento, histórico e suporte existentes.
- Envio e aprovação exigem conexão. A retomada garante fotos cujo salvamento tenha sido confirmado; não inclui captura integral sem conexão. As fotos seguem o ciclo de conservação do histórico do serviço e suas regras existentes de privacidade, sem prazo novo específico nesta feature.
- As regras de contratação, cobrança, cancelamento, contestação e confirmação final permanecem as do fluxo de Guincho vigente; serviços de outras modalidades não passam a exigir vistoria.
