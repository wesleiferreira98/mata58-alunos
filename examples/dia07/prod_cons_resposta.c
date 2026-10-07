/* RESPOSTA DA ATIVIDADE: as seis lacunas de prod_cons_atividade.c preenchidas.
 * Cada lacuna vem marcada com "LACUNA n" e explica a resposta, o motivo
 * e o que acontece se a resposta estiver errada (comportamentos testados).
 *
 * Resumo das respostas:
 *   1. N        (empty: todas as vagas livres no inicio)
 *   2. 0        (full: nenhum item no inicio)
 *   3. &empty   (produtor espera VAGA)
 *   4. &full    (produtor avisa: mais um ITEM)
 *   5. &full    (consumidor espera ITEM)
 *   6. &empty   (consumidor avisa: mais uma VAGA)
 *
 * Regra para lembrar: cada thread ESPERA o que consome e AVISA o que cria.
 * O produtor consome vagas e cria itens; o consumidor, o contrario.
 */
/* Aula de 7/10: produtor-consumidor com buffer circular e semaforos POSIX.
 *
 * Sua tarefa: completar as SEIS lacunas marcadas com numeros.
 * O mutex ja esta pronto. As lacunas tratam de DISPONIBILIDADE:
 *   empty conta as VAGAS livres do buffer;
 *   full  conta os ITENS prontos para consumir.
 *
 * Perguntas que ajudam:
 *   Quantas vagas existem quando o programa comeca? E quantos itens?
 *   Antes de inserir, o produtor precisa ter certeza de que...?
 *   Depois de inserir, quem precisa ficar sabendo? De que?
 *   E o consumidor, do lado contrario?
 *
 * Compile e execute (na raiz do repositorio):
 *   gcc -std=c11 -Wall -Wextra -Wpedantic -Werror \
 *       examples/dia07/prod_cons_resposta.c -o prod-cons-resposta -pthread
 *   ./prod-cons-resposta
 *
 * Correto: termina com "Fim: 40 itens produzidos e consumidos; buffer vazio."
 * Se o programa TRAVAR (parar de imprimir sem terminar), aperte Ctrl + C:
 * alguma thread ficou esperando um semaforo que ninguem vai liberar.
 *
 * Semaforo em uma frase: um contador que nunca fica negativo.
 *   sem_wait: se o contador > 0, diminui 1 e segue; se for 0, DORME ate alguem fazer sem_post.
 *   sem_post: aumenta 1 e acorda quem estiver dormindo nele.
 */
#include <assert.h>     /* assert: confere invariantes durante a execucao */
#include <errno.h>      /* errno, EINTR */
#include <pthread.h>    /* pthread_create, pthread_join */
#include <semaphore.h>  /* sem_t, sem_init, sem_wait, sem_post, sem_destroy */
#include <stdio.h>      /* printf, perror */
#include <stdlib.h>     /* rand, srand, exit */
#include <string.h>     /* strerror */
#include <time.h>       /* time, para a semente de rand */

#define N 20            /* capacidade do buffer */
#define TOTAL_ITENS 40  /* quantos itens o produtor gera antes de terminar */
static sem_t mutex, empty, full;
/* Buffer circular: hi = proxima posicao de escrita, lo = proxima de leitura,
 * count = quantos itens estao guardados agora. */
static int buffer[N], lo, hi, count;

/* Funcoes sem_* devolvem -1 e preenchem errno em caso de erro. */
static void check_sem(int resultado, const char *operacao)
{
    if (resultado == -1) { perror(operacao); exit(EXIT_FAILURE); }
}

/* sem_wait que tenta de novo se for interrompido por um sinal (EINTR). */
static void wait_sem(sem_t *sem)
{
    int resultado;
    do { resultado = sem_wait(sem); } while (resultado == -1 && errno == EINTR);
    check_sem(resultado, "sem_wait");
}

/* pthread_* devolvem o codigo de erro (0 = sucesso). */
static void check_thread(int erro, const char *operacao)
{
    if (erro != 0) {
        fprintf(stderr, "%s: %s\n", operacao, strerror(erro));
        exit(EXIT_FAILURE);
    }
}

/* Chamadas somente com mutex adquirido. */
static void insert_item(int item)
{
    assert(count < N);          /* se falhar: o produtor inseriu sem haver vaga */
    buffer[hi] = item;
    hi = (hi + 1) % N;          /* % N: depois da ultima posicao, volta para a 0 */
    ++count;
}

static int remove_item(void)
{
    assert(count > 0);          /* se falhar: o consumidor retirou sem haver item */
    int item = buffer[lo];
    lo = (lo + 1) % N;          /* leitura tambem da a volta: FIFO circular */
    --count;
    return item;
}

static void *producer(void *arg)
{
    (void)arg;
    for (int i = 0; i < TOTAL_ITENS; ++i) {
        int item = rand() % 100; /* Apenas o produtor usa rand. */
        /* Passo 1: antes de pegar o mutex, espere o que o produtor precisa. */
        /* LACUNA 3: wait_sem(&empty)
         * O produtor precisa de uma VAGA para inserir. Se empty = 0 (buffer
         * cheio), ele dorme aqui ate o consumidor liberar uma vaga.
         * Vem ANTES do mutex: quem dorme segurando o mutex impede o outro de
         * mexer no buffer, e ninguem acorda ninguem (deadlock).
         * Erro comum: wait_sem(&full). Com full = 0 no inicio, o produtor dorme
         * esperando um item que so ele poderia criar: o programa TRAVA. */
        wait_sem(&empty);
        wait_sem(&mutex);       /* Passo 2: pega a chave do buffer */
        insert_item(item);      /* Passo 3: regiao critica */
        printf("Produzido: %d | ocupacao: %d/%d\n", item, count, N);
        check_sem(sem_post(&mutex), "sem_post mutex");  /* Passo 4: devolve a chave */
        /* Passo 5: avise o outro lado sobre o que voce acabou de criar. */
        /* LACUNA 4: sem_post(&full)
         * O produtor acabou de criar um ITEM: soma 1 em full e acorda o
         * consumidor, se ele estiver dormindo em wait_sem(&full).
         * Erro comum: sem_post(&empty). Ai empty nunca diminui de verdade, o
         * produtor nunca para e insere com o buffer cheio:
         * "Assertion `count < N' failed". */
        check_sem(sem_post(&full), "sem_post full");
    }
    return NULL;
}

static void *consumer(void *arg)
{
    (void)arg;
    for (int i = 0; i < TOTAL_ITENS; ++i) {
        /* Passo 1: antes de pegar o mutex, espere o que o consumidor precisa. */
        /* LACUNA 5: wait_sem(&full)
         * O consumidor precisa de um ITEM. Se full = 0 (buffer vazio), ele
         * dorme aqui ate o produtor criar um item. Tambem vem ANTES do mutex. */
        wait_sem(&full);
        wait_sem(&mutex);       /* Passo 2: pega a chave do buffer */
        int item = remove_item();   /* Passo 3: regiao critica */
        /* count e lido dentro da mesma regiao que protege suas escritas. */
        printf("Consumido: %d | ocupacao: %d/%d\n", item, count, N);
        check_sem(sem_post(&mutex), "sem_post mutex");  /* Passo 4: devolve a chave */
        /* Passo 5: avise o outro lado sobre o que voce acabou de liberar. */
        /* LACUNA 6: sem_post(&empty)
         * O consumidor acabou de liberar uma VAGA: soma 1 em empty e acorda o
         * produtor, se ele estiver dormindo em wait_sem(&empty).
         * Erro comum: sem_post(&full). Ai full cresce sem haver item, e o
         * consumidor retira de um buffer vazio: "Assertion `count > 0' failed". */
        check_sem(sem_post(&empty), "sem_post empty");
    }
    return NULL;
}

int main(void)
{
    srand((unsigned)time(NULL));
    /* sem_init(semaforo, 0 = compartilhado entre threads do processo, valor inicial) */
    check_sem(sem_init(&mutex, 0, 1), "sem_init mutex");   /* buffer comeca livre */
    /* LACUNA 1: N
     * No inicio o buffer esta vazio: as N posicoes sao vagas livres.
     * Erro comum: 0. Com empty = 0 e full = 0, o produtor espera vaga e o
     * consumidor espera item; ninguem avanca e o programa TRAVA. */
    check_sem(sem_init(&empty, 0, N), "sem_init empty");
    /* LACUNA 2: 0
     * No inicio nao ha nenhum item pronto.
     * Erro comum: N. O consumidor acha que ha itens e retira de um buffer
     * vazio: "Assertion `count > 0' failed". O mesmo acontece se as lacunas
     * 1 e 2 forem trocadas (empty = 0, full = N).
     * Invariante util: com nenhuma operacao em andamento, empty + full = N. */
    check_sem(sem_init(&full, 0, 0), "sem_init full");
    pthread_t produtor, consumidor;
    check_thread(pthread_create(&produtor, NULL, producer, NULL), "pthread_create");
    check_thread(pthread_create(&consumidor, NULL, consumer, NULL), "pthread_create");
    /* join espera o termino; nao impede uma thread de terminar antes. */
    check_thread(pthread_join(produtor, NULL), "pthread_join");
    check_thread(pthread_join(consumidor, NULL), "pthread_join");
    assert(count == 0);         /* tudo que foi produzido foi consumido */
    check_sem(sem_destroy(&mutex), "sem_destroy mutex");
    check_sem(sem_destroy(&empty), "sem_destroy empty");
    check_sem(sem_destroy(&full), "sem_destroy full");
    printf("Fim: %d itens produzidos e consumidos; buffer vazio.\n", TOTAL_ITENS);
    return EXIT_SUCCESS;
}
