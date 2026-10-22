# Implementation Plan: Vistoria ampliada e rota de transporte do Guincho

**Branch**: `feature/refinamento-guincho` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: `specs/031-guincho-vistoria-rota/spec.md`. Identificador lógico retornado pelo Spec Kit: `031-guincho-vistoria-rota`; a branch Git real permanece independente, conforme a especificação. Nenhuma branch foi criada/trocada.

## Summary

Ampliar o álbum para 17 pontos com apenas FRONT/REAR/LEFT/RIGHT obrigatórios; manter a compatibilidade de fotos/versões antigas. Substituir o estado de captura único por pendências duráveis e estados individuais, com retorno imediato ao álbum, preparação amostrada, miniaturas privadas e retomada automática ao reabrir o PitGo. Preservar a aprovação de todo o conjunto e o início manual posterior.

Após início confirmado, mostrar no mapa do guincheiro o percurso viário até o destino contratado. Nova integração autenticada usa o roteador OSRM-compatible já configurado, dados autoritativos do atendimento e snapshot/lease específico do Guincho. Recalcular somente em desvio confirmado, com limite distribuído de chamadas, mantendo PitTec e cotação intactos. A entrega se apoia no GPS/observação atualizados já presentes no workspace, sem reverter essas mudanças.

## Technical Context

**Language/Version**: Kotlin/Compose plugin 2.2.10 no catálogo, AGP 9.1.1 e bytecode Java 11; PostgreSQL/PLpgSQL e TypeScript/Deno nas Edge Functions existentes. Confirmar toolchain efetiva no build, sem upgrade geral.

**Primary Dependencies**: Compose BOM 2026.06.01/Material3, Activity Compose 1.13.0, Lifecycle 2.10.0, Navigation Compose 2.9.8, Supabase Kotlin 3.2.6/Storage, Ktor 3.3.1, Coil 3.3.0, ExifInterface 1.4.2, MapLibre 13.5.0 e kotlinx.serialization já instalados. AtomicFile, FileProvider e coroutines existentes; nenhuma dependência nova prevista.

**Storage**: Tabelas de vistoria e bucket privado guincho-inspections existentes; colunas aditivas de categoria/miniatura e RPCs v2. Files privados duráveis por ambiente/conta/atendimento e journal JSON atômico por captura; cache de downloads separado. Uma tabela de snapshot/lease operacional da rota por atendimento. Sem Room, imagens em Bundle/DataStore ou serviço permanente de upload.

**Testing**: Build Dev/Homol/Prod, lint Homol, isolamento de ambiente, análise da Edge Function e roteiro funcional manual com duas contas em Dev e aparelhos de homologação. Sem criação de suítes, testes, mocks ou fixtures, conforme constituição. Esta fase só valida documentos; build e ensaios funcionais serão executados na implementação.

**Target Platform**: Android minSdk 24, targetSdk 36, compileSdk 37; telefones e janelas adaptativas. Supabase Auth/Postgres/Storage/Edge Functions e roteador OSRM-compatible já usado pelo Guincho.

**Project Type**: App Android MVVM com backend Supabase. Não criar módulos Gradle, nova aplicação web ou arquitetura de sincronização genérica.

**Performance Goals**: SC-001–009: miniatura local/álbum em <=2 s após callback; resposta visual em <=500 ms; salvamento <=15 s; retomada iniciada <=5 s; percurso inicial/recálculo <=5 s; sincronização entre participantes <=10 s; miniaturas/ampliação <=5 s, nas proporções e condições de referência da spec.

**Constraints**: Original JPEG <=5 MiB/2560 px; thumb <=256 KiB/480 px; normalização única, mutações/envio serializados por atendimento e até dois downloads priorizados. Nenhum falso READY/aprovado; aprovação depende também da apresentação válida de originais. Captura aceita é preservada em cancelamento/rotação; nova versão enviada permanece imutável. Upload não precisa continuar com o app encerrado. Sem rota direta como substituto de caminho viário. Secrets/URLs com coordenadas e imagens nunca em logs.

**Scale/Scope**: 17 pontos nomeados por versão, mais detalhes livres existentes; um pipeline de envio ativo e um pedido de rota em voo por atendimento. Fila local até 512 MiB, reserva mínima de espaço 64 MiB e cache de leitura 128 MiB; limites não removem pendências nem criam limite comercial de fotos. Paginação atual 20 fotos/10 versões, clamps 100/50. Rota <=10000 pontos/2000 km; no máximo uma admissão de cálculo a cada 5 s por atendimento. Sem novo objetivo global de usuários/TPS.

## Constitution Check

*GATE antes da Fase 0: PASS. Reavaliação após a Fase 1: PASS.*

| Princípio | Antes da pesquisa | Depois do desenho |
| --- | --- | --- |
| I. Simplicidade e requisito atual | Ampliar vistoria e rota, preservando fluxo financeiro | Reutilizar Repository/Storage/renderer; journal de arquivos e cache de rota têm uso direto; sem sync genérico, banco local ou infraestrutura nova de mapas |
| II. Android nativo e MVVM | Manter Compose, ViewModel, StateFlow, injeção manual | UI emite eventos; SyncViewModel da feature ativa Repository na raiz autenticada; data sources cuidam de arquivos; nenhuma dependência externa nova |
| III. Estado e experiência | Etapas, falhas e aprovação explícitas | Estado por foto, grade lazy, portrait local com fallback adaptativo; acessibilidade, retorno à transação e rota somente após início confirmado |
| IV. Dados e segurança | Preservar RLS, papéis, versão e guard financeiro | Paths exatos reservados, assets imutáveis, replay autorizado, geração por sessão, backup excluído; endpoint usa identidade validada antes de cache e service-role; sem coordenadas do body |
| V. Qualidade proporcional | Não gerar testes ou refatorações alheias | Checks reais e roteiro manual; leitura otimizada, memória/concurrency limitadas; novas migrations apenas na implementação e compatibilidade v1/v2 explícita |

Nenhuma exceção necessária. A persistência de fotos pendentes é requisito explícito; não usar cache removível como única garantia. A nova tabela/Edge Function isola a etapa do Guincho sem alterar os contratos de rota/cobrança de PitTec. Orientação é best-effort do sistema em telefones, com apresentação vertical estável e restauração de estado também em janelas que possam girar.

## Project Structure

### Documentation (this feature)

```text
specs/031-guincho-vistoria-rota/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── inspection-api.md
│   ├── inspection-ui.md
│   └── guincho-route-api.md
└── checklists/requirements.md
```

`tasks.md` será gerado por `$speckit-tasks`; não pertence à saída desta fase.

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/app/PitGoAppContainer.kt
├── core/navigation/PitGoNavHost.kt
├── core/routing/model/RouteModels.kt                  # geometria existente reutilizada
├── core/map/ui/ParticipantMapStyleController.kt       # evitar reaplicar linha inalterada
├── core/map/ui/MapLibreMapHost.kt                     # callback do percurso efetivamente renderizado
├── features/guincho/
│   ├── data/GuinchoInspectionCaptureFiles.kt
│   ├── data/GuinchoInspectionPendingStore.kt           # novo, journal/arquivos duráveis
│   ├── data/GuinchoInspectionDtos.kt
│   ├── data/GuinchoRouteDtos.kt                        # novo, contrato distinto de PitTec
│   ├── model/GuinchoInspectionModels.kt
│   ├── model/GuinchoRouteModels.kt                     # novo, rota/estado/progresso
│   ├── repository/GuinchoInspectionRepository.kt
│   ├── repository/GuinchoRouteRepository.kt            # novo
│   ├── viewmodel/GuinchoInspectionViewModel.kt
│   ├── viewmodel/GuinchoInspectionSyncViewModel.kt     # novo, raiz autenticada
│   └── ui/{GuinchoInspectionScreen,GuinchoTrackingContent}.kt
└── features/appointmenttracking/{model,viewmodel}/
app/src/main/res/xml/{guincho_inspection_file_paths,backup_rules,data_extraction_rules}.xml
app/src/main/res/values/strings.xml
supabase/migrations/                                  # novas migrations aditivas
supabase/functions/guincho-route/index.ts              # novo
supabase/config.toml
```

**Structure Decision**: Domínio da vistoria e rota permanecem em features/guincho. Compartilhar apenas geometria e renderer existentes. O runner fica no Repository atual; o PendingStore concentra persistência atômica. SyncViewModel é necessário para retomar fora do álbum sem colocar acesso ao Repository na UI nem regra de fotos no SessionViewModel. Não mudar telas/serviços de outras modalidades ou o dashboard de suporte como pré-requisito.

## Phase 0 — Pesquisa concluída

[research.md](research.md) resolve catálogo compatível, fila durável, orientação, amostragem, miniaturas, idempotência, autorização, cache de rota e desvio. Fonte local identifica bloqueio global durante upload, cancelamento de todas as imagens a cada revisão, normalização in-place, cache removível e ausência de polilinha na tela do Guincho. Documentação oficial confirma os limites de lifecycle/orientação e os contratos de Storage/roteador. Não restam decisões técnicas abertas para a Fase 1.

## Phase 1 — Desenho e sequência de integração

1. **Schema/API compatíveis**: criar migrations novas para detail_point/miniaturas e unicidade, ampliar guards/cópia do manifesto e paths exatos. RPCs de leitura/preparação v2; v1 mantém enum/projeção e respeita requisitos de assets da linha. Original e miniatura são confirmados juntos; substituição não remove a anterior antes do sucesso. Manter submissão bloqueada por qualquer reserva ativa.
2. **Persistência e runner**: journal antes do launch; arquivos brutos/derivados separados, IDs e hashes locais congelados. Commits atômicos protegidos por geração/Mutex. Repository aplica fila retomável, reconhecimento de READY remoto, retries classificados e sessão/versão. Raiz autenticada ativa pela visibilidade/conectividade. Logout/troca de ambiente invalida geração, cancela operações e limpa apresentação/cache de leitura em IO, preservando journals e arquivos de capturas aceitas ainda não confirmadas, privados por conta/ambiente. Retomar somente após autenticação da mesma conta e revalidação de permissões; remover pendências somente após salvamento confirmado ou descarte explícito permitido. Excluir backup/transferência e restringir URI de câmera.
3. **Álbum e revisão**: 17 quadros lazy, fotos livres, estado individual e próxima captura liberada durante envio. Portrait restrito e apresentação estável em janelas grandes; launcher estável e recuperação sem relançamento duplicado. Merge preserva imagens por ID, miniaturas carregam primeiro, originais validados para aprovação com prioridade da visualização explícita. Preservar retorno do cliente à transação e versões históricas.
4. **Rota server-side**: tabela operacional com lease/budget e leitura autorizada; claims/commits service-role revalidam dados do atendimento. Edge Function autenticada obtém origem publicada e destino contratado e solicita GeoJSON ao roteador. Timeout/backoff preservam última rota; cleanup impede resultado tardio após encerramento.
5. **Rota Android**: integrar ao tracking somente para guincheiro em IN_PROGRESS. Reagir ao início confirmado e à retomada, mesmo sem fix novo. Deduplicar chamadas, detectar desvio com série de fixes precisos e reavaliar o último ao concluir. Renderer recebe polilinha própria memoizada por versão e só reaplica geometria alterada, sem fallback direto; enquadramento respeita gesto e não recentra para retirada. Não mudar lógica de PitTec.
6. **Validação/entrega**: build três flavors, lint/isolamento, checagem TS e revisão de migrations/RLS; executar cenários manuais autorizados, medir SCs e registrar evidências. Preparar eventos de captura/primeiro frame/salvamento em T030 e início/desvio/primeiro frame da rota em T046, antes das validações; usar relógio monotônico ou trace conforme quickstart. T061 revisa as medições já disponíveis. Inventariar funções/secrets do projeto-alvo antes de deploy; backend primeiro, APK compatível depois. Antes de cada aplicação, revisar todo o conjunto pendente mostrado pelo dry-run, inclusive migrations de outras histórias; serializar aplicação ao mesmo projeto e aguardar arquivos/dependências/guards/permissões completos. Paralelismo de desenvolvimento não autoriza aplicar migration incompleta.

Interfaces e invariantes estão em [data-model.md](data-model.md) e [contracts](contracts/inspection-api.md); procedimentos em [quickstart.md](quickstart.md).

## Rollout e riscos materiais

- Só alterações aditivas em migrations novas; não editar as oito migrations da feature 030 já aplicadas. Readers v1 continuam recebendo point legado; readers v2 conhecem categoria/variante. Não reclassificar ou regenerar ativos de versões enviadas/aprovadas.
- Reservas v1 pendentes usam original-only; novas reservas v2 exigem original+thumb. Preparação/confirm/replay distinguem pela própria linha. Importar estado local legado somente quando conta, atendimento, ponto e intenção forem comprováveis; não adivinhar associação de arquivos sem contexto.
- Reconexão pode ocorrer após reserva, upload, confirmação ou mudança de versão. Reconciliação precede recaptura/limpeza; outra sessão fechando a versão gera bloqueio explícito, não envio silencioso para versão nova.
- Portrait não é imposição universal no Android 16/17; recuperação independente de rotação é gate de entrega. Confirmar em telefones usados e janela >=600dp.
- Homol/Prod compartilham backend; Dev é separado. Não usar serviços/pagamentos reais para produzir evidências de teste. Roteador e função precisam existir/configurar-se no projeto-alvo; presença de fonte não prova deploy remoto.
- Não fazer downgrade apagando fotos/colunas ou afrouxando aprovação/início. Corrigir aditivamente; app legado pode ler a projeção compatível. Sem garantia de retomada após remoção de dados/desinstalação, com tratamento explícito de arquivo ausente conforme spec.
- As alterações de GPS já no workspace fazem parte da base considerada; preservá-las e conferir integração ao produzir tasks/implementação.

## Complexity Tracking

Nenhuma violação constitucional. Novos componentes têm responsabilidade demonstrada: PendingStore preserva capturas; SyncViewModel permite retomada fora do álbum; DTO/Repository específicos validam um contrato de rota diferente; snapshot/lease limita concorrência entre Edge instances. Não há razão para banco relacional local, WorkManager, nova biblioteca de imagem/mapas ou nova camada UseCase.
