# Research: Acompanhamento pós-pagamento e cobrança PitTec

## Decisão 1: manter o cálculo financeiro no backend e congelar a proposta

- **Decisão**: O aceite do PitTec calcula e congela a proposta no atendimento. A criação e a aprovação do pagamento usam o total congelado no atendimento.
- **Rationale**: O código já possui `pittec_pricing_rules`, campos financeiros em `appointments` e uma projeção separada por papel. O cálculo no servidor impede que o aplicativo altere preço, percentual ou taxas.
- **Alternativas consideradas**: calcular no Android; rejeitado porque permite divergência entre cliente, PitTec e Mercado Pago. Recalcular a cada consulta; rejeitado porque uma alteração posterior de configuração não pode mudar uma cobrança já apresentada.

## Decisão 2: usar a distância da busca como base do adicional por quilômetro

- **Decisão**: Usar a distância em metros registrada no candidato do despacho, convertida para quilômetros, multiplicada pela tarifa vigente por quilômetro e congelada no aceite.
- **Rationale**: A busca já registra `distance_meters` para o candidato escolhido, tornando o cálculo reproduzível e sem nova dependência de rota. O arredondamento acontece no valor monetário em centavos.
- **Alternativas consideradas**: calcular distância no cliente; rejeitado porque localização e fórmula ficariam sob controle do dispositivo. Consultar rota externa antes do aceite; adiado, pois não é necessário para o requisito atual e adicionaria latência e uma dependência externa.

## Decisão 3: separar as projeções financeiras por papel

- **Decisão**: O PitTec recebe serviço, adicional de quilometragem e valor do PitTec. O cliente recebe somente o total final para exibição e pagamento.
- **Rationale**: Atende à regra de confidencialidade entre os papéis e reduz o risco de a UI apresentar markup ou taxas por engano.
- **Alternativas consideradas**: enviar todos os componentes e ocultá-los apenas na tela; rejeitado porque dados não necessários não devem atravessar a fronteira de autorização.

## Decisão 4: separar orçamento comercial e ordem de teste

- **Decisão**: A proposta usa R$ 2,00 por quilômetro (R$ 10,00 a cada 5 km), aplica 40% e taxas ao valor comercial, e congela também `payment_amount`. Durante os testes, `payment_amount` é sorteado em centavos entre R$ 1,00 e R$ 2,00 para Pix e cartão.
- **Rationale**: O cliente precisa ver e confirmar o total real, enquanto o Mercado Pago deve receber uma ordem de R$ 1,00. Ambos os valores precisam ser persistidos para que a aprovação não seja confundida com uma alteração de preço.
- **Alternativas consideradas**: confiar no `amount` do aplicativo; rejeitado por segurança e consistência financeira.

## Decisão 5: manter a navegação existente e usar o valor de teste somente na ordem

- **Decisão**: O checkout continua mostrando o total do snapshot aprovado; para PitTec, a requisição de criação usa o `payment_amount` aleatório congelado pelo backend e não envia um valor fixo pelo Android.
- **Rationale**: A tela continua coerente com o orçamento comercial, e a ordem enviada ao Mercado Pago permanece controlada pelo servidor.
- **Alternativas consideradas**: manter o valor fixo; rejeitado porque contradiz a regra comercial e permite que a tela mostre um total diferente da cobrança.

## Implementação: decisões verificadas

- A função `mp-create-payment` usa o valor reservado no banco. A regra ativa fixa `distance_rate_per_km = 2.00`, `payment_test_mode = true` e a faixa `test_payment_min_amount = 1.00` até `test_payment_max_amount = 2.00` durante a fase de testes.
- O checkout consulta o total autorizado via `get_my_active_pittec_dispatch`, inclusive após recriação; argumentos de navegação não são a fonte do preço.
- A tela real do prestador é `PitTecProviderDashboardScreen.kt`, que usava `ServiceEstimate` simulado. Agora usa os valores congelados do atendimento.
- Chegada permanece em `ACCEPTED` com `arrived_at`; somente a ação de início muda para `IN_PROGRESS` e preenche `service_started_at`. Isso preserva os índices e regras de rastreamento existentes.
- `client_start_acknowledged_at` registra o reconhecimento opcional do cliente sem bloquear o início operacional.
- A leitura direta de `appointments` por authenticated foi revogada: as RPCs continuam disponibilizando a projeção autorizada. Broadcasts de despacho enviam apenas identificador/revisão.
- O backend atual já valida aprovação contra o total do cliente; foi preservado e exercitado em transação com rollback.
- A tarifa configurada é R$ 2,00/km, equivalente a R$ 10,00 a cada 5 km. Novos aceites sem tarifa continuam bloqueados.
- Taxas existentes verificadas: percentual 0 e taxa fixa R$ 0,00; continuam configuráveis. O adicional comercial aplicado é exatamente 40%.
