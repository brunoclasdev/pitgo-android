# Implementation Plan: Seleção de serviços condicionada à modalidade do prestador

**Branch**: `016-conditional-provider-services` | **Date**: 2026-09-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/016-conditional-provider-services/spec.md`

## Summary

Ao cadastrar um prestador, a etapa de escolha de serviço no catálogo passa a depender da
modalidade: Fixo continua exigindo a escolha manual de pelo menos um serviço (ex.: borracharia
escolhendo pneus e rodas); Móvel (Guincho) e PitTec deixam de exibir essa etapa, pois cada um tem um
único serviço implícito no catálogo (`dispatch_kind = MOBILE_SEQUENTIAL` e
`PITTEC_AUTO_SEQUENTIAL`, respectivamente), atribuído automaticamente. Móvel continua coletando
preço/duração do prestador para esse serviço implícito; PitTec não coleta preço nem duração, pois
seu valor (fixo + variável por distância, conforme Clarifications do spec) é definido centralmente
pela plataforma, não pelo prestador. A implementação é inteiramente no app Android — nenhuma
migração ou mudança de RPC é necessária.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11 bytecode

**Primary Dependencies**: Jetpack Compose BOM 2026.06.01, Lifecycle 2.10.0, Kotlin
Coroutines/StateFlow, Supabase Kotlin 3.2.6 (Postgrest)

**Storage**: PostgreSQL gerenciado pelo Supabase; nenhuma tabela ou coluna nova — leitura estendida
de `public.services.dispatch_kind`, já existente

**Testing**: validação manual orientada por cenários (`quickstart.md`), build Android
debug/`lintDebug`; a constituição não exige testes automatizados e o projeto não possui
infraestrutura de testes configurada

**Target Platform**: Android API 24+, compile SDK 37, target SDK 36

**Project Type**: aplicativo Android de módulo único; funcionalidade inteiramente client-side

**Performance Goals**: resolução do serviço implícito de Móvel/PitTec deve completar dentro do
tempo já aceitável de carregamento do catálogo de cadastro (mesma ordem de grandeza da consulta de
categorias já existente); nenhum requisito de performance novo

**Constraints**: nenhuma migração de banco; nenhuma mudança de contrato no RPC
`create_provider_registration`; comportamento de Fixo não pode regredir; comportamento de Guincho já
implementado (rastreamento, despacho) não pode ser afetado

**Scale/Scope**: um fluxo existente (cadastro de prestador) ajustado; três modalidades; nenhuma tela
nova, nenhuma rota nova

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação pré-pesquisa | Verificação pós-design |
|---|---|---|
| Simplicidade e entrega orientada ao requisito | PASS — resolve exatamente a regra pedida, sem infraestrutura nova. | PASS — nenhuma migração, nenhuma RPC nova, nenhuma abstração especulativa; reaproveita 100% da validação/envio existentes. |
| Android nativo moderno com MVVM | PASS — mudança fica em model/DTO, ViewModel e Composable já existentes do `features/providerregistration`. | PASS — nenhum novo padrão arquitetural introduzido. |
| Estado previsível e experiência consistente | PASS — reaproveita `ProviderRegistrationDraft`/`ProviderEditingContent`, estados já explícitos. | PASS — modalidade continua sendo um campo único e explícito controlando a UI, sem booleans paralelos. |
| Dados, segurança e falhas explícitas | PASS — nenhuma nova superfície de dados sensíveis; falha de catálogo inconsistente reaproveita classificação de erro já existente. | PASS — resolução do serviço implícito distingue ausência/duplicidade e falha de forma compreensível, sem expor detalhe técnico. |
| Qualidade proporcional e evolução sustentável | PASS — sem testes automatizados não solicitados; mudança pequena e localizada. | PASS — nenhum código morto deixado; seção de oferta antiga permanece só para Fixo. |

Não há violação constitucional a justificar.

## Project Structure

### Documentation (this feature)

```text
specs/016-conditional-provider-services/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── provider-registration-service-rule.md
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/features/providerregistration/
├── model/ProviderRegistrationModels.kt        # CatalogService ganha dispatchKind
├── data/ProviderRegistrationDtos.kt           # CatalogServiceDto ganha dispatch_kind
├── repository/ProviderRegistrationRepository.kt  # novo método: resolver serviço implícito por dispatch_kind
├── viewmodel/ProviderRegistrationViewModel.kt # onLocationModeSelected monta/limpa a oferta implícita
└── ui/ProviderRegistrationScreen.kt           # esconde a seção de oferta para Móvel/PitTec; forma reduzida para Móvel
```

Nenhum diretório ou arquivo novo além do já existente em `features/providerregistration`; nenhuma
mudança em `supabase/migrations` ou `supabase/functions`.

**Structure Decision**: manter a organização feature-first já adotada; toda a mudança fica contida
em `features/providerregistration`, sem tocar `providerdashboard`, `pittec` ou os arquivos de
despacho (`mobiledispatch`), que já resolvem o Guincho/PitTec por outros meios depois do cadastro.

## Complexity Tracking

Nenhuma violação: a funcionalidade reduz a complexidade do fluxo existente (uma etapa a menos para
Móvel e PitTec) sem introduzir camada, dependência ou padrão novo.
