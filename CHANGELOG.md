# Changelog

## 0.3.1 — Tripper Playground (aguarda validação física)

- Catálogo com evidência separada por referência e Hunter, prévias e editor limitado de sequências simuladas.
- Teste físico individual de bússola de 5–15 s pelo link existente, com consentimento por sessão e prioridade da navegação.
- Histórico local estruturado, relatório sanitizado e nenhuma persistência de PIN ou endereço Bluetooth.
- Base Graphite Grey compartilhada; pesquisa de tela ESP32-S3 independente, sem firmware novo ou compra.

Mudanças relevantes do Hunter Lab. Versões no formato `X.Y.Z` (igual ao `versionName`).

## [0.3.0] — em preparação (não validada na moto)

**Navegação real (MVP)**
- Destino por busca de endereço (Geocoder do Android), coordenadas digitadas ou link `geo:` compartilhado.
- Rotas pela GraphHopper com a chave do próprio usuário; resumo da rota antes de iniciar.
- GPS com rastreamento na rota, manobra seguinte com histerese, desvio confirmado e recálculo com limites.
- Serviço em primeiro plano (tela bloqueada) com notificação e botão **Encerrar**; trajeto simulado para teste parado.
- Tripper: telas de navegação e "recalculando" (comandos documentados), só em sessão de navegação, com PIN digitado
  pelo usuário. Sem o Tripper, a navegação continua no celular.

**Radares fixos (prévia)**
- Radares do OpenStreetMap (Overpass), cache local por região, casamento com a rota e o sentido, alertas a 700/300 m
  (ajustáveis), aviso de cobertura limitada. Sem dados, nenhum alerta.

**Interface** (feita pelo Codex): Início, Navegação, Personalização e Configurações; Laboratório BLE e simuladores
em Configurações → Avançado.

**Permissões novas**: internet (só rotas e radares), localização durante a navegação, notificações, serviço em
primeiro plano e wake lock.

## [0.2.2] — consolidada (incluída na 0.3.0)

- Relatório do laboratório mantém escritas e respostas da última sessão após a desconexão (antes: 0 e 0).
- Envios periódicos repetidos não inundam mais o histórico (contados; capacidade 400 linhas).
- Testes físicos registrados: PIN aceito e bússola exibida em duas sessões; relógio voltou sozinho; app oficial ok.

## [0.2.1] — 2026-11-21

- Conexão do laboratório só com anúncio recente e conectável, usando o dispositivo do próprio anúncio
  (diagnóstico do status 147 da 0.2.0).
- Variantes de diagnóstico do servidor GATT local e linhas `diag:` no relatório, sem mudar o padrão.

## [0.2.0] — 2026-11-21

- Infraestrutura da primeira transmissão: `TripperLink` (handshake, PIN manual, confirmação humana, fila,
  keepalive, interrupção) e adaptador Android com cliente e servidor GATT.
- Laboratório BLE com autorização por sessão e botão Interromper sempre visível.

## [0.1.0] — 2026-11-21

- Primeira versão: biblioteca de protocolo, core puro, app com quatro abas, simuladores e BLE somente leitura.
- Testes físicos somente leitura na Hunter 350 2027 (descoberta do `RE_DISP` e perfil GATT).
