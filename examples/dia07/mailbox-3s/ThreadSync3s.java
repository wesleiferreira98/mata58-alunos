/**
 * ThreadSync3s.java
 * This version uses Mailbox3s.java and Producers and Consumers
 * as before but with suitable variable renaming.
 * The Mailbox3g class has synchronized methods and uses
 * wait() and notify(). Sample output in ThreadSync3s.txt.
 *
 * Aula de 7/10: o programa principal. Identico ao ThreadSync3b: uma caixa,
 * dois produtores (Dave e Bill) e um consumidor. Toda a diferenca de
 * comportamento esta em Mailbox3s. O programa nunca termina sozinho.
 *
 * Compile e execute (na raiz do repositorio):
 *   javac -d build/java-3s examples/dia07/mailbox-3s/*.java
 *   timeout 5 java -cp build/java-3s ThreadSync3s
 */

public class ThreadSync3s {

	public static void main(String [] args) {
		Mailbox3s mailbox = new Mailbox3s();	// UM objeto compartilhado (o monitor)
		Consumer3s cons1 = new Consumer3s(mailbox);
		Producer3s prod1 = new Producer3s( mailbox, "Dave", "Hello, world.");
		Producer3s prod2 = new Producer3s(mailbox, "Bill", "Hot dog!");
		cons1.start();		// start() cria cada thread e executa o seu run()
		prod1.start();
		prod2.start();
	}
}
