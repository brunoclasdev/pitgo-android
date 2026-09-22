# Quickstart: Pix e cancelamento do pagamento

## Pré-requisitos

- Workspace na raiz do projeto.
- Java/Android SDK configurados.
- Supabase remoto configurado para o projeto.
- Uma conta cliente autenticada e uma solicitação PitTec que tenha chegado a `AWAITING_PAYMENT`.
- Chave Pix e credenciais de produção habilitadas no Mercado Pago.

## Validação estática e build

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
npx supabase db lint --project-ref eetbrkznuqbdrnybnyhf
```

Resultado esperado: build, lint e validação das migrações sem erro.

## Cenário 1: Pix na tela

1. Abra o fluxo PitTec como cliente.
2. Aguarde o aceite do prestador e a abertura da tela de pagamento.
3. Selecione Pix.
4. Confirme que o valor mostrado é `R$ 1,00`.
5. Confirme que o QR Code é visível.
6. Toque em “Copiar código Pix” e cole o resultado em um campo de texto para conferir que não está vazio.
7. Feche e reabra a tela de pagamento.
8. Confirme que a mesma cobrança é recuperada e nenhuma segunda cobrança é criada.

Resultado esperado: QR Code e Copia e Cola permanecem disponíveis enquanto o Pix estiver válido.

## Cenário 2: resposta Pix incompleta

1. Use uma cobrança cuja resposta ainda esteja em processamento ou sem QR completo.
2. Aguarde o tempo limite de verificação.
3. Confirme que a tela sai do carregamento infinito e oferece tentar novamente ou cancelar.

Resultado esperado: o cliente nunca vê um Pix pronto sem QR e Copia e Cola válidos.

## Cenário 3: cancelamento antes do pagamento

1. Abra uma cobrança Pix de R$ 1,00.
2. Toque em cancelar e confirme.
3. Consulte a solicitação e o pagamento com uma consulta autenticada de leitura.
4. Retorne à tela inicial de solicitação PitTec.
5. Tente iniciar uma nova solicitação.

Resultado esperado: solicitação e pagamento ficam `CANCELLED`, a solicitação antiga não bloqueia o cliente e uma nova solicitação pode começar.

## Cenário 4: cancelamento depois da aprovação

1. Conclua um pagamento Pix de R$ 1,00.
2. Solicite o cancelamento antes do início ou conforme a regra permitida.
3. Consulte o estado persistido até o reembolso terminar.

Resultado esperado: o pagamento aprovado permanece no histórico, o reembolso segue `REFUND_PENDING` até `REFUNDED` ou `REFUND_FAILED`, e o cliente retorna à tela inicial depois de o cancelamento ser registrado.

## Cenário 5: concorrência e retomada

1. Inicie o cancelamento em dois dispositivos ou toque repetidamente no botão.
2. Feche o aplicativo durante a resposta.
3. Reabra o aplicativo e consulte o fluxo.

Resultado esperado: somente uma operação de cancelamento é efetiva; o aplicativo mostra o estado persistido e não cria cobrança duplicada.

## Evidências mínimas

- Screenshot da tela Pix com QR Code e ação Copia e Cola.
- Valor exibido e cobrado igual a `R$ 1,00`.
- IDs da solicitação e pagamento com status final verificado sem expor tokens ou dados de cartão.
- Log sem credenciais, número completo de cartão, CPF completo ou código Pix completo.
