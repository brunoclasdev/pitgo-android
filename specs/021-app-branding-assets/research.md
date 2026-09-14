# Research: Aplicar identidade visual do app

## Decision: Usar o pipeline de recursos Android existente para o ícone

**Rationale**: O manifesto já aponta para `@mipmap/ic_launcher` e o projeto já possui recursos
adaptativos em `mipmap-anydpi-v26`. Substituir os layers/bitmaps existentes preserva o contrato do
launcher, a compatibilidade com instalações antigas e não introduz uma nova camada de UI.

**Alternatives considered**: Alterar o manifesto para apontar diretamente para um drawable PNG;
rejeitado porque perde o tratamento por densidade e o suporte adaptativo já presente.

## Decision: Usar splash compatível com Android 12+ e fallback para API 24+

**Rationale**: O aplicativo suporta API 24+, enquanto a splash nativa moderna do Android só possui
o mesmo comportamento visual a partir da API 31. O tema de inicialização deve usar os atributos
nativos quando disponíveis e o mecanismo AndroidX compatível quando necessário, mantendo a imagem
inteira centralizada. A duração mínima de 500 ms será controlada pela inicialização da Activity,
sem atrasar além do necessário.

**Alternatives considered**: Criar uma tela Compose de splash sempre visível; rejeitado porque
duplica o mecanismo de inicialização do sistema, pode piscar e perde consistência no Android 12+.

## Decision: Preservar a imagem inteira usando escala contain/área segura

**Rationale**: A especificação exige que `pitgo-image1200.png` não seja cortada nem deformada e que
`pitgo-icon.png` permaneça reconhecível sob máscaras de launcher. Os recursos serão preparados para
respeitar a área segura e as densidades suportadas, mantendo o arquivo-fonte como referência visual.

**Alternatives considered**: Escala que preenche e corta bordas; rejeitada porque pode remover partes
da marca. Esticar para preencher; rejeitado porque distorce o logotipo.

## Decision: Tratar os PNGs como dependências verificáveis de entrada

**Rationale**: Os arquivos foram nomeados pelo produto, mas não estão presentes no workspace no
momento do planejamento. A implementação deve falhar de forma explícita se eles não forem
fornecidos, evitando gerar uma marca substituta ou um recurso vazio.

**Alternatives considered**: Reutilizar o ícone atual como placeholder; rejeitado porque ocultaria
uma dependência de produto e poderia chegar ao APK final.
