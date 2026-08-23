# Quickstart: validar MapLibre e atualização em tempo real

## 1. Configuração

Além das propriedades Supabase existentes, fornecer em `local.properties` ignorado:

```properties
mapStyleUrl=https://<managed-provider>/<style>.json?key=<client-token>
mapStyleProviderName=<provider-name>
mapStyleAttribution=© OpenStreetMap contributors · <provider-credit>
mapStyleAttributionUrl=https://<provider-attribution-page>
```

CI usa `ORG_GRADLE_PROJECT_mapStyleUrl` e equivalentes. Somente token público/restrito; nenhum valor
real em arquivo versionado, argv, log ou artefato.

## 2. Backend local

```bash
supabase start
supabase db reset
supabase db lint
```

Confirmar migration: EMPTY ativo, ENDED terminal, topic/snapshot somente participante, publish
terminal negado e ausência de histórico. Não executar db push remoto automaticamente.

## 3. Build/dependências

```bash
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:assembleRelease
./gradlew :app:dependencyInsight --dependency android-sdk-opengl --configuration debugRuntimeClasspath
./gradlew :app:dependencyInsight --dependency maps-compose --configuration debugRuntimeClasspath
./gradlew :app:dependencyInsight --dependency play-services-maps --configuration debugRuntimeClasspath
./gradlew :app:dependencyInsight --dependency osmdroid-android --configuration debugRuntimeClasspath
```

Esperado: MapLibre OpenGL 13.5.0 presente; Maps Compose, Play Services Maps e osmdroid ausentes;
Play Services Location presente; apenas um backend MapLibre.

```bash
rg -n 'com\.google\.maps|com\.google\.android\.gms\.maps|MAPS_API_KEY|com\.google\.android\.geo\.API_KEY' app gradle
rg -n -i 'osmdroid|TileSourceFactory|ManagedXyz' app gradle
```

Esperado vazio. Revisar diff para nenhuma URL/token real.

## 4. Style gate

Validar build failure sem ecoar valores para campo ausente, HTTP, user-info/fragment, placeholder e
host demo/público. Antes de produção, inspecionar Style JSON e confirmar sources/TileJSON/sprites/
glyphs HTTPS gerenciados, pt-BR quando suportado e attribution completa.

## 5. Descoberta

Em API 24, 36 e smoke API 37:

1. Abrir serviço e conferir markers no endereço empresarial para FIXED/MOBILE.
2. Provider sem coordenada válida não aparece posicionado.
3. Mapa/Lista selecionam mesmo provider e abrem mesmo resumo.
4. Resumo mostra somente dados públicos e CTA.
5. CTA navega com serviceId + providerId, sem nome/preço na rota.
6. Testar clusters próximos e coordenadas iguais; todos acessíveis por sheet/lista.
7. Pan/zoom/busca rápida não aceita resposta antiga nem reseta câmera.
8. Negar localização mantém região alternativa.
9. Cadastro empresarial não instancia mapa.

## 6. Style/resources/lifecycle

1. Style válido carrega e attribution OSM/provedor permanece visível/clicável.
2. Style inválido/401 falha com retry e lista preservada.
3. Tile 401/429 repetido, sprite/glyph failure e recuperação produzem banner correto.
4. Tile isolado/cancelado durante pan não vira outage.
5. Modo avião preserva dados e recursos cacheados quando disponíveis.
6. Retry mantém câmera, seleção e último marker; não recria MapView nem limpa cache.
7. Retry seguido de Ended/session expiry não ressuscita marker em callback tardio.
8. Alternar Mapa/Lista, rotacionar e navegar repetidamente deixa um MapView/listener set vivo.
9. Testar aparelhos/emuladores de GPUs diferentes; nenhuma dependência Vulkan é carregada.

## 7. Realtime

1. Atualizações novas movem o mesmo marker/source.
2. Revisão 5 seguida de 4 não regride.
3. DELETE/tombstone seguido de evento igual/antigo não ressuscita.
4. Reinsert version 1 com publishedAt posterior é aceito.
5. Broadcast entre subscribe/snapshot vence snapshot atrasado.
6. Após 30s, marker permanece stale com última atualização.
7. Offline mantém stale; reconnect refaz subscribe + snapshot.
8. COMPLETED/CANCELLED recebe ENDED, limpa marker/canal e bloqueia publish.
9. Sessão expirada/trocada limpa coordenada antes do gate.
10. Terceiro usuário não recebe snapshot/topic.

## 8. Responsividade/acessibilidade

- 320dp, paisagem e fonte 200%; sem corte de ação.
- TalkBack opera Mapa/Lista, provider, resumo e CTA.
- MapView não é única representação.
- Controles >=48dp; CTA >=56dp.
- Cluster coincidente possui lista completa.
- Live/stale/ended comunicados por texto/ícone, sem anúncio a cada update.
- Attribution nunca fica permanentemente coberta.

## 9. Gate de produção

Registrar fornecedor, SLA/limites, attribution e restrições do token. Validar AAB e ABI delivery,
API/GPU matrix e política de logs. Reavaliar backend Vulkan apenas em feature separada após matriz
de dispositivos; não introduzir fallback silencioso.
