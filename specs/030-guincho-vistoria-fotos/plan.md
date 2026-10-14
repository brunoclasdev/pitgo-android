# Implementation Plan: Vistoria fotográfica do veículo no Guincho

**Branch**: `feature/refinamento-guincho` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: `specs/030-guincho-vistoria-fotos/spec.md`

**Feature directory**: `specs/030-guincho-vistoria-fotos`; o resolvedor do Spec Kit identifica a feature como `030-guincho-vistoria-fotos`, independentemente da branch Git atual acima.

## Summary

Ao tocar em “Cheguei”, o guincheiro registra a chegada e fotografa frente, traseira, lateral esquerda e lateral direita, com fotos opcionais de detalhes. O cliente revisa o conjunto enviado e aprova ou solicita correção. A aprovação libera o botão “Iniciar transporte”; somente a ação posterior do guincheiro inicia a execução. Fotos, versões e decisões ficam no histórico.

Implementar a vistoria dentro da feature Guincho existente, mantendo MVVM, snapshots e observação compartilhada. Acrescentar três tabelas para versões/fotos/associações, Storage privado e RPCs autoritativos. Corrigir no backend a transição atual que inicia o serviço na chegada; preservar pagamento, conclusão bilateral e os serviços já chegados. Reutilizar o detalhe de contestação no dashboard para consulta excepcional auditada pelo suporte.

## Technical Context

**Language/Version**: Kotlin/Compose plugin 2.2.10 conforme catálogo, Android Gradle Plugin 9.1.1, bytecode Java 11; PostgreSQL/PLpgSQL no Supabase. Dashboard existente usa TypeScript/Next.js 16.3.6; não alterar seu stack.

**Primary Dependencies**: Compose BOM 2026.06.01, Material3, Activity Compose 1.13.0, Lifecycle 2.10.0, Navigation Compose 2.9.8, Supabase Kotlin 3.2.6, Ktor 3.3.1, Coil 3.3.0 e MapLibre existentes. Adicionar somente módulo oficial `storage-kt` da versão Supabase instalada e Jetpack ExifInterface 1.4.2 para upload privado e normalização da orientação. Confirmar compatibilidade na implementação, sem upgrades gerais.

**Storage**: PostgreSQL para versões/decisões/metadados, bucket privado `guincho-inspections` para JPEG, cache temporário privado Android por fingerprint de ambiente e conta. Sem Room nem armazenamento de imagem em DataStore; SavedStateHandle contém somente referências da intenção pendente.

**Testing**: Compilação dos três flavors, lint Homol e isolamento de ambiente; validação funcional manual em Dev com dois participantes e revisão SQL/RLS dos contratos. Não gerar testes, mocks ou fixtures automaticamente, conforme constituição. O roteiro não declara cenários executados nem autoriza transações reais.

**Target Platform**: Android minSdk 24, compileSdk 37, targetSdk 36; Supabase; dashboard web existente para suporte autorizado.

**Project Type**: Aplicativo móvel Android com backend Supabase e integração localizada no dashboard administrativo existente.

**Performance Goals**: Metas SC-002/003: captura/envio até 5 minutos e revisão até 2 minutos nas condições especificadas; 95% de 20 mudanças visíveis ao outro participante em até 10 segundos. Reutilizar polling de 2 segundos/CHANGED existentes, com uma requisição em voo por recurso e suspensão quando oculto.

**Constraints**: Quatro pontos obrigatórios, aprovação explícita da versão vigente, início manual pelo guincheiro, nenhum bypass por timeout/app antigo, objetos e versões enviados imutáveis. JPEG até 5 MiB, lado maior até 2560 px, processamento fora da Main Thread. Rede exigida para salvamento/aprovação. Transporte/conclusão guardados no servidor. Respeitar o prazo/cancelamento existente sem apagar fotos.

**Scale/Scope**: Uma vistoria de retirada por atendimento e uma versão vigente; correções reutilizam fotos. Leitura paginada (10 versões/20 fotos por página, clamps definidos no contrato); sem limite comercial novo de fotos adicionais ou correções. Download cache local limitado a 128 MiB. Sem requisito novo de throughput global, captura offline integral, análise de danos, fotos na entrega ou exportação de laudo.

## Constitution Check

*GATE antes da pesquisa: PASS. GATE após o desenho: PASS.*

| Princípio | Verificação antes e depois do desenho |
|---|---|
| I. Simplicidade e escopo | Entrega completa de captura/revisão/histórico. Três tabelas necessárias, decisão incorporada à versão, sem event store ou sync genérico. Suporte usa tela/servidor existentes |
| II. Android nativo e MVVM | UI → ViewModel → Repository → Supabase/arquivos. Activity Result, FileProvider, Jetpack, StateFlow e injeção manual existentes; nenhum padrão alternativo |
| III. Estado e experiência | Estados explícitos da vistoria separados do atendimento; ação de início posterior à aprovação. Material3, temas, labels, fonte ampliada, loading/erro/retry e navegação centralizada |
| IV. Dados e segurança | RLS e RPCs validam papel, pagamento, revisão e estado; bucket privado, INSERT reservado, zero UPDATE/DELETE pelos participantes; consulta de suporte auditada e restrita ao caso |
| V. Qualidade proporcional | Sem testes não solicitados, upgrades gerais ou refatoração externa ao fluxo. Compilação/lint e roteiro manual proporcionais; imagens dimensionadas e trabalho fora da Main Thread |

ExifInterface e o módulo Storage têm uso atual demonstrado. Cache temporário é necessário para TakePicture/upload/revisão e não representa banco offline. As entidades de vistoria são componentes do atendimento exigidos pelo requisito atual, não novos domínios de marketplace. Nenhuma exceção constitucional necessária.

## Project Structure

### Documentation (this feature)

```text
specs/030-guincho-vistoria-fotos/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── guincho-inspection-api.md
│   └── guincho-inspection-ui.md
└── checklists/requirements.md
```

`tasks.md` pertence à próxima etapa `$speckit-tasks` e não é criado neste comando.

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/app/PitGoAppContainer.kt
├── core/navigation/{PitGoRoutes,PitGoNavHost}.kt
├── core/network/SupabaseClientProvider.kt
├── features/guincho/
│   ├── data/GuinchoInspectionDtos.kt                    # novo
│   ├── data/GuinchoInspectionCaptureFiles.kt            # novo, arquivos privados
│   ├── model/GuinchoInspectionModels.kt                # novo
│   ├── repository/GuinchoInspectionRepository.kt        # novo
│   ├── viewmodel/GuinchoInspectionViewModel.kt          # novo
│   └── ui/{GuinchoInspectionScreen,GuinchoTrackingContent,GuinchoHistoryScreen}.kt
├── features/mobiledispatch/{data,model,repository}/
└── features/appointmenttracking/{viewmodel,ui}/
app/src/main/AndroidManifest.xml
app/src/main/res/xml/guincho_inspection_file_paths.xml     # novo
app/src/main/res/values/strings.xml
app/build.gradle.kts
gradle/libs.versions.toml
supabase/migrations/                                     # novas migrations posteriores ao histórico atual
```

Integração de suporte no repositório existente `/Users/brunocesar/projetos-web/pitgo`:

```text
src/services/contestacoes.ts
src/types/database.ts
src/lib/supabase/admin.ts                                # documentar uso server-side autorizado
src/app/(dashboard)/contestacoes/[id]/page.tsx
src/app/api/contestacoes/[disputeId]/fotos/[photoId]/route.ts  # novo proxy autenticado
src/components/contestacoes/GuinchoInspectionGallery.tsx     # novo, somente se separar da página ajudar
```

**Structure Decision**: Vistoria em `features/guincho`, consumida pelo tracking/histórico atuais. Models/DTOs compartilhados recebem apenas o resumo. Não criar módulo Gradle, camada UseCase de repasse ou nova Edge Function. O dashboard somente amplia leitura do caso existente; respeitar seu AGENTS.md e guias Next.js locais antes de editar código na implementação.

## Phase 0 — Pesquisa concluída

[research.md](research.md) registra decisões, alternativas e evidência do código/documentação primária. Principais riscos resolvidos: ARRIVE inicia serviço na migration mais recente; callback de chegada também envia START no Android; STOP causado pela câmera limpa o tracking; chaves de operação atuais não bastam para upload/envio; cancelamento por prazo não passa sempre pelo helper específico.

## Phase 1 — Desenho e sequência de integração

1. Adicionar tabelas, constraints/grants e bucket privado. Ativar nova ARRIVE e proteger START/COMPLETE na função central, mantendo wrappers antigos, valor financeiro Homol e dados pré-pagamento. Usar novas migrations com timestamp válido posterior ao histórico existente; não editar migrations aplicadas.
2. Implementar reserva/upload/confirmação, descarte de reserva, seleção de rascunho, envio, decisão e nova versão de correção com revisão/locks/idempotência. Atualizar snapshots e canais existentes a cada mudança efetiva. Expor leitura paginada de histórico preservando fotos confirmadas sem envio.
3. Integrar Storage no cliente Supabase, Repository e captura por TakePicture/FileProvider. Persistir referências pendentes em SavedStateHandle vinculadas à sessão/ambiente; normalizar imagem antes da prévia/upload e limpar cache/intenção no logout e na invalidação do fingerprint existente. Não exigir câmera para consultar histórico.
4. Integrar tela/rota por papel, resumo no acompanhamento, retomada e correção. Separar início explícito da chegada. Bloquear aprovação enquanto houver foto sem apresentação bem-sucedida. Encerramento/cancelamento muda para consulta e preserva material.
5. Integrar “Ver vistoria” no histórico e consulta auditada no dashboard do suporte; download administrativo somente depois da autorização do funcionário para aquele caso/foto. Não alterar políticas financeiras ou decisões de disputa.
6. Compilar/lint/isolamento, revisar grants/RLS e cenários de compatibilidade, executar validação manual autorizada em Dev e registrar resultados reais no quickstart. Medir SCs separadamente; build não comprova fluxo funcional.

**Artefatos**: [modelo](data-model.md), [contrato de backend](contracts/guincho-inspection-api.md), [contrato de UI](contracts/guincho-inspection-ui.md), [roteiro](quickstart.md).

## Rollout e riscos materiais

- Disponibilização do app compatível e ativação do backend precisam ser coordenadas. Backend novo deve vir antes de novos atendimentos completos no app novo; apps antigos não recebem dispensa. Informar atualização necessária para continuar vistoria/revisão se estiverem incompatíveis. Nenhuma versão do app pode liberar transporte no servidor sem a aprovação exigida.
- Primeiro validar em Dev, cujo backend é separado. Homol e Prod compartilham backend real; aplicar migration nele afeta ambos. Planejamento não aplica migrations, publica app nem movimenta pagamentos.
- Manter serviço já chegado com flag false; pedido antigo ainda a caminho recebe vistoria ao chegar. Não fazer rollback apagando tabelas/fotos ou retirando o bloqueio de atendimentos já sujeitos à vistoria; corrigir aditivamente se necessário.
- Conservação acompanha o histórico; não existe neste repositório um prazo específico executável para fotos. Esta entrega mantém os registros e não cria remoção automática. Sem promessa de certificação jurídica/pericial.
- Prazo de atendimento existente pode cancelar uma vistoria pendente; consulta permanece. A reserva do prestador não é liberada apenas porque o cliente ainda está revisando.

## Complexity Tracking

Nenhuma violação constitucional. As adições têm responsabilidade direta na feature: captura/armazenamento privado, versionamento necessário ao registro anterior, gate de execução e leitura auditada pelo suporte.
