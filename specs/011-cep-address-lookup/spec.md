# Feature Specification: Busca de endereço por CEP

**Feature Branch**: `[011-cep-address-lookup]`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "Implementar busca de CEP para preencher automaticamente os campos de endereço durante o cadastro; a pessoa informa o CEP com 8 dígitos e preenche manualmente apenas o número da residência. Quando houver erro em qualquer campo obrigatório, marcar o campo e informar o motivo do problema."

## Clarifications

### Session 2026-08-23

- Q: Quando a consulta do CEP deve ser iniciada após os oito dígitos serem informados? → A: Por uma ação explícita `Buscar CEP`, habilitada somente quando o CEP possuir oito dígitos.
- Q: Em quais cadastros a busca de CEP deve ser entregue nesta feature? → A: Apenas no cadastro de empresa nesta entrega.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Preencher endereço pelo CEP (Priority: P1)

Uma pessoa que está preenchendo o cadastro de empresa informa um CEP brasileiro com oito dígitos, solicita a consulta e recebe rua, complemento, bairro, cidade e estado preenchidos automaticamente. Depois, informa manualmente o número da residência e continua o cadastro.

**Why this priority**: É o valor principal da feature: reduzir digitação, erros e abandono durante o preenchimento do endereço.

**Independent Test**: Pode ser testada informando o CEP `01001000`, consultando-o e verificando o preenchimento de Praça da Sé, lado ímpar, Sé, São Paulo e SP, enquanto o número permanece vazio e editável.

**Acceptance Scenarios**:

1. **Given** um formulário de cadastro com endereço vazio, **When** a pessoa informa `01001000` e aciona `Buscar CEP`, **Then** o sistema preenche rua com `Praça da Sé`, complemento com `lado ímpar`, bairro com `Sé`, cidade com `São Paulo` e estado com `SP`.
2. **Given** uma consulta concluída com sucesso, **When** os campos de endereço são exibidos, **Then** o número da residência permanece vazio, obrigatório e disponível para preenchimento manual.
3. **Given** uma consulta concluída com sucesso, **When** a pessoa corrige algum campo retornado, **Then** o valor corrigido é mantido para a conclusão do cadastro.

---

### User Story 2 - Identificar campos obrigatórios com erro (Priority: P2)

Uma pessoa que tenta concluir o cadastro com um ou mais campos obrigatórios ausentes ou inválidos identifica imediatamente cada campo que precisa de correção e entende o motivo específico do problema.

**Why this priority**: Sem indicação associada ao campo, a pessoa não sabe o que corrigir e pode acreditar que o botão de cadastro não funciona.

**Independent Test**: Pode ser testada deixando número e cidade vazios e informando um CEP incompleto; ao tentar concluir, os três campos devem ser marcados simultaneamente, cada um com sua mensagem correspondente, e o primeiro erro deve ficar visível.

**Acceptance Scenarios**:

1. **Given** um cadastro com campos obrigatórios vazios, **When** a pessoa tenta concluir, **Then** todos os campos obrigatórios ausentes são marcados e cada um informa que o preenchimento é obrigatório.
2. **Given** um campo preenchido em formato inválido, **When** a pessoa tenta concluir, **Then** o próprio campo é marcado e exibe o motivo específico, incluindo a regra esperada quando aplicável.
3. **Given** múltiplos campos inválidos distribuídos pelo formulário, **When** a validação termina, **Then** todos os erros são apresentados e o primeiro campo inválido na ordem visual é levado para uma posição visível e recebe foco.
4. **Given** um campo marcado com erro, **When** a pessoa corrige seu valor, **Then** o erro desse campo é removido quando o valor se torna válido sem apagar dados ou erros de outros campos.

---

### User Story 3 - Corrigir CEP inválido ou inexistente (Priority: P3)

Uma pessoa que informa um CEP incompleto, malformado ou inexistente recebe orientação clara para corrigir o dado sem perder os demais campos independentes do endereço.

**Why this priority**: Impede consultas inválidas e evita que uma falha previsível deixe o formulário em estado confuso.

**Independent Test**: Pode ser testada informando menos de oito dígitos e, depois, um CEP inexistente, confirmando que a consulta inválida não preenche endereço e que a mensagem permite correção imediata.

**Acceptance Scenarios**:

1. **Given** um CEP com menos ou mais de oito dígitos, **When** a pessoa tenta consultar, **Then** a consulta não é iniciada e o campo informa que são necessários exatamente oito dígitos.
2. **Given** um CEP com oito dígitos que não existe, **When** a consulta termina, **Then** o sistema informa que o CEP não foi encontrado e mantém o campo de CEP disponível para correção.
3. **Given** um endereço preenchido por uma consulta anterior, **When** a pessoa altera o CEP, **Then** os campos derivados da consulta anterior são invalidados para impedir o envio de um endereço incompatível, mas o número digitado manualmente é preservado.

---

### User Story 4 - Continuar após falha temporária (Priority: P4)

Uma pessoa que está sem conexão ou encontra indisponibilidade temporária recebe uma mensagem acionável e pode tentar novamente ou preencher o endereço manualmente.

**Why this priority**: A dependência externa não deve transformar uma conveniência de preenchimento em bloqueio do cadastro.

**Independent Test**: Pode ser testada simulando ausência de rede, confirmando mensagem e nova tentativa, e verificando que os campos continuam editáveis segundo as validações já existentes.

**Acceptance Scenarios**:

1. **Given** ausência de conexão, **When** a pessoa consulta um CEP válido, **Then** o sistema informa que não foi possível consultar e oferece nova tentativa.
2. **Given** indisponibilidade ou demora excessiva do serviço de CEP, **When** a consulta falha, **Then** o sistema encerra o estado de carregamento, preserva o CEP e permite tentar novamente.
3. **Given** uma falha de consulta, **When** a pessoa prefere continuar, **Then** os campos de endereço permanecem editáveis e o cadastro segue sujeito às validações de endereço já existentes.

### Edge Cases

- O campo aceita somente os oito dígitos significativos; colagem de CEP com máscara ou espaços é normalizada antes da validação.
- Uma resposta válida pode omitir rua, complemento ou bairro para CEPs genéricos; apenas os valores presentes são preenchidos e os campos ausentes permanecem editáveis.
- Alterar o CEP durante uma consulta invalida a resposta antiga; somente a resposta correspondente ao CEP atual pode preencher o formulário.
- Toques repetidos durante a mesma consulta não iniciam solicitações concorrentes.
- Uma resposta malformada, incompleta ou incompatível não pode substituir o endereço atual silenciosamente.
- O campo complemento pode receber valor automático e continuar opcional/editável.
- O número da residência nunca é inferido a partir da consulta e não é apagado quando o CEP muda.
- O serviço pode responder que o CEP não existe mesmo com sucesso de comunicação; esse caso é tratado como “CEP não encontrado”, não como falha de conexão.
- Quando vários campos possuem erro, todos são marcados na mesma validação; a pessoa não precisa corrigir e reenviar um campo por vez para descobrir o próximo problema.
- Campos inválidos fora da área visível são alcançados automaticamente sem impedir que a pessoa navegue livremente pelo restante do formulário.
- Mensagens extensas ou fonte ampliada não podem encobrir o campo, a mensagem nem a próxima ação disponível.
- Uma falha geral do cadastro que não possa ser atribuída com segurança a um campo é apresentada separadamente e não marca arbitrariamente um campo correto.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST disponibilizar a consulta de CEP no cadastro de empresa nesta entrega.
- **FR-002**: O campo de CEP MUST aceitar e manter exatamente oito dígitos significativos, normalizando máscara, espaços e caracteres de formatação colados pela pessoa.
- **FR-003**: O sistema MUST apresentar uma ação explícita `Buscar CEP`, habilitada somente quando o CEP normalizado possuir exatamente oito dígitos; a consulta MUST NOT ser iniciada automaticamente apenas pela digitação.
- **FR-004**: Durante a consulta, o sistema MUST apresentar progresso, impedir consultas duplicadas e manter o restante do formulário em estado consistente.
- **FR-005**: Em uma consulta bem-sucedida, o sistema MUST mapear logradouro para rua, complemento para complemento, bairro para bairro, localidade para cidade e UF para estado.
- **FR-006**: O sistema MUST manter o CEP normalizado no formulário após uma consulta bem-sucedida.
- **FR-007**: O número da residência MUST permanecer como entrada manual, obrigatória e independente do resultado da consulta.
- **FR-008**: Os campos preenchidos automaticamente MUST permanecer editáveis para correções ou complementação de resultados ausentes.
- **FR-009**: Ao alterar o CEP depois de uma consulta bem-sucedida, o sistema MUST invalidar os campos derivados da resposta anterior e preservar o número informado manualmente.
- **FR-010**: O sistema MUST descartar respostas tardias que não correspondam mais ao CEP atualmente exibido.
- **FR-011**: Quando o CEP não existir, o sistema MUST informar “CEP não encontrado”, não preencher campos com dados antigos e permitir correção imediata.
- **FR-012**: O sistema MUST distinguir CEP inválido, ausência de conexão, tempo esgotado, indisponibilidade do serviço e resposta inesperada, exibindo mensagens compreensíveis sem detalhes técnicos.
- **FR-013**: Após uma falha temporária, a pessoa MUST poder tentar novamente sem redigitar o CEP.
- **FR-014**: A falha do serviço de consulta MUST NOT impedir o preenchimento manual e a conclusão do cadastro quando os campos atenderem às validações de endereço existentes.
- **FR-015**: Campos não utilizados do resultado, como unidade, região, códigos administrativos e DDD, MUST NOT alterar outros dados do cadastro.
- **FR-016**: A consulta MUST transmitir somente o CEP necessário; credenciais, sessão, documentos e demais dados pessoais do cadastro MUST NOT ser enviados ao serviço de CEP.
- **FR-017**: A integração MUST usar o serviço ViaCEP como fonte dos dados de endereço e tratar sua indicação explícita de CEP inexistente.
- **FR-018**: O comportamento MUST ser integrado ao estado do formulário, preservando os dados digitados durante rotação, recomposição e retorno do carregamento enquanto o cadastro permanecer aberto.
- **FR-019**: A ação de consulta, o progresso e as mensagens de erro MUST ser acessíveis, possuir rótulos compreensíveis e não depender apenas de cor.
- **FR-020**: Esta feature MUST NOT criar ou alterar registros persistidos apenas por consultar um CEP; o endereço é salvo somente quando o fluxo de cadastro existente for concluído.
- **FR-021**: Antes de concluir o cadastro, o sistema MUST validar todos os campos obrigatórios e todos os formatos conhecidos, apresentando na mesma tentativa o conjunto completo de erros encontrados.
- **FR-022**: Cada campo obrigatório ausente ou inválido MUST ser marcado visualmente no próprio controle e MUST exibir, junto ao campo, uma mensagem que explique o motivo do problema.
- **FR-023**: Mensagens de erro de campo MUST ser específicas e acionáveis, distinguindo pelo menos ausência de valor, formato inválido, tamanho inválido e seleção obrigatória quando essas regras se aplicarem.
- **FR-024**: Quando houver múltiplos erros, o sistema MUST manter todos visíveis no estado do formulário e direcionar a pessoa ao primeiro campo inválido segundo a ordem visual.
- **FR-025**: Ao corrigir um campo, o sistema MUST reavaliar e remover somente o erro relacionado quando a entrada se tornar válida, preservando os demais valores e erros ainda existentes.
- **FR-026**: A marcação de erro MUST NOT depender apenas de cor e MUST associar semanticamente o motivo ao campo correspondente para tecnologias assistivas.
- **FR-027**: Erros remotos que possam ser atribuídos com segurança a um campo MUST ser apresentados nesse campo; falhas gerais ou desconhecidas MUST ser exibidas como mensagem do formulário sem atribuição arbitrária.
- **FR-028**: Enquanto existirem erros obrigatórios conhecidos, o cadastro MUST NOT ser enviado ou considerado concluído.

### Key Entities

- **CEP consultado**: valor brasileiro normalizado com oito dígitos, associado ao identificador da tentativa de consulta e ao seu estado atual.
- **Resultado de endereço**: conjunto opcional de CEP formatado, rua, complemento, bairro, cidade e estado obtido para o CEP atual.
- **Estado da consulta**: estado exclusivo entre não consultado, consultando, encontrado, não encontrado e falha recuperável.
- **Endereço do cadastro**: dados editáveis usados pelo fluxo existente; recebe os valores encontrados, preserva o número manual e só é persistido ao concluir o cadastro.
- **Erro de campo**: motivo tipado associado a um campo específico, incluindo ausência, formato, tamanho, seleção ou indisponibilidade conhecida; permanece até a entrada ser corrigida ou revalidada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em condições normais de conexão, 95% das consultas válidas apresentam o resultado ou uma mensagem conclusiva em até 3 segundos.
- **SC-002**: Para o CEP de referência `01001000`, 100% dos cinco campos mapeados exibem os valores esperados antes de a pessoa informar o número.
- **SC-003**: Uma pessoa consegue completar a parte de endereço informando apenas CEP e número quando o resultado contém todos os demais campos necessários.
- **SC-004**: Em 100% dos testes com CEP inválido, inexistente, resposta tardia ou falha de rede, dados de outro CEP não são aplicados ao formulário.
- **SC-005**: Após falha temporária, a nova tentativa pode ser iniciada com uma única ação e sem redigitar o CEP.
- **SC-006**: Em 100% dos testes de consulta, nenhum dado do cadastro além do CEP é transmitido ao serviço externo.
- **SC-007**: A pessoa consegue concluir o endereço manualmente quando a consulta está indisponível, sem ficar presa em carregamento ou bloqueio permanente.
- **SC-008**: Em 100% dos testes com campos obrigatórios ausentes ou inválidos, cada campo problemático é marcado e apresenta um motivo compreensível na mesma tentativa de conclusão.
- **SC-009**: Em formulários com múltiplos erros, o primeiro campo inválido fica visível após uma única tentativa e todos os demais erros permanecem disponíveis para correção.
- **SC-010**: Em testes com leitor de tela, 100% das mensagens de erro de campo são anunciadas em associação com o respectivo campo e podem ser compreendidas sem depender da cor.

## Assumptions

- A integração desta entrega ocorre exclusivamente no endereço do cadastro de empresa; eventual reutilização em outros fluxos dependerá de uma feature futura.
- A consulta é uma assistência de preenchimento, não uma validação definitiva de residência nem prova de posse do endereço.
- Rua, complemento, bairro, cidade e estado podem ser editados após o preenchimento automático porque alguns CEPs possuem dados incompletos ou genéricos.
- País continua definido como Brasil pelo fluxo de cadastro existente e não é alterado pela consulta.
- Número permanece obrigatório conforme o cadastro atual; complemento permanece opcional.
- A persistência, geocodificação e validação final do endereço continuam pertencendo ao fluxo de cadastro existente e estão fora da responsabilidade da consulta de CEP.
- O serviço ViaCEP é uma dependência externa sem credencial administrativa e pode apresentar indisponibilidade, demora ou resposta de CEP inexistente.

## Out of Scope

- Pesquisa de endereço por rua, cidade ou estado.
- Sugestões automáticas enquanto cada dígito é digitado.
- Geocodificação de latitude e longitude.
- Validação de que a pessoa reside ou opera no endereço encontrado.
- Persistência isolada do resultado antes da conclusão do cadastro.
- Uso dos campos unidade, estado por extenso, região, IBGE, GIA, DDD ou SIAFI em outras partes do aplicativo.
- Suporte a códigos postais de outros países.
- Integração da consulta de CEP em cadastro de cliente ou em outros formulários além do cadastro de empresa.
