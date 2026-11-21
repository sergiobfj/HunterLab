# Roadmap

Prioridades do projeto: (1) navegação curva a curva, (2) alertas de radar, (3) modos visuais e
personalização, (4) experimentos com os símbolos existentes.

## 0.1.0 — Base, descoberta e simulação ✅

- [x] Biblioteca de protocolo, core puro, app com quatro abas, BLE somente leitura.
- [x] Testes físicos somente leitura na Hunter 350 2027: `RE_DISP`, perfil GATT igual ao documentado, app oficial intacto.

## 0.2.0 — Etapa 02A: infraestrutura da primeira transmissão (esta versão) ✅ código · ⏳ teste físico

- [x] `TripperLink` no core: máquina de estados, autorização por sessão, handshake documentado, PIN manual,
      confirmação humana, retomada condicionada, fila serializada, keepalive, teste de bússola, interrupção, limpeza.
- [x] Adaptador Android: cliente GATT (só `01FF0101-…`) e servidor GATT local, sem anúncio, sem CCCD.
- [x] Laboratório BLE na aba Dispositivos, com Interromper sempre visível; detalhes GATT recolhidos.
- [x] Guardas automatizadas: sem escrita fora do adaptador, sem transmissão na inicialização, sem persistência.
- [ ] **T07–T11** na Hunter (roteiro em `PRIMEIRO_HANDSHAKE.md`) — depende de autorização sua.

## 0.2.1 — Diagnóstico do status 147 (esta versão) ⏳ validação no celular

- [x] Conexão do laboratório só com anúncio recente e conectável, usando o dispositivo do próprio anúncio (como a
      referência). Opção para reproduzir a 0.2.0 no diagnóstico.
- [x] Variantes de diagnóstico do servidor local (antes/depois da conexão/desligado), sem mudar o padrão.
- [x] Linhas `diag:` idênticas na inspeção e no laboratório; resumo por tentativa no relatório.
- [ ] **Reteste D1–D6** sem transmissão (`DIAGNOSTICO_CONEXAO.md`). O 147 só será dado como resolvido depois dele.

## 0.2.x — Ajustes pós-teste físico

- [ ] Registrar o formato das respostas do Pod (com captura detalhada, em sessão dedicada).
- [ ] Se o handshake falhar na Hunter: avaliar, com autorização, a ativação do CCCD como a referência faz.
- [ ] Atualizar `HunterValidation` do que for comprovado.

## 0.3.0 — Navegação real (só depois da bússola validada)

- [ ] Novo `NavigationProvider` com fonte externa, sem modificar APKs de terceiros (avaliar termos de uso).
- [ ] Liberar `NavigationFrame` na lista branca do laboratório, com reenvio a cada 2 s e prioridade do árbitro.

## 0.4.0 — Radares reais

- [ ] Fonte local de radares, sem servidor; localização com permissão e justificativa próprias.

## 0.5.0 — Visual e símbolos

- [ ] Sequências de símbolos no hardware, só depois de revisar `HardwareExecutionPolicy`.

## Decisões

Aprovadas em 21/11/2026:

| Decisão | Resolução |
|---|---|
| Início do handshake | **Exclusivamente manual** (um toque) |
| Reconhecimento do dispositivo | **Não persistir** entre execuções (RESUME só na mesma execução) |
| CCCD / notificações | **Desativados** por enquanto |
| Reconexão automática | **Não implementar** nesta versão |
| Testes físicos | **Preparar T07–T09** para execução manual; T10 (bússola) e T11 ficam para depois |

Pendentes: autorização para executar T07–T09 (decisão do usuário no local); depois, autorização própria para T10.

## Dívida técnica conhecida

- `Slider` do Material3 1.5 usa a sobrecarga obsoleta (isolada em `HunterSlider`).
- `connectGatt(Context, Boolean, Callback, Int)` obsoleta na API 37; a alternativa só existe na API 37+.
- Sem testes instrumentados/UI (não há aparelho/emulador neste ambiente); a camada Android do laboratório só foi
  compilada e analisada pelo Lint — o comportamento real depende do teste físico.
- Sem CI.
