# Plano de validação física — navegação e radares (0.3.x → 1.0.0)

Resultados de handshake e bússola (já executados) estão em `TESTES_FISICOS.md`. Este plano cobre o que falta para
declarar o MVP comprovado **na moto**. Registre cada resultado separando a camada onde foi observado:

| Camada | O que prova | Quem executa |
|---|---|---|
| Software (testes JVM) | Lógica de rota, rastreamento, radar e transmissão com relógio virtual | CI / `./gradlew test` |
| Android real, sem Tripper | Permissões, GPS, serviço em primeiro plano, rede, interface | Você, em casa |
| Android + Tripper, moto parada | Telas reais no display, link longo, interrupção, retorno ao relógio | Você, moto parada |
| Moto em movimento | Precisão em uso real | Você, com o celular **já configurado e fixo** |

**Segurança:** nunca opere o celular pilotando. Configure destino, conexão e PIN com a moto parada. Em N04–N06, de
preferência com alguém acompanhando. Não mexa na parte elétrica da moto.

## Pré-requisitos
- APK da versão em teste instalado (ver `RELEASE_PROCESS.md` para trocar o debug pelo release assinado).
- Chave da GraphHopper cadastrada em *Configurações* (conta gratuita criada por você).
- Permissões: localização precisa, notificações, Dispositivos por perto.
- App oficial Royal Enfield com parada forçada durante os testes com o Tripper.

## Roteiros

| ID | Cenário | Como | Aprovado se |
|---|---|---|---|
| N00 | Instalação e permissões | Instalar, abrir, conceder permissões, cadastrar a chave | Sem travamentos; Configurações mostra tudo concedido |
| N01 | Rota real sem Tripper (parado) | Escolher destino a 2–5 km → resumo da rota | Distância/tempo coerentes com outro app de mapas; erro claro sem internet |
| N02 | **Trajeto simulado com o Tripper** (moto parada, ignição ligada) | Conectar → PIN → *Simular trajeto* | Ícones e distâncias do Tripper iguais à prévia do app; recalculando aparece; chegada mostra o destino e o relógio volta depois |
| N03 | Tela bloqueada (simulado, 10 min) | Iniciar N02 e bloquear o celular | Tripper continua atualizando; notificação com **Encerrar** funciona |
| N04 | Queda do Tripper | Durante N02, desligar a ignição por 10 s | App continua navegando e mostra "Tripper desconectado"; reconexão manual funciona após religar |
| N05 | Rota real curta (em movimento) | Rota urbana conhecida, 2–5 km, com 3+ manobras e uma rotatória | Manobra certa antes de cada cruzamento; distância cai de forma coerente; sem alternância entre manobras |
| N06 | Desvio e recálculo (em movimento) | Sair da rota de propósito numa rua segura | "Recalculando" no Tripper e nova rota em até ~15 s; sem recálculos repetidos |
| N07 | GPS ruim / sem internet | Túnel ou área coberta; desligar dados móveis no meio da rota | Sem travar; mantém a rota atual; mensagem clara; recupera ao voltar o sinal |
| N08 | Radar conhecido | Rota que passa por radar fixo **mapeado no OpenStreetMap** | Alerta aos ~700/300 m na via certa; sem alerta em via paralela; aviso de cobertura limitada visível |
| N09 | Encerrar e restaurar | Encerrar navegação | Tripper volta ao relógio (sozinho ou após ciclar a ignição — registrar qual); app oficial conecta depois |
| N10 | Bateria | 30 min navegando com tela apagada | Registrar % gasto do celular (referência, sem meta fixa) |
| N11 | Atualização do APK | Instalar a versão seguinte do release por cima | Atualiza sem desinstalar; preferências mantidas |

## Ficha (por roteiro)
Data · versão · celular/Android · roteiro · resultado (APROVADO / REPROVADO / INCONCLUSIVO) · o que o display
mostrou · relatório copiado (Laboratório → Histórico técnico, quando houver sessão com o Tripper) · observações.

Nunca anote o PIN. Nunca registre o endereço completo do Tripper.

## Critério para 1.0.0
N00–N09 aprovados em pelo menos uma execução cada, N05/N06 em duas rotas diferentes, sem regressão do app oficial.
