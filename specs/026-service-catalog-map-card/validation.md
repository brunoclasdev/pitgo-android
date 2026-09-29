# Validação — feature 026

Data: 2026-09-29. Registro da implementação inicial local e, na seção final,
da publicação e análise remota autorizadas posteriormente pelo usuário.

## Comandos e resultados

- `JAVA_HOME=/usr/local/opt/openjdk@21 PATH=/usr/local/opt/openjdk@21/bin:$PATH ./gradlew assembleDebug -q`:
  compilação concluída após corrigir o import de `ProviderLocationMode`.
- `git diff --check`: sem erros.
- `npx --yes supabase status`: executado; ambiente local indisponível porque Docker e
  Podman não estão instalados. A primeira tentativa no sandbox também encontrou
  restrição de rede; a execução autorizada fora dele confirmou a ausência do runtime.
- `npm install --prefix /private/tmp/pitgo-026-sql --no-save --package-lock=false @electric-sql/pglite`:
  instalado somente na pasta temporária, sem dependência nova no projeto.
- `node /private/tmp/pitgo-026-sql/validate.mjs`: migration integral executada em banco
  PostgreSQL/PGlite descartável e repetida para conferir idempotência da carga.
  O roteiro temporário não constitui uma nova suíte do repositório.
- `adb -s RQCTB04EDNW install -r app/build/outputs/apk/debug/app-debug.apk`: `Success`.
  Dispositivo Samsung SM-A336M (A33); abertura solicitada por `am start`.
  A hierarquia exibida é a tela de bloqueio por senha, não a interface do app.
  Segundo aparelho Samsung SM-A705MN (A70) também reportou `showing=true`, `secure=true`.

## Verificação SQL descartável

A migration executada foi exatamente
`supabase/migrations/20260930000000_service_catalog_and_pittec_map_card.sql`.
O banco temporário reproduziu as tabelas necessárias, restrições de unicidade,
relações e funções de contexto. JWT/staff, geocodificação e tipos/funções de geografia
foram substituídos por fixtures locais. Portanto, estes resultados verificam a nova
migration e seus contratos, mas não equivalem a executar toda a cadeia histórica de
migrations, RLS, autenticação Supabase ou PostGIS reais.

Resultados obtidos:

- Sete categorias, cada uma com exatamente 15 especialidades BROWSE após a carga.
- IDs preexistentes de categoria, serviço e dois serviços implícitos preservados.
- Reexecução não duplicou serviços: 105 especialidades e dois serviços implícitos.
- Staff criou um 16º serviço; não existe limite permanente de 15.
- Nome repetido sem distinção de maiúsculas/aparado foi rejeitado com `DUPLICATE_NAME`.
- Cliente e prestador sem staff foram rejeitados em criação/atualização.
- Grant de execução negado a `anon` e permitido a `authenticated`, que continua
  sujeito à autorização interna de staff.
- Criação de serviço implícito e alteração de implícito/categoria protegida rejeitadas.
- Atualização/desativação registrada no audit log da mesma transação.
- Cadastro FIXED com duas ofertas persistiu exatamente duas ofertas e uma categoria.
- Ofertas duplicadas, categorias diferentes e modalidades incompatíveis rejeitadas.
- Serviço desativado e categoria desativada rejeitados com `CATALOG_CHANGED`.
- Desativação conservou as quatro ofertas históricas criadas nos cenários.
- Cadastro repetido retornou `ALREADY_REGISTERED` sem nova gravação.
- Móvel e PitTec cadastraram somente seus serviços implícitos correspondentes;
  PitTec conservou oferta gratuita.

Saída do roteiro:

```text
PASS seed: 7 x 15, existing IDs, implicit services, replay
PASS management: staff-only create/update, service 16, duplicates, implicit protection, audit
PASS registration: exact selected offers, one category, duplicate/mixed/inactive/wrong-mode rejection, MOBILE/PITTEC regression, idempotence
PASS grants: anon denied; inactive category rejects new registration and preserves historical offers
```

## Revisão de implementação e cobertura

| Requisitos | Implementação / evidência | Limite da evidência |
|---|---|---|
| FR-001–FR-004, SC-001/SC-003 | Categoria principal, ofertas herdando categoria, duplicados desabilitados/validados; SQL persistiu duas ofertas | Cadastro visual ainda pendente |
| FR-005/FR-008, SC-002 | 105 linhas da spec, BROWSE, 7 × 15 na fixture; IDs implícitos preservados | Conferir dados legados reais antes de publicar |
| FR-006/FR-010 | RPCs staff, metadados, ativação/desativação e auditoria; 16º serviço aceito | JWT/RLS reais não exercitados |
| FR-007/FR-009 | Reconsulta no envio, locks transacionais, categoria/serviço ativo, modo implícito validado | Concorrência entre conexões reais ainda pendente |
| FR-011–FR-013, SC-005/SC-007 | Resumo inferior compacto, detalhes expansíveis, CANCEL condicionado a allowedActions | Inspeção visual após pagamento pendente |
| FR-014, SC-006 | Atualizações PitTec não emitem nova câmera após primeira posição; recentralização explícita, bounds de rota/markers, mesmo MapView | Não executadas as 20 atualizações no aparelho |
| FR-015 | Textos por status, resumo resetado ao encerrar/ocultar, cancelamento protegido | Transições visuais reais pendentes |
| FR-016 | Strings, descrição de expansão, toque Material, painel rolável limitado a 45%, altura medida reservada no mapa | Fonte ampliada, temas e orientação pendentes |
| SC-004 | Nenhum participante avaliado | Taxa de 90% não medida nem afirmada |

O seed preserva itens legados e estados ativos já existentes por `ON CONFLICT DO NOTHING`.
Em bases com especialidades adicionais fora das listas, ou itens da lista previamente
desativados, a contagem ativa pode diferir da fixture. Conferir esses dados no ambiente
de teste antes da publicação, sem excluir ou reativar ofertas históricas implicitamente.

## Pendências

- T010: executar cadastro na interface, confirmação/cancelamento da troca de categoria,
  retry/erro/vazio e regressão visual das modalidades; conferir especialidades FIXED de
  Guincho/PitTec na descoberta e contratação fixa.
- T019: fluxo real de pagamento aprovado, chegada, IN_PROGRESS, término, GPS antigo,
  offline e 20 atualizações com pan/zoom; registrar marcadores e rota na área útil.
- T020: verificação manual com fonte ampliada, temas, orientação e acessibilidade.
- T021: build e diff verificados; cenários integrados de quickstart pendentes.
- Executar a migration em Supabase descartável completo, com autenticação, RLS,
  PostGIS e dados legados representativos. A execução PGlite cobre T014 no nível de
  contrato SQL isolado; não substitui essa regressão de integração.

Não foram criados pagamentos, cadastros ou mutações no banco remoto. Não houve
publicação, push ou commit nesta etapa de implementação.

## Publicação e análise remota — 2026-09-29

Etapa posterior, explicitamente autorizada pelo usuário: aplicar migrations e
conferir sincronização com o banco vinculado. Todos os comandos Supabase usaram
`npx --yes supabase`.

### Sincronização e migrations

- O primeiro dry-run identificou três migrations remotas ausentes no checkout.
  Seus nomes e SQL foram recuperados diretamente de
  `supabase_migrations.schema_migrations`, sem reparar ou apagar o histórico:
  `20260929050000_dashboard_pittec_earnings_created_at.sql`,
  `20260929060000_dashboard_pittec_earnings_rolling_period.sql` e
  `20260929070000_dashboard_home_recent_blocks.sql`.
- Aplicada `20260930000000_service_catalog_and_pittec_map_card.sql`.
- A análise real revelou grants amplos de escrita direta e políticas somente
  de itens ativos, que impediam o prestador de ler suas ofertas desativadas.
  Aplicada `20260930010000_catalog_write_grants.sql`: leitura autenticada,
  escrita somente via RPC staff e políticas suplementares para histórico do dono.
- O teste de duplicação encontrou ausência de índice único de nomes de serviços
  no banco legado. Como não havia duplicatas existentes, aplicada
  `20260930020000_catalog_name_uniqueness.sql`, com unicidade de categoria e
  serviço por nome aparado e sem distinção de maiúsculas.
- Conferência final de `migration list --linked`: **77 versões locais/remotas,
  nenhuma divergência**, incluindo as três migrations recuperadas e as três novas.
- Dry-run final de `db push --linked --dry-run`: `upToDate: true`,
  `migrations: []`, `Remote database is up to date`.

### Integridade e contratos publicados

- Catálogo: **108 serviços em 20 categorias**. São 105 especialidades novas
  distribuídas em sete categorias, cada uma com exatamente 15 BROWSE ativos,
  mais os três serviços anteriores.
- **11 prestadores e 11 ofertas preservados**; hash dos vínculos das ofertas
  idêntico antes/depois. IDs, nomes, categorias, estados e dispatch kinds dos
  serviços anteriores, assim como as categorias anteriores, preservados.
- Zero nomes duplicados, zero ofertas órfãs, zero projeções de categoria
  ausentes/extras e zero incompatibilidades entre modalidade e dispatch kind.
- As quatro funções desta feature têm MD5 de `prosrc` igual ao corpo SQL local:
  cadastro, criação de categoria, criação de serviço e atualização de catálogo.
  Todas continuam SECURITY DEFINER com `search_path` vazio, execução negada a
  `anon` e permitida a `authenticated` conforme seus guards internos.
- RLS habilitada nas duas tabelas; authenticated tem SELECT e não tem
  INSERT/UPDATE/DELETE. Os dois índices únicos normalizados estão presentes.

### Validação transacional no banco real

`npx --yes supabase db query --linked --file /private/tmp/pitgo-026-remote-rpc-check.sql`
executou criação/edição/desativação com contexto de um staff ativo e verificou:

- Criação de 16 serviços, sem limite permanente de 15.
- Duplicação de categoria e serviço rejeitada com `DUPLICATE_NAME`.
- Categoria inativa rejeitada com `CATEGORY_NOT_FOUND`.
- Alteração do serviço implícito rejeitada com `IMPLICIT_SERVICE_PROTECTED`.
- 19 ações de criação/atualização registradas no audit log da transação.
- As três mutações RPC negadas a prestador sem staff com `STAFF_REQUIRED`.
- Escrita direta negada e execução RPC negada a `anon`.
- Serviço/categoria desativados ainda legíveis pelo prestador dono de uma oferta
  histórica; invisíveis para contexto autenticado que não possui essa oferta.

Todos os registros, vínculos e eventos de auditoria de teste foram revertidos
com `ROLLBACK`. A conferência final encontrou **zero categorias de teste residuais**
e os mesmos 11 vínculos de ofertas. Não foram alterados appointments nem pagamentos.

`npx --yes supabase db lint --linked --schema public,private --level error --fail-on error`
concluiu com `results: []`, sem erros SQL nas funções desses schemas.

### Dados legados e limites remanescentes

- Há **12 categorias antigas ativas sem serviços**; foram preservadas. O app já
  apresenta o estado vazio para elas. As sete categorias desta feature estão completas.
- O serviço implícito **Guincho permanece na categoria Bateria**, associação já
  existente antes da publicação. ID, disponibilidade e despacho MOBILE_SEQUENTIAL
  foram preservados; não houve remanejamento de ofertas/projeções históricas.
- A validação de cadastro novo com prova real de geocodificação, interface Android,
  pagamento e 20 atualizações de mapa continua pendente conforme T010/T019/T020/T021.
- Esta análise verifica histórico de migrations, contratos publicados, integridade,
  permissões/RLS do catálogo e lint SQL. Não representa um diff integral de todos
  os objetos históricos do projeto.
- Os três arquivos recuperados e as duas migrations corretivas estão no checkout;
  não foi criado novo commit nem push nesta etapa de publicação.
