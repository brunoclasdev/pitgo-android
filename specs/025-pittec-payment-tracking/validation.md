# Validação — feature 025

## Concluído

- `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL; apenas avisos de depreciação preexistentes.
- `git diff --check`: sem erros.
- `npx supabase db push --linked --dry-run`: detectou somente a migração nova.
- Migração completa executada no banco vinculado dentro de transação encerrada em ROLLBACK.
- Cálculo verificado com serviço 100,00 + 12 km × 2,00 = PitTec 124,00; adicional 49,60 + taxa fixa 5,00 = total 178,60.
- Ordem de pagamento configurada separadamente e sorteada entre R$ 1,00 e R$ 2,00, mantendo o total comercial congelado.
- Teste da função de preço gerou valores como R$ 1,03, R$ 1,37, R$ 1,83 e R$ 1,95, todos em centavos dentro da faixa configurada.
- Migração `20260923000000_pittec_pricing_distance_and_total.sql` publicada no projeto Supabase vinculado.
- Função `mp-create-payment` publicada no projeto Supabase vinculado.
- Verificação remota: tarifa R$ 2,00/km, modo de teste ativo, ordem R$ 1,00 e cálculo de 100,00 + 12 km = PitTec 124,00; com taxa remota atual de R$ 0,00, total comercial R$ 173,60.
- APK instalado e aberto com sucesso nos quatro dispositivos ADB conectados: ASUS, A70, A33 e A32.
- Leitura direta de appointments bloqueada para authenticated; snapshot do cliente sem componentes; snapshot do PitTec sem total do cliente.
- Início sem chegada recusado; chegada sem pagamento recusada.
- Aprovação financeira simulada apenas dentro da transação libera ACCEPTED; chegada registra horário mantendo ACCEPTED; início registra horário e IN_PROGRESS.
- Repetições de início e reconhecimento do cliente são idempotentes.
- Nenhuma cobrança Mercado Pago foi criada por essa validação; todos os registros de validação foram revertidos.

## Pendente

- Validar interação nos aparelhos e pagamento Pix real, movimentação física, retomada offline e cancelamento/conclusão.

O build local está em `app/build/outputs/apk/debug/app-debug.apk`. Não há alteração permanente no banco remoto nesta etapa.
