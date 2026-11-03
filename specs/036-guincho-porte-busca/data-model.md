# Data Model: Guincho por porte

Nomes novos abaixo são propostas de implementação; tabelas sem indicação de novas já existem. UUIDs como identidade, timestamps UTC, BRL e cálculos `numeric`/BigDecimal, sem valores monetários em ponto flutuante.

## Catálogos e preços

| Entidade | Campos relevantes | Regras |
|---|---|---|
| `vehicle_transport_categories` (nova) | id, code, name, active, revision, created_at, updated_at | code único e estável, nome não vazio; micro-ônibus deve estar representado no catálogo operacional. |
| `guincho_sizes` (nova) | id, code, name, active, revision, created_at, updated_at | code único e estável; rótulo não implica capacidade. |
| `guincho_size_compatibility` (nova) | size_id, vehicle_category_id | PK composta e FKs; só relação explícita permite transporte; edição atômica com revisão do porte. |
| `guincho_size_prices` (nova) | id, size_id, version, request_id, base_amount, currency, active, effective_from, effective_until, created_by, created_at | Preço positivo com duas casas, BRL; request_id único para idempotência; somente uma versão vigente por porte; valor histórico imutável. |
| `guincho_pricing_rules` | campos existentes de distância, markup, fee, duração e driver price | Continua fonte dos componentes comuns. Sem uso do base_amount global em nova cotação por porte. |

Desativação é lógica: manter FKs e histórico; não apagar porte/categoria referenciado. Desativado sai de novas ofertas, sem reescrever pedidos confirmados. Nenhum valor ou capacidade comercial deve ser inventado pela migração.

## Cadastros existentes

- `vehicles.transport_category_id`: FK opcional para migração; obrigatória para usar busca de guincho. Leitura do proprietário e atualização pelas rotas/RPCs de veículo já existentes, com validação definitiva no servidor. Atualizar Vehicle/VehicleDraft/DTOs/formulários de garagem e onboarding.
- `provider_vehicles.revision`: contador otimista novo, inicializado em 1 para registros existentes e incrementado em edição; acompanha updated_at já existente.
- `provider_vehicles.guincho_size_id`: FK opcional para legado; obrigatória em novo cadastro/edição GUINCHO. Não exigir porte para veículo de outra modalidade.
- `providers.dispatch_vehicle_id`: FK opcional de migração, obrigatória para ofertar guincho. Validar transacionalmente que o veículo pertence ao provider; FK isolada não prova propriedade. Troca pelo proprietário autenticado, nunca com veículo alheio.
- Prestador legado sem veículo pode cadastrar seu primeiro guincho no perfil, com os campos obrigatórios e porte explicitamente escolhido; cadastro owner-scoped e idempotente, sem backfill presumido.
- Modelo de frota: uma conta oferece o veículo de atendimento selecionado, sem inferência pelo maior veículo cadastrado.

## Cotação e pedido

Adicionar a `guincho_quotes`: dispatch_vehicle_id, guincho_size_id, size_price_id/version e transport_category_id; capturar também rótulos relevantes no snapshot. Manter pricing_rule_id/version e todos os valores atuais. Pedidos referenciam a cotação e preservam seu contexto para histórico e pagamento. Cada nova escolha confirmada gera um novo service_searches/appointment pelo fluxo existente; nunca reabrir o pedido financeiro cancelado como se ainda estivesse em busca. Linhas antigas aceitam campos novos nulos; não fabricar snapshots retroativos.

Novas cotações: categoria/porte ativos, relação compatível, veículo selecionado próprio, preço por porte e regra comum vigentes, disponibilidade e ausência de exclusão. Reserva valida novamente capacidade e identidade da oferta, sem substituir o preço de uma cotação ainda válida por tarifa mais recente. Troca de veículo de atendimento ou porte em relação ao snapshot exige nova seleção/cotação; alteração apenas do preço não.

Cálculo preservado:

- distância = round(km × tarifa comum, 2);
- remuneração/base = preço do porte + distância;
- markup = round(base × percentual PitGo / 100, 2);
- taxas = round(base × percentual de taxas / 100 + taxa fixa, 2);
- total = base + markup + taxas; preço reservado do guincheiro não participa.

## Contexto retomável e exclusões

- `guincho_search_contexts` (nova): id, client_id, draft_id, generation, state (`DISCOVERY`, `REQUESTED`, `CLOSED`), appointment_id opcional, revision, updated_at. Um contexto não encerrado por cliente. A viagem completa continua em `guincho_trip_drafts`; contexto não replica endereços. Usar locks para preservar unicidade e prioridade do atendimento operacional.
- `guincho_search_exclusions` (nova): context_id, generation, provider_id, source_appointment_id, created_at. PK(context_id, generation, provider_id); source aponta para recusa verificável, nunca entrada livre do cliente.
- Ao salvar viagem, comparar os dados confirmados (vehicle_id, retirada e destino com endereços normalizados e coordenadas confirmadas) sob lock; incrementar generation somente em alteração efetiva. Revision continua sendo controle otimista técnico e pode mudar sem limpar exclusões.
- As exclusões abrangem o provider que recusou, independentemente de troca de veículo da sua frota. Recusas sucessivas acumulam exclusões da mesma geração. Mudar um dos dados da viagem inicia nova geração; não ressuscitar exclusões antigas se o usuário depois voltar aos dados anteriores.
- Pedido anterior à implantação sem contexto: na recusa, recuperar o rascunho e a cotação vinculados ao appointment, verificar que pertencem ao mesmo cliente e criar o contexto uma única vez sob lock por cliente/pedido, preservando veículo, retirada e destino. Não inferir porte/categoria ausentes nem alterar snapshots financeiros antigos. Se já existir contexto de viagem mais recente ou atendimento operacional novo, não sobrescrevê-lo nem redirecionar o cliente.
- Logout limpa estado local; dados remotos continuam restritos ao cliente. Reabertura usa contexto do servidor e não histórico de navegação local.

## Transições

1. Salvar viagem válida → DISCOVERY; descobrir/cotar não encerra a geração.
2. Reservar cotação → REQUESTED, ligar appointment e preservar operação idempotente existente.
3. Recusa confirmada do appointment corrente → cancelar pedido conforme regras existentes, registrar exclusão uma vez, invalidar seleção/cotação recusadas e retornar contexto a DISCOVERY na mesma transação.
4. Pedido pago legado recusado → mesmo passo, mais reembolso existente; falha/pêndencia financeira não bloqueia a busca.
5. Aceite → seguir estados existentes; confirmação de pagamento/execução não é alterada por esta feature. Um contexto ligado a atendimento operacional não é reaberto por recusa atrasada de outro pedido.
6. Cancelamento voluntário, expiração ou outro encerramento definitivo diferente de recusa → CLOSED somente para o contexto ligado ao pedido encerrado, preservando prazos, regras financeiras e navegação existentes; não gerar exclusão de guincheiro nem retorno automático à busca por recusa. Recusa explícita segue os passos 3–4, não esta transição.
7. Conclusão definitiva do atendimento → CLOSED. Nova viagem cria contexto novo; exclusão não é bloqueio permanente. Eventos de pedidos antigos não encerram nem reabrem contexto de outro atendimento.

## Segurança e concorrência

RLS para leitura do próprio contexto, rascunho, exclusões, veículos e snapshots permitidos; nenhum cliente grava exclusões ou preços diretamente. MANAGER cadastra tipos/compatibilidades/preços via RPC auditada. Catálogo ativo é leitura autenticada, sem histórico de tarifas administrativas ou preço reservado exposto ao app.

RPCs SECURITY DEFINER com search_path vazio, autenticação explícita e grants mínimos. Lock por contexto/appointment para recusa/reserva; revisão esperada em edição administrativa e de cadastro; request_id com comparação de payload em retries. Índices nas FKs de elegibilidade, preço vigente por porte e contexto atual por client_id. Alterações concorrentes retornam conflito em vez de escolher arbitrariamente uma versão.
