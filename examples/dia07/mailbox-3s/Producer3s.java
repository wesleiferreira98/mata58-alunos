/**
 * Producer3s.java
 * This is a source of 2 line messages to the mail box. There is a
 * delay between the two strings. The 100 ms is arbitrary.
 * If the delay is only 10 ms between the 2 sections fo the message
 * then the mail is not mixed up.
 * See also Consumer1.java and ThreadSync0.java
 *
 * Aula de 7/10: um PRODUTOR. Igual ao Producer3b; so a caixa mudou.
 * Para sempre: guarda uma mensagem e descansa um tempo aleatorio.
 * Agora, se a caixa estiver ocupada por outra thread, storeMessage
 * espera a chave (synchronized) antes de comecar a escrever.
 */


public class Producer3s extends Thread {

	private final int DELAY = 131;		// descanso maximo (ms) entre duas mensagens
	private Mailbox3s myMailbox;		// a caixa COMPARTILHADA

	private String msg;					// o que este produtor diz
	private String name;				// "Dave" ou "Bill"

	public Producer3s (Mailbox3s box, String name, String msg) {
		this.msg = msg;
		this.name = name;
		myMailbox = box;
	}

	public void run() {
		while(true) {		// laco infinito
		try {
				// Fica bloqueado aqui enquanto outra thread tiver a chave da caixa.
				myMailbox.storeMessage("My name is, " + name + ". " +
										"I say, " + msg);
				// O descanso fica FORA do metodo synchronized: aqui o produtor
				// ja devolveu a chave, e as outras threads podem usar a caixa.
				sleep((int) (DELAY * Math.random()));
			} catch (InterruptedException e) {}
		}
	}
}
