# Capturas reais — Samsung / radares

Samsung SM-A155M, Android 16, usuário principal. Captura por PixelCopy da janela real do aplicativo,
sem montar ou gerar a interface fora do aparelho e sem incluir notificações de outros aplicativos.
Não são imagens de emulador ou Layoutlib. **Os estados da tela são fixtures sintéticas**, sem GPS ou BLE.

| Prefixo | Largura | Fonte |
|---|---:|---:|
| window-normal | 450 dp | 100% |
| window-narrow100 | 320 dp | 100% |
| window-dense150 | 320 dp | 150% |

Cada prefixo possui known (100 km/h), missing (ausente), conflict (divergente) e conditional (condicionado).
Os três últimos exibem traço, nunca um número. Distância, placa e estado do limite são verificados antes
do botão Encerrar, sem rolar a tela. Informações secundárias permanecem disponíveis ao rolar.

[Limite conhecido, fonte ampliada](window-dense150-known.png) · [Conflito, fonte ampliada](window-dense150-conflict.png)

`summary.json` registra configurações, assinatura, resultados e hashes. `instrumentation-results.txt` contém saídas
JUnit finais sem serial, UID do aparelho, caminhos de instalação ou segredos. Os APKs extraídos, dumps de UI
e logcat integrais ficam no registro local privado. Nenhum dado de contato ou notificação foi publicado.

A prova de TTS é do componente real de voz no Samsung com fixture, não uma rota GPS de ponta a ponta.
Os callbacks técnicos não substituem confirmação auditiva humana, que está pendente nesta revisão.
Ver o relatório principal [RADAR_SPEED_LIMITS.md](../../RADAR_SPEED_LIMITS.md).
