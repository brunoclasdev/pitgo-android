# Contract: Realtime, localização e recuperação PitTec

## Princípio

Realtime comunica que algo mudou; RPCs devolvem o estado autoritativo. Todo consumidor refaz o
snapshot ao receber uma invalidação, ordena por `revision` e mantém polling enquanto o fluxo não for
terminal. Nenhum payload de broadcast concede autoridade para mudar estado.

## Tópicos

| Tópico | Leitores | Disponibilidade | Conteúdo mínimo |
|---|---|---|---|
| `mobile-dispatch:<request_id>` | cliente dono | da criação ao encerramento | `CHANGED`, `request_id`, `revision` |
| `provider-dispatch:<provider_id>` | PitTec dono | enquanto há oferta/reserva ou mudança relevante | `CHANGED`, `request_id`, `revision` |
| `payment:<request_id>` | cliente dono | durante checkout/recuperação | `CHANGED`, `request_id`, `status`, `updated_at` |
| `appointment-location:<appointment_id>` | cliente e PitTec vinculados | somente `ACCEPTED`/`IN_PROGRESS` | `CHANGED`, participante, versão, timestamp |
| `appointment-route:<appointment_id>` | cliente e PitTec vinculados | somente quando tracking autorizado | invalidação de snapshot opcional |

## Autorização

- Tópicos são privados e a política deriva participante de `auth.uid()`.
- A política de `payment:<request_id>` usa helper privado `SECURITY DEFINER`, com `search_path = ''`,
  execução pública revogada e leitura limitada ao cliente associado; ela não consulta diretamente uma
  tabela inacessível ao papel `authenticated`.
- O PitTec aprende a aprovação por `provider-dispatch`, sem receber dados do cartão, Pix ou payment.
- `appointment-location` e RPCs de localização não incluem
  `AWAITING_CLIENT_CONFIRMATION`/`AWAITING_PAYMENT` nas políticas.
- Coordenadas de discovery continuam aproximadas/privadas conforme o contrato atual e não são
  convertidas em localização de appointment antes do pagamento.

## Emissões obrigatórias

- criação/reagendamento/oferta/recusa/timeout: `mobile-dispatch`; o PitTec candidato recebe
  `provider-dispatch` quando a oferta entra ou sai;
- aceite do PitTec: ambos os tópicos de dispatch, depois do commit;
- confirmação/recusa/timeout do cliente: ambos os tópicos de dispatch, depois do commit;
- mudança persistida de pagamento: `payment`; aprovação reconciliada também emite ambos os tópicos de
  dispatch;
- cancelamento/conclusão: dispatch para participantes e interrupção das emissões de localização;
- nova posição válida após autorização: `appointment-location`.

Eventos só são enviados após a transação que alterou o snapshot. Duplicatas são permitidas; perda de
evento é recuperada pelo polling.

## Recuperação e polling

- Cliente: observa `mobile-dispatch:<request_id>` e chama `get_my_active_pittec_dispatch` a cada cinco
  segundos em `SEARCHING`, `AWAITING_PROVIDER`, `AWAITING_CLIENT_CONFIRMATION`, `AWAITING_PAYMENT`,
  `ACCEPTED` e `IN_PROGRESS` enquanto a tela precisar do estado.
- PitTec: observa `provider-dispatch:<provider_id>` e usa restore compatível com mobile e PitTec; não
  chama somente `restoreMobileDispatch` quando a oferta é PitTec.
- Checkout: observa `payment:<request_id>`, persiste refresh de `PENDING` no backend e volta a buscar o
  snapshot do dispatch após `APPROVED`.
- Tracking: busca imediatamente posições atuais ao entrar, assina o tópico e mantém o mecanismo de
  watchdog/fallback já existente.
- Reabertura sem request em memória restaura a única solicitação ativa; nunca chama start antes de
  concluir restore.

Se WebSocket falhar, a UI informa conectividade sem apagar o último snapshot e o polling continua com
backoff já adotado pelo app. Ao reconectar, uma leitura completa vence qualquer evento armazenado.

## Ordenação e concorrência

- `service_searches.revision` ordena snapshots do fluxo;
- versão/timestamp de `provider_current_locations` ordena posições;
- `quote.version` impede ação sobre preço anterior;
- um evento com revisão menor ou igual à já aplicada pode apenas disparar refetch; nunca reduz estado;
- confirmações, expiração, dois aceites e cancelamento versus webhook são serializados no banco;
- estado terminal é monotônico e não pode ser reativado por mensagem tardia.

## Contrato de atualização de localização

1. O foreground service do PitTec solicita uma leitura precisa aproximadamente a cada 10 segundos,
   mesmo com tela bloqueada, e mantém notificação persistente.
2. Somente leitura válida é publicada; falha conserva a última posição com timestamp.
3. O backend associa a leitura ao appointment somente quando `tracking_authorized = true`.
4. Cliente e PitTec atualizam o marcador e a distância ao receber uma versão maior.
5. Uma posição que excede o limiar de freshness existente aparece como desatualizada; não é
   extrapolada nem substituída por coordenada inventada.
6. Ao cancelar/concluir, novas publicações no contexto desse appointment são rejeitadas e observers
   são encerrados.

Meta observável: em conectividade normal, pelo menos 95% das posições válidas devem estar visíveis ao
outro participante em até 10 segundos. O roteiro mede do `captured_at` aceito no servidor ao estado
renderizado no segundo aparelho.

## Disponibilidade de localização e permissões

- GPS desligado, permissão negada/revogada, leitura inválida e rede indisponível são estados distintos
  no ViewModel e têm mensagem/ação de recuperação.
- O PitTec recebe orientação para restabelecer o foreground tracking quando o lease ou a permissão
  falhar.
- O cliente vê a última posição do PitTec como desatualizada; não é obrigado a conceder localização
  em segundo plano.
- O local fixo continua vindo do ponto original da solicitação; `publish_client_location` não o muda
  para dispatch PitTec.

## Falha do cron

O deploy valida explicitamente a presença dos schedules de avanço e expiração. Se pg_cron estiver
temporariamente indisponível, chamadas de restore/mutation executam avanço/expiração lazy antes de
responder. Essa recuperação mantém integridade; o monitoramento operacional deve sinalizar atraso na
continuidade server-side em vez de transformá-lo em `EXHAUSTED`.
