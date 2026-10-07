package br.edu.mata58.dinheiro;

import java.util.ArrayDeque;
import java.util.List;

/** Ciclo de vida cooperativo. Nunca suspende uma thread segurando um semaforo. */
public final class Simulation implements AutoCloseable {
    public enum State { READY, RUNNING, PAUSED, CLOSED }

    /** Como o buffer e protegido. */
    public enum Mode {
        SEMAPHORES("Semáforos"),        // empty, full e mutex (o padrao)
        MONITOR("Monitor"),             // synchronized, wait() e notifyAll(), sem semaforos
        UNPROTECTED("Sem proteção");    // semaforos sem o mutex: a corrida aparece
        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record View(BoundedBuffer.Snapshot buffer, State state, String producerStatus,
                       String consumerStatus, List<String> events, Mode mode) {
        public boolean protectedMode() { return mode != Mode.UNPROTECTED; }
    }

    /** Espera maxima dentro do wait() do monitor antes de reconferir pausa e reinicio. */
    private static final long MONITOR_WAIT_MILLIS = 100;

    /** Pausa entre ler e escrever o contador no modo sem protecao (a janela da corrida). */
    public static final long RACE_WINDOW_MILLIS = 250;

    private final Object gate = new Object();
    private BoundedBuffer buffer = new BoundedBuffer(10);
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private State state = State.READY;
    private String producerStatus = "Pronto", consumerStatus = "Pronto";
    private double producerRate = 2, consumerRate = 2;
    private int nextItem = 1;
    private long generation;
    private Mode mode = Mode.SEMAPHORES;
    private final Thread producer = new Thread(() -> work(true), "produtor");
    private final Thread consumer = new Thread(() -> work(false), "consumidor");

    public Simulation() {
        producer.setDaemon(true);
        consumer.setDaemon(true);
        producer.start();
        consumer.start();
    }

    public void start() {
        synchronized (gate) {
            ensureOpen();
            if (state != State.RUNNING) {
                state = State.RUNNING;
                generation++;
                log("Simulacao em execucao: modo " + modeName());
                gate.notifyAll();
            }
        }
    }

    public void pause() {
        synchronized (gate) {
            ensureOpen();
            if (state == State.RUNNING) {
                state = State.PAUSED;
                generation++;
                producerStatus = consumerStatus = "Pausado";
                log("Pausa: buffer preservado");
                gate.notifyAll();
            }
        }
    }

    public void reset() {
        synchronized (gate) {
            ensureOpen();
            state = State.READY;
            generation++;
            buffer = new BoundedBuffer(10);
            nextItem = 1;
            producerStatus = consumerStatus = "Pronto";
            events.clear();
            log("Buffer reiniciado: modo " + modeName());
            gate.notifyAll();
        }
    }

    /**
     * Troca o modo de protecao. So pode ser trocado com a simulacao parada,
     * e a troca reinicia o buffer para comecar a comparacao do zero.
     */
    public void setMode(Mode newMode) {
        synchronized (gate) {
            ensureOpen();
            if (state == State.RUNNING)
                throw new IllegalStateException("Pause a simulacao antes de trocar o modo");
            mode = newMode;
        }
        reset();
    }

    /** Atalho: true = semaforos com mutex; false = sem protecao. */
    public void setProtected(boolean on) { setMode(on ? Mode.SEMAPHORES : Mode.UNPROTECTED); }

    private String modeName() {
        return switch (mode) {
            case SEMAPHORES -> "semaforos";
            case MONITOR -> "monitor";
            case UNPROTECTED -> "SEM PROTECAO (mutex desligado)";
        };
    }

    public void rates(double production, double consumption) {
        if (!Double.isFinite(production) || !Double.isFinite(consumption)
                || production < 0.2 || production > 10 || consumption < 0.2 || consumption > 10)
            throw new IllegalArgumentException("Velocidades entre 0.2 e 10 itens/s");
        synchronized (gate) {
            ensureOpen();
            producerRate = production;
            consumerRate = consumption;
            gate.notifyAll();
        }
    }

    private void work(boolean producing) {
        try {
            while (true) {
                BoundedBuffer target;
                int item;
                synchronized (gate) {
                    while (state != State.RUNNING) {
                        if (state == State.CLOSED) return;
                        gate.wait();
                    }
                    if (mode == Mode.SEMAPHORES) {
                        // Modo semaforos: igual ao original, tudo serializado pelo gate.
                        boolean changed;
                        if (producing) {
                            changed = buffer.offer(nextItem, 0);
                            producerStatus = changed ? "Produzindo" : "Aguardando vaga: buffer cheio";
                            if (changed) log("Produziu nota #" + nextItem++);
                        } else {
                            Integer value = buffer.poll(0);
                            changed = value != null;
                            consumerStatus = changed ? "Consumindo" : "Aguardando item: buffer vazio";
                            if (changed) log("Consumiu nota #" + value);
                        }
                        if (!changed) {
                            gate.wait(); // Libera gate; outra thread pode alterar a disponibilidade.
                            continue;
                        }
                        gate.notifyAll();
                        waitInterval(producing);
                        continue;
                    }
                    target = buffer;
                    item = nextItem;
                    if (mode == Mode.MONITOR) {
                        // Modo monitor: a espera acontece no wait() do PROPRIO buffer,
                        // entao a operacao roda fora do gate (senao ninguem entraria).
                        gate.notifyAll();
                    } else {
                        // Modo sem protecao: prepara a operacao e SAI do gate, para que
                        // produtor e consumidor possam estar na regiao critica ao mesmo tempo.
                        if (producing) producerStatus = "Na região crítica, SEM mutex";
                        else consumerStatus = "Na região crítica, SEM mutex";
                    }
                }

                if (mode == Mode.MONITOR) {
                    boolean done;
                    Integer taken = null;
                    if (producing) done = target.offerMonitor(item, MONITOR_WAIT_MILLIS);
                    else { taken = target.pollMonitor(MONITOR_WAIT_MILLIS); done = taken != null; }
                    synchronized (gate) {
                        if (target != buffer || state == State.CLOSED) continue;  // houve reset no meio
                        if (!done) {
                            // O wait() do monitor acabou sem vaga ou sem item: volta ao inicio
                            // do laco, que respeita pausa e reinicio, e espera de novo.
                            if (state == State.RUNNING) {
                                if (producing) producerStatus = "Aguardando vaga: dormindo em wait() no monitor";
                                else consumerStatus = "Aguardando item: dormindo em wait() no monitor";
                            }
                            continue;
                        }
                        if (producing) {
                            producerStatus = "Produzindo";
                            log("Produziu nota #" + nextItem++ + " (notifyAll acorda quem espera item)");
                        } else {
                            consumerStatus = "Consumindo";
                            log("Consumiu nota #" + taken + " (notifyAll acorda quem espera vaga)");
                        }
                        gate.notifyAll();
                        waitInterval(producing);
                    }
                    continue;
                }

                BoundedBuffer.UnsafeResult result = producing
                        ? target.offerUnprotected(item, 0, RACE_WINDOW_MILLIS)
                        : target.pollUnprotected(0, RACE_WINDOW_MILLIS);

                synchronized (gate) {
                    if (target != buffer || state == State.CLOSED) continue;  // houve reset no meio
                    if (!result.done()) {
                        if (producing) producerStatus = "Aguardando vaga: buffer cheio";
                        else consumerStatus = "Aguardando item: buffer vazio";
                        // Espera com tempo limite: o aviso da outra thread pode ter vindo
                        // antes de entrarmos aqui (despertar perdido), entao reconferimos.
                        gate.wait(50);
                        continue;
                    }
                    if (producing) {
                        producerStatus = "Produzindo";
                        log("Produziu nota #" + nextItem++);
                    } else {
                        consumerStatus = "Consumindo";
                        log("Consumiu nota #" + result.item());
                    }
                    if (result.overlap())
                        log("CORRIDA: produtor e consumidor na regiao critica ao mesmo tempo");
                    if (result.lostUpdate())
                        log("CORRIDA: " + (producing ? "produtor" : "consumidor") + " leu size=" + result.seen()
                            + ", a outra thread mudou para " + result.found()
                            + " e ele escreveu " + result.written() + ": uma atualizacao se perdeu");
                    gate.notifyAll();
                    waitInterval(producing);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Intervalo da velocidade escolhida; deve ser chamado com o gate adquirido. */
    private void waitInterval(boolean producing) throws InterruptedException {
        // Notificacoes de disponibilidade nao encurtam o intervalo de velocidade.
        double rate = producing ? producerRate : consumerRate;
        long currentGeneration = generation;
        long deadline = System.nanoTime() + (long)(1_000_000_000 / rate);
        while (state == State.RUNNING && generation == currentGeneration) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            gate.wait(Math.max(1, remaining / 1_000_000));
        }
    }

    public View view() {
        synchronized (gate) {
            return new View(buffer.snapshot(), state, producerStatus, consumerStatus,
                            List.copyOf(events), mode);
        }
    }

    private void log(String event) {
        events.addFirst(event);
        while (events.size() > 12) events.removeLast();
    }

    private void ensureOpen() {
        if (state == State.CLOSED) throw new IllegalStateException("Simulacao encerrada");
    }

    @Override public void close() {
        synchronized (gate) {
            state = State.CLOSED;
            producerStatus = consumerStatus = "Encerrado";
            gate.notifyAll();
        }
        producer.interrupt();
        consumer.interrupt();
    }

    public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        for (Thread thread : new Thread[]{producer, consumer}) {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) thread.join(Math.max(1, remaining / 1_000_000));
        }
        return !producer.isAlive() && !consumer.isAlive();
    }
}
