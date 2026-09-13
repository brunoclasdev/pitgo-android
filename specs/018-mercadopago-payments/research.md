# Phase 0 Research: Pagamento de serviços (Pix e cartão)

## 1. Tokenização de cartão no Android

**Decision**: Usar o SDK Android oficial do Mercado Pago (`mercadopago-android-px` /
`mercadopago-android-sdk` mais recente estável na data da implementação) apenas para o formulário
de cartão e a geração do `card_token`; nenhuma outra parte do checkout (resumo, confirmação) usa
componentes prontos do SDK, para manter a UI 100% Material/Compose já do projeto.

**Rationale**: É o único caminho suportado para tokenizar PAN/CVV sem colocar o app em escopo PCI
DSS SAQ D — o token é gerado no dispositivo com a Public Key e nunca inclui PAN/CVV em texto claro
no tráfego de rede do PitGo.

**Alternatives considered**:
- Formulário próprio + chamada direta à API de tokenização do Mercado Pago sem o SDK: rejeitado —
  reimplementaria validação de bandeira/BIN e tratamento de erro que o SDK já mantém atualizado.
- Checkout Pro (redirecionamento web): rejeitado nesta rodada porque o usuário já decidiu por SDK
  nativo com tokenização no app (ver histórico da conversa que originou a spec).

## 2. Cartão salvo: Customers + Customer Cards

**Decision**: Ao cadastrar o primeiro cartão, `mp-save-card` cria um "Customer" do Mercado Pago
vinculado ao `user_id` do PitGo (armazenando o `mp_customer_id` numa coluna do perfil ou da própria
tabela `saved_cards`) e salva o `card_token` gerado no app como "Customer Card" desse Customer,
recebendo de volta um `mp_card_id` reutilizável, junto com bandeira e últimos 4 dígitos — dados que
o Mercado Pago já devolve na resposta, sem o PitGo precisar derivá-los do PAN.

**Rationale**: É o mecanismo do próprio Mercado Pago para cobranças futuras sem redigitar dados
(equivalente a "salvar cartão" em qualquer app de pagamento); o `mp_card_id` é estável e pode ser
referenciado em `POST /v1/payments` como `token` de pagamentos futuros.

**Alternatives considered**:
- Guardar o `card_token` original para reuso: rejeitado — tokens de cartão do Mercado Pago são de
  uso único/curta duração (minutos), inadequados para "pagar depois".
- Split de pagamento com repasse automático a prestadores (Marketplace/Application Fee): fora de
  escopo — a spec cobre só a cobrança do cliente; repasse a prestadores é decisão de produto
  separada, não solicitada.

## 3. Pix via API de Pagamentos

**Decision**: Gerar a cobrança Pix chamando `POST /v1/payments` com
`payment_method_id: "pix"`; a resposta traz `point_of_interaction.transaction_data.qr_code`
(copia e cola) e `qr_code_base64` (imagem pronta), então **nenhuma biblioteca de geração de QR é
necessária** — o Mercado Pago já devolve a imagem Base64. Corrigido em relação ao plano inicial: a
dependência de QR Code é dispensável.

**Rationale**: Usar o Base64 devolvido pela própria API evita depender de mais uma biblioteca
(alinhado a KISS/YAGNI) e garante que o QR exibido é exatamente o que o Mercado Pago valida.

**Alternatives considered**: gerar o QR localmente a partir do `qr_code` copia-e-cola com uma
biblioteca cliente (`zxing-core`) — descartado por ser redundante com o Base64 já fornecido.

## 4. Expiração do código Pix

**Decision**: Definir `date_of_expiration` da cobrança Pix para 30 minutos a partir da criação,
valor padrão de mercado e consistente com a suposição já registrada em spec.md. Ao expirar, o
webhook recebe o status `expired`; o app oferece gerar um novo código (novo `request_id` de
cobrança, mesmo `request_id` de solicitação) ou trocar de forma de pagamento.

**Rationale**: 30 minutos equilibra tempo suficiente para o cliente pagar sem manter uma solicitação
"presa" por muito tempo sem despachar prestador.

## 5. Verificação do webhook

**Decision**: `mp-webhook` valida o cabeçalho `x-signature`/`x-request-id` do Mercado Pago com o
segredo de assinatura de webhook (gerado no painel de integrações do Mercado Pago e guardado como
`MP_WEBHOOK_SECRET` em Supabase Secrets), conforme o algoritmo HMAC-SHA256 documentado pelo
Mercado Pago. Notificações com assinatura inválida são descartadas com `401` sem tocar no banco.
Mesmo com assinatura válida, o handler sempre busca o pagamento por `id` via
`GET /v1/payments/{id}` antes de gravar qualquer status — a notificação em si só é usada como
gatilho, nunca como fonte de verdade do valor/status.

**Rationale**: Evita que notificações forjadas alterem `payments` e mitiga o caso de payloads de
webhook desatualizados ou incompletos.

## 6. Idempotência de cobrança

**Decision**: `mp-create-payment` envia o cabeçalho `X-Idempotency-Key` da API do Mercado Pago com
o valor do `request_id` da solicitação (mais um sufixo de tentativa, ex.:
`request_id:attempt`, se o cliente pedir para gerar um novo código Pix após expiração). Antes de
chamar a API, a função também verifica via RPC se já existe um `payments` com status `pending` ou
`approved` para aquele `request_id` e, se houver, devolve o existente em vez de criar outro.

**Rationale**: Cobre tanto o caso de rede instável (retry do app reenviando a mesma requisição)
quanto o de múltiplos toques do usuário, satisfazendo FR-011/SC-006 sem estado adicional no app.

## 7. Retry e fallback do estorno automático

**Decision**: `mp-refund-payment` tenta a chamada de estorno (`POST /v1/payments/{id}/refunds`) até
3 vezes com backoff curto (ex.: 2 s, 8 s, 20 s) dentro da própria invocação/reentradas via fila
(RPC volta a enfileirar se a função falhar por erro transitório). Se as 3 tentativas falharem, o
pagamento é marcado `REFUND_FAILED` em vez de `REFUNDED`; o app mostra "reembolso em andamento" ao
cliente e o caso fica listado para o suporte consultar (consulta simples por status, sem precisar
de um painel novo nesta versão).

**Rationale**: Atende a decisão do usuário (retry automático + fallback para suporte) com a menor
infraestrutura possível — sem fila externa, reaproveitando o próprio Postgres como registro dos
casos pendentes.

**Alternatives considered**: fila dedicada (ex.: pg_cron ou serviço externo) para retries futuros
além da própria invocação — descartado por ora; se a taxa de falha de estorno se mostrar relevante
em produção, um `pg_cron` que varre `REFUND_FAILED`/`REFUND_PENDING` pode ser adicionado depois sem
mudar o contrato das tabelas.

## 8. CPF do pagador

**Decision**: Reaproveitar `Profile.cpf` (já coletado no cadastro do cliente, ver
`core/account/model/AccountModels.kt`) como `payer.identification.number` nas chamadas ao Mercado
Pago (Pix exige CPF do pagador). Nenhuma tela nova de coleta de CPF é necessária.

**Rationale**: Elimina uma etapa de UX e um requisito de dado que o app já possui; consistente com
KISS/YAGNI.
