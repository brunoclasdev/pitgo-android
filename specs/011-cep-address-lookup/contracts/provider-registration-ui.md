# Contract: UI do cadastro empresarial com busca de CEP

## Entrada e navegação

A feature permanece no destino existente `provider-registration`. Não cria argumento, rota, tela ou
back-stack novo. O `ProviderRegistrationRoute` recebe o mesmo ViewModel, cuja factory passa a
receber também o `ViaCepAddressLookupRepository` do `PitGoAppContainer`.

## Controle de CEP

O item atual de CEP passa a conter:

- `OutlinedTextField` com label `CEP`, teclado numérico e máscara visual existente;
- `OutlinedButton` textual `Buscar CEP`;
- progresso textual `Buscando CEP…` durante a chamada;
- status/erro persistente e ação de tentar novamente quando aplicável.

O botão é habilitado somente quando:

- CEP normalizado tem oito dígitos;
- consulta não está `Loading`;
- cadastro não está `Submitting`.

Em largura confortável e fonte abaixo de 150%, campo e botão podem ficar lado a lado. Em largura
compacta, paisagem estreita ou fonte ampliada, ficam empilhados e o botão ocupa a largura. Nenhum
controle possui altura rígida; alvos têm pelo menos 48dp.

`ImeAction.Search` é uma ação explícita equivalente ao botão e só chama a busca quando o CEP é
válido. Não há chamada causada apenas pelo oitavo dígito.

## Eventos do ViewModel

```text
onFieldChanged(PostalCode, value)
lookupPostalCode()
retryPostalCodeLookup()
onFieldChanged(addressField, value)
submit()
```

Regras:

- colagem mascarada é normalizada para no máximo oito dígitos;
- toque duplicado durante loading é no-op;
- edição do CEP cancela/invalida consulta ativa;
- durante loading, os cinco campos que podem ser sobrescritos ficam temporariamente desabilitados;
- número e demais dados do formulário continuam editáveis;
- submit fica desabilitado/guardado até a consulta sair de Loading;
- consulta fica desabilitada durante submit.

## Renderização dos estados

| Estado | CEP | Botão | Campos derivados | Mensagem |
|---|---|---|---|---|
| Idle | editável | Buscar CEP | editáveis | nenhuma |
| Loading | editável | desabilitado + progresso | desabilitados | Buscando CEP… |
| Found completo | editável | Buscar CEP | preenchidos/editáveis | Endereço preenchido. Informe o número. |
| Found incompleto | editável | Buscar CEP | presentes preenchidos, ausentes editáveis | Endereço preenchido. Complete os campos restantes. |
| NotFound | erro no CEP | Tentar novamente | preservados | CEP não encontrado. |
| Failure | válido/editável | Tentar novamente | preservados/editáveis | mensagem de rede/timeout/serviço/resposta |

Uma falha temporária não bloqueia preenchimento manual nem conclusão válida. `NotFound` bloqueia
enquanto o mesmo CEP permanecer, pois é um erro conhecido do valor.

## Aplicação e edição

- sucesso atualiza todos os campos aceitos em uma única mudança de estado;
- valores vazios do ViaCEP não apagam entrada manual;
- número nunca é preenchido, limpo ou desabilitado pelo lookup;
- campos preenchidos continuam editáveis;
- mudar o CEP depois de sucesso limpa os campos registrados na proveniência e preserva número;
- sucesso invalida a geocodificação/verificação anterior, sem iniciá-la automaticamente;
- resposta tardia ou pertencente a outro CEP não altera a tela.

Depois de sucesso, foco/scroll vai para o primeiro campo obrigatório ainda vazio; se nenhum estiver
vazio, vai para Número.

## Validação completa do cadastro

Ao tocar `Cadastrar empresa`:

1. validar todos os campos e seletores conhecidos;
2. apresentar todos os erros na mesma tentativa;
3. rolar até o primeiro inválido na ordem visual;
4. focar o controle quando focável ou anunciar o grupo semântico;
5. não chamar cadastro enquanto houver erro.

Cada TextField usa `isError`, supporting text e semântica `error`. Modalidade, ofertas e seletores
também mostram sua mensagem junto ao grupo. Mensagens distinguem obrigatório, formato, tamanho,
seleção, CEP inexistente e conflito remoto. Limites máximos são informados numericamente.

Após essa tentativa, editar um campo revalida apenas aquele campo. Seu erro só desaparece quando o
valor se torna válido; valores e erros dos outros campos permanecem.

## Acessibilidade e lifecycle

- status da consulta e falhas usam live region polite;
- loading contém indicador e texto, não somente animação;
- estado nunca depende apenas de cor;
- ordem TalkBack segue CEP, busca, status e demais campos;
- `LazyColumn`, `imePadding` e largura máxima existentes permanecem;
- rotação/recomposição conserva draft e consulta no ViewModel sem reiniciar chamada;
- sair, trocar sessão ou submeter cancela/invalida a consulta;
- nenhuma mensagem técnica, URL ou resposta bruta é exibida.
