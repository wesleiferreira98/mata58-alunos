/* Aula de 7/10: condicao de corrida com duas threads e um contador comum.
 *
 * O que observar:
 *   1. Duas threads somam 1 ao MESMO contador, ITERACOES vezes cada uma.
 *   2. O esperado seria 2 * ITERACOES, mas o resultado costuma sair MENOR
 *      e muda a cada execucao.
 *   3. contador++ nao e uma operacao unica: e LER, SOMAR 1 e ESCREVER.
 *      Se as duas threads leem o mesmo valor antes de escrever, um
 *      incremento se perde.
 *
 * Atencao: acessar uma variavel comum de duas threads sem sincronizacao e
 * uma "data race", e em C isso e comportamento indefinido. Este programa
 * existe so para MOSTRAR o problema; nunca escreva codigo assim de verdade.
 * O volatile abaixo nao conserta nada: ele so impede o compilador de juntar
 * as somas numa conta unica, para que a corrida apareca na execucao.
 *
 * Compile e execute:
 *   gcc -Wall -Wextra examples/dia07/corrida.c -o corrida -pthread
 *   ./corrida
 */
#include <pthread.h>    /* pthread_create, pthread_join */
#include <stdio.h>      /* printf, fprintf */
#include <stdlib.h>     /* EXIT_SUCCESS, EXIT_FAILURE */
#include <string.h>     /* strerror */

#define ITERACOES 1000000

/* Variavel global: TODAS as threads do processo enxergam a mesma.
 * (Na aula de 5/10, cada processo tinha a sua copia; aqui nao.) */
static volatile int contador = 0;

/* Codigo executado por cada thread. */
static void *somar(void *arg)
{
    (void)arg;  /* nao usamos o argumento */
    for (int i = 0; i < ITERACOES; ++i) {
        /* REGIAO CRITICA SEM PROTECAO: le, soma 1 e escreve.
         * A outra thread pode ler o valor antigo no meio desse caminho. */
        contador = contador + 1;
    }
    return NULL;
}

int main(void)
{
    pthread_t t1, t2;

    /* Cria as duas threads. Elas comecam a executar somar() imediatamente
     * e disputam a CPU entre si (e com a main). */
    int erro = pthread_create(&t1, NULL, somar, NULL);
    if (erro == 0) erro = pthread_create(&t2, NULL, somar, NULL);
    if (erro != 0) {
        fprintf(stderr, "pthread_create: %s\n", strerror(erro));
        return EXIT_FAILURE;
    }

    /* pthread_join espera cada thread terminar (o "waitpid" das threads). */
    pthread_join(t1, NULL);
    pthread_join(t2, NULL);

    printf("Contador: %d (esperado: %d)\n", contador, 2 * ITERACOES);
    if (contador != 2 * ITERACOES)
        printf("Perdemos %d incrementos por causa da condicao de corrida.\n",
               2 * ITERACOES - contador);
    return EXIT_SUCCESS;
}
