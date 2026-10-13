package sistema_estoque_pedidos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PedidoConcorrenciaTest extends ConcorrenciaTestBase {

    @Autowired
    private PedidoService pedidoService;

    // Dois pedidos disputam o mesmo estoque.
    @Test
    void doisPedidosDisputandoOUltimoEstoqueConfirmamApenasUm()
            throws Exception {
        Long produtoId = criarProdutoComEstoque(10);
        Long pedidoA = criarPedido(item(produtoId, 8));
        Long pedidoB = criarPedido(item(produtoId, 8));

        List<Boolean> resultados = ExecucaoSimultanea.executar(
                List.of(confirmar(pedidoA), confirmar(pedidoB)));

        assertEquals(1, contarSucessos(resultados));
        assertEquals(2, saldo(produtoId));
        assertEquals(1, quantidadeMovimentacoes(produtoId));
        verificarCadeiaDeSaldos(produtoId, 10);

        long confirmados = List.of(pedidoA, pedidoB).stream()
                .filter(id -> "CONFIRMADO".equals(statusDoPedido(id)))
                .count();
        assertEquals(1, confirmados);
    }

    // O mesmo pedido confirmado por várias threads.
    @Test
    void mesmoPedidoConfirmadoVariasVezesDescontaEstoqueUmaUnicaVez()
            throws Exception {
        Long produtoId = criarProdutoComEstoque(100);
        Long pedidoId = criarPedido(item(produtoId, 8));

        List<Boolean> resultados =
                ExecucaoSimultanea.repetir(10, confirmar(pedidoId));

        assertEquals(1, contarSucessos(resultados));
        assertEquals(92, saldo(produtoId));
        assertEquals("CONFIRMADO", statusDoPedido(pedidoId));
        assertEquals(1, quantidadeMovimentacoes(produtoId));
        verificarCadeiaDeSaldos(produtoId, 100);
    }

    // Protege a ordenação dos bloqueios: sem ela, aparece deadlock.
    @Test
    void pedidosComProdutosEmOrdemOpostaNaoGeramDeadlock() throws Exception {
        Long produto1 = criarProdutoComEstoque(100);
        Long produto2 = criarProdutoComEstoque(100);

        for (int rodada = 0; rodada < 20; rodada++) {
            Long pedidoA = criarPedido(item(produto1, 1), item(produto2, 1));
            Long pedidoB = criarPedido(item(produto2, 1), item(produto1, 1));

            List<Boolean> resultados = ExecucaoSimultanea.executar(
                    List.of(confirmar(pedidoA), confirmar(pedidoB)));

            assertEquals(2, contarSucessos(resultados));
        }

        assertEquals(60, saldo(produto1));
        assertEquals(60, saldo(produto2));
        verificarCadeiaDeSaldos(produto1, 100);
        verificarCadeiaDeSaldos(produto2, 100);
    }

    // Confirmar e cancelar o mesmo rascunho ao mesmo tempo.
    @Test
    void confirmarECancelarOMesmoRascunhoDeixaEstoqueCoerente()
            throws Exception {
        for (int rodada = 0; rodada < 10; rodada++) {
            Long produtoId = criarProdutoComEstoque(10);
            Long pedidoId = criarPedido(item(produtoId, 4));

            List<Boolean> resultados = ExecucaoSimultanea.executar(
                    List.of(confirmar(pedidoId), cancelar(pedidoId)));

            boolean confirmou = resultados.get(0);
            boolean cancelou = resultados.get(1);

            assertTrue(cancelou);
            assertEquals("CANCELADO", statusDoPedido(pedidoId));
            assertEquals(10, saldo(produtoId));
            // Se a confirmação venceu, houve a saída e a devolução;
            // se o cancelamento venceu, o estoque nem foi tocado.
            assertEquals(confirmou ? 2 : 0, quantidadeMovimentacoes(produtoId));
            verificarCadeiaDeSaldos(produtoId, 10);
        }
    }

    private Callable<Boolean> confirmar(Long pedidoId) {
        return () -> {
            try {
                pedidoService.confirmar(pedidoId);
                return true;
            } catch (ResponseStatusException exception) {
                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                return false;
            }
        };
    }

    private Callable<Boolean> cancelar(Long pedidoId) {
        return () -> {
            pedidoService.cancelar(pedidoId);
            return true;
        };
    }

    private CriarPedidoRequest.ItemRequest item(Long produtoId, int quantidade) {
        return new CriarPedidoRequest.ItemRequest(produtoId, quantidade);
    }

    private Long criarPedido(CriarPedidoRequest.ItemRequest... itens) {
        PedidoResposta resposta = pedidoService.cadastrar(
                new CriarPedidoRequest("Cliente de concorrência", List.of(itens)));

        pedidosCriados.add(resposta.id());

        return resposta.id();
    }

    private long contarSucessos(List<Boolean> resultados) {
        return resultados.stream().filter(Boolean::booleanValue).count();
    }
}