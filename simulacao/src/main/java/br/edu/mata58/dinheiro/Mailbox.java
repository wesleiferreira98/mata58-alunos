package br.edu.mata58.dinheiro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caixa de correio do exemplo do professor (Mailbox3b / Mailbox3s), com quatro
 * modos de protecao. Dois produtores (Dave e Bill) escrevem a mensagem LETRA POR
 * LETRA no mesmo array; um consumidor le o array inteiro.
 *
 *   UNSYNCHRONIZED   Mailbox3b: nada de sincronizacao. As letras se misturam.
 *   PROFESSOR        Mailbox3s: synchronized + while/wait + notify. Mensagens
 *                    integras, mas o produtor nao espera a caixa esvaziar:
 *                    uma mensagem nao lida pode ser sobrescrita (perdida).
 *   FULL_MONITOR     monitor completo: o produtor tambem espera (while cheia: wait)
 *                    e todos usam notifyAll. Integras e sem perdas.
 *   SEMAPHORES       a mesma caixa com semaforos: vaga (1), mensagem (0), mutex (1).
 *
 * Os dados mostrados na tela (letras, autores, atividades, contadores) sao lidos
 * sem trava: e so exibicao, e a interface nunca pode ficar presa esperando a caixa.
 */
public final class Mailbox {
    public enum Mode {
        UNSYNCHRONIZED("Sem sincronização (Mailbox3b)"),
        PROFESSOR("Monitor do professor (Mailbox3s)"),
        FULL_MONITOR("Monitor completo (sem perdas)"),
        SEMAPHORES("Semáforos (vaga, mensagem, mutex)");
        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /** O que cada thread esta fazendo agora (para a tela). */
    public enum Activity {
        IDLE("Pronto"), WAITING_KEY("Esperando a chave da caixa"),
        WAITING_EMPTY("Esperando a caixa esvaziar: dormindo em wait()"),
        WAITING_SLOT("Esperando vaga: dormindo no semáforo"),
        WRITING("Escrevendo"), RESTING("Descansando"),
        WAITING_MAIL_MONITOR("Esperando correio: dormindo em wait()"),
        WAITING_MAIL_SEMAPHORE("Esperando correio: dormindo no semáforo"),
        READING("Lendo"), NO_MAIL("Caixa vazia: \"How sad, no mail ..\"");
        private final String text;
        Activity(String text) { this.text = text; }
        public String text() { return text; }
    }

    /** Como a mensagem chegou ao consumidor. */
    public enum Verdict { INTACT, MIXED, CUT }

    public record Received(String text, List<Integer> writers, Verdict verdict, int author) {}

    public enum Outcome { GOT, NO_MAIL, WAITING }
    public record Retrieval(Outcome outcome, Received message) {}

    public record Snapshot(Mode mode, String text, List<Integer> writers, boolean hasMail, int keyHolder,
                           Activity dave, int daveProgress, Activity bill, int billProgress, Activity consumer,
                           long sentDave, long sentBill, long intact, long mixed, long cut, long lost,
                           long noMail, long overlaps, long partialReads, int writersInside,
                           List<Received> recent, List<String> events) {}

    /** Pausa entre duas letras; a simulacao implementa respeitando Pausar. */
    public interface Pacer { void letter() throws InterruptedException; }

    public static final int MAX = 72;
    public static final int NOBODY = 0, DAVE = 1, BILL = 2, CONSUMER = 3;

    public static String messageOf(int who) {
        return who == DAVE ? "My name is, Dave. I say, Hello, world."
                           : "My name is, Bill. I say, Hot dog!";
    }
    public static String nameOf(int who) {
        return switch (who) { case DAVE -> "Dave"; case BILL -> "Bill"; case CONSUMER -> "consumidor"; default -> "ninguém"; };
    }

    private final Mode mode;
    // Estado compartilhado, como na Mailbox do professor.
    private final char[] message = new char[MAX];
    private final int[] writers = new int[MAX];         // quem escreveu cada letra (so para a tela)
    private volatile boolean hasMail;
    private volatile int unreadAuthor = NOBODY;         // autor da mensagem nao lida (para detectar perdas)

    // Semaforos do modo SEMAPHORES (caixa de uma posicao).
    private final Semaphore slot = new Semaphore(1), mail = new Semaphore(0), mutex = new Semaphore(1);

    // Instrumentacao (sempre correta: atomica).
    private final AtomicInteger writersInside = new AtomicInteger(), readersInside = new AtomicInteger();
    private final AtomicLong sentDave = new AtomicLong(), sentBill = new AtomicLong(), intact = new AtomicLong(),
            mixed = new AtomicLong(), cut = new AtomicLong(), lost = new AtomicLong(), noMail = new AtomicLong(),
            overlaps = new AtomicLong(), partialReads = new AtomicLong();
    private volatile int keyHolder = NOBODY;
    private final Activity[] activity = {Activity.IDLE, Activity.IDLE, Activity.IDLE, Activity.IDLE};
    private final int[] progress = new int[3];
    private final ArrayDeque<Received> recent = new ArrayDeque<>();
    private final ArrayDeque<String> events = new ArrayDeque<>();

    public Mailbox(Mode mode) {
        this.mode = mode;
        java.util.Arrays.fill(message, ' ');
    }

    public Mode mode() { return mode; }

    public void setActivity(int who, Activity a) { activity[who] = a; }

    // ------------------------------------------------------------------
    // Produtor: storeMessage
    // ------------------------------------------------------------------

    /**
     * Guarda a mensagem de Dave ou Bill. Devolve false se, nos modos que esperam
     * a caixa esvaziar, o tempo acabou antes (a simulacao tenta de novo).
     */
    public boolean store(int who, Pacer pacer, long timeoutMillis) throws InterruptedException {
        switch (mode) {
            case UNSYNCHRONIZED -> {            // Mailbox3b: nenhuma protecao
                write(who, pacer);
                return true;
            }
            case PROFESSOR -> {                 // Mailbox3s: synchronized + notify
                activity[who] = Activity.WAITING_KEY;
                synchronized (this) {
                    keyHolder = who;
                    try {
                        write(who, pacer);      // o sleep entre letras acontece COM a chave
                        notify();
                    } finally { keyHolder = NOBODY; }
                }
                return true;
            }
            case FULL_MONITOR -> {              // monitor completo: espera a caixa esvaziar
                activity[who] = Activity.WAITING_KEY;
                synchronized (this) {
                    long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
                    keyHolder = who;
                    try {
                        while (hasMail) {
                            long left = (deadline - System.nanoTime()) / 1_000_000;
                            activity[who] = Activity.WAITING_EMPTY;
                            if (left <= 0) return false;
                            keyHolder = NOBODY;
                            wait(left);         // solta a chave e dorme
                            keyHolder = who;
                        }
                        write(who, pacer);
                        notifyAll();            // acorda o consumidor (e o outro produtor)
                    } finally { keyHolder = NOBODY; }
                }
                return true;
            }
            default -> {                        // SEMAPHORES: vaga, mutex, mensagem
                if (!slot.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    activity[who] = Activity.WAITING_SLOT;
                    return false;
                }
                boolean locked = false, written = false;
                try {
                    activity[who] = Activity.WAITING_KEY;
                    mutex.acquire();
                    locked = true;
                    keyHolder = who;
                    write(who, pacer);
                    written = true;
                } finally {
                    keyHolder = NOBODY;
                    if (locked) mutex.release();
                    if (written) mail.release(); else slot.release();
                }
                return true;
            }
        }
    }

    /** Escreve letra por letra, como o storeMessage do professor. */
    private void write(int who, Pacer pacer) throws InterruptedException {
        String msg = messageOf(who);
        if (writersInside.incrementAndGet() > 1) {
            overlaps.incrementAndGet();
            event("CORRIDA: Dave e Bill escrevendo na caixa ao mesmo tempo");
        }
        activity[who] = Activity.WRITING;
        try {
            for (int i = 0; i < msg.length(); i++) {
                message[i] = msg.charAt(i);
                writers[i] = who;
                progress[who] = i + 1;
                pacer.letter();                 // pausa entre letras (MAXPROCESSTIME)
            }
            for (int i = msg.length(); i < MAX; i++) { message[i] = ' '; writers[i] = NOBODY; }
            if (hasMail) {                      // havia uma mensagem nao lida: foi sobrescrita
                lost.incrementAndGet();
                event("PERDIDA: a mensagem de " + nameOf(unreadAuthor) + " foi sobrescrita por "
                      + nameOf(who) + " antes de ser lida");
            }
            unreadAuthor = who;
            hasMail = true;
            (who == DAVE ? sentDave : sentBill).incrementAndGet();
        } finally {
            writersInside.decrementAndGet();
            progress[who] = 0;
        }
    }

    // ------------------------------------------------------------------
    // Consumidor: retrieveMessage
    // ------------------------------------------------------------------

    public Retrieval retrieve(long timeoutMillis) throws InterruptedException {
        switch (mode) {
            case UNSYNCHRONIZED -> {            // Mailbox3b: se nao ha correio, volta de maos vazias
                if (!hasMail) {
                    noMail.incrementAndGet();
                    activity[CONSUMER] = Activity.NO_MAIL;
                    return new Retrieval(Outcome.NO_MAIL, null);
                }
                return new Retrieval(Outcome.GOT, read());
            }
            case PROFESSOR, FULL_MONITOR -> {   // while (!hasMail) wait()
                activity[CONSUMER] = Activity.WAITING_KEY;
                synchronized (this) {
                    long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
                    keyHolder = CONSUMER;
                    try {
                        while (!hasMail) {
                            long left = (deadline - System.nanoTime()) / 1_000_000;
                            activity[CONSUMER] = Activity.WAITING_MAIL_MONITOR;
                            if (left <= 0) return new Retrieval(Outcome.WAITING, null);
                            keyHolder = NOBODY;
                            wait(left);
                            keyHolder = CONSUMER;
                        }
                        Received r = read();
                        if (mode == Mode.FULL_MONITOR) notifyAll();   // acorda produtor que espera vaga
                        return new Retrieval(Outcome.GOT, r);
                    } finally { keyHolder = NOBODY; }
                }
            }
            default -> {                        // SEMAPHORES
                if (!mail.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    activity[CONSUMER] = Activity.WAITING_MAIL_SEMAPHORE;
                    return new Retrieval(Outcome.WAITING, null);
                }
                boolean locked = false, done = false;
                Received r = null;
                try {
                    activity[CONSUMER] = Activity.WAITING_KEY;
                    mutex.acquire();
                    locked = true;
                    keyHolder = CONSUMER;
                    r = read();
                    done = true;
                } finally {
                    keyHolder = NOBODY;
                    if (locked) mutex.release();
                    if (done) slot.release(); else mail.release();
                }
                return new Retrieval(Outcome.GOT, r);
            }
        }
    }

    /** Le as 72 posicoes do jeito que estiverem e classifica o resultado. */
    private Received read() {
        activity[CONSUMER] = Activity.READING;
        readersInside.incrementAndGet();
        boolean writing = writersInside.get() > 0;
        String text = new String(message);
        List<Integer> who = new ArrayList<>(MAX);
        for (int w : writers) who.add(w);
        hasMail = false;
        unreadAuthor = NOBODY;
        readersInside.decrementAndGet();
        if (writing) {
            partialReads.incrementAndGet();
            event("CORRIDA: o consumidor leu a caixa enquanto um produtor escrevia");
        }

        boolean dave = who.contains(DAVE), bill = who.contains(BILL);
        int author = dave ? DAVE : BILL;
        Verdict verdict;
        if (dave && bill) { verdict = Verdict.MIXED; mixed.incrementAndGet(); }
        else if (text.stripTrailing().equals(messageOf(author))) { verdict = Verdict.INTACT; intact.incrementAndGet(); }
        else { verdict = Verdict.CUT; cut.incrementAndGet(); }
        String shown = text.stripTrailing();
        Received r = new Received(shown, List.copyOf(who.subList(0, shown.length())), verdict, author);
        synchronized (recent) {
            recent.addFirst(r);
            while (recent.size() > 8) recent.removeLast();
        }
        return r;
    }

    private void event(String text) {
        synchronized (events) {
            events.addFirst(text);
            while (events.size() > 12) events.removeLast();
        }
    }

    public Snapshot snapshot() {
        List<Integer> who = new ArrayList<>(MAX);
        for (int w : writers) who.add(w);
        List<Received> rec;
        synchronized (recent) { rec = List.copyOf(recent); }
        List<String> ev;
        synchronized (events) { ev = List.copyOf(events); }
        return new Snapshot(mode, new String(message), List.copyOf(who), hasMail, keyHolder,
                activity[DAVE], progress[DAVE], activity[BILL], progress[BILL], activity[CONSUMER],
                sentDave.get(), sentBill.get(), intact.get(), mixed.get(), cut.get(), lost.get(),
                noMail.get(), overlaps.get(), partialReads.get(), writersInside.get(), rec, ev);
    }
}
