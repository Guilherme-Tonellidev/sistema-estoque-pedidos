package sistema_estoque_pedidos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class ExecucaoSimultanea {

    private ExecucaoSimultanea() {
    }

    static List<Boolean> repetir(int vezes, Callable<Boolean> tarefa)
            throws Exception {
        List<Callable<Boolean>> tarefas = new ArrayList<>();
        for (int i = 0; i < vezes; i++) {
            tarefas.add(tarefa);
        }
        return executar(tarefas);
    }

    static List<Boolean> executar(List<Callable<Boolean>> tarefas)
            throws Exception {
        int total = tarefas.size();
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CountDownLatch prontas = new CountDownLatch(total);
        CountDownLatch largada = new CountDownLatch(1);

        try {
            List<Future<Boolean>> futuros = new ArrayList<>();
            for (Callable<Boolean> tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    prontas.countDown();
                    largada.await();
                    return tarefa.call();
                }));
            }

            prontas.await();
            largada.countDown();

            List<Boolean> resultados = new ArrayList<>();
            for (Future<Boolean> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}