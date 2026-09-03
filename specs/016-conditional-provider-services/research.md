# Research: Seleção de serviços condicionada à modalidade do prestador

## 1. Como identificar o serviço implícito de Móvel/PitTec sem depender do nome

**Decision**: Identificar o serviço implícito de cada modalidade pelo campo `dispatch_kind` já
existente em `public.services` (`MOBILE_SEQUENTIAL` para Móvel, `PITTEC_AUTO_SEQUENTIAL` para
PitTec), em vez de comparar pelo nome ("Guincho"/"PitTec").

**Rationale**: `dispatch_kind` já é a distinção de negócio real usada em todo o app (catálogo da
Home, despacho automático) para diferenciar esses dois serviços de todos os demais. Buscar por esse
campo é resiliente a renomeações do serviço no catálogo e evita duplicar uma regra de string em
mais um lugar do app. O dado já é lido do banco pelo cadastro (mesma tabela `services`), então não
exige nenhuma migração — apenas adicionar o campo ao DTO/modelo Android que já existe.

**Alternatives considered**:
- Comparar pelo nome exato "Guincho"/"PitTec": rejeitado por depender de string fixa e quebrar se o
  nome for ajustado no catálogo (ex.: acentuação, tradução).
- Criar uma nova coluna/flag exclusiva para "serviço implícito de cadastro": rejeitado por duplicar
  uma distinção que `dispatch_kind` já expressa (YAGNI).

## 2. Onde resolver o serviço implícito e como validá-lo

**Decision**: Adicionar um método de repositório dedicado que busca o(s) serviço(s) ativos com um
`dispatch_kind` específico (`MOBILE_SEQUENTIAL` ou `PITTEC_AUTO_SEQUENTIAL`), buscando no máximo 2
registros para distinguir três resultados possíveis: exatamente um (uso normal), nenhum (catálogo
sem esse serviço) e mais de um (catálogo com duplicidade) — os dois últimos casos bloqueiam o envio
do cadastro com uma falha compreensível (FR-009), sem exigir uma constraint nova no banco.

**Rationale**: Mantém a regra de unicidade como uma verificação de leitura no momento do cadastro
(consistente com a mesma cautela já usada na migração que semeia o catálogo do PitTec), sem
introduzir uma constraint de banco nova para um cenário que já é improvável e administrado por quem
mantém o catálogo.

**Alternatives considered**:
- Confiar cegamente no primeiro resultado (`limit(1)`) sem checar duplicidade: rejeitado por poder
  mascarar um catálogo mal configurado sem avisar ninguém.
- Adicionar uma constraint de unicidade parcial em `services` para `dispatch_kind in
  ('MOBILE_SEQUENTIAL','PITTEC_AUTO_SEQUENTIAL')`: mudança de banco desproporcional ao requisito
  atual; pode ser revisitada separadamente se o catálogo crescer sem controle editorial.

## 3. Como reaproveitar o formulário de oferta existente para Móvel

**Decision**: Continuar usando a mesma estrutura de rascunho (`OfferDraft`) e a mesma validação já
existentes; ao selecionar a modalidade Móvel, o ViewModel substitui a lista de ofertas por uma única
oferta pré-vinculada ao serviço "Guincho" resolvido (categoria e serviço já preenchidos e
não-editáveis), mantendo os campos de preço e duração como os únicos que a tela mostra e que o
prestador ainda preenche.

**Rationale**: Reaproveita 100% da validação de preço/duração e do envio já existentes (nenhuma
mudança em `ProviderRegistrationValidation` nem em `create_provider_registration`); a tela apenas
deixa de renderizar os seletores de categoria/serviço para essa oferta. Isso respeita a constituição
do projeto (menor solução que preserve qualidade) e evita duplicar a lógica de precificação por
prestador que o Guincho já usa.

**Alternatives considered**:
- Criar um formulário de submissão paralelo específico para Móvel/PitTec, fora do fluxo de
  `OfferDraft`/validação atual: rejeitado por duplicar validação e aumentar a superfície de
  manutenção sem necessidade.

## 4. Como tratar PitTec sem nenhum campo de preço

**Decision**: Ao selecionar a modalidade PitTec, o ViewModel substitui a lista de ofertas por uma
única oferta pré-vinculada ao serviço "PitTec" resolvido, já marcada como gratuita (`isFree = true`,
preço zero) e com uma duração padrão interna (não editável, não exibida). A tela não renderiza
nenhum campo de oferta para essa modalidade.

**Rationale**: Consistente com a Clarification já registrada no spec: o valor cobrado pelo PitTec
(fixo + variável por distância) é definido centralmente pela plataforma, não pelo prestador nem por
oferta de catálogo — então o campo `provider_services.price` existente não representa esse valor e
não deve ser exposto nem preenchido manualmente. A duração é apenas um dado técnico exigido pelo
contrato de envio já existente (`create_provider_registration`); um valor padrão interno satisfaz a
validação sem significado de negócio para o cliente.

**Alternatives considered**:
- Alterar `create_provider_registration` para aceitar ofertas sem preço/duração quando o serviço for
  PitTec: rejeitado por exigir uma migração de banco para um requisito que o app já resolve sem
  mudança de contrato, contrariando KISS.

## 5. Impacto em código já existente

**Decision**: Nenhuma mudança de backend (migração ou RPC) é necessária. Todas as mudanças ficam no
app Android: modelo/DTO do catálogo de cadastro (novo campo `dispatchKind`), ViewModel de cadastro
(gestão do rascunho de ofertas por modalidade) e tela de cadastro (ocultar/mostrar a seção de
oferta).

**Rationale**: A tabela `services` já expõe `dispatch_kind`; a RPC `create_provider_registration` já
aceita qualquer oferta válida independentemente da modalidade do prestador. O requisito é inteiramente
sobre o que a tela pede e o que o ViewModel monta antes de enviar.
