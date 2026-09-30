# Research: Guincho

## Contratação dirigida

**Decision**: `GUINCHO_DIRECT`, com identificação pelo cadastro MOBILE/oferta Guincho, preservando MOBILE_SEQUENTIAL/PitTec.
**Rationale**: start/request/recusa/expiração móveis atuais avançam fila; mp-create-payment valida modalidades fechadas. Não podem receber seleção exclusiva sem ramo explícito.
**Alternatives considered**: modificar globalmente móvel (quebra pedidos antigos); pagamento paralelo (duplicação).

## Rota e cotação

**Decision**: Edge Function autenticada `guincho-quote` calcula retirada → destino usando configuração OSRM-compatible já disponível; persiste distância confiável com RPC service-role. Validade 15 min, padrão de cotação existente. Sem rota, erro recuperável e contratação bloqueada.
**Rationale**: pittec-route já tem configuração/limites/timeouts, mas depende de appointment e não cobre pré-pagamento. Route devolve distância viária em metros ([OSRM](https://project-osrm.org/docs/v5.24.0/api/)). Reutilizar rota para revisão do rascunho, pois não depende do guincheiro selecionado.
**Alternatives considered**: distância enviada pelo Android (manipulável); linha reta (não é percurso solicitado); novo fornecedor (desnecessário).

## Preço e taxas

**Decision**: `guincho_pricing_rules`, tarifa inicial 3.00, markup 10.00, taxas copiadas da política vigente, vigência/versão. Mesma fórmula de taxas existente: percentual sobre remuneração + fixo. Em Guincho, payment_amount = customer_total_amount; Dev simula esse valor sem MP, Homol/Prod cobram o total mostrado.
**Rationale**: cálculo PitTec inclui tarifa própria, distância prestador-cliente e payment_test_mode que reduz cobrança; não serve diretamente. R$ 5 da spec é exemplo, não taxa padrão.
**Alternatives considered**: usar diretamente regras PitTec (distância/tarifa/valor de teste incorretos); calcular preço confiável na UI (inseguro).

## Reserva e compensação

**Decision**: reservar prestador ao Contratar em AWAITING_PAYMENT, transacionalmente; expiração da reserva acompanha a intenção de pagamento vigente. APPROVED libera oferta PENDING e inicia prazo de resposta existente. Segundo cliente recebe indisponibilidade antes de cobrar. Aprovação tardia de pedido cancelado gera compensação/refund, nunca ressuscita reserva.
**Rationale**: evitar dois pagamentos para um prestador; índices/jobs atuais contemplam estados limitados e avanço sequencial. Espera por confirmação não expira automaticamente e DISPUTED libera reserva.
**Alternatives considered**: cobrar sem reserva (reembolso frequente); ofertar antes de aprovação (contradiz privacidade/fluxo).

## Orders/refund e projeção

**Decision**: estender reserva/checkout/reconciliação/refund existentes com appointment_id e quote_version; chaves duráveis, amount confiável. Reembolso solicitado em SQL e executado pelo pipeline sem chamada externa sob lock. Cliente recebe só total; prestador só remuneração e dados após aprovação.
**Rationale**: infraestrutura atual já tem recuperação e compensação; Orders fornece X-Idempotency-Key ([MP](https://www.mercadopago.com.br/developers/en/reference/online-payments/checkout-api/create-order/post)). Apenas esconder campos na UI não protege valores/dados.
**Alternatives considered**: refund no browser (inseguro); chave nova por retry (duplica); nova infraestrutura (sem necessidade).

## Contestação e dashboard

**Decision**: DISPUTED fora de índices/restauradores operacionais; histórico separado; MANAGER/ADMIN/OWNER ativos resolvem via dashboard com justificativa/revisão/auditoria. Refund integral inclui todo amount pago.
**Rationale**: usuário liberou novos pedidos durante contestação; dashboard em `/Users/brunocesar/projetos-web/pitgo` usa `dashboard_access_at_least('MANAGER')`. Resolver caso velho não altera o novo.
**Alternatives considered**: bloquear prestador (rejeitado); manual fora do dashboard/parcial (fora do escopo aprovado).

## Integração Android

**Decision**: MVVM, mapas/checkout/tracking existentes; enums/snapshots atualizados em conjunto; datas opcionais JSON null; parar rastreamento operacional após solicitar conclusão, mantendo observação de estado.
**Rationale**: falhas anteriores vieram de jsonb_set com SQL NULL e enum novo incompleto. Sem novos SDKs nem alterações de versões neste planejamento.
**Alternatives considered**: novo tracking (duplicação); strings de estado sem tipos (viola constituição).

## Inventário de implementação

`service_searches` recebe modalidade própria para distinguir pedidos dirigidos de pedidos móveis legados do mesmo serviço. Constraints afetadas: status de appointments/searches e modalidade/contexto de payments. Índices operacionais existentes já incluem espera por pagamento/confirmação e excluem DISPUTED. Broadcasts `mobile-dispatch`/`provider-dispatch` existentes enviam apenas ID/revisão. Funções de aceite/expiração móveis avançam fila e exigem isolamento do ramo dirigido. `private.payment_can_release` exige aprovação atual, moeda/valor liquidado e ausência de tentativa concorrente. Backend de rota usa `PITTEC_ROUTING_BASE_URL`/`PITTEC_ROUTING_API_KEY`, timeout 8 s e limite 2.000 km. Dev tem gateway simulado local; Homol/Prod compartilham a base de produção e MP. Não publicar aprovação simulada no banco de produção.
