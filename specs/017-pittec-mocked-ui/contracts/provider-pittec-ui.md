# UI Contract: Home e navegação mockadas do prestador PitTec

## Route selection and isolation

- `ProviderReady` com `locationMode == PITTEC` abre `PitTecProviderDashboardRoute`.
- FIXED e MOBILE continuam usando o dashboard atual sem mudança funcional.
- A rota PitTec não instancia `ProviderDashboardViewModel`, não restaura dispatch e não inicia
  tracking/notificações reais.
- Ao detectar sessão PITTEC, o app remove PITTEC do watchdog e envia uma parada local-only para
  eventual serviço legado; essa parada cancela coleta/notificação local sem RPC nem alteração de
  disponibilidade remota.

## Location gate

O mapa é composto somente em `ProviderLocationGate.READY`.

| Estado | Conteúdo obrigatório | Ação |
|---|---|---|
| `CHECKING_PERMISSION` | feedback de verificação | nenhuma |
| `PERMISSION_REQUIRED` | localização precisa necessária | solicitar COARSE + FINE |
| `APPROXIMATE_ONLY` | precisão aproximada não atende à Home | solicitar precisão/abrir ajustes do app |
| `SERVICES_DISABLED` | localização do sistema desativada | abrir ajustes de localização |
| `LOCATING` | obtendo posição real | aguardar com progresso textual |
| `UNAVAILABLE` | não foi possível obter fix no timeout | tentar novamente |
| `READY` | mapa, marcador real do PitTec e estado Disponível | permitir evento mock |

Ao retornar dos Ajustes ou retomar a Activity, a rota revalida permissões e serviços. Nenhuma
coordenada mock é mostrada enquanto o gate estiver bloqueado. A posição real é lida em foreground,
mantida apenas em memória e nunca publicada.

## Incoming request

- A fixture gera uma solicitação uma vez quando a Home está pronta; estados disponível/concluído
  também oferecem “Simular nova solicitação” para repetir a demonstração explicitamente.
- O ViewModel cria `MockServiceRequest` com revisão nova e `pulseConsumed=false`.
- Se animações estiverem habilitadas e o efeito ainda não tiver sido consumido, a tela faz
  exatamente três pulsos roxos em até 3 s e marca o efeito consumido no início.
- Com escala de animação zero, o pulso é omitido. Em todos os casos, um card roxo persistente
  contém cliente, perímetro/distância, total, “Aceitar” e “Recusar”.
- O card usa texto/estrutura/semântica além da cor e permanece acionável após o efeito.
- `onAccept()` e `onDecline()` competem pela mesma revisão: a primeira ação válida vence, desabilita
  imediatamente ambas e qualquer repetição vira no-op.
- Recusar remove o alerta e volta a Disponível. Aceitar cria um atendimento local e abre o mapa de
  acompanhamento em até 2 s.

## Bottom navigation

O mesmo `PitGoBottomNavigation` do app recebe `PitTecBottomNavigationItems` nesta ordem:

1. Mapa
2. Dados da corrida
3. Valores
4. Perfil

Ícone e label aparecem conforme o componente atual. A seleção usa indicador, ícone e texto, nunca
somente cor. As quatro áreas são abas dentro da mesma destination e compartilham o mesmo estado.

### Mapa

- Renderiza localização real em Disponível/Incoming e ambos os participantes após aceite.
- Contém estado textual e ações correspondentes.

### Dados da corrida

- Antes do primeiro aceite: estado vazio explicativo.
- Depois: cliente, PitTec, perímetro, distância mais recente, estado, solicitação/aceite/início e
  conclusão quando existentes.
- Mostra apenas `currentOrLastAppointment`; não lista histórico.

### Valores

- Antes do primeiro aceite: estado vazio explicativo.
- Depois: R$ 50,00 fixos, R$ 10,00 × faixas, adicional e total do mesmo atendimento mostrado em
  Dados da corrida.
- Não mostra saldo, extrato, ganhos ou ação financeira.

### Perfil

- Mostra dados básicos já presentes no `ProviderSnapshot` e a ação de logout existente.
- Não reutiliza campos de veículo/endereço exclusivos do perfil cliente e não grava dados.

## Appointment actions

- No deslocamento, “Já nos encontramos” registra a confirmação local uma vez; o card informa que
  aguarda o cliente e avança após resposta automática de 3 s.
- Em andamento, “Finalizar serviço” registra uma vez; o card informa que aguarda o cliente e
  conclui após resposta automática de 3 s.
- Após conclusão, movimento para, `currentOrLastAppointment` permanece disponível nas abas e
  “Voltar ao mapa” retorna ao estado Disponível sem apagar o último atendimento.

## Accessibility and layout

- Solicitação nova é anunciada semanticamente; mudanças comuns de estado usam live region polite.
- Botões respeitam os touch targets existentes; conteúdo das abas é rolável com fonte ampliada.
- Falha do mapa não remove informações ou ações textuais.
- Light/dark usam `MaterialTheme.colorScheme.primary`/containers, preservando contraste e o roxo do
  design system sem cor hardcoded na tela.
