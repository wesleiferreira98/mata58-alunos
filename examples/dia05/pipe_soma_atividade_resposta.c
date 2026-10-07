/* RESPOSTA DA ATIVIDADE: as seis lacunas de pipe_soma_atividade.c preenchidas.
 * Cada lacuna vem marcada com "LACUNA n" e explica a resposta, o motivo
 * e o que acontece se a resposta estiver errada.
 *
 * Resumo das respostas:
 *   1. fd[0]                                        (filho fecha a leitura)
 *   2. 10 + 20                                      (calculo no filho)
 *   3. transferir(fd[1], &valor, sizeof valor, 1)   (filho envia)
 *   4. fd[1]                                        (pai fecha a escrita)
 *   5. transferir(fd[0], &valor, sizeof valor, 0)   (pai recebe)
 *   6. fd[0]                                        (pai fecha a leitura)
 */
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
        /* LACUNA 1: close(fd[0])
         * O filho so ESCREVE no pipe, entao a ponta de LEITURA (fd[0]) nao serve
         * para ele. Fechar o que nao se usa evita referencias sobrando ao canal.
         * Erro comum: fechar fd[1] aqui. Ai o filho nao consegue mais enviar
         * ("write: Bad file descriptor") e o pai recebe EOF sem nenhum byte
         * ("Transferencia incompleta"). */
        close(fd[0]);

        /* O calculo acontece na memoria do filho; o pai nao ve esta variavel. */
        /* LACUNA 2: 10 + 20
         * O calculo acontece na memoria do FILHO. Depois desta linha, valor vale
         * 30 so no filho; o valor do pai continua indefinido ate chegar pelo pipe. */
        valor = 10 + 20;

        /* Envia os bytes de valor: descritor, endereco, tamanho e modo. */
        /* LACUNA 3: transferir(fd[1], &valor, sizeof valor, 1)
         *   fd[1]          a ponta de ESCRITA, a unica que o filho manteve aberta;
         *   &valor         o ENDERECO da variavel: transferir copia os bytes que
         *                  estao ali (passar valor, sem &, nao compila com -Werror);
         *   sizeof valor   quantos bytes enviar: o tamanho de um int (4 no Linux);
         *   1              modo 1 = enviar (chama write).
         * O SO copia esses bytes da memoria do filho para o buffer do pipe. */
        int resultado = transferir(fd[1], &valor, sizeof valor, 1);

        /* Fechar a escrita avisa o leitor de que nao vira mais nada (EOF). */
        close(fd[1]);

        /* _exit encerra so o filho, sem esvaziar buffers herdados do pai.
         * O codigo de saida diz ao pai se o envio deu certo. */
        _exit(resultado == 0 ? EXIT_SUCCESS : EXIT_FAILURE);
    }

    /* ===== PAI: LE do pipe ===== */

    /* O pai so le: fecha a ponta que ele nunca usa.
     * Esquecer uma ponta aberta pode impedir o EOF e travar a leitura. */
    /* LACUNA 4: close(fd[1])
     * O pai so LE, entao fecha a ponta de ESCRITA.
     * Por que isso importa: o EOF so chega quando TODAS as pontas de escrita
     * estao fechadas. Se o pai mantiver fd[1] aberto e o filho falhar antes de
     * escrever, o read do pai espera para sempre, porque o proprio pai ainda
     * conta como um possivel escritor. */
    close(fd[1]);

    /* Recebe os bytes em valor. Se o filho ainda nao escreveu, read espera. */
    /* LACUNA 5: transferir(fd[0], &valor, sizeof valor, 0)
     *   fd[0]          a ponta de LEITURA;
     *   &valor         onde guardar os bytes recebidos (a variavel do PAI);
     *   sizeof valor   quantos bytes esperar: os mesmos 4 que o filho enviou;
     *   0              modo 0 = receber (chama read).
     * Se o filho ainda nao escreveu, read bloqueia (espera) ate os dados chegarem.
     * Os dois &valor sao variaveis diferentes, uma em cada processo: o pipe
     * copiou os bytes de uma para a outra. */
    int resultado = transferir(fd[0], &valor, sizeof valor, 0);

    /* Terminou de usar o canal: fecha a ponta que usou. */
    /* LACUNA 6: close(fd[0])
     * O pai terminou de ler e fecha a ponta que USOU, a de leitura.
     * (fd[1] ja foi fechado na lacuna 4; fechar de novo seria um erro.) */
    close(fd[0]);

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
