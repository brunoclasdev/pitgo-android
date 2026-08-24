# Implementation Plan: Busca de endereço por CEP

**Branch**: `[011-cep-address-lookup]` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-cep-address-lookup/spec.md`

## Summary

Adicionar ao cadastro de empresa uma consulta explícita de CEP pelo ViaCEP. A pessoa informa oito
dígitos e aciona `Buscar CEP`; rua, complemento, bairro, cidade e UF são preenchidos em uma única
atualização do draft, enquanto o número permanece manual. Os campos continuam editáveis e uma
mudança posterior do CEP invalida somente os valores que vieram da consulta anterior.

A integração reutilizará Ktor OkHttp e kotlinx.serialization já presentes no app, por meio de um
`ViaCepAddressLookupRepository` dedicado, sem autenticação, cache persistente, backend intermediário
ou nova dependência. O `ProviderRegistrationViewModel` existente será o dono do job, do identificador
monotônico da tentativa e do estado explícito da consulta. A tela atual ganhará o controle
responsivo de busca, feedback acessível e correções no mecanismo geral de erro/foco para marcar
todos os campos inválidos com seus respectivos motivos.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11 e Gradle Kotlin DSL

**Primary Dependencies**: Android SDK, Jetpack Compose BOM 2026.06.01, Material 3, Lifecycle/ViewModel
2.10.0, Coroutines, Ktor Client OkHttp 3.3.1 e kotlinx.serialization JSON 1.9.0; nenhuma dependência
nova

**Storage**: N/A para a consulta; resultado permanece somente no draft em memória e o cadastro
existente continua responsável pela persistência final no Supabase

**Testing**: sem testes automatizados, conforme constituição; validação manual com
`:app:assembleDebug`, `:app:lintDebug`, dispositivo/emulador, ViaCEP real e cenários de falha

**Target Platform**: Android minSdk 24, targetSdk 36 e compileSdk 37; smartphones em retrato e
paisagem, teclado aberto e fonte até 200%

**Project Type**: aplicativo Android nativo de módulo único, organizado por feature

**Performance Goals**: 95% das consultas válidas devem produzir endereço ou mensagem conclusiva em
até 3 segundos; apenas uma chamada por tentativa; nenhuma recomposição deve reiniciar a consulta

**Constraints**: consulta somente por ação explícita; exatamente oito dígitos; preenchimento manual
continua possível após falha temporária; nenhum dado além do CEP é enviado ao ViaCEP; sem Retrofit,
proxy Supabase, cache, persistência, busca automática, testes ou mudança de navegação

**Scale/Scope**: um formulário existente, um endpoint GET, cinco campos derivados e uma tentativa
ativa por `ProviderRegistrationViewModel`

## Constitution Check

*GATE inicial e pós-design: aprovado sem exceções.*

- **I — Simplicidade/KISS/YAGNI**: a tela, o ViewModel, a validação e a pilha Ktor existentes são
  estendidos. Não haverá nova tela, Use Case, Retrofit, proxy, cache ou infraestrutura especulativa.
- **II — Android/MVVM**: a UI somente renderiza e emite eventos; o ViewModel coordena estado e
  concorrência; o Repository é a fronteira exclusiva da API externa. `StateFlow` e Coroutines
  permanecem o padrão da feature.
- **III — Estado/UX**: consulta, submissão, catálogo e verificação de endereço permanecem estados
  separados. Erros de campo são completos, acionáveis, associados semanticamente e o primeiro
  inválido fica visível.
- **IV — Dados/segurança**: o cliente ViaCEP não recebe sessão, token, CNPJ, e-mail ou endereço
  parcial. Respostas são validadas como não confiáveis e erros técnicos nunca chegam à UI/log.
- **V — Qualidade proporcional**: nenhuma dependência é adicionada; jobs são canceláveis, respostas
  tardias são rejeitadas por identidade da tentativa e a validação manual cobre rede, lifecycle,
  acessibilidade e responsividade sem criar testes não solicitados.

O design pós-Phase 1 mantém o gate aprovado. O novo Repository tem uso atual demonstrável porque
centraliza o único acesso ao serviço externo; os demais artefatos apenas estendem responsabilidades
já existentes.

## Project Structure

### Documentation (this feature)

```text
specs/011-cep-address-lookup/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── provider-registration-ui.md
│   └── viacep-lookup.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/
│   ├── app/PitGoAppContainer.kt
│   └── navigation/PitGoNavHost.kt
└── features/providerregistration/
    ├── model/ProviderRegistrationModels.kt
    ├── repository/
    │   ├── ProviderRegistrationRepository.kt
    │   └── ViaCepAddressLookupRepository.kt
    ├── validation/ProviderRegistrationValidation.kt
    ├── viewmodel/ProviderRegistrationViewModel.kt
    └── ui/ProviderRegistrationScreen.kt

app/src/main/res/values/strings.xml
```

**Structure Decision**: manter o módulo Android único e a organização feature-first. O cliente e o
DTO privado do ViaCEP ficam juntos no novo Repository porque existe somente um endpoint; separá-los
em DataSource/DTO públicos não reduziria complexidade. O container cria uma única instância por
processo e a factory existente do ViewModel recebe essa dependência. Não há nova rota, recurso
gráfico, arquivo Gradle, manifest, migration ou função Supabase.

## Complexity Tracking

Nenhuma violação constitucional foi identificada.
