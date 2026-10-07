CC = gcc
CFLAGS = -std=c11 -Wall -Wextra -Wpedantic -Werror -O2
BUILD = build
DIA07 = examples/dia07
PROGRAMS = $(BUILD)/fork2 $(BUILD)/pipe2 $(BUILD)/peterson $(BUILD)/prod_cons \
           $(BUILD)/pipe-soma $(BUILD)/fork-basico $(BUILD)/memoria-independente \
           $(BUILD)/pipe-soma-resposta $(BUILD)/corrida $(BUILD)/prod-cons-resposta \
           $(BUILD)/corrida-atomic $(BUILD)/corrida-mutex

.PHONY: all test java run-fork run-pipe run-peterson run-prod-cons run-java-3b run-java-3s
all: $(PROGRAMS)

$(BUILD):
	mkdir -p $@

$(BUILD)/fork2: examples/dia05/fork2.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@

$(BUILD)/pipe2: examples/dia05/pipe2.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@ -lm

$(BUILD)/peterson: $(DIA07)/peterson-code.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@ -pthread

$(BUILD)/prod_cons: $(DIA07)/prod_cons.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@ -pthread

test: all
	python3 tests/test_examples.py

java: $(BUILD)/java-3b/.compiled $(BUILD)/java-3s/.compiled

$(BUILD)/java-3b/.compiled: $(wildcard $(DIA07)/mailbox-3b/*.java)
	mkdir -p $(BUILD)/java-3b
	javac -d $(BUILD)/java-3b $^
	touch $@

$(BUILD)/java-3s/.compiled: $(wildcard $(DIA07)/mailbox-3s/*.java)
	mkdir -p $(BUILD)/java-3s
	javac -d $(BUILD)/java-3s $^
	touch $@

run-fork: $(BUILD)/fork2
	./$(BUILD)/fork2
run-pipe: $(BUILD)/pipe2
	./$(BUILD)/pipe2
run-peterson: $(BUILD)/peterson
	./$(BUILD)/peterson
run-prod-cons: $(BUILD)/prod_cons
	./$(BUILD)/prod_cons
run-java-3b: java
	java -cp $(BUILD)/java-3b ThreadSync3b
run-java-3s: java
	java -cp $(BUILD)/java-3s ThreadSync3s

.PHONY: test-simulation run-simulation
test-simulation:
	mkdir -p $(BUILD)/simulation-tests
	java -m jdk.compiler/com.sun.tools.javac.Main -d $(BUILD)/simulation-tests simulacao/src/main/java/br/edu/mata58/dinheiro/BoundedBuffer.java simulacao/src/main/java/br/edu/mata58/dinheiro/Simulation.java simulacao/src/main/java/br/edu/mata58/dinheiro/Mailbox.java simulacao/src/main/java/br/edu/mata58/dinheiro/MailboxSimulation.java simulacao/src/test/java/br/edu/mata58/dinheiro/SimulationTest.java simulacao/src/test/java/br/edu/mata58/dinheiro/MailboxTest.java
	java -cp $(BUILD)/simulation-tests br.edu.mata58.dinheiro.SimulationTest
run-simulation:
	mvn -f simulacao/pom.xml javafx:run

.PHONY: run-pipe-soma
$(BUILD)/pipe-soma: examples/dia05/pipe_soma.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@
run-pipe-soma: $(BUILD)/pipe-soma
	./$(BUILD)/pipe-soma

.PHONY: run-fork-basico run-memoria
$(BUILD)/fork-basico: examples/dia05/fork_basico.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@
$(BUILD)/memoria-independente: examples/dia05/memoria_independente.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@
run-fork-basico: $(BUILD)/fork-basico
	./$(BUILD)/fork-basico
run-memoria: $(BUILD)/memoria-independente
	./$(BUILD)/memoria-independente

.PHONY: run-resposta
$(BUILD)/pipe-soma-resposta: examples/dia05/pipe_soma_atividade_resposta.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@
run-resposta: $(BUILD)/pipe-soma-resposta
	./$(BUILD)/pipe-soma-resposta

.PHONY: run-corrida run-resposta-dia07
$(BUILD)/corrida: examples/dia07/corrida.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@ -pthread
$(BUILD)/prod-cons-resposta: examples/dia07/prod_cons_resposta.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@ -pthread
run-corrida: $(BUILD)/corrida
	./$(BUILD)/corrida
run-resposta-dia07: $(BUILD)/prod-cons-resposta
	./$(BUILD)/prod-cons-resposta
.PHONY: run-caixa run-corrida-atomic run-corrida-mutex
$(BUILD)/caixa/.compiled: $(wildcard examples/dia07/caixa/*.java)
	mkdir -p $(BUILD)/caixa
	javac -d $(BUILD)/caixa $^
	touch $@
run-caixa: $(BUILD)/caixa/.compiled
	java -cp $(BUILD)/caixa TesteCaixa
$(BUILD)/corrida-atomic: examples/dia07/corrida_atomic.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@ -pthread
$(BUILD)/corrida-mutex: examples/dia07/corrida_mutex.c | $(BUILD)
	$(CC) $(CFLAGS) $< -o $@ -pthread
run-corrida-atomic: $(BUILD)/corrida-atomic
	./$(BUILD)/corrida-atomic
run-corrida-mutex: $(BUILD)/corrida-mutex
	./$(BUILD)/corrida-mutex

clean:
	rm -rf $(BUILD)
.PHONY: clean
