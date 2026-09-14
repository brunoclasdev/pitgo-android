# Data Model: Recursos de identidade visual

Esta feature não altera entidades persistidas, banco de dados, APIs ou modelos de domínio. Os
elementos abaixo são recursos estáticos empacotados no aplicativo.

## AppIconAsset

- **Fonte**: `pitgo-icon.png`, fornecido pelo produto.
- **Uso**: launcher, lista de aplicativos, recentes e informações do app.
- **Regras**: arte completa dentro da área segura; versões por densidade; sem transparência ou
  corte que impeça reconhecimento; referência do manifesto permanece `@mipmap/ic_launcher`.

## SplashImageAsset

- **Fonte**: `pitgo-image1200.png`, fornecido pelo produto.
- **Uso**: imagem da inicialização do aplicativo.
- **Regras**: imagem inteira centralizada, proporção preservada, sem deformação; fallback visual se
  o recurso não puder ser carregado.

## StartupPresentationState

- **Ready**: primeira tela funcional preparada; permite remover a splash após o mínimo de 500 ms.
- **Loading**: inicialização ainda em andamento; mantém a splash visível.
- **Fallback**: recurso visual não carregado; usa uma superfície/identificação segura existente e
  continua a inicialização sem travar.

## Relationships

`AppIconAsset` identifica a instalação fora do app. `SplashImageAsset` apresenta a marca durante
`StartupPresentationState.Loading`. Nenhum dos dois depende de autenticação, localização ou dados
do usuário.
