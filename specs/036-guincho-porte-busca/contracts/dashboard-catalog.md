# Contrato administrativo: catálogo, compatibilidade e preço por porte

Interface a implementar no dashboard externo, cujo frontend não está neste repositório. Nomes RPC novos são propostos; são parte da entrega backend, não APIs já publicadas.

## Acesso

Escritas e leitura administrativa exigem `private.dashboard_access_at_least('MANAGER')`, sem confiar em flags no payload. Reutilizar `record_dashboard_action`, registrando ator, alvo, versão e ação sem dados pessoais desnecessários. Revogar escrita direta em tabelas para usuários móveis e anon; RPCs com search_path vazio e grants explícitos.

## RPCs propostos

| RPC | Entrada | Resultado |
|---|---|---|
| `get_dashboard_guincho_catalog` | sem argumentos | Categorias e portes, ativos/inativos, revisões, compatibilidades e preços vigentes; histórico financeiro continua via leitura administrativa existente. |
| `set_dashboard_vehicle_transport_category` | p_request_id, p_category_id opcional, p_expected_revision opcional, p_config:{code,name,active} | Categoria criada/atualizada e revisão. |
| `set_dashboard_guincho_size` | p_request_id, p_size_id opcional, p_expected_revision opcional, p_config:{code,name,active,vehicle_category_ids} | Porte e conjunto completo de compatibilidades, salvos atomicamente com revisão. |
| `set_dashboard_guincho_size_price` | p_request_id, p_size_id, p_expected_version opcional, p_base_amount | Nova versão de preço ativa imediatamente, aposentando a anterior do mesmo porte; BRL. |

Atualização exige revisão/versão esperada; nulo somente em criação. Retry com mesmo request_id/payload retorna resultado anterior; payload diferente com mesmo ID retorna IDEMPOTENCY_CONFLICT. Uma transação inválida não altera o catálogo/preço vigente. Atualizações simultâneas usam lock por entidade e verificação de revisão.

Não remover fisicamente entidades referenciadas. Desativação afeta novas descobertas/cotações, preserva histórico e pedidos já confirmados; antes de reserva, revalidar capacidade/ativação sem refazer o preço já cotado. Categorias/portes usados por registros legados não devem receber atribuição automática por nome.

## Regras de entrada

- code único e estável; nome aparado não vazio; IDs de categorias existentes e ativos para ativar novas relações.
- Porte ativo exige ao menos uma categoria compatível. Ativar porte sem preço pode ser permitido para preparação operacional, mas não gera oferta contratável até existir preço e regra comum vigentes.
- Preço base > 0, duas casas, dentro da precisão monetária existente. Rejeitar negativo, zero e excesso de precisão; não arredondar silenciosamente valor administrativo inválido.
- Componentes comuns continuam em `set_dashboard_guincho_pricing`; seu base_amount legado não será fallback de novas ofertas por porte. `get_current_service_pricing` deve deixar explícito que guincho é por porte e não apresentar preço global como oferta contratável; manter compatibilidade para PitTec.
- Sem alteração de taxas/duração/remuneração por porte nesta feature. Preserve guincheiro_price reservado fora do cálculo.

## Comportamento da interface externa

O gestor pode listar/criar/editar categorias, portes, compatibilidades e preços. Formulário de porte apresenta seleção múltipla de categorias transportáveis e o preço atual; confirmar gravação e sinalizar alterações concorrentes. Mostrar item sem tarifa como não ofertável. Micro-ônibus deve poder ser associado explicitamente a portes aprovados pela operação.

Não preencher preços/capacidades fictícios para viabilizar lançamento. Antes da liberação, operação precisa cadastrar matriz real e valores e classificar cadastros existentes.

## Erros e validação de acesso

`STAFF_REQUIRED` (42501), `INVALID_CATALOG`/`INVALID_PRICING`/`IDEMPOTENCY_CONFLICT` (22023), `CATALOG_NOT_FOUND` (P0002), `CATALOG_CONFLICT`/`PRICING_CONFLICT` (40001). Exibir mensagem acionável sem SQL bruto. Validar MANAGER e negar anon, cliente, prestador e staff abaixo do nível necessário, inclusive via chamada direta.

## Pré-requisito da integração externa (T031)

Repositório, caminhos reais da interface/cliente RPC e responsável ainda não foram identificados neste workspace. Registrar esses dados aqui quando disponíveis, antes de executar a integração. Não usar caminhos fictícios nem considerar registro documental como implementação da UI. T031 permanece pendente até a integração efetiva e T033 até sua validação fim a fim; tarefas locais independentes podem prosseguir.

## Limite de entrega

RPCs e contratos deste repositório viabilizam a integração, mas não substituem a interface administrativa. A validação fim a fim da feature exige o formulário do dashboard externo consumindo essas operações; registrar a integração como dependência explícita ao gerar tarefas.
