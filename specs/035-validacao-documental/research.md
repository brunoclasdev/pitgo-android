# Research: Cadastro e validação documental

## 1. Autenticação e aprovação são estados distintos

**Decision**: Manter Supabase Auth e introduzir contexto autenticado restrito, com capacidades por modalidade no carregamento central de sessão. `profiles.active` e `providers.active` continuam representando bloqueios operacionais, não o resultado completo da análise documental.

**Rationale**: `SessionRepository.loadAccount`, `AccountContextRepository.load` e `AppSessionState` atualmente convertem perfil inativo em bloqueio e roteiam exclusivamente cliente ou prestador. A nova conta deve conseguir autenticar e corrigir documentos sem ganhar capacidade de negócio. Ativar um perfil apenas para permitir login abriria caminhos antigos.

**Alternatives considered**: Usar somente `active` impediria distinguir login permitido de negócio bloqueado; adicionar verificações isoladas em cada tela duplicaria a política e deixaria acessos diretos expostos.

## 2. Modalidades coexistentes

**Decision**: Evoluir `account_roles` de chave única `user_id` para chave `(user_id, account_type)`, preservando vínculos e sem duplicar identidade Auth. Criar contexto v2 com conjunto de modalidades e capacidades independentes. Contexto v1 continua compatível com a modalidade original, sem conceder nova capacidade. Atualizar consumidores SQL que presumem uma única linha.

**Rationale**: A migration `20260821000000_account_roles_provider_ownership.sql` e o modelo Kotlin atual pressupõem exclusividade. FR-022 exige cliente funcional durante análise do prestador. O usuário escolhe o modo de atuação; escolher modo não altera aprovação. Para contas antigas, registrar dispensa apenas nas modalidades realmente existentes no corte de implantação.

**Alternatives considered**: Substituir CLIENT por PROVIDER viola a decisão do usuário; criar segunda conta quebraria unicidade de CPF e e-mail; dispensar toda modalidade de conta antiga permitiria ingressar como prestador sem análise.

## 3. Cadastro básico comum e compatibilidade

**Decision**: Reutilizar signup e validações existentes para um formulário básico comum. Criar identidade cadastral privada por usuário, com nome, sobrenome, documento e contato para ambas as modalidades; manter `profiles.name` como projeção compatível. Prestadores usam exclusivamente CNPJ; clientes usam CPF ou CNPJ, com unicidade por documento. Credenciais permanecem no Auth.

**Rationale**: `ProviderSignUpRepository` envia somente `account_type`; `SupabaseSignUpRepository` envia dados e espera perfil ativo. Alterar o trigger efetivamente instalado (incluindo a exceção para dashboard staff), a confirmação pós-signup e o retorno de sessão evita recuperação infinita de uma conta válida mas restrita. CNPJ pertence ao cadastro empresarial posterior.

**Alternatives considered**: CPF exclusivo em `profiles` não cobre prestadores sem perfil; duplicação de CPF em vários locais como autoridades independentes permite inconsistência. Contas antigas sem CPF continuam sem exigência retroativa; valores existentes válidos devem participar da unicidade e conflitos devem ser diagnosticados antes do corte, nunca resolvidos por mesclagem automática.

## 4. Evidências privadas e versões imutáveis

**Decision**: Bucket privado `registration-documents`, upload autenticado para caminho reservado por servidor e sem sobrescrita. Fotos JPEG ou PNG de até 10 MiB; upload confirmado pelo servidor antes de integrar a submissão. Quatro tipos pessoais e `VEHICLE_DOCUMENT` por veículo de atendimento. Snapshots de dados e vínculos registration_submission_evidence tornam-se imutáveis ao submeter. Cada arquivo existe uma vez e pode ser selecionado por versões sucessivas do mesmo case; a correção cria novos vínculos, sem duplicar storage_path ou alterar relações históricas. Download autenticado com RLS; não persistir URLs públicas ou assinadas.

**Rationale**: Buckets privados aplicam RLS e permitem download autenticado, conforme [Supabase Storage](https://supabase.com/docs/guides/storage/buckets/fundamentals). Uma referência a arquivo não prova recebimento nem autenticidade. RLS de escrita deve consultar reserva e versão em rascunho; bloquear UPDATE/DELETE de evidência submetida impede trocar o objeto depois da análise. Configurar MIME/tamanho no bucket e conferir metadados e integridade de imagem no serviço de confirmação, sem OCR.

**Alternatives considered**: Bucket público exporia documentos. Upsert no mesmo caminho invalidaria a prova revisada. URLs assinadas são credenciais temporárias compartilháveis; não são necessárias com downloads autenticados. Não há transação distribuída entre objeto e SQL: confirmação e submissão validam existência, e upload órfão não conta como foto entregue.

## 5. Captura Android

**Decision**: Usar `ActivityResultContracts.TakePicture`, FileProvider e arquivos privados, seguindo o padrão de captura existente em guincho sem importar regras de vistoria. Tirar e revisar a foto no dispositivo, corrigir orientação e remover EXIF de localização; não gravar na galeria. Processamento fora da Main Thread, sem Context em ViewModel, com limpeza após confirmação de upload, cancelamento e logout. Arquivos pendentes não entram em backup.

**Rationale**: A [referência oficial TakePicture](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.TakePicture) suporta gravação da foto em URI fornecida. A base já inclui Activity Compose, ExifInterface, Coil e Storage; não há necessidade de biblioteca nova. Não há garantia de prova de vida, que está fora do escopo.

**Alternatives considered**: CameraX adicionaria dependência e complexidade sem requisito de câmera embutida; reaproveitar o fluxo completo de inspeção acoplaria documentos pessoais a atendimentos.

## 6. Autoridade e atomicidade administrativa

**Decision**: Reutilizar `dashboard_staff` ativo e `private.dashboard_access_at_least('MANAGER')` para revisão, incluindo ADMIN e OWNER. Decisão usa lock por cadastro/modalidade, revisão esperada e chave de operação. Conferência de todas as evidências, aprovação, ativação da modalidade e histórico são gravados em uma transação. Registrar auditoria administrativa existente sem valores de documentos.

**Rationale**: As migrations `20260928050000_dashboard_staff.sql` e `20260928060000_dashboard_audit_log.sql` já definem a hierarquia. Não criar outro sistema de permissões. Funções privilegiadas usam nomes qualificados, `search_path = ''`, grants mínimos e titular derivado de `auth.uid()`, em linha com [Database Functions](https://supabase.com/docs/guides/database/functions). Autorizações não vêm de metadados editáveis pelo usuário, conforme [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security).

**Alternatives considered**: Usar service role no dashboard cliente ou aplicativo ignora isolamento; atribuir papel administrativo por user_metadata permite escalada. Criar RBAC adicional não é necessário para esta entrega.

## 7. Bloqueio além da navegação

**Decision**: Capacidade efetiva = modalidade existente e (aprovada ou dispensa legada) e restrições operacionais vigentes satisfeitas. Aplicar helper SQL na autorização das RPCs e políticas; reforçar Edge Functions antes de qualquer efeito externo, inclusive quando usam service role. Remover ativação/disponibilidade automática ao concluir cadastro.

**Rationale**: `ProviderRegistrationRepository.submit` chama `set_provider_availability(true)` após cadastrar. Isso deve passar a apenas salvar dados e encaminhar ao envio documental. Guards de prestador só podem publicar/ativar ofertas e disponibilidade após aprovação. Dados de onboarding precisam ser graváveis por RPCs dedicadas mesmo sem acesso ao marketplace. Webhooks de pagamento continuam autenticados como integração e não devem depender da sessão do cliente.

**Alternatives considered**: Bloquear apenas Home não impede RPC direta, pagamento ou busca que revele conta ainda não aprovada. Usar `profiles.active=false` globalmente bloquearia o cliente durante análise do prestador.

## 8. Implantação e validação

**Decision**: Migrações aditivas com instaladores preparados sem execução antecipada; Edge Functions compatíveis com marcador privado consultado no servidor implantadas primeiro, e migration final 20261105114000_registration_atomic_cutover.sql executando backfill, snapshot legado, troca de trigger e ativação dos guards SQL na mesma transação com serialização de signup/membership; ausência de registro nunca significa dispensa. Novas contas antigas versões do app também ficam restritas. Rollback não pode reabrir acesso: manter guards e recuperar por correção aditiva. Validar em dev/homol com contas sintéticas, build das variantes e roteiro manual, sem gerar testes automatizados não solicitados.

**Rationale**: Preserva FR-020 e a constituição. O ambiente atual contém uma migration local não versionada de guincho, que não integra esta feature e não deve ser alterada ou aplicada incidentalmente.

**Alternatives considered**: Data de criação recebida do dispositivo é manipulável; dispensar por ausência de registro abriria falha; reset indiscriminado de banco remoto é incompatível com preservação de dados.

Todas as decisões técnicas de planejamento estão resolvidas; versões citadas no plano são as instaladas no repositório, sem proposta de atualização de dependências.

## Ajustes confirmados durante implementação

- O decodificador de imagens não está disponível nativamente no runtime Deno do backend. A função de confirmação usa `npm:imagescript@1.3.0` somente no servidor para validar JPEG/PNG reais, com conferência de assinatura/dimensões antes da decodificação (máximo 16 milhões de pixels). Nenhuma dependência Android foi adicionada. Referência: [ImageScript](https://github.com/imagescript/ImageScript).
- A primeira etapa foi unificada em `BasicRegistrationScreen`/`BasicRegistrationViewModel`, usados pelas duas rotas existentes. Isso evita duplicar os sete campos nos dois formulários anteriores; os caminhos antigos não são mais usados como entrada na navegação.
- O complemento reutiliza os formulários atuais e `sync_my_registration_complement` copia valores já validados pelo servidor para o snapshot. `save_my_registration_draft` permite alterar a lista de veículos e exige que os demais campos sejam iguais ao snapshot atual, evitando injetar dados complementares não validados. Correções de empresa reutilizam a validação de catálogo/geocodificação do cadastro original.
- Bloqueios de negócio usam wrappers transacionais sobre as funções públicas inventariadas, preservando suas verificações originais e revogando acesso direto às cópias privadas. Prestadores em análise permanecem fora da descoberta por bloqueio documental de publicação; uma desativação operacional explícita é preservada após a aprovação.
- Recibos guardam hash SHA-256 do comando e resultado mínimo; repetições de comandos de cadastro retornam a situação corrente autorizada sem repetir a mutação. Não persistem outra cópia dos documentos ou dados pessoais no recibo.
