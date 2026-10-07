package br.edu.mata58.dinheiro;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffer FIFO: vagas/itens reservam disponibilidade; mutex protege o estado.
 *
 * Ha tres jeitos de usar o buffer, um para cada modo da simulacao:
 *   offer/poll                       semaforos (empty, full, mutex), o padrao;
 *   offerMonitor/pollMonitor         monitor Java (synchronized, wait, notifyAll);
 *   offerUnprotected/pollUnprotected semaforos SEM o mutex (mostra a corrida).
 * Um mesmo objeto e usado em um modo so: trocar o modo cria um buffer novo.
 *
 * Alem das operacoes protegidas (offer/poll), ha versoes SEM PROTECAO
 * (offerUnprotected/pollUnprotected) para a aula: elas continuam usando os
 * semaforos de vagas e itens, mas NAO pegam o mutex. A atualizacao do
 * contador compartilhado size vira "ler, esperar, escrever", como o
 * contador++ em camera lenta, e a condicao de corrida fica visivel.
 */
public final class BoundedBuffer {
    public record Snapshot(List<Integer> slots, int size, int nextWrite, int nextRead,
                           long produced, long consumed,
                           int notesInSlots, int insideNow, long overlaps, long lostUpdates) {}

    /** O que aconteceu numa operacao sem protecao. */
    public record UnsafeResult(boolean done, int item, boolean overlap, boolean lostUpdate,
                               int seen, int found, int written) {
        static UnsafeResult notDone() { return new UnsafeResult(false, 0, false, false, 0, 0, 0); }
    }

    private final int[] items;
    private final Semaphore empty, full = new Semaphore(0), mutex = new Semaphore(1);
    private int head, tail;
    private volatile int size;          // contador compartilhado (volatile NAO evita a corrida)
    private long produced, consumed;    // cada um e alterado por uma unica thread

    // Instrumentacao para detectar a corrida (operacoes atomicas, sempre corretas):
    private final AtomicInteger inside = new AtomicInteger();     // threads na regiao critica agora
    private final AtomicLong overlaps = new AtomicLong();         // vezes em que duas estiveram juntas
    private final AtomicLong lostUpdates = new AtomicLong();      // escritas de size que apagaram outra

    public BoundedBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacidade deve ser positiva");
        items = new int[capacity];
        empty = new Semaphore(capacity);
    }

    public boolean offer(int value, long timeoutMillis) throws InterruptedException {
        if (value <= 0) throw new IllegalArgumentException("Item deve ser positivo");
        if (!empty.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) return false;
        boolean locked = false, inserted = false;
        try {
            mutex.acquire();
            locked = true;
            enter();
            try {
                items[tail] = value;
                tail = (tail + 1) % items.length;
                size++;
                produced++;
                inserted = true;
            } finally { inside.decrementAndGet(); }
        } finally {
            if (locked) mutex.release();
            // Devolve a reserva se interrompido antes da insercao.
            if (inserted) full.release(); else empty.release();
        }
        return true;
    }

    public Integer poll(long timeoutMillis) throws InterruptedException {
        if (!full.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) return null;
        boolean locked = false, removed = false;
        int value;
        try {
            mutex.acquire();
            locked = true;
            enter();
            try {
                value = items[head];
                items[head] = 0;
                head = (head + 1) % items.length;
                size--;
                consumed++;
                removed = true;
            } finally { inside.decrementAndGet(); }
        } finally {
            if (locked) mutex.release();
            if (removed) empty.release(); else full.release();
        }
        return value;
    }

    /**
     * Insercao SEM o mutex. Os semaforos continuam garantindo que ha vaga;
     * so a exclusao mutua foi desligada. windowMillis e a pausa entre ler e
     * escrever o contador: e nessa janela que a outra thread pode entrar.
     */
    public UnsafeResult offerUnprotected(int value, long timeoutMillis, long windowMillis)
            throws InterruptedException {
        if (value <= 0) throw new IllegalArgumentException("Item deve ser positivo");
        if (!empty.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) return UnsafeResult.notDone();
        boolean overlap = enter();          // ja havia alguem na regiao critica?
        int seen, found, written;
        try {
            seen = size;                    // 1. LE o contador compartilhado
            items[tail] = value;
            tail = (tail + 1) % items.length;
            pause(windowMillis);            // 2. "calcula": a outra thread pode entrar aqui
            found = size;                   // o que a outra thread deixou no contador
            written = seen + 1;
            size = written;                 // 3. ESCREVE: se found != seen, apaga a escrita da outra
            produced++;
            overlap |= inside.get() > 1;    // alguem entrou enquanto eu estava aqui?
        } finally { inside.decrementAndGet(); }
        full.release();
        return finish(value, overlap, seen, found, written);
    }

    /** Retirada SEM o mutex; mesma ideia de offerUnprotected. */
    public UnsafeResult pollUnprotected(long timeoutMillis, long windowMillis)
            throws InterruptedException {
        if (!full.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) return UnsafeResult.notDone();
        boolean overlap = enter();
        int value, seen, found, written;
        try {
            seen = size;                    // 1. LE o contador
            value = items[head];
            items[head] = 0;
            head = (head + 1) % items.length;
            pause(windowMillis);            // 2. janela da corrida
            found = size;
            written = seen - 1;
            size = written;                 // 3. ESCREVE
            consumed++;
            overlap |= inside.get() > 1;
        } finally { inside.decrementAndGet(); }
        empty.release();
        return finish(value, overlap, seen, found, written);
    }

    /**
     * Insercao com MONITOR, sem semaforos: o proprio objeto e o monitor.
     * synchronized da a chave (exclusao mutua); while + wait() espera vaga
     * soltando a chave; notifyAll() acorda quem esperava item.
     * Devolve false se o tempo acabar sem vaga (para a simulacao poder pausar).
     */
    public synchronized boolean offerMonitor(int value, long timeoutMillis) throws InterruptedException {
        if (value <= 0) throw new IllegalArgumentException("Item deve ser positivo");
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        while (size == items.length) {              // cheio: espera vaga
            long left = (deadline - System.nanoTime()) / 1_000_000;
            if (left <= 0) return false;
            wait(left);                             // solta a chave e dorme; ao acordar, reconfere
        }
        enter();
        try {
            items[tail] = value;
            tail = (tail + 1) % items.length;
            size++;
            produced++;
        } finally { inside.decrementAndGet(); }
        notifyAll();                                // acorda o consumidor, se ele dormia
        return true;
    }

    /** Retirada com MONITOR: espelho de offerMonitor. Devolve null se o tempo acabar. */
    public synchronized Integer pollMonitor(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        while (size == 0) {                         // vazio: espera item
            long left = (deadline - System.nanoTime()) / 1_000_000;
            if (left <= 0) return null;
            wait(left);
        }
        int value;
        enter();
        try {
            value = items[head];
            items[head] = 0;
            head = (head + 1) % items.length;
            size--;
            consumed++;
        } finally { inside.decrementAndGet(); }
        notifyAll();                                // acorda o produtor, se ele dormia
        return value;
    }

    /** Marca a entrada na regiao critica; devolve true se ja havia outra thread la. */
    private boolean enter() {
        boolean overlap = inside.incrementAndGet() > 1;
        if (overlap) overlaps.incrementAndGet();
        return overlap;
    }

    private UnsafeResult finish(int value, boolean overlap, int seen, int found, int written) {
        boolean lost = found != seen;       // o contador mudou durante a minha janela
        if (lost) lostUpdates.incrementAndGet();
        return new UnsafeResult(true, value, overlap, lost, seen, found, written);
    }

    /** Pausa que nao abandona a operacao no meio: se interrompida, termina e repassa o aviso. */
    private static void pause(long millis) {
        if (millis <= 0) return;
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public Snapshot snapshot() {
        // Pega as duas protecoes: o mutex (modo semaforos) e o monitor (modo monitor).
        mutex.acquireUninterruptibly();
        try {
            synchronized (this) { return snapshotLocked(); }
        } finally { mutex.release(); }
    }

    private Snapshot snapshotLocked() {
        List<Integer> slots = new ArrayList<>();
        int notes = 0;
        for (int item : items) { slots.add(item); if (item != 0) notes++; }
        return new Snapshot(List.copyOf(slots), size, tail, head, produced, consumed,
                            notes, inside.get(), overlaps.get(), lostUpdates.get());
    }
}
