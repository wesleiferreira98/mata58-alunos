package br.edu.mata58.dinheiro;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Aba "Mailbox": o exemplo do professor (Mailbox3b e Mailbox3s) na tela.
 * Cada letra da caixa aparece na cor de quem a escreveu: Dave em azul, Bill em
 * laranja. Uma mensagem com as duas cores e a condicao de corrida acontecendo.
 * Como na outra aba, a interface so le snapshots; as threads nunca mexem na tela.
 */
final class MailboxPane {
    private static final Color DAVE = Color.web("#1f6fb2"), BILL = Color.web("#c0560f"),
            EMPTY = Color.web("#b4c3d1");

    private final MailboxSimulation simulation = new MailboxSimulation();
    private final ScrollPane root;
    private final Label stateLabel = new Label(), modeInfo = new Label(), raceAlert = new Label(),
            lossAlert = new Label(), boxStatus = new Label(), counters = new Label(),
            daveStatus = new Label(), billStatus = new Label(), consumerStatus = new Label();
    private final TextFlow box = new TextFlow();
    private final VBox boxCard, received = new VBox(6);
    private final ProgressBar daveProgress = new ProgressBar(0), billProgress = new ProgressBar(0);
    private final Button start = new Button("Iniciar"), pause = new Button("Pausar");
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final RadioButton[] modes = new RadioButton[Mailbox.Mode.values().length];
    private final Slider letterPause = slider(0, 100, 30), producerRest = slider(0, 3000, 1500),
            consumerCheck = slider(0, 3000, 1000);
    private final TextArea log = new TextArea();

    MailboxPane() {
        Label title = new Label("Mailbox: dois produtores e um consumidor");
        title.getStyleClass().add("title");
        HBox header = new HBox(20, title, stateLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        Label subtitle = new Label("Exemplo do professor (Mailbox3b e Mailbox3s). Dave diz \"Hello, world.\" (azul) "
                + "e Bill diz \"Hot dog!\" (laranja). Cada um escreve a sua mensagem LETRA POR LETRA "
                + "na mesma caixa; o consumidor lê a caixa inteira.");
        subtitle.setWrapText(true);
        modeInfo.setWrapText(true);
        raceAlert.setWrapText(true);
        raceAlert.getStyleClass().add("alert");
        lossAlert.setWrapText(true);
        lossAlert.getStyleClass().add("warning");
        for (Label l : new Label[]{modeInfo, raceAlert, lossAlert}) l.setMaxWidth(Double.MAX_VALUE);

        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; "
                + "-fx-border-color: #b4c3d1; -fx-border-radius: 10;");
        boxCard = new VBox(12, new Label("CAIXA DE CORREIO: char[] message, 72 posições"), box, boxStatus);
        boxCard.getStyleClass().add("card");

        daveProgress.setMaxWidth(Double.MAX_VALUE);
        billProgress.setMaxWidth(Double.MAX_VALUE);
        VBox people = new VBox(10,
                person("Dave (produtor)", DAVE, daveProgress, "dave-bar"), daveStatus,
                person("Bill (produtor)", BILL, billProgress, "bill-bar"), billStatus,
                bold("Consumidor"), consumerStatus);
        people.getStyleClass().add("card");

        VBox receivedCard = new VBox(10, new Label("MENSAGENS QUE O CONSUMIDOR RECEBEU (mais recente primeiro)"),
                received, counters);
        receivedCard.getStyleClass().add("card");

        VBox timing = new VBox(10,
                sliderRow("Pausa entre letras (MAXPROCESSTIME)", letterPause, " ms"),
                sliderRow("Descanso dos produtores (DELAY)", producerRest, " ms"),
                sliderRow("Consumidor confere a cada (CHECKTIME)", consumerCheck, " ms"));
        timing.getStyleClass().add("card");

        start.setOnAction(e -> { simulation.start(); refresh(); });
        pause.setOnAction(e -> { simulation.pause(); refresh(); });
        Button reset = new Button("Reiniciar");
        reset.setOnAction(e -> { simulation.reset(); refresh(); });
        HBox buttons = new HBox(12, start, pause, reset);

        FlowPane modeRow = new FlowPane(20, 8);
        for (Mailbox.Mode mode : Mailbox.Mode.values()) {
            RadioButton b = new RadioButton(mode.toString());
            b.setToggleGroup(modeGroup);
            b.setUserData(mode);
            if (mode == Mailbox.Mode.UNSYNCHRONIZED) b.getStyleClass().add("unsafe-check");
            b.setOnAction(e -> {
                try { simulation.setMode(mode); } catch (IllegalStateException running) { /* so parado */ }
                refresh();
            });
            modes[mode.ordinal()] = b;
            modeRow.getChildren().add(b);
        }
        VBox modeCard = new VBox(10, new Label("Versão da caixa (troque com a simulação parada):"), modeRow);
        modeCard.getStyleClass().add("card");

        Label challenge = new Label("Experimente: em \"Sem sincronização\" aparecem letras das duas cores na mesma "
                + "mensagem. Em \"Monitor do professor\" as mensagens chegam inteiras, mas veja o contador de "
                + "PERDIDAS. Nos dois últimos modos, nada se mistura e nada se perde.");
        challenge.setWrapText(true);
        log.setEditable(false); log.setPrefRowCount(5); log.setFocusTraversable(false);

        VBox content = new VBox(16, header, subtitle, modeInfo, raceAlert, lossAlert, boxCard, people,
                receivedCard, timing, buttons, modeCard, challenge,
                new Label("Avisos (mais recente primeiro)"), log);
        content.setPadding(new Insets(24));
        root = new ScrollPane(content);
        root.setFitToWidth(true);

        letterPause.valueProperty().addListener((o, a, b) -> applyTiming());
        producerRest.valueProperty().addListener((o, a, b) -> applyTiming());
        consumerCheck.valueProperty().addListener((o, a, b) -> applyTiming());
        applyTiming();
        refresh();
    }

    Node node() { return root; }

    void close() { simulation.close(); }

    private void applyTiming() {
        simulation.timing(Math.round(letterPause.getValue()), Math.round(producerRest.getValue()),
                          Math.round(consumerCheck.getValue()));
    }

    void refresh() {
        MailboxSimulation.View view = simulation.view();
        Mailbox.Snapshot m = view.mailbox();
        boolean running = view.state() == Simulation.State.RUNNING;
        stateLabel.setText(switch (view.state()) {
            case READY -> "Pronto"; case RUNNING -> "Em execução";
            case PAUSED -> "Pausado"; case CLOSED -> "Encerrado";
        });
        start.setText(view.state() == Simulation.State.PAUSED ? "Continuar" : "Iniciar");
        start.setDisable(running);
        pause.setDisable(!running);
        for (RadioButton b : modes) { b.setDisable(running); b.setSelected(b.getUserData() == m.mode()); }

        // Explicacao do modo, no contexto da caixa.
        modeInfo.setText(switch (m.mode()) {
            case UNSYNCHRONIZED -> "SEM SINCRONIZAÇÃO (Mailbox3b): storeMessage e retrieveMessage não são "
                    + "synchronized. Dave e Bill podem escrever no mesmo array ao mesmo tempo, e o consumidor pode "
                    + "ler uma mensagem pela metade. Sem correio, ele volta com \"How sad, no mail ..\".";
            case PROFESSOR -> "MONITOR DO PROFESSOR (Mailbox3s): os dois métodos são synchronized; o consumidor "
                    + "espera com while (!youHaveMail) wait() e o produtor avisa com notify(). As mensagens chegam "
                    + "inteiras, mas storeMessage NÃO espera a caixa esvaziar: uma mensagem ainda não lida pode ser "
                    + "sobrescrita.";
            case FULL_MONITOR -> "MONITOR COMPLETO (CaixaDeCorreio): o consumidor faz while (vazia) wait(), o "
                    + "produtor também faz while (cheia) wait(), e todos usam notifyAll(). Nada se mistura e "
                    + "nada se perde.";
            case SEMAPHORES -> "SEMÁFOROS: a caixa é um buffer de uma posição. \"vaga\" começa em 1, \"mensagem\" "
                    + "em 0, e o mutex protege as letras. Mesmo resultado do monitor completo, com outra ferramenta.";
        });
        modeInfo.getStyleClass().setAll("label", m.mode() == Mailbox.Mode.UNSYNCHRONIZED ? "warning" : "info");

        // A caixa: cada letra na cor de quem a escreveu.
        box.getChildren().clear();
        for (int i = 0; i < Mailbox.MAX; i++) {
            int who = m.writers().get(i);
            Text t = new Text(who == Mailbox.NOBODY ? "·" : String.valueOf(m.text().charAt(i)));
            t.setFont(Font.font("Monospaced", FontWeight.BOLD, 22));
            t.setFill(who == Mailbox.DAVE ? DAVE : who == Mailbox.BILL ? BILL : EMPTY);
            box.getChildren().add(t);
        }
        boolean twoWriting = m.writersInside() > 1;
        String key = m.mode() == Mailbox.Mode.UNSYNCHRONIZED ? "não existe neste modo"
                : m.keyHolder() == Mailbox.NOBODY ? "livre" : "com " + Mailbox.nameOf(m.keyHolder());
        boxStatus.setText("Há correio não lido: " + (m.hasMail() ? "sim" : "não") + "   |   Chave da caixa: " + key
                + (twoWriting ? "   |   AGORA: Dave e Bill escrevendo juntos!" : ""));
        if (twoWriting) { if (!boxCard.getStyleClass().contains("race-live")) boxCard.getStyleClass().add("race-live"); }
        else boxCard.getStyleClass().remove("race-live");

        // Quem esta fazendo o que.
        daveProgress.setProgress(m.daveProgress() / (double) Mailbox.messageOf(Mailbox.DAVE).length());
        billProgress.setProgress(m.billProgress() / (double) Mailbox.messageOf(Mailbox.BILL).length());
        daveStatus.setText(status(m.dave(), m.daveProgress(), Mailbox.DAVE));
        billStatus.setText(status(m.bill(), m.billProgress(), Mailbox.BILL));
        consumerStatus.setText(m.consumer().text());

        // Mensagens recebidas, com o veredito.
        received.getChildren().clear();
        if (m.recent().isEmpty()) received.getChildren().add(new Label("Nenhuma ainda."));
        for (Mailbox.Received r : m.recent()) {
            Label tag = new Label(switch (r.verdict()) {
                case INTACT -> "✔ íntegra"; case MIXED -> "✖ MISTURADA"; case CUT -> "✖ CORTADA";
            });
            tag.setMinWidth(130);
            tag.getStyleClass().setAll("label", r.verdict() == Mailbox.Verdict.INTACT ? "tag-ok" : "tag-bad");
            TextFlow text = new TextFlow();
            for (int i = 0; i < r.text().length(); i++) {
                Text t = new Text(String.valueOf(r.text().charAt(i)));
                t.setFont(Font.font("Monospaced", 16));
                int who = r.writers().get(i);
                t.setFill(who == Mailbox.DAVE ? DAVE : who == Mailbox.BILL ? BILL : EMPTY);
                text.getChildren().add(t);
            }
            HBox row = new HBox(12, tag, text);
            row.setAlignment(Pos.CENTER_LEFT);
            received.getChildren().add(row);
        }
        counters.setText("Enviadas: Dave " + m.sentDave() + ", Bill " + m.sentBill()
                + "   |   Recebidas íntegras: " + m.intact() + "   Misturadas: " + m.mixed()
                + "   Cortadas: " + m.cut() + "   Perdidas: " + m.lost() + "   \"How sad\": " + m.noMail());

        // Alertas.
        boolean race = m.overlaps() > 0 || m.partialReads() > 0 || m.mixed() > 0 || m.cut() > 0 || twoWriting;
        show(raceAlert, race);
        if (race) raceAlert.setText("⚠ CONDIÇÃO DE CORRIDA DETECTADA\n"
                + (twoWriting ? "AGORA: Dave e Bill estão escrevendo na caixa ao mesmo tempo!\n" : "")
                + "Vezes em que os dois escreveram juntos: " + m.overlaps() + "\n"
                + "Leituras feitas no meio de uma escrita: " + m.partialReads() + "\n"
                + "Mensagens recebidas misturadas: " + m.mixed() + "   |   cortadas: " + m.cut());
        show(lossAlert, m.lost() > 0);
        if (m.lost() > 0) lossAlert.setText("✉ MENSAGENS PERDIDAS: " + m.lost() + "\n"
                + "Uma mensagem ainda não lida foi sobrescrita pelo outro produtor. "
                + (m.mode() == Mailbox.Mode.PROFESSOR
                   ? "Na Mailbox3s, storeMessage não espera a caixa esvaziar: integridade sim, entrega não."
                   : "Sem sincronização, ninguém espera ninguém."));

        String text = String.join("\n", m.events());
        if (!text.equals(log.getText())) log.setText(text);
    }

    private static String status(Mailbox.Activity a, int progress, int who) {
        if (a == Mailbox.Activity.WRITING)
            return "Escrevendo: letra " + progress + " de " + Mailbox.messageOf(who).length();
        return a.text();
    }

    private static HBox person(String name, Color color, ProgressBar bar, String barClass) {
        Label label = new Label(name);
        label.setMinWidth(170);
        label.setTextFill(color);
        label.setStyle("-fx-font-weight: bold;");
        bar.getStyleClass().add(barClass);
        HBox row = new HBox(16, label, bar);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(bar, Priority.ALWAYS);
        return row;
    }

    private static Label bold(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private static Slider slider(double min, double max, double value) {
        Slider s = new Slider(min, max, value);
        s.setShowTickLabels(true); s.setShowTickMarks(true);
        s.setMajorTickUnit((max - min) / 4);
        s.setMaxWidth(Double.MAX_VALUE);
        return s;
    }

    private static HBox sliderRow(String name, Slider slider, String unit) {
        Label label = new Label(name); label.setMinWidth(330);
        Label value = new Label(); value.setMinWidth(80);
        Runnable update = () -> value.setText(Math.round(slider.getValue()) + unit);
        slider.valueProperty().addListener((o, a, b) -> update.run());
        update.run();
        HBox row = new HBox(16, label, slider, value);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private static void show(Label label, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }
}
