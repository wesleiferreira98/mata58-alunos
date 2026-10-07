/* Aula de SO: todos os processos continuam o laco de fork.
 *
 * O que observar:
 *   1. Com tamanho = 3, o programa cria 8 processos ao todo (1, 2, 4, 8),
 *      porque cada filho tambem continua o laco e chama fork de novo.
 *   2. Cada filho escreve na SUA copia do vetor v.
 *   3. Por isso o processo original imprime soma 0, e nao 5 + 6 + 7 = 18.
 */
#include <errno.h>      /* errno, EINTR, ECHILD */
#include <stdio.h>      /* printf, perror */
#include <stdlib.h>     /* calloc, free, EXIT_SUCCESS, EXIT_FAILURE */
#include <sys/wait.h>   /* waitpid e as macros WIFEXITED, WEXITSTATUS */
#include <unistd.h>     /* fork, getpid */

int main(void)
{
    const int tamanho = 3;

    /* calloc reserva o vetor JA ZERADO: v = [0, 0, 0].
     * (O exemplo original usava malloc, que nao zera a memoria.) */
    int *v = calloc((size_t)tamanho, sizeof *v);

    /* Guarda o PID de quem comecou o programa. Os filhos herdam este numero,
     * mas so um processo tera getpid() == original: o proprio original. */
    pid_t original = getpid();

    int falhou = 0;
    if (v == NULL) { perror("calloc"); return EXIT_FAILURE; }

    /* Em cada volta, TODO processo vivo chama fork.
     * i = 0: 1 processo vira 2.  i = 1: 2 viram 4.  i = 2: 4 viram 8. */
    for (int i = 0; i < tamanho; ++i) {
        pid_t pid = fork();

        /* -1: o SO nao conseguiu criar o filho (por exemplo, limite de processos). */
        if (pid == -1) { perror("fork"); falhou = 1; break; }

        /* 0: estamos no filho. Ele escreve na SUA copia do vetor.
         * O pai recebeu o PID do filho (> 0), nao entra aqui e nao ve a escrita.
         * Repare que o filho NAO sai do laco: ele segue para a proxima volta. */
        if (pid == 0) v[i] = i + 5;
    }

    /* Cada processo espera e coleta os SEUS filhos diretos.
     * waitpid(-1, ...) espera qualquer filho; repetimos ate nao restar nenhum.
     * Importante: esperar NAO traz o vetor do filho para o pai. */
    for (;;) {
        int status;
        pid_t pid = waitpid(-1, &status, 0);
        if (pid > 0) {
            /* Coletou um filho: confere se ele terminou normalmente e com codigo 0. */
            if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) falhou = 1;
        } else if (errno == EINTR) continue;   /* espera interrompida por sinal: tenta de novo */
        else if (errno == ECHILD) break;       /* nao ha mais filhos: fim da coleta */
        else { perror("waitpid"); falhou = 1; break; }
    }

    /* Somente o processo original soma e imprime.
     * O vetor DELE nunca foi alterado, entao a soma e 0. */
    if (getpid() == original) {
        int soma = 0;
        for (int i = 0; i < tamanho; ++i) soma += v[i];
        printf("Soma total no pai: %d\n", soma);
    }

    /* Cada processo libera a sua propria copia do vetor. */
    free(v);
    return falhou ? EXIT_FAILURE : EXIT_SUCCESS;
}
