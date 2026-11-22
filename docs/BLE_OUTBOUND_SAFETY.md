# Barreira de saída BLE — revisão passiva de 22/11/2026

Base: main 0.3.1 `78f2e8a`. Branch isolada `feat/mvp-ble-ota-guard`.
Sem conexão com a moto, transmissão, sondagem, firmware, download de APK oficial ou acesso a backend.

## Evidência e incerteza

A [reconstrução estática OpenTripper, revisão a2f9d12](https://github.com/spandan-kumar/OpenTripper/blob/a2f9d128fc9d6c6654c4c3d72605fc49ca435107/docs/POD_OTA_PROTOCOL.md)
associa **o opcode no byte `[0]`** à atualização pela característica UART também usada por navegação:

| Opcode | Interpretação atribuída à análise de terceiro |
|---|---|
| `02` | Entrada no fluxo de atualização |
| `04` | Parâmetros de blocos/segmentos |
| `05` | Inicialização da transferência |
| `06` | Cabeçalho de bloco |
| `07` | Dados de segmento |
| `08` | Digest do bloco |
| `09` | Verificação |
| `0A` | Finalização/aborto |

Confiança: **forte indício documental de terceiro**, não especificação oficial nem confirmação física na Hunter 2027.
Há quadros variáveis nessa análise. Bloquear somente o serviço OTAP separado não protege uma UART compartilhada.
Não reproduzimos o desmonte do aplicativo nem copiamos código sem licença.

`03` permanece inalterado. O [WazeTripper](https://github.com/jnsdias/wazetripper/blob/7c4235d5c4de5a1a60aa74ea6ece0c06e9ffab07/docs/PROTOCOL.md)
o usa como keepalive; a outra análise o interpreta como consulta de versão.
Essas interpretações podem coexistir; não basta evidência para mudar o comportamento validado.
Não passamos a interpretar respostas ou iniciar atualizações a partir delas.

## Proteção implementada

A main já tinha lista branca por tipo e pacote decodificado, construtores restritos no core e validação na retirada da fila.
Os opcodes acima já eram rejeitados ali. O adaptador Android, porém, conferia apenas tamanho, serviço e característica
antes da escrita. Adicionamos a mesma lista branca no **último ponto de saída**.

1. O adaptador mantém a checagem de tamanho de 20 bytes.
2. Faz uma cópia dos bytes, valida tamanho e CRC pela implementação existente e consulta `CommandWhitelist`.
3. Em caso de recusa retorna `WriteStart.Refused(-5)`; nenhum conteúdo é logado pelo novo código.
4. Ambos os caminhos Android (API 33+ e API 31–32) escrevem exclusivamente a cópia aprovada.

Não há denylist baseada em ocorrência de bytes. Os valores `02`/`04–0A` podem existir legitimamente em `[2]`/`[7]`
(símbolos), distâncias, horário ou `[18..19]` (CRC). O significado depende da posição estrutural.
Não foram criados builders de atualização, comandos novos, mudanças no protocolo, frequência, autorização, PIN,
propriedade de sessão, watchdog, navegação ou Playground. A guarda final complementa essas proteções; não as substitui.

## Regressões

`OutboundPacketSafetyTest` exercita:

- Oito opcodes sensíveis, seis preenchimentos com CRC correto e todos os tipos de comando declarados, inclusive tipos falsamente rotulados.
- Todos os outros opcodes desconhecidos de um byte, em vez de liberar qualquer coisa ausente de uma denylist.
- Comprimentos inválidos, inclusive 41 e 247 bytes, e corrupção de cada um dos 20 bytes de um quadro permitido.
- `03` canônico preservado e variante malformada recusada.
- Valores sensíveis em ícones, horário, distância e CRC real: todos continuam aceitos nos campos permitidos.
- Todos os builders já autorizados, bússolas dia/noite e fluxos normais dos três proprietários de sessão em rádio falso.

`SafetyGuardTest` passa a exigir que os dois caminhos de escrita no único adaptador usem a cópia validada.
As verificações existentes de isolamento permanecem intactas. É cobertura JVM + inspeção de fontes Android;
não afirma execução dos dois níveis de API em aparelho nem validação física.

Resultado local: **459 testes**, zero falhas/erros (protocol 115, core 320, app 24); Lint debug zero achados;
build debug aprovado. Os 448 testes da base continuam presentes, mais dez testes core e um de isolamento Android.

APK local: `build/distributions/HunterLab-0.3.1-ble-guard-debug-local.apk` na pasta principal (ignorado pelo Git).
SHA-256: `324d3555d50e0b45e21810c082e15dbb1646ea917f196fd16f761ca5a9b48d67`.
`dev.hunterlab.app.debug`, versão `0.3.1-debug`, code 6. Assinatura verificada pelo `apksigner`;
certificado SHA-256 `cd7734c98417fe084fb5d9c149c98d897db6b271cc3c32f999de4b4d855033cf`, igual ao APK
local consolidado 0.3.1. O certificado da instalação no Samsung não foi extraído nesta revisão;
compatibilidade com o APK local anterior foi confirmada, atualização no aparelho permanece pendente.
Não desinstalar ou limpar dados para contornar divergência de assinatura. APK de CI pode usar outra chave debug.

## Pesquisa do Claude

Os dois documentos de `b2c1713` foram incorporados por conteúdo, preservando a branch/commit original e a autoria histórica,
sem cherry-pick integral ou reescrita. O relatório é pesquisa documental, não laudo do hardware do usuário.
Frases categóricas sobre firmware devem ser lidas com essa limitação: não há firmware do Pod analisado por nós,
nem prova de impossibilidade de um comando gráfico desconhecido. Continua válida a conclusão operacional:
**não existe evidência suficiente de imagens arbitrárias, GIFs ou vídeo no Tripper Pod original**.
FOTA não é API de gráficos e não autoriza experimentos ativos.
