# Plano de implementação: avaliação de guincheiros e PitTecs

**Branch**: `feature/avaliacao-guincheiro` | **Data**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: Especificação em `specs/034-avaliacao-guincheiros-pittecs/spec.md`

## Resumo

Adicionar avaliações opcionais de uma a cinco estrelas para atendimentos concluídos de Guincho e PitTec, com comentário de até 500 caracteres. A gravação será definitiva e única por atendimento, autorizada por RPC Supabase que valida cliente e estado concluído. O cliente poderá dispensar a tela e avaliar pelo histórico. Outros clientes verão apenas média e quantidade; descrição terá leitura restrita ao autor, prestador e suporte autorizado.

## Contexto técnico

**Linguagem/versão**: Kotlin; Compose compiler 2.2.10 (Kotlin 2.2.10 no catálogo); SQL/PostgreSQL para Supabase.

**Dependências principais**: Jetpack Compose/Material 3, ViewModel, StateFlow, Coroutines, Navigation Compose, Supabase Kotlin 3.2.6 e Ktor 3.3.1 já presentes.

**Armazenamento**: PostgreSQL/Supabase, com tabela de avaliação e restrição única por atendimento. Sem persistência local requerida.

**Validação**: Build Gradle existente e roteiro manual ponta a ponta definido em [quickstart.md](quickstart.md). Não adicionar testes automatizados sem solicitação, conforme constituição.

**Plataforma alvo**: Android nativo (compileSdk 37, targetSdk 36) e backend Supabase PostgreSQL.

**Tipo de projeto**: Aplicativo móvel Android com backend Supabase.

**Metas de desempenho**: Fluxo de envio simples, com resposta percebida imediata; seguir o critério da spec de concluir em até dois minutos. Resumo agregado deve ser uma consulta enxuta sem carregar comentários individuais.

**Restrições**: Conclusão do atendimento não pode depender da avaliação; validar autorização e estado no servidor; comentário privado; falhas preservam o formulário para retry; nenhuma edição/exclusão após envio; não adicionar dependências.

**Escopo**: Duas modalidades (Guincho e PitTec), uma nota e comentário opcional por atendimento, estado no acompanhamento e acesso posterior pelo histórico, agregado de avaliação nos dados públicos de prestador.

## Verificação da constituição

### Gate antes da pesquisa

- Simplicidade: PASS — uma entidade de avaliação compartilhada, um contrato RPC e agregação restrita cobrem as modalidades sem camadas novas.
- Arquitetura Android MVVM: PASS — fluxo UI → ViewModel → Repository → Supabase, alinhado ao padrão atual.
- Estado/UX: PASS — estados explícitos de pendente, enviando, sucesso, erro e já avaliado; conclusão e avaliação opcionais permanecem independentes.
- Dados/segurança: PASS — server-side validation, unicidade no banco, resumo público mínimo e leitura privada autorizada.
- Qualidade: PASS — reutiliza dependências e segue convenção atual de migrations/RPC; build e cenários manuais sem gerar testes automáticos.

### Gate após desenho

- PASS — o desenho mantém escopo e arquitetura existentes, não propõe segredo no cliente, acesso direto amplo à tabela, dependência nova, alteração dos estados de atendimento nem testes não solicitados.
- Nenhuma exceção à constituição necessária.

## Estrutura do projeto

### Documentação da feature

```text
specs/034-avaliacao-guincheiros-pittecs/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── service-rating-rpcs.md
```

### Código fonte previsto

```text
app/src/main/java/com/trustarx/pitgo/
├── features/
│   ├── appointmenttracking/       # oferta opcional após conclusão
│   ├── guincho/                   # histórico compartilhado de atendimentos e perfil
│   ├── pittec/                    # fluxo existente; histórico usa a tela compartilhada de atendimentos
│   └── rating/                    # UI, ViewModel, modelo e Repository compartilhados
└── core/                          # rotas, componentes e container existentes

supabase/
└── migrations/                    # tabela, constraints, grants e RPCs da avaliação
```

**Decisão estrutural**: Colocar a capacidade compartilhada sob `features/rating`, mantendo os pontos de entrada em acompanhamento/histórico das features de atendimento. Alterar snapshots ou RPCs de histórico apenas para fornecer estado mínimo de elegibilidade/avaliação, preservando o comportamento de conclusão atual. Agregados públicos entram nos dados de perfil dos prestadores. A implementação confirmou que ambas as modalidades usam `appointments.provider_id` e que a tela/ViewModel existentes de histórico de Guincho podem renderizar os snapshots de PitTec. O RPC `list_my_service_appointments` serve as duas modalidades, acessado pelo repository e pela rota de histórico existentes; não é necessário criar uma segunda tela/ViewModel de histórico.

## Rastreamento para artefatos

- Modelo, invariantes e privacidade: [data-model.md](data-model.md)
- Contrato de submissão, resumo e leitura privada: [contracts/service-rating-rpcs.md](contracts/service-rating-rpcs.md)
- Cenários de validação de Guincho/PitTec, privacidade, retry e estados inelegíveis: [quickstart.md](quickstart.md)
- Evidência e decisões tecnológicas: [research.md](research.md)
