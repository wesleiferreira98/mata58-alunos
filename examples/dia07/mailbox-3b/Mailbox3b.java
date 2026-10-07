/**
 *Mailbox3b.java
 * Objects of the Mailbox3b class are shared by Producer and
 * Consumer objects. Producers send messages to the mailbox at
 * random intervals. Consumers collect the mail at fixed intervals.
 *
 * This mailbox does not work because there is no coordination.
 * To fix you need the Java synchronization facility.
 * See also ThreadSync3b.java, Producer3b.java and Consumer3b.java.
 * The synchronized version is in Mailbox3g.java and Mailbox3s.java.
 *
 * Aula de 7/10 (comentarios em portugues):
 * Caixa de correio SEM sincronizacao. Um unico objeto desta classe e
 * compartilhado por dois produtores (Dave e Bill) e um consumidor.
 *
 * O que observar:
 *   1. Os dois produtores escrevem no MESMO array message, letra por letra.
 *   2. Entre uma letra e outra, o produtor dorme alguns milissegundos;
 *      nesse intervalo, o outro produtor pode escrever por cima.
 *   3. Resultado: mensagens misturadas, como "Het dog!" ou "Hello, worl".
 *      E a condicao de corrida da aula, agora em Java.
 */

public class Mailbox3b {
	// ESTADO COMPARTILHADO: os tres threads (2 produtores + 1 consumidor)
	// mexem nestes campos ao mesmo tempo, sem nenhuma protecao.
	private boolean youHaveMail;	// no longer public.
								// state variables should be private!
								// (true = ha uma mensagem esperando para ser lida)
	private char [] message;		// a mensagem, guardada letra por letra
	private final int MAXMESSAGE = 72;		// tamanho fixo do array
	private final int MAXPROCESSTIME = 7;	// pausa maxima (ms) entre duas letras


	public Mailbox3b() {
		youHaveMail = false;					// a caixa comeca vazia
		message = new char[MAXMESSAGE];
	}

	// Chamado pelos PRODUTORES. Nao e synchronized: Dave e Bill podem estar
	// aqui dentro ao mesmo tempo. Este metodo inteiro e uma REGIAO CRITICA
	// desprotegida.
	public  void storeMessage(String msg) {

		// Copia a mensagem uma letra por vez, a partir da posicao 0.
		for(int i = 0; i < msg.length(); i++) {
			message[i] = msg.charAt(i);
			try {
				// Pausa aleatoria de 0 a 6 ms entre as letras. Enquanto este
				// produtor dorme, o outro pode ser escalonado e escrever as
				// SUAS letras nas mesmas posicoes. Quanto maior MAXPROCESSTIME,
				// maior essa janela, e mais mensagens saem misturadas.
				Thread.sleep((int) (MAXPROCESSTIME * Math.random()));
			}
			catch (InterruptedException e) {}	// interrupcao ignorada
		}
		// Completa o resto do array com espacos (apaga sobras de mensagens maiores).
		for(int i = msg.length(); i < MAXMESSAGE; i++)
			message[i] = ' ';
		youHaveMail = true;		// avisa que ha correio (mas ninguem e acordado:
								// o consumidor so descobre quando perguntar de novo)
	}


	// Chamado pelo CONSUMIDOR, a cada 20 ms. Tambem nao e synchronized:
	// pode ler o array enquanto um produtor ainda esta escrevendo nele.
	public String retrieveMessage() {

		if(youHaveMail) {
			// Le as 72 letras do jeito que estiverem AGORA, talvez pela metade.
			String msg =  new String(message,0, 72);
			youHaveMail = false;	// marca a caixa como vazia
			return (msg );
		}
		else {
			// Caixa vazia: em vez de esperar, volta de maos vazias.
			// (Na versao 3s, o consumidor DORME com wait() ate chegar correio.)
			return "How sad, no mail ..";
		}
	}
}
