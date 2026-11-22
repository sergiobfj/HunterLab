# Roadmap

Prioridades do projeto: (1) navegação curva a curva, (2) alertas de radar, (3) modos visuais e
personalização, (4) experimentos com os símbolos existentes.

## 0.1.0 — Base, descoberta e simulação ✅

- [x] Biblioteca de protocolo, core puro, app com quatro abas, BLE somente leitura.
- [x] Testes físicos somente leitura na Hunter 350 2027: `RE_DISP`, perfil GATT igual ao documentado, app oficial intacto.

## 0.2.0 / 0.2.1 — Laboratório BLE ✅ código · ✅ teste físico

- [x] `TripperLink` (estados, autorização por sessão, handshake manual, PIN, keepalive, bússola, interrupção).
- [x] 0.2.1: conexão só com anúncio recente (corrigiu o status 147 nas sessões observadas).
- [x] **Na Hunter:** PIN aceito visualmente e bússola exibida em duas sessões; relógio voltou sozinho; app oficial ok.

## 0.2.2 — Consolidação ✅

- [x] Relatório mantém os contadores da última sessão após a desconexão (antes mostrava 0 escritas/0 respostas).
- [x] Histórico não é inundado por envios periódicos (contados, não listados um a um; capacidade 400).
- [x] Resultados físicos registrados (`TESTES_FISICOS.md`); CI e processo de release preparados (`RELEASE_PROCESS.md`).

## 0.3.0 — Navegação real (MVP) ⏳ validação física

- [x] Contratos: `RouteProvider`, fonte de localização, `NavigationEngine`, `TripperDisplayController`, `ScreenStreamPort`.
- [x] Rotas GraphHopper (chave do usuário), rastreamento com histerese, desvio e recálculo com limites.
- [x] Telas de navegação e "recalculando" liberadas **só** em sessões de navegação (lista branca + porta própria).
- [x] Serviço em primeiro plano (localização + dispositivo conectado), notificação com Encerrar, trajeto simulado.
- [x] Interface diária (Codex): Início, Navegação, Personalização, Configurações; laboratório em Avançado.
- [ ] Roteiros N00–N07 de `PHYSICAL_TEST_PLAN.md` (telas de navegação ainda não testadas na moto).

## 0.3.1 — Tripper Playground ⏳ validação estacionária

- [x] Catálogo por evidência, prévias Compose e oito bússolas tipadas; 71 códigos não são 71 confirmações na Hunter.
- [x] Editor de sequências somente simulado, com pausa, ordem e limites; efeitos físicos bloqueados.
- [x] Modo físico desativado por padrão; sessão própria, handshake existente, interlock síncrono com navegação e teste de 5–15 s.
- [x] Histórico local estruturado, exportação sanitizada e preservação após desconexão.
- [x] Base Graphite Grey compartilhada, sem refazer o tema no Playground.
- [x] [Pesquisa Hunter Display](FUTURE_ROUND_DISPLAY.md): ESP32-S3/LVGL e limites de GIF/vídeo; sem compra ou firmware.
- [ ] Checklist de [TRIPPER_PLAYGROUND.md](TRIPPER_PLAYGROUND.md) na Hunter parada; verificar interrupção, relógio e app oficial.
- [ ] Decidir próximos símbolos físicos somente após evidência individual; não aumentar cadência para animação.

## 0.4.0 — Radares reais ⏳

- [x] Radares fixos do OpenStreetMap (Overpass), cache por tiles, casamento com a rota e sentido, alertas 700/300 m.
- [ ] Roteiro N08 com radar mapeado conhecido.

## 1.0.0 — Estabilização

- [ ] N00–N11 aprovados; release assinado com chave estável; documentação curta de uso.
- [ ] Decidir com base em teste (T11): `HANDSHAKE_RESUME` para não digitar o PIN a cada viagem e reconexão automática.

## Decisões

Aprovadas em 21/11/2026:

| Decisão | Resolução |
|---|---|
| Início do handshake | **Exclusivamente manual** (um toque) |
| Reconhecimento do dispositivo | **Não persistir** entre execuções (RESUME só na mesma execução) |
| CCCD / notificações | **Desativados** por enquanto |
| Reconexão automática | **Não implementar** nesta versão |
| Testes físicos | **Preparar T07–T09** para execução manual; T10 (bússola) e T11 ficam para depois |

Tomadas na 0.3.0 (documentadas em `MVP_ARCHITECTURE.md`): persistência mínima de preferências (sem PIN, sem
endereço do Tripper); rede só para rotas e radares; navegação usa confirmação única por sessão depois dos termos aceitos
uma vez (o checklist de 5 itens continua no laboratório). Pendente de teste: retomada sem PIN (T11).

## Dívida técnica conhecida

- `Slider` do Material3 1.5 usa a sobrecarga obsoleta (isolada em `HunterSlider`).
- `connectGatt(Context, Boolean, Callback, Int)` obsoleta na API 37; a alternativa só existe na API 37+.
- Sem testes instrumentados/UI (não há aparelho/emulador neste ambiente); a camada Android do laboratório só foi
  compilada e analisada pelo Lint — o comportamento real depende do teste físico.
- CI Android ativa com testes, Lint, APK e checksum; release estável ainda depende da assinatura.


## Consolidação 0.3.1 e identidade

- [x] Reunir Playground, base Graphite Grey e pesquisa gráfica do Pod, preservando o código funcional da 0.3.1.
- [ ] Escolher e aprovar nova marca entre cavalo, roadster e híbrida. Assets novos permanecem fora da main até aprovação.
- [ ] Validar a 0.3.1 no Samsung e na Hunter estacionada seguindo o procedimento existente; sem testes físicos nesta consolidação.

### Branch de limites e voz — 22/11/2026

- Evidências OSM/Pardal PE, limite condicionado/conflitante sem número e fonte por radar.
- GraphHopper `max_speed` separado do limite do equipamento; voz opcional sem comandos BLE novos.
- Pendente: revisão das prévias, integração controlada, GraphHopper com chave válida e validação Android/TTS no Samsung.
