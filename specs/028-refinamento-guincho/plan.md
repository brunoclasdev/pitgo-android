# Implementation Plan: Refinamento do Guincho

**Branch**: `feature/refinamento-guincho` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

**Input**: `specs/028-refinamento-guincho/spec.md`

## Summary

Contratação dirigida de Guincho: confirmação de retirada/destino/veículo, seleção no mapa, cotação viária confiável, pagamento e oferta exclusiva ao profissional escolhido. Reaproveitar mapas, checkout, reconciliação e acompanhamento. R$ 3/km configurável, 10% sobre remuneração e taxas existentes; conclusão bilateral e contestação resolvida no dashboard. Introduzir modalidade `GUINCHO_DIRECT` preservando contratos e pedidos `MOBILE_SEQUENTIAL`/PitTec antigos.

## Technical Context

**Language/Version**: Kotlin com Compose Compiler/Serialization 2.2.10, SQL PostgreSQL 17, TypeScript/Deno nas Edge Functions; TypeScript 5 no dashboard.

**Primary Dependencies**: versões do repositório: AGP 9.1.1, Compose BOM 2026.06.01, Navigation 2.9.8, Supabase Kotlin 3.2.6, Ktor 3.3.1, MapLibre 13.5.0; dashboard Next 16.3.6/React 19.2.8. Sem novas bibliotecas ou atualização de versões.

**Storage**: Supabase PostgreSQL/PostGIS, appointments/payments existentes e novas regras, rascunhos, cotações e contestações.

**Testing**: build/lint existentes, revisão SQL/RLS e validação manual de quickstart.md. Nenhuma suíte nova, conforme constituição.

**Target Platform**: Android minSdk 24/targetSdk 36; Supabase e dashboard web existente.

**Project Type**: aplicativo Android com backend e dashboard em repositório separado.

**Performance Goals**: estados em até 10 s em 95% das verificações; polling de segurança 5 s no fluxo ativo; uma cotação em voo por seleção, descartando resposta obsoleta.

**Constraints**: preços no backend; sem cobrança por haversine; dados identificáveis ao prestador só após aprovação; cotação paga imutável; sem secrets no app/browser; contestação não bloqueia prestador.

**Scale/Scope**: FR-001–023; limite inicial 200 marcadores/25 km seguindo descoberta existente; um pedido operacional ativo por guincheiro, casos contestados fora dessa exclusividade. Sem contratação automática de outro profissional.

## Constitution Check

PASS antes da pesquisa e após o desenho. Reuso de componentes e infraestrutura; UI → ViewModel → Repository e StateFlow; enums explícitos; loading/erro/vazio e acessibilidade Material; cálculo confiável, RLS, comandos transacionais, autorização MANAGER+ e auditoria; validação proporcional sem gerar testes não solicitados. Dashboard separado é necessário ao requisito, não uma nova aplicação. Nenhuma exceção à constituição.

## Project Structure

### Documentation (this feature)

```text
specs/028-refinamento-guincho/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
└── contracts/{guincho-api,guincho-ui}.md
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── features/guincho/{data,model,repository,ui,viewmodel}/
├── features/{providermap,payment,mobiledispatch,appointmenttracking}/
├── features/{providertracking,providerdashboard}/
└── core/{navigation,app,environment}/
supabase/
├── migrations/
└── functions/{guincho-quote,mp-*}/
```

**Structure Decision**: feature guincho para entrada/mapa/cotação; checkout, tracking e notificações compartilhados com ramos dirigidos explícitos. Dashboard localizado em `/Users/brunocesar/projetos-web/pitgo`: integrar `src/services/atendimentos.ts`, nova `src/services/contestacoes.ts`, rota `src/app/(dashboard)/contestacoes/` e tipos. Ler AGENTS.md/documentação local Next antes de implementar nesse repositório. Nesta fase não editar o dashboard.

## Implementation Sequence

1. Schema aditivo, regras, modalidade, índices operacionais, RLS e contratos. Auditar constraints/enums, inclusive pagamentos, candidatos e jobs.
2. Cotação viária, reserva dirigida, checkout, aprovação tardia e compensação durável, sem avanço sequencial.
3. Android entrada/mapa/total e pagamento; oferta paga exclusiva, restauração e notificações.
4. Cheguei/início/conclusão/confirmação/contestação; histórico separado do pedido ativo.
5. Dashboard fila/detalhe/decisão com justificativa, autorização e refund pipeline.
6. Validar três flavors e regressões; backend antes de APK/dashboard, entrada dirigida só com contrato compatível.

Fase 0: [research.md](research.md). Fase 1: [data-model.md](data-model.md), [contratos](contracts/guincho-api.md) e [quickstart.md](quickstart.md). Sem decisões pendentes. Hooks não registrados (`.specify/extensions.yml` ausente). Tasks ficam para `$speckit-tasks`.

## Complexity Tracking

Nenhuma violação ou complexidade adicional sem requisito demonstrável.

## Implementation inventory — 2026-09-30

Android em `feature/refinamento-guincho`; dashboard em `develop`, com alterações locais pré-existentes em Home/layout/auth/tipos que devem ser preservadas. AGENTS.md e guias locais Next de autenticação/formulários lidos. Ignores Android/Docker/dashboard cobrem builds, dependências, logs e secrets; nenhuma dependência nova. Modalidade dirigida registrada no pedido, independente da modalidade do catálogo, preservando contratos de pedidos móveis antigos. Integrações: `reserve_order_payment`, reconciliação Orders, getters mobile/PitTec, broadcasts privados e jobs de expiração. Dados de veículo usam `vehicles.user_id`; regras de taxas vêm de `pittec_pricing_rules`, sem assumir taxa do exemplo.
