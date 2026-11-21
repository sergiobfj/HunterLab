# Interface Hunter Lab — entrega do Agente B

## Arquivos

- Criados: `ui/home/HomeScreen.kt`, `ui/navigation/NavigationScreen.kt`,
  `ui/personalization/PersonalizationScreen.kt`, `ui/settings/SettingsScreen.kt`,
  `ui/components/DailyComponents.kt`, `ui/components/TripperConnection.kt` e
  `ui/preview/SampleStates.kt` (caminhos relativos a `app/src/main/java/dev/hunterlab/app/`).
- Alterados: `ui/HunterLabRoot.kt`, `ui/theme/Theme.kt` e `ui/theme/HunterIcons.kt`.
- Contratos, lógica, telas avançadas, MainActivity, dependências e Manifest preservados.

## Decisões de interface

- Quatro abas de uso diário; laboratório, simuladores e Playground em Configurações → Avançado,
  reutilizando as telas e os ViewModels originais. `LabActiveStrip` continua global.
- Grafite, tipografia maior, âmbar para ação principal e alertas, seções sem pilhas de cartões.
- Início com busca, recentes, coordenadas com erro retornado pela ação, resumo da rota e ações
  fixas de iniciar/simular. Calcular/atualizar rota é explícito. Aviso de chave ausente com atalho.
- Painel com manobra, distância, próxima instrução, chegada, estados de GPS/Tripper e radar,
  incluindo cobertura limitada e atribuição. Prévia reutiliza `TripperDisplay`.
- Termos com cinco itens obrigatórios na primeira conexão; todos os passos do Tripper exigem toque.
  PIN usa somente `remember`, teclado Password e limpeza ao enviar. Aceite depende de `pinDelivered`.
- Interromper e Encerrar ficam fixos em todas as abas quando aplicáveis; só Encerrar pede confirmação.
  Com teclado aberto, a barra de abas é recolhida e os controles de segurança ficam acima dele.
- Personalização contém somente aparência, rodapé e radar no Tripper. Chave mascarada,
  limiares em passos de 100 m (padrão 700/300 m) e permissões com launchers Android nas Configurações.
- Alvos de toque de pelo menos 48 dp, descrições nas ações só com ícone, títulos semânticos,
  conteúdo rolável e previews em 360 × 640 dp, incluindo fonte ampliada.

## Verificação e pendências

- Revisão estática dos callbacks, estados de PIN, limites de radar e espaços em branco concluída.
- Tentativa: `.\gradlew.bat :app:compileDebugKotlin --console=plain`, com o JAVA_HOME existente (JDK 25).
  O wrapper falhou antes de compilar: `AccessDeniedException: C:\.gradle`, ao criar o lock da distribuição.
  Nenhuma nova tentativa ou alteração do ambiente foi feita, conforme o handoff.
- Integração pelo orquestrador: a MainActivity ainda chama `HunterLabRoot(ble)`. Ela precisa fornecer
  os `NavBindings` reais e chamar `HunterLabRoot(ble, nav)`. A assinatura antiga não tem fallback com
  estado fictício. Sem esse ajuste, haverá erro de argumento ausente na compilação.
- Compilar após integrar a lógica; renderizar os previews e validar em emulador/celular 360 × 640 dp,
  fonte ampliada, teclado/PIN, permissões (API 31 e 33+) e fluxo físico do Tripper. Não houve
  validação visual renderizada ou execução em dispositivo neste sandbox.
