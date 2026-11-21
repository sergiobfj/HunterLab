# Arquitetura do MVP de navegação (0.3.0)

Complementa `ARQUITETURA.md` (módulos e Laboratório BLE). Regra mantida: **lógica no `core` (JVM puro, testada)**,
Android fino no `app`, protocolo isolado em `protocol`.

## Fluxo

```
Destino (Geocoder | "lat, lon" | geo:) ──► RouteProvider (GraphHopper, chave do usuário) ──► Route
                                                                                            │
GPS (AndroidLocationSource, 1 Hz, só durante a navegação) ──► NavigationEngine ◄────────────┘
   ou RouteDriveSimulator (trajeto simulado)                   │  RouteTracker: progresso, manobra, desvio
                                                               │  efeitos: RequestRoute / CancelRequest
                                                               ▼
                                     NavigationSnapshot ──► TripperDisplayController ──► ScreenStreamPort
RadarRepository (Overpass, cache) ──► RouteRadarSession ──► RadarPhase ──┘   (ScreenComposer)   = TripperLink
                                                                                     (sessão NAVIGATION)
```

## Componentes

| Peça | Onde | Papel |
|---|---|---|
| `GeoPoint`, `Polyline`, `CoordinateParser` | `core/geo` | Contrato geográfico compartilhado (projeção na rota, distâncias, leitura de coordenadas/links). |
| `RouteProvider`, `GraphHopperRouteProvider`, `GraphHopperParser`, `HttpGet` | `core/route` | Rota e manobras tipadas (`Maneuver` do protocolo). A rede é uma porta (`HttpGet`). |
| `NavigationEngine`, `RouteTracker`, `RouteDriveSimulator` | `core/navigation` | Máquina de estados pura com tempo injetado: IDLE → ROUTING → NAVIGATING ⇄ OFF_ROUTE/REROUTING → ARRIVED/FAILED. |
| `RouteRadarSession`, `RouteRadarMatcher`, `OverpassParser`, `TileKey`, `RadarUpdatePolicy` | `core/radar` | Radares reais projetados na rota; reutiliza o `RadarEngine` 1D (700/300 m). |
| `TripperDisplayController` | `core/display` | Decide iniciar/atualizar/encerrar a transmissão a partir de navegação + radar. |
| `ScreenStreamPort` (implementado pelo `TripperLink`) | `core/link` | Telas de navegação e "recalculando", reenvio a cada 2 s, **só** em sessão `NAVIGATION` autenticada. |
| `NavigationController` | `app/nav` | Orquestra tudo na thread principal; implementa `NavActions` e publica `HomeUi`/`NavUi`/`SettingsUi`. |
| `NavigationService` | `app/nav` | FGS `location|connectedDevice` + wake lock parcial enquanto há navegação ou sessão do Tripper. |
| `HttpsClient`, `RadarRepository`, `AppPreferences` | `app/net`, `app/radar`, `app/settings` | Únicos pontos de rede, arquivo e preferências (verificado pelo `SafetyGuardTest`). |
| Telas | `app/ui/home`, `navigation`, `personalization`, `settings` | Feitas pelo Codex sobre `ui/contract/NavContracts.kt`. |

## Decisões (0.3.0)

| Decisão | Motivo |
|---|---|
| GraphHopper Free com a chave do usuário; `profile=car` | Instruções com códigos de manobra e distâncias; sem custo para o app. O plano gratuito não tem perfil de moto nem `heading` (ver `NAVIGATION_PROVIDERS.md`). Plano B: Valhalla (`costing=motorcycle`). |
| Busca pelo `Geocoder` do Android; fallback coordenadas/`geo:` | Sem chave e sem custo; aparelhos com Google Play têm o serviço. |
| Radares do OSM via Overpass, tiles de 0,2°, validade 14 dias, 1 download/tile/h | Base aberta (ODbL) e política de uso do Overpass; nunca consulta a cada fix. Sem dados → sem alertas. |
| Telas de navegação liberadas na lista branca | Comandos documentados ("Confirmado" na Meteor); só pela `ScreenStreamPort`, nunca no laboratório. |
| Sessão `NAVIGATION` sem o limite de 5 min do laboratório (teto 6 h) e sem interrupção ao sair da tela | Navegar com tela bloqueada. O controle passa ao FGS, cuja notificação sempre tem **Encerrar**. |
| Termos (5 itens) aceitos **uma vez**; depois um toque por conexão | Evita confirmações a cada viagem sem remover a autorização explícita por sessão. O laboratório mantém o checklist por sessão. |
| PIN digitado a cada conexão; sem reconexão automática | `HANDSHAKE_RESUME` ainda não testado na Hunter (T11). Decidir após teste. |
| Rota inicial reutiliza a do resumo (< 3 min, < 150 m) | Economiza créditos da API. |
| Wake lock parcial durante o serviço | O reenvio de telas precisa acontecer a cada 2 s com a tela apagada (o Tripper derruba o link sem pacotes ~5 s). |

## Threads
Tudo do `NavigationEngine`, `TripperLink` e `RouteRadarSession` roda na **thread principal**. Rede e disco rodam num
executor único (`hunterlab-io`) e devolvem o resultado com `Handler.post`. O motor descarta respostas de pedidos
antigos e fixes fora de ordem.

## Limitações conhecidas
- Sem estimativa de posição sem GPS (a tela congela nos últimos valores).
- Recálculo no plano gratuito sem `heading`: a nova rota pode começar com um retorno.
- Radares com `direction` mal mapeada podem ser descartados; vias paralelas a < 25 m no mesmo sentido não são separadas.
- A camada Android (controlador, serviço, GPS, rede) só foi compilada e analisada pelo Lint; o comportamento real
  depende de `PHYSICAL_TEST_PLAN.md`.
