/* Aula de 5/10: os dois retornos de fork e a coleta do filho.
 *
 * O que observar:
 *   1. fork e chamada UMA vez, mas retorna DUAS: no pai e no filho.
 *   2. No filho, fork devolve 0. No pai, devolve o PID do filho.
 *   3. O getppid() do filho e o PID do pai.
 *   4. A ordem das linhas "Filho" e "Pai" pode mudar entre execucoes.
 */
#include <errno.h>      /* errno, EINTR */
#include <stdio.h>      /* printf, fflush, perror */
#include <stdlib.h>     /* EXIT_SUCCESS, EXIT_FAILURE */
#include <sys/wait.h>   /* waitpid, WIFEXITED, WEXITSTATUS */
#include <unistd.h>     /* fork, getpid, getppid, _exit */

int main(void)
{
    /* Ate aqui existe um so processo. */
    printf("Antes do fork: PID %d\n", (int)getpid());

    /* printf guarda o texto num buffer na memoria do processo.
     * O fork copia essa memoria; se o texto ainda estivesse no buffer,
     * ele sairia duas vezes. fflush entrega o texto ao SO agora. */
    fflush(stdout);

    /* A partir desta linha existem DOIS processos executando o mesmo codigo.
     * Cada um recebe um valor diferente em pid. */
    pid_t pid = fork();

    /* -1: o filho nao foi criado; so o pai existe e trata o erro. */
    if (pid == -1) { perror("fork"); return EXIT_FAILURE; }

    /* 0: este bloco executa SOMENTE no filho. */
    if (pid == 0) {
        printf("Filho: fork retornou 0; meu PID %d; meu pai %d\n",
               (int)getpid(), (int)getppid());

        /* _exit encerra o filho na hora, sem esvaziar os buffers de stdio.
         * Por isso esvaziamos antes, ou a linha acima poderia se perder. */
        fflush(stdout);
        _exit(EXIT_SUCCESS);
    }

    /* Daqui para baixo, so o pai executa: o filho ja saiu com _exit.
     * No pai, pid guarda o PID do filho recem criado. */
    printf("Pai: fork retornou %d; meu PID %d\n", (int)pid, (int)getpid());

    /* O pai espera o filho terminar e coleta o seu estado.
     * Se a espera for interrompida por um sinal (EINTR), tenta de novo. */
    int status;
    pid_t coletado;
    do { coletado = waitpid(pid, &status, 0); } while (coletado == -1 && errno == EINTR);
    if (coletado == -1) { perror("waitpid"); return EXIT_FAILURE; }

    /* WIFEXITED: o filho terminou normalmente?  WEXITSTATUS: com qual codigo? */
    if (WIFEXITED(status))
        printf("Pai: filho %d terminou com codigo %d\n", (int)coletado, WEXITSTATUS(status));
    return EXIT_SUCCESS;
}
