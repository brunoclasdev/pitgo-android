# Contratos de serviço e UI: Guincho

Contrato proposto para a implementação036, não descrição de endpoints já implantados. RPCs usam Supabase/PostgREST autenticado; quote usa Edge Function existente.

## Catálogo e cadastros

- Nova `get_guincho_transport_catalog()` → `{sizes:[{id,code,name,revision,vehicle_category_ids}],vehicle_categories:[{id,code,name}]}` com itens ativos. Não retorna regras administrativas ou driver price.
- `create_provider_registration(p_provider,p_address,p_offers)` mantém assinatura e inclui `p_provider.vehicle.guincho_size_id`; servidor valida porte ativo e seleciona o veículo inicial como dispatch_vehicle_id.
- Para prestador legado sem nenhum veículo cadastrado, `register_my_guincho_vehicle(p_request_id,p_plate,p_make,p_model,p_color,p_size_id)` cadastra o primeiro veículo próprio, com campos obrigatórios, porte ativo e idempotência, selecionando-o inicialmente. A regularização fica acessível no perfil GUINCHO.
- Nova `set_my_guincho_vehicle(p_vehicle_id,p_size_id,p_expected_revision,p_request_id)` permite ao proprietário classificar veículo existente; validar revisão e posse, sem permitir alterar veículo alheio. Edição dos demais dados continua no fluxo de cadastro/perfil existente; se não houver editor, expor editor mínimo de veículo nesse fluxo.
- Nova `select_my_guincho_dispatch_vehicle(p_vehicle_id,p_request_id)` escolhe veículo próprio classificado para a oferta atual. Sem escolha válida, provider não é descoberto.
- Estender DTOs e RPCs existentes de criação/edição de `vehicles` com `transport_category_id`, preservando chamadas de outras modalidades; busca de guincho exige categoria. Não inferir categoria a partir de modelo textual.

## Descoberta, cotação e contratação

- `save_guincho_trip_draft(...)`: manter argumentos atuais; criar/atualizar contexto do cliente e a geração quando os dados confirmados mudarem. Retornar draft_id/revision existentes e search_context_id/search_generation aditivos. Mesmo input não limpa exclusões.
- `discover_guincho_providers(p_draft_id,p_expected_revision,p_limit=200)`: manter assinatura e campos atuais, acrescentando dispatch_vehicle_id, size_id e size_name. Um provider por resultado; filtrar no servidor por veículo escolhido, categoria, compatibilidade, exclusões da geração, preço vigente e critérios existentes de disponibilidade/localização. Atualizar definição SQL de forma compatível (RETURNS TABLE pode exigir drop/recreate e restauração dos grants).
- `POST functions/v1/guincho-quote`: corpo atual `{draft_id,input_revision,provider_id}`; não aceitar preço do app. Manter cálculo de rota existente. `store_guincho_quote` resolve veículo de atendimento, compatibilidade e preço do porte em transação e captura snapshot.
- Quote mantém quote_id/version/total/duração/moeda/expiração atuais e acrescenta identificação do veículo de atendimento, porte e versão de preço do porte. Valores monetários serializados como decimal, não float.
- `create_guincho_request_before_payment(p_quote_id,p_operation_id)` é o caminho Android atual; `create_guincho_request(...)` legado também recebe os guards de capacidade e exclusão. Confirmar cotação/revisão, posse e disponibilidade sem recalcular valores de proposta vigente. Não alterar aceite antes do pagamento.

## Recusa e recuperação

- `respond_guincho_request(...)`: preservar argumentos e resposta existentes. Ao recusar, inserir exclusão e tornar contexto retomável atomicamente com cancelamento; usar o motivo existente `PROVIDER_DECLINED`. Repetição não duplica cancelamento, exclusão ou reembolso. Verificar os triggers/workers financeiros existentes: `private.cancel_guincho_appointment` isoladamente não garante enqueue de reembolso. Nova contratação cria outro pedido, preservando o cancelado no histórico.
- Para pedido criado antes da implantação, sem contexto persistente, a recusa deve criar o contexto uma única vez a partir do rascunho/cotação vinculados ao appointment e pertencentes ao mesmo cliente. Usar lock e identidade do pedido para retries; preservar dados da viagem e snapshots financeiros. Não substituir contexto mais recente ou atendimento operacional novo.
- Cancelamento voluntário, expiração e demais encerramentos definitivos diferentes de recusa fecham somente o contexto ligado ao pedido correspondente, sem registrar exclusão ou disparar retorno por recusa. Preservar comportamento operacional/financeiro existente e ignorar eventos antigos para outros contextos.
- Nova `get_my_guincho_search_resume()` sem client_id fornecido pelo chamador. Retorna união:
  - `{outcome:"ACTIVE_DISPATCH", ...snapshot existente}` quando há atendimento operacional atual;
  - `{outcome:"RESUMABLE_SEARCH", context_id, generation, revision, draft:{id,revision,vehicle_id,pickup_address,pickup_latitude,pickup_longitude,destination_address,destination_latitude,destination_longitude}, excluded_provider_ids, rejected_appointment_id}`;
  - `{outcome:"NONE"}` se não há busca nem atendimento a recuperar.
- A causa explícita da recusa precisa estar na projeção de tracking/retomada autorizada. Não tratar todo CANCELLED como recusa; não retornar ao mapa ao consultar histórico. Expor apenas IDs/contexto do cliente autenticado.
- Guarda de idempotência na navegação por appointment/context/generation; substituir tracking atual pela busca, remover checkout/quote antigos, impedir loop de restauração. Priorizar ACTIVE_DISPATCH sobre recusa antiga.
- Recuperação/reconciliação reutiliza lifecycle, observadores e conectividade atuais. Não precisa aguardar reembolso para apresentar busca; acompanhamento financeiro continua no histórico atual.

## Falhas estáveis

Preservar mapeamentos atuais e acrescentar `VEHICLE_CATEGORY_REQUIRED`, `GUINCHO_SIZE_REQUIRED`, `INCOMPATIBLE_VEHICLE`, `PROVIDER_EXCLUDED`, `CATALOG_CONFLICT` e `NO_SEARCH_CONTEXT`. Autorização: `CLIENT_REQUIRED`, `PROVIDER_REQUIRED`, `DRAFT_UNAVAILABLE`; conflitos: `REVISION_CONFLICT`, `QUOTE_CONFLICT`; preço: `PRICING_UNAVAILABLE`; disponibilidade: `PROVIDER_UNAVAILABLE`. Edge Function deve mapear os novos erros explicitamente, sem convertê-los todos em SERVICE_UNAVAILABLE.

UI usa mensagens traduzidas e ações: completar cadastro, atualizar busca, escolher outro prestador, tentar novamente, renovar sessão. Offline preserva dados e informa pendência; vazio não é falha genérica.

## Contrato visual

Destino: card no topo abaixo do TopAppBar, dentro de insets e imePadding, com altura adaptável e rolagem interna existente. Controles de localização/câmera não podem ficar escondidos sob o card. Status da home: primeiro card acima do mapa, com textos de disponibilidade e switch existentes. Bordas de todos os cards das duas telas seguem token roxo e largura existente; não alterar o posicionamento global do componente compartilhado. Validar fonte ampliada, claro/escuro e teclado aberto.
