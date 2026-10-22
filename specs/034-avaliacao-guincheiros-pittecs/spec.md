# Feature Specification: Avaliação de guincheiros e PitTecs

**Feature Branch**: `feature/avaliacao-guincheiro`

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "Criar avaliacao dos guincheiros e pittecs. Sera por estrelas (1, 2, 3, 4 e 5) e uma descricao falando do atendimento. Nao sera obrigatorio, sera opcional."

## Clarifications

### Session 2026-10-22

- Q: O cliente poderá editar uma avaliação depois de enviá-la? → A: Não. Depois do envio, a avaliação é definitiva e não pode ser editada.

## User Scenarios & Testing

### User Story 1 - Avaliar o atendimento concluído (Priority: P1)

Após um atendimento de guincho ou PitTec ser concluído, o cliente pode registrar uma nota de uma a cinco estrelas e, se quiser, escrever uma descrição sobre o atendimento. A avaliação é vinculada ao atendimento e ao prestador responsável.

**Why this priority**: Permite ao cliente dar retorno sobre as duas modalidades de atendimento incluídas no pedido.

**Independent Test**: Concluir um atendimento de cada modalidade, enviar diferentes notas e comentários e conferir que cada avaliação fica associada ao atendimento e prestador corretos.

**Acceptance Scenarios**:

1. **Given** um atendimento concluído sem avaliação, **When** o cliente seleciona de uma a cinco estrelas e confirma, **Then** a nota é registrada para aquele atendimento e prestador.
2. **Given** um atendimento concluído sem avaliação, **When** o cliente seleciona uma nota e deixa a descrição vazia, **Then** a avaliação é registrada sem descrição.
3. **Given** um atendimento concluído, **When** o cliente tenta registrar uma nota fora da faixa de uma a cinco, **Then** o sistema recusa a avaliação e informa que é necessário escolher uma das cinco notas.
4. **Given** um atendimento de guincho concluído e um atendimento PitTec concluído, **When** o cliente avalia ambos, **Then** cada avaliação pertence somente ao respectivo atendimento e prestador.
5. **Given** um prestador com avaliações, **When** outro cliente consulta seus dados no aplicativo, **Then** pode ver a média e a quantidade de avaliações, mas não as descrições.
6. **Given** um cliente, o prestador avaliado ou o suporte, **When** consulta uma avaliação que tem permissão para ver, **Then** a descrição é exibida sem expor o comentário publicamente aos demais clientes.

---

### User Story 2 - Continuar sem avaliar e retornar depois (Priority: P1)

O cliente pode dispensar a opção de avaliação e continuar usando o aplicativo. Mais tarde, pode encontrar o atendimento concluído no histórico e registrar a avaliação pendente.

**Why this priority**: A avaliação não pode impedir o uso do serviço ou obrigar o cliente a fornecer feedback.

**Independent Test**: Concluir um atendimento, dispensar a avaliação, sair do acompanhamento, abrir o atendimento concluído pelo histórico e registrar a nota.

**Acceptance Scenarios**:

1. **Given** a opção de avaliar um atendimento concluído, **When** o cliente a dispensa, **Then** o atendimento continua concluído e o cliente pode continuar para fora da tela.
2. **Given** um atendimento concluído ainda sem avaliação, **When** o cliente o abre pelo histórico, **Then** a opção de avaliar continua disponível.
3. **Given** um atendimento concluído já avaliado, **When** o cliente o abre pelo histórico, **Then** a avaliação registrada é indicada, não pode ser editada e não oferece uma segunda avaliação.

### Edge Cases

- Um atendimento cancelado, contestado ou ainda em andamento não pode receber uma avaliação de atendimento concluído.
- A tentativa de registrar uma segunda avaliação para o mesmo atendimento não pode duplicar a nota nem o comentário.
- Depois de enviada, uma avaliação é definitiva e não pode ser editada nem substituída.
- Se o envio falhar ou não houver conexão, a avaliação não pode ser indicada como salva; o cliente deve poder tentar novamente.
- Fechar ou deixar a tela da avaliação sem enviar deve preservar o status concluído do atendimento e não criar uma avaliação parcial.
- O comentário não pode superar o limite aceito pela funcionalidade e deve ser tratado como texto fornecido pelo usuário.

## Requirements

### Functional Requirements

- **FR-001**: Após a conclusão de um atendimento de guincho ou PitTec, o sistema MUST oferecer ao cliente a opção de avaliar o prestador responsável.
- **FR-002**: A avaliação MUST exigir uma nota inteira de uma a cinco estrelas; não pode haver nota inicial selecionada.
- **FR-003**: A avaliação MUST permitir uma descrição opcional sobre o atendimento, limitada a 500 caracteres.
- **FR-004**: O sistema MUST permitir que o cliente dispense a avaliação sem atrasar ou reverter a conclusão do atendimento.
- **FR-005**: Se dispensar a avaliação, o cliente MUST poder avaliar mais tarde ao abrir o atendimento concluído pelo histórico.
- **FR-006**: Cada atendimento concluído MUST aceitar no máximo uma avaliação do cliente, vinculada ao próprio atendimento e ao prestador associado.
- **FR-007**: O sistema MUST apresentar o estado pendente, envio em andamento, sucesso ou falha de envio; uma falha MUST permitir uma nova tentativa sem duplicar uma avaliação já salva.
- **FR-008**: Atendimentos em andamento, cancelados ou contestados MUST NOT aceitar avaliações de conclusão.
- **FR-009**: O histórico MUST distinguir atendimentos concluídos avaliados daqueles que ainda podem receber uma avaliação.
- **FR-010**: A disponibilidade e o registro de avaliações MUST ser iguais para atendimentos de guincho e PitTec.
- **FR-011**: Outros clientes MUST poder ver a média e a quantidade de avaliações do prestador ao consultá-lo no aplicativo; sem avaliações, o sistema MUST indicar que ainda não há notas.
- **FR-012**: A descrição MUST ficar visível somente ao cliente que a enviou, ao prestador avaliado e ao suporte; outros clientes MUST NOT ver a descrição nem dados que identifiquem quem avaliou.
- **FR-013**: Depois do envio confirmado, uma avaliação MUST ser definitiva; o cliente MUST NOT poder editá-la, apagá-la ou substituí-la por outra avaliação para o mesmo atendimento.
- **FR-014**: O perfil do guincheiro MUST exibir a média e a quantidade de avaliações recebidas e um botão para consultar seus comentários.
- **FR-015**: A consulta de comentários do prestador MUST listar somente suas próprias avaliações, com nota, data e descrição opcional, sem permitir leitura das avaliações privadas de outro prestador.

### Key Entities

- **Avaliação de atendimento**: Nota de uma a cinco estrelas e descrição opcional, associadas a um único atendimento concluído e seu prestador.
- **Atendimento**: Serviço de guincho ou PitTec solicitado pelo cliente, com estado de conclusão que autoriza a avaliação.
- **Prestador**: Guincheiro ou PitTec responsável pelo atendimento avaliado.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Um cliente consegue escolher e enviar uma nota, com ou sem descrição, em até dois minutos após abrir a avaliação.
- **SC-002**: Em todos os cenários de dispensa, o cliente consegue sair da avaliação e continuar no aplicativo sem alterar o status do atendimento concluído.
- **SC-003**: Em testes das duas modalidades, 100% das avaliações salvas ficam associadas ao atendimento e prestador corretos.
- **SC-004**: Em todos os atendimentos concluídos já avaliados, novas tentativas não criam avaliações duplicadas.
- **SC-005**: Uma falha temporária de envio informa ao cliente que não houve confirmação e preserva a possibilidade de tentar novamente.
- **SC-006**: Ao consultar cada prestador com avaliações, 100% das médias e quantidades apresentadas correspondem às avaliações registradas, sem expor as descrições a outros clientes.

## Assumptions

- Uma avaliação contém uma única nota geral, sem categorias separadas para pontualidade, comunicação ou condição do veículo.
- A descrição não é obrigatória e pode ser omitida completamente.
- A avaliação pode ser feita logo após a conclusão ou posteriormente pelo histórico; dispensá-la não expira o atendimento nem altera sua conclusão.
- Uma avaliação não está disponível para atendimentos cancelados ou contestados.
- Uma avaliação enviada é definitiva; o histórico apenas informa que ela já foi registrada.
- Outros clientes veem somente a média e a quantidade de avaliações no aplicativo; a descrição fica restrita ao autor, prestador avaliado e suporte.
- A média é calculada a partir das avaliações registradas e, na ausência delas, o prestador aparece sem nota.
