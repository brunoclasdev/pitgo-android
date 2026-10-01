# Implementation Plan: Alerta e Home do guincheiro

**Branch**: `feature/refinamento-guincho` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

**Input**: `specs/029-guincho-alerta-home/spec.md`. O identificador lógico retornado por setup-plan é `029-guincho-alerta-home`; a branch Git permanece `feature/refinamento-guincho`.

## Summary

Adicionar Home do guincheiro com mapa próprio e apresentar uma solicitação paga em card global sobre qualquer tela do prestador, preservando a navegação até a resposta. Reutilizar a descoberta do pedido, aceite/recusa, notificações e acompanhamento existentes. Corrigir a navegação automática de ofertas GUINCHO_DIRECT pendentes e a repetição sonora por revisão. Nenhuma alteração de banco, Edge Function, pagamento ou dependência é necessária ao desenho.

## Technical Context

**Language/Version**: Kotlin 2.2.10; Gradle Kotlin DSL; AGP 9.1.1, conforme catálogo atual.

**Primary Dependencies**: Compose BOM 2026.06.01, Material3, Lifecycle 2.10.0, Navigation Compose 2.9.8, MapLibre 13.5.0, Supabase 3.2.6 e NotificationCompat existentes. Sem upgrades neste escopo.

**Storage**: Pedidos existentes no Supabase. Preferências de alerta por conta/pedido usando DataStore existente somente para impedir repetição sonora após recriação do processo; sem copiar dados pessoais. Estado de câmera/localização efêmero.

**Testing**: Compilação dos três flavors e lint proporcional; validação funcional manual pelo usuário, conforme orientação da sessão. Não gerar testes, mocks nem executar testes manuais nos devices por conta própria.

**Target Platform**: Android minSdk 24, targetSdk 36, compileSdk 37; dev, homol e prod.

**Project Type**: Aplicativo Android nativo com backend existente.

**Performance Goals**: Card e notificação em até 10 segundos em 95% das amostras sob condições de SC-001/002; posição inicial em até 10 segundos em 95% das aberturas sob SC-004. Manter polling existente de 5 segundos, sem observadores duplicados no mesmo escopo de UI.

**Constraints**: Oferta paga PENDING com prazo original de 60 segundos; permissão de localização não pode bloquear perfil/histórico ou oferta já liberada. Som respeita configurações do sistema. Sem dados pessoais em logs, notificação ou armazenamento de deduplicação.

**Scale/Scope**: Um prestador autenticado, um pedido vigente e uma notificação por pedido por aparelho; sem descoberta de outros guinchos na Home do prestador.

## Constitution Check

*GATE antes da pesquisa e após o desenho: PASS.*

- I: Apenas Home e alerta solicitados; reaproveitar fluxo 028 e infraestrutura de PitTec.
- II: MVVM existente; UI emite eventos, ViewModel decide estado, Repository executa acesso. Nenhum novo framework.
- III: Estados explícitos, StateFlow, Material3, tema centralizado, fonte ampliada, toque acessível; ViewModels sem Context.
- IV: Dados de cliente somente no pedido pago autorizado; backend continua autoridade de prazo/resposta; permissões e falhas explícitas.
- V: Nenhum teste automático criado, nenhuma mudança de dependência e nenhum componente sem integração. Manual fica com o usuário.
- Pós-desenho: contratos não exigem novas tabelas nem camadas de repasse. DataStore tem finalidade concreta: deduplicação sonora após recriação do serviço.

## Project Structure

### Documentation (this feature)

```text
specs/029-guincho-alerta-home/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/guincho-home-alert.md
└── checklists/requirements.md
```

`tasks.md` será produzido por speckit-tasks.

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/navigation/PitGoNavHost.kt
├── core/app/PitGoAppContainer.kt
├── core/map/ui/MapLibreMapHost.kt
├── core/location/repository/DeviceLocationRepository.kt
├── features/guincho/{model,viewmodel,ui}/
├── features/mobiledispatch/{repository,viewmodel,ui}/
├── features/providerdashboard/{ui,viewmodel}/
└── features/providertracking/{notification,service}/
app/src/main/res/values/strings.xml
```

**Structure Decision**: Componentes específicos de Home e card em features/guincho. Uma instância de ProviderIncomingRequestViewModel por sessão do guincheiro no escopo acima do NavHost controla o pedido e é compartilhada com o card; não instanciar outro observador por tela. ViewModel da Home controla localização/câmera e utiliza DeviceLocationRepository. MapLibreMapHost permanece compartilhado; adicionar callback de gesto do usuário se necessário para distinguir gesto e movimento programático.

## Phase 0 — Pesquisa

Concluída em [research.md](research.md): lacunas verificadas no código, reutilização, deduplicação, limites de notificação e gestos. Sem decisões pendentes.

## Phase 1 — Desenho

1. Definir seleção de Home para prestador móvel de Guincho usando os dados existentes de modo/serviço, mantendo demais perfis.
2. Integrar mapa, posição atual, recuperação e Recentralizar. Observar localização apenas durante Home visível; disponibilidade/publicação em segundo plano seguem o serviço existente.
3. Elevar o observador de pedidos do guincheiro ao escopo autenticado e inserir card único sobre o NavHost. Remover somente a navegação automática para ProviderRequest no PENDING GUINCHO_DIRECT; manter acompanhamento para estados aceitos/em execução/aguardando confirmação.
4. Reutilizar respond e projeção de remuneração, prazo original e recuperação. Desabilitar ações durante envio e quando o prazo vencer. Recusa confirmada volta à Home; aceite confirmado abre tracking uma vez.
5. Fortalecer notificação: ID estável, atualização silenciosa, prazo de expiração, limpeza de notificações e dados visíveis por conta; preservar o marcador durável mínimo de deduplicação até encerramento ou expiração, sem dados pessoais. Retomar MainActivity e consultar pedido vigente, nunca executar resposta pela notificação.
6. Dimensionar card rolável com área de mapa reservada na Home; no Perfil/Histórico, preservar a tela atual sem exigir mapa; câmera segue a posição considerando a área efetivamente livre. Validar retorno à Home depois da confirmação final e regressões dos demais perfis.

Contratos: [contracts/guincho-home-alert.md](contracts/guincho-home-alert.md). Modelo: [data-model.md](data-model.md). Validação: [quickstart.md](quickstart.md).

## Complexity Tracking

Nenhuma violação ou exceção à constituição.

## Implementação — levantamento

T001/T002: working tree inicialmente contém apenas a documentação 029. ProviderSnapshot fornece locationMode; MOBILE identifica Guincho pois o cadastro associa automaticamente o serviço implícito, conforme feature 016 FR-003. Não há coleção de serviços no snapshot e não é necessário consultar nome traduzido. Pedido global reutiliza ProviderIncomingRequestViewModel; restauração genérica do prestador MOBILE será delegada a ele para evitar observadores duplicados. Ignore files existentes cobrem Gradle, APKs e secrets.
