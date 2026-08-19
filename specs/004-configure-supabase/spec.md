# Feature Specification: Configurar Supabase

**Feature Branch**: `004-configure-supabase`

**Created**: 2026-08-19

**Status**: Draft

**Input**: User description: "Vamos configurar o Supabase no Android. Vou passar o supabaseUrl e o supabaseKey."

## Clarifications

### Session 2026-08-19

- Q: Quando `supabaseUrl` ou `supabaseKey` estiver ausente ou inválido, em que momento a configuração deve falhar? → A: Interromper qualquer build com configuração ausente ou inválida.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Disponibilizar a conexão configurada (Priority: P1)

Como equipe de desenvolvimento, queremos fornecer o endereço do projeto e uma chave segura para
cliente para que o aplicativo fique preparado para consumir os serviços remotos nas próximas
features.

**Why this priority**: Sem uma configuração válida e centralizada, nenhuma integração posterior
com o serviço remoto pode ser iniciada de forma previsível.

**Independent Test**: Pode ser testado fornecendo os dois valores esperados, preparando e abrindo
o aplicativo e confirmando que a configuração fica disponível para consumo interno sem alterar os
fluxos existentes.

**Acceptance Scenarios**:

1. **Given** que foram fornecidos um endereço válido e uma chave permitida para cliente, **When** o
   aplicativo é preparado e iniciado, **Then** a integração fica configurada e pronta para uso.
2. **Given** que a integração foi configurada, **When** diferentes áreas internas precisarem
   acessá-la, **Then** recebem a mesma configuração central, sem duplicar endereço ou chave.
3. **Given** que a configuração é válida, **When** o usuário percorre os fluxos já existentes,
   **Then** não ocorre mudança visível ou regressão causada apenas pela configuração do serviço.

---

### User Story 2 - Detectar configuração ausente ou inválida (Priority: P2)

Como pessoa desenvolvedora, quero receber uma indicação clara quando algum valor obrigatório
estiver ausente ou inválido para corrigir o ambiente antes de integrar uma funcionalidade de
negócio.

**Why this priority**: Falhas explícitas evitam erros tardios, diagnósticos ambíguos e tentativas de
conexão com parâmetros incorretos.

**Independent Test**: Pode ser testado omitindo cada valor, usando endereço malformado e usando
uma chave administrativa conhecida, confirmando que cada caso é recusado com diagnóstico
acionável e sem exposição de credenciais.

**Acceptance Scenarios**:

1. **Given** que o endereço ou a chave não foi fornecido, **When** qualquer variante do aplicativo
   é preparada, **Then** o build é interrompido com indicação do campo ausente.
2. **Given** que o endereço não representa uma conexão HTTPS válida, **When** a configuração é
   validada, **Then** o valor é recusado antes de qualquer uso remoto.
3. **Given** que a chave corresponde a um padrão administrativo ou secreto conhecido, **When** a
   configuração é validada, **Then** ela é recusada e não é incorporada ao aplicativo.

---

### User Story 3 - Preservar credenciais e ambientes (Priority: P3)

Como equipe responsável pelo aplicativo, queremos fornecer os valores por ambiente sem gravá-los
no histórico do projeto para reduzir exposição acidental e permitir configurações distintas entre
desenvolvimento e automação.

**Why this priority**: Mesmo uma chave permitida para cliente deve ser tratada com disciplina para
evitar cópias desnecessárias, enquanto credenciais administrativas jamais podem chegar ao app.

**Independent Test**: Pode ser testado configurando dois ambientes com valores distintos e
inspecionando arquivos rastreados, artefatos de diagnóstico e mensagens de erro para confirmar que
nenhuma chave completa foi registrada ou versionada.

**Acceptance Scenarios**:

1. **Given** que há ambientes locais e automatizados, **When** cada ambiente fornece seus próprios
   valores, **Then** ambos usam o mesmo contrato de configuração sem editar arquivos rastreados.
2. **Given** que ocorre uma falha de configuração, **When** o diagnóstico é apresentado, **Then** a
   mensagem identifica o problema sem exibir a chave completa.
3. **Given** que o repositório é inspecionado após a configuração, **When** arquivos rastreados são
   pesquisados, **Then** não há credencial real gravada no código, nos recursos ou na
   documentação da feature.

### Edge Cases

- Valores contendo apenas espaços são tratados como ausentes.
- Espaços acidentais antes ou depois dos valores não alteram uma configuração válida.
- Endereços sem esquema, com esquema diferente de HTTPS ou sem host são recusados.
- Chaves públicas legadas para cliente e chaves publicáveis atuais podem ser aceitas, desde que
  sejam destinadas ao uso no aplicativo.
- Chaves identificadas como `service_role`, secretas ou administrativas são recusadas mesmo que o
  endereço seja válido.
- Mensagens de erro e diagnósticos não incluem a chave completa nem dados administrativos.
- Indisponibilidade de internet não transforma uma configuração local válida em inválida; a
  comunicação remota será tratada pelas features que efetivamente a utilizarem.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST aceitar exatamente dois valores obrigatórios para configurar o
  serviço remoto: `supabaseUrl` e `supabaseKey`.
- **FR-002**: A configuração MUST ser fornecida externamente aos arquivos rastreados do projeto,
  usando o mesmo contrato de nomes em ambiente local e em automação.
- **FR-003**: `supabaseUrl` MUST ser não vazio, representar um endereço HTTPS absoluto e possuir
  host válido.
- **FR-004**: `supabaseKey` MUST ser não vazia e apropriada para distribuição em um aplicativo
  cliente, como chave publicável ou chave pública/anon legada.
- **FR-005**: O sistema MUST rejeitar padrões conhecidos de chave secreta, administrativa ou
  `service_role` e MUST NOT incorporá-los ao aplicativo.
- **FR-006**: Valores válidos MUST ser normalizados quanto a espaços externos antes do uso, sem
  modificar seu conteúdo interno.
- **FR-007**: O aplicativo MUST disponibilizar uma única configuração compartilhada para futuras
  integrações, evitando cópias divergentes do endereço e da chave.
- **FR-008**: A ausência ou invalidade de qualquer valor MUST interromper o build de todas as
  variantes, inclusive desenvolvimento e produção, com uma falha explícita e acionável.
- **FR-009**: Mensagens de erro, diagnósticos e logs MUST NOT revelar a chave completa nem qualquer
  credencial administrativa.
- **FR-010**: Endereço e chave reais MUST NOT ser adicionados ao código, recursos, documentação ou
  outros arquivos versionados.
- **FR-011**: A configuração MUST funcionar tanto para preparação local do aplicativo quanto para
  preparação automatizada, sem exigir mudanças no código entre ambientes.
- **FR-012**: Esta feature MUST NOT autenticar usuários, consultar ou alterar registros, enviar
  arquivos, assinar atualizações em tempo real ou criar regras de acesso ao backend.
- **FR-013**: Configurar o serviço MUST NOT solicitar permissões sensíveis ao usuário nem alterar a
  experiência visual dos fluxos existentes.
- **FR-014**: A integração configurada MUST ficar pronta para ser reutilizada pelas futuras
  funcionalidades de acesso a dados, sem que elas precisem conhecer a origem dos valores de
  configuração.

### Key Entities

- **Configuração do serviço remoto**: Conjunto formado pelo endereço do projeto e pela chave
  permitida para cliente; deve ser válido, único por ambiente e acessível internamente.
- **Ambiente de execução**: Contexto que fornece os valores de configuração, como preparação local
  ou automatizada, sem alterar o contrato consumido pelo aplicativo.
- **Chave permitida para cliente**: Credencial publicável ou pública/anon destinada a aplicativos;
  não concede privilégios administrativos e continua sujeita às regras de acesso do backend.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% das preparações com endereço e chave válidos, o aplicativo inicia com a
  integração disponível e os fluxos existentes continuam funcionais.
- **SC-002**: Em 100% dos builds avaliados com valor ausente, endereço inválido ou chave
  administrativa conhecida, a preparação é interrompida antes da geração do aplicativo.
- **SC-003**: Uma pessoa desenvolvedora consegue configurar um novo ambiente fornecendo somente os
  dois valores exigidos em até 5 minutos, sem editar código rastreado.
- **SC-004**: A inspeção de arquivos versionados, mensagens de erro e logs encontra zero ocorrências
  da chave real completa.
- **SC-005**: Em 100% das validações de regressão desta feature, nenhuma tela, navegação ou interação
  existente apresenta mudança funcional causada pela configuração.
- **SC-006**: Em 100% dos ambientes suportados avaliados, a origem dos valores pode mudar sem
  alterar o contrato consumido pelas futuras integrações do aplicativo.

## Assumptions

- O usuário fornecerá `supabaseUrl` e `supabaseKey` antes da implementação ou validação final; os
  valores reais não serão registrados nesta especificação.
- `supabaseKey` será uma chave publicável atual ou uma chave pública/anon legada. Chaves
  `service_role`, secretas ou administrativas não serão aceitas em nenhuma circunstância.
- Cada preparação do aplicativo usa um único projeto remoto por vez.
- A segurança dos dados continuará dependendo das políticas e regras de acesso configuradas no
  backend; uma chave de cliente não substitui essas proteções.
- Inicialização da configuração não exige uma consulta de teste nem acesso a dados reais.
- Autenticação, banco de dados, Storage, Realtime, funções remotas, modelos de dados e telas de
  erro de conectividade pertencem a features posteriores.
- Nenhum teste automatizado será criado sem solicitação explícita, conforme a governança atual do
  projeto; a validação desta feature poderá usar preparação, análise estática e inspeção manual.
