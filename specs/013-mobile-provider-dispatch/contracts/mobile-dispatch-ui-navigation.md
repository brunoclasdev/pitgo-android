# Contract: UI e navegação do despacho móvel

## Entrada por catálogo

- `HomeService.dispatchKind == MOBILE_SEQUENTIAL` → `mobile-dispatch/new/{serviceId}`.
- `BROWSE` → `service-map/{serviceId}`, preservando providers fixos.
- A rota `new` cria/restaura a busca e é substituída por `mobile-dispatch/{searchId}`.
- Incoming request abre `provider-request/{appointmentId}`.
- Argumentos são somente IDs opacos; preço, owner, coordenadas e status vêm do snapshot.

## Estados do cliente

| Estado | Conteúdo/Ação |
|--------|---------------|
| Restoring | progresso textual; sem Home prematura |
| LocationRequired | propósito, solicitar novamente, resolver Settings |
| SEARCHING | “Buscando prestador”, raio confirmado e cancelamento explícito |
| OPTION_FOUND | proposta completa, carro no mapa, “Solicitar este guincho” e “Próximo” |
| AWAITING_PROVIDER | preço garantido, countdown visual e cancelamento explícito |
| ACCEPTED | confirmação e acesso ao tracking autorizado |
| IN_PROGRESS | status, tracking e concluir/cancelar conforme ações permitidas |
| EXHAUSTED | vazio após 50 km, “Buscar novamente” |
| CANCELLED/COMPLETED | confirmação terminal e retorno seguro |
| Offline/Failure | último snapshot preservado e retry idempotente |
| SessionExpired | dados limpos antes do refresh/login |

Recusa, expiração e cancelamento pelo provider aparecem como notice persistido antes de voltar a
SEARCHING/OPTION_FOUND. “Próximo” representa decisão do cliente e não usa o mesmo texto/status.

## Proposta

O card textual é a fonte acessível e contém:

- nome público da empresa;
- preço exibido; após request, rótulo “Preço garantido nesta solicitação”;
- localidade pública aproximada (cidade/UF do cadastro empresarial), enquanto distância e marcador
  usam exclusivamente a posição móvel corrente;
- distância estimada;
- status e ações permitidas pelo snapshot.

Não mostra CNPJ, telefone, endereço completo, histórico ou dados de outras solicitações. Enquanto
uma mutation está pendente, ambas as ações conflitantes ficam desabilitadas; falha mantém a proposta
e permite repetir a mesma intenção.

## Provider

Dashboard mostra estados `Indisponível`, `Iniciando`, `Publicando`, `Degradado` e `Reservado`.
Incoming request tem destaque acima do conteúdo comum e destino dedicado com preço, serviço,
localidade segura, distância, deadline e ações Aceitar/Recusar. Recusa pré-aceite não exige motivo;
cancelamento pós-aceite exige.

Notificação abre o mesmo destino. Aceitar/recusar não dispara duas vezes e o resultado confirmado
determina navegação para tracking ou Dashboard.

## Cancelamento

Após aceite, cliente e provider abrem uma `ModalBottomSheet` rolável com motivo multiline
obrigatório, erro inline e confirmação. Com fonte 200%/IME, conteúdo permanece alcançável.
Back/ação da notificação não contornam o motivo. Cancelamento do provider retoma busca; do cliente
encerra.

## MapLibre e acessibilidade

- `MapMarkerKind.MOBILE_PROVIDER` usa drawable de carro; seleção altera forma/contorno além de cor.
- Provider FIXED mantém `ADDRESS`.
- Tela de proposta mostra um único carro e, opcionalmente, referência do cliente; não há clustering.
- Controller atualiza `GeoJsonSource.setGeoJson`, sem recriar MapView/style nem recentralizar após
  gesto salvo.
- Mapa é uma região semântica; proposta/status/actions fora dele contêm toda informação essencial.
- Controles têm alvo >=48dp; ação primária >=56dp; estados relevantes usam live region polite, mas
  countdown e updates de 10 s não são anunciados continuamente.
- Compacto/fonte alta usa `LazyColumn` de uma coluna e mapa colapsável; largura >=840dp com
  fontScale <1.5 pode usar mapa + painel. Sem bottom navigation no fluxo ativo.

## Retomada e back stack

Após autenticação, `DispatchResumeViewModel` consulta o contexto ativo antes de estabilizar Home ou
Dashboard:

```text
None -> raiz comum da conta
ClientSearch(searchId) -> mobile-dispatch/{searchId}
ClientTracking(appointmentId) -> appointment/{appointmentId}/tracking
ProviderIncoming(appointmentId) -> provider-request/{appointmentId}
ProviderTracking(appointmentId) -> provider-tracking/{appointmentId}
```

Não roubar navegação quando o usuário já está numa rota relacionada. Mesmo usuário reautenticado
restaura snapshot; troca/logout cancela jobs/canais, apaga proposta/coordenadas e nunca reaproveita
ID anterior. Back não cancela silenciosamente uma busca: informa que ela continua recuperável ou
oferece o cancelamento explícito permitido.
