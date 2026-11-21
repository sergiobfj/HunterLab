# Handoff para o Codex — Interface do Hunter Lab (Agente B)

Este é o prompt usado para delegar a interface ao Codex CLI. Pode ser reutilizado numa execução separada
(`codex exec --skip-git-repo-check --sandbox workspace-write --cd <cópia-do-projeto> - < docs/CODEX_HANDOFF.md`).

---

Você é o engenheiro de interface (Jetpack Compose + Material 3) do **Hunter Lab**, app Android (Kotlin, minSdk 31,
targetSdk 37, Compose BOM 2026.11.01, sem DI) que mostra navegação curva a curva e alertas de radar no **Tripper Pod**
original de uma Royal Enfield Hunter 350. A lógica já existe ou está sendo feita por outro agente; **você só faz telas**.

## Objetivo
O app funciona, mas tem informação técnica demais. Transforme-o numa experiência diária simples, mantendo o
Laboratório BLE e os simuladores como área **avançada e separada** (não remova nenhuma ferramenta de diagnóstico).

## Direção visual
Premium e minimalista, com a personalidade da Hunter 350: fundo grafite (tokens em `ui/theme/Theme.kt`,
`HunterColors`), âmbar discreto só para ação principal e alertas, tipografia grande e legível (uso com luva/sol),
boa hierarquia, ícones consistentes (`ui/theme/HunterIcons.kt`), ótimo em celulares pequenos (360×640 dp).
Evite: cartões demais, textos longos, confirmações em tarefas do dia a dia, visual futurista exagerado.
Português do Brasil em todos os textos.

## Contrato (somente leitura — NÃO altere)
`app/src/main/java/dev/hunterlab/app/ui/contract/NavContracts.kt`: `HomeUi`, `NavUi`, `SettingsUi`, `TripperUi`,
`GpsUi`, `RadarUi`, `ManeuverUi`, `RouteSummaryUi`, `SearchUi`, `DestinationUi`, `NavActions`, `NavBindings`.
As telas recebem estado + `NavActions`; **não** acessam BLE, rede, GPS, preferências nem ViewModels da lógica.
Textos dinâmicos já chegam formatados.

## Arquivos
Pode criar/alterar:
- `ui/home/**`, `ui/navigation/**`, `ui/personalization/**`, `ui/settings/**`, `ui/preview/**` (novos)
- `ui/HunterLabRoot.kt` (nova estrutura), `ui/theme/**` (refinar tokens/ícones sem quebrar usos), `ui/components/**`
  (adicionar componentes; não quebrar os existentes)

Não altere: `ui/contract/**`, `ui/lab/**`, `ui/devices/**`, `ui/simulation/**`, `ui/playground/**`, `ui/tripper/**`
(reutilize), `ble/**`, `MainActivity.kt`, `HunterLabApplication.kt`, módulos `core`/`protocol`, Gradle, Manifest.

## Estrutura
`HunterLabRoot(ble: BleController, nav: NavBindings)` — nova assinatura. Barra inferior com 4 destinos:

1. **Início** — estado do Tripper (chip com ação Conectar/estado), campo de destino (busca → `search`; resultados e
   recentes → `selectDestination`; opção "Coordenadas" → `setDestinationFromText`, mostrando o erro devolvido),
   resumo da rota (`route`), botão principal **Iniciar navegação** (`startNavigation(false)`), ação secundária
   discreta **Simular trajeto** (`startNavigation(true)`). Sem chave de rotas (`providerConfigured=false`): aviso
   curto com atalho para Configurações. Se a navegação estiver ativa, atalho para a aba Navegação.
2. **Navegação** — tela "de painel": ícone grande da manobra (reutilize `ui/tripper/IconGlyphs` / `TripperDisplay`
   com `tripperPreview`), distância enorme, instrução e rua, próxima manobra pequena, tempo restante e chegada,
   pílulas de GPS e Tripper, faixa de radar (`radar.alertText`) com o aviso de cobertura limitada (`radar.disclaimer`,
   discreto) e a atribuição. Fluxo do Tripper inline conforme `tripper.status`: conectar → "Mostrar PIN no Tripper"
   (`requestPin`) → campo de PIN (`submitPin`) → "Sim, aceitou" (`confirmPinAccepted`, ativo só se
   `tripper.pinDelivered`) / "Está pedindo PIN" (`reportPinRejected`). Botão **Encerrar** (`stopNavigation`) com uma
   única confirmação. `interruptTripper` sempre acessível quando `tripper.canInterrupt`. Mostrar "Trajeto simulado"
   quando `simulated`. Sem navegação ativa: estado vazio com atalho para Início.
3. **Personalização** — modo diurno/noturno (`setDayNight`), rodapé do Tripper (`setBottomLine`), radar no Tripper
   (`setRadarOnTripper`), com prévia (`TripperDisplay`). Só recursos comprovados; nada experimental.
4. **Configurações** — chave da API de rotas (campo mascarado, salvar `setApiKey`, remover `clearApiKey`; mostrar só
   `apiKeyMasked`), radares (ligar/desligar, limiares 700/300 m com passos — `setRadarThresholds`), permissões
   (localização precisa, notificações no Android 13+, Bluetooth "Dispositivos por perto") com botões que pedem a
   permissão via `rememberLauncherForActivityResult` e depois chamam `refreshPermissions`, versão, atribuições e a
   seção **Avançado**: "Laboratório BLE e diagnóstico" (abre o `DevicesScreen` existente, que contém o laboratório),
   "Simulador de navegação", "Simulador de radares", "Playground" — reaproveite as telas e ViewModels existentes
   exatamente como o `HunterLabRoot` atual faz (incluindo `LabActiveStrip` visível em todas as abas quando houver
   sessão do laboratório).

Termos no primeiro uso: quando `tripper.needsTerms` e o usuário tocar em conectar, mostre um diálogo com os 5 itens
(moto parada; app oficial Royal Enfield fechado; li as instruções; entendo que o pareamento pode afetar o do app
oficial; sei onde está Interromper/Encerrar). Confirmar → `acceptTerms()` e depois `connectTripper()`.

## Regras obrigatórias
- PIN: só `remember` (nunca `rememberSaveable`), teclado `KeyboardType.Password`, limpar após enviar, nunca logar.
- Nenhuma ação automática de BLE: cada passo do Tripper é um toque do usuário.
- Não reduza proteções (Interromper/Encerrar sempre visíveis quando aplicável).
- Sem novas dependências. Material 3 do BOM atual; `HunterSlider` em `ui/components` contorna a API obsoleta do Slider.
- Acessibilidade: alvos ≥ 48 dp, `contentDescription` nos ícones de ação, contraste adequado.
- Previews: `ui/preview/SampleStates.kt` com estados de exemplo (navegando, PIN, sem chave, radar em alerta) e
  `@Preview` das telas, usando um `NavActions` vazio.

## Entrega
Ao terminar, escreva `docs/CODEX_REPORT.md` (curto): arquivos criados/alterados, decisões de UI, o que ficou pendente.
Se o sandbox permitir, rode `./gradlew :app:compileDebugKotlin` (JAVA_HOME = JDK 17+); se não, não insista — o
orquestrador compila e integra.
