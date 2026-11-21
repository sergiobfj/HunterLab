# Hunter Lab — entrega de interface

## Branch e isolamento

- Branch: `feat/ui-hunter-dashboard`, worktree irmão `HunterLab-ui`.
- Base: `8af0688` (main pública 0.2.1). Nenhuma integração na main nesta entrega.
- `protocol/`, `core/`, transporte BLE, MainActivity, Manifest, versões Gradle e assinatura não foram alterados.
- Não foram copiados módulos do MVP ou alterações locais do Claude. A única entrada recebida da engenharia é a cópia **inalterada** do contrato declarado estável `NavContracts.kt` (SHA-256 `533f668b0f6d6b3eb2aeab0cea888cc541d29ba728c3ba996ff56cb055cde8b8`).

## Interface

Quatro áreas: Início, Navegação, Personalização e Configurações. Os rótulos compactos da barra são Início, Navegar, Visual e Ajustes; os nomes completos permanecem nos títulos/descrições acessíveis.

- Grafite, âmbar, texto quente e contraste AA verificado nos tokens de texto.
- Hierarquia curta, uma ação principal, números grandes e espaço para texto ampliado.
- Telas recebem estados e `NavActions`; não acessam transporte, rede, GPS ou preferências diretamente.
- `HunterLabRoot(ble, nav: NavBindings? = null)` preserva a chamada da base publicada e aceita a injeção dos bindings reais do MVP sem alterar esta interface.
- Sem bindings, a base não simula navegação real: oferece conexão pelo fluxo existente, prévia local de aparência e acesso aos simuladores explicitamente identificados.
- O Laboratório BLE, diagnóstico, simuladores e Playground foram mantidos sem alteração, em Configurações → Avançado. O atalho Conectar do Início abre o fluxo existente na base publicada.
- A faixa do laboratório continua global. Sessões de navegação fornecidas pelos bindings têm Interromper global; Encerrar navegação é fixo e pede uma confirmação.
- PIN e chave usam campos seguros com estado criado por `remember`, sem restauração em estado salvo, sem logs e com limpeza antes do envio.
- Manobra e ETA antigos ficam ocultos durante recálculo, fora da rota e erro. Dados fictícios existem apenas nas prévias.

## Arquivos

- Raiz: `ui/HunterLabRoot.kt`.
- Design: `ui/theme/Theme.kt`, `HunterIcons.kt`, `ui/components/ProductComponents.kt`.
- Telas: `ui/home/`, `ui/navigation/`, `ui/personalization/`, `ui/settings/`.
- Contrato estável: `ui/contract/NavContracts.kt`, sem modificações.
- Prévias: `ui/preview/SampleStates.kt` e [galeria renderizada](ui/README.md).
- Testes: `UiPresentationTest`, `UiAccessibilityTest`, `UiBoundaryTest`.

## CI

Commit `5c77d6d`: action Android atualizada para v4/Node 24 com SHA fixado; modo do wrapper corrigido para `100755`. Pushes de branches `feat/ui-*` também executam CI.

[Execução das correções](https://github.com/sergiobfj/HunterLab/actions/runs/44956751827): **sucesso e zero anotações/avisos**. Essa execução contém as correções de CI; não representa validação remota das telas ainda locais.

Não houve alteração das regras de assinatura, secrets, publicação de release ou identificadores do aplicativo.

## Validação

- Compilação Kotlin: aprovada.
- Suíte completa: **268/268 testes aprovados**, zero erros e zero testes ignorados após a correção autorizada do guard.
- Os **oito testes novos de UI passaram**, incluindo isolamento dos dados fictícios, descarte visual de manobra/ETA antigos e contraste AA.
- APKs debug e release sem assinatura: compilados. **Lint debug e release sem apontamentos.** Na revisão final, a suíte completa, o build debug e o Lint debug foram executados novamente e concluíram com sucesso.
- 12 prévias Compose renderizadas por Layoutlib/AGP: Início, navegação, radar, PIN, personalização, configurações, fonte 150%, tela ampla, vazio, base publicada, loading e erro.
- A ferramenta de screenshot usou um init script e fixtures temporários, guardados na auditoria local. Nenhuma dependência de screenshot ou flag experimental foi acrescentada ao build versionado.
- Nenhum emulador ou aparelho físico foi usado. As imagens são renders de Compose com dados fictícios, não testes BLE ou navegação GPS real.
- Gitleaks 8.30.1 sobre o conteúdo staged da UI: nenhum segredo encontrado. `git diff --cached --check`: sem erros.

## Correção final autorizada do guard

O `SafetyGuardTest` passou a reconhecer somente os três caminhos auditados que usam `NavActions`: `contract/NavContracts.kt`, `home/LocalAppearanceActions.kt` e `navigation/TripperControls.kt`. As demais barreiras de transmissão e inicialização permanecem intactas.

O `UiBoundaryTest` preserva as verificações de transporte, rede, GPS, persistência, segredos e estados fictícios. Foram incluídos contraexemplos que comprovam a detecção de acessos proibidos, sem ampliar a lista de componentes autorizados. A suíte continua com 268 testes.

A branch alternativa permanece independente. A integração 0.3.0 usa a interface conectada aos provedores reais entregue pelo Claude; esta implementação e suas prévias são preservadas para comparação e eventual polimento posterior.

## Integração controlada posterior

1. Alinhar a adaptação do guard sem sobrescrever as proteções novas do MVP.
2. Conferir o contrato final contra o snapshot e fornecer os `NavBindings` reais na inicialização mantida pela engenharia.
3. Combinar os módulos em branch de integração, resolver conflitos de UI deliberadamente e repetir testes, build e Lint.
4. Verificar permissões, teclado, TalkBack, navegação entre áreas e comunicação com o Tripper no Samsung SM-A155M antes de integrar na main.
