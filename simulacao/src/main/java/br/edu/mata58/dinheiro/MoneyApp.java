package br.edu.mata58.dinheiro;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

/** A interface apenas renderiza snapshots; workers nunca acessam componentes JavaFX. */
public final class MoneyApp extends Application {
    private final Simulation simulation = new Simulation();
    private final Label[] slots = new Label[10];
    private final Label summary = new Label(), production = new Label(), consumption = new Label();
    private final Label counters = new Label(), stateLabel = new Label();
    private final TextArea log = new TextArea();
    private final ProgressBar occupancy = new ProgressBar(0);
    private final Button start = new Button("Iniciar"), pause = new Button("Pausar");
    // Como o buffer e protegido: semaforos, monitor, ou sem protecao (mostra a corrida).
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final RadioButton semaphoresMode = modeButton("Semáforos (empty, full, mutex)", Simulation.Mode.SEMAPHORES);
    private final RadioButton monitorMode = modeButton("Monitor (synchronized, wait, notifyAll)", Simulation.Mode.MONITOR);
    private final RadioButton unprotectedMode = modeButton("SEM proteção (desliga o mutex)", Simulation.Mode.UNPROTECTED);
    private final Label modeWarning = new Label(), raceAlert = new Label();
    private VBox bufferCard;
    private Timeline timeline;
    private MailboxPane mailboxPane;    // segunda aba: o exemplo da Mailbox do professor

    @Override public void start(Stage stage) {
        Label title = new Label("Produção de dinheiro");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Produtor e consumidor • dez posições • exclusão mútua e disponibilidade");
        subtitle.setWrapText(true);
        HBox header = new HBox(20, title, stateLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Label();
            slots[i].setMinSize(150, 105);
            slots[i].setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            slots[i].setAlignment(Pos.CENTER);
            grid.add(slots[i], i % 5, i / 5);
            GridPane.setHgrow(slots[i], Priority.ALWAYS);
        }
        occupancy.setMaxWidth(Double.MAX_VALUE);
        bufferCard = new VBox(14, new Label("BUFFER CIRCULAR: NOTAS EM ORDEM FIFO"),
                summary, occupancy, grid, counters);
        bufferCard.getStyleClass().add("card");
        Label explanation = new Label("Vagas permitem produzir. Itens permitem consumir. "
                + "W indica a próxima escrita; R, a próxima leitura. A caixa no topo explica como o modo "
                + "escolhido protege o buffer.");
        explanation.setWrapText(true);
        VBox rates = new VBox(12, rateControl("Banco / produtor", true), production,
                rateControl("Carro-forte / consumidor", false), consumption, explanation);
        rates.getStyleClass().add("card");
        start.setOnAction(event -> { simulation.start(); refresh(); });
        pause.setOnAction(event -> { simulation.pause(); refresh(); });
        Button reset = new Button("Reiniciar");
        reset.setOnAction(event -> { simulation.reset(); refresh(); });
        unprotectedMode.getStyleClass().add("unsafe-check");
        HBox buttons = new HBox(12, start, pause, reset);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label modeTitle = new Label("Proteção do buffer (troque com a simulação parada):");
        HBox modes = new HBox(20, semaphoresMode, monitorMode, unprotectedMode);
        modes.setAlignment(Pos.CENTER_LEFT);
        VBox modeCard = new VBox(10, modeTitle, modes);
        modeCard.getStyleClass().add("card");
        modeWarning.setWrapText(true);
        raceAlert.setWrapText(true);
        raceAlert.getStyleClass().add("alert");
        modeWarning.setMaxWidth(Double.MAX_VALUE);
        raceAlert.setMaxWidth(Double.MAX_VALUE);
        Label challenge = new Label("Experimente: produtor mais rápido → buffer cheio; "
                + "consumidor mais rápido → buffer vazio. Quem precisa esperar em cada caso? "
                + "Compare os modos: em Semáforos e em Monitor o comportamento é o mesmo; "
                + "em SEM proteção, com produtor 10 e consumidor 3, aparece a condição de corrida.");
        challenge.setWrapText(true);
        log.setEditable(false); log.setPrefRowCount(6); log.setFocusTraversable(false);
        VBox content = new VBox(16, header, subtitle, modeWarning, raceAlert, bufferCard, rates, buttons, modeCard, challenge,
                new Label("Últimos eventos (mais recente primeiro)"), log);
        content.setPadding(new Insets(24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        mailboxPane = new MailboxPane();
        Tab moneyTab = new Tab("Produção de dinheiro (produtor e consumidor)", scroll);
        Tab mailboxTab = new Tab("Mailbox (exemplo do professor)", mailboxPane.node());
        TabPane tabs = new TabPane(moneyTab, mailboxTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Scene scene = new Scene(tabs, 1000, 900);
        scene.getStylesheets().add(getClass().getResource("/simulation.css").toExternalForm());
        stage.setTitle("MATA58 · Produtor e consumidor | Weslei Ferreira Santos");
        stage.setScene(scene); stage.setMinWidth(950); stage.setMinHeight(650);
        timeline = new Timeline(new KeyFrame(Duration.millis(100), event -> {
            refresh();
            if (mailboxTab.isSelected()) mailboxPane.refresh();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play(); refresh(); stage.show();
    }

    private final Slider producerRate = slider(), consumerRate = slider();
    private static Slider slider() {
        Slider slider = new Slider(0.2, 10, 2);
        slider.setShowTickLabels(true); slider.setShowTickMarks(true);
        slider.setMajorTickUnit(2); slider.setBlockIncrement(0.2);
        slider.setMaxWidth(Double.MAX_VALUE);
        return slider;
    }

    private HBox rateControl(String name, boolean producer) {
        Slider slider = producer ? producerRate : consumerRate;
        Label value = new Label(); value.setMinWidth(100);
        Runnable update = () -> {
            value.setText(String.format("%.1f itens/s", slider.getValue()));
            simulation.rates(producerRate.getValue(), consumerRate.getValue());
        };
        slider.valueProperty().addListener((observable, oldValue, newValue) -> update.run());
        update.run();
        Label label = new Label(name); label.setMinWidth(205);
        HBox row = new HBox(16, label, slider, value);
        row.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private void refresh() {
        Simulation.View view = simulation.view();
        BoundedBuffer.Snapshot buffer = view.buffer();
        String state = switch (view.state()) {
            case READY -> "Pronto"; case RUNNING -> "Em execução";
            case PAUSED -> "Pausado"; case CLOSED -> "Encerrado";
        };
        stateLabel.setText(state);
        start.setText(view.state() == Simulation.State.PAUSED ? "Continuar" : "Iniciar");
        start.setDisable(view.state() == Simulation.State.RUNNING);
        pause.setDisable(view.state() != Simulation.State.RUNNING);
        // O modo so pode ser trocado com a simulacao parada.
        boolean running = view.state() == Simulation.State.RUNNING;
        for (RadioButton b : new RadioButton[]{semaphoresMode, monitorMode, unprotectedMode}) {
            b.setDisable(running);
            b.setSelected(b.getUserData() == view.mode());
        }
        modeWarning.setText(switch (view.mode()) {
            case SEMAPHORES -> "MODO SEMÁFOROS: empty conta as vagas, full conta os itens e o mutex "
                    + "garante que só uma thread mexe no buffer por vez (como no prod_cons.c).";
            case MONITOR -> "MODO MONITOR: o buffer é um objeto synchronized, sem nenhum semáforo. "
                    + "Sem vaga, o produtor faz while (cheio) wait(); sem item, o consumidor faz "
                    + "while (vazio) wait(). Quem insere ou retira chama notifyAll() para acordar o outro.";
            case UNPROTECTED -> "MODO SEM PROTEÇÃO: o mutex está desligado. Produtor e consumidor podem entrar "
                    + "juntos na região crítica e estragar o contador do buffer. Os semáforos de vagas e itens "
                    + "continuam ligados.";
        });
        modeWarning.getStyleClass().setAll("label", view.protectedMode() ? "info" : "warning");
        // size e o contador compartilhado: e ele que a corrida estraga.
        summary.setText(buffer.size() + " itens disponíveis   |   " + (10 - buffer.size()) + " vagas livres");
        occupancy.setProgress(Math.max(0, Math.min(1, buffer.size() / 10.0)));
        counters.setText("Produzidas: " + buffer.produced() + "   Consumidas: " + buffer.consumed()
                + "   Notas nas posições: " + buffer.notesInSlots()
                + "   Encontros na região crítica: " + buffer.overlaps());
        boolean raceNow = buffer.insideNow() > 1;
        boolean raceSeen = buffer.overlaps() > 0 || buffer.lostUpdates() > 0;
        show(raceAlert, raceSeen || raceNow);
        if (raceSeen || raceNow) {
            raceAlert.setText("⚠ CONDIÇÃO DE CORRIDA DETECTADA\n"
                    + (raceNow ? "AGORA: produtor e consumidor estão juntos na região crítica!\n" : "")
                    + "Vezes em que os dois estiveram juntos na região crítica: " + buffer.overlaps() + "\n"
                    + "Atualizações do contador perdidas: " + buffer.lostUpdates() + "\n"
                    + "Contador do buffer: " + buffer.size() + "   |   Notas de verdade nas posições: "
                    + buffer.notesInSlots()
                    + (buffer.size() != buffer.notesInSlots()
                       ? "   (diferentes! pause e confira: a diferença permanece)" : ""));
        }
        if (raceNow) { if (!bufferCard.getStyleClass().contains("race-live")) bufferCard.getStyleClass().add("race-live"); }
        else bufferCard.getStyleClass().remove("race-live");
        production.setText("Produtor: " + view.producerStatus());
        consumption.setText("Consumidor: " + view.consumerStatus());
        for (int i = 0; i < slots.length; i++) {
            int item = buffer.slots().get(i);
            String pointers = (i == buffer.nextWrite() ? " W" : "") + (i == buffer.nextRead() ? " R" : "");
            slots[i].setText("Posição " + i + pointers + "\n\n" + (item == 0 ? "Vazia" : "Nota #" + item));
            slots[i].getStyleClass().setAll("slot", item == 0 ? "empty" : "occupied");
        }
        String text = String.join("\n", view.events());
        if (!text.equals(log.getText())) log.setText(text);
    }

    private RadioButton modeButton(String text, Simulation.Mode mode) {
        RadioButton button = new RadioButton(text);
        button.setToggleGroup(modeGroup);
        button.setUserData(mode);
        button.setOnAction(event -> {
            try { simulation.setMode(mode); }
            catch (IllegalStateException running) { /* so troca com a simulacao parada */ }
            refresh();
        });
        return button;
    }

    private static void show(Label label, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }

    @Override public void stop() {
        if (timeline != null) timeline.stop();
        simulation.close();
        if (mailboxPane != null) mailboxPane.close();
    }
    public static void main(String[] args) { launch(args); }
}
