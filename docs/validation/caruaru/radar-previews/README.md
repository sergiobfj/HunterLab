# Prévias de radares

Layoutlib / Compose Preview Renderer, geradas em 22/11/2026. Dados sintéticos, sem dispositivo ou BLE.
Os componentes reproduzíveis estão em `app/src/main/java/dev/hunterlab/app/ui/preview/RadarPreviews.kt`.

- KnownRadar: limite cadastrado 50 km/h, 360 dp.
- MissingRadar: limite ausente, sem número.
- ConflictRadar: fontes divergentes, sem número.
- ConditionalRadar: condição não resolvida, sem número.
- LargeTypeRadar: 100 km/h, 320 dp e fonte a 150%.
- NavigationRadar: manobra, radar e limite do trecho separados; Tripper desconectado.
- comparison.png: prancha das seis telas.

A placa mantém numeral estável para caber no círculo; textos adjacentes ampliam e quebram linha.
Contraste de texto principal/secundário e numerais revisto. O vermelho é borda de sinalização,
não texto de informação. Desconhecido usa borda neutra.

Renderizador experimental usado somente na revisão local, sem dependência/configuração experimental
publicada. Houve avisos de aquecimento lento do Layoutlib; as seis imagens finais foram abertas e inspecionadas.
Não equivale a teste de interface no Samsung, motor TTS ou navegação com tela bloqueada.
