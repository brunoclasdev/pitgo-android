# Quickstart: Corrigir cadastro de veículos e erros por campo

## Pré-requisitos

- Android SDK/JDK e propriedades locais já exigidas pelo projeto configuradas.
- Supabase CLI instalado e projeto local inicializável.
- Uma conta `CLIENT` ativa, uma conta `PROVIDER` e, para cenários de duplicidade, dois clientes.
- Confirmar os contratos em [vehicle-rpcs.md](./contracts/vehicle-rpcs.md) e o modelo em
  [data-model.md](./data-model.md).

## 1. Validar a migration localmente

Execute somente contra o Supabase local:

```bash
supabase start
supabase db reset
supabase db lint
```

Esperado:

- `create_vehicle` e `set_primary_vehicle` existem com as assinaturas documentadas;
- ambas são `SECURITY DEFINER`, possuem `search_path` vazio e `authenticated` pode executar;
- `anon`/`public` não podem executar;
- RLS continua habilitado em `vehicles` e não há grant direto de INSERT/UPDATE ao app.

Confira o diff remoto antes de qualquer implantação deliberada:

```bash
supabase migration list
supabase db push --dry-run
```

`supabase db push` real é uma ação operacional explícita e não faz parte automática da validação
local deste guia.

## 2. Compilar o aplicativo

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Esperado: build e lint concluem sem novas dependências, imports sem uso ou recursos ausentes.

## 3. Fluxo mínimo e primeiro principal

1. Entre com cliente sem veículos e mantenha o onboarding pendente.
2. Escolha cadastrar veículo.
3. Informe apenas Marca e Modelo; deixe todo opcional vazio.
4. Salve.

Esperado: uma única linha é criada para `auth.uid()`, opcionais são `null`, `active=true`,
`is_primary=true`, onboarding é resolvido e Home abre sem formulário no Voltar. Home e Garagem
mostram o veículo após a primeira atualização.

## 4. Cadastro pela Garagem e troca de principal

1. Pela Garagem, crie um segundo veículo sem marcar “Tornar veículo principal”.
2. Confirme que retorna à Garagem e o principal anterior permanece.
3. Crie ou selecione outro veículo como principal.
4. Repita rapidamente a seleção em duas sessões/dispositivos da mesma conta.

Esperado: o formulário sai da pilha, a Garagem recarrega e exatamente um veículo ativo fica
principal após cada transação concluída.

## 5. Validação e foco por campo

Faça uma tentativa contendo simultaneamente Marca vazia, Modelo vazio, placa inválida, Renavam com
menos de 11 dígitos, chassi inválido, anos fora do intervalo e quilometragem negativa.

Esperado:

- todos os campos problemáticos são marcados com as mensagens de
  [vehicle-form-ui.md](./contracts/vehicle-form-ui.md);
- a lista rola e foca Marca em até 1 segundo;
- corrigir Marca remove somente seu erro;
- repetir o submit foca o próximo campo inválido na ordem visual;
- os demais valores continuam preenchidos.

Também valide placa antiga (`ABC1234`), Mercosul (`ABC1D23`), quilometragem zero e ano corrente + 1.

## 6. Duplicidade

1. Cadastre placa, Renavam e chassi válidos no primeiro cliente.
2. Tente cada identificador separadamente no segundo cliente.

Esperado: somente o campo em conflito recebe “Este identificador já está cadastrado.”; nenhuma
constraint, SQLSTATE ou valor completo aparece na tela/log.

## 7. Falhas gerais

Valide separadamente:

- modo avião/sem DNS;
- timeout;
- sessão expirada;
- Supabase 5xx;
- função removida/incompatível em ambiente descartável;
- resposta perdida logo após commit.

Esperado: nenhum campo válido é marcado, o draft é preservado quando recuperável e RPC ausente usa
exatamente “O cadastro de veículos está temporariamente indisponível. Tente novamente mais tarde.”
Não há retry automático. Se o commit ocorreu antes da perda de resposta, sair e reabrir a Garagem
mostra o veículo remoto sem criação automática adicional.

## 8. Segurança

- Uma conta PROVIDER não consegue abrir/usar o fluxo como cliente nem executar as RPCs.
- Chamar `set_primary_vehicle` com ID de outro cliente retorna falha neutra e não altera linhas.
- Payload de `create_vehicle` não contém `user_id`.
- Logs não contêm token, exception PostgREST bruta, placa completa, Renavam ou chassi.
- Consultas com clientes diferentes só retornam os próprios veículos por RLS.

## 9. UX e acessibilidade

Em largura compacta, paisagem, teclado aberto e fonte ampliada:

- todos os campos, erro e CTA permanecem alcançáveis;
- foco/scroll funciona inclusive em Chassi e Quilometragem;
- leitor de tela anuncia erro do campo e banner geral sem depender apenas da cor;
- toques repetidos durante progresso resultam em uma única chamada.
