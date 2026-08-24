# Research: Busca de endereço por CEP

## 1. Fronteira da integração

**Decision**: criar `ViaCepAddressLookupRepository` dentro da feature de cadastro empresarial. Ele
possui um único `HttpClient(OkHttp)` process-scoped, configurado sem autenticação, cookies, logging,
retry automático ou cache persistente. O Repository de cadastro Supabase permanece inalterado.

**Rationale**: ViaCEP é uma fonte externa pública e diferente do Supabase. Um cliente dedicado evita
herdar headers de sessão e mantém o contrato de privacidade de transmitir somente o CEP. O
`PitGoAppContainer` já é a composition root do app e garante uma única instância do Repository.
Ktor recomenda reutilizar `HttpClient`, em vez de criar um por chamada.

**Alternatives considered**:

- reutilizar o cliente Supabase: rejeitado por misturar base URL, plugins e contexto autenticado;
- Supabase Edge Function: rejeitado por criar backend/operação sem necessidade atual;
- Retrofit ou OkHttp direto: rejeitados por duplicarem a pilha Ktor já instalada;
- DataSource, Use Case e DTOs públicos separados: rejeitados porque um único endpoint não justifica
  novas camadas.

Fonte: [Ktor — criação e configuração do cliente](https://ktor.io/docs/client-create-and-configure.html).

## 2. Dependências e decodificação

**Decision**: reutilizar `ktor-client-okhttp:3.3.1` e `kotlinx-serialization-json:1.9.0` já declarados.
Ler o corpo com `bodyAsText()` e decodificar com `Json { ignoreUnknownKeys = true }`. O DTO fica
privado no mesmo arquivo do Repository. Não adicionar ContentNegotiation, Retrofit ou outra
dependência.

**Rationale**: são suficientes para um único GET/JSON. A decodificação manual permite interpretar
`erro` tanto como booleano quanto como string, compatível com o comportamento real do ViaCEP, sem
adicionar dois módulos Ktor apenas para `response.body<Dto>()`.

**Alternatives considered**:

- Ktor ContentNegotiation + serialization: válido, mas desnecessário para uma resposta;
- parser JSON manual por strings/regex: rejeitado por fragilidade;
- aceitar campos desconhecidos como erro: rejeitado porque a API retorna vários campos fora do
  escopo e pode evoluir sem quebrar os seis consumidos.

## 3. Contrato HTTP e validação da resposta

**Decision**: chamar apenas `GET https://viacep.com.br/ws/{cep}/json/`, com `Accept:
application/json`, host fixo e exatamente oito dígitos no path. Desabilitar redirects e
`expectSuccess`; o Repository inspeciona status e corpo. Um resultado é aceito somente quando:

- `erro` não representa `true`;
- o CEP retornado, normalizado, é igual ao solicitado;
- `localidade` não está vazia;
- `uf` pertence às 27 siglas brasileiras.

Rua, complemento e bairro podem vir vazios. `erro=true` é `NotFound`; resposta incompatível ou
malformada é `UnexpectedResponse`. Unidade, estado por extenso, região, IBGE, GIA, DDD e SIAFI são
ignorados.

**Rationale**: a documentação oficial exige oito dígitos, informa HTTP 400 para formato inválido e
usa `erro=true` para CEP válido inexistente. Conferir CEP/cidade/UF impede que uma resposta parcial
ou trocada substitua o draft silenciosamente.

**Alternatives considered**:

- confiar em todo HTTP 200: rejeitado porque CEP inexistente também retorna 200;
- exigir rua e bairro: rejeitado porque CEPs genéricos podem omiti-los;
- usar campos administrativos retornados: rejeitado por não terem consumidor nesta feature.

Fonte: [ViaCEP — Webservice CEP e IBGE](https://viacep.com.br/).

## 4. Timeout e classificação de falhas

**Decision**: configurar timeout total de 2.500 ms, conexão de 1.500 ms e socket de 2.000 ms. O
Repository relança `CancellationException` e mapeia sem expor Throwable/corpo:

| Sinal | Resultado de domínio |
|---|---|
| request/connect/socket timeout, HTTP 408/504 | `Timeout` |
| DNS/rota indisponível | `Offline` |
| HTTP 429, 5xx ou falha transitória de conexão | `ServiceUnavailable` |
| outro HTTP, TLS, JSON ou contrato inválido | `UnexpectedResponse` |

Não há retry automático. A tentativa seguinte é sempre uma ação explícita da pessoa.

**Rationale**: o teto deixa margem para apresentar um resultado ou mensagem antes dos 3 segundos
do critério de sucesso. A documentação Ktor oferece limites separados e cancelamento pela coroutine.
Evitar retry automático também reduz chamadas duplicadas e respeita o alerta do ViaCEP contra uso
massivo.

**Alternatives considered**:

- timeout maior: rejeitado por não atender de forma determinística o alvo de 3 segundos;
- retry exponencial invisível: rejeitado porque pode ultrapassar o alvo e duplicar tráfego;
- uma mensagem genérica para toda falha: rejeitada por impedir orientação acionável.

Fontes: [Ktor — timeouts](https://ktor.io/docs/client-timeout.html) e
[Ktor — requests e cancelamento](https://ktor.io/docs/client-requests.html#cancel-a-request).

## 5. Estado, concorrência e lifecycle

**Decision**: adicionar `PostalCodeLookupState` imutável ao `ProviderEditingContent` e manter um
`postalCodeLookupJob` no ViewModel. Cada tentativa recebe `attemptId` monotônico e o CEP capturado.
O resultado só é aplicado se o estado ainda for `Loading` com o mesmo ID/CEP e o draft ainda
contiver esse CEP. Alterar CEP, iniciar submissão ou perder/trocar sessão cancela o job e invalida a
geração.

Toques repetidos durante `Loading` são ignorados. A submissão final é desabilitada/guardada durante
a consulta, e a consulta é desabilitada durante `Submitting`, mantendo as duas máquinas de estado
independentes.

**Rationale**: apenas cancelar o job não protege contra callback já entregue, e apenas comparar o
CEP falha no ciclo A → B → A. O par geração+CEP evita preenchimento tardio. O ViewModel já sobrevive
a recomposição/rotação e não exige `SavedStateHandle`, que persistiria dados do cadastro além do
necessário.

**Alternatives considered**:

- debounce/busca automática: rejeitado pela clarificação da feature;
- estado `rememberSaveable`: rejeitado por criar duas fontes de verdade;
- `SavedStateHandle`: rejeitado porque a necessidade é rotação com o destino vivo, não restauração
  após morte do processo;
- múltiplas chamadas concorrentes: rejeitadas por risco de overwrite e uso desnecessário da API.

Fonte: [Android — ViewModel e estado salvo](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate).

## 6. Aplicação dos dados e proveniência

**Decision**: sucesso atualiza o `AddressDraft` atomicamente. Valores não vazios de rua,
complemento, bairro, cidade e UF substituem os respectivos campos e são registrados em
`CepAutofillProvenance`. Campos omitidos preservam o valor manual atual e não entram na proveniência.
Número nunca é alterado.

Se o CEP mudar após sucesso, os campos registrados como derivados são limpos e o número é
preservado. Uma correção manual é aceita para o CEP atual, mas o campo continua marcado como
originado daquela consulta para não carregar endereço antigo para um novo CEP. Toda aplicação ou
edição relevante invalida a verificação/geocodificação existente.

**Rationale**: o conjunto por campo diferencia valor realmente fornecido pelo ViaCEP de campo
genérico preenchido manualmente. Manter a proveniência após uma correção prioriza consistência ao
trocar o CEP, sem impedir a correção para o CEP atual.

**Alternatives considered**:

- limpar todos os campos em qualquer edição de CEP: rejeitado porque apagaria valores manuais que o
  ViaCEP nunca forneceu;
- remover proveniência quando a pessoa edita: rejeitado porque poderia transportar endereço do CEP
  anterior para outro;
- preencher campo ausente com vazio: rejeitado porque destrói trabalho manual;
- tornar autofill read-only: rejeitado pela exigência de correção e pelos CEPs genéricos.

## 7. Falhas recuperáveis e preenchimento manual

**Decision**: `NotFound` adiciona `POSTAL_CODE_NOT_FOUND` ao campo CEP e bloqueia conclusão enquanto
o valor não mudar. Offline, timeout, indisponibilidade e resposta inesperada aparecem no conjunto
de consulta, com retry, mas não marcam um CEP sintaticamente válido como incorreto e não impedem
conclusão manual quando o restante do formulário estiver válido.

**Rationale**: inexistência é uma propriedade conhecida do valor; indisponibilidade é uma condição
externa e não deve transformar a assistência em bloqueio.

**Alternatives considered**:

- bloquear sempre até uma consulta bem-sucedida: rejeitado por FR-014;
- ignorar CEP inexistente no submit: rejeitado porque existe um erro conhecido do campo;
- Snackbar efêmero: rejeitado porque retry e contexto devem permanecer próximos ao CEP.

## 8. Erros de formulário, foco e acessibilidade

**Decision**: preservar a estratégia de validar todos os campos no submit, mas corrigir três lacunas
atuais:

1. validar na ordem visual, iniciando por modalidade e seguindo todos os campos/ofertas;
2. após a primeira validação, revalidar somente o campo editado e remover seu erro apenas quando ele
   ficar válido;
3. derivar o índice do primeiro erro da mesma lista ordenada de linhas renderizada pela
   `LazyColumn`, rolar, aguardar composição e então pedir foco. Não usar constante fixa para ofertas.

`TEXT_TOO_LONG` passa a mostrar o limite do campo. Modalidade, grupos e seletores também recebem
erro visual, texto e semântica. O conjunto CEP/botão usa `liveRegion = Polite`; TextFields mantêm
`isError`, supporting text e `semantics { error(...) }`.

**Rationale**: o código já acumula erros, mas hoje remove erro inválido cedo demais e não consegue
focar campos offscreen não compostos. A lista ordenada única mantém renderização e foco alinhados
mesmo com linhas condicionais.

**Alternatives considered**:

- validar todos os campos enquanto a pessoa digita desde o início: rejeitado por feedback
  prematuro/ruidoso;
- mostrar apenas banner geral: rejeitado por não indicar o campo;
- manter `FORM_ITEMS_BEFORE_OFFERS`: rejeitado porque linhas condicionais tornam o índice frágil;
- depender apenas de cor: rejeitado por acessibilidade.

Fontes: [Compose — semântica de erros](https://developer.android.com/develop/ui/compose/accessibility/semantics)
e [Compose — FocusRequester](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior).
