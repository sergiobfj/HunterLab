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
