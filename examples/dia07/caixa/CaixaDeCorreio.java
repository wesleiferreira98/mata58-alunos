/**
 * Aula de 7/10: caixa de correio de uma posicao, SEM perdas.
 * E o produtor-consumidor com N = 1, escrito com monitor em vez de semaforos.
 * Diferencas para a Mailbox3s: guardar() ESPERA a caixa esvaziar, e os dois
 * metodos usam notifyAll(), porque ha dois produtores e um consumidor.
 */
public class CaixaDeCorreio {
    private String mensagem;          // a unica posicao
    private boolean cheia = false;    // ha mensagem nao lida?

    public synchronized void guardar(String msg) throws InterruptedException {
        while (cheia)                 // caixa ocupada: solta a chave e espera
            wait();
        mensagem = msg;
        cheia = true;
        notifyAll();                  // acorda quem espera (o consumidor, em especial)
    }

    public synchronized String retirar() throws InterruptedException {
        while (!cheia)                // caixa vazia: solta a chave e espera
            wait();
        String msg = mensagem;
        cheia = false;
        notifyAll();                  // acorda quem espera (os produtores, em especial)
        return msg;
    }
}
