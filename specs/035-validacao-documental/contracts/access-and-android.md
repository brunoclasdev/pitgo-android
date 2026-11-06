# Contract: Capacidades, navegação e cobertura de bloqueio

## Android

Sessão continua centralizada em SessionRepository. `Authenticated` deve conseguir representar conta restrita e conjunto de capacidades CLIENT/PROVIDER, sem transformar análise documental em falha de autenticação. ViewModels expõem StateFlow com estado imutável. Navegação usa rotas centralizadas para cadastro complementar, captura/revisão de documentos e acompanhamento. Composables apenas mostram estado e emitem eventos; câmera é acionada na camada de UI, processamento de arquivo fica no componente de dados, sem Context retido em ViewModel.

Após signup básico: abrir segunda etapa da modalidade pretendida, sem marcar onboarding de veículo concluído nem abrir Home por sucesso de Auth. Reentrada recupera o case servidor. Antes de modalidade aprovada, permitir apenas completar cadastro, acompanhar/corrigir, atualizar situação, recuperar acesso e sair. Se CLIENT já está aprovado, manter sua navegação e apresentar entrada explícita para cadastro/status do prestador; não trocar o modo para prestador automaticamente durante a análise.

Campos da segunda etapa reutilizam os formulários atuais e persistem rascunho. Pré-requisitos de cadastro (catálogo para escolher ofertas, CEP, geocodificação do endereço cadastral e declaração do veículo) devem ter operações dedicadas autorizadas para onboarding, sem abrir busca comercial, contratação ou disponibilidade. Captura tem slots e feedback acessíveis, progresso, falha e nova tentativa; incluir documento por veículo de atendimento, não por veículo de garagem do cliente.

Atualizar o contexto ao iniciar sessão, retomar aplicativo e após envio/decisão consultada; permitir atualização manual enquanto aguarda. Não fazer polling contínuo nem exigir Realtime para liberar o cadastro. Credenciais válidas com estado indisponível não liberam novas ações de negócio. Serviço continua sendo autoridade mesmo com tela desatualizada.

## Matriz de autorização

| Operação | Modalidade exigida | Condição |
|---|---|---|
| Cadastro complementar e evidências próprias | Membership da modalidade | Autenticado, titular, case e versão editáveis; não requer aprovação. |
| Fila, detalhe e decisão de análise | Staff ativo | MANAGER ou superior; sem autoanálise. |
| Buscar prestadores, solicitar/cotar, contratar e pagar | CLIENT | Capacidade efetiva de cliente. |
| Publicar ofertas, ficar disponível, receber/aceitar e executar serviços | PROVIDER | Capacidade efetiva de prestador e regras operacionais existentes. |
| Seleção de candidatos nas buscas | PROVIDER do candidato | Elegível documentalmente e operacionalmente; pendentes não aparecem. |
| Operação de atendimentos existentes e documentos de vistoria | Participante na modalidade pertinente | Capacidade e vínculo do atendimento; preservar fluxo legado autorizado. |
| Webhook/reconciliação de pagamento | Integração servidor | Assinatura/segredo e vínculo do pagamento atuais; sem exigir JWT do usuário. Não criar nova contratação por conta pendente. |

Capacidade efetiva usa helper privado central `private.can_use_account_mode(user_id, account_type)`. Não expor consulta arbitrária desse helper para enumerar usuários. A função consulta membership, case aprovado ou dispensa explícita e flags operacionais. `private.has_account_type` permanece verificação de pertencimento, necessária para onboarding, sem ser usada sozinha como autorização de negócio.

## Superfícies que a implementação deve cobrir

- `get_my_account_context`, novo contexto v2, carregamento de AccountRepository, `AppSessionState`, `PitGoNavHost`, bootstrap e retomada de dispatch: não navegar para marketplace da modalidade pendente.
- Signup/trigger e `create_provider_registration`: salvar rascunho ou registrar dados operacionais inativos; retirar `set_provider_availability(true)` automático de `ProviderRegistrationRepository.submit`.
- RLS de tabelas expostas por PostgREST: distinguir leitura do próprio onboarding de leitura comercial. Revisar políticas permissivas que, combinadas por OR, possam anular bloqueio.
- Guincho: `discover_guincho_providers`, `save_guincho_trip_draft`, `create_guincho_request_before_payment`, demais comandos de atendimento, funções `guincho-quote`, `guincho-route` e acesso a documentos/alertas do atendimento.
- PitTec/móvel: `list_nearby_pittecs`, início/restauração/aceite de dispatch, `respond_to_pittec_quote`, `start_mobile_dispatch`, `respond_to_mobile_dispatch`, disponibilidade e publicação de serviços.
- Pagamentos: `mp-create-payment`, `mp-save-card`, `mp-remove-card`, cancelamentos/reembolsos iniciados pelo usuário e simulação. Conferir capacidade antes de efeito externo, inclusive se a Edge Function utiliza service role. Não alterar autenticação de webhook/reconciliação nem impedir cumprimento de obrigações já criadas.
- Dashboard: mutações de ativação em `20260928090000_dashboard_pittecs_clientes.sql` e sucessoras; nenhum caminho genérico pode ativar conta nova sem decisão documental.
- Storage: bucket separado, grants mínimos, reservas e políticas restritivas específicas; leitura de documentos pelo staff não libera leitura a outros usuários.

Antes de fechar a implementação, produzir inventário das funções públicas, grants e RLS efetivamente instalados, confrontar com chamadas de Repository e Edge Functions e registrar a verificação na revisão. Os exemplos acima são pontos confirmados no código, não uma lista que autoriza deixar rotas equivalentes sem proteção.

## Compatibilidade e corte

Preparar os instaladores sem habilitar o fluxo antecipadamente. Criar dispensa somente nas modalidades existentes dentro da transação final que troca o trigger e ativa os guards SQL, em `20261105114000_registration_atomic_cutover.sql`. Implantar antes as proteções das Edge Functions: devem consultar o marcador de corte no servidor a cada operação, usar autorização legada antes da ativação e capacidade nova depois, negando em caso de falha na consulta. A transação de corte serializa signup/membership concorrentes; não é suficiente confiar na ordem de migrations aplicadas separadamente. Cliente antigo que inicia PROVIDER recebe exigência de análise. Provider antigo incompleto/inativo não se torna ativo por dispensa. Contexto v1 não promove nova modalidade; app antigo pode exigir atualização para concluir cadastro, mas nunca contornar o servidor.

Nenhuma migration anterior é reescrita. Não aplicar a migration local não versionada de restauração de valor do guincho como parte desta feature. As telas do dashboard pertencem ao projeto administrativo; este repositório entrega o contrato, armazenamento e operações autenticadas necessários à integração.


## Inventário do código para implementação

Autoridade observada no repositório; não representa inspeção de dados remotos. Funções abaixo devem ser classificadas por operação antes de ativar o corte.

| RPC pública | Definição mais recente no repositório |
|---|---|
| `ack_guincho_inspection_push_delivery` | `supabase/migrations/20261021123000_guincho_inspection_push_dispatch.sql` |
| `acknowledge_pittec_start` | `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` |
| `advance_due_pittec_searches` | `supabase/migrations/20260913010000_pittec_call_tracking.sql` |
| `arrive_guincho_appointment` | `supabase/migrations/20260930140000_guincho_transport.sql` |
| `arrive_pittec_appointment` | `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` |
| `begin_guincho_inspection_correction` | `supabase/migrations/20261014141000_guincho_inspection_catalog_guards.sql` |
| `begin_provider_ownership_migration` | `supabase/migrations/20260821000000_account_roles_provider_ownership.sql` |
| `cancel_mobile_appointment` | `supabase/migrations/20260913010000_pittec_call_tracking.sql` |
| `cancel_pittec_search` | `supabase/migrations/20260913010000_pittec_call_tracking.sql` |
| `cancel_unpaid_guincho_request` | `supabase/migrations/20260930146000_guincho_cancel_unpaid_request.sql` |
| `claim_guincho_inspection_push_deliveries` | `supabase/migrations/20261021123000_guincho_inspection_push_dispatch.sql` |
| `claim_guincho_route_refresh` | `supabase/migrations/20261014150000_guincho_route_refresh.sql` |
| `claim_provider_ownership_migration` | `supabase/migrations/20260821000000_account_roles_provider_ownership.sql` |
| `complete_mobile_appointment` | `supabase/migrations/20260930100000_pittec_client_completion_confirmation.sql` |
| `confirm_guincho_completion` | `supabase/migrations/20260930141000_guincho_completion.sql` |
| `confirm_guincho_inspection_photo` | `supabase/migrations/20261014143000_guincho_inspection_commands_v2.sql` |
| `confirm_guincho_inspection_photo_v2` | `supabase/migrations/20261014143000_guincho_inspection_commands_v2.sql` |
| `confirm_pittec_completion` | `supabase/migrations/20260930100000_pittec_client_completion_confirmation.sql` |
| `create_dashboard_staff` | `supabase/migrations/20260928050000_dashboard_staff.sql` |
| `create_guincho_request` | `supabase/migrations/20260930135000_guincho_direct_reservation.sql` |
| `create_provider_geocode_proof` | `supabase/migrations/20260821040000_provider_geocode_proof_rpc.sql` |
| `create_provider_registration` | `supabase/migrations/20260930000000_service_catalog_and_pittec_map_card.sql` |
| `create_service` | `supabase/migrations/20260930000000_service_catalog_and_pittec_map_card.sql` |
| `create_service_category` | `supabase/migrations/20260930000000_service_catalog_and_pittec_map_card.sql` |
| `create_vehicle` | `supabase/migrations/20260823010000_fix_create_vehicle_coalesce.sql` |
| `dashboard_authorize_guincho_inspection_photo` | `supabase/migrations/20261014131000_guincho_inspection_support_reads.sql` |
| `dashboard_cancel_atendimento` | `supabase/migrations/20260928090000_dashboard_pittecs_clientes.sql` |
| `dashboard_get_cliente_detail` | `supabase/migrations/20260928090000_dashboard_pittecs_clientes.sql` |
| `dashboard_get_guincho_dispute` | `supabase/migrations/20260930143000_guincho_support_resolution.sql` |
| `dashboard_get_guincho_inspection` | `supabase/migrations/20261014131000_guincho_inspection_support_reads.sql` |
| `dashboard_get_pagamento_for` | `supabase/migrations/20260928090000_dashboard_pittecs_clientes.sql` |
| `dashboard_get_pittec_detail` | `supabase/migrations/20260929010000_dashboard_pittec_categories.sql` |
| `dashboard_get_pittec_earnings` | `supabase/migrations/20260929050000_dashboard_pittec_earnings_created_at.sql` |
| `dashboard_home_recent_blocks` | `supabase/migrations/20260929070000_dashboard_home_recent_blocks.sql` |
| `dashboard_list_atendimentos_for` | `supabase/migrations/20260928090000_dashboard_pittecs_clientes.sql` |
| `dashboard_list_clientes` | `supabase/migrations/20260928090000_dashboard_pittecs_clientes.sql` |
| `dashboard_list_guincho_disputes` | `supabase/migrations/20260930143000_guincho_support_resolution.sql` |
| `dashboard_list_guincho_inspection_photos` | `supabase/migrations/20261014131000_guincho_inspection_support_reads.sql` |
| `dashboard_list_guincho_inspection_versions` | `supabase/migrations/20261014131000_guincho_inspection_support_reads.sql` |
| `dashboard_list_pittec_earnings_for_period` | `supabase/migrations/20260929060000_dashboard_pittec_earnings_rolling_period.sql` |
| `dashboard_list_pittecs` | `supabase/migrations/20260929010000_dashboard_pittec_categories.sql` |
| `dashboard_refund_pagamento` | `supabase/migrations/20260928090000_dashboard_pittecs_clientes.sql` |
| `dashboard_resolve_guincho_dispute` | `supabase/migrations/20260930143000_guincho_support_resolution.sql` |
| `dashboard_update_cliente_fields` | `supabase/migrations/20260928090000_dashboard_pittecs_clientes.sql` |
| `dashboard_update_pittec_fields` | `supabase/migrations/20260928090000_dashboard_pittecs_clientes.sql` |
| `dashboard_update_provider_service` | `supabase/migrations/20260928090000_dashboard_pittecs_clientes.sql` |
| `decide_guincho_inspection` | `supabase/migrations/20261014130000_guincho_inspection_review.sql` |
| `defer_guincho_inspection_push_delivery` | `supabase/migrations/20261021123000_guincho_inspection_push_dispatch.sql` |
| `discard_guincho_inspection_photo` | `supabase/migrations/20261014124000_guincho_inspection_photo_commands.sql` |
| `discover_guincho_providers` | `supabase/migrations/20261022100000_service_ratings.sql` |
| `discover_service_providers` | `supabase/migrations/20260823020000_mobile_provider_dispatch.sql` |
| `dispute_guincho_completion` | `supabase/migrations/20260930142000_guincho_disputes.sql` |
| `expire_due_mobile_dispatch_requests` | `supabase/migrations/20260823020000_mobile_provider_dispatch.sql` |
| `expire_due_pittec_confirmations` | `supabase/migrations/20260913010000_pittec_call_tracking.sql` |
| `expire_due_pittec_payments` | `supabase/migrations/20260921020000_expire_pittec_unpaid_quotes.sql` |
| `expire_due_stalled_appointments` | `supabase/migrations/20260929040000_expire_stalled_appointments.sql` |
| `fail_guincho_route_refresh` | `supabase/migrations/20261014151000_guincho_route_finish_cleanup.sql` |
| `finish_guincho_route_refresh` | `supabase/migrations/20261014151000_guincho_route_finish_cleanup.sql` |
| `get_appointment_client_location` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `get_appointment_provider_location` | `supabase/migrations/20260821030000_appointment_location_stream_hardening.sql` |
| `get_guincho_appointment_snapshot` | `supabase/migrations/20260930132000_guincho_snapshot_contract.sql` |
| `get_guincho_inspection` | `supabase/migrations/20261014153000_guincho_inspection_named_snapshot.sql` |
| `get_guincho_inspection_push_delivery` | `supabase/migrations/20261021123000_guincho_inspection_push_dispatch.sql` |
| `get_guincho_inspection_v2` | `supabase/migrations/20261014153000_guincho_inspection_named_snapshot.sql` |
| `get_guincho_route_snapshot` | `supabase/migrations/20261014145000_guincho_route_snapshot.sql` |
| `get_my_account_context` | `supabase/migrations/20260821000000_account_roles_provider_ownership.sql` |
| `get_my_active_guincho_request` | `supabase/migrations/20260930132000_guincho_snapshot_contract.sql` |
| `get_my_active_mobile_dispatch` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `get_my_active_pittec_dispatch` | `supabase/migrations/20260930100000_pittec_client_completion_confirmation.sql` |
| `get_my_client_address` | `supabase/migrations/20260823120000_client_addresses.sql` |
| `get_my_guincho_inspection_alert` | `supabase/migrations/20261021121000_guincho_inspection_alert_api.sql` |
| `get_my_pending_mobile_dispatch` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `get_payment_history` | `supabase/migrations/20260915027000_orders_payment_recovery_reads.sql` |
| `get_payment_status` | `supabase/migrations/20260922030000_payment_status_snapshot.sql` |
| `get_pittec_appointment_snapshot` | `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` |
| `get_pittec_route_context` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `get_pittec_route_snapshot` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `get_provider_rating_summary` | `supabase/migrations/20261022100000_service_ratings.sql` |
| `get_recoverable_payment` | `supabase/migrations/20260921010000_scope_recoverable_pittec_payment.sql` |
| `get_saved_card_tokenization_context` | `supabase/migrations/20260915026000_orders_saved_card_context.sql` |
| `get_service_rating` | `supabase/migrations/20261022100000_service_ratings.sql` |
| `list_dashboard_audit_log` | `supabase/migrations/20260928060000_dashboard_audit_log.sql` |
| `list_guincho_inspection_photos` | `supabase/migrations/20261014142000_guincho_inspection_reads_v2.sql` |
| `list_guincho_inspection_photos_v2` | `supabase/migrations/20261014142000_guincho_inspection_reads_v2.sql` |
| `list_guincho_inspection_versions` | `supabase/migrations/20261014122000_guincho_inspection_snapshots.sql` |
| `list_home_services` | `supabase/migrations/20260930110000_fix_pittec_snapshot_and_home_services.sql` |
| `list_my_guincho_appointments` | `supabase/migrations/20260930142000_guincho_disputes.sql` |
| `list_my_pending_guincho_inspection_approvals` | `supabase/migrations/20261021121000_guincho_inspection_alert_api.sql` |
| `list_my_provider_ratings` | `supabase/migrations/20261022103000_provider_rating_comments.sql` |
| `list_my_service_appointments` | `supabase/migrations/20261022100000_service_ratings.sql` |
| `list_nearby_pittecs` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `list_saved_cards` | `supabase/migrations/20260912000000_mercadopago_payments.sql` |
| `list_service_provider_locations` | `supabase/migrations/20260823060000_initial_provider_map_locations.sql` |
| `mp_apply_evidence` | `supabase/migrations/20260915022000_orders_payment_reconciliation.sql` |
| `mp_begin_operation` | `supabase/migrations/20260915029000_orders_payment_cancellation.sql` |
| `mp_claim_payment` | `supabase/migrations/20260915028000_orders_payment_compensation.sql` |
| `mp_claim_work` | `supabase/migrations/20260923010000_payment_worker_priority.sql` |
| `mp_defer_work` | `supabase/migrations/20260915022000_orders_payment_reconciliation.sql` |
| `mp_record_operation` | `supabase/migrations/20260915029000_orders_payment_cancellation.sql` |
| `mp_register_order_identity` | `supabase/migrations/20260915028000_orders_payment_compensation.sql` |
| `mp_store_webhook` | `supabase/migrations/20260915022000_orders_payment_reconciliation.sql` |
| `next_mobile_dispatch_candidate` | `supabase/migrations/20260823020000_mobile_provider_dispatch.sql` |
| `prepare_guincho_inspection_photo` | `supabase/migrations/20261014152000_guincho_inspection_legacy_projection.sql` |
| `prepare_guincho_inspection_photo_v2` | `supabase/migrations/20261014143000_guincho_inspection_commands_v2.sql` |
| `publish_client_location` | `supabase/migrations/20260923020000_pittec_client_tracking_location.sql` |
| `publish_mobile_provider_location` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `publish_provider_location` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `record_dashboard_action` | `supabase/migrations/20260928060000_dashboard_audit_log.sql` |
| `register_my_guincho_inspection_push` | `supabase/migrations/20261021121000_guincho_inspection_alert_api.sql` |
| `remove_saved_card` | `supabase/migrations/20260912000000_mercadopago_payments.sql` |
| `request_guincho_completion` | `supabase/migrations/20260930141000_guincho_completion.sql` |
| `request_mobile_dispatch_candidate` | `supabase/migrations/20260823020000_mobile_provider_dispatch.sql` |
| `request_payment_cancellation` | `supabase/migrations/20260915029000_orders_payment_cancellation.sql` |
| `reserve_homol_order_payment` | `supabase/migrations/20260930153000_homol_random_checkout_amount.sql` |
| `reserve_order_payment` | `supabase/migrations/20260930136000_guincho_payment_context.sql` |
| `respond_guincho_request` | `supabase/migrations/20260930154000_guincho_accept_before_payment.sql` |
| `respond_to_mobile_dispatch` | `supabase/migrations/20260929040000_expire_stalled_appointments.sql` |
| `respond_to_pittec_quote` | `supabase/migrations/20260913010000_pittec_call_tracking.sql` |
| `revoke_my_guincho_inspection_push` | `supabase/migrations/20261021121000_guincho_inspection_alert_api.sql` |
| `save_guincho_trip_draft` | `supabase/migrations/20260930133000_guincho_discovery_and_drafts.sql` |
| `set_dashboard_staff_active` | `supabase/migrations/20260928050000_dashboard_staff.sql` |
| `set_primary_vehicle` | `supabase/migrations/20260823000000_restore_vehicle_rpcs.sql` |
| `set_provider_availability` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `simulate_guincho_payment` | `supabase/migrations/20260930151000_guincho_simulation_quote_revalidation.sql` |
| `start_guincho_transport` | `supabase/migrations/20260930140000_guincho_transport.sql` |
| `start_mobile_appointment` | `supabase/migrations/20260923000000_pittec_pricing_distance_and_total.sql` |
| `start_mobile_dispatch` | `supabase/migrations/20260915025000_orders_dispatch_payment_guards.sql` |
| `start_pittec_dispatch` | `supabase/migrations/20260913010000_pittec_call_tracking.sql` |
| `stop_mobile_provider_location` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |
| `stop_provider_location` | `supabase/migrations/20260821020000_appointment_current_locations.sql` |
| `store_guincho_quote` | `supabase/migrations/20260930134000_guincho_quotes.sql` |
| `submit_guincho_inspection` | `supabase/migrations/20261014143000_guincho_inspection_commands_v2.sql` |
| `submit_service_rating` | `supabase/migrations/20261022100000_service_ratings.sql` |
| `unselect_guincho_inspection_photo` | `supabase/migrations/20261014124000_guincho_inspection_photo_commands.sql` |
| `update_service_catalog_item` | `supabase/migrations/20260930000000_service_catalog_and_pittec_map_card.sql` |
| `upsert_my_client_address` | `supabase/migrations/20260823120000_client_addresses.sql` |
| `upsert_pittec_route_snapshot` | `supabase/migrations/20260902000000_pittec_on_demand.sql` |

## Revisão estática da implementação

A seleção de modalidade foi explicitada também no consumidor de `account_roles` da função `geocode-provider-address`, que antes usava uma consulta escalar por usuário. Os wrappers de negócio preservam as assinaturas, grants originais e verificações de ownership; suas cópias privadas têm execução revogada de anon/authenticated. RLS restritiva complementa a proteção de leitura comercial, mantendo o acesso do titular aos dados necessários ao complemento. Recuperação usa cliente Auth isolado e somente em memória; não importa a sessão de recuperação para a sessão comercial.

Os ViewModels de documentos e recuperação pertencem a stores limitados ao fluxo, cancelados ao sair. Temporários documentais ficam em noBackupFilesDir e o FileProvider só expõe essa subpasta; imagens de conferência não usam cache de disco ou memória do Coil. A verificação estática não substitui os cenários de autorização e concorrência pendentes no quickstart.
