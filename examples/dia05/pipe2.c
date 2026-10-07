/* Aula de SO: exemplo de Maycon Leone M. Peixoto, revisado.
 * Seis processos, cinco pipes; somente o pai le a entrada do terminal.
 * Os pipes transportam doubles binarios entre processos da mesma maquina.
 *
 * Quem cria quem:
 *   pai original
 *     calculador  (son1): recebe -b, 2a e delta; calcula e envia x1 e x2 ao pai
 *     coordenador (son5): cria os tres fornecedores
 *       fornecedor 0: envia -b    pelo canais[0]
 *       fornecedor 1: envia 2a    pelo canais[1]
 *       fornecedor 2: envia delta pelo canais[2]
 *
 * Por onde os dados passam:
 *   fornecedores -> canais[0], [1], [2] -> calculador
 *   calculador   -> canais[3] (x1), canais[4] (x2) -> pai
 *
 * Arvore de criacao e fluxo de dados sao coisas diferentes: os fornecedores
 * sao filhos do coordenador, mas enviam ao calculador.
 */
#include <errno.h>      /* errno, EINTR, ECHILD */
#include <math.h>       /* sqrt, isfinite (compile com -lm) */
#include <signal.h>     /* signal, SIGPIPE */
#include <stdio.h>      /* printf, scanf, fprintf, perror */
#include <stdlib.h>     /* EXIT_SUCCESS, EXIT_FAILURE */
#include <sys/wait.h>   /* waitpid, WIFEXITED, WEXITSTATUS */
#include <unistd.h>     /* pipe, fork, read, write, close, _exit */

/* Os cinco pipes. canais[i][0] e a ponta de leitura; canais[i][1], a de escrita.
 * Sao criados pelo pai antes dos fork, entao todos os processos os herdam. */
static int canais[5][2];

/* Mantem somente os descritores usados por este processo.
 * Recebe ate tres canais para continuar lendo e dois para continuar escrevendo;
 * -1 significa "nenhum". Todas as outras pontas dos cinco pipes sao fechadas.
 * Fechar o que nao se usa e o que permite ao leitor receber EOF no fim. */
static void fechar_exceto(int leitura1, int leitura2, int leitura3,
                          int escrita1, int escrita2)
{
    for (int i = 0; i < 5; ++i) {
        if (i != leitura1 && i != leitura2 && i != leitura3)
            close(canais[i][0]);
        if (i != escrita1 && i != escrita2) close(canais[i][1]);
    }
}

/* Envia os bytes de um double. write pode escrever menos que o pedido,
 * entao repetimos ate enviar os sizeof(double) bytes. */
static int enviar(int fd, double valor)
{
    const unsigned char *p = (const unsigned char *)&valor;
    size_t restante = sizeof valor;
    while (restante > 0) {
        ssize_t n = write(fd, p, restante);
        if (n < 0 && errno == EINTR) continue;      /* interrompido por sinal: tenta de novo */
        if (n <= 0) { perror("write"); return -1; } /* erro, por exemplo EPIPE (ninguem le) */
        p += n;
        restante -= (size_t)n;
    }
    return 0;
}

/* Recebe os bytes de um double. read pode devolver menos que o pedido;
 * se devolver 0 (EOF), todos os escritores fecharam o canal antes do fim. */
static int receber(int fd, double *valor)
{
    unsigned char *p = (unsigned char *)valor;
    size_t restante = sizeof *valor;
    while (restante > 0) {
        ssize_t n = read(fd, p, restante);
        if (n < 0 && errno == EINTR) continue;
        if (n < 0) { perror("read"); return -1; }
        if (n == 0) {
            fprintf(stderr, "Pipe fechado antes de receber o valor completo.\n");
            return -1;
        }
        p += n;
        restante -= (size_t)n;
    }
    return 0;
}

/* Cada pai coleta todos os seus filhos, inclusive em caminhos de falha.
 * Devolve -1 se algum filho terminou com erro. */
static int aguardar_filhos(void)
{
    int falhou = 0;
    for (;;) {
        int status;
        pid_t pid = waitpid(-1, &status, 0);
        if (pid > 0) {
            if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) falhou = 1;
        } else if (errno == EINTR) continue;    /* interrompido: espera de novo */
        else if (errno == ECHILD) break;        /* nao ha mais filhos */
        else { perror("waitpid"); return -1; }
    }
    return falhou ? -1 : 0;
}

/* Processo calculador: le os tres componentes, calcula e envia as raizes. */
static void calculador(void)
{
    double menos_b, dois_a, delta, x1, x2;
    /* Fica so com: leitura de canais[0], [1], [2] e escrita de canais[3], [4]. */
    fechar_exceto(0, 1, 2, 3, 4);
    /* Cada receber espera (bloqueia) ate o fornecedor correspondente escrever. */
    if (receber(canais[0][0], &menos_b) == -1 ||
        receber(canais[1][0], &dois_a) == -1 ||
        receber(canais[2][0], &delta) == -1) _exit(EXIT_FAILURE);
    x1 = (menos_b - sqrt(delta)) / dois_a;
    x2 = (menos_b + sqrt(delta)) / dois_a;
    if (!isfinite(x1) || !isfinite(x2)) {
        fprintf(stderr, "Raizes fora do intervalo numerico suportado.\n");
        _exit(EXIT_FAILURE);
    }
    /* Envia x1 pelo canais[3] e x2 pelo canais[4] ao pai. */
    int falhou = enviar(canais[3][1], x1) == -1;
    if (enviar(canais[4][1], x2) == -1) falhou = 1;
    fechar_exceto(-1, -1, -1, -1, -1);  /* fecha tudo o que ainda estava aberto */
    _exit(falhou ? EXIT_FAILURE : EXIT_SUCCESS);
}

/* Processo coordenador: cria tres fornecedores, um por componente. */
static void fornecedores(double a, double b, double delta)
{
    /* Coordenador conserva temporariamente as tres pontas de escrita,
     * so para que os fornecedores as herdem no fork. Fecha todo o resto. */
    for (int i = 0; i < 5; ++i) {
        close(canais[i][0]);
        if (i >= 3) close(canais[i][1]);
    }
    /* Os valores ja estao na memoria (herdados do pai); o objetivo e
     * envia-los ao calculador pelos pipes, para exercitar o protocolo. */
    double valores[3] = {-b, 2 * a, delta};
    int falhou = 0;
    for (int i = 0; i < 3; ++i) {
        pid_t pid = fork();
        if (pid == -1) { perror("fork fornecedor"); falhou = 1; break; }
        if (pid == 0) {
            /* Fornecedor i: fecha as pontas de escrita dos OUTROS dois canais. */
            for (int j = 0; j < 3; ++j)
                if (j != i) close(canais[j][1]);
            int resultado = enviar(canais[i][1], valores[i]);
            close(canais[i][1]);
            _exit(resultado == 0 ? EXIT_SUCCESS : EXIT_FAILURE);
        }
    }
    /* Fornecedores criados: o coordenador nao escreve nada, entao fecha
     * as tres pontas. Se ficasse com elas, o calculador nunca veria EOF. */
    for (int i = 0; i < 3; ++i) close(canais[i][1]);
    if (aguardar_filhos() == -1) falhou = 1;
    _exit(falhou ? EXIT_FAILURE : EXIT_SUCCESS);
}

int main(void)
{
    double a, b, delta;

    /* So o pai le o teclado, e faz isso ANTES dos fork. */
    printf(">>> Digite tres numeros separados por espaco e tecle Enter: a b delta\n");
    printf(">>> Exemplo: 1 -3 1   (delta ja calculado; o programa nao pede c)\n");
    printf("a b delta: ");
    fflush(stdout);  /* mostra o pedido agora e evita texto duplicado nos filhos */
    if (scanf("%lf %lf %lf", &a, &b, &delta) != 3 ||
        !isfinite(a) || !isfinite(b) || !isfinite(delta) ||
        a == 0 || delta < 0 || !isfinite(2 * a)) {
        fprintf(stderr, "Entrada invalida: use numeros finitos, a != 0 e delta >= 0.\n");
        return EXIT_FAILURE;
    }
    /* Converte pipe sem leitor em erro de write, permitindo tratar a falha.
     * Sem isto, escrever num pipe sem leitor mataria o processo com SIGPIPE. */
    if (signal(SIGPIPE, SIG_IGN) == SIG_ERR) { perror("signal"); return EXIT_FAILURE; }

    /* Cria os cinco pipes ANTES dos fork, para todos os filhos herdarem. */
    for (int i = 0; i < 5; ++i) {
        if (pipe(canais[i]) == -1) {
            perror("pipe");
            for (int j = 0; j < i; ++j) {
                close(canais[j][0]); close(canais[j][1]);
            }
            return EXIT_FAILURE;
        }
    }

    /* Primeiro filho: o calculador. calculador() termina com _exit, nunca retorna. */
    pid_t son1 = fork();
    if (son1 == -1) {
        perror("fork calculador");
        fechar_exceto(-1, -1, -1, -1, -1);
        return EXIT_FAILURE;
    }
    if (son1 == 0) calculador();

    /* Segundo filho: o coordenador, que cria os fornecedores. */
    pid_t son5 = fork();
    if (son5 == -1) {
        perror("fork coordenador");
        fechar_exceto(-1, -1, -1, -1, -1);
        (void)aguardar_filhos();
        return EXIT_FAILURE;
    }
    if (son5 == 0) fornecedores(a, b, delta);

    /* Pai: fica so com a leitura de canais[3] e canais[4] e recebe as raizes. */
    fechar_exceto(3, 4, -1, -1, -1);
    double x1, x2;
    int falhou = receber(canais[3][0], &x1) == -1;
    if (receber(canais[4][0], &x2) == -1) falhou = 1;
    close(canais[3][0]); close(canais[4][0]);

    /* Coleta os dois filhos diretos (calculador e coordenador). */
    if (aguardar_filhos() == -1) falhou = 1;
    if (!falhou) printf("Raizes: x1 = %.10g; x2 = %.10g\n", x1, x2);
    return falhou ? EXIT_FAILURE : EXIT_SUCCESS;
}
