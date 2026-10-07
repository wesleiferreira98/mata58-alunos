/**
 * Consumer3s.java
 * Works with Mailbox3g.java. Same as Consumer3b.java with renamed
 * variables
 *
 * Aula de 7/10: o CONSUMIDOR. Quase igual ao Consumer3b, com uma linha a
 * mais: imprime "Looking for my mail .." antes de cada consulta.
 * A diferenca de comportamento vem da caixa: retrieveMessage() agora
 * so volta quando HA correio. Por isso, na saida, depois de cada
 * "Looking for my mail .." vem sempre uma mensagem, e nunca "How sad".
 */

public class Consumer3s extends Thread {
	private Mailbox3s myMailbox;		// a mesma caixa dos produtores
	private final int CHECKTIME = 20;	// intervalo (ms) entre duas consultas

	public Consumer3s(Mailbox3s box) {
		myMailbox = box;
	}

	public void run () {
		while(true) {		// laco infinito
			System.out.println("Looking for my mail ..");
			// Se a caixa estiver vazia, o consumidor DORME dentro de
			// retrieveMessage() (wait), sem gastar CPU, ate um produtor
			// guardar uma mensagem e chamar notify().
			System.out.println(myMailbox.retrieveMessage());
			try {
				Thread.sleep(CHECKTIME);	// espera 20 ms antes da proxima consulta
			}
			catch (InterruptedException e) {}

		}
	}
}
