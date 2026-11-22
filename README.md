# Hunter Lab

Aplicativo Android experimental e independente para explorar o **Tripper Pod original da Royal Enfield Hunter 350 2027**. Kotlin, Jetpack Compose e Bluetooth LE, com foco em uma experiência simples de navegação e no entendimento do protocolo.

**Versão desta branch: 0.3.1 · base MVP: 0.3.0 · Base validada fisicamente informada: 0.2.1.** Projeto não oficial, sem afiliação ou endosso da Royal Enfield, Eicher Motors, Waze ou Google. Faça testes com a moto parada e em local seguro.

## Compatibilidade conhecida

| Item | Ambiente informado pelo mantenedor |
|---|---|
| Motocicleta | Royal Enfield Hunter 350, linha 2027 |
| Display | Tripper Pod original |
| Celular | Samsung SM-A155M, Android 16 |
| Requisito mínimo do aplicativo | Android 12 (API 31), Bluetooth LE |

Não há confirmação de compatibilidade com outras motos, celulares ou firmwares. Observações do WazeTripper na Meteor 350 são referências de protocolo, não validação do Hunter Lab nesse modelo.

## Funcionalidades implementadas

Conforme testes físicos relatados pelo mantenedor para a 0.2.1:

- Conexão com o Tripper real, handshake e autenticação com confirmação visual do usuário.
- Envio de bússola Norte/noturna e Leste/diurna.
- Restauração do relógio original e funcionamento preservado do aplicativo oficial Royal Enfield.

O código também inclui busca BLE, inspeção GATT, laboratório com comandos controlados, diagnóstico, catálogo de símbolos e simuladores de navegação e radares. **Os simuladores não são navegação GPS real e não transmitem instruções de rota ao Tripper.**

A transmissão existente exige autorização por sessão e ações manuais no Laboratório BLE. A autenticação depende de confirmação no display; uma escrita aceita pelo Android não confirma autenticação.

## Em desenvolvimento — 0.3.0 (implementada, ainda não validada na moto)

- Navegação real: destino por busca, coordenadas ou link `geo:`; rotas GraphHopper com a chave do próprio usuário;
  GPS com desvio e recálculo; telas de navegação no Tripper só em sessão de navegação autenticada.
- Radares fixos do OpenStreetMap (Overpass) com cache local e aviso de cobertura limitada.
- Interface em Início, Navegação, Personalização e Configurações; Laboratório BLE em Configurações → Avançado.
- Novas permissões: internet (só rotas e radares), localização durante a navegação, notificações e serviço em
  primeiro plano. Detalhes em [MVP_ARCHITECTURE](docs/MVP_ARCHITECTURE.md); validação em
  [PHYSICAL_TEST_PLAN](docs/PHYSICAL_TEST_PLAN.md).

Consulte o [roadmap](docs/ROADMAP.md). Não há capturas de tela públicas verificadas nesta entrega.

## Arquitetura

```text
protocol/  Kotlin puro: pacotes, CRC, comandos documentados, ícones e manobras
core/      Kotlin puro: estados, fila de escrita, sessão BLE, simuladores e composição do display
app/       Android + Compose: interface, permissões e adaptadores Android BLE
docs/      Arquitetura, protocolo, procedimentos e resumos de testes
```

A interface consome os contratos do core; alterações visuais devem preservar a comunicação já testada no hardware. Consulte a [arquitetura](docs/ARQUITETURA.md) e a [referência do protocolo](docs/PROTOCOLO.md).

## Compilação e testes

Use o Gradle Wrapper incluído. A configuração encontrada na auditoria usa Gradle **9.9.2**, Android Gradle Plugin **9.5.2**, JDK **25** (ambiente local e CI: Temurin 25), Android SDK Platform **37.2** e Build-Tools **36.0.0**. As versões declaradas estão em `gradle/libs.versions.toml`.

Instale o SDK e configure `ANDROID_HOME` ou crie `local.properties` com `sdk.dir` apontando para sua instalação. Esse arquivo é privado e não deve ser commitado.

```bash
./gradlew test
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

No Windows, use `./gradlew.bat`. APK de desenvolvimento: `app/build/outputs/apk/debug/app-debug.apk`.

## Instalação e assinatura

Instale manualmente o APK de desenvolvimento no celular de teste, pelo gerenciador de arquivos ou por `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

O debug usa `dev.hunterlab.app.debug` e a chave de debug da máquina que compilou. APKs gerados em máquinas diferentes podem ter certificados diferentes. Não há garantia de atualização sobre uma instalação anterior sem conferir pacote e certificado.

A distribuição de release depende de uma chave estável aprovada pelo mantenedor, armazenada fora do Git. Nenhuma chave definitiva deve ser criada ou publicada automaticamente. Veja o [plano de distribuição](docs/DISTRIBUICAO.md).

## Segurança e limitações

- **0.3.0 é um MVP sem validação física.** Apenas a base 0.2.1 tem os testes de Tripper relatados acima.
- As rotas usam **perfil de carro** da GraphHopper; não são otimizadas ou validadas para motocicletas e podem exigir conferir restrições legais de circulação.
- Radares fixos dependem de contribuição e cobertura OSM. A ausência de alerta não indica ausência de fiscalização.
- Origem/destino seguem por HTTPS para GraphHopper; tiles da região da rota para Overpass; buscas de endereço usam o Geocoder do aparelho. A chave do usuário, até cinco destinos recentes e o cache ficam no armazenamento privado. A chave não tem criptografia adicional; root ou depuração autorizada podem expô-la. Backup e transferência de dados do app são explicitamente excluídos.
- Serviço foreground, GPS com tela bloqueada e consumo de bateria ainda exigem testes no Samsung. Sem serviço foreground, a navegação e o link são encerrados.

- Não opere o celular pilotando. O protocolo é parcial e experimental.
- Conexão, autorização e autenticação são estados distintos. Não há reconexão automática no laboratório atual.
- PINs, chaves, tokens, dados de localização, endereços BLE completos e relatórios brutos não devem aparecer em commits ou issues públicas.
- A captura detalhada de respostas BLE pode conter dados sensíveis, inclusive PIN. Revise e anonimize qualquer evidência antes de compartilhar.
- Conserve logs e relatórios físicos privados em `field-logs/` ou `private-reports/`, ignorados pelo Git.

Consulte a [auditoria de publicação](docs/AUDITORIA_PUBLICACAO.md) e os [testes físicos](docs/TESTES_FISICOS.md). Os roteiros antigos documentam etapas anteriores; não substituem o relato atual do mantenedor.

## Tripper Playground 0.3.1

A branch de funcionalidade acrescenta catálogo por evidência, prévias, sequências simuladas e testes individuais de bússola com prioridade absoluta da navegação. A validação física da 0.3.1 ainda está pendente. [Recursos, matriz e checklist](docs/TRIPPER_PLAYGROUND.md) · [Pesquisa de tela circular independente](docs/FUTURE_ROUND_DISPLAY.md).

## Documentação técnica

- [Arquitetura](docs/ARQUITETURA.md)
- [Protocolo e níveis de confiança](docs/PROTOCOLO.md)
- [Procedimento de handshake](docs/PRIMEIRO_HANDSHAKE.md)
- [Investigação histórica da conexão/status 147](docs/DIAGNOSTICO_CONEXAO.md)
- [Testes físicos](docs/TESTES_FISICOS.md)
- [Roadmap](docs/ROADMAP.md)
- [Arquitetura do MVP de navegação](docs/MVP_ARCHITECTURE.md) · [Provedores de rota](docs/NAVIGATION_PROVIDERS.md) · [Dados de radar](docs/RADAR_DATA.md)
- [Plano de validação física](docs/PHYSICAL_TEST_PLAN.md) · [Processo de release](docs/RELEASE_PROCESS.md) · [Changelog](CHANGELOG.md)

## Licenciamento e créditos

**A licença do código próprio do Hunter Lab ainda será escolhida pelo mantenedor.** A disponibilidade pública do repositório não substitui essa escolha; esta entrega não aplica automaticamente uma licença ao projeto.

O [WazeTripper, de jnsdias](https://github.com/jnsdias/wazetripper), fornece a referência pública do protocolo e vetores de teste, sob [Apache-2.0](https://github.com/jnsdias/wazetripper/blob/main/LICENSE). O código local se apresenta como implementação Kotlin independente. Não são incluídos APKs, recursos ou código proprietário do Waze ou da Royal Enfield.

As dependências e as limitações da revisão de origem estão em [créditos de terceiros](THIRD_PARTY_NOTICES.md). Marcas e nomes de produtos pertencem aos respectivos titulares.
