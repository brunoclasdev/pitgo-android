# Contract: Map UI and Navigation

## Discovery route

```kotlin
ServiceMapRoute(
    serviceId: String,
    onBack: () -> Unit,
    onRequestService: (providerId: String, serviceId: String) -> Unit,
    onSessionExpired: () -> Unit,
)
```

- `serviceId` é UUID opaco validado antes do RPC.
- Rota não recebe nome, preço, categoria, owner ou coordenadas.
- Repository mantém `discover_service_providers` e rejeita coordenadas inválidas.
- Cadastro empresarial não instancia MapLibre nem apresenta mapa.

## Public result

UI recebe somente providerId, nome público, modalidade, coordenada do endereço, distância e
estimativa. CNPJ, contato e localização móvel privada são proibidos.

## Map/list parity

- Mapa e Lista usam mesma coleção e seleção.
- Lista contém 100% dos resultados, inclusive clusters/grupos coincidentes.
- Recusa de localização preserva busca por viewport/região.
- MapLibre MapView é uma região semântica; lista/sheet são superfícies TalkBack autoritativas.

## GeoJSON interaction

- Source de providers agrupa pontos próximos até clusterMaxZoom.
- Coordenadas exatamente iguais são pré-agrupadas por `groupKey` estável.
- Toque consulta apenas layers da aplicação em área equivalente a alvo >=48dp.
- Provider individual abre resumo Compose.
- Cluster próximo centraliza e aumenta zoom; não usa APIs de leaves/expansion sobre source mutável.
- Grupo coincidente abre lista Compose com todos os IDs.
- Nenhum provider é removido do modelo por clustering.

## Provider summary and request

Resumo mostra nome, modalidade localizada, distância/estimativa e CTA
`SOLICITAR ATENDIMENTO`, sem dados privados. Em compacto usa sheet rolável; em expandido pode usar
painel lateral.

CTA navega somente com IDs:

```text
request-service?serviceId={serviceId}&providerId={providerId}
```

IDs são URI-encoded e resolvidos no destino. RequestService carrega serviço do catálogo, preserva
providerId no callback mockado e mostra estado inválido sem fallback demo quando necessário.

## Discovery states

| Estado | UI | Recuperação |
|--------|----|------------|
| Data Loading | progresso; resultado anterior pode permanecer | aguardar/voltar |
| Ready | mapa/lista/contagem | buscar nesta área |
| Empty | nenhum provider | alterar região |
| Offline | banner e lista/cache anterior | retry/ver lista |
| Data Failure | mensagem genérica | retry |
| Style Failed | lista e seleção preservadas | recarregar mapa |
| Resource Degraded | mapa possível + banner | retry/ver lista |
| Location Denied | explicação sem bloqueio | região/configurações |
| Session Expired | limpa dados privados | gate central |

Generation token impede resposta de busca anterior de sobrescrever viewport/serviço atual.

## Appointment tracking

```kotlin
AppointmentTrackingRoute(
    appointmentId: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
)
```

- child screen com TopAppBar e sem bottom navigation;
- uma source GeoJSON não clusterizada e um marker estável;
- nova posição aceita atualiza apenas source;
- câmera só acompanha enquanto “Seguir prestador” estiver ativo;
- live: `Ao vivo · Atualizado às HH:mm:ss`;
- stale: mantém marker e mostra `Localização desatualizada · Última atualização às HH:mm:ss`;
- tile/style failure não apaga posição nem texto;
- Ended/session expiry remove imediatamente a feature privada.

## Responsive/accessibility

- controles >=48dp e CTA >=56dp;
- fonte 200% força painel único, sem altura fixa;
- texto quebra linha e sheet é rolável;
- seleção/live/stale usam forma, ícone e texto além de cor;
- attribution permanece visível/clicável;
- updates de 10 s não geram anúncios TalkBack repetitivos.
