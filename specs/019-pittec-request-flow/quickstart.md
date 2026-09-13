# Quickstart: validar o fluxo correto de solicitação do PitTec

## Pré-requisitos

1. Migration desta feature aplicada no projeto Supabase compartilhado (`supabase db push`), com pelo
   menos um prestador PitTec disponível (`location_mode = 'PITTEC'`, `available = true`) e um
   segundo dispositivo/sessão para representar esse prestador.
2. Cliente de teste com CPF cadastrado (Pix exige, `018`) e/ou um cartão salvo.
3. Dois dispositivos/emuladores: um logado como cliente, outro como o prestador PitTec de teste.

## Build

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Esperado: build e lint passam sem erros novos atribuíveis a esta feature.

## Cenário 1 — Mapa e solicitação sem pagamento prévio (US1)

1. Como cliente, toque em "PitTec" na Home.
2. Confirme que a tela abre direto no mapa centralizado na sua posição, com um botão de
   "Solicitar" visível — **sem nenhuma tela de pagamento antes disso**.
3. Confirme que negar a permissão de localização impede a centralização/o botão até conceder.

## Cenário 2 — PitTec encontrado, confirmar (US2)

1. Com o prestador de teste disponível e próximo, toque em "Solicitar".
2. Confirme que, ao encontrar o prestador, aparece um card com o nome dele e a distância — **antes
   de qualquer pagamento**.
3. Confirme (no dispositivo do prestador) que ele já recebeu a oferta `PENDING` nesse momento
   (comportamento existente, inalterado).
4. Toque em confirmar no card do cliente; confirme que a tela passa a mostrar "aguardando resposta
   do prestador", sem cobrança.

## Cenário 3 — Recusar e tentar o próximo (US2)

1. Repita o Cenário 2 até o card "PitTec encontrado" aparecer.
2. Toque em recusar; confirme que nenhuma cobrança ocorre e que a busca continua (verifique no
   dispositivo do prestador de teste que a oferta anterior foi cancelada).
3. Se houver um segundo prestador de teste disponível, confirme que ele recebe a próxima oferta.

## Cenário 4 — Valores e início do serviço (US3)

1. No dispositivo do prestador, aceite a oferta.
2. No dispositivo do cliente, confirme que aparece o card de valores com o valor real (tarifa fixa
   de plataforma) — não mais um valor fictício.
3. Confirme via Pix ou cartão salvo; confirme que, após a aprovação, o cliente é levado ao
   acompanhamento do atendimento (mesma tela já existente).
4. No dispositivo do prestador, confirme que marcar "Cheguei" só é aceito depois do passo anterior
   (pagamento aprovado) — tentar antes deve falhar de forma tratada, não travar o app.

## Cenário 5 — Desistências não cobram (edge cases)

1. Repita o Cenário 2, mas volte a tela (botão Voltar) antes de confirmar o card "PitTec
   encontrado"; confirme que nada foi cobrado e nenhum atendimento ficou pendente.
2. Repita o Cenário 4, mas cancele antes de confirmar o card de valores; confirme que nenhuma
   cobrança ocorreu.
3. Force uma falha de pagamento no card de valores (ex.: cartão de teste de recusa); confirme
   mensagem compreensível e opção de tentar de novo sem perder o prestador já aceito.

## Verificações técnicas complementares

- `supabase db lint` sem erros na migração.
- Conferir manualmente que `start_pittec_dispatch` aceita chamadas sem nenhum `payments` aprovado
  associado (a guarda foi removida) e que `arrive_pittec_appointment` recusa sem pagamento aprovado.
- Conferir que nenhum log do app ou das Edge Functions imprime nome completo do prestador fora do
  campo `candidate`/`provider` já previsto.
