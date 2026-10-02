package sistema_estoque_pedidos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class PedidoIntegracaoTest {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> produtosCriados = new ArrayList<>();
    private final List<Long> pedidosCriados = new ArrayList<>();

    @BeforeEach
    void verificarBancoDeTestes() {
        String banco = jdbc.queryForObject(
                "SELECT current_database()", String.class);

        assertEquals(
                "estoque_test_db",
                banco,
                "Os testes devem executar no banco estoque_test_db.");
    }

    @AfterEach
    void removerDadosCriadosPeloTeste() {
        TransactionTemplate transacao =
                new TransactionTemplate(transactionManager);

        transacao.executeWithoutResult(status -> {
            for (Long pedidoId : pedidosCriados) {
                jdbc.update(
                        "DELETE FROM itens_pedido WHERE pedido_id = ?",
                        pedidoId);

                jdbc.update(
                        "DELETE FROM pedidos WHERE id = ?",
                        pedidoId);
            }

            for (Long produtoId : produtosCriados) {
                jdbc.update(
                        "DELETE FROM movimentacoes_estoque WHERE produto_id = ?",
                        produtoId);

                jdbc.update(
                        "DELETE FROM produtos WHERE id = ?",
                        produtoId);
            }
        });
    }

    @Test
    void deveConfirmarPedidoEDescontarTodosOsProdutos() {
        Long produtoA = criarProdutoComEstoque(10);
        Long produtoB = criarProdutoComEstoque(8);

        Long pedidoId = criarPedido(
                new CriarPedidoRequest.ItemRequest(produtoA, 2),
                new CriarPedidoRequest.ItemRequest(produtoB, 3));

        pedidoService.confirmar(pedidoId);

        verificarPedido(pedidoId, "CONFIRMADO", true, false);
        verificarSaldo(produtoA, 8);
        verificarSaldo(produtoB, 5);

        verificarMovimentacao(
                produtoA, "SAIDA", 2, 10, 8,
                "Confirmação do pedido #" + pedidoId);

        verificarMovimentacao(
                produtoB, "SAIDA", 3, 8, 5,
                "Confirmação do pedido #" + pedidoId);

        assertEquals(1, quantidadeMovimentacoes(produtoA));
        assertEquals(1, quantidadeMovimentacoes(produtoB));
    }

    @Test
    void deveRecusarPedidoInteiroQuandoUmProdutoNaoTemSaldo() {
        Long produtoA = criarProdutoComEstoque(10);
        Long produtoB = criarProdutoComEstoque(1);

        Long pedidoId = criarPedido(
                new CriarPedidoRequest.ItemRequest(produtoA, 2),
                new CriarPedidoRequest.ItemRequest(produtoB, 3));

        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> pedidoService.confirmar(pedidoId));

        assertEquals(409, erro.getStatusCode().value());

        verificarPedido(pedidoId, "RASCUNHO", false, false);
        verificarSaldo(produtoA, 10);
        verificarSaldo(produtoB, 1);

        assertEquals(0, quantidadeMovimentacoes(produtoA));
        assertEquals(0, quantidadeMovimentacoes(produtoB));
    }

    @Test
    void deveCancelarPedidoConfirmadoEDevolverTodosOsProdutos() {
        Long produtoA = criarProdutoComEstoque(10);
        Long produtoB = criarProdutoComEstoque(8);

        Long pedidoId = criarPedido(
                new CriarPedidoRequest.ItemRequest(produtoA, 2),
                new CriarPedidoRequest.ItemRequest(produtoB, 3));

        pedidoService.confirmar(pedidoId);
        pedidoService.cancelar(pedidoId);

        verificarPedido(pedidoId, "CANCELADO", true, true);
        verificarSaldo(produtoA, 10);
        verificarSaldo(produtoB, 8);

        verificarMovimentacao(
                produtoA, "ENTRADA", 2, 8, 10,
                "Cancelamento do pedido #" + pedidoId);

        verificarMovimentacao(
                produtoB, "ENTRADA", 3, 5, 8,
                "Cancelamento do pedido #" + pedidoId);

        assertEquals(2, quantidadeMovimentacoes(produtoA));
        assertEquals(2, quantidadeMovimentacoes(produtoB));
    }

    @Test
    void deveRecusarSegundoCancelamentoSemDevolverNovamente() {
        Long produtoId = criarProdutoComEstoque(10);

        Long pedidoId = criarPedido(
                new CriarPedidoRequest.ItemRequest(produtoId, 2));

        pedidoService.confirmar(pedidoId);
        pedidoService.cancelar(pedidoId);

        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> pedidoService.cancelar(pedidoId));

        assertEquals(409, erro.getStatusCode().value());

        verificarPedido(pedidoId, "CANCELADO", true, true);
        verificarSaldo(produtoId, 10);
        assertEquals(2, quantidadeMovimentacoes(produtoId));

        verificarMovimentacao(
                produtoId, "ENTRADA", 2, 8, 10,
                "Cancelamento do pedido #" + pedidoId);
    }

    private Long criarProdutoComEstoque(int quantidade) {
        Produto produto = new Produto(
                "TEST-" + UUID.randomUUID(),
                "Produto de integração",
                new BigDecimal("10.00"),
                0);

        // Prepara o saldo inicial sem gerar histórico neste cenário.
        produto.registrarEntrada(quantidade);

        Produto salvo = produtoRepository.saveAndFlush(produto);
        produtosCriados.add(salvo.getId());

        return salvo.getId();
    }

    private Long criarPedido(CriarPedidoRequest.ItemRequest... itens) {
        CriarPedidoRequest request = new CriarPedidoRequest(
                "Cliente de integração",
                List.of(itens));

        PedidoResposta resposta = pedidoService.cadastrar(request);
        pedidosCriados.add(resposta.id());

        return resposta.id();
    }

    private void verificarSaldo(Long produtoId, int esperado) {
        Integer saldo = jdbc.queryForObject(
                "SELECT quantidade_estoque FROM produtos WHERE id = ?",
                Integer.class,
                produtoId);

        assertEquals(Integer.valueOf(esperado), saldo);
    }

    private int quantidadeMovimentacoes(Long produtoId) {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM movimentacoes_estoque
                WHERE produto_id = ?
                """,
                Integer.class,
                produtoId);
    }

    private void verificarPedido(
            Long pedidoId,
            String statusEsperado,
            boolean confirmado,
            boolean cancelado) {

        String status = jdbc.queryForObject(
                "SELECT status FROM pedidos WHERE id = ?",
                String.class,
                pedidoId);

        Boolean possuiConfirmacao = jdbc.queryForObject(
                """
                SELECT data_confirmacao IS NOT NULL
                FROM pedidos WHERE id = ?
                """,
                Boolean.class,
                pedidoId);

        Boolean possuiCancelamento = jdbc.queryForObject(
                """
                SELECT data_cancelamento IS NOT NULL
                FROM pedidos WHERE id = ?
                """,
                Boolean.class,
                pedidoId);

        assertEquals(statusEsperado, status);
        assertEquals(Boolean.valueOf(confirmado), possuiConfirmacao);
        assertEquals(Boolean.valueOf(cancelado), possuiCancelamento);
    }

    private void verificarMovimentacao(
            Long produtoId,
            String tipo,
            int quantidade,
            int saldoAnterior,
            int saldoPosterior,
            String motivo) {

        Integer registros = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM movimentacoes_estoque
                WHERE produto_id = ?
                  AND tipo = ?
                  AND quantidade = ?
                  AND saldo_anterior = ?
                  AND saldo_posterior = ?
                  AND motivo = ?
                """,
                Integer.class,
                produtoId,
                tipo,
                quantidade,
                saldoAnterior,
                saldoPosterior,
                motivo);

        assertEquals(Integer.valueOf(1), registros);
    }
}