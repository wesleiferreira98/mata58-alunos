/**
 * Consumer3b.java
 * A consumer class which gets messages from a mailbox and prints
 * them to stdout.	Looks for messages every CHECKTIME ms.
 * Used with Mailbox3b.java. Producer3b.java and ThreadSync3b.java.
 * (The classes ...3b, ...3g and ...3s for the the Consumers,
 * Producers and ThreadSyncs are all the same except for variable
 * name changes.)
 *
 * Aula de 7/10: o CONSUMIDOR. Uma thread que, para sempre, olha a caixa
 * a cada 20 ms e imprime o que encontrar. Se nao ha correio, imprime
 * "How sad, no mail .." (a caixa 3b nao faz o consumidor esperar).
 */

public class Consumer3b extends Thread {
	private Mailbox3b myMailbox;		// a mesma caixa dos produtores
	private final int CHECKTIME = 20;	// intervalo (ms) entre duas consultas

	public Consumer3b(Mailbox3b box) {
		myMailbox = box;
	}

	public void run () {
		while(true) {		// laco infinito
			// Pergunta "tem correio?" e imprime a resposta, seja ela qual for.
			// Isto e parecido com espera ocupada: o consumidor fica consultando
			// a caixa repetidamente, mesmo quando ela esta vazia.
			System.out.println(myMailbox.retrieveMessage());
			try {
				Thread.sleep(CHECKTIME);	// espera 20 ms e pergunta de novo
			}
			catch (InterruptedException e) {}

		}
	}
}
