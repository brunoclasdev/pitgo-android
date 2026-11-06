# Implementation Plan: Cadastro em duas etapas e validação documental

**Branch**: `develop` (branch Git atual; identificador de feature resolvido pelo Spec Kit: `035-validacao-documental`) | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/035-validacao-documental/spec.md`

## Summary

Entregar cadastro básico comum para cliente e prestador, complementação com documentos privados e revisão humana pelo time PitGo. Novas contas autenticam antes da aprovação, mas só usam funcionalidades da modalidade aprovada. Clientes mantêm acesso durante uma nova análise como prestador. Preservar modalidades existentes por dispensa explícita, sem reativar contas bloqueadas.

Integrar o fluxo ao MVVM existente e às RPCs do Supabase, com aprovação por modalidade, snapshots versionados com seleção de arquivos por submissão, recuperação de senha sem alterar aprovação, documento por veículo de atendimento e decisão administrativa transacional. Evoluir o modelo atual de papel exclusivo para memberships coexistentes e contexto de sessão com capacidades. Reutilizar Auth, Storage, FileProvider, captura Android, staff administrativo e auditoria existentes. Preparar dados e operações para o dashboard; suas telas são responsabilidade do projeto administrativo.

## Technical Context

**Language/Version**: Kotlin configurado pelo toolchain AGP 9.1.1; plugins Compose/serialization 2.2.10; SQL PostgreSQL no Supabase; TypeScript/Deno nas Edge Functions existentes. Versões são as declaradas no repositório, sem upgrade nesta feature.

**Primary Dependencies**: Compose BOM 2026.06.01/Material 3, Lifecycle 2.10.0, Navigation Compose 2.9.8, Supabase Kotlin 3.2.6, Ktor 3.3.1, Coil 3.3.0, ExifInterface 1.4.2, Activity Compose 1.13.0. Não adicionar biblioteca de câmera, OCR, biometria ou nova estrutura de DI. No backend, ImageScript 1.3.0 valida decodificação JPEG/PNG, pois Deno não fornece decodificador nativo; justificativa em research.md.

**Storage**: Supabase Auth para credenciais, PostgreSQL privado para identidade/cases/versões/decisões e bucket privado para documentos. Arquivos Android privados temporários, fora de backup; nenhum Room ou novo cache de documentos persistente.

**Testing**: Compilar variantes dev/homol e validar manualmente cenários de autorização, concorrência, correção e migração em ambiente isolado. Não gerar suítes, mocks ou fixtures automatizados sem solicitação, conforme constituição. Roteiro em [quickstart.md](quickstart.md).

**Target Platform**: Android minSdk 24, compileSdk 37, targetSdk 36; ambientes dev/homol/prod já definidos. Backend Supabase compartilhado com projeto de dashboard.

**Project Type**: Aplicativo Android nativo com backend Supabase e contrato administrativo externo.

**Performance Goals**: Meta de aceite proposta: formulário básico concluído em até três minutos por 9 de 10 participantes (SC-005); consultas de cadastro/fila sem imagens p95 até dois segundos em homol com rede estável e 1.000 casos sintéticos. Processar e enviar imagens fora da Main Thread, uma por vez, mantendo feedback por arquivo. O tempo de revisão humana não recebe SLA novo.

**Constraints**: Quatro fotos pessoais e documento por veículo de atendimento do prestador; imagens JPEG/PNG até 10 MiB; proibir acesso público e sobrescrita de evidência submetida; nenhuma liberação offline presumida; titular não altera estado administrativo; ativação não remove bloqueio operacional. Senhas/documentos/URLs com credenciais não entram em logs.

**Scale/Scope**: Duas modalidades de acesso, três níveis de staff já existentes, quatro estados de case e submissões versionadas. Fila padrão 20/máximo 100 por página; um envio corrente por modalidade; quantidade de veículos acompanha o cadastro vigente, sem limite comercial novo. Escopo não inclui telas do dashboard, OCR, prova de vida, recadastramento de modalidades antigas ou política nova de retenção.

## Constitution Check

*GATE: avaliado antes da pesquisa e novamente após o desenho.*

| Princípio | Pré-design | Pós-design e evidência |
|---|---|---|
| I. Simplicidade e requisito atual | PASS | Uma feature documental; versões, membership adicional e RPCs são necessários às decisões do usuário; sem RBAC paralelo ou serviço de identidade externo. |
| II. Android nativo/MVVM | PASS | UI → ViewModel → Repository → Supabase/arquivos, StateFlow e Navigation Compose existentes; nenhum Use Case de mero repasse. |
| III. Estado e experiência | PASS | Sessão autenticada restrita, estados exclusivos por modalidade, slots claros, retomada e erro acionável; temas e acessibilidade mantidos. |
| IV. Dados e segurança | PASS | Auth separado de capacidade, RLS/grants mínimos, objetos privados imutáveis, decisão atômica, guards de negócio e ausência de PII em logs. |
| V. Qualidade proporcional | PASS | Sem testes automatizados não solicitados ou refatorações alheias; compilação e roteiro manual verificam fluxos, migração e segurança. |

Nenhuma exceção constitucional necessária. Dependências não são atualizadas: confirmar versões estáveis compatíveis somente se a implementação demonstrar necessidade concreta de mudança.

## Project Structure

### Documentation (this feature)

```text
specs/035-validacao-documental/
├── spec.md
├── checklists/requirements.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    ├── registration-api.md
    └── access-and-android.md
```

`tasks.md` será gerado por `$speckit-tasks`, não por este comando.

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/account/{data,model,repository}/       # memberships e contexto/capacidades
├── core/auth/{model,repository}/              # autenticação restrita centralizada
├── core/navigation/                          # rotas e gate por modalidade
├── core/app/PitGoAppContainer.kt              # composição das dependências
├── features/signup/                          # BasicRegistrationScreen/ViewModel: primeira etapa comum
├── features/providersignup/                  # reaproveitar primeira etapa comum
├── features/providerregistration/            # dados complementares sem autoativação
└── features/registrationverification/        # nova feature: data/model/repository/ui/viewmodel
app/src/main/AndroidManifest.xml              # FileProvider existente
app/src/main/res/                             # textos e exclusão de arquivos de backup
supabase/migrations/                         # novas migrations aditivas
supabase/functions/
├── registration-evidence-confirm/index.ts    # confirmar imagem real e reserva
├── _shared/                                 # guarda de capacidade se reutilizada
└── funções de negócio existentes             # capacidade antes de efeitos externos
```

**Structure Decision**: Reutilizar módulos existentes para signup, sessão e cadastro empresarial; agrupar somente a captura, envio e acompanhamento documental na nova feature. Um Repository documental cobre rascunho, upload e estado; recursos de arquivo ficam em data e recebem dependências no container. Extrair utilitário compartilhado de captura apenas se reduzir duplicação real sem importar domínio de vistoria. Não criar módulo de frota ou novo app administrativo.

## Phase 0 — Research

Concluída em [research.md](research.md), com inspeção do código e documentação oficial. Resolvidos: exclusividade atual de papéis; perfil inativo bloqueando login; signup de prestador sem identidade pessoal; reuso de staff; upload privado com reserva; conferência de imagem; guards de negócio e migração segura.

Referências do repositório: `20260821000000_account_roles_provider_ownership.sql`, `20260928050000_dashboard_staff.sql`, `20260928090000_dashboard_pittecs_clientes.sql`, migrations de inspeção `2026101412*`, SessionRepository, AccountContextRepository, SignUpRepository, ProviderSignUpRepository e ProviderRegistrationRepository.

## Phase 1 — Design & Contracts

Concluída: [data-model.md](data-model.md), [API administrativa e de cadastro](contracts/registration-api.md), [capacidades e Android](contracts/access-and-android.md) e [quickstart.md](quickstart.md).

Sequência de implementação recomendada para decomposição posterior:

1. Inventariar schema/autorizações atuais, diagnosticar CPF e dependências da chave de papéis; desenhar migrations aditivas com corte consistente e dispensa legada por modalidade.
2. Implementar rascunhos, reserva/confirmação, submissão, revisão, histórico, grants/RLS e ativação transacional, preservando as exceções de staff no signup.
3. Aplicar capacidade por modalidade às RPCs, RLS, discovery, ativadores do dashboard e Edge Functions; isolar permissões de onboarding das de marketplace.
4. Unificar dados iniciais no Android, ajustar carregamento pós-signup/contexto v2 e navegação por capacidades; suportar cliente aprovado com prestador pendente.
5. Integrar recuperação de senha e retorno ao contexto restrito, formulários complementares, captura, retomada, submissão, acompanhamento e correção; remover ativação/disponibilidade automática do cadastro atual.
6. Preparar instaladores de banco sem corte antecipado; implantar guards das Edge Functions compatíveis com marcador consultado no servidor e executar backfill, snapshot legado, troca de trigger e ativação de guards SQL na transação final `20261105114000_registration_atomic_cutover.sql`. Validar concorrência e dispensa após o commit.
7. Executar validação completa de quickstart em dev/homol e entregar contrato ao projeto do dashboard sem publicação ou alteração remota automática.

## Risks and rollout

- A alteração do papel exclusivo afeta joins e selects escalares de SQL/Kotlin. Não basta adicionar tabela documental; inventário e revisão dos consumidores são parte obrigatória do trabalho.
- O bloqueio global por `active` não representa modalidades independentes. Manter flags operacionais e exigir o novo helper nas entradas de negócio, inclusive rotas administrativas antigas.
- SQL e Storage não compartilham transação de upload. Reserva e confirmação impedem que arquivos ausentes sejam aprovados; imagens submetidas não podem ser sobrescritas ou apagadas por clientes.
- Snapshot da modalidade legada deve ocorrer junto da instalação do novo fluxo, evitando corrida de signup e dispensa baseada em ausência. Não reescrever migrations históricas.
- Não aplicar migration local não versionada de guincho nem executar reset/push remoto incidentalmente. Esta fase só gera design; não modifica banco, app ou infraestrutura.
- Preservar guards em qualquer recuperação de implantação; não propor rollback que volte a permitir autoativação de novas contas.

## Complexity Tracking

Sem violações a justificar. Memberships coexistentes, submissões versionadas e revisão transacional atendem diretamente FR-016, FR-019 e FR-022.
