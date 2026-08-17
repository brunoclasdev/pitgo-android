# Feature Specification: Layouts iniciais do PitGo

**Feature Branch**: `develop`

**Created**: 2026-08-17

**Status**: Draft

**Input**: User description: "Criar as telas iniciais Welcome e Home do PitGo, com dados locais,
identidade visual roxa e navegação básica de Entrar para a Home."

## Clarifications

### Session 2026-08-17

- Q: Como as telas Welcome e Home devem reagir ao modo escuro do sistema? → A: A Welcome mantém
  o gradiente roxo; a Home acompanha o modo claro ou escuro.
- Q: Como o logo PitGo deve ser representado no topo da Welcome? → A: Exibir “PitGo” como wordmark
  textual estilizado.
- Q: Os cards de diagnóstico e serviços devem aceitar toque nesta entrega? → A: Todos aceitam
  toque e mostram feedback, mas não navegam.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conhecer o PitGo e entrar (Priority: P1)

Como motorista que abriu o PitGo pela primeira vez, quero entender rapidamente a proposta do
aplicativo e entrar na experiência principal para buscar ajuda para o meu veículo.

**Why this priority**: A tela de entrada apresenta a marca, comunica os benefícios essenciais e
oferece o único caminho funcional exigido para chegar à Home.

**Independent Test**: Abrir o aplicativo, verificar todo o conteúdo da Welcome e selecionar
“Entrar”; a entrega é válida quando uma única ação leva à Home correta.

**Acceptance Scenarios**:

1. **Given** que o aplicativo foi aberto, **When** a tela inicial aparece, **Then** o usuário vê o
   logo PitGo, a mensagem “Seu carro precisa. O PitGo resolve.”, o subtítulo, os três benefícios e
   as três ações previstas.
2. **Given** que a Welcome está visível, **When** o usuário seleciona “Entrar”, **Then** a Home é
   apresentada sem exigir autenticação, cadastro ou conexão com a internet.
3. **Given** que a Welcome está visível, **When** o usuário seleciona “Criar conta” ou “Sou uma
   empresa · Cadastrar”, **Then** recebe feedback visual do toque e permanece na Welcome.

---

### User Story 2 - Visualizar o contexto do atendimento (Priority: P2)

Como motorista que entrou na Home, quero reconhecer meu perfil, veículo e localização de
atendimento para confirmar o contexto antes de escolher um serviço.

**Why this priority**: O contexto pessoal e veicular orienta a jornada e reduz o risco de o usuário
escolher um atendimento pensando em outro veículo ou endereço.

**Independent Test**: Abrir diretamente a Home e conferir saudação, avatar, veículo, ano, placa,
endereço e a opção de diagnóstico, sem depender da Welcome.

**Acceptance Scenarios**:

1. **Given** que a Home foi aberta, **When** o cabeçalho aparece, **Then** o usuário vê “Olá,”,
   “Rafael 👋” e um avatar circular com a letra “R”.
2. **Given** que a Home está visível, **When** o usuário consulta o veículo selecionado, **Then** vê
   “BMW 320i M Sport”, “2020 · PGO-2A20”, uma identificação visual de carro e o endereço “Av.
   Paulista, 1500 · São Paulo” acompanhado de identificação visual de localização.
3. **Given** que o usuário não sabe qual serviço escolher, **When** observa a seção principal,
   **Then** encontra a opção destacada “Não sei o que aconteceu” com a explicação “Descreva o
   problema e sugerimos o serviço”.

---

### User Story 3 - Explorar serviços disponíveis (Priority: P3)

Como motorista na Home, quero comparar os serviços e seus valores iniciais para identificar
rapidamente a categoria mais adequada à minha necessidade.

**Why this priority**: O catálogo é a principal área de descoberta da Home, mas nesta entrega ele
é informativo e não inicia uma contratação.

**Independent Test**: Abrir diretamente a Home e verificar os seis serviços, preços iniciais e a
barra inferior fixa; nenhum fluxo posterior precisa estar implementado.

**Acceptance Scenarios**:

1. **Given** que a Home está visível, **When** o usuário percorre a grade, **Then** encontra Bateria
   por R$ 320, Troca de óleo por R$ 220, Pneus por R$ 180, Luz no painel por R$ 150, Pequenos
   reparos por R$ 260 e Carro não liga por R$ 280, todos precedidos por “A partir de”.
2. **Given** uma tela de smartphone suportada, **When** o catálogo é apresentado, **Then** os
   serviços aparecem em duas colunas, permanecem legíveis e podem ser alcançados por rolagem.
3. **Given** que a Home está visível, **When** o usuário observa a barra inferior, **Then** vê
   Início, Serviços, Garagem, Atendimentos e Perfil, com Início identificado como selecionado.
4. **Given** qualquer card de diagnóstico ou serviço da Home, **When** o usuário o seleciona,
   **Then** há feedback visual imediato e a tela permanece no estado atual.

### Edge Cases

- Em telas baixas, modo paisagem ou com texto ampliado, o conteúdo rolável deve permanecer
  alcançável sem ficar oculto de forma permanente pela barra inferior.
- Textos extensos, especialmente “Técnicos verificados”, “Pequenos reparos” e “Av. Paulista, 1500
  · São Paulo”, não podem ser truncados de modo a perder significado nem se sobrepor a outros
  elementos.
- Toques rápidos e repetidos em “Entrar” não devem criar múltiplas instâncias consecutivas da
  Home.
- Ao voltar pela navegação do sistema a partir da Home, o usuário deve retornar à mesma Welcome;
  ao voltar na Welcome, aplica-se o comportamento padrão de saída do aplicativo.
- A área inferior deve respeitar barras de gesto ou navegação do dispositivo, mantendo todos os
  itens acessíveis.
- A ausência de rede, sessão autenticada, veículo real ou localização real não altera os dados
  demonstrativos nem impede o fluxo Welcome → Home.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST apresentar a Welcome como primeiro destino desta experiência.
- **FR-002**: A Welcome MUST usar fundo em gradiente roxo, o nome “PitGo” como wordmark textual
  estilizado no topo, textos brancos e margens laterais visualmente consistentes com
  aproximadamente 24 dp na referência; nenhum símbolo gráfico adicional de marca é necessário.
- **FR-003**: A Welcome MUST apresentar o título em duas linhas “Seu carro precisa.” e “O PitGo
  resolve.”, acompanhado de subtítulo que explique a oferta de assistência veicular.
- **FR-004**: A Welcome MUST apresentar exatamente três benefícios: “Atendimento rápido” com
  “PitTechs próximos em minutos.”; “Técnicos verificados” com “Profissionais avaliados e
  aprovados.”; e “Tudo pelo app” com “Bateria, óleo, pneus, diagnóstico e mais.”. Os benefícios
  MUST ser apresentados em cards com roxo mais claro que o fundo.
- **FR-005**: A Welcome MUST apresentar as ações “Criar conta”, “Entrar” e “Sou uma empresa ·
  Cadastrar” com formato arredondado e tratamentos visuais que permitam distingui-las.
- **FR-006**: Somente a ação “Entrar” MUST navegar nesta entrega, levando diretamente à Home; as
  demais ações MUST permanecer sem continuidade funcional e não podem abrir telas não solicitadas.
- **FR-007**: Na apresentação clara, a Home MUST usar fundo claro e apresentar a saudação “Olá,”,
  o nome demonstrativo “Rafael 👋” e um avatar circular roxo com a letra “R”.
- **FR-008**: A Home MUST apresentar um card de veículo com o rótulo “Veículo selecionado”, o modelo
  “BMW 320i M Sport”, os dados “2020 · PGO-2A20” e identificação visual de carro.
- **FR-009**: A Home MUST apresentar “Av. Paulista, 1500 · São Paulo” abaixo do card de veículo,
  acompanhado por identificação visual roxa de localização.
- **FR-010**: A Home MUST apresentar o título “O que seu carro precisa hoje?” seguido por uma opção
  destacada com “Não sei o que aconteceu” e “Descreva o problema e sugerimos o serviço”; esse card
  MUST aceitar toque, fornecer feedback visual e permanecer na Home.
- **FR-011**: A Home MUST apresentar exatamente seis serviços, na ordem de leitura: Bateria — A
  partir de R$ 320; Troca de óleo — A partir de R$ 220; Pneus — A partir de R$ 180; Luz no painel
  — A partir de R$ 150; Pequenos reparos — A partir de R$ 260; Carro não liga — A partir de R$ 280.
- **FR-012**: Cada serviço MUST possuir identificação visual coerente, nome completo e preço
  inicial, apresentados em um card com comportamento visual consistente com os demais. Todos os
  cards de serviço MUST aceitar toque, fornecer feedback visual e permanecer na Home.
- **FR-013**: O catálogo MUST usar uma grade de duas colunas em smartphones e permitir rolagem
  quando o conteúdo não couber na área disponível.
- **FR-014**: A Home MUST manter uma barra inferior fixa com Início, Serviços, Garagem,
  Atendimentos e Perfil; Início MUST estar selecionado em roxo e os demais itens MUST permanecer
  sem navegação nesta entrega.
- **FR-015**: A barra inferior MUST permanecer visível sem impedir o acesso ao último item do
  conteúdo rolável.
- **FR-016**: As duas telas MUST seguir uma identidade visual centralizada baseada em roxo
  principal `#7C32E8`, roxo escuro `#5520A8`, fundo claro `#FAF9FD`, branco `#FFFFFF`, texto
  principal `#17151D` e texto secundário `#6F6B78`, admitindo apenas ajustes necessários de
  contraste.
- **FR-017**: As telas MUST ser utilizáveis nos tamanhos de smartphone suportados, em orientação
  vertical e horizontal e com ampliação de texto, sem sobreposição, corte de conteúdo essencial ou
  áreas inacessíveis.
- **FR-018**: Todo controle interativo MUST oferecer feedback imediato, área de toque adequada e
  descrição compreensível; ícones informativos MUST ter significado disponível sem depender
  somente de cor.
- **FR-019**: O conteúdo MUST estar disponível integralmente sem rede, autenticação, permissão de
  localização, banco de dados ou dados pessoais reais.
- **FR-020**: O sistema MUST preservar o comportamento esperado de retorno: Home retorna à Welcome
  e toques repetidos em “Entrar” não acumulam destinos duplicados.
- **FR-021**: A implementação desta feature MUST limitar-se às duas telas, aos componentes visuais
  nelas exibidos e ao fluxo Welcome → Home, sem criar continuidade de cadastro, diagnóstico,
  serviço, garagem, atendimento ou perfil.
- **FR-022**: A Welcome MUST manter seu gradiente roxo nos modos claro e escuro; a Home MUST
  acompanhar o modo de aparência do sistema com paletas específicas que preservem hierarquia
  visual, legibilidade e contraste sem alterar o conteúdo ou o fluxo.

### Scope Boundaries

- **In scope**: apresentação da Welcome e da Home, conteúdo demonstrativo, feedback visual,
  responsividade, acessibilidade e navegação básica entre as duas telas.
- **Out of scope**: criação de conta, autenticação real, cadastro empresarial, seleção real de
  veículo ou endereço, diagnóstico, listagem ou contratação de prestadores, pagamentos,
  integrações remotas, persistência e qualquer destino da barra inferior além de Início.

### Key Entities

- **Benefício**: item demonstrativo da Welcome composto por título, descrição e identificação
  visual, exibido em uma ordem definida.
- **Perfil demonstrativo**: identidade local usada na Home, composta pelo nome Rafael e pela
  inicial R; não representa uma conta autenticada.
- **Veículo demonstrativo**: contexto local composto por rótulo, modelo, ano, placa e endereço;
  não representa um veículo persistido.
- **Serviço demonstrativo**: opção informativa composta por nome, identificação visual, texto de
  preço inicial e ordem de exibição.
- **Item de navegação**: destino visual da barra inferior composto por nome, identificação visual
  e estado selecionado; apenas Início está ativo nesta entrega.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 95% das aberturas em condições normais, o usuário visualiza conteúdo útil da
  Welcome em até 2 segundos.
- **SC-002**: Em 100% das validações funcionais, o usuário chega à Home com uma única seleção de
  “Entrar” e um toque repetido rapidamente não cria mais de uma Home no histórico.
- **SC-003**: Em 100% dos tamanhos de smartphone e escalas de texto suportados na validação, todo o
  conteúdo especificado permanece legível e alcançável por rolagem, sem sobreposição.
- **SC-004**: Em 100% das validações sem conexão, os três benefícios, o contexto do veículo e os
  seis serviços continuam disponíveis com os textos e valores corretos.
- **SC-005**: Pelo menos 90% dos participantes de uma avaliação de usabilidade identificam um
  serviço adequado a uma necessidade apresentada em até 10 segundos na primeira tentativa.
- **SC-006**: Em 100% das verificações de acessibilidade, controles possuem nome compreensível,
  foco acionável, alvo de toque adequado e informação que não depende exclusivamente de cor.
- **SC-007**: Em 100% das validações da Home, a barra inferior permanece fixa e o último serviço
  pode ser alcançado sem ficar encoberto.

## Assumptions

- A descrição textual fornecida é a referência funcional desta especificação; eventuais imagens
  de referência servem apenas para proximidade visual e não ampliam o escopo.
- Os textos demonstrativos, valores, nome, avatar, veículo, placa e endereço são fixos nesta
  entrega e não precisam ser editados nem persistidos.
- “Entrar” representa apenas a passagem para a Home; não existe verificação de credenciais ou
  estado de sessão nesta feature.
- Todas as ações sem destino explicitamente solicitado apresentam feedback visual, mas mantêm a
  tela atual.
- A apresentação clara descrita é a referência principal da Home; a Home acompanha o modo de
  aparência do sistema, enquanto a Welcome preserva o gradiente roxo em ambos os modos. Esta
  feature não adiciona um novo seletor de tema.
- O comportamento padrão de retorno do dispositivo é suficiente para Welcome e Home; não há gesto
  ou transição personalizada exigida.
