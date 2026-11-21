# Provedores de navegação — pesquisa para o MVP

Consulta: **2026-11-21** (Agente C). Legenda: **[F]** fato verificado na fonte oficial · **[F*]** verificado no código open source ou por medição própria · **[I]** inferência minha · **[N]** não encontrado/não verificado. Nenhuma conta criada, nenhuma chave usada; única chamada de rota: 1 teste no Valhalla público (Sé → Paulista).

## 1. GraphHopper Directions API (hospedada)

Fontes: [docs/OpenAPI](https://docs.graphhopper.com/) · [preços](https://www.graphhopper.com/pricing/) · [créditos](https://support.graphhopper.com/support/solutions/articles/44000718211-what-is-one-credit-) · [termos v9](https://www.graphhopper.com/terms/) · [atribuição](https://www.graphhopper.com/attribution/) · [core pt_BR](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/resources/com/graphhopper/util/pt_BR.txt)

| Item | Resultado |
|---|---|
| Endpoint | `GET https://graphhopper.com/api/1/route?key=…` — chave em toda requisição [F]. POST `/route` recomendado só para muitos pontos [F]. |
| `point` | Repetido, formato `lat,lon` [F]. Plano Free: máx. 5 pontos/requisição [F]. |
| `profile` | Free: só `car`, `bike`, `foot` [F]. `scooter` ("Moped", ~50 km/h, entregas) é pago [F]; `motorcycle` (Kurviger: prefere curvas, evita cidades e rodovias) "sob consulta" [F]. **Usar `car`** [I]. |
| `locale` | Default `en`; doc cita `pt_PT` [F]. O core tem `pt_BR.txt` ("vire à esquerda", "Entre na rotatória") [F*]; aceitação de `pt_BR` na hospedada: [I], confirmar na 1ª chamada. |
| `points_encoded` | Default `true` (polyline); `false` → `[lon,lat]` [F]. |
| `instructions` / `calc_points` / `elevation` | Defaults `true` / `true` / `false` [F]. `details=street_name` opcional [F]. |
| `heading` | 0–360° (norte, horário), por ponto; `heading_penalty` (300 s) e `pass_through` — **todos exigem `ch.disable=true`** [F]. O Free "cannot use the flexible mode (ch.disable=true)" [F] ⇒ **sem heading no Free**. |
| Resposta | `paths[0].distance` (m), `.time` (ms), `.points`, `.instructions[]`; `info.copyrights` = `["GraphHopper","OpenStreetMap contributors"]` [F]. A doc avisa que as instruções "can sometimes be misleading" [F]. |

**`paths[0].instructions[i]`** [F]: `text` (no idioma de `locale`), `street_name`, `distance` (m), `time` (**ms**), `interval` = [i0, i1] índices em `points`, `sign`, `exit_number` e `turn_angle` (rad; 0<r<2π horário, -2π<r<0 anti-horário) só quando `sign = 6`, `heading`/`last_heading` opcionais. `exited` (bool) existe no serializador do core [F*], não na doc hospedada.

| sign | Significado [F] | sign | Significado [F] |
|---|---|---|---|
| -98 | retorno, lado desconhecido | 1 | leve à direita |
| -8 | retorno à esquerda | 2 | à direita |
| -7 | mantenha-se à esquerda | 3 | acentuada à direita |
| -6 | sair da rotatória (**"not yet used"**) | 4 | chegada (antes do último ponto) |
| -3 | acentuada à esquerda | 5 | antes de um ponto *via* |
| -2 | à esquerda | 6 | entrar na rotatória (+`exit_number`, `turn_angle`) |
| -1 | leve à esquerda | 7 | mantenha-se à direita |
| 0 | siga na via | 8 | retorno à direita |

O core também define `9` balsa, `-99` desconhecido, `101–103` transporte público [F*]; a doc exige tolerar sinais desconhecidos [F] → mapear para "siga" + `text` [I].

| HTTP | Doc [F] | Mensagens típicas |
|---|---|---|
| 400 | "Your request is not valid…"; corpo `{message, hints[]}` | "Cannot find point 0: lat,lon" (`PointNotFoundException`), "Connection between locations not found" (`ConnectionNotFoundException`), "Point 0 is out of bounds…" — textos do core [F*]; iguais na hospedada [I] |
| 401 | "Authentication necessary" | chave ausente/inválida |
| 429 | "API limit reached" — limite diário **ou** por minuto | aguardar `X-RateLimit-Reset` (s) [I] |
| 500 | "Internal server error…" | 5xx: nova tentativa com espera [I] |

Cabeçalhos `X-RateLimit-Limit/Remaining/Reset/Credits` em todas as respostas [F].

**Plano Free e termos** [F]: 0 €, **500 créditos/dia**, 5 locais, crédito por minuto limitado (sem número), sem cartão, sem flexible mode, **"for non-commercial use only"**, suporte só no fórum. Rota com 2–10 pontos = **1 crédito**; alternativas +1. Termos (v9, 09/08/2021): uso comercial do Free só "in the development phase" e 1 Free por usuário (§5.1); cache temporário no cliente permitido, redistribuir a API exige pacote próprio (§5.2); impedir que terceiros usem a API violando os termos (§5.6). **Atribuição**: §3 exige atribuição GraphHopper sem white label e sempre a do OSM; a página de atribuição diz que a da GraphHopper "is not required, but appreciated" e exige link para `openstreetmap.org/copyright` → conflito; mostrar as duas [I]. Distribuição pelo GitHub: chave do dono embutida no APK fica extraível e todos dividem 500 créditos/dia [I] → manter **chave do próprio usuário** (desenho atual da 0.3.0).

**Geocoding API** [F]: `GET /api/1/geocode?q=…&locale=pt&limit=5&key=…` (mesma chave; `point=lat,lon` como viés; `reverse=true`). Provedores: `default` (motor interno, prefix search/autocomplete, `locale` en/fr/es/de/pt/ru/it, 0,3 crédito), `nominatim` (hospedado pela OpenCage, sem autocomplete, 0,9), `opencagedata` (0,9). "provider … can fall back to `default` at any time". Consta no plano Free; limite próprio [N].

## 2. Google Navigation SDK for Android + turn-by-turn feed

Fontes: [visão geral](https://developers.google.com/maps/documentation/navigation/android-sdk/overview) · [setup](https://developers.google.com/maps/documentation/navigation/android-sdk/cloud-setup) · [cobrança](https://developers.google.com/maps/documentation/navigation/android-sdk/usage-and-billing) · [preços](https://developers.google.com/maps/billing-and-pricing/pricing) · [TBT feed](https://developers.google.com/maps/documentation/navigation/android-sdk/tbt-feed) · [cobertura](https://developers.google.com/maps/documentation/navigation/android-sdk/coverage-nav-sdk) · [termos §12](https://cloud.google.com/maps-platform/terms/maps-service-terms)

- Cadastro: projeto Google Cloud **com billing ativo**, API `navigationsdk.googleapis.com`, chave restrita [F]. O usuário final precisa aceitar os Termos de Navegação do Google (o SDK pede na 1ª navegação) [F]. App com Nav SDK não pode incluir o Maps SDK [F].
- Custo: SKU Enterprise "Navigation Request", cobrado por destino/waypoint pedido; guidance e rerrotas grátis [F]; **1.000 eventos/mês grátis**, depois **US$ 25,00/1.000** (até 100 mil) [F]. Uso pessoal (~60 rotas/mês) caberia na franquia, mas exige cartão e cada instalação gasta a cota do dono da chave [I].
- Termos §12: pode usar sem mapa Google; **não** pode combinar com mapa não-Google; cache de lat/lng ≤ 30 dias [F]. Nada encontrado que proíba app pessoal ou distribuído [N].
- TBT feed: serviço recebe `NavInfo` ~1×/s (estado `ENROUTE`/`REROUTING`/`STOPPED`; `StepInfo` com `Maneuver`, distância ao próximo passo, via, faixas, ícones; tempo/distância restantes), citado para displays de duas rodas [F]. Depende de navegação guiada ativa no SDK [F/I]. Brasil: rotas de duas rodas (`TWO_WHEELER`) cobertas [F]. Complexidade alta [I].

## 3. Alternativas open source

| Opção | Fatos | Fonte |
|---|---|---|
| OSRM demo (`router.project-osrm.org`) | ≤ 1 req/s; "reasonable, non-commercial use-cases"; sem garantias; perfis car/foot/bike [F]. Sem moto [F]. Não gera texto de instrução (só `maneuver.type/modifier`) [I]. | [wiki](https://github.com/Project-OSRM/osrm-backend/wiki/Demo-server) |
| FOSSGIS (`routing.openstreetmap.de`, `valhalla1.openstreetmap.de`) | **1 req/s**; User-Agent que identifique o app (UA de biblioteca não serve); Referer se possível; máx. 2 conexões; atribuição + link `fixthemap`; e-mail de contato no site do app; "URLs … should not be hardcoded into the app"; comercial só se não for parte substancial; sem alto tráfego; um servidor, sem garantia, acesso revogável [F]. | [termos](https://www.fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen/) |
| Valhalla | `costing=motorcycle` (**BETA**: `use_highways`, `use_trails`, `use_curvature`) e `motor_scooter`; `language=pt-BR`; manobras `type` 0–43 (26/27 rotatória, `roundabout_exit_count`), `length` (km), `time` (s), `begin_shape_index`, textos `verbal_*`; location `heading` + `heading_tolerance` (60°); erros 171 "No suitable edges near location", 442 "No path could be found" [F]. Teste 21/11: valhalla1 v3.9.3 respondeu `motorcycle` + `pt-BR` (3,5 km) [F*]. | [API](https://valhalla.github.io/valhalla/api/route/api-reference/) |
| openrouteservice | Chave obrigatória; padrão 2.000 directions/dia; 403 ao estourar o diário, 429 por minuto [F]; perfis `driving-car`, `driving-hgv`, `cycling-*`, `foot-*`, `wheelchair` — sem moto [F]; 6.000 km e 50 waypoints/rota [F]. Termos, uso comercial e atribuição [N] (página de planos migrou para account.heigit.org e não renderizou). | [FAQ](https://giscience.github.io/openrouteservice/frequently-asked-questions) · [limites](https://giscience.github.io/openrouteservice/hosted-api-restrictions) |
| Auto-hospedagem (GraphHopper/OSRM/Valhalla + extrato Brasil) | Sem limites de terceiros; exige servidor 24 h, vários GB de RAM para importar o Brasil e atualização periódica [I]. Contraria "sem servidor próprio". | — |

## 4. Waze — existe acesso oficial às manobras?

- **Transport SDK**: banner oficial "Support for the Waze Transport SDK will end on November 1, 2026" → **encerrado** na data desta consulta [F]. Era só para parceiros aprovados de transporte; oferecia ETA, pontos da rota, direção e distância da próxima curva; "not intended for building your own navigation app" [F]. [fonte](https://developers.google.com/waze/intro-transport?hl=en)
- **Navigation Connect API** (sucessor indicado): abre Google Maps/Waze standalone; o backend recebe localização, ETA, status, distância/tempo restantes e polyline (SKU Enterprise); a doc não menciona manobras [F/N]. [fonte](https://developers.google.com/maps/documentation/navigation/connect/overview)
- **Deep links** só abrem o Waze para buscar/navegar; **Audio Kit** é para conteúdo de áudio; **Data Feeds**/**Waze for Cities** trocam dados de trânsito com parceiros e órgãos públicos [F]. [fonte](https://developers.google.com/waze)
- **Conclusão**: não há meio oficial atual para um app de terceiros obter manobras curva a curva do Waze [F + N]. Ler notificações/acessibilidade seria não oficial e frágil — fora do escopo.

## 5. Geocodificação (escolher o destino sem custo)

| Opção | Fatos | Limites/termos |
|---|---|---|
| `android.location.Geocoder` | `getFromLocationName(nome, max, listener)` e variante com bounding box: **API 33+** [F]; síncronas deprecadas na 33 (bloqueiam) [F]; `isPresent()`; "best guess", sem garantia, pode voltar vazio sem backend [F]. | Cotas e backend não documentados [N]; em aparelho com Google Play deve funcionar [I]. minSdk 31: na API 31–32 usar a síncrona em thread de fundo [I]. |
| Nominatim (OSMF) | Máx. **1 req/s**; UA/Referer identificável; atribuição; **autocomplete proibido**; cache recomendado; soma de todos os usuários conta; app deve poder trocar de servidor sem atualização [F]. | [política](https://operations.osmfoundation.org/policies/nominatim/) |
| Photon (komoot) | Typeahead suportado; "extensive usage will be throttled"; sem garantia [F]. | Termos/atribuição [N]. [site](https://photon.komoot.io/) |
| GraphHopper `/geocode` | Mesma chave, autocomplete, 0,3 crédito [F]. | Gasta a cota de rotas [I]. |

**Recomendação** [I]: (1) `Geocoder` do Android, busca ao tocar "Buscar" (não a cada tecla), viés por bounding box ao redor da posição; (2) fallback GraphHopper `/geocode` (`provider=default`, `locale=pt`); (3) entrada manual `lat,lon` e `intent-filter` `ACTION_VIEW` para `geo:lat,lon`, `geo:0,0?q=lat,lng(rótulo)`, `geo:0,0?q=endereço` (formatos [F] em [intents comuns](https://developer.android.com/guide/components/intents-common#Maps)).

## 6. Android 16 (API 36) e target 37 — FGS para navegar com tela bloqueada

Fontes: [tipos de FGS](https://developer.android.com/develop/background-work/services/fgs/service-types) · [iniciar FGS](https://developer.android.com/develop/background-work/services/fgs/launch) · [timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout) · [notificações](https://developer.android.com/develop/ui/views/notifications/notification-permission) · [mudanças 16](https://developer.android.com/about/versions/16/behavior-changes-16) · [mudanças 17](https://developer.android.com/about/versions/17/behavior-changes-17)

| Tipo | Permissão de manifesto | Pré-requisito em runtime [F] |
|---|---|---|
| `location` | `FOREGROUND_SERVICE_LOCATION` | localização do sistema ligada + `ACCESS_COARSE_LOCATION` ou `ACCESS_FINE_LOCATION` concedida |
| `connectedDevice` | `FOREGROUND_SERVICE_CONNECTED_DEVICE` | ≥ 1 de: `BLUETOOTH_CONNECT`/`_ADVERTISE`/`_SCAN` concedida; ou declarar `CHANGE_NETWORK_STATE`/`CHANGE_WIFI_STATE`/…; ou `UsbManager.requestPermission()` |

- Além de `FOREGROUND_SERVICE`; tipos no manifesto e em `startForeground()` (pode ser `LOCATION or CONNECTED_DEVICE`); tipo não declarado → `IllegalArgumentException`; permissão faltando → `SecurityException` [F].
- Localização é "while-in-use": não se cria FGS `location` em background sem `ACCESS_BACKGROUND_LOCATION` [F]. Iniciar pelo botão na Activity visível dispensa essa permissão [F/I]. Início em background (target 31+) → `ForegroundServiceStartNotAllowedException` [F].
- `POST_NOTIFICATIONS` (33+): não é exigida para iniciar FGS, mas a notificação é obrigatória; se negada, aparece só no Gerenciador de tarefas [F].
- Limites de tempo (target 35+): só `dataSync`/`mediaProcessing` (6 h/24 h) e `shortService` (~3 min); **nenhum para `location` ou `connectedDevice`** [F].
- API 36: edge-to-edge sem opt-out, predictive back, intents BLE `ACTION_KEY_MISSING`/`ACTION_ENCRYPTION_CHANGE` [F]; mudança específica de FGS `location`/`connectedDevice`: [N].
- API 37: "background audio hardening" — áudio/foco em background exige FGS com capacidade while-in-use [F] (relevante se houver voz/TTS [I]); `ACCESS_LOCAL_NETWORK` obrigatória só para LAN [F].

## 7. Comparativo

| Opção | Android | Manobras | Distâncias | Licença | Custo/limites | Chave | Brasil | Complexidade | Moto |
|---|---|---|---|---|---|---|---|---|---|
| **GraphHopper Free** | HTTP/JSON | `sign` + texto pt_BR | m e ms/instrução | serviço fechado; dados OSM (ODbL); Free não comercial | 500 créd./dia ≈ 500 rotas; 5 pontos; sem heading | sim (usuário) | OSM | baixa | `car` (moto paga/sob consulta) |
| Google Nav SDK | SDK nativo | `Maneuver`, faixas, ícones | m, s, ~1 Hz | proprietária (termos GMP) | 1.000/mês grátis, depois US$ 25/1.000; billing | sim + cartão | sim, 2 rodas | alta | `TWO_WHEELER` |
| Valhalla FOSSGIS | HTTP/JSON | `type` 0–43 + texto pt-BR | km, s/manobra | livre; dados ODbL | 1 req/s, sem garantia, sem alto tráfego | não | OSM | baixa–média | `motorcycle` (BETA) |
| OSRM demo | HTTP/JSON | `type`/`modifier`, sem texto | m, s | livre; dados ODbL | 1 req/s, não comercial | não | OSM | média | não |
| openrouteservice | HTTP/JSON | tipos + texto | m, s | termos [N] | 2.000/dia | sim | OSM | baixa | não |
| Auto-hospedado | HTTP/JSON | conforme motor | conforme motor | livre | servidor próprio | — | OSM | alta | conforme motor |
| Waze | — | **nenhum acesso** | — | — | — | — | — | — | — |

## 8. Recomendação para o MVP

**Principal: GraphHopper Directions API, plano Free, chave do próprio usuário.**
`GET /api/1/route?point=lat,lon&point=lat,lon&profile=car&locale=pt_BR&points_encoded=false&instructions=true&elevation=false&key=…`
— sem `heading`/`ch.disable` (indisponíveis no Free). É a opção mais simples (um GET, texto em pt-BR, sem SDK), cabe no uso pessoal (≈ 500 rotas/dia) e já é o desenho da 0.3.0. Cuidados [I]: limitar recálculos (ex.: ≥ 30 s entre eles); tratar 400/401/429 com mensagens claras; sem `heading`, o recálculo pode começar com "faça um retorno" — testar em campo; mostrar "Rotas: GraphHopper · Dados © OpenStreetMap contributors (ODbL)".

**Plano B: Valhalla FOSSGIS** atrás da mesma interface `RouteProvider`: `costing=motorcycle`, `language=pt-BR`, `heading` no ponto de partida; URL configurável, User-Agent próprio, ≤ 1 req/s, só uso pessoal (servidor voluntário, sem garantia). **Plano C** (se precisar de qualidade Google): Navigation SDK + TBT feed, aceitando billing e complexidade maior.
