# Implementation Plan: Contas independentes e localização de prestadores

**Branch**: `[009-provider-location-accounts]` | **Date**: 2026-08-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-provider-location-accounts/spec.md`

## Summary

Separar cliente e empresa em identidades autenticadas de papel único, mantendo `profiles` apenas
para clientes e tornando `providers` uma empresa com identidade própria e uma conta Auth
proprietária. O cadastro empresarial passa a ser um fluxo público dedicado, seguido por onboarding
autenticado que salva empresa, modalidade, categorias/serviços e endereço geocodificado por uma
RPC atômica e idempotente. Providers legados continuam pertencendo ao cliente até uma transferência
assistida que troca somente a conta proprietária da empresa.

A descoberta usa Google Maps Compose e uma projeção PostGIS filtrada pelo UUID exato do serviço;
providers fixos e móveis aparecem publicamente pelo endereço. Somente durante um atendimento
`ACCEPTED` ou `IN_PROGRESS`, um provider móvel pode manter uma única posição corrente, publicada
aproximadamente a cada 10 segundos por um foreground service e distribuída aos participantes por
Supabase Broadcast privado. A posição fica desatualizada após 30 segundos e nunca forma histórico.

## Technical Context

**Language/Version**: Kotlin 2.2.10; SQL/PLpgSQL do PostgreSQL gerenciado pelo Supabase; TypeScript/Deno para Edge Functions

**Primary Dependencies**: Jetpack Compose BOM 2026.06.01, Material 3, Lifecycle 2.10.0, Navigation Compose 2.9.8, Supabase Kotlin 3.2.6 (Auth, Postgrest, Functions e Realtime), Ktor OkHttp 3.3.1, Google Play services Location 21.4.0, Google Maps Compose/Utils 8.4.0

**Storage**: Supabase Auth + PostgreSQL com PostGIS; tabelas públicas protegidas por RLS; tabela privada de provas efêmeras de geocodificação/migração; uma linha de localização corrente por atendimento; nenhuma persistência local de coordenadas

**Testing**: Sem testes automatizados, conforme constituição e escopo; validação manual com `assembleDebug`, `lintDebug`, Supabase CLI, dispositivos/emuladores Android 12–16 e inspeção de RLS/Realtime

**Target Platform**: Android minSdk 24, targetSdk 36, compileSdk 37; smartphones, paisagem e fonte até 200%

**Project Type**: Aplicativo Android nativo com backend Supabase versionado no mesmo repositório

**Performance Goals**: primeiro resultado/estado vazio do mapa em até 3 s p95; atualização móvel alvo de 10 s; marcador considerado desatualizado em 30 s; consulta espacial limitada a 200 resultados e renderizada com clustering

**Constraints**: um papel por conta/e-mail; nenhuma service-role no APK; permissão precisa obrigatória apenas no onboarding/tracking de provider; tracking em background somente por foreground service iniciado em tela visível; sem histórico; mapa público nunca usa posição móvel; credenciais e coordenadas sensíveis não aparecem em logs

**Scale/Scope**: dois destinos autenticados (cliente/empresa), cadastro e migração de empresa, catálogo paginado sem limite artificial de seleção, mapa por serviço, no máximo um atendimento ativo por provider nesta versão e uma posição corrente por atendimento

## Constitution Check

*GATE inicial e pós-design: aprovado.*

- **I — Simplicidade/KISS/YAGNI**: cada camada nova tem uso atual. Edge Function protege a chave de
  geocodificação; PostGIS resolve busca espacial; Realtime atende atualização do marcador; foreground
  service é necessário para continuidade visível em background. Não haverá histórico, frota,
  despacho completo, cache local ou framework de DI.
- **II — Android/MVVM**: UI stateless, ViewModels com `StateFlow`, Repositories para Supabase,
  localização e geocodificação, Coroutines e Navigation Compose. A DI manual existente será mantida.
- **III — Estado/UX**: sessão, permissão, verificação de endereço, mapa, cadastro, tracking e recência
  serão estados selados e mutuamente exclusivos; Material 3, design system e acessibilidade existentes
  serão reutilizados.
- **IV — Dados/segurança**: tipo de conta vem de tabela autoritativa, nunca de metadata mutável;
  owner é derivado de `auth.uid()`; mutações críticas usam RPC `SECURITY DEFINER` endurecida; RLS e
  Broadcast privado limitam participantes; dispositivo guarda somente a posição corrente em memória.
- **V — Qualidade proporcional**: nenhuma suíte de testes será criada. Compilação, lint, migrations,
  permissões, RLS, concorrência, foreground service e fluxos completos serão validados manualmente.

O design pós-Phase 1 permanece conforme a constituição. A tabela mínima `appointments` é uma âncora
de autorização exigida pelo tracking; ela não implementa despacho, aceite ou pagamento. As Edge
Functions são necessárias para ocultar a chave de Geocoding e renovar coordenadas dentro da política
de retenção escolhida.

## Project Structure

### Documentation (this feature)

```text
specs/009-provider-location-accounts/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── account-provider-database.md
│   ├── geocoding-edge-function.md
│   ├── location-realtime.md
│   └── navigation-session.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/
│   ├── account/
│   │   ├── data/AccountContextDtos.kt
│   │   ├── model/AccountContext.kt
│   │   └── repository/AccountContextRepository.kt
│   ├── auth/
│   │   ├── model/AppSessionState.kt
│   │   └── repository/SessionRepository.kt
│   ├── catalog/
│   │   ├── model/ServiceCatalogModels.kt
│   │   └── repository/ServiceCatalogRepository.kt
│   ├── location/
│   │   ├── data/FusedLocationDataSource.kt
│   │   ├── model/DeviceLocationModels.kt
│   │   └── repository/DeviceLocationRepository.kt
│   ├── app/PitGoAppContainer.kt
│   ├── navigation/{PitGoNavHost.kt,PitGoRoutes.kt}
│   └── network/SupabaseClientProvider.kt
├── features/
│   ├── providersignup/{model,repository,ui,viewmodel}/
│   ├── providerregistration/{data,model,repository,ui,validation,viewmodel}/
│   ├── providerdashboard/{model,repository,ui,viewmodel}/
│   ├── providermigration/{data,model,repository,ui,viewmodel}/
│   ├── providermap/{data,model,repository,ui,viewmodel}/
│   ├── providertracking/{data,model,repository,service,viewmodel}/
│   ├── appointmenttracking/{data,model,repository,ui,viewmodel}/
│   ├── home/{ui,viewmodel}/
│   ├── login/{ui,viewmodel}/
│   └── signup/{repository,ui,viewmodel}/
└── MainActivity.kt

app/src/main/res/
├── drawable/ic_notification_location.xml
└── values/strings.xml

supabase/
├── config.toml
├── functions/
│   ├── geocode-provider-address/index.ts
│   └── refresh-provider-geocodes/index.ts
└── migrations/
    ├── 20260821000000_account_roles_provider_ownership.sql
    ├── 20260821010000_provider_geography_discovery.sql
    └── 20260821020000_appointment_current_locations.sql
```

**Structure Decision**: manter um único módulo Android organizado por feature, os serviços
compartilhados em `core`, migrations versionadas em `supabase/migrations` e duas Edge Functions
estreitas. O cadastro empresarial existente será evoluído no próprio pacote; não será criada uma
segunda implementação concorrente. `RequestServiceScreen` permanece fora desta alteração, mas o
clique da Home passa a navegar ao mapa usando o UUID do serviço carregado do catálogo real.

## Complexity Tracking

Nenhuma violação da constituição foi identificada. As responsabilidades adicionais são diretamente
exigidas por autenticação de dois papéis, geocodificação segura, mapa e tracking autorizado.
