# Contract: Seleção de serviços no cadastro do prestador

## Read flow

1. App carrega categorias ativas.
2. Prestador `FIXED` seleciona uma categoria principal.
3. App carrega serviços ativos dessa categoria e permite selecionar vários.
4. Cada oferta selecionada recebe preço, duração, descrição e regra de gratuidade já existentes.

Móvel e PitTec mantêm a resolução implícita existente e não exibem seleção manual.

As especialidades das categorias Guincho/PitTec são elegíveis somente para FIXED e usam BROWSE.
Clientes encontram e contratam essas ofertas pelo fluxo de serviços fixos; não iniciam despacho automático.

## Registration payload

```json
{
  "provider": {},
  "address": {},
  "offers": [
    {
      "service_id": "uuid",
      "price": "100.00",
      "duration_minutes": 60,
      "description": "opcional",
      "is_free": false
    }
  ]
}
```

## Server validation

O RPC deve rejeitar payload sem ofertas para `FIXED`, duplicado, serviço inexistente/inativo, categoria inativa, serviços de categorias diferentes ou oferta incompatível com a modalidade. `provider_categories` é recalculada a partir dos serviços aceitos.

## User-facing failures

`CatalogLoadResult` continua distinguindo loading, offline, timeout, sessão expirada e indisponível. Uma seleção que deixou de existir entre leitura e envio retorna `CatalogChanged`, orientando o prestador a revisar a oferta.

## Troca de categoria e compatibilidade

A categoria principal pertence ao rascunho FIXED; todas as novas ofertas a herdam.
Trocar uma categoria já selecionada exige confirmação e substitui as ofertas por uma
oferta vazia da nova categoria, descartando preços, durações e descrições anteriores.
Cancelar a confirmação conserva o rascunho. Respostas de carregamento são armazenadas
pela categoria solicitada e não mudam a categoria atualmente selecionada.

O envio consulta novamente categoria/serviços ativos. O RPC também bloqueia as linhas
de categoria e serviços durante a transação e retorna `P0002`/`CATALOG_CHANGED` se a
seleção foi desativada ou removida. Preço, duração, duplicidade, prova de geocodificação
e demais validações anteriores continuam vigentes.

Cadastros antigos não são reescritos: a leitura do resumo inclui os serviços históricos,
mesmo inativos ou originalmente distribuídos em várias categorias. Uma repetição do
cadastro mantém `ALREADY_REGISTERED`. Móvel/PitTec exigem exatamente uma oferta do
dispatch kind implícito correspondente; não recebem as especialidades BROWSE.
