/**
 * Producer3b.java
 * Produces short string messages at random intervals.
 * See also Mailbox3b.java, ThreadSync3b.java, and Consumer3b.java
 *
 * Aula de 7/10: um PRODUTOR. Cada objeto desta classe e uma thread
 * (extends Thread) que, para sempre, guarda uma mensagem na caixa
 * compartilhada e depois descansa um tempo aleatorio.
 */

public class Producer3b extends Thread {

	private final int DELAY = 131;		// descanso maximo (ms) entre duas mensagens
																	// if 100 messages mixed up.
	private Mailbox3b myMailbox;	// a caixa COMPARTILHADA (a mesma para todos)
	private String msg;				// o que este produtor diz ("Hello, world." ou "Hot dog!")
	private String name;			// quem e este produtor ("Dave" ou "Bill")

	public Producer3b (Mailbox3b box, String name, String msg) {
		this.msg = msg;
		this.name = name;
		myMailbox = box;
	}

	// run() e o codigo que a thread executa depois de start().
	public void run() {
		while(true) {		// laco infinito: o programa nunca termina sozinho
		try {
				// Guarda "My name is, Dave. I say, Hello, world." na caixa.
				// Na 3b, isso pode acontecer AO MESMO TEMPO que o outro produtor.
				myMailbox.storeMessage("My name is, " + name + ". " +
										"I say, " + msg);
				// Descansa de 0 a 130 ms antes da proxima mensagem.
				sleep((int) (DELAY * Math.random()));
			} catch (InterruptedException e) {}
		}
	}
}
