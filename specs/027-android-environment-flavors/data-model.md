# Data Model: Android Environment Flavors

## EnvironmentConfig

Representa a configuração client-safe imutável da variante gerada.

| Campo | Tipo | Regra |
|---|---|---|
| `id` | `EnvironmentId` | Exatamente `DEV`, `HOMOL` ou `PROD`; vem do flavor, nunca da UI. |
| `supabaseProjectRef` | `String` | Projeto esperado; deve corresponder ao host canônico do endpoint. Dev é separado; Homol e Prod usam o mesmo projeto. |
| `label` | `String` | Rótulo seguro para UI/log sanitizado. |
| `supabaseUrl` | `String` | HTTPS absoluto, host válido e backend correspondente ao ambiente. |
| `supabaseKey` | `String` | Publishable/anon key client-safe; service-role é rejeitada. |
| `mapStyleUrl` | `String` | Configuração já exigida pelo app, validada no mesmo arquivo. |
| `mercadoPagoPublicKey` | `String?` | Obrigatória somente para `HOMOL` e `PROD`; nunca é Access Token. |
| `paymentMode` | `PaymentMode` | `SIMULATED` para `DEV`, `INTEGRATED` para `HOMOL` e `PROD`. |
| `fingerprint` | `String` | Identificador estável usado para detectar troca de instalação. |

## EnvironmentId

Enum fechado: `DEV`, `HOMOL`, `PROD`. Não deve ser criado a partir de string de navegação ou preferência editável.

## PaymentMode

Enum fechado:

- `SIMULATED`: não chama Mercado Pago, Edge Functions de pagamento, RPC financeiro ou Realtime financeiro externo.
- `INTEGRATED`: usa o caminho atual de pagamento e o backend Supabase associado ao flavor.

## PaymentGateway

Contrato de domínio usado pelo checkout e pelo fluxo PitTec. Deve cobrir as operações que hoje pertencem ao `PaymentRepository`: criação, consulta de status, observação de eventos, cancelamento, reembolso e recuperação. A assinatura deve manter os modelos e estados existentes para não alterar decisões de UI fora do escopo.

Implementações:

- `IntegratedPaymentGateway`: delega para `PaymentRepository` e é permitido somente em `HOMOL`/`PROD`.
- `SimulatedPaymentGateway`: produz tentativa local com identificador de teste e transições determinísticas de `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED` e `CANCELLED`, sem persistir como pagamento real.

## EnvironmentState

Metadado local com `activeFingerprint`. Deve ser mantido em DataStore separado do domínio financeiro.

### Transições

1. Primeiro início em instalação limpa: inicializa o escopo e grava o fingerprint. Se houver dados legados sem fingerprint, limpa/invalida esses dados antes de registrar a identidade.
2. Mesmo fingerprint: preserva estado compatível e inicia a sessão normalmente.
3. Fingerprint diferente: executa limpeza/invalidação, grava o novo fingerprint e só então permite a sessão.
4. Configuração inválida: não cria cliente Supabase nem inicia operação financeira.

## Relações

```text
EnvironmentConfig 1 ── 1 PaymentMode
EnvironmentConfig 1 ── 1 Backend Supabase
PaymentMode 1 ── 1 PaymentGateway
EnvironmentState 1 ── 1 installed EnvironmentConfig fingerprint
```

## SimulatedPitTecFlow

Snapshot local de desenvolvimento com UUID, identificação explícita de simulação e fase tipada. Após `PaymentStatus.APPROVED`, segue `EN_ROUTE` → `IN_PROGRESS` → `COMPLETED`, com opção de cancelamento válida antes da conclusão. O estado é isolado pelo fingerprint e pode ser reiniciado por QA. Não é entidade de atendimento no servidor e não emite mutações de despacho remoto. Cenários pré-carregados locais permitem testar sem criar atendimento remoto. O repository expõe StateFlow ao ViewModel, que renderiza os componentes existentes.

## Segurança e retenção

`EnvironmentConfig` pode conter valores client-safe em `BuildConfig`, mas nunca secrets administrativos. `EnvironmentState` não deve armazenar Access Token, cartão, CVV ou payload financeiro. A limpeza de troca remove sessão, cache de pagamento, onboarding/estado de tracking incompatível e dados locais de recuperação; preferências visuais podem permanecer.
