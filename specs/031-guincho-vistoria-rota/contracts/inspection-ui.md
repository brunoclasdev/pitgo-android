# Contrato de UI e ciclo de vida

Referências: [spec](../spec.md), [modelo](../data-model.md), [fotos](inspection-api.md), [rota](guincho-route-api.md).

## Álbum do guincheiro

- Grade lazy de 17 pontos em ordem estável, quatro principais em destaque e opcionais identificadas; detalhes livres em coleção adicional. Mostrar progresso obrigatório 0/4 separado do total.
- Mini quadro com nome, obrigatoriedade, miniatura e estado textual: Pendente, Preparando, Salvando, Salva ou Falha. Estado e ações não dependem somente de cor.
- Câmera ativa é exclusiva; preparar/salvar um item não bloqueia capturar outro ponto, rolar ou abrir uma imagem. Enviar conjunto continua bloqueado por obrigatório ausente ou qualquer inclusão/substituição pendente.
- Callback aceito volta imediatamente ao álbum com raw local no quadro correto. Não exigir abrir uma tela de prévia ou tocar novamente em Salvar para voltar ao álbum; confirmação manual de saída não deve se tornar etapa obrigatória.
- Na substituição, conservar a foto confirmada com indicação de nova tentativa pendente. Recuperar ou abandonar explicitamente a substituição; nunca apresentar a nova como salva antes da confirmação.
- Recusa da câmera, arquivo perdido, armazenamento insuficiente ou falha remota mostra ação somente no ponto afetado. Cancelar a câmera mantém o álbum como estava.

## Rotação, resultado e retomada

Registrar TakePicture incondicionalmente na rota, com chave estável e capture_id persistido antes do launch. Reconstrução da tela não relança automaticamente a câmera nem processa duas vezes um callback. AWAITING_CAMERA não é sucesso só porque existe um arquivo incompleto; reconcile resultado pendente do Activity Result com o journal.

Na reabertura após encerramento da tarefa, se nenhum resultado puder ser restaurado, sair da espera com aviso de captura interrompida e ação de recaptura. Preservar o raw para recuperação explícita; não exibir sucesso nem abrir a câmera automaticamente.

Tentar novamente nesse caso é uma recuperação explícita do raw preservado, não confirmação automática pela presença do arquivo. Exigir arquivo disponível/legível e contexto editável antes do envio. Quando o callback não foi persistido, usar o horário aproximado de gravação do raw como captured_at; callbacks já registrados mantêm sua data congelada. Preparação, upload e confirmação continuam necessários antes de Salva.

Solicitar portrait somente na vistoria em telefones suportados e restaurar requestedOrientation anterior ao sair; não reter Activity no ViewModel. Em telas/janelas onde o sistema permite rotação apesar da solicitação, manter apresentação vertical do conteúdo, adaptação ao espaço e estado da captura. Miniatura/full devem representar corretamente fotos horizontais; não girar bytes pela orientação atual da tela.

Persistência e runner ficam no Repository/data sources. GuinchoInspectionSyncViewModel na raiz autenticada controla ativação por sessão/conectividade/lifecycle, enquanto UI emite eventos de visibilidade; não acessar Repository diretamente pelo Composable. Retomar fila da conta ao abrir PitGo, mesmo que a primeira tela seja o mapa inicial. Fechar/pausar não apaga pendências. Logout explícito/troca de ambiente invalida geração, cancela operações e limpa apresentação/cache de leitura, preservando journals e arquivos de capturas aceitas ainda não confirmadas por conta/ambiente. Outra conta não os consulta nem envia; autenticação da mesma conta no mesmo ambiente e revalidação de permissões permitem retomada. Remoção exige salvamento confirmado ou descarte explícito permitido.

Se câmera externa suspender a visibilidade da Activity, a fila pode pausar e retomar; o retorno não depende de manter Job vivo. Imagem aceita permanece durável e os UUIDs não mudam. Reabertura sem rede mostra pendência; conexão recuperada no app retoma automaticamente, sem botão de retomada obrigatório.

O runner usa uma tolerância de 10 s na perda de visibilidade para não cancelar o envio anterior a cada abertura breve da câmera. Logout/perda de sessão ou rede continuam interrompendo imediatamente; morte do processo encerra os Jobs. Isso não cria garantia de upload com o aplicativo fechado, nem altera a retomada pelo journal.

## Revisão do cliente e histórico

Mostrar conjunto da versão enviada com categoria/legenda, miniaturas e ampliação. Não preencher opcionais vazias como erro. Imagens carregam individualmente; falha não inutiliza os outros quadros. Pré-carga/validação dos originais é limitada e cede prioridade à imagem aberta.

Aprovação só fica habilitada com originais do conjunto disponíveis e validados, versão vigente e permissão atual. Miniatura, sucesso de envio no outro aparelho ou cache de outra conta não bastam. Erro posterior em uma imagem desabilita aprovação até recuperação. Exibir progresso da preparação para revisão sem bloquear navegação.

Após aprovação confirmada, retornar à transação e aguardar início pelo guincheiro. Histórico mantém conjuntos, decisão e orientação originais, sem exigir novas opcionais em vistorias antigas. Não esconder fotos por categoria desconhecida: leitor v2 pode apresentar um detalhe adicional com rótulo genérico quando necessário, sem considerá-lo obrigatório.

## Carregamento e cache

AsyncImage mantém size por constraints e chaves imutáveis de conta/ambiente/foto/variante; preview por capture_id. Até dois downloads; thumb/visualização explícita têm prioridade, validação antecipada de originais ocorre sem bloquear UI. Arquivos temporários nunca chegam ao renderer. Merge de revisão preserva IDs inalterados; falha de sessão/permissão invalida o contexto, não reaproveita imagens entre contas.

Antes dos ensaios de desempenho, T030/T046 integram eventos monotônicos de entrada e conclusão visível ao trace do Android. Sucesso de download/decodificação sozinho não comprova imagem exibida: contar até o primeiro frame com a miniatura/original ou percurso esperado. Medições de ação contam até o primeiro frame de feedback. Registrar somente duração/categoria e amostra anônima; método, ferramenta e cálculo de p95 ficam no quickstart. T061 revisa essa instrumentação, sem adiar sua criação para depois das validações.

## Mapa de transporte

- Apenas após IN_PROGRESS e início confirmado, abrir/enquadrar percurso viário e destino do veículo no mapa do guincheiro. Aprovação isolada mantém a etapa aguardando início.
- Carregando/recalculando mostra atividade de rota sem bloquear posição, detalhes ou conclusão quando esta for permitida pelo fluxo existente.
- Desvio confirmado recalcula automaticamente; oscilações, GPS antigo ou impreciso não disparam recálculos repetidos. Marcador atualiza sem chamar roteador em cada fix.
- Falha mantém destino e rota anterior como referência, se houver, com mensagem e ação de recuperação. Sem geometria válida, não desenhar uma linha direta como trajeto.
- A câmera respeita gesto do usuário e não volta ao pickup durante transporte. Retomada recupera etapa/destino sem novo início; encerramento remove indicação de rota ativa.
- Cliente mantém a transação e seu tracking; esta entrega não exige nova navegação de rota para ele nem comandos por voz.

## Acessibilidade

Labels pt-BR de ponto, situação, veículo e ação; TalkBack anuncia mudança de estado e falha sem repetir toda a grade. Áreas de toque de pelo menos 48 dp, fontes ampliadas, contraste e temas PitGo. Ordem lógica na grade e no mapa; estados não dependem de cor/ícone. Validar portrait/landscape, multiwindow e >=600dp sem prometer bloqueio de orientação que o sistema não oferece.
