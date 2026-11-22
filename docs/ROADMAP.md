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
- CI Android existente: testes, Lint, build e artefatos; distribuição estável depende da decisão de assinatura.


## Identidade Hunter e capacidade gráfica do Pod

- [x] Marca original H em movimento, vetores adaptativo/monocromático e prévias no design grafite/lima, em branch isolada.
- [x] [Pesquisa passiva do Pod](TRIPPER_GRAPHICS_RESEARCH.md): comandos conhecidos, pista FOTA JDCP, distinção de Dash e limites de evidência.
- [ ] Integrar os assets após conferência das frentes Graphite Grey/Playground; validar recorte do launcher e splash no Samsung sem iniciar sessão BLE.
- [ ] Obter documentação pública legível dos componentes e confirmação documental da revisão 2027. GIFs/vídeos permanecem **sem suporte demonstrado**, sem intervenção em firmware/hardware.
