# Tripper Playground 0.3.1

Ferramenta estacionária em **Personalização → Tripper Playground**; permanece também em Configurações → Avançado. A base funcional é a 0.3.0. A interface reutiliza a base Graphite Grey revisada em `feat/ui-graphite-grey`, sem segunda identidade visual ou cópia do transporte BLE.

## Funcionalidades e categorias

- Bússola: oito direções, prévia diurna/noturna e evidência separada por combinação.
- Navegação: códigos catalogados, incluindo setas principais e pequenas; todos somente em prévia nesta entrega.
- Indicadores: códigos especiais, prévia do relógio original e overlay de ligação. Desenhos aproximados, sem promessa de pixels idênticos.
- Efeitos: editor e quatro exemplos locais — bússola girando, alternância, manobras e símbolos conhecidos. Adicionar/remover, reordenar, duração de 2–10 s por etapa, até oito etapas e 45 s no total. Reproduzir, pausar, interromper e reiniciar; não toca automaticamente nem entra em loop.
- Histórico: até 50 resultados privados, persistidos localmente. Data, símbolos, modo, duração, contadores, motivo do término e observações estruturadas. Copiar relatório sanitizado ou apagar o histórico.

**Não são GIFs ou vídeos.** O protocolo conhecido aceita telas/comandos documentados; não oferece framebuffer, imagens livres, scripts, bytes ou hexadecimal arbitrário. A pesquisa de outra tela está separada em [FUTURE_ROUND_DISPLAY.md](FUTURE_ROUND_DISPLAY.md).

## Evidência e elegibilidade

`Confidence` no protocolo refere-se à referência externa (Meteor), não à Hunter. A UI discrimina: observado na Hunter, confirmado somente na referência, provável e não verificado. O relato físico conhecido confirma **Norte/noturna e Leste/diurna** na Hunter; não comprova as outras combinações. Contadores de escrita ou respostas não elevam essa classificação. Resultado visual é declarado pelo usuário, não inferido de callback BLE.

Os **71 códigos de ícones** continuam catalogados. Acrescentam-se oito opções de bússola e duas prévias especiais, totalizando 81 entradas selecionáveis. O relógio nativo é uma prévia sem comando de restauração. Campo de distância, rodapé e geometria são exemplos quando se trata de prévia, nunca substitutos dos dados reais de navegação.

### Transmissão real nesta entrega

Somente `TripperCommands.compass(direction, period)` por `startCompassTest`, com direção tipada N/NE/E/SE/S/SW/W/NW. A prévia de todos os outros símbolos é permitida; transmissão experimental de manobras, ligação, recálculo, rota iniciada e sequências permanece bloqueada. A navegação 0.3.0 mantém sua própria allowlist e seus comandos, sem alteração dos builders.

O fluxo de conexão usa os comandos de infraestrutura já existentes: mostrar PIN, envio manual de PIN, ajuste de horário e keepalive. Eles não viram botões de edição de pacotes. Não há tentativa automática de PIN, reconexão automática, pareamento de sistema, escrita OTA ou alteração de firmware.

## Segurança e propriedade da sessão

1. Modo físico nasce **desativado**, inclusive após reiniciar o processo. Abrir, selecionar, trocar modo ou reproduzir a prévia não inicia transmissão.
2. O usuário confirma cinco condições, incluindo moto parada e app oficial fechado. Velocidade GPS não é usada como prova de estacionamento. O consentimento não é persistido e expira em dois minutos.
3. Busca explícita reutiliza o scanner existente, sem conectar. É necessário um único anúncio `RE_DISP` recente; zero, mais de um, anúncio antigo ou não conectável impedem conexão. A descoberta GATT e a autenticação continuam nas barreiras do link validado. Nome BLE não é identidade criptográfica.
4. Conectar abre o perfil e o servidor pelo caminho existente; o handshake só começa em **Mostrar PIN no Tripper**. PIN efêmero, mascarado, apagado do campo ao enviar; confirmação visual habilitada somente após a entrega ao controlador.
5. Exibir exige sessão `PLAYGROUND`, autorização, autenticação confirmada, nenhuma navegação ativa e símbolo elegível. Duração: **5, 10 ou 15 segundos**. Cadência conservada em **2 segundos**.
6. Navegação/recálculo têm prioridade. Uma barreira síncrona no link revoga a sessão experimental antes de iniciar navegação, bloqueia nova conexão e impede enfileirar/bombear novas escritas experimentais. O laboratório não pode comandar sessão de outro dono.
7. **Interromper** permanece fora da rolagem. Cancela timers, novos envios e a fila pelo mecanismo existente, invalida o experimento e respeita o fechamento do link. Um pacote já entregue ao rádio não pode ser desfeito.
8. Sair da tela, colocar o app em segundo plano, perder conexão, esgotar a duração/consentimento ou iniciar navegação retorna ao estado seguro. O Playground não usa serviço foreground para manter testes experimentais com a tela bloqueada.

Não se promete relógio imediato: pode depender do watchdog, do encerramento espontâneo do link ou de ciclo de ignição. Não enviar outros comandos para tentar forçar essa recuperação.

## Histórico sem segredos

Observações são opções estruturadas (correspondeu à prévia, símbolo diferente, sem mudança, visor vazio, instabilidade), não texto livre. Recuperação do relógio também é escolhida pelo usuário. Isso evita guardar PIN, tokens, endereços, nomes de pessoas, coordenadas ou dumps por meio de comentários. O relatório não inclui logs BLE, bytes de autenticação ou identificação do dispositivo. Dados simulados têm contadores físicos **não aplicáveis**, sem números inventados.

Cada experimento físico registra deltas dos contadores do link no intervalo do teste. Isso mede callbacks entregues ao controlador e respostas recebidas, não confirmação do efeito no visor. O registro permanece após desconexão e não é apagado ao abrir uma nova sessão. O armazenamento privado usa as exclusões de backup/transferência já configuradas; não há telemetria ou envio automático de relatórios.

## Arquitetura e limites das mudanças

`PlaygroundCatalog`/`PlaygroundSelection` são puros; `PreviewSequence`/`PreviewPlayer` não recebem porta BLE. `PlaygroundController` reutiliza `TripperLink` e o agendador existente. A máquina de estados, CRC, serialização de escritas, GATT e builders continuam compartilhados. As mudanças no link limitam-se ao novo dono de sessão, interlock de prioridade e restrições do Playground. `NavigationController` apenas sinaliza início/fim da prioridade, sem mudar GPS, roteamento ou radar.

Na camada Android, `PlaygroundViewModel` recebe estados reais e transmite intenções ao controlador; `PlaygroundContent` recebe estado/ações e pode ser renderizado sem hardware. A persistência está em `AppPreferences`, mantendo um só ponto de SharedPreferences. O guard de segurança reconhece somente o novo arquivo de controlador, sem autorizar componentes desconhecidos.

## Primeiro teste físico — somente parado

- [ ] Instalar o APK local mantendo pacote/certificado anteriores; conferir versão 0.3.1. Não usar artefato CI como atualização sem comparar assinatura.
- [ ] Fechar o aplicativo oficial, estacionar em local seguro e encerrar navegação, simuladores e outras sessões BLE.
- [ ] Abrir o Playground: confirmar modo físico desligado e ausência de escrita ao selecionar símbolos ou reproduzir sequência local.
- [ ] Selecionar Norte/noturna; ativar modo físico confirmando todas as condições.
- [ ] Buscar um único Tripper logo após ligar a ignição. Se houver ambiguidade, não contornar o bloqueio.
- [ ] Conectar; pedir PIN, digitá-lo manualmente e confirmar visualmente a aceitação. Não interpretar escrita aceita como autenticação automática.
- [ ] Exibir por **5 segundos**, registrar o que o visor mostrou e aguardar o encerramento; conferir contadores sem concluir que comprovam o desenho.
- [ ] Conferir e registrar retorno do relógio; repetir Leste/diurna como regressão do comportamento já observado.
- [ ] Testar Interromper, saída da tela e app em segundo plano. Confirmar ausência de novos envios; conferir recuperação e aplicativo oficial depois.
- [ ] Somente depois experimentar individualmente as outras direções/modos, sempre parado, com novo consentimento e registro visual.
- [ ] Confirmar que navegação ativa ou sessão alheia bloqueia o modo físico. Sequências reais devem continuar indisponíveis.

Sem testes na Hunter, a 0.3.1 está pronta para **validação**, não validada no hardware. Não instalar ou operar animações durante pilotagem.

## Validação local

448 testes: protocol 115, core 310 e app 23, sem falhas, erros ou ignorados. Preservados os 421 testes da 0.3.0; adicionados 23 do núcleo Playground, três de fronteira Android e um de contraste do design system. Testes cobrem catálogo, confiança, seleção, prévias, sequências, limites, autenticação, ambiguidade, proprietário, prioridade, interrupção, desconexão, callbacks atrasados, falha de gravação e sanitização. O teste de segurança original permanece ativo e reconhece apenas o novo controlador conhecido.

Build debug aprovado; Lint debug sem achados após remover o aviso de chamada vazia no encerramento do ViewModel. Há três avisos Kotlin herdados da base: duas APIs antigas de máscara de senha e um `!!` desnecessário. Não são achados de Lint nem foram ocultados. Sete renders Layoutlib sem mudanças de dependência no build versionado; flags/fixtures experimentais ficaram na auditoria privada.

A matriz exercita todos os 81 recursos em prévia JVM, mas não compara pixels do hardware. A suíte Android não é instrumentada: compilação, guards de fonte, Lint e renders não provam comportamento real de permissões/GATT/TalkBack. Nenhum teste físico foi feito nesta entrega. O resultado remoto deve ser conferido na execução CI da branch publicada.

## Prévias de interface

Renders reais de Compose/Layoutlib com estados **fictícios**, sem aparelho, GATT ou GPS. Mostram o componente do Playground; não incluem a raiz Android com as quatro abas. Nenhum secret ou dispositivo real foi usado.

[Catálogo](playground/previews/catalogo.png) · [Modo físico sintético](playground/previews/fisico.png) · [PIN vazio](playground/previews/pin.png) · [Efeitos](playground/previews/efeitos.png) · [Histórico fictício](playground/previews/historico.png) · [320 dp / fonte 150%](playground/previews/fonte150.png) · [840 dp](playground/previews/amplo.png).

As ações de exibir/interromper ficam fora da rolagem. O restante do conteúdo é rolável. A renderização não valida toque, TalkBack, comportamento com teclado real, BLE ou resposta física do visor.

## Matriz de recursos

As linhas abaixo vêm do catálogo Kotlin exercitado nos testes. Android significa prévia construída nos testes JVM e build aprovado; não significa imagem comparada ao hardware. Em Hunter, D = diurno e N = noturno. `Referência` remete ao [protocolo WazeTripper](https://github.com/jnsdias/wazetripper/blob/main/docs/PROTOCOL.md) e aos níveis já mantidos em `protocol/TripperIcons.kt`; `Hunter` remete aos relatos de [TESTES_FISICOS.md](TESTES_FISICOS.md). Sem ensaio novo nesta entrega.

<!-- MATRIX_START -->
| Identificador | Nome | Tipo | Comando | Builder | Confiança externa | Evidência | Android | Hunter (diurno / noturno) | Transmissão |
|---|---|---|---|---|---|---|---|---|---|
| compass-N | Bússola N | Bússola | `10 11 41` | `TripperCommands.compass` | Confirmado | Referência §4 + relato T10 | Prévia JVM | Não testado / Observado | Bússola individual, 5–15 s |
| compass-NE | Bússola NE | Bússola | `10 11 41` | `TripperCommands.compass` | Confirmado | Referência §4 + relato T10 | Prévia JVM | Não testado / Não testado | Bússola individual, 5–15 s |
| compass-E | Bússola L | Bússola | `10 11 41` | `TripperCommands.compass` | Confirmado | Referência §4 + relato T10 | Prévia JVM | Observado / Não testado | Bússola individual, 5–15 s |
| compass-SE | Bússola SE | Bússola | `10 11 41` | `TripperCommands.compass` | Confirmado | Referência §4 + relato T10 | Prévia JVM | Não testado / Não testado | Bússola individual, 5–15 s |
| compass-S | Bússola S | Bússola | `10 11 41` | `TripperCommands.compass` | Confirmado | Referência §4 + relato T10 | Prévia JVM | Não testado / Não testado | Bússola individual, 5–15 s |
| compass-SW | Bússola SO | Bússola | `10 11 41` | `TripperCommands.compass` | Confirmado | Referência §4 + relato T10 | Prévia JVM | Não testado / Não testado | Bússola individual, 5–15 s |
| compass-W | Bússola O | Bússola | `10 11 41` | `TripperCommands.compass` | Confirmado | Referência §4 + relato T10 | Prévia JVM | Não testado / Não testado | Bússola individual, 5–15 s |
| compass-NW | Bússola NO | Bússola | `10 11 41` | `TripperCommands.compass` | Confirmado | Referência §4 + relato T10 | Prévia JVM | Não testado / Não testado | Bússola individual, 5–15 s |
| icon-0 (0x00) | Destino | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-1 (0x01) | Destino à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-2 (0x02) | Destino à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-3 (0x03) | Convergir à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-4 (0x04) | Convergir à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-5 (0x05) | Bifurcação à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-6 (0x06) | Bifurcação à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-7 (0x07) | Saída de via à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-8 (0x08) | Saída de via à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-9 (0x09) | Seguir em frente | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-10 (0x0A) | Rotatória horária (base) | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-11 (0x0B) | Rotatória horária, saída 1 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-12 (0x0C) | Rotatória horária, saída 2 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-13 (0x0D) | Rotatória horária, saída 3 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-14 (0x0E) | Rotatória horária, saída 4 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-15 (0x0F) | Rotatória horária, saída 5 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-16 (0x10) | Rotatória horária, saída 6 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-17 (0x11) | Rotatória horária, saída 7 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-18 (0x12) | Rotatória horária, saída 8 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-19 (0x13) | Rotatória horária, saída 9 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-20 (0x14) | Virar à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-21 (0x15) | Virar à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-22 (0x16) | Curva acentuada à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-23 (0x17) | Curva acentuada à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-24 (0x18) | Curva leve à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-25 (0x19) | Curva leve à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-26 (0x1A) | Retorno (horário) | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-27 (0x1B) | Convergir | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-28 (0x1C) | Recalculando rota | Indicadores | `10 11` | `TripperCommands.recalculating` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-29 (0x1D) | Acesso à via pela direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-30 (0x1E) | Acesso à via pela esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-31 (0x1F) | Acesso leve pela direita | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-32 (0x20) | Acesso leve pela esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-33 (0x21) | Acesso acentuado pela direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-34 (0x22) | Acesso acentuado pela esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-35 (0x23) | Desconhecido 0x23 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-36 (0x24) | Desconhecido 0x24 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-37 (0x25) | Desconhecido 0x25 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-38 (0x26) | Desconhecido 0x26 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-39 (0x27) | Sair/manter à direita (suave) | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-40 (0x28) | Sair/manter à esquerda (suave) | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-41 (0x29) | Desconhecido 0x29 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-42 (0x2A) | Desconhecido 0x2A | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-43 (0x2B) | Manter faixa, destaque à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-44 (0x2C) | Manter faixa, destaque à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-45 (0x2D) | Saída acentuada à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-46 (0x2E) | Saída acentuada à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-47 (0x2F) | Saída: manter/acentuada à direita | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-48 (0x30) | Saída: manter/acentuada à esquerda | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-49 (0x31) | Rotatória anti-horária (base) | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-50 (0x32) | Rotatória, saída 1 | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-51 (0x33) | Rotatória, saída 2 | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-52 (0x34) | Rotatória, saída 3 | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-53 (0x35) | Rotatória, saída 4 | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-54 (0x36) | Rotatória, saída 5 | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-55 (0x37) | Rotatória, saída 6 | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-56 (0x38) | Rotatória, saída 7 | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-57 (0x39) | Rotatória, saída 8 | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-58 (0x3A) | Rotatória, saída 9 | Navegação | `10 11` | `TripperCommands.navigation` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-59 (0x3B) | Desconhecido 0x3B | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-60 (0x3C) | Partida | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-61 (0x3D) | Retorno (anti-horário) | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-62 (0x3E) | Balsa (barco) | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-63 (0x3F) | Balsa (trem) | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-64 (0x40) | Desconhecido 0x40 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-65 (0x41) | Tela de bússola (seletor) | Navegação | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-66 (0x42) | Sem dados móveis | Indicadores | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-67 (0x43) | Desconhecido 0x43 | Navegação | `10 11` | `TripperCommands.navigation` | Não verificado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-68 (0x44) | Bateria fraca | Indicadores | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-69 (0x45) | Rota iniciada | Indicadores | `10 11` | `TripperCommands.routeStarted` | Provável | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| icon-255 (0xFF) | Vazio | Indicadores | `10 11` | `TripperCommands.navigation` | Confirmado | TripperIcons / referência §6 | Prévia JVM | Não testado / Não testado | Prévia apenas |
| native-clock | Relógio original | Indicadores | `sem envio` | `TripperScreen.NativeClock (sem pacote)` | Confirmado | Relato Hunter / referência §4 | Prévia JVM | Retorno observado; sem comando | Prévia apenas |
| call-overlay | Indicador de ligação | Indicadores | `40 05` | `TripperCommands.callIcon` | Confirmado | Referência §4.1 | Prévia JVM | Não testado / Não testado | Prévia apenas |
<!-- MATRIX_END -->

### Infraestrutura, comandos não selecionáveis e limites

| Recurso | Comando / builder | Evidência | Android / Hunter | Elegibilidade no Playground |
|---|---|---|---|---|
| Mostrar PIN | `21 01` / `handshakeShowPin` | Referência e relato Hunter | Fluxo existente / observado | Somente toque explícito de autenticação |
| PIN manual | `20` / `pin` | Referência e relato Hunter | Builder e limites testados / observado | Somente campo efêmero; não exportar conteúdo |
| Retomar | `21 00` / `handshakeResume` | Referência; retomada Hunter pendente | Suportado pelo laboratório | Não exposto pelo Playground |
| Keepalive | `03` / `pingFirmware` | Referência e sessão Hunter | Mantido pelo link | Infraestrutura interna após autorização |
| Ajustar relógio | `50` / `setTime` | Referência / verificação específica pendente | Parte do handshake existente | Infraestrutura; sem editor livre |
| Tela ociosa | `10 11 3C` / `idleScreen` | Referência, sem restauração garantida | Builder existente / Hunter não verificada | Bloqueada; não é retorno ao relógio |
| GIF / vídeo / imagem livre | Sem comando conhecido | Incompatível com o protocolo documentado | Não implementado no Tripper | Nunca enviar |
| Bytes, script, firmware ou OTA | Nenhuma porta exposta | Não documentado/não autorizado | Não implementado | Sempre bloqueado |
