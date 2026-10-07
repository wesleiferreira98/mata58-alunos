package br.edu.mata58.dinheiro;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Roda o exemplo da Mailbox: dois produtores (Dave e Bill) e um consumidor,
 * todos compartilhando UMA caixa. Mesmo ciclo de vida da simulacao do dinheiro:
 * Iniciar, Pausar (congela ate no meio de uma letra) e Reiniciar.
 *
 * As operacoes na caixa rodam FORA do gate, para que as threads possam de fato
 * se atropelar (modo sem sincronizacao) ou dormir no wait() da caixa (monitor).
 */
public final class MailboxSimulation implements AutoCloseable {
    public record View(Simulation.State state, Mailbox.Snapshot mailbox,
                       long letterPauseMillis, long producerRestMillis, long consumerCheckMillis) {}

    /** Espera maxima dentro de wait()/semaforo antes de reconferir pausa e reinicio. */
    private static final long WAIT_SLICE_MILLIS = 100;

    private final Object gate = new Object();
    private Simulation.State state = Simulation.State.READY;
    private Mailbox mailbox = new Mailbox(Mailbox.Mode.UNSYNCHRONIZED);
    private long letterPause = 30, producerRest = 1500, consumerCheck = 1000;   // em milissegundos
    private final Thread dave = new Thread(() -> produce(Mailbox.DAVE), "Dave");
    private final Thread bill = new Thread(() -> produce(Mailbox.BILL), "Bill");
    private final Thread consumer = new Thread(this::consume, "consumidor");

    /** Pausa entre letras que respeita Pausar: equivale ao sleep(MAXPROCESSTIME * random). */
    private final Mailbox.Pacer pacer = () -> pausableSleep(ThreadLocalRandom.current().nextLong(letterPause + 1));

    public MailboxSimulation() {
        for (Thread t : new Thread[]{dave, bill, consumer}) { t.setDaemon(true); t.start(); }
    }

    public void start() {
        synchronized (gate) {
            ensureOpen();
            state = Simulation.State.RUNNING;
            gate.notifyAll();
        }
    }

    public void pause() {
        synchronized (gate) {
            ensureOpen();
            if (state == Simulation.State.RUNNING) state = Simulation.State.PAUSED;
            gate.notifyAll();
        }
    }

    public void reset() { setMode(mailbox.mode()); }

    /** Troca o modo (so com a simulacao parada) e comeca com uma caixa nova. */
    public void setMode(Mailbox.Mode mode) {
        synchronized (gate) {
            ensureOpen();
            if (state == Simulation.State.RUNNING)
                throw new IllegalStateException("Pause a simulacao antes de trocar o modo");
            state = Simulation.State.READY;
            mailbox = new Mailbox(mode);
            gate.notifyAll();
        }
    }

    public void timing(long letterPauseMillis, long producerRestMillis, long consumerCheckMillis) {
        synchronized (gate) {
            letterPause = Math.max(0, letterPauseMillis);
            producerRest = Math.max(0, producerRestMillis);
            consumerCheck = Math.max(10, consumerCheckMillis);
            gate.notifyAll();
        }
    }

    private void produce(int who) {
        try {
            while (true) {
                Mailbox target = awaitRunning();
                boolean done = target.store(who, pacer, WAIT_SLICE_MILLIS);
                if (!done || target != current()) continue;     // esperou demais, ou houve reinicio
                target.setActivity(who, Mailbox.Activity.RESTING);
                // Descanso de 0 a DELAY, como o sleep((int)(DELAY * Math.random())) do professor.
                pausableSleep(ThreadLocalRandom.current().nextLong(producerRest + 1));
            }
        } catch (InterruptedException closed) {
            Thread.currentThread().interrupt();
        }
    }

    private void consume() {
        try {
            while (true) {
                Mailbox target = awaitRunning();
                Mailbox.Retrieval r = target.retrieve(WAIT_SLICE_MILLIS);
                if (r.outcome() == Mailbox.Outcome.WAITING || target != current()) continue;
                pausableSleep(consumerCheck);                   // CHECKTIME do professor
            }
        } catch (InterruptedException closed) {
            Thread.currentThread().interrupt();
        }
    }

    private Mailbox current() { synchronized (gate) { return mailbox; } }

    private Mailbox awaitRunning() throws InterruptedException {
        synchronized (gate) {
            while (state != Simulation.State.RUNNING) {
                if (state == Simulation.State.CLOSED) throw new InterruptedException();
                gate.wait();
            }
            return mailbox;
        }
    }

    /** Dorme o tempo pedido, mas congela enquanto estiver pausado. */
    private void pausableSleep(long millis) throws InterruptedException {
        synchronized (gate) {
            long remaining = millis;
            while (true) {
                if (state == Simulation.State.CLOSED) throw new InterruptedException();
                if (state != Simulation.State.RUNNING) { gate.wait(); continue; }
                if (remaining <= 0) return;
                long before = System.nanoTime();
                gate.wait(remaining);
                remaining -= (System.nanoTime() - before) / 1_000_000;
            }
        }
    }

    public View view() {
        Mailbox box;
        Simulation.State s;
        long lp, pr, cc;
        synchronized (gate) { box = mailbox; s = state; lp = letterPause; pr = producerRest; cc = consumerCheck; }
        return new View(s, box.snapshot(), lp, pr, cc);   // snapshot sem trava: nunca prende a tela
    }

    private void ensureOpen() {
        if (state == Simulation.State.CLOSED) throw new IllegalStateException("Simulacao encerrada");
    }

    @Override public void close() {
        synchronized (gate) {
            state = Simulation.State.CLOSED;
            gate.notifyAll();
        }
        for (Thread t : new Thread[]{dave, bill, consumer}) t.interrupt();
    }

    public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        for (Thread t : new Thread[]{dave, bill, consumer}) {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) t.join(Math.max(1, remaining / 1_000_000));
        }
        return !dave.isAlive() && !bill.isAlive() && !consumer.isAlive();
    }
}
