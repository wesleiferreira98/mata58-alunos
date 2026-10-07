/* ESQUELETO: complete as seis lacunas. Nao compila antes de completar. */
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
 *       examples/dia07/prod_cons_atividade.c -o prod-cons-aluno -pthread
 *   ./prod-cons-aluno
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
        wait_sem(/* 3: semaforo que o produtor espera */);
        wait_sem(&mutex);       /* Passo 2: pega a chave do buffer */
        insert_item(item);      /* Passo 3: regiao critica */
        printf("Produzido: %d | ocupacao: %d/%d\n", item, count, N);
        check_sem(sem_post(&mutex), "sem_post mutex");  /* Passo 4: devolve a chave */
        /* Passo 5: avise o outro lado sobre o que voce acabou de criar. */
        check_sem(sem_post(/* 4: semaforo que o produtor avisa */), "sem_post 4");
    }
    return NULL;
}

static void *consumer(void *arg)
{
    (void)arg;
    for (int i = 0; i < TOTAL_ITENS; ++i) {
        /* Passo 1: antes de pegar o mutex, espere o que o consumidor precisa. */
        wait_sem(/* 5: semaforo que o consumidor espera */);
        wait_sem(&mutex);       /* Passo 2: pega a chave do buffer */
        int item = remove_item();   /* Passo 3: regiao critica */
        /* count e lido dentro da mesma regiao que protege suas escritas. */
        printf("Consumido: %d | ocupacao: %d/%d\n", item, count, N);
        check_sem(sem_post(&mutex), "sem_post mutex");  /* Passo 4: devolve a chave */
        /* Passo 5: avise o outro lado sobre o que voce acabou de liberar. */
        check_sem(sem_post(/* 6: semaforo que o consumidor avisa */), "sem_post 6");
    }
    return NULL;
}

int main(void)
{
    srand((unsigned)time(NULL));
    /* sem_init(semaforo, 0 = compartilhado entre threads do processo, valor inicial) */
    check_sem(sem_init(&mutex, 0, 1), "sem_init mutex");   /* buffer comeca livre */
    check_sem(sem_init(&empty, 0, /* 1: vagas no inicio */), "sem_init empty");
    check_sem(sem_init(&full, 0, /* 2: itens no inicio */), "sem_init full");
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
