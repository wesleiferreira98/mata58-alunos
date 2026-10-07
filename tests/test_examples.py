"""Testes de comportamento das demonstracoes C; execute com make test."""
import re
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(name, data=""):
    return subprocess.run(
        [str(ROOT / "build" / name)], input=data, text=True,
        capture_output=True, timeout=10, check=False,
    )


class ExamplesTest(unittest.TestCase):
    def test_fork_parent_keeps_own_memory(self):
        result = run("fork2")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "Soma total no pai: 0\n")

    def test_fork_basico_both_returns(self):
        result = run("fork-basico")
        self.assertEqual(result.returncode, 0, result.stderr)
        filho = re.search(r"Filho: fork retornou 0; meu PID (\d+); meu pai (\d+)", result.stdout)
        pai = re.search(r"Pai: fork retornou (\d+); meu PID (\d+)", result.stdout)
        self.assertTrue(filho and pai, result.stdout)
        self.assertEqual(filho.group(1), pai.group(1))
        self.assertEqual(filho.group(2), pai.group(2))
        self.assertIn(f"Pai: filho {pai.group(1)} terminou com codigo 0", result.stdout)
        self.assertEqual(result.stdout.count("Antes do fork"), 1)

    def test_memoria_same_address_different_values(self):
        result = run("memoria-independente")
        self.assertEqual(result.returncode, 0, result.stderr)
        filho = re.search(r"Filho: x = 99 em (\S+)", result.stdout)
        pai = re.search(r"Pai:   x = 10 em (\S+)", result.stdout)
        self.assertTrue(filho and pai, result.stdout)
        self.assertEqual(filho.group(1), pai.group(1))

    def test_pipe_soma_child_sends_to_parent(self):
        result = run("pipe-soma")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "Resultado recebido: 30\n")

    def test_activity_answer_matches_solution(self):
        result = run("pipe-soma-resposta")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "Resultado recebido: 30\n")

    def test_corrida_runs_and_never_exceeds_expected(self):
        result = run("corrida")
        self.assertEqual(result.returncode, 0, result.stderr)
        found = re.search(r"Contador: (\d+) \(esperado: 2000000\)", result.stdout)
        self.assertTrue(found, result.stdout)
        self.assertLessEqual(int(found.group(1)), 2000000)

    def test_corrida_fixes_never_lose_increments(self):
        for name in ["corrida-atomic", "corrida-mutex"]:
            for attempt in range(3):
                with self.subTest(name=name, attempt=attempt):
                    result = run(name)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertIn("Contador: 2000000 (esperado: 2000000)", result.stdout)

    def test_prod_cons_activity_answer(self):
        result = run("prod-cons-resposta")
        self.assertEqual(result.returncode, 0, result.stderr)
        produced = re.findall(r"Produzido: (\d+)", result.stdout)
        consumed = re.findall(r"Consumido: (\d+)", result.stdout)
        self.assertEqual(len(produced), 40)
        self.assertEqual(produced, consumed)
        self.assertIn("40 itens produzidos e consumidos; buffer vazio.", result.stdout)

    def test_pipe_roots(self):
        for data, roots in [
            ("1 -3 1\n", "x1 = 1; x2 = 2"),
            ("1 -2 0\n", "x1 = 1; x2 = 1"),
            ("1 0 4\n", "x1 = -1; x2 = 1"),
            ("0.5 -1.5 0.25\n", "x1 = 1; x2 = 2"),
        ]:
            with self.subTest(data=data):
                result = run("pipe2", data)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn(roots, result.stdout)

    def test_pipe_invalid_input(self):
        for data in ["", "abc\n", "1 2\n", "0 2 1\n", "1 2 -1\n",
                     "nan 2 1\n", "1 inf 1\n", "1 2 nan\n",
                     "1e308 2 1\n", "1e-308 1e308 1\n"]:
            with self.subTest(data=data):
                result = run("pipe2", data)
                self.assertNotEqual(result.returncode, 0)
                self.assertTrue(result.stderr)
                self.assertNotIn("Raizes:", result.stdout)

    def test_peterson_protects_all_increments(self):
        for attempt in range(10):
            with self.subTest(attempt=attempt):
                result = run("peterson")
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("Contador: 20000 (esperado: 20000)", result.stdout)

    def test_producer_consumer_fifo_and_capacity(self):
        for attempt in range(10):
            with self.subTest(attempt=attempt):
                result = run("prod_cons")
                self.assertEqual(result.returncode, 0, result.stderr)
                produced = re.findall(r"Produzido: (\d+)", result.stdout)
                consumed = re.findall(r"Consumido: (\d+)", result.stdout)
                self.assertEqual(len(produced), 40)
                self.assertEqual(produced, consumed)
                occupancy = re.findall(r"ocupacao: (\d+)/20", result.stdout)
                self.assertEqual(len(occupancy), 80)
                self.assertTrue(all(0 <= int(n) <= 20 for n in occupancy))
                self.assertIn("40 itens produzidos e consumidos; buffer vazio.", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
