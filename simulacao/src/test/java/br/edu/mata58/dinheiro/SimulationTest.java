package br.edu.mata58.dinheiro;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Testes sem janela nem dependencias externas. */
public final class SimulationTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void until(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Tempo limite");
            Thread.sleep(5);
        }
    }
    public static void main(String[] args) throws Exception {
        BoundedBuffer buffer = new BoundedBuffer(10);
        check(buffer.poll(0) == null, "Buffer inicialmente vazio");
        for (int i = 1; i <= 10; i++) check(buffer.offer(i, 0), "Dez vagas reais");
        check(!buffer.offer(11, 0), "Nao ultrapassar capacidade");
        for (int i = 1; i <= 10; i++) check(buffer.poll(0) == i, "FIFO");
        check(buffer.snapshot().size() == 0, "Buffer vazio apos consumo");
        for (int i = 1; i <= 25; i++) {
            check(buffer.offer(i, 0), "Reutiliza indice circular");
            check(buffer.poll(0) == i, "FIFO apos dar volta");
        }
        // Interromper quem espera disponibilidade nao deve inventar permissoes.
        Thread blocked = new Thread(() -> {
            try { buffer.poll(10000); throw new AssertionError("Deveria interromper"); }
            catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
        });
        blocked.start(); blocked.interrupt(); blocked.join(1000);
        check(!blocked.isAlive(), "Interrupcao encerra espera");
        check(buffer.offer(100, 0) && buffer.poll(0) == 100, "Buffer segue utilizavel");

        BoundedBuffer concurrent = new BoundedBuffer(10);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> producer = workers.submit(() -> {
                try {
                    for (int i = 1; i <= 3000; i++)
                        check(concurrent.offer(i, 2000), "Produtor nao progride");
                } catch (InterruptedException e) { throw new RuntimeException(e); }
            });
            Future<?> consumer = workers.submit(() -> {
                try {
                    for (int i = 1; i <= 3000; i++) {
                        Integer value = concurrent.poll(2000);
                        check(value != null && value == i, "FIFO concorrente");
                    }
                } catch (InterruptedException e) { throw new RuntimeException(e); }
            });
            producer.get(10, TimeUnit.SECONDS); consumer.get(10, TimeUnit.SECONDS);
            check(concurrent.snapshot().size() == 0, "Sem itens perdidos");
        } finally { workers.shutdownNow(); }

        try (Simulation simulation = new Simulation()) {
            simulation.rates(10, 0.2); simulation.start();
            until(() -> simulation.view().buffer().size() == 10);
            until(() -> simulation.view().producerStatus().contains("Aguardando vaga"));
            simulation.pause();
            var paused = simulation.view().buffer();
            Thread.sleep(200);
            check(paused.equals(simulation.view().buffer()), "Pausa deve congelar estado");
            simulation.reset();
            check(simulation.view().buffer().produced() == 0, "Reset zera contadores");
            simulation.rates(0.2, 10); simulation.start();
            until(() -> simulation.view().consumerStatus().contains("Aguardando item"));
            simulation.pause(); simulation.start(); simulation.close();
            check(simulation.awaitTermination(1000), "Threads devem encerrar");
        }
        // Operacoes sem protecao, usadas por uma thread so: nada de errado acontece.
        BoundedBuffer alone = new BoundedBuffer(3);
        for (int i = 1; i <= 3; i++) check(alone.offerUnprotected(i, 0, 0).done(), "Sem protecao insere");
        check(!alone.offerUnprotected(4, 0, 0).done(), "Sem protecao respeita vagas (semaforo)");
        for (int i = 1; i <= 3; i++) {
            BoundedBuffer.UnsafeResult r = alone.pollUnprotected(0, 0);
            check(r.done() && r.item() == i && !r.overlap() && !r.lostUpdate(), "Sozinho: FIFO e sem corrida");
        }
        check(!alone.pollUnprotected(0, 0).done(), "Sem protecao respeita itens (semaforo)");
        check(alone.snapshot().size() == 0 && alone.snapshot().overlaps() == 0, "Sozinho: contador certo");

        // Modo protegido em ritmo maximo: nunca ha duas threads na regiao critica.
        try (Simulation simulation = new Simulation()) {
            simulation.rates(10, 10); simulation.start();
            Thread.sleep(1500);
            simulation.pause();
            var snap = simulation.view().buffer();
            check(snap.overlaps() == 0 && snap.lostUpdates() == 0, "Com mutex nao ha corrida");
            check(snap.size() == snap.notesInSlots(), "Com mutex o contador bate com as notas");
            simulation.close();
        }

        // Modo sem protecao: a corrida deve ser detectada.
        try (Simulation simulation = new Simulation()) {
            simulation.setProtected(false);
            check(!simulation.view().protectedMode(), "Modo sem protecao ligado");
            simulation.rates(10, 10); simulation.start();
            boolean refused = false;
            try { simulation.setProtected(true); } catch (IllegalStateException e) { refused = true; }
            check(refused, "Nao troca o modo com a simulacao rodando");
            until(() -> simulation.view().buffer().overlaps() > 0);
            until(() -> simulation.view().events().stream().anyMatch(e -> e.startsWith("CORRIDA")));
            simulation.pause();
            simulation.setProtected(true);
            check(simulation.view().protectedMode() && simulation.view().buffer().overlaps() == 0,
                  "Voltar ao modo protegido reinicia o buffer");
            simulation.close();
            check(simulation.awaitTermination(2000), "Threads devem encerrar no modo sem protecao");
        }
        // Buffer como monitor (synchronized, wait, notifyAll), sob concorrencia: FIFO e nada perdido.
        BoundedBuffer monitor = new BoundedBuffer(10);
        check(monitor.pollMonitor(10) == null, "Monitor vazio: retirada espera e desiste");
        ExecutorService monitorWorkers = Executors.newFixedThreadPool(2);
        try {
            Future<?> producer = monitorWorkers.submit(() -> {
                try {
                    for (int i = 1; i <= 3000; i++)
                        check(monitor.offerMonitor(i, 2000), "Produtor do monitor nao progride");
                } catch (InterruptedException e) { throw new RuntimeException(e); }
            });
            Future<?> consumer = monitorWorkers.submit(() -> {
                try {
                    for (int i = 1; i <= 3000; i++) {
                        Integer value = monitor.pollMonitor(2000);
                        check(value != null && value == i, "FIFO no monitor");
                    }
                } catch (InterruptedException e) { throw new RuntimeException(e); }
            });
            producer.get(10, TimeUnit.SECONDS); consumer.get(10, TimeUnit.SECONDS);
            var snap = monitor.snapshot();
            check(snap.size() == 0 && snap.overlaps() == 0, "Monitor: sem itens perdidos e sem corrida");
        } finally { monitorWorkers.shutdownNow(); }

        // Modo monitor na simulacao: as esperas acontecem no wait() do buffer.
        try (Simulation simulation = new Simulation()) {
            simulation.setMode(Simulation.Mode.MONITOR);
            check(simulation.view().mode() == Simulation.Mode.MONITOR, "Modo monitor ligado");
            simulation.rates(10, 0.2); simulation.start();
            until(() -> simulation.view().buffer().size() == 10);
            until(() -> simulation.view().producerStatus().contains("wait()"));
            simulation.pause(); Thread.sleep(300);
            simulation.rates(0.2, 10); simulation.start();
            until(() -> simulation.view().consumerStatus().contains("wait()"));
            simulation.pause(); Thread.sleep(300);
            var snap = simulation.view().buffer();
            check(snap.overlaps() == 0 && snap.lostUpdates() == 0, "Monitor: nenhuma corrida");
            check(snap.size() == snap.notesInSlots(), "Monitor: contador bate com as notas");
            simulation.close();
            check(simulation.awaitTermination(2000), "Threads devem encerrar no modo monitor");
        }
        MailboxTest.run();
        System.out.println("OK: capacidade, FIFO, concorrencia, interrupcao, cheio/vazio, pausa, reset, encerramento,"
                + " monitor, deteccao de corrida sem mutex e Mailbox (3b, 3s, monitor completo, semaforos)");
    }
}
