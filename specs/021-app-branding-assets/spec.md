# Feature Specification: Aplicar identidade visual do app

**Feature Branch**: `021-app-branding-assets`

**Created**: 2026-09-14

**Status**: Draft

**Input**: User description: "Colocar o ícone do aplicativo usando `pitgo-icon.png` da pasta imagens e usar `pitgo-image1200.png` como imagem em uma tela de splash."

## Clarifications

### Session 2026-09-14

- Q: A splash deve permanecer visível apenas até o carregamento inicial terminar ou deve ter um tempo mínimo de exibição? → A: Até o carregamento terminar, com mínimo de 500 ms.
- Q: Como a imagem `pitgo-image1200.png` deve se ajustar à tela de splash? → A: Imagem inteira centralizada, preservando proporção e sem corte.
- Q: O `pitgo-icon.png` deve aparecer como a arte completa do ícone em todos os launchers, mesmo quando o Android aplicar máscaras de ícone adaptativo? → A: Preservar a arte completa dentro da área segura do launcher.

## User Scenarios & Testing

### User Story 1 - Ícone do aplicativo (Priority: P1)

Como usuário, quero reconhecer o PitGo pelo ícone oficial na tela inicial do dispositivo e nas áreas do sistema que exibem aplicativos.

**Why this priority**: O ícone é a identificação principal do aplicativo fora da interface e deve estar correto em qualquer instalação.

**Independent Test**: Instalar ou atualizar o aplicativo em um dispositivo Android e verificar o ícone no launcher, na lista de aplicativos recentes e nas informações do aplicativo.

**Acceptance Scenarios**:

1. **Given** o aplicativo está instalado, **When** o usuário visualiza o launcher, **Then** o ícone exibido corresponde à imagem oficial `pitgo-icon.png`.
2. **Given** o aplicativo é atualizado mantendo a instalação existente, **When** o sistema atualiza os recursos do app, **Then** o ícone oficial continua sendo exibido sem ícone genérico ou quebrado.

### User Story 2 - Splash com imagem oficial (Priority: P1)

Como usuário, quero ver a imagem oficial do PitGo durante a abertura do aplicativo para reconhecer a marca enquanto o conteúdo inicial é carregado.

**Why this priority**: A abertura é o primeiro contato visual e deve apresentar uma identidade consistente antes da tela inicial.

**Independent Test**: Abrir o aplicativo a partir do launcher em um dispositivo Android e verificar a tela de splash durante o carregamento e a transição para o conteúdo inicial.

**Acceptance Scenarios**:

1. **Given** o aplicativo está fechado, **When** o usuário o abre, **Then** a tela de splash apresenta `pitgo-image1200.png` de forma íntegra e centralizada.
2. **Given** o carregamento inicial termina, **When** o aplicativo navega para a primeira tela, **Then** a splash desaparece sem deixar uma tela vazia, travamento ou imagem deformada.
3. **Given** o dispositivo possui diferentes densidades e tamanhos de tela, **When** o aplicativo é aberto, **Then** a imagem mantém proporção, legibilidade e margens adequadas.

### Edge Cases

- Se um dos arquivos de imagem não estiver disponível no pacote de recursos no momento da implementação, a implementação deve interromper a integração desse recurso e registrar a dependência ausente, sem inserir um arquivo substituto inventado.
- Se o sistema já tiver um ícone antigo em cache, uma nova instalação ou atualização deve continuar fornecendo os novos recursos válidos para que o launcher possa atualizar a representação.
- Se o carregamento inicial for muito rápido, a splash ainda deve respeitar a transição normal para a primeira tela, sem piscar ou permanecer indefinidamente.
- Imagens com transparência, proporção ou resolução inesperadas não devem resultar em distorção visual nem em conteúdo cortado de forma que impeça o reconhecimento da marca.

## Requirements

### Functional Requirements

- **FR-001**: O aplicativo MUST usar `pitgo-icon.png` como fonte visual do ícone oficial do aplicativo.
- **FR-002**: O ícone MUST ser disponibilizado para os tamanhos e densidades de launcher suportados pelo aplicativo, preservando a arte completa, a proporção e a legibilidade da imagem original dentro da área segura de máscaras do launcher.
- **FR-003**: O aplicativo MUST exibir `pitgo-image1200.png` na tela de splash durante o carregamento inicial.
- **FR-004**: A splash MUST exibir a imagem oficial inteira, centralizada e preservando sua proporção em telas suportadas, sem esticar, deformar ou cortar o conteúdo.
- **FR-005**: A splash MUST desaparecer automaticamente quando a primeira tela funcional estiver pronta, respeitando um tempo mínimo de 500 ms, e não MUST bloquear a navegação normal do aplicativo.
- **FR-006**: O aplicativo MUST fornecer um fallback visual seguro caso a imagem da splash não possa ser carregada em tempo de execução, sem travar ou exibir uma tela vazia.
- **FR-007**: A integração MUST preservar os fluxos existentes de autenticação, navegação, tema e inicialização do aplicativo.
- **FR-008**: Os recursos visuais MUST ser empacotados no aplicativo final e verificados em uma instalação limpa e em uma atualização sobre uma versão anterior.

### Key Entities

- **Ícone do aplicativo**: Recurso visual usado pelo launcher e pelas superfícies do sistema que identificam o aplicativo.
- **Imagem de splash**: Recurso visual exibido durante a inicialização antes da primeira tela funcional.
- **Recursos de marca**: Arquivos fornecidos pelo produto (`pitgo-icon.png` e `pitgo-image1200.png`) que devem ser preservados como fonte visual da identidade do app.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Em 100% das instalações limpas verificadas nos dispositivos Android suportados, o launcher exibe o ícone PitGo oficial completo, sem corte relevante causado pela máscara do launcher.
- **SC-002**: Em 100% das aberturas verificadas, a splash exibe a imagem oficial antes da primeira tela funcional, sem tela vazia ou travamento.
- **SC-003**: Em uma verificação com pelo menos três tamanhos ou densidades de tela, a imagem inteira da splash permanece centralizada, mantém proporção e não apresenta deformação ou corte.
- **SC-004**: A transição da splash para a primeira tela funcional ocorre em até 2 segundos após a inicialização estar pronta, respeitando o mínimo de 500 ms e sem exigir interação adicional do usuário.
- **SC-005**: Nenhum fluxo existente de login, home ou navegação apresenta regressão após a inclusão dos recursos de marca.

## Assumptions

- Os arquivos `pitgo-icon.png` e `pitgo-image1200.png` serão disponibilizados no diretório de imagens indicado pelo usuário antes da implementação.
- A plataforma-alvo é Android e o comportamento de splash deve seguir o mecanismo de inicialização já adotado pelo aplicativo.
- A mesma identidade visual será usada para cliente e Pittec, sem variações por perfil nesta primeira versão.
- O fallback da splash pode usar uma superfície e uma identificação textual do PitGo já existentes no tema do aplicativo.
- A inclusão do ícone e da splash não exige alteração de backend, banco de dados ou permissões.
