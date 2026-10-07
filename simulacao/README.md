# Produção de dinheiro: simulação visual

Simulação do produtor-consumidor em JavaFX. O **banco** é o produtor (cria notas), o **carro-forte** é o consumidor (retira notas) e o **cofre** é um buffer de 10 posições. Por baixo, são threads Java sincronizadas como no `prod_cons.c`.

Autor: Weslei Ferreira Santos (trabalho de 2022, refeito para a MATA58).

## Executar

Requisitos: **JDK 25 ou superior**, Maven e ambiente gráfico. O Maven baixa o JavaFX na primeira execução, por isso ela precisa de internet.

Na pasta principal do repositório:

```bash
make run-simulation     # abre a janela
make test-simulation    # testa o modelo, sem abrir janela
```

Ou dentro desta pasta:

```bash
mvn test
mvn javafx:run
```

## O que experimentar

### Aba do cofre (produtor-consumidor)

Na tela: **W** é a próxima posição de escrita (o `hi` do C), **R** é a próxima leitura (o `lo`), "itens disponíveis" corresponde a `full` e "vagas livres" a `empty`.

1. Clique em **Iniciar** e acompanhe W e R.
2. Produtor em 10 itens/s e consumidor em 0,2: o buffer enche e aparece "Aguardando vaga". No C, o produtor dorme em `sem_wait(&empty)`.
3. Inverta as velocidades: o buffer esvazia e aparece "Aguardando item". No C, o consumidor dorme em `sem_wait(&full)`.
4. **Pausar** congela tudo; **Continuar** retoma; **Reiniciar** zera o buffer. Com a simulação pausada, itens + vagas = 10.
5. **Modo monitor:** pause e escolha **Monitor (synchronized, wait, notifyAll)**. Repita os passos 2 e 3: o comportamento é o mesmo, mas quem espera agora dorme em `wait()`, sem nenhum semáforo.
6. **Condição de corrida:** pause, escolha **SEM proteção (desliga o mutex)**, ponha o produtor em 10 e o consumidor em 3 e inicie. O alerta vermelho mostra quantas vezes as duas threads estiveram juntas na região crítica e quantas atualizações do contador se perderam.

| Modo | Como o buffer é protegido |
|---|---|
| Semáforos (padrão) | `empty`, `full` e `mutex`, como o `prod_cons.c` |
| Monitor | o próprio objeto: `synchronized`, `while (cheio) wait()`, `while (vazio) wait()`, `notifyAll()` |
| Sem proteção | `empty` e `full`, sem o `mutex` |

No modo sem proteção, cada operação lê o contador, espera 250 ms e só então escreve o novo valor: é o `contador++` em câmera lenta, para a corrida ficar visível.

### Aba Mailbox

Mostra as mailboxes de `examples/dia07`. Dave ("Hello, world.", em azul) e Bill ("Hot dog!", em laranja) escrevem letra por letra na mesma caixa; cada letra aparece na cor de quem a escreveu, então uma mensagem misturada tem as duas cores.

| Versão da caixa | Equivale a | O que aparece |
|---|---|---|
| Sem sincronização | `Mailbox3b` | Mensagens misturadas ou cortadas, alerta de condição de corrida |
| Monitor | `Mailbox3s` | Mensagens íntegras, mas algumas perdidas (sobrescritas antes de serem lidas) |
| Monitor completo | `CaixaDeCorreio` | Íntegras e nenhuma perdida: os produtores esperam a caixa esvaziar |
| Semáforos | caixa de uma posição com vaga, mensagem e mutex | Mesmo resultado do monitor completo |

Os controles imitam as constantes dos exemplos Java: pausa entre letras (`MAXPROCESSTIME`), descanso dos produtores (`DELAY`) e intervalo do consumidor (`CHECKTIME`).

## Código

| Classe | Papel |
|---|---|
| `BoundedBuffer` | O cofre: semáforos de vagas, itens e mutex, índices circulares, e os modos monitor e sem proteção |
| `Simulation` | As threads do produtor e do consumidor, com pausa, reinício e velocidades |
| `Mailbox`, `MailboxSimulation`, `MailboxPane` | A aba Mailbox: a caixa nos quatro modos, as três threads e a tela |
| `MoneyApp` | A janela JavaFX; atualiza a tela a cada 100 ms, sem que as threads mexam nela |
| `SimulationTest`, `MailboxTest` | Testes do modelo |
