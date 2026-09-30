# UI Contracts: Guincho

## Cliente

Home Guincho → rota centralizada GuinchoMap → preencher retirada pela localização atual → informar destino → confirmar retirada/destino/veículo no contexto do mapa → selecionar ícone → mini card nome/total/Contratar → checkout existente → pago/aguarda aceite → acompanhamento → confirmação ou contestação.

Pode mostrar mapa antes da entrada, sem seleção/contratação habilitada até confirmar dados. Cotação vinculada ao provider_id/input_revision selecionados; troca de marcador/dados descarta resultado antigo. Reserva só em Contratar. Loading/vazio/erro/GPS/rota indisponível/offline precisam feedback; total definitivo sem breakdown ou linha reta. Voltar não cria cobrança ou cancela pedido pago silenciosamente.

Conclusão: card visível sem expandir detalhes; Sim, deu tudo certo ou Informar problema com motivo obrigatório. Histórico abre disputa antiga separadamente do novo pedido operacional.

## Guincheiro

Notificação/dashboard → oferta paga com nome/carro/retirada/destino/remuneração → Aceitar/Recusar → a caminho → Cheguei → Iniciar transporte → Concluir atendimento → espera de confirmação. Reabrir na espera retorna à conclusão; ainda não Disponível. DISPUTED libera novas ofertas conforme disponibilidade escolhida, sem fechamento automático do caso. Restore novo pedido tem prioridade; resolução velha afeta só histórico/notificação.

## Dashboard

Fila Contestação de Guincho paginada → detalhe → Concluir sem reembolso ou Cancelar com reembolso integral → justificativa e confirmação. MANAGER+ na UI e backend; resolvido fica leitura; repetição mantém primeira decisão. Refund pending/failed não informa dinheiro devolvido antes de confirmação.

## Apresentação e ambiente

Material/design system existente, áreas ≥48dp, descrições de ícones, estado além de cor, fonte ampliada e temas Light/Dark/System. Mini card com padding medido preserva região útil de mapa. Valores pt-BR. Navegação ID/revisão evita redirecionamento repetido; terminal só fecha o pedido correspondente. Dev simula total sem MP; Homol/Prod usam MP e total definitivo, preservando demais serviços.

A retirada não tem campo de texto editável. A permissão de localização é solicitada ao abrir a tela; a retirada usa posição recente e endereço obtido pelo Geocoder. Sem GPS, o cliente seleciona o ponto no mapa. Sem endereço disponível, o ponto continua identificado pelas coordenadas. Só o destino tem campo de endereço.
