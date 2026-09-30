# Feature Specification: Android Environment Flavors

**Feature Branch**: `027-android-environment-flavors`

**Created**: 2026-09-29

**Status**: Draft

**Input**: User description: "Criar ambientes diferentes no Android. Criar Flavors dimensions: dev, homol e prod. Utilizar chaves, tokens e secrets nos 3 ambientes por enquanto. O Ambiente de dev não fará transação com Mercado Pago, ele criara um fluxo igual, mas sem transação, para testar as telas, os demais sim."

## Clarifications

### Session 2026-09-29

- Q: No ambiente `homol`, o Mercado Pago deve usar credenciais de teste/sandbox ou credenciais reais com transações controladas? → A: Credenciais reais; pela decisão de 30/09/2026, Homol usa o mesmo backend e a mesma integração Mercado Pago de Prod.
- Q: As versões `dev`, `homol` e `prod` devem poder ser instaladas simultaneamente no mesmo dispositivo? → A: Não; somente um ambiente será instalado por vez e a versão seguinte substituirá a anterior.
- Q: Onde os valores de configuração de cada ambiente devem ser fornecidos durante a geração do aplicativo? → A: Em arquivos separados por ambiente, mantidos fora do controle de versão.
- Q: Os ambientes `dev`, `homol` e `prod` devem usar backends separados ou compartilhar o backend atual com configurações diferentes? → A: Em 30/09/2026, Homol passou a espelhar Prod, incluindo Supabase e Mercado Pago; Dev mantém seu backend separado e pagamentos simulados.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Selecionar o ambiente do aplicativo (Priority: P1)

Como responsável por desenvolver, homologar ou publicar o aplicativo, quero gerar uma versão identificada como `dev`, `homol` ou `prod`, para que cada versão use o contexto correto sem alterações manuais no código.

**Why this priority**: Versões misturadas podem enviar usuários para serviços ou pagamentos errados.

**Independent Test**: Gerar uma versão de cada ambiente, instalar e confirmar que o nome, a identificação e as configurações correspondem ao ambiente escolhido.

**Acceptance Scenarios**:

1. **Given** uma versão `dev`, **When** ela é aberta, **Then** identifica desenvolvimento e usa somente configurações de `dev`.
2. **Given** uma versão `homol` ou `prod`, **When** ela é aberta, **Then** identifica o flavor correspondente e usa as mesmas configurações públicas e o backend de produção.
3. **Given** uma versão `dev` instalada com sessão ativa, **When** uma versão `homol` a substitui no mesmo dispositivo, **Then** o aplicativo identifica `homol`, usa seu backend e invalida a sessão e o estado incompatíveis da versão anterior antes de qualquer chamada autenticada.

---

### User Story 2 - Testar pagamentos sem cobrança em dev (Priority: P1)

Como pessoa desenvolvedora ou de QA, quero executar em `dev` o mesmo fluxo visual e de estados de pagamento sem criar transação no Mercado Pago, para testar checkout, aprovação, falha, cancelamento e retorno sem risco financeiro.

**Why this priority**: O desenvolvimento precisa repetir os testes sem gerar cobranças ou dados financeiros desnecessários.

**Independent Test**: Em `dev`, iniciar um pedido e percorrer os cenários de pagamento; confirmar que os estados aparecem como no fluxo normal e que nenhuma transação externa é criada.

**Acceptance Scenarios**:

1. **Given** o aplicativo `dev` em um fluxo que exige pagamento, **When** a pessoa inicia o pagamento, **Then** o aplicativo executa uma simulação equivalente sem chamar o Mercado Pago.
2. **Given** uma simulação `dev`, **When** a pessoa escolhe aprovação, falha, expiração ou cancelamento, **Then** o aplicativo apresenta o estado correspondente.
3. **Given** uma versão `dev`, **When** qualquer etapa de pagamento é executada, **Then** nenhuma cobrança, preferência ou transação externa é criada.
4. **Given** aprovação simulada em `dev`, **When** a pessoa retorna ao PitTec, **Then** pode percorrer deslocamento, início do atendimento e conclusão com estado local simulado, sem liberar um atendimento remoto por pagamento fictício.

---

### User Story 3 - Usar pagamentos reais nos ambientes autorizados (Priority: P1)

Como responsável por homologação ou produção, quero que `homol` e `prod` usem as mesmas credenciais e integrações de pagamento de produção, para validar o comportamento real do Mercado Pago.

**Why this priority**: Homologação e produção precisam validar o comportamento integrado, enquanto a simulação fica limitada a `dev`.

**Independent Test**: Conferir as configurações e o gateway integrado de Homol e Prod; um pagamento controlado em qualquer um dos dois usa o backend e a conta Mercado Pago de produção.

**Acceptance Scenarios**:

1. **Given** `homol`, **When** um pagamento é iniciado, **Then** usa as mesmas credenciais e destino de `prod`.
2. **Given** `prod`, **When** um pagamento é iniciado, **Then** usa as credenciais e o destino de produção.
3. **Given** `dev`, **When** a mesma operação é iniciada, **Then** nenhuma credencial de `homol` ou `prod` é usada.

---

### User Story 4 - Diagnosticar o ambiente com segurança (Priority: P2)

Como pessoa de desenvolvimento, suporte ou QA, quero identificar o ambiente ativo e receber erros coerentes com ele, para diagnosticar configurações sem expor tokens, chaves ou secrets.

**Why this priority**: A identificação reduz erros de diagnóstico e protege credenciais.

**Independent Test**: Consultar a identificação da versão, provocar uma falha de configuração e verificar a mensagem sem segredos na tela ou nos logs.

**Acceptance Scenarios**:

1. **Given** qualquer ambiente instalado, **When** a pessoa consulta sua identificação, **Then** vê `dev`, `homol` ou `prod` sem valores sensíveis.
2. **Given** configuração obrigatória ausente ou inválida, **When** um fluxo dependente dela começa, **Then** há falha clara e nenhuma credencial de outro ambiente é usada.
3. **Given** erro de pagamento ou rede, **When** ele é registrado, **Then** tokens, secrets e dados financeiros ficam ocultos.

### Edge Cases

- Uma versão `dev` não pode mudar para cobrança real por configuração remota, parâmetro de tela ou estado persistido.
- Credencial de `homol` ou `prod` ausente, inválida ou expirada deve impedir a chamada correspondente.
- A troca de uma versão por outra não deve reutilizar sessão, cache ou dados de pagamento incompatíveis com o novo ambiente.
- Primeira execução após atualização de uma versão legada sem identidade de ambiente deve invalidar os dados incompatíveis antes de gravar a nova identidade; instalação limpa deve apenas inicializar o escopo.
- Falhas de rede no fluxo simulado de `dev` devem continuar permitindo testar os estados locais definidos.
- Tokens administrativos, secrets e credenciais privadas devem permanecer fora do APK e do repositório.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE oferecer exatamente os ambientes `dev`, `homol` e `prod` para gerar e identificar versões Android.
- **FR-002**: Cada ambiente DEVE ter um arquivo local separado fora do controle de versão; Homol DEVE repetir os valores públicos de Prod, enquanto Dev usa seu backend separado.
- **FR-003**: A versão instalada DEVE exibir identificação clara do ambiente sem expor valores sensíveis.
- **FR-004**: O sistema DEVE impedir que `dev` inicie, autorize ou crie qualquer transação externa do Mercado Pago.
- **FR-005**: `dev` DEVE reproduzir o fluxo de pagamento necessário para testar telas, estados, retornos, aprovação, falha, expiração e cancelamento sem cobrança externa.
- **FR-006**: A simulação DEVE produzir estados e respostas suficientes para exercitar as mesmas decisões de apresentação do fluxo integrado.
- **FR-007**: `homol` e `prod` DEVEM permitir transações reais usando o mesmo backend e as mesmas credenciais de produção; as transações de `homol` devem ser controladas por pessoas autorizadas.
- **FR-008**: O sistema DEVE rejeitar configuração obrigatória ausente, inválida ou incompatível antes de uma operação financeira.
- **FR-009**: Como somente um ambiente será instalado por vez, a atualização ou troca de versão DEVE limpar, migrar ou invalidar de forma segura sessão, cache, preferências e dados de pagamento incompatíveis com o novo ambiente.
- **FR-010**: `homol` e `prod` DEVEM compartilhar dados, endpoint e credenciais públicas do backend de produção; `dev` DEVE usar backend separado. A sessão local DEVE ser invalidada ao trocar de flavor.
- **FR-011**: O sistema NÃO DEVE incluir secrets administrativos, tokens privados ou credenciais de infraestrutura no código versionado ou em logs.
- **FR-012**: Mensagens de erro DEVEM distinguir ambiente e configuração sem exibir chaves, tokens, secrets, dados completos de pagamento ou detalhes internos.
- **FR-013**: O sistema DEVE preservar o fluxo de pagamento integrado existente para ambientes diferentes de `dev`.
- **FR-014**: O comportamento selecionado DEVE ser determinístico por versão gerada; alterar o ambiente não pode depender de ação casual dentro da tela.
- **FR-015**: A configuração dos ambientes DEVE ser verificável antes da distribuição da versão correspondente.
- **FR-016**: Em `dev`, a aprovação simulada DEVE permitir continuar localmente o fluxo PitTec pelas telas de deslocamento, atendimento e conclusão, sem enviar pagamentos fictícios ao despacho remoto.
- **FR-017**: A validação conjunta pré-distribuição DEVE rejeitar Dev no backend de produção, rejeitar diferenças públicas entre Homol e Prod e rejeitar divergência entre project ref e endpoint.

### Key Entities

- **Ambiente de execução**: contexto `dev`, `homol` ou `prod`, com identidade e regras próprias.
- **Configuração de ambiente**: endpoints, chaves, tokens e referências a secrets; valores privados não aparecem em telas ou logs.
- **Backend de ambiente**: projeto de desenvolvimento exclusivo de Dev ou projeto de produção compartilhado por Homol/Prod; a identidade e o modo de pagamento pertencem ao flavor.
- **Modo de pagamento**: comportamento `simulado` para `dev` ou `integrado` para `homol` e `prod`.
- **Sessão de pagamento de teste**: representação temporária do fluxo simulado, com estados equivalentes sem cobrança externa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das versões geradas são identificadas corretamente como `dev`, `homol` ou `prod`.
- **SC-002**: 100% das tentativas de pagamento em `dev` terminam sem criar transação, preferência ou cobrança no Mercado Pago.
- **SC-003**: 100% dos cenários de aprovação, falha, expiração e cancelamento podem ser exercitados em `dev` sem credenciais financeiras reais.
- **SC-004**: 100% dos pagamentos em `homol` e `prod` usam o destino e as credenciais correspondentes, sem fallback silencioso.
- **SC-005**: 100% das configurações obrigatórias inválidas produzem erro antes da operação financeira.
- **SC-006**: Nenhum token privado, secret administrativo, credencial de infraestrutura ou dado financeiro sensível aparece em telas ou logs de validação.
- **SC-007**: Uma pessoa de QA distingue o ambiente em até 10 segundos e inicia um cenário simulado em `dev` sem alterar configurações manualmente.
- **SC-008**: Em `dev`, aprovação, deslocamento, início do atendimento e conclusão podem ser percorridos sem chamadas financeiras externas ou mutações de atendimento remoto pela simulação.
- **SC-009**: A verificação conjunta exige Homol igual a Prod, Dev separado e project ref compatível com o endpoint.

## Assumptions

- Os três ambientes existirão como versões alternativas do aplicativo; somente um ambiente será instalado por vez e a versão seguinte substituirá a anterior.
- Dev usa o projeto de desenvolvimento; Homol e Prod usam o mesmo projeto, dados e integrações de produção.
- Transações feitas em Homol são transações de produção e exigem controle operacional.
- O APK de depuração de Homol usa a assinatura de debug do Android. O release permanece sem assinatura até receber uma chave de distribuição; APKs sem assinatura não são instaláveis.
- A simulação `dev` poderá ser repetida sem conta, cartão ou autorização real do Mercado Pago.
- O fluxo de pagamento existente continuará sendo a referência de estados para `homol` e `prod`.
- Secrets privados ficam no backend correspondente: Dev em seu projeto, Homol/Prod no projeto de produção; arquivos locais de provisionamento permanecem fora do controle de versão.
- Testes de produção serão executados somente por pessoas autorizadas e com transações controladas.
