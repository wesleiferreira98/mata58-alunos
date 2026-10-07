/**
 * ThreadSync3b.java
 * Illustrates confusion caused when unsynchronized thread access.
 *
 * Sample output is in ThreadSync3b.txt. Notice that once in a while the
 * messages get mixed up. Dave appears to send Bill's message and
 * vice versa. Other errors abound. You can make this worse
 * by increaseing the MAXPROCESSTIME in the Mailbox3b.java file.
 * Other files in this group: Mailbox3b.java, Producer3b.java and
 * Consumer3b.java. (Note that all the Consumer, Producer and
 * ThreadSync classes are the same for versions 3b, 3g, and 3s
 * except for variable name changes to make them work with the
 * different versions of the Mailbox class.)
 * This version uses an unsynchronized mailbox. DOES NOT WORK
 * PROPERLY!!!
 *
 * Aula de 7/10: o programa principal. Cria UMA caixa e TRES threads que a
 * compartilham. Depois disso, a main termina, mas o programa continua
 * rodando enquanto as threads rodarem (para sempre: use Ctrl + C ou timeout).
 *
 * Compile e execute (na raiz do repositorio):
 *   javac -d build/java-3b examples/dia07/mailbox-3b/*.java
 *   timeout 5 java -cp build/java-3b ThreadSync3b
 */

public class ThreadSync3b {

	public static void main(String [] args) {
		// UM objeto compartilhado: e ele que sofre a condicao de corrida.
		Mailbox3b mailbox = new Mailbox3b();
		// Tres threads, todas recebendo a MESMA caixa.
		Consumer3b cons1 = new Consumer3b(mailbox);
		Producer3b prod1 = new Producer3b( mailbox, "Dave", "Hello, world.");
		Producer3b prod2 = new Producer3b(mailbox, "Bill", "Hot dog!");
		// start() cria a thread de verdade e faz ela executar run().
		// (Chamar run() direto NAO criaria thread: rodaria aqui mesmo, na main.)
		cons1.start();
		prod1.start();
		prod2.start();
	}
}
