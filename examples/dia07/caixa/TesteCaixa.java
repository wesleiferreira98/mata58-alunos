/**
 * Aula de 7/10: dois produtores e um consumidor usando CaixaDeCorreio.
 * Cada produtor envia N mensagens; o consumidor confere se TODAS chegaram.
 * (Na Mailbox3s, algumas se perderiam por sobrescrita.)
 *
 * Compile e execute (na raiz do repositorio):
 *   javac -d build/caixa examples/dia07/caixa/*.java
 *   java -cp build/caixa TesteCaixa
 */
public class TesteCaixa {
    public static void main(String[] args) throws InterruptedException {
        CaixaDeCorreio caixa = new CaixaDeCorreio();
        final int n = 20000;
        final int[] dave = {0}, bill = {0};

        Thread p1 = new Thread(() -> {
            try { for (int i = 0; i < n; i++) caixa.guardar("Dave " + i); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread p2 = new Thread(() -> {
            try { for (int i = 0; i < n; i++) caixa.guardar("Bill " + i); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread consumidor = new Thread(() -> {
            try {
                for (int i = 0; i < 2 * n; i++) {
                    String msg = caixa.retirar();
                    if (msg.startsWith("Dave")) dave[0]++; else bill[0]++;
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        p1.start(); p2.start(); consumidor.start();
        p1.join(); p2.join(); consumidor.join();   // espera as tres threads terminarem
        System.out.println("Recebidas de Dave: " + dave[0] + " | de Bill: " + bill[0]
                + " (esperado: " + n + " de cada)");
    }
}
