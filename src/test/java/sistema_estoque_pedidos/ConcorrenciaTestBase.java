package sistema_estoque_pedidos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Sem @Transactional: cada thread precisa da própria transação.
@SpringBootTest
@ActiveProfiles("test")
abstract class ConcorrenciaTestBase {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ProdutoRepository produtoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> produtosCriados = new ArrayList<>();
    protected final List<Long> pedidosCriados = new ArrayList<>();

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

    protected Long criarProdutoComEstoque(int quantidade) {
        Produto produto = new Produto(
                "CONC-" + UUID.randomUUID(),
                "Produto de concorrência",
                new BigDecimal("10.00"),
                0);

        // Saldo inicial sem gerar histórico, como no PedidoIntegracaoTest.
        produto.registrarEntrada(quantidade);

        Produto salvo = produtoRepository.saveAndFlush(produto);
        produtosCriados.add(salvo.getId());

        return salvo.getId();
    }

    protected Long criarProduto(int quantidade, int estoqueMinimo) {
        Produto produto = new Produto(
                "CONC-" + UUID.randomUUID(),
                "Produto de teste",
                new BigDecimal("10.00"),
                estoqueMinimo);

        // Saldo inicial sem gerar histórico; zero não passa por registrarEntrada.
        if (quantidade > 0) {
            produto.registrarEntrada(quantidade);
        }

        Produto salvo = produtoRepository.saveAndFlush(produto);
        produtosCriados.add(salvo.getId());

        return salvo.getId();
    }

    protected int saldo(Long produtoId) {
        return jdbc.queryForObject(
                "SELECT quantidade_estoque FROM produtos WHERE id = ?",
                Integer.class,
                produtoId);
    }

    protected int quantidadeMovimentacoes(Long produtoId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimentacoes_estoque WHERE produto_id = ?",
                Integer.class,
                produtoId);
    }

    protected String statusDoPedido(Long pedidoId) {
        return jdbc.queryForObject(
                "SELECT status FROM pedidos WHERE id = ?",
                String.class,
                pedidoId);
    }

    // Cada registro deve partir do saldo em que o anterior terminou.
    // Se houve atualização perdida, a cadeia quebra.
    protected void verificarCadeiaDeSaldos(Long produtoId, int saldoInicial) {
        List<int[]> registros = jdbc.query(
                """
                SELECT saldo_anterior, saldo_posterior
                FROM movimentacoes_estoque
                WHERE produto_id = ?
                ORDER BY id
                """,
                (rs, linha) -> new int[] {
                        rs.getInt("saldo_anterior"),
                        rs.getInt("saldo_posterior") },
                produtoId);

        int esperado = saldoInicial;

        for (int[] registro : registros) {
            assertEquals(esperado, registro[0]);
            esperado = registro[1];
        }

        assertEquals(saldo(produtoId), esperado);
    }
}