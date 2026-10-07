/* Aula de SO: produtor-consumidor com buffer circular e semaforos POSIX.
 *
 * O que observar:
 *   1. Uma thread PRODUZ numeros e outra os CONSOME, pelo mesmo buffer de N posicoes.
 *   2. Tres semaforos, com papeis diferentes:
 *        mutex (comeca em 1): so uma thread por vez mexe no buffer   -> EXCLUSAO MUTUA
 *        empty (comeca em N): quantas vagas livres existem           -> DISPONIBILIDADE
 *        full  (comeca em 0): quantos itens prontos existem          -> DISPONIBILIDADE
 *   3. Com buffer cheio, o produtor dorme em sem_wait(&empty).
 *      Com buffer vazio, o consumidor dorme em sem_wait(&full).
 *   4. A ORDEM importa: primeiro espera vaga/item, depois pega o mutex.
 *      Na ordem inversa, as duas threads podem travar para sempre (deadlock).
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
    assert(count < N);          /* empty garante que ha vaga */
    buffer[hi] = item;
    hi = (hi + 1) % N;          /* % N: depois da ultima posicao, volta para a 0 */
    ++count;
}

static int remove_item(void)
{
    assert(count > 0);          /* full garante que ha item */
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
        wait_sem(&empty);       /* 1. reserva uma VAGA (dorme se o buffer estiver cheio) */
        wait_sem(&mutex);       /* 2. pega a chave do buffer */
        insert_item(item);      /* 3. regiao critica: mexe em buffer, hi e count */
        printf("Produzido: %d | ocupacao: %d/%d\n", item, count, N);
        check_sem(sem_post(&mutex), "sem_post mutex");  /* 4. devolve a chave */
        check_sem(sem_post(&full), "sem_post full");    /* 5. avisa: ha mais um ITEM */
    }
    return NULL;
}

static void *consumer(void *arg)
{
    (void)arg;
    for (int i = 0; i < TOTAL_ITENS; ++i) {
        wait_sem(&full);        /* 1. reserva um ITEM (dorme se o buffer estiver vazio) */
        wait_sem(&mutex);       /* 2. pega a chave do buffer */
        int item = remove_item();   /* 3. regiao critica: mexe em buffer, lo e count */
        /* count e lido dentro da mesma regiao que protege suas escritas. */
        printf("Consumido: %d | ocupacao: %d/%d\n", item, count, N);
        check_sem(sem_post(&mutex), "sem_post mutex");  /* 4. devolve a chave */
        check_sem(sem_post(&empty), "sem_post empty");  /* 5. avisa: ha mais uma VAGA */
        /* Processamento do item, se necessario, pode ocorrer aqui fora do lock. */
    }
    return NULL;
}

int main(void)
{
    srand((unsigned)time(NULL));
    /* sem_init(semaforo, 0 = compartilhado entre threads do processo, valor inicial) */
    check_sem(sem_init(&mutex, 0, 1), "sem_init mutex");   /* buffer comeca livre */
    check_sem(sem_init(&empty, 0, N), "sem_init empty");   /* todas as N vagas livres */
    check_sem(sem_init(&full, 0, 0), "sem_init full");     /* nenhum item ainda */
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
