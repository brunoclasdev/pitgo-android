# Data Model: Pix e cancelamento do pagamento

## Payment

Pagamento associado a uma solicitação PitTec e a uma única tentativa de cobrança.

| Campo | Regra para esta feature |
|---|---|
| `id` | Identificador único da tentativa |
| `request_id` | Solicitação PitTec relacionada |
| `method` | `PIX` ou `SAVED_CARD` |
| `amount` | Deve ser exatamente `1.00` durante esta feature |
| `currency` | `BRL` |
| `status` | `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`, `CANCELLED`, `REFUND_PENDING`, `REFUNDED` ou `REFUND_FAILED` |
| `sync_state` | `CREATING`, `VERIFYING` ou `CURRENT` |
| `cancel_requested_at` | Preenchido quando o cliente solicita cancelamento |
| `refund_submission_state` | Controla tentativa, incerteza, falha e conclusão do reembolso |
| `pix_qr_code` | Copia e Cola; obrigatório junto com os demais dados para Pix pronto |
| `pix_qr_code_base64` | Imagem do QR; obrigatório junto com os demais dados para Pix pronto |
| `pix_expires_at` | Prazo do código Pix |
| `revision` | Impede aplicar snapshot antigo sobre estado novo |

## ServiceSearch / Solicitação PitTec

A solicitação do cliente. O cancelamento confirmado antes da aprovação deve mudar o status para `CANCELLED`, limpar a disponibilidade operacional associada e removê-la da lista de solicitações ativas.

## Appointment / Atendimento PitTec

Vínculo criado após o aceite do prestador. Deve permanecer sem início enquanto o pagamento não estiver `APPROVED`. O cancelamento do cliente não pode apagar o histórico de pagamento nem marcar um pagamento aprovado como não realizado.

## PixCharge

Objeto de apresentação derivado do pagamento:

- `qr_code`: texto copiável não vazio.
- `qr_code_base64`: base64 não vazio para renderização.
- `expires_at`: data futura quando fornecida.

O objeto só é considerado disponível quando os três campos necessários ao contrato estiverem presentes. A tela deve mostrar erro acionável quando qualquer dado obrigatório estiver ausente.

## State transitions

```text
PENDING + Pix disponível -> tela Pix
PENDING + Pix incompleto -> VERIFYING/erro recuperável
PENDING + cancelamento -> payment CANCELLED; request CANCELLED
PENDING + expiração -> payment EXPIRED; request encerrada conforme expiração
APPROVED + cancelamento -> REFUND_PENDING -> REFUNDED | REFUND_FAILED
APPROVED + sem cancelamento -> atendimento liberado
```

Regras de concorrência:

- Uma solicitação deve ter no máximo uma operação de cancelamento efetiva.
- Uma tentativa de pagamento deve ser protegida por idempotência e revisão persistida.
- Um snapshot mais antigo não pode substituir um estado mais novo.
