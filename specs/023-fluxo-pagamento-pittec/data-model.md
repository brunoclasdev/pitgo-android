# Modelo de dados: Fluxo de pagamento do PitTec

## Entidades existentes reutilizadas

### service_searches

Solicitação PitTec criada pelo cliente e usada como request_id financeiro.

Campos relevantes:

- id: UUID estável da solicitação e external_reference do pagamento.
- client_id, service_id: proprietários e serviço solicitado.
- status: SEARCHING, AWAITING_PROVIDER, AWAITING_CLIENT_CONFIRMATION,
  AWAITING_PAYMENT, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED ou EXHAUSTED.
- revision, updated_at: concorrência otimista e retomada.
- localização e precisão: usados pela busca, fora do escopo financeiro.

Regras:

- Nenhuma cobrança é criada enquanto o status não tiver um appointment aceito.
- A solicitação é encerrada quando a proposta expira sem pagamento aprovado.

### appointments

Representa o candidato PitTec aceito pelo prestador e a proposta financeira congelada.

Campos existentes usados:

- id, dispatch_search_id, client_id, provider_id, service_id.
- quoted_price: valor base devido ao prestador.
- quote_version: versão monotônica da proposta.
- status, revision, accepted_at, client_confirmation_deadline,
  client_confirmed_at e client_confirmed_quote_version.

Campos aditivos planejados:

| Campo | Tipo | Regra |
|---|---|---|
| pricing_rule_id | UUID | Regra comercial aplicada, obrigatória para proposta paga |
| provider_amount | numeric(12,2) | Igual ao valor base congelado; positivo quando o serviço é pago |
| markup_amount | numeric(12,2) | 40% do provider_amount, arredondado a centavos |
| customer_fee_amount | numeric(12,2) | Taxas da regra vigente, arredondadas a centavos |
| customer_total_amount | numeric(12,2) | provider_amount + markup_amount + customer_fee_amount |
| pricing_currency | text | BRL para esta feature |
| quote_expires_at | timestamptz | accepted_at + 15 minutos |
| pricing_rule_version | bigint | Versão imutável copiada da regra no aceite |

Regras:

- provider_amount e quoted_price devem coincidir para preservar compatibilidade existente.
- customer_total_amount deve ser positivo e igual à soma dos componentes.
- quote_expires_at não pode ser alterado após confirmação do cliente.
- Pagamento válido deve referenciar appointment_id e quote_version e ter amount igual a
  customer_total_amount, nunca provider_amount.

### payments

Registro de tentativa Orders existente, estendido semanticamente para PitTec.

Campos relevantes:

- request_id = service_searches.id.
- appointment_id, quote_version e service_id vinculam a tentativa à proposta.
- amount = customer_total_amount.
- provider_api = ORDERS; external_reference/idempotency_key permanecem estáveis.
- status/sync_state/revision controlam criação, verificação, aprovação e recuperação.
- saved_card_id e últimos quatro dígitos identificam o método sem armazenar PAN/CVV.

Regras:

- Uma tentativa ativa ou aprovada impede nova criação para o mesmo contexto.
- Uma tentativa que exceda a validade deve ser cancelada/reconciliada antes de qualquer nova
  proposta.
- APPROVED só libera o atendimento quando o valor, moeda, contexto e quote_version forem válidos.

## Entidade nova

### pittec_pricing_rules

Regra comercial versionada que fornece as taxas aplicadas ao total do cliente.

Campos:

| Campo | Tipo | Regra |
|---|---|---|
| id | UUID | Identificador da regra |
| version | bigint | Única e monotônica |
| name | text | Nome operacional não sensível |
| markup_percent | numeric(5,2) | 40.00 para a regra desta feature |
| fee_percent | numeric(7,4) | Percentual de taxa, não negativo |
| fee_fixed_amount | numeric(12,2) | Parcela fixa em BRL, não negativa |
| currency | text | BRL |
| effective_from | timestamptz | Início da vigência |
| effective_until | timestamptz nullable | Fim opcional, maior que início |
| active | boolean | Permite suspender a regra |
| created_at, updated_at | timestamptz | Auditoria |

Regra de seleção:

- Para cada aceite, selecionar uma única regra ativa cujo período contenha o instante do aceite.
- Se nenhuma regra válida existir, o aceite não avança para confirmação financeira.
- A regra selecionada e seus parâmetros são copiados/congelados na proposta para que alterações
  futuras não mudem cobranças existentes.
- A configuração da regra não deve ser editável pelo cliente ou prestador.

## Visões financeiras por papel

### Visão do prestador

Permitidos: service name, provider_amount, currency, quote_version, prazo de resposta e estado.

O snapshot nunca inclui markup_amount, customer_fee_amount ou customer_total_amount.

### Visão do cliente

Permitidos: service name, markup_amount, customer_fee_amount, customer_total_amount, currency,
quote_version, quote_expires_at, estado e cartões/métodos disponíveis por APIs próprias.

O snapshot nunca inclui provider_amount ou qualquer campo de recebimento do prestador.

### Visão interna do backend

Inclui todos os componentes, pricing_rule_id/version, payment IDs e estados de reconciliação.
Não é retornada por RPC autenticada de cliente.

## Estados e transições

    SEARCHING/AWAITING_PROVIDER
      └─ prestador aceita ─> AWAITING_CLIENT_CONFIRMATION
           ├─ cliente confirma versão vigente ─> AWAITING_PAYMENT
           │    ├─ pagamento aprovado ─> ACCEPTED
           │    │    └─ prestador chega ─> IN_PROGRESS
           │    ├─ pagamento pendente ─> AWAITING_PAYMENT
           │    └─ pagamento recusado/erro ─> AWAITING_PAYMENT
           ├─ cliente recusa ─> CANCELLED/SEARCHING conforme regra de despacho
           └─ 15 minutos sem pagamento aprovado ─> CANCELLED/EXHAUSTED
                └─ prestador liberado

Guards:

- Aceite exige candidato ativo, serviço pago e provider_services.price positivo.
- Confirmação exige appointment/status/revision/quote_version pertencentes ao cliente e
  quote_expires_at maior que o horário do servidor.
- Criação de pagamento exige confirmação do cliente e o total congelado; amount enviado pelo app é
  apenas comparação opcional, nunca autoridade.
- APPROVED exige pagamento integral, moeda BRL, mesma solicitação, appointment, versão e total.
- Nenhuma transição pode ser repetida para liberar dois atendimentos.

## Privacidade, RLS e auditoria

- RPCs de snapshot e transição usam identidade autenticada e verificam client_id/provider_id.
- O cliente não acessa diretamente appointments ou tabelas de regras para compor valores.
- O prestador não consulta customer_total_amount; o cliente não consulta provider_amount.
- Logs não registram PAN, CVV, tokens, payloads do provedor ou componentes financeiros de outro
  papel; eventos operacionais usam UUID local, estado e código sanitizado.
