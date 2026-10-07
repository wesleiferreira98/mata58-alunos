/* Aula de 7/10: corrida.c consertado com um MUTEX (pthread_mutex_t).
 *
 * pthread_mutex_lock / pthread_mutex_unlock delimitam a regiao critica:
 * so uma thread por vez executa "contador = contador + 1".
 * Se o mutex estiver ocupado, a thread que chega DORME (nao gira).
 *
 * Compile e execute:
 *   gcc -Wall -Wextra examples/dia07/corrida_mutex.c -o corrida-mutex -pthread
 *   ./corrida-mutex
 */
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>

#define ITERACOES 1000000

static int contador = 0;                                 /* inteiro comum */
static pthread_mutex_t trava = PTHREAD_MUTEX_INITIALIZER; /* a "chave" */

static void *somar(void *arg)
{
    (void)arg;
    for (int i = 0; i < ITERACOES; ++i) {
        pthread_mutex_lock(&trava);      /* entra: pega a chave (ou dorme) */
        contador = contador + 1;         /* regiao critica protegida */
        pthread_mutex_unlock(&trava);    /* sai: devolve a chave */
    }
    return NULL;
}

int main(void)
{
    pthread_t t1, t2;
    if (pthread_create(&t1, NULL, somar, NULL) != 0 ||
        pthread_create(&t2, NULL, somar, NULL) != 0) {
        fprintf(stderr, "pthread_create falhou\n");
        return EXIT_FAILURE;
    }
    pthread_join(t1, NULL);
    pthread_join(t2, NULL);
    printf("Contador: %d (esperado: %d)\n", contador, 2 * ITERACOES);
    return contador == 2 * ITERACOES ? EXIT_SUCCESS : EXIT_FAILURE;
}
