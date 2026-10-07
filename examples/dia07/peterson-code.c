/* Peterson para duas threads: atomics C11 sequencialmente consistentes.
 * Espera ocupada intencional para fins didaticos; nao ha alternancia forcada.
 *
 * O que observar:
 *   1. Duas threads incrementam o MESMO contador, como em corrida.c,
 *      mas agora cada incremento fica dentro de uma regiao critica.
 *   2. enter_region / leave_region garantem que so UMA thread por vez
 *      execute a regiao critica (exclusao mutua).
 *   3. O resultado sai sempre 20000: nenhum incremento se perde.
 *   4. Quem espera fica girando no while (espera ocupada): gasta CPU.
 *
 * Protocolo de Peterson (para a thread "eu"):
 *   interested[eu] = verdadeiro      "quero entrar"
 *   turn = outro                     "mas, se houver disputa, voce primeiro"
 *   espera enquanto o outro quer entrar E a vez e dele
 *   ... regiao critica ...
 *   interested[eu] = falso           "sai, nao quero mais"
 *
 * Por que atomics? Com variaveis comuns, o compilador e o processador podem
 * reordenar leituras e escritas, e o algoritmo deixa de funcionar. As
 * operacoes atomic_load e atomic_store garantem a ordem que Peterson exige.
 */
#include <pthread.h>    /* pthread_create, pthread_join */
#include <stdatomic.h>  /* atomic_int, atomic_bool, atomic_load, atomic_store */
#include <stdio.h>      /* printf, fprintf */
#include <stdlib.h>     /* exit, EXIT_SUCCESS, EXIT_FAILURE */
#include <string.h>     /* strcpy, strerror */

#define ITERACOES 10000
static atomic_int turn = 0;         /* de quem e a vez quando ambos querem entrar */
static atomic_bool interested[2];   /* interested[i]: a thread i quer entrar */
static char cr[16];                 /* recurso compartilhado: nome de quem esta dentro */
static int contador;                /* recurso compartilhado: protegido pela regiao critica */

/* Protocolo de ENTRADA: so retorna quando for seguro entrar. */
static void enter_region(int eu)
{
    int outro = 1 - eu;                     /* a outra thread: 0 <-> 1 */
    atomic_store(&interested[eu], 1);       /* anuncia: eu quero entrar */
    atomic_store(&turn, outro);             /* cede a vez em caso de empate */
    /* Espera enquanto o outro quer entrar E a vez e dele.
     * Se o outro nao quer entrar, a condicao e falsa e eu entro direto.
     * Se ambos querem, quem escreveu turn por ULTIMO e quem espera. */
    while (atomic_load(&interested[outro]) && atomic_load(&turn) == outro) {
        /* Espera ocupada: a thread continua rodando e testando, gastando CPU. */
    }
}

/* Protocolo de SAIDA: avisa que nao esta mais na regiao critica. */
static void leave_region(int eu)
{
    atomic_store(&interested[eu], 0);
}

/* Codigo de cada thread: ITERACOES entradas e saidas da regiao critica. */
static void *thread(void *arg)
{
    int eu = *(int *)arg;   /* 0 ou 1: qual das duas threads sou eu */
    for (int i = 0; i < ITERACOES; ++i) {
        enter_region(eu);
        /* ===== REGIAO CRITICA: so uma thread por vez chega aqui ===== */
        strcpy(cr, eu == 0 ? "thread0" : "thread1#######");
        ++contador;                     /* agora o incremento nao se perde */
        if (i == 0) printf("Regiao critica: %s\n", cr);
        /* ===== fim da regiao critica ===== */
        leave_region(eu);
    }
    return NULL;
}

/* pthread_* devolvem o codigo de erro (0 = sucesso), e nao -1 com errno. */
static void check(int erro, const char *operacao)
{
    if (erro != 0) {
        fprintf(stderr, "%s: %s\n", operacao, strerror(erro));
        exit(EXIT_FAILURE);
    }
}

int main(void)
{
    pthread_t threads[2];
    int ids[2] = {0, 1};    /* cada thread recebe o endereco do seu numero */
    atomic_init(&interested[0], 0);
    atomic_init(&interested[1], 0);
    for (int i = 0; i < 2; ++i)
        check(pthread_create(&threads[i], NULL, thread, &ids[i]), "pthread_create");
    /* Espera as duas threads terminarem antes de ler o contador. */
    for (int i = 0; i < 2; ++i)
        check(pthread_join(threads[i], NULL), "pthread_join");
    printf("Contador: %d (esperado: %d)\n", contador, 2 * ITERACOES);
    return contador == 2 * ITERACOES ? EXIT_SUCCESS : EXIT_FAILURE;
}
