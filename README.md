# MATA58 Sistemas Operacionais: processos, IPC e sincronização

Códigos das aulas de 5/10 e 7/10/2026, com instruções para compilar e executar.

Material-base: Prof. Maycon Leone M. Peixoto. Exemplos revisados e simulação: Weslei Ferreira Santos.

## O que tem aqui

```text
examples/dia05/   processos, fork e pipe (C)
examples/dia07/   threads, corrida, Peterson, semáforos (C) e monitores (Java)
simulacao/        simulação visual do produtor-consumidor (JavaFX)
material/         slides e apostilas das duas aulas, em PDF
tests/            testes automáticos dos exemplos em C
Makefile          atalhos para compilar e executar
```

## Requisitos

Linux com:

* **gcc** e **make** para os exemplos em C;
* **Python 3** para os testes;
* **JDK 25 ou superior** para os exemplos Java;
* **Maven** e ambiente gráfico para a simulação.

No Ubuntu ou Debian:

```bash
sudo apt install build-essential python3 openjdk-25-jdk maven
```

No Fedora:

```bash
sudo dnf install gcc make python3 java-25-openjdk-devel maven
```

Todos os comandos abaixo são executados **na pasta principal** do repositório. Os executáveis ficam em `build/`.

```bash
make          # compila todos os exemplos em C
make test     # compila e confere se os exemplos se comportam como esperado
make clean    # apaga a pasta build/
```

## Aula de 5/10: processos e comunicação

| Comando | Arquivo | O que mostra |
|---|---|---|
| `make run-fork-basico` | `fork_basico.c` | fork retorna duas vezes: 0 no filho, PID do filho no pai |
| `make run-memoria` | `memoria_independente.c` | mesmo endereço, valores diferentes no pai e no filho |
| `make run-fork` | `fork2.c` | fork dentro de um laço cria 8 processos; o pai soma 0 |
| `make run-pipe-soma` | `pipe_soma.c` | o filho envia um inteiro ao pai por um pipe |
| `make run-pipe` | `pipe2.c` | seis processos e cinco pipes calculam as raízes de uma equação |
| `make run-resposta` | `pipe_soma_atividade_resposta.c` | resposta comentada da atividade |

O `run-pipe` pede três números: **a**, **b** e **delta** (já calculado, o programa não pede c). Exemplo: digite `1 -3 1` e o resultado será `x1 = 1; x2 = 2`.

### Atividade: pipe

O esqueleto está em `examples/dia05/pipe_soma_atividade.c`. Ele **só compila depois** de completar as seis lacunas:

```bash
gcc -std=c11 -Wall -Wextra -Wpedantic -Werror examples/dia05/pipe_soma_atividade.c -o pipe-soma-aluno
./pipe-soma-aluno
```

## Aula de 7/10: concorrência e sincronização

### Exemplos em C

| Comando | Arquivo | O que mostra |
|---|---|---|
| `make run-corrida` | `corrida.c` | duas threads perdem incrementos: o contador sai menor que o esperado |
| `make run-corrida-atomic` | `corrida_atomic.c` | o mesmo contador, correto, com operação atômica |
| `make run-corrida-mutex` | `corrida_mutex.c` | o mesmo contador, correto, com mutex |
| `make run-peterson` | `peterson-code.c` | exclusão mútua com Peterson e espera ocupada |
| `make run-prod-cons` | `prod_cons.c` | produtor-consumidor com os semáforos mutex, empty e full |
| `make run-resposta-dia07` | `prod_cons_resposta.c` | resposta comentada da atividade |

O resultado do `run-corrida` muda a cada execução. É isso que mostra a condição de corrida.

### Atividade: produtor-consumidor

O esqueleto está em `examples/dia07/prod_cons_atividade.c`. Ele **só compila depois** de completar as seis lacunas:

```bash
gcc -std=c11 -Wall -Wextra -Wpedantic -Werror examples/dia07/prod_cons_atividade.c -o prod-cons-aluno -pthread
./prod-cons-aluno
```

Correto: a última linha é `Fim: 40 itens produzidos e consumidos; buffer vazio.`

Se o programa travar, aperte `Ctrl + C`: alguma thread ficou esperando um semáforo que ninguém vai liberar. Se aparecer `Assertion ... failed`, alguém inseriu sem vaga ou retirou sem item.

### Monitores em Java (mailboxes)

Uma caixa de correio compartilhada por dois produtores (Dave diz "Hello, world." e Bill diz "Hot dog!") e um consumidor.

| Comando | Pasta | O que mostra |
|---|---|---|
| `make run-java-3b` | `mailbox-3b/` | sem sincronização: as mensagens se misturam |
| `make run-java-3s` | `mailbox-3s/` | com `synchronized`, `wait` e `notify`: mensagens íntegras |
| `make run-caixa` | `caixa/` | caixa completa: nenhuma mensagem misturada nem perdida |

As mailboxes 3b e 3s **nunca terminam sozinhas**: aperte `Ctrl + C` depois de ver algumas linhas. Os arquivos `ThreadSync3b.txt` e `ThreadSync3s.txt` mostram exemplos de saída.

Sem `make`, compile e rode direto (exemplo da 3b):

```bash
javac -d build/java-3b examples/dia07/mailbox-3b/*.java
timeout --foreground 5 java -cp build/java-3b ThreadSync3b | grep "My name"
```

O `timeout` encerra o programa depois de 5 segundos e o `grep` mostra só as mensagens. O `--foreground` é necessário quando há `| grep`; sem ele, nada aparece na tela.

### Simulação visual

A simulação "Produção de dinheiro" mostra o produtor-consumidor na tela: o banco produz notas, o carro-forte consome, e o cofre tem 10 posições. Mais detalhes em [simulacao/README.md](simulacao/README.md).

```bash
make run-simulation     # abre a janela (a primeira vez baixa o JavaFX e precisa de internet)
make test-simulation    # testa o modelo, sem abrir janela
```

## Problemas comuns

| Problema | Solução |
|---|---|
| `make: command not found` ou `gcc: command not found` | Instale os pacotes da seção Requisitos. |
| `undefined reference to pthread_create` ou `sem_wait` | Faltou `-pthread` no final do comando do gcc. |
| `undefined reference to sqrt` | Faltou `-lm` no final do comando do gcc (só no `pipe2.c`). |
| O esqueleto da atividade não compila | É proposital: complete as lacunas primeiro. |
| `No such file or directory` | Você não está na pasta principal do repositório. |
| A simulação não abre | Confira `java -version` (precisa ser 25 ou mais) e se o Maven está instalado. |

## Para estudar

Os slides e as apostilas das duas aulas estão em `material/`. As apostilas trazem os mesmos temas com mais calma e exercícios com respostas.
