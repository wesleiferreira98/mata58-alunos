package br.edu.mata58.dinheiro;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Testes da aba Mailbox, sem janela. Chamados por SimulationTest.main. */
final class MailboxTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void until(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Tempo limite");
            Thread.sleep(10);
        }
    }

    static void run() throws Exception {
        // Sem sincronizacao (Mailbox3b): as letras de Dave e Bill se misturam.
        try (MailboxSimulation s = new MailboxSimulation()) {
            s.setMode(Mailbox.Mode.UNSYNCHRONIZED);
            s.timing(5, 0, 50);
            s.start();
            until(() -> s.view().mailbox().overlaps() > 0);
            until(() -> s.view().mailbox().mixed() + s.view().mailbox().cut() > 0);
            s.close();
            check(s.awaitTermination(2000), "Mailbox 3b: threads devem encerrar");
        }

        // Monitor do professor (Mailbox3s): nada se mistura, mas mensagens se perdem.
        try (MailboxSimulation s = new MailboxSimulation()) {
            s.setMode(Mailbox.Mode.PROFESSOR);
            s.timing(2, 0, 300);
            s.start();
            until(() -> s.view().mailbox().lost() > 0);
            s.pause();
            Thread.sleep(200);
            var m = s.view().mailbox();
            check(m.mixed() == 0 && m.cut() == 0 && m.overlaps() == 0 && m.partialReads() == 0,
                  "Mailbox 3s: synchronized impede a mistura");
            s.close();
        }

        // Monitor completo e semaforos: nada se mistura e nada se perde.
        for (Mailbox.Mode mode : new Mailbox.Mode[]{Mailbox.Mode.FULL_MONITOR, Mailbox.Mode.SEMAPHORES}) {
            try (MailboxSimulation s = new MailboxSimulation()) {
                s.setMode(mode);
                // Descanso pequeno: com descanso zero, um produtor pode ganhar a caixa sempre
                // (inanicao: monitores e semaforos do Java nao garantem ordem de chegada).
                s.timing(1, 30, 20);
                s.start();
                until(() -> s.view().mailbox().intact() >= 20);
                s.pause();
                Thread.sleep(300);
                var m = s.view().mailbox();
                long sent = m.sentDave() + m.sentBill();
                check(m.mixed() == 0 && m.cut() == 0 && m.lost() == 0 && m.overlaps() == 0,
                      mode + ": nada misturado nem perdido");
                check(sent - m.intact() <= 1, mode + ": toda mensagem enviada e recebida (no maximo uma na caixa)");
                check(m.sentDave() > 0 && m.sentBill() > 0, mode + ": os dois produtores progridem");
                s.close();
                check(s.awaitTermination(2000), mode + ": threads devem encerrar");
            }
        }

        // Trocar o modo com a simulacao rodando e recusado.
        try (MailboxSimulation s = new MailboxSimulation()) {
            s.start();
            boolean refused = false;
            try { s.setMode(Mailbox.Mode.PROFESSOR); } catch (IllegalStateException e) { refused = true; }
            check(refused, "Mailbox: nao troca o modo rodando");
        }
    }
}
