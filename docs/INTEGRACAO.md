# Integração entre engenharia e interface

## Responsabilidades

Engenharia mantém `protocol/`, `core/`, BLE, provedores, GPS, radares e regras de atualização do Tripper. Interface mantém Compose, design system, navegação entre telas, acessibilidade, documentação pública e workflows. Alterações necessárias em contratos devem ser combinadas antes de editar os mesmos arquivos.

## Primeira publicação

A base física 0.2.1 foi isolada em `codex/publication` usando as fontes originais verificadas por SHA-256 na auditoria. O trabalho de MVP 0.2.2 na pasta original não faz parte dessa primeira publicação.

O integrador confere conteúdo, segurança, testes, build e Lint, cria o commit e publica em `main` com push normal. Para incorporar o MVP, a engenharia deve encerrar suas escritas e indicar quais funcionalidades e contratos estão prontos. Usar branches separadas e incluir somente arquivos revisados de cada entrega. Não incluir arquivos parcialmente escritos, fazer force push ou reescrever autoria.

## Contratos de interface observados na auditoria

`NavigationProvider` expõe `info`, `snapshots: StateFlow<NavigationSnapshot>`, `start()` e `stop()`. O snapshot observado inclui status, manobras, distâncias, tempo restante, identificação da origem e indicador `simulated`.

Na versão observada desse contrato não há destino, ETA absoluto, estado GPS ou resumo da última utilização. A interface deve representar ausência de dados e aguardar contratos reais para esses campos. Não tratar rascunho do simulador em estado `IDLE` como rota ativa e não apresentar simulação como navegação real.

Antes de integrar telas de navegação, conferir novamente os contratos finais do MVP; este documento registra a inspeção inicial, não congela a API da engenharia.

## Próxima entrega de interface

- Início: estado real do Tripper e ação principal; destino/resumo apenas quando disponíveis.
- Navegação: manobra e distâncias do core, estados vazios/loading/erro, origem simulada claramente identificada.
- Personalização: recursos compatíveis e experimentos identificados, sem enviar comandos novos.
- Configurações: permissões, versão, sobre, acesso ao Laboratório BLE e diagnóstico completo.

Usar grafite, preto suave, âmbar e branco suave, hierarquia de texto clara, alvos de toque de pelo menos 48 dp e layouts adaptáveis. Preservar autorização por sessão e as ações de interrupção. Verificar larguras compactas/expandidas e escala de fonte antes de afirmar validação visual.

## Entrega da engenharia — 0.3.0 (21/11/2026, sessão Claude Code)

**Estado:** escritas encerradas na pasta original (alterações **não commitadas**, sobre o commit `8af0688`).
Verificado do zero nesta pasta: `./gradlew test :app:lintDebug :app:assembleDebug` → BUILD SUCCESSFUL; 416 testes
(protocol 115, core 287, app 14), 0 falhas; Lint 0 achados. **Não validado na moto.**

**Contratos prontos (estáveis):**
- UI: `app/.../ui/contract/NavContracts.kt` (`HomeUi`, `NavUi`, `SettingsUi`, `TripperUi`, `NavActions`, `NavBindings`).
- Core: `core/geo` (GeoPoint/Polyline), `core/route` (RouteProvider/HttpGet), `core/navigation` (NavigationEngine),
  `core/radar` (RouteRadarSession), `core/link/ScreenStreamPort` + `SessionPurpose`, `core/display/TripperDisplayController`.

**Interface já implementada** (pelo Codex CLI desta sessão, sobre esses contratos): `ui/home`, `ui/navigation`,
`ui/personalization`, `ui/settings`, `ui/preview`, `ui/components/DailyComponents.kt`, `TripperConnection.kt`,
`HunterLabRoot.kt` e tema. A "próxima entrega de interface" acima já está coberta; refinamentos devem partir destes arquivos.

**Fora do escopo da engenharia (não toquei):** `.github/`, `DISTRIBUICAO.md`, `AUDITORIA_PUBLICACAO.md`,
`THIRD_PARTY_NOTICES.md`, `.gitignore`, `.gitattributes`. Workflows alternativos do agente de release ficaram
fora do projeto para não duplicar `android-*.yml`.

**Atenção ao integrar:** a 0.3.0 adiciona permissões (INTERNET, localização, FGS, notificações, wake lock) e libera
telas de navegação na lista branca (só em sessão `NAVIGATION`). Revisar `docs/MVP_ARCHITECTURE.md` e
`SafetyGuardTest` antes de publicar. A dependência nova é só `kotlinx-serialization-json` 1.9.0 (API de árvore).

## Consolidação pelo integrador

A entrega foi copiada com conferência de hashes para `HunterLab-mvp-030` / `feat/mvp-030`, preservando o original. A UI funcional da engenharia é a escolhida; não integrar automaticamente a UI alternativa. Decisões, ajustes e limites da evidência: [revisão da integração 0.3.0](INTEGRACAO_030.md).

## Frente Graphite Grey — base visual compartilhada

Branch `feat/ui-graphite-grey`, baseada na main `a5fad26`, worktree própria. Primeira entrega coesa: paleta grafite/verde-oliva solicitada, tokens de espaços e shapes 10/14/18 dp, tipografia, chips sem âmbar de seleção, botões primários e controles de segurança que se empilham com fonte ampliada. Cores de sinal possuem variantes de texto com contraste testado; o tom fraco é reservado a estados desabilitados/decorativos. Sem dynamic color por padrão.

Validação da base: 422 testes (421 anteriores + contraste dos papéis usados), Lint debug sem achados, build debug aprovado. Não há validação em aparelho. Esta entrega é a base do design system, não a conclusão do polimento de todas as telas. A frente Playground 0.3.1 pode integrar este commit pronto; não deve copiar arquivos em edição nem refazer o tema.

## Playground 0.3.1 — integração isolada

Worktree `HunterLab-playground-031`, branch `feat/tripper-playground-031`, base main `a5fad26`. Integração normal da base visual pronta `4b7169b`, sem copiar arquivos em edição. Nenhum arquivo em `protocol/` ou adaptadores Android BLE foi alterado. O novo dono `PLAYGROUND` e a barreira de prioridade usam o link existente; o controlador de navegação só sinaliza início/fim dessa prioridade. Todos os demais símbolos/efeitos continuam simulados; só bússolas individuais são elegíveis no Playground físico.

A main permanece na 0.3.0 até integração controlada. Procedimentos, inventário e fronteiras: `TRIPPER_PLAYGROUND.md`; pesquisa independente: `FUTURE_ROUND_DISPLAY.md`. Não considerar esta entrega validada na moto. O restante do polimento visual do aplicativo continua na frente Graphite Grey; não substituí-lo pelo Playground.


## Consolidação 0.3.1 e marca pendente — 21/11/2026

Worktree `HunterLab-consolidation-031`, branch `feat/mvp-031-consolidation`, criada de `306a3cf` após conferir main, worktrees e diffs. A base Graphite `4b7169b` já é ancestral do Playground. Integração da pesquisa pelo commit isolado `8d7841a`; não integrar `6c97737` nem aplicar o H em movimento, que não foi aprovado.

Código funcional, protocolo, adaptadores BLE, permissões, assinatura, versão 0.3.1/code 6 e proteções do Playground preservados byte a byte da branch funcional. Marca nova em branch de arte separada, com três conceitos para aprovação. Documentação consolidada em `CONSOLIDACAO_031.md`.


## Estudos de marca A/B/C — aprovação pendente

Worktree `HunterLab-brand-exploration`, branch `feat/ui-hunter-brand-concepts`, sobre main consolidada `78f2e8a`. Três caminhos distintos: A Cavalo de rua (recomendação artística), B Roadster e C Elo mecânico. Pranchas, fontes SVG e aplicações em `docs/brand/README.md`.

A está ligada aos recursos launcher/splash **somente nesta branch de estudos**; B/C são candidatos usados em prévias. A main mantém o ícone anterior. Não mesclar esta branch nem selecionar identidade definitiva sem aprovação explícita do usuário. Pacote, assinatura, permissões, protocolo, core, BLE, navegação, GPS e radar preservados. Nenhum teste físico.
