# Feature Specification: Catálogo de serviços por categoria e mapa PitTec

**Feature Branch**: `026-service-catalog-map-card`

**Created**: 2026-09-29

**Status**: Draft

**Input**: User description: "Refinamento de serviços e categorias de serviços. Quando o provider escolher o serviço que vai prestar, deve existir uma lista de serviços dentro da categoria escolhida. Uma borracharia pode oferecer apenas conserto de pneus ou também venda de rodas, por exemplo. Para cada categoria haverá vários serviços para o provider escolher, com até 15 serviços por categoria. Na tela do PitTec, depois que o pagamento for aprovado e o PitTec estiver indo até o cliente, o card atual fica na frente e atrapalha a visualização do mapa; ajustar isso."

## Clarifications

### Session 2026-09-29

- Q: Um prestador fixo deve escolher serviços de uma única categoria principal ou pode selecionar serviços de várias categorias no mesmo cadastro? → A: Uma categoria principal por prestador; ele pode selecionar vários serviços dentro dela.
- Q: O limite de 15 serviços por categoria deve ser aplicado somente ao catálogo inicial ou também impedir novos cadastros de serviços acima desse limite? → A: A lista inicial terá até 15 serviços por categoria; a gestão poderá adicionar mais serviços depois.
- Q: No estado compacto do acompanhamento PitTec, a ação de cancelar deve continuar visível diretamente ou ficar disponível somente quando o cliente expandir o resumo? → A: A ação de cancelar fica disponível somente quando o resumo for expandido.
- Q: Quem deve poder adicionar, editar ou desativar serviços depois da carga inicial do catálogo? → A: Somente administradores ou equipe autorizada do catálogo.
- Refinamento aprovado após análise: até 15 especialidades selecionáveis na carga inicial, excluindo os serviços implícitos Guincho/PitTec da contagem; especialidades dessas categorias são ofertas de prestadores fixos e seguem o fluxo de serviços fixos.
- Refinamento aprovado após análise: enquadrar marcadores e rota na área livre acima do resumo na abertura ou recentralização; preservar pan/zoom manual nas atualizações.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Prestador escolhe serviços específicos da categoria (Priority: P1)

Como prestador fixo, quero escolher uma categoria e selecionar somente os serviços que realmente
ofereço, para que os clientes encontrem uma oferta precisa e eu não seja apresentado como capaz de
realizar serviços que não presto.

**Why this priority**: A escolha detalhada é a finalidade principal do refinamento do cadastro e
afeta diretamente a qualidade da busca e da comparação de prestadores.

**Independent Test**: Iniciar o cadastro de um prestador fixo, escolher a categoria Borracharia,
selecionar dois serviços e concluir o cadastro; verificar que apenas os dois serviços escolhidos
ficam associados ao prestador.

**Acceptance Scenarios**:

1. **Given** um prestador fixo na etapa de serviços, **When** ele escolhe uma categoria, **Then** a
   tela apresenta somente os serviços ativos daquela categoria para seleção.
2. **Given** uma categoria com vários serviços, **When** o prestador seleciona alguns e confirma,
   **Then** cada seleção aparece como uma oferta separada e pode receber seus próprios dados de
   atendimento, conforme as regras atuais do cadastro.
3. **Given** um prestador que não oferece todos os serviços da categoria, **When** ele conclui o
   cadastro, **Then** os serviços não selecionados não são associados ao prestador nem exibidos
   como ofertas dele.
4. **Given** um prestador que adicionou uma oferta, **When** ele tenta adicionar o mesmo serviço
   novamente, **Then** o sistema impede a duplicação e informa que o serviço já foi adicionado.

### User Story 2 - Catálogo mantém opções úteis por categoria (Priority: P1)

Como responsável pelo catálogo, quero que cada categoria comece com uma lista clara de até 15
serviços específicos, para que o prestador consiga escolher com rapidez e o cliente veja nomes
compreensíveis, mantendo a possibilidade de ampliar o catálogo depois.

**Why this priority**: Sem um catálogo consistente, a nova etapa de seleção não produz uma oferta
confiável.

**Independent Test**: Consultar cada categoria inicial, confirmar que seus serviços estão
organizados, ativos e dentro do limite de 15 opções; abrir uma categoria no cadastro e verificar
que a mesma lista é apresentada.

**Acceptance Scenarios**:

1. **Given** uma categoria ativa, **When** o catálogo é consultado, **Then** seus serviços ativos
   são listados agrupados nessa categoria, sem serviços de outra categoria.
2. **Given** uma categoria incluída na carga inicial, **When** a equipe autorizada adiciona um novo
   serviço posteriormente, **Then** o serviço pode ser incluído após validação de nome, descrição,
   categoria e disponibilidade.
3. **Given** uma categoria sem serviços ativos, **When** o prestador abre a seleção, **Then** a
   tela informa que não há opções disponíveis e não permite concluir uma oferta vazia.

### User Story 3 - Cliente acompanha o PitTec com mapa desobstruído (Priority: P1)

Como cliente, depois de pagar e enquanto o PitTec se desloca até mim, quero enxergar o mapa e o
trajeto com clareza, mantendo apenas o resumo necessário do atendimento disponível para consulta.

**Why this priority**: A visualização do deslocamento é a principal informação desse estado; um
card grande sobre o mapa reduz a confiança e dificulta acompanhar a chegada.

**Independent Test**: Criar uma solicitação PitTec, aprovar o pagamento e abrir o acompanhamento
enquanto o prestador está em `ACCEPTED`; verificar que o mapa permanece visível e que o resumo não
encobre os marcadores nem o trajeto principal.

**Acceptance Scenarios**:

1. **Given** o pagamento aprovado e o PitTec em deslocamento, **When** a tela de acompanhamento é
   exibida, **Then** o mapa ocupa a área principal e o resumo aparece em formato compacto na borda
   inferior, com os marcadores e a rota enquadrados na área livre acima do resumo ao abrir ou recentralizar o mapa.
2. **Given** o resumo compacto visível, **When** o cliente toca nele, **Then** o sistema expande os
   detalhes do atendimento sem sair do mapa e oferece uma ação clara para recolhê-los novamente.
3. **Given** o cliente movimentou ou aproximou o mapa, **When** a posição do PitTec é atualizada,
   **Then** a atualização não força o mapa de volta para uma posição que esconda a visualização
   escolhida pelo cliente.
4. **Given** a solicitação muda para chegada, andamento ou estado terminal, **When** o estado é
   atualizado, **Then** o resumo muda para o texto e as ações correspondentes sem recuperar o
   layout obstrutivo do estado anterior.

### Edge Cases

- Se uma categoria ultrapassar 15 serviços depois da expansão autorizada do catálogo, todos os
  serviços ativos válidos podem ser exibidos, sem remover ofertas já existentes.
- Se um serviço selecionado for desativado ou removido do catálogo antes do envio do cadastro, o
  envio deve ser bloqueado e o prestador deve ser orientado a revisar a seleção.
- Se o catálogo não carregar, o prestador deve receber uma mensagem compreensível e não pode
  concluir o cadastro com uma seleção não validada.
- Se o nome de uma categoria ou serviço for longo, a opção deve continuar identificável sem
  quebrar a ação de seleção.
- Se o mapa não tiver rota pronta ou perder temporariamente a localização, o mapa deve continuar
  utilizável, mostrar o estado aproximado quando possível e manter o resumo compacto.
- Se o cliente tocar em uma ação enquanto o card estiver expandido, a ação deve permanecer acessível
  e o resultado deve ser comunicado sem esconder o mapa de forma permanente.
- No estado compacto do PitTec, o cliente deve ver o status e a informação de deslocamento; a ação
  de cancelar só deve aparecer após a expansão do resumo.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE organizar os serviços por uma única categoria ativa e exibir, na etapa
  de cadastro de prestador fixo, somente os serviços pertencentes à categoria escolhida.
- **FR-002**: O sistema DEVE permitir que o prestador selecione um ou mais serviços da categoria
  escolhida, respeitando as validações atuais de preço, duração e serviço obrigatório.
- **FR-003**: O sistema DEVE impedir que o mesmo serviço seja adicionado mais de uma vez ao mesmo
  cadastro e DEVE permitir remover uma seleção antes do envio.
- **FR-004**: O sistema DEVE persistir somente os serviços selecionados pelo prestador como ofertas
  dele; os demais serviços da categoria não devem ser associados automaticamente.
- **FR-005**: Cada categoria do catálogo inicial DEVE oferecer no máximo 15 especialidades ativas para
  seleção de prestadores fixos, excluindo da contagem os serviços implícitos Guincho e PitTec; a gestão do catálogo PODE adicionar novos serviços posteriormente,
  desde que mantenha nomes, descrições, categoria e estado de disponibilidade válidos.
- **FR-006**: Cada serviço do catálogo DEVE ter nome compreensível, descrição opcional, categoria
  única e estado ativo ou inativo; serviços inativos não podem ser escolhidos em novos cadastros.
- **FR-007**: O sistema DEVE validar novamente categoria e serviços imediatamente antes de concluir
  o cadastro e informar quando uma seleção deixou de estar disponível.
- **FR-008**: O catálogo inicial DEVE conter as listas de serviços definidas nesta especificação
  para Borracharia, Mecânica, Auto elétrica, Funilaria e pintura, Lavagem e estética, Guincho e
  PitTec.
- **FR-009**: As regras existentes para prestadores Móvel e PitTec, que recebem serviço implícito
  e não fazem seleção manual, DEVEM continuar válidas.
  As especialidades das categorias Guincho e PitTec são selecionáveis somente por prestadores fixos
  e aparecem para clientes no fluxo de serviços fixos, sem despacho automático.
- **FR-010**: Somente administradores ou membros da equipe autorizada do catálogo PODEM adicionar,
  editar, ativar ou desativar categorias e serviços após a carga inicial.
- **FR-011**: Após o pagamento aprovado, quando o PitTec estiver em deslocamento no estado
  `ACCEPTED`, a tela DEVE exibir o mapa como conteúdo principal e manter o resumo do atendimento
  compacto e ancorado na borda inferior.
- **FR-012**: O resumo do acompanhamento DEVE mostrar no estado compacto apenas o status e a
  informação essencial de distância ou chegada; a ação de cancelar e os demais detalhes devem
  ficar disponíveis ao expandir.
- **FR-013**: O cliente DEVE conseguir expandir e recolher o resumo sem abandonar o mapa, e o
  estado expandido não pode impedir o acesso às ações nem ocultar permanentemente os marcadores e
  o trajeto.
- **FR-014**: A tela DEVE preservar a posição manual do mapa durante atualizações de localização,
  salvo quando o cliente solicitar explicitamente reenquadrar a rota ou os marcadores.
- **FR-015**: A apresentação do resumo DEVE se adaptar aos estados `ACCEPTED`, `IN_PROGRESS` e
  estados terminais, mantendo ações e mensagens coerentes com cada estado.
- **FR-016**: O mapa e o resumo DEVEM continuar acessíveis com aumento de fonte, conteúdo textual
  equivalente ao status visual e áreas de toque adequadas.

### Catálogo inicial de serviços

Cada lista abaixo contém no máximo 15 opções. Os nomes são sugestões iniciais de catálogo e podem
ser desativados pela gestão do catálogo quando não fizerem sentido para uma região. As listas de
Guincho e PitTec são especialidades selecionáveis somente por prestadores fixos. Os serviços
implícitos únicos Guincho e PitTec são preservados separadamente e excluídos da contagem de 15;
prestadores Móvel e PitTec continuam recebendo apenas seu serviço implícito sem seleção manual.

| Categoria | Serviços iniciais |
|---|---|
| Borracharia | Conserto de pneu furado; Remendo de pneu; Troca de pneu; Calibragem de pneus; Rodízio de pneus; Alinhamento; Balanceamento; Montagem de pneu; Desmontagem de pneu; Venda de pneus; Venda de rodas; Conserto de roda; Troca de válvula; Reparo de pneu run flat; Venda de acessórios para pneus |
| Mecânica | Troca de óleo; Troca de filtro; Revisão preventiva; Diagnóstico do motor; Diagnóstico eletrônico; Troca de bateria; Troca de pastilhas de freio; Troca de discos de freio; Manutenção de suspensão; Troca de amortecedores; Manutenção de embreagem; Troca de correia; Manutenção do sistema de arrefecimento; Manutenção do ar-condicionado; Inspeção pré-viagem |
| Auto elétrica | Diagnóstico elétrico; Instalação de bateria; Recarga de bateria; Troca de alternador; Reparo de alternador; Reparo de motor de partida; Instalação de faróis; Reparo de iluminação; Instalação de alarme; Instalação de acessórios elétricos; Instalação de câmera veicular; Instalação de som automotivo; Reparo de vidros elétricos; Reparo de travas elétricas; Rastreamento de falha elétrica |
| Funilaria e pintura | Reparo de amassado; Martelinho de ouro; Pintura de peça; Pintura parcial; Pintura completa; Polimento técnico; Recuperação de para-choque; Troca de para-choque; Reparo de capô; Reparo de porta; Reparo de paralama; Reparo de teto; Restauração de roda; Aplicação de retoque; Avaliação de colisão |
| Lavagem e estética | Lavagem simples; Lavagem completa; Lavagem técnica; Higienização interna; Higienização de ar-condicionado; Limpeza de bancos; Limpeza de estofados; Polimento de pintura; Enceramento; Cristalização de pintura; Vitrificação de pintura; Revitalização de plásticos; Limpeza de motor; Descontaminação de pintura; Remoção de chuva ácida |
| Guincho | Guincho urbano; Reboque de carro; Reboque de moto; Transporte de veículo; Auxílio para pane mecânica; Auxílio para pane elétrica; Resgate em garagem; Resgate em estacionamento; Remoção após colisão; Transporte para oficina; Transporte para concessionária; Troca de pneu emergencial; Recarga emergencial de bateria; Chaveiro automotivo emergencial; Apoio em estrada |
| PitTec | Diagnóstico automotivo no local; Avaliação de pane; Inspeção antes da compra; Inspeção pré-viagem; Orientação para manutenção; Leitura de falhas; Avaliação de bateria; Avaliação de pneus; Avaliação de freios; Avaliação de suspensão; Avaliação de sistema elétrico; Avaliação de superaquecimento; Avaliação de ruído; Avaliação após colisão; Encaminhamento para oficina |

### Key Entities *(include if feature involves data)*

- **Categoria de serviço**: agrupamento ativo do catálogo, com nome, descrição e limite de opções
  disponíveis.
- **Serviço de catálogo**: oferta nomeada pertencente a uma categoria, com descrição e estado de
  disponibilidade.
- **Oferta do prestador**: vínculo entre prestador fixo e serviço selecionado, incluindo os dados
  de preço e duração já exigidos pelo cadastro.
- **Acompanhamento PitTec**: estado apresentado ao cliente durante a solicitação, com status,
  marcadores, rota, distância e ações permitidas.
- **Resumo de acompanhamento**: painel compacto ou expandido que apresenta as informações e ações
  do atendimento sem substituir o mapa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cadastros de prestadores fixos, a lista apresentada após escolher uma
  categoria contém somente serviços daquela categoria.
- **SC-002**: 100% das categorias incluídas no catálogo inicial têm no máximo 15 especialidades ativas
  selecionáveis por prestadores fixos, excluindo os serviços implícitos; expansões posteriores do catálogo podem ultrapassar esse número mediante gestão
  autorizada.
- **SC-003**: Em 100% dos cadastros concluídos, o prestador fica associado somente aos serviços
  que confirmou, sem duplicações ou associações automáticas indevidas.
- **SC-004**: Pelo menos 90% dos prestadores de teste conseguem escolher e confirmar seus serviços
  sem retornar à etapa de categoria para corrigir uma seleção inválida.
- **SC-005**: Em 100% dos acompanhamentos PitTec no estado `ACCEPTED`, o cliente consegue ver os
  dois marcadores e a rota ou estimativa na área útil livre acima do resumo compacto ao abrir ou
  recentralizar o mapa, quando as localizações estiverem disponíveis. Após pan/zoom manual,
  prevalece a posição escolhida pelo cliente, mesmo que marcadores saiam da área visível.
- **SC-006**: Em um teste com 20 atualizações de localização, a posição manual do mapa permanece
  preservada até que o cliente solicite reenquadramento.
- **SC-007**: O cliente encontra status e distância do acompanhamento em até uma interação a partir
  da tela do mapa, e encontra a ação de cancelar em até uma interação adicional após expandir o
  resumo.

## Assumptions

- A funcionalidade de seleção detalhada se aplica ao cadastro de prestadores Fixos; as regras
  existentes de serviço implícito para Móvel e PitTec permanecem fora da seleção manual.
- O limite de 15 vale para a carga inicial de cada categoria; expansões posteriores podem superar
  esse número e o catálogo pode manter serviços inativos para histórico e compatibilidade.
- Preço, duração, serviço gratuito e demais dados da oferta continuam seguindo as validações já
  existentes do cadastro de prestador.
- As categorias listadas são o catálogo inicial esperado; novas categorias podem ser adicionadas
  posteriormente, inclusive com mais de 15 serviços ativos quando houver necessidade de catálogo.
- A gestão posterior do catálogo é restrita a administradores ou equipe autorizada; prestadores e
  clientes não publicam alterações diretamente.
- O mapa continua sendo a área principal do acompanhamento depois do pagamento, e o resumo pode
  ser expandido quando o cliente precisar consultar detalhes ou executar uma ação.
- A rota pode ser real ou estimada conforme a disponibilidade de localização, sem bloquear o
  acompanhamento quando o serviço de rotas estiver temporariamente indisponível.
- A tela deve reutilizar os estados de atendimento já definidos para PitTec, incluindo pagamento
  aprovado, deslocamento, chegada, andamento, conclusão e cancelamento.
