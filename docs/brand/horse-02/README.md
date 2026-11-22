# Hunter Lab — marca oficial 02, Cavalo gráfico

A proposta 02 foi aprovada pelo usuário em 22/11/2026. Implementação isolada em
`feat/brand-horse-02`, sobre `4dd8408` da branch de radares. A main não foi alterada.
Aprovação da marca não equivale à validação física de navegação ou radares na Hunter.

## Origem e recursos

Fonte: `9cbb667`, `feat/ui-horse-art-references`, pasta `02-graphic`.
Foi reaproveitado somente o desenho aprovado de corpo inteiro galopando, sem redesenhar
cabeça, patas, crina ou cauda. A1, A2 e as propostas 01/03 permanecem nas branches de origem.
Nenhuma imagem de referência do WhatsApp ou marca Royal Enfield foi incorporada.

- `symbol.svg`: símbolo editável da proposta original.
- `launcher-foreground.svg`: composição editável de 108 × 108, incluindo o detalhe verde-lima.
- `icon-monochrome.svg`: símbolo monocromático editável.
- `android-vector-source/`: vetores Android originais, preservados como fonte.
- `icon48-graphite.png`: prévia real de 48 × 48 pixels, anterior à instalação.
- `provenance.json`: origem e hashes dos recursos.
- `resource-validation.json`: medição do enquadramento na área segura adaptativa.

Os dois PNGs transparentes de execução são renderizações determinísticas de 864 × 864
em `drawable-nodpi`, derivadas dos mesmos caminhos vetoriais. Essa representação evita
dois avisos `VectorPath` do Lint por caminhos de 9.267 caracteres; não há supressão de avisos
nem simplificação artística. Adaptive icon e round icon usam foreground/background separados;
o themed icon usa a silhueta branca sem o detalhe de cor. O splash existente referencia o mesmo foreground.
O raio opaco máximo medido é 32,5312 dp, dentro do círculo seguro de raio 33 dp.

## Cores e Samsung

Fundo do recurso `#101416`, cavalo `#E8ECEE`, detalhe `#B7CF62`.
A configuração original de cores dos ícones do Samsung foi restaurada e preservada.
O usuário pediu explicitamente para mantê-la: no tema atual, o launcher aplica cavalo preto
sobre fundo cinza. Não exigir mudança da paleta do sistema para exibir a marca.

As imagens em `device/` são recortes de capturas reais do SM-A155M, Android 16.
Recortes de launcher preservam os pixels originais; a variante de 48 px é uma redução
identificada, não uma captura nativa nessa resolução. A captura colorida foi obtida
na verificação temporária da opção de ícones, antes da restauração da configuração original;
não representa a configuração final escolhida pelo usuário. Vídeos e telas integrais do
telefone ficam privados, sem identificadores do aparelho no repositório público.

## Validação local e instalação

- 513 testes JVM: 115 protocol, 365 core e 33 app; nenhuma falha ou teste ignorado.
- Android Lint: zero achados. Build debug aprovado.
- Recursos adaptativos, monocromáticos e splash inspecionados no Samsung; sem corte de patas ou cauda.
- Abertura a frio e as quatro áreas verificadas no APK final, sem crash observado.
- Um teste instrumentado no Samsung passou com fixtures de sentido desconhecido/rua paralela.
  Isso não comprova cobertura física de radares ou TTS auditivo.
- Certificados do APK extraído do aparelho e do APK novo comparados e idênticos:
  SHA-256 `cd7734c98417fe084fb5d9c149c98d897db6b271cc3c32f999de4b4d855033cf`.
- Atualização Android retornou `Success`, sem desinstalação, downgrade ou limpeza de dados.
- APK instalado extraído novamente e idêntico ao APK local; arquivo de preferências idêntico
  imediatamente antes/depois da atualização. Isso verifica as preferências, não cada arquivo privado.
- Pacote `dev.hunterlab.app.debug`, versão `0.3.1-debug`, código 6, assinatura debug local preservados.

APK local: `build/distributions/HunterLab-0.3.1-horse-02-debug-local.apk`.
SHA-256: `e5124c0fef3c94a6631bfa3feb2366288492d45090dab417c22f942ca3c0c442`.
O APK do CI tem outra chave debug; não presumir compatibilidade de atualização.

Nenhum Kotlin/Java, contrato, permissão, applicationId, transporte BLE, proteção OTA,
GPS, radar, voz, Playground ou workflow foi modificado. Não houve conexão com o Tripper,
transmissão experimental, OTA ou teste na moto. A confirmação auditiva humana do TTS
continua pendente e não é resolvida pela aprovação visual da marca.

A publicação desta branch não autoriza merge automático na main nem release estável.
