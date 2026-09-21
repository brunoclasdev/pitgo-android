# Research: Fluxo de pagamento do PitTec

## Contexto existente

**Decision**: Estender o fluxo PitTec existente e o backend de pagamentos Orders já implementado.

**Rationale**: O projeto já possui busca sequencial, aceite/recusa, estados
AWAITING_CLIENT_CONFIRMATION e AWAITING_PAYMENT, snapshots por papel, PaymentRepository, SDK de
cartões, reserva idempotente, webhook e executor de reconciliação. Criar um segundo checkout ou
uma segunda máquina de pagamento aumentaria o risco de cobranças duplicadas.

**Alternatives considered**: Manter cobrança antes da busca; rejeitado porque contradiz a regra
de aceitar antes de calcular/cobrar. Criar um fluxo PitTec financeiro separado; rejeitado porque
duplicaria Orders, reconciliação, cartões e histórico.

## Fonte do valor base

**Decision**: Usar o preço ativo de provider_services associado ao candidato aceito e congelá-lo
na proposta/appointment no momento do aceite.

**Rationale**: provider_services.price já é a fonte do valor do prestador e o dispatcher já grava
quoted_price/quote_version. Congelar a versão impede que uma alteração posterior no cadastro mude
uma proposta que o cliente está revisando.

**Alternatives considered**: Buscar o menor preço atual a cada tela; rejeitado por permitir que a
mesma proposta mude entre visualização, confirmação e cobrança. Usar um preço enviado pelo app;
rejeitado por não ser autoridade financeira.

## Fórmula monetária

**Decision**: Calcular em centavos/decimal exato: valor base = valor do prestador; adicional =
valor base × 40%, arredondado para centavos; taxas = componentes da regra comercial vigente,
também arredondados para centavos; total do cliente = base + adicional + taxas.

**Rationale**: A decisão da clarificação exige 40% antes das taxas e exibição dos componentes.
Persistir cada componente e a versão da regra torna o valor reproduzível sem ponto flutuante.

**Alternatives considered**: Calcular somente o total e reconstruir os componentes na UI;
rejeitado porque pode gerar divergência e expor uma fórmula diferente da cobrada. Arredondar
apenas no total; rejeitado porque o cliente precisa conferir os componentes em centavos.

## Regra comercial de taxas

**Decision**: Criar uma regra versionada e ativa para o PitTec, com vigência, percentual/fixo de
taxa, moeda e marca de ativação. A proposta grava a regra e os valores calculados que utilizou.

**Rationale**: A clarificação escolheu uma regra vigente e configurável. A proposta deve
continuar imutável mesmo quando uma regra futura entrar em vigor.

**Alternatives considered**: Constante no app; rejeitado por ser manipulável e exigir nova versão.
Taxa enviada pelo cliente; rejeitado por não ser confiável. Configuração sem versionamento;
rejeitado porque impede auditoria e recuperação de propostas antigas.

## Separação por papel

**Decision**: O snapshot SQL calcula uma visão específica para o chamador autenticado. O cliente
recebe apenas adicional, taxas e total; o prestador recebe somente provider_amount. A persistência
completa permanece protegida no backend.

**Rationale**: Ocultar campos na tela não protege dados que já foram enviados ao aplicativo.
Filtrar antes da serialização atende à privacidade e à constituição do projeto.

**Alternatives considered**: Enviar todos os valores e esconder componentes na UI; rejeitado por
vazamento. Criar duas tabelas duplicadas de valores; rejeitado por aumentar inconsistência.

## Confirmação e expiração

**Decision**: Após o aceite, a proposta fica em AWAITING_CLIENT_CONFIRMATION por 15 minutos.
Confirmação usa quote_version e revision; uma confirmação válida leva a AWAITING_PAYMENT. Se
expirar sem pagamento aprovado, libera o prestador, encerra a solicitação e bloqueia pagamento.

**Rationale**: A janela foi definida na clarificação e o projeto já tem cron para expiração lazy/
periódica de confirmações. A guarda deve ser server-side para funcionar com o app fechado.

**Alternatives considered**: Dois minutos atuais; rejeitado porque é curto para revisão/cadastro
de cartão. Manter a busca ativa após expirar; rejeitado porque deixa prestador e valor presos.
Permitir pagamento tardio; rejeitado por quebrar a validade do preço.

## Cartões

**Decision**: Reutilizar SavedCardRepository e CoreMethods. O checkout lista cartões salvos e
permite entrar na tokenização nativa para cadastrar um novo cartão, atualizar a lista e tentar o
pagamento da mesma proposta confirmada.

**Rationale**: O projeto já protege PAN/CVV e possui contexto de tokenização. O cadastro inline
atende a decisão da clarificação sem persistir dados sensíveis no app.

**Alternatives considered**: Exigir cartão cadastrado antes de abrir o checkout; rejeitado porque
a decisão permite cadastro durante o pagamento. Criar formulário próprio para PAN/CVV; rejeitado
por segurança e duplicação do SDK existente.

## Mercado Pago Orders e idempotência

**Decision**: Enviar ao Mercado Pago apenas o total do cliente, depois da confirmação. Manter
external_reference, idempotency key e recovery já persistidos; timeout, 409, 423 ou 5xx continuam
em verificação/reconciliação, sem POST duplicado.

**Rationale**: O prestador não precisa conhecer o total do cliente e o processador deve cobrar
exatamente o total congelado. O executor existente suporta app fechado e resposta perdida.

**Alternatives considered**: Criar uma cobrança por tentativa de cartão; rejeitado porque falha
de rede pode duplicar cobrança. Aprovar o atendimento após retorno local do app; rejeitado porque
o webhook/snapshot do backend é a autoridade.

## Início do atendimento

**Decision**: O trigger/RPC server-side só libera ACCEPTED/IN_PROGRESS quando houver pagamento
aprovado, vigente, íntegro, não cancelado e compatível com appointment, quote_version e total.

**Rationale**: Impede que o prestador inicie por estado local ou aprovação de uma proposta
diferente. O app navega para tracking somente após o snapshot financeiro aprovado.

**Alternatives considered**: Liberar ao criar a Order; rejeitado porque criação não significa
quitação. Liberar com pagamento pendente; rejeitado pelo requisito de negócio.
