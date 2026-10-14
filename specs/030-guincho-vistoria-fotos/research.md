# Research: Vistoria fotográfica do Guincho

**Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

Pesquisa do código Android/Supabase, do dashboard existente e de documentação primária. Os levantamentos Android e backend foram delegados conforme a fase de pesquisa da skill. Nenhuma implementação, migration aplicada, compilação ou validação funcional foi executada nesta fase.

## 1. Separar chegada, vistoria e início

**Decision**: Manter o pedido em `ACCEPTED` depois de `ARRIVE`, registrar somente `arrived_at`, criar a vistoria obrigatória e habilitar `START` apenas após aprovação da versão vigente. A aprovação não altera `service_started_at`; o guincheiro toca em “Iniciar transporte”.

**Rationale**: `20260930154000_guincho_accept_before_payment.sql` altera o corpo original de `private.guincho_provider_transition` para que ARRIVE também defina `IN_PROGRESS` e `service_started_at`. A nova migration deve substituir explicitamente esse comportamento, preservando os ajustes posteriores de `#variable_conflict use_column` e de pagamento com `coalesce(payment_amount,customer_total_amount)`. Alterar só a UI ou só START deixaria um caminho de início sem vistoria.

**Alternatives considered**: Iniciar na aprovação contradiz o esclarecimento B. Um novo estado global de atendimento ampliaria parsers e políticas desnecessariamente; estados próprios da vistoria complementam `ACCEPTED`.

**Evidence**: `supabase/migrations/20260930140000_guincho_transport.sql`, `20260930149000_guincho_plpgsql_row_resolution.sql`, `20260930153000_homol_random_checkout_amount.sql`, `20260930154000_guincho_accept_before_payment.sql`; `GuinchoTrackingContent.kt` e `AppointmentTrackingViewModel.kt`.

## 2. Compatibilidade decidida na chegada

**Decision**: Acrescentar `appointments.guincho_inspection_required` com default false. Na primeira chegada após ativação da migration, o backend define true atomicamente e cria a primeira versão em rascunho. Registros que já possuam `arrived_at` conservam false e o fluxo legado. A versão do app não determina dispensa.

**Rationale**: A spec usa o momento da chegada como fronteira. Backfill por data de criação ou pela reserva dispensaria pedidos anteriores ainda a caminho, contrariando FR-017. A repetição de ARRIVE deve recuperar o resultado registrado antes de recalcular obrigatoriedade. O parâmetro não pode ser escrito pelo aplicativo.

**Alternatives considered**: Exigir vistoria retroativa em transporte viola a compatibilidade. Isentar apps antigos abre desvio: novos atendimentos iniciados por eles permanecem bloqueados até uso de app compatível.

**Evidence**: Guardas de `start_mobile_appointment` e `complete_mobile_appointment` em `20260930144000_guincho_shared_dispatch_guards.sql` já delegam ao fluxo específico de Guincho.

## 3. Modelo pequeno com evidência imutável

**Decision**: Representar a vistoria como agregado do atendimento, com três tabelas: versões (incluindo decisão), fotos imutáveis e associação versão/foto. Não criar tabela de decisão, tabela de cabeçalho ou event store. Correções reutilizam referências a fotos inalteradas; uma nova captura tem outro ID e objeto. Preservar também fotos confirmadas que foram refeitas antes do envio, identificadas como não enviadas quando não pertencem a um conjunto enviado.

**Rationale**: Atende histórico, autoria, revisão e preservação sem duplicar imagens nem informações do pedido. Veículo e participantes vêm do `guincho_trip_snapshot` congelado, não do cadastro atual. Decisão única por versão cabe na própria linha da versão.

**Alternatives considered**: Substituir o blob apagaria o registro anterior. Duplicar todos os blobs a cada correção aumentaria armazenamento. Um journal genérico de operações é desnecessário; chaves de operação nas linhas afetadas e transições naturais permitem repetição segura.

## 4. Upload privado e confirmação em duas etapas

**Decision**: Bucket privado `guincho-inspections`; preparar uma reserva de foto, fazer upload autenticado com `upsert=false` e confirmar pelo RPC após verificar o objeto e seus metadados no servidor. Caminho atribuído pelo backend: `<appointment_id>/<photo_id>.jpg`. Storage permite INSERT apenas no caminho reservado ao guincheiro, enquanto o pedido e rascunho forem graváveis, e SELECT aos participantes. Não conceder UPDATE/DELETE aos participantes nem URLs públicas.

**Rationale**: Storage e PostgreSQL não participam de uma única transação de negócio. A confirmação separada evita marcar uma foto como salva quando somente os metadados foram registrados. Falha após upload pode ser recuperada confirmando o mesmo ID, sem sobrescrever. A documentação distingue INSERT de sobrescrita, que requer SELECT/UPDATE. [Storage access control](https://supabase.com/docs/guides/storage/security/access-control).

**Alternatives considered**: Bucket público expõe as fotos. URLs assinadas no app criam credenciais temporárias compartilháveis; usar download autenticado e cache privado por conta. [Private buckets](https://supabase.com/docs/guides/storage/buckets/fundamentals), [Kotlin upload](https://supabase.com/docs/reference/kotlin/storage-from-upload).

## 5. Captura, imagem e dependências

**Decision**: Usar `ActivityResultContracts.TakePicture` e `FileProvider` com arquivo temporário privado, sem galeria. Não declarar CAMERA quando a captura é delegada ao app de câmera do sistema; tratar ausência, cancelamento, bloqueio e imagem inválida. [TakePicture](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.TakePicture), [permissões mínimas](https://developer.android.com/privacy-and-security/minimize-permission-requests#take-photo), [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider).

**Rationale**: Activity Compose e Core já existem. URI, ponto e operação pendentes ficam em `SavedStateHandle`, vinculados à conta/fingerprint de ambiente, e não são apagados em STOP. O tracking atual usa `LifecycleStartEffect` e limpa estado em `onHidden`; a tela dedicada de vistoria deve sobreviver à câmera externa sem confundir STOP com descarte.

**Decision**: Normalizar orientação, redimensionar sem ampliar para lado maior até 2560 px e gerar JPEG qualidade inicial 90, reduzindo qualidade até 80 se necessário para no máximo 5 MiB. Se não couber, informar erro recuperável, sem enviar silenciosamente imagem ilegível. Preview é do arquivo que será salvo. Usar `BitmapFactory`/`Bitmap` fora da Main Thread e `androidx.exifinterface:exifinterface:1.4.2` para orientação em toda a faixa Android; a normalização não copia localização EXIF.

**Rationale**: Limite abaixo de 6 MB permite upload simples; o tamanho de pixels mantém detalhes para ampliação. A normalização gera a evidência efetivamente enviada, não pretende preservar um original pericial. ExifInterface é Jetpack oficial, cobre orientação e espelhamento sem APIs de câmera novas. [Upload padrão](https://supabase.com/docs/guides/storage/uploads/standard-uploads), [ExifInterface estável consultada](https://developer.android.com/jetpack/androidx/releases/exifinterface).

**Alternatives considered**: CameraX e upload retomável adicionariam infraestrutura sem necessidade. Reutilizar versões atuais do projeto, acrescentando somente `storage-kt` alinhado ao Supabase 3.2.6 instalado e ExifInterface; reconfirmar compatibilidade no momento de implementar, sem atualizar todo o catálogo.

## 6. Retomada, concorrência e publicação

**Decision**: Toda mutação trava `service_searches → appointments → versão/foto`, valida papel, revisão, estado do pedido e pagamento. Incrementa revisão do atendimento e busca e reutiliza os canais privados CHANGED. O resumo via polling existente de 2 segundos desperta o carregamento dos detalhes; não transportar fotos no broadcast.

**Rationale**: A consulta atual descarta snapshots antigos por revisão. Guardas no servidor devem cobrir tanto RPCs novos quanto START/COMPLETE legados. Chave de operação é persistida na reserva, envio e decisão; reuso com payload diferente é conflito. IDs não nulos sozinhos, como em alguns RPCs antigos, não garantem a idempotência desse fluxo.

**Alternatives considered**: Outro websocket ou polling paralelo no tracking duplica trabalho. Sessões sem rede não aprovam; a garantia de retomada é para fotos confirmadas remotamente.

**Decision**: Isolar cache/captura por EnvironmentConfig.fingerprint + conta + foto e limpar também no callback clearIncompatible de EnvironmentStateRepository.validate.

**Rationale**: Todos os flavors compartilham application ID. PitGoAppContainer invalida autenticação ao trocar fingerprint por caminho que não passa obrigatoriamente por beforeSignOut; limpar somente no logout deixaria referências a imagens de outro backend.

## 7. Cancelamento, conservação e suporte

**Decision**: Derivar permissão de escrita do estado do atendimento em todo RPC e política, incluindo cancelamento genérico por prazo. Preservar fotos confirmadas e decisões após conclusão, cancelamento ou contestação. Não introduzir expiração automática dos registros nem job de limpeza das imagens confirmadas nesta feature: conservá-las junto ao histórico, sem prazo específico novo. Reservas incompletas ficam distinguíveis; não implementar agora um serviço de limpeza especulativo.

**Rationale**: `20260929040000_expire_stalled_appointments.sql` pode encerrar diretamente um pedido `ACCEPTED`, sem chamar o cancelamento específico do Guincho. O prazo de atendimento existente continua aplicável; ele não aprova fotos. O repositório não contém uma política executável de expiração do histórico de veículos/imagens; presumir um cron existente apagaria evidência indevidamente.

**Decision**: Suporte consulta apenas no contexto de contestação permitida, com `private.dashboard_access_at_least('MANAGER')` e auditoria via `record_dashboard_action`. Adicionar projeção auditada de manifesto e autorização de foto; download pelo servidor do dashboard, usando o cliente administrativo existente somente após autorização com a sessão do funcionário. Sem concessão geral de leitura de Storage a MANAGER, sem nova Edge Function.

**Rationale**: O dashboard já possui detalhe de contestação, cliente SSR e cliente administrativo server-side. `dashboard_get_guincho_dispute` é STABLE; não inserir auditoria nele. Novos RPCs de leitura auditada devem ser VOLATILE. Referência: `20260930143000_guincho_support_resolution.sql`, `20260928060000_dashboard_audit_log.sql` e, no dashboard, `src/services/contestacoes.ts`, `src/lib/supabase/admin.ts`.

**Alternatives considered**: Ampliar RLS de Storage para qualquer funcionário ignora a restrição ao caso e a auditoria. Uma Edge Function exclusiva duplicaria o servidor já disponível. Suporte não aprova, altera nem dispensa a vistoria.

## Resultado e pontos para implementação

Todas as decisões de desenho estão resolvidas. Não se exige nova política comercial de prazo ou quantidade total de fotos. Listar fotos e versões em páginas, sem limitar arbitrariamente fotos adicionais ou ciclos de correção. Os limites de payload por imagem e as revisões controlam recursos e concorrência. Na implementação, conferir políticas preexistentes de Storage, configuração do ambiente, versões fixadas e instruções Next.js do dashboard antes de editar código. Isso é verificação de integração, não pendência de produto.
