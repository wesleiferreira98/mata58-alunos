/**
 *Mailbox3s.java
 * A better designed mail box. The state fields are private.
 *
 * Aula de 7/10 (comentarios em portugues):
 * Caixa de correio com MONITOR. E a Mailbox3b com tres mudancas:
 *   1. storeMessage e retrieveMessage sao synchronized: para entrar, a
 *      thread precisa da CHAVE (lock) deste objeto. So uma thread por vez
 *      fica dentro de qualquer um dos dois metodos.
 *   2. retrieveMessage usa while + wait(): sem correio, o consumidor SOLTA
 *      a chave e DORME, em vez de voltar com "How sad, no mail".
 *   3. storeMessage termina com notify(): acorda o consumidor que dormia.
 *
 * O que observar na saida:
 *   - nenhuma mensagem misturada (mudanca 1);
 *   - nenhum "How sad, no mail" (mudanca 2).
 *
 * LIMITE: storeMessage NAO espera a caixa esvaziar. Se os dois produtores
 * guardam antes de o consumidor ler, a primeira mensagem e sobrescrita e
 * se perde. Esta versao garante INTEGRIDADE, nao ENTREGA. A versao sem
 * perdas esta em examples/dia07/caixa/CaixaDeCorreio.java.
 */

public class Mailbox3s {
	// Mesmo estado da 3b, agora protegido pelo monitor deste objeto.
	private boolean youHaveMail = false;	// ha mensagem nao lida?
	private char [] message;
	private final int MAXMESSAGE = 72;
	private final int MAXPROCESSTIME = 7;

	public Mailbox3s() {
		youHaveMail = false;
		message = new char[MAXMESSAGE];
	}

	// synchronized: antes de executar, a thread pega a chave da caixa.
	// Se outro produtor (ou o consumidor) estiver dentro, esta espera na porta.
	public  synchronized void storeMessage(String msg) {

		for(int i = 0; i < msg.length(); i++) {
			message[i] = msg.charAt(i);
			try {
				// A mesma pausa da 3b. Mas Thread.sleep NAO solta a chave:
				// o produtor dorme entre as letras SEGURANDO o monitor, e
				// por isso ninguem consegue escrever no meio da mensagem.
				Thread.sleep((int) (MAXPROCESSTIME * Math.random()));
			}
			catch (InterruptedException e) {}
		}
		for(int i = msg.length(); i < MAXMESSAGE; i++)
			message[i] = ' ';
		youHaveMail = true;
		// Acorda UMA thread que esteja em wait() neste objeto (o consumidor).
		// Ela nao entra na hora: so depois que este metodo terminar e a
		// chave for devolvida. (Aqui nao ha "enquanto cheia: wait()":
		// e por isso que uma mensagem nao lida pode ser sobrescrita.)
		notify();
	}		// ao sair do metodo synchronized, a chave e devolvida


	public  synchronized String retrieveMessage() {

		// Sem correio: wait() SOLTA a chave (para um produtor poder entrar
		// e guardar) e coloca o consumidor para DORMIR, sem gastar CPU.
		// Ao ser acordado, ele precisa pegar a chave de novo e, por causa
		// do while, CONFERE outra vez se ha correio antes de seguir.
		// (Com if, seguiria mesmo num despertar espurio ou se outra thread
		// tivesse mudado o estado antes dele.)
		while (youHaveMail == false) {
			try {
				wait();
			}
			catch (InterruptedException e) {}
		}

		if(youHaveMail) {	// now always true at this point!
							// (o while acima garante: aqui sempre ha correio)
			String msg =  new String(message,0, MAXMESSAGE);
			youHaveMail = false;	// caixa vazia de novo
			return (msg );
		}
		else
			return "How sad, no mail.";		// nunca acontece nesta versao
	}




}
