/* Aula de 5/10: o mesmo endereco virtual guarda valores diferentes.
 *
 * O que observar:
 *   1. Pai e filho imprimem o MESMO endereco para a variavel x.
 *   2. Mesmo assim, o filho ve 99 e o pai ve 10.
 *   3. O endereco e virtual: cada processo o interpreta no seu proprio
 *      espaco de memoria, e o SO o leva a posicoes fisicas diferentes.
 */
#include <errno.h>      /* errno, EINTR */
#include <stdio.h>      /* printf, fflush, perror */
#include <stdlib.h>     /* EXIT_SUCCESS, EXIT_FAILURE */
#include <sys/wait.h>   /* waitpid */
#include <unistd.h>     /* fork, _exit */

int main(void)
{
    /* x existe antes do fork, entao o filho recebe uma copia com o valor 10. */
    int x = 10;

    pid_t pid = fork();
    if (pid == -1) { perror("fork"); return EXIT_FAILURE; }

    if (pid == 0) {
        /* Somente o filho executa este bloco. */
        x = 99; /* Altera somente a copia do filho. */

        /* %p imprime o endereco de x dentro do espaco do filho. */
        printf("Filho: x = %d em %p\n", x, (void *)&x);

        /* _exit nao esvazia buffers de stdio: esvaziamos antes. */
        fflush(stdout);
        _exit(EXIT_SUCCESS);
    }

    /* O pai espera o filho terminar. Assim temos certeza de que a
     * escrita x = 99 ja aconteceu antes de o pai olhar para x. */
    int status;
    pid_t coletado;
    do { coletado = waitpid(pid, &status, 0); } while (coletado == -1 && errno == EINTR);
    if (coletado == -1) { perror("waitpid"); return EXIT_FAILURE; }

    /* Mesmo depois da escrita do filho, o x do pai continua 10,
     * e o endereco impresso e igual ao do filho. */
    printf("Pai:   x = %d em %p\n", x, (void *)&x);
    return EXIT_SUCCESS;
}
