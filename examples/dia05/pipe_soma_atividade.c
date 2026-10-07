/* ESQUELETO: complete as seis lacunas. Nao compila antes de completar. */
/* Aula de 5/10: um filho envia um inteiro ao pai pelo pipe.
 *
 * O que observar:
 *   1. O pipe e criado ANTES do fork, para que pai e filho herdem as duas pontas.
 *   2. fd[0] e a ponta de LEITURA; fd[1] e a ponta de ESCRITA.
 *   3. Cada processo fecha a ponta que nao usa.
 *   4. O valor viaja como bytes copiados pelo SO, e nao por memoria compartilhada.
 */
#include <errno.h>      /* errno, EINTR */
#include <stdio.h>      /* printf, fprintf, perror */
#include <stdlib.h>     /* EXIT_SUCCESS, EXIT_FAILURE */
#include <sys/wait.h>   /* waitpid, WIFEXITED, WEXITSTATUS */
#include <unistd.h>     /* pipe, fork, read, write, close, _exit */

/* Envia (modo = 1) ou recebe (modo = 0) exatamente "tamanho" bytes.
 * read e write podem transferir MENOS bytes do que o pedido, entao
 * repetimos ate mover tudo. Devolve 0 em sucesso e -1 em erro. */
static int transferir(int fd, void *dado, size_t tamanho, int modo)
{
    unsigned char *p = dado;            /* percorre o dado byte a byte */
    while (tamanho > 0) {               /* enquanto faltar algum byte */
        ssize_t n = modo ? write(fd, p, tamanho) : read(fd, p, tamanho);
        if (n < 0 && errno == EINTR) continue;  /* interrompido por sinal: tenta de novo */
        if (n < 0) { perror(modo ? "write" : "read"); return -1; }
        if (n == 0) {
            /* read devolveu 0 (EOF): o escritor fechou o canal antes do fim. */
            fprintf(stderr, "Transferencia incompleta: nenhum byte recebido/enviado.\n");
            return -1;
        }
        p += n;                         /* avanca no dado */
        tamanho -= (size_t)n;           /* desconta o que ja foi */
    }
    return 0;
}

int main(void)
{
    int fd[2], valor;

    /* Cria o canal: fd[0] le, fd[1] escreve. Deve vir ANTES do fork. */
    if (pipe(fd) == -1) { perror("pipe"); return EXIT_FAILURE; }

    /* Depois do fork, pai e filho tem copias de fd[0] e fd[1]
     * que apontam para o MESMO pipe dentro do SO. */
    pid_t pid = fork();
    if (pid == -1) {
        perror("fork"); close(fd[0]); close(fd[1]); return EXIT_FAILURE;
    }

    if (pid == 0) {
        /* ===== FILHO: calcula e ESCREVE no pipe ===== */

        /* O filho so escreve: fecha a ponta que ele nunca usa. */
        close(/* 1: ponta que o filho nao usa */);

        /* O calculo acontece na memoria do filho; o pai nao ve esta variavel. */
        valor = /* 2: calculo */;

        /* Envia os bytes de valor: descritor, endereco, tamanho e modo. */
        int resultado = /* 3: enviar valor pelo helper transferir */;

        /* Fechar a escrita avisa o leitor de que nao vira mais nada (EOF). */
        close(fd[1]);

        /* _exit encerra so o filho, sem esvaziar buffers herdados do pai.
         * O codigo de saida diz ao pai se o envio deu certo. */
        _exit(resultado == 0 ? EXIT_SUCCESS : EXIT_FAILURE);
    }

    /* ===== PAI: LE do pipe ===== */

    /* O pai so le: fecha a ponta que ele nunca usa.
     * Esquecer uma ponta aberta pode impedir o EOF e travar a leitura. */
    close(/* 4: ponta que o pai nao usa */);

    /* Recebe os bytes em valor. Se o filho ainda nao escreveu, read espera. */
    int resultado = /* 5: receber valor pelo helper transferir */;

    /* Terminou de usar o canal: fecha a ponta que usou. */
    close(/* 6: ponta usada pelo pai */);

    /* Coleta o filho. waitpid NAO traz o valor: ele ja chegou pelo pipe.
     * Aqui so confirmamos que o filho terminou bem. */
    int status;
    pid_t coletado;
    do { coletado = waitpid(pid, &status, 0); } while (coletado == -1 && errno == EINTR);
    if (coletado == -1) { perror("waitpid"); return EXIT_FAILURE; }

    /* So imprime se a leitura deu certo E o filho terminou normalmente com codigo 0. */
    if (resultado != 0 || !WIFEXITED(status) || WEXITSTATUS(status) != 0)
        return EXIT_FAILURE;
    printf("Resultado recebido: %d\n", valor);
    return EXIT_SUCCESS;
}
