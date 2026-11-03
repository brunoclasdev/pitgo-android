# Research: Guincho por porte e retomada da busca

**Date**: 2026-11-03. Pesquisa baseada no código local; sem troca de stack ou atualização de bibliotecas.

## 1. Classificação de veículos e capacidade

**Decision**: Adicionar catálogo administrativo de tipos/portes e categorias transportáveis com IDs estáveis. Categorias de veículos do cliente são referenciadas explicitamente; relação N:N determina compatibilidade. Administração usa dashboard; o app lê catálogo ativo e salva IDs, não nomes livres.

**Rationale**: `core/vehicle/model/VehicleModels.kt` não possui categoria; `20261023102000_provider_vehicles_registration.sql` contém placa, marca, modelo e cor sem porte. A busca atual não pode determinar capacidade com esses campos. Exigir classificação no fluxo de guincho sem quebrar cadastros PitTec/loja. Registros legados ficam sem oferta até regularização.

**Alternatives considered**: Inferir capacidade pela marca/modelo ou por ordem leve/médio/pesado cria falso positivo; enum fixo contradiz decisão de administração no dashboard. Não inventar limites de peso ou catálogo comercial de produção.

## 2. Identidade do guincho oferecido

**Decision**: Associar `dispatch_vehicle_id` ao provider GUINCHO, referenciando veículo próprio com porte válido. Cadastro inicial seleciona o primeiro veículo informado; edição permite escolher o veículo efetivamente em serviço. Uma oferta por provider, consistente com marcadores e API atuais.

**Rationale**: `discover_guincho_providers` retorna uma linha por provider; não há identidade de veículo na cotação. Deve existir uma escolha explícita quando houver frota, com snapshot na cotação/pedido e revalidação antes da reserva. Não ampliar para despacho simultâneo de vários guinchos da mesma conta.

**Alternatives considered**: Usar maior porte da frota viola a compatibilidade da oferta; oferecer toda a frota altera identidade de seleção e disponibilidade além do escopo.

## 3. Fonte do preço e snapshots

**Decision**: Manter `guincho_pricing_rules` para distância/percentuais/taxas/duração e adicionar preço base versionado por porte. Uma cotação captura ambas as versões e os valores calculados. O base_amount global antigo fica apenas para compatibilidade histórica; novas cotações por porte não fazem fallback.

**Rationale**: `20261023110000_central_provider_pricing_schema.sql` já oferece gestão MANAGER auditada e `20261023112000_central_guincho_quote_pricing.sql` centraliza o cálculo. A única mudança comercial requerida é o base_amount por porte. `guincheiro_price` continua reservado, fora do cálculo.

**Alternatives considered**: Duplicar todas as taxas por porte cria configurações não solicitadas; preço livre do prestador contraria a spec035; recotar compulsoriamente com nova tarifa viola cotações vigentes.

## 4. Aceite antes do pagamento e autoridade de reserva

**Decision**: Preservar `create_guincho_request_before_payment` usado por `GuinchoRepository.reserve`, atualizar também `create_guincho_request` legado e o predicado de `store_guincho_quote`. Validar propriedade, porte, categoria, exclusão, disponibilidade e revisão no servidor.

**Rationale**: `20260930154000_guincho_accept_before_payment.sql` introduziu aceite antes de pagamento; a migração de preços posterior redefine o caminho legado. Atualizar somente o legado deixa o caminho real vulnerável a divergências. O endpoint `supabase/functions/guincho-quote/index.ts` autentica o cliente e delega armazenamento à função SQL, ponto adequado para enforcement definitivo.

**Alternatives considered**: Reordenar pagamento amplia o escopo; filtrar somente no mapa permite contratação direta de opção incompatível.

## 5. Recusa e exclusões persistentes

**Decision**: Persistir um contexto de busca por cliente, ligado ao rascunho, e exclusões vinculadas à geração dos dados da viagem. Em `respond_guincho_request` com recusa, registrar exclusão e disponibilizar retomada na mesma transação de cancelamento. Manter histórico financeiro separado do contexto retomável.

**Rationale**: `save_guincho_trip_draft` incrementa revision mesmo em salvamentos idênticos. Exclusões não podem ser vinculadas somente a essa revision. Usar geração própria incrementada apenas quando o veículo ou os endereços/pontos confirmados mudarem; normalização de espaços não é mudança de viagem, nem jitter de GPS sem nova confirmação. Manter exclusões ao repetir refresh/salvar/reabrir. Mudança de um dado confirmado inicia nova geração e encerra exclusões anteriores.

**Rationale adicional**: `private.cancel_guincho_appointment` encerra o atendimento, mas não comprova sozinho o agendamento de reembolso. Na implementação, rastrear os triggers/worker de pagamentos e verificar que recusa paga legada efetivamente dispara o reembolso uma única vez, corrigindo a ligação se faltar. Não reutilizar o service_searches cancelado como nova busca financeira.

**Alternatives considered**: Lista só em memória falha após reabertura; deduzir a recusa de qualquer CANCELLED navega indevidamente; procurar indefinidamente todos os pedidos iguais pode transformar exclusão da busca em bloqueio permanente.

## 6. Navegação e restauração

**Decision**: Expor recuperação com prioridade ACTIVE_DISPATCH > RESUMABLE_SEARCH > NONE. Carregar contexto do servidor ao abrir mapa, reconciliar após recusa e reconexão, e navegar só para recusa explícita do pedido atual, em modo cliente e fora do histórico.

**Rationale**: `PitGoNavHost.kt` remove a rota de mapa em alguns redirecionamentos; `AppointmentTrackingScreen.kt` trata conclusão e cancelamento após contestação, sem retorno específico por recusa. `GuinchoMapViewModel` mantém viagem em StateFlow e SavedStateHandle apenas para idempotência de contratação; isso não recupera viagem após encerramento frio.

**Alternatives considered**: Apenas popBackStack não garante mapa nem dados; evento efêmero sem consulta persistente perde a recusa com app fechado. Reutilizar canais de atualização existentes, sem criar serviço de polling novo.

## 7. Layout e administração externa

**Decision**: Reutilizar `core/ui/AddressEntryMapCard.kt`, que já usa borda primária de 1,5dp, mudando somente o posicionamento no mapa de guincho. Status vira primeiro card da home, antes do mapa; adaptar demais cards no escopo com tokens existentes.

**Rationale**: `GuinchoMapScreen.kt` alinha o card em BottomCenter; `GuinchoProviderHomeScreen.kt` mostra disponibilidade abaixo do mapa. O repositório e o plano035 confirmam ausência do frontend administrativo. Definir integração por RPC e guia para a interface externa, sem afirmar que essa interface já foi entregue.

**Alternatives considered**: Novo design system ou painel móvel aumenta escopo; editar globalmente o card compartilhado para mover todas as telas alteraria outras modalidades.

## Resultado

Sem decisões técnicas abertas para decomposição em tarefas. Dados comerciais reais (portes, compatibilidades e valores) serão fornecidos pela operação no dashboard antes da liberação. A interface externa é uma dependência explícita de entrega, não um artefato presente neste repositório.

## Inspeção de implementação

`respond_guincho_request` cancela por PROVIDER_DECLINED via `private.cancel_guincho_appointment`, que encerra appointment/search e remove localizações. A reconciliação financeira trata aprovação tardia em pedido cancelado como REFUND_PENDING, mas a recusa de pagamento já aprovado exige encaminhamento explícito ao worker (`work_action=REFUND`, `next_action_at`), preservando o status financeiro e a idempotência. Reservas atuais devem partir da definição central mais recente de `create_guincho_request`, preservando a variante antes do pagamento.

### Ajustes confirmados durante implementação

- As funções atuais de descoberta e snapshot têm wrappers de avaliação (migration `20261022100000_service_ratings.sql`). Extensões de filtros/causa de recusa devem atuar em `private.discover_guincho_providers_before_ratings` e `private.guincho_snapshot_before_ratings`; a projeção pública mantém avaliações e acrescenta veículo/porte.
- Cadastro de veículo do onboarding reutiliza a rota/formulário da garagem; não foi criado formulário duplicado.
- Regularização inclui prestador legado sem nenhum `provider_vehicles`: `register_my_guincho_vehicle` permite cadastro owner-scoped e idempotente do primeiro veículo, sem inferir capacidade.
- Reserva e pagamento verificam referências imutáveis de preço, inclusive a simulação existente. Cotação/reserva mantêm locks de provider, veículo, categoria e porte; não refazem valores de uma cotação vigente após atualização de tarifa.
- Retorno automático por recusa consulta o contexto autoritativo e consome o evento na sessão, evitando loop ao voltar à home. Reabertura fria recupera o contexto remoto; atendimento de outra modalidade impede a retomada de uma busca antiga.
