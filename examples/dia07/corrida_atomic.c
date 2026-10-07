/* Aula de 7/10: corrida.c consertado com uma operacao ATOMICA.
 *
 * atomic_fetch_add faz "ler, somar 1 e escrever" como uma operacao
 * indivisivel: nenhuma outra thread consegue entrar no meio.
 * No x86-64 ela vira a instrucao "lock addl": o prefixo lock trava a
 * linha de cache durante a operacao, entre todos os nucleos.
 *
 * Compile e execute:
 *   gcc -Wall -Wextra examples/dia07/corrida_atomic.c -o corrida-atomic -pthread
 *   ./corrida-atomic
 */
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>

#define ITERACOES 1000000

static atomic_int contador = 0;     /* agora e um inteiro atomico */

static void *somar(void *arg)
{
    (void)arg;
    for (int i = 0; i < ITERACOES; ++i)
        atomic_fetch_add(&contador, 1);   /* soma indivisivel */
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
    printf("Contador: %d (esperado: %d)\n", atomic_load(&contador), 2 * ITERACOES);
    return atomic_load(&contador) == 2 * ITERACOES ? EXIT_SUCCESS : EXIT_FAILURE;
}
