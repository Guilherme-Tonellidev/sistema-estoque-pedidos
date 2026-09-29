package sistema_estoque_pedidos;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProdutoTest {

    private Produto novoProduto() {
        return new Produto(
                "MOUSE-001",
                "Mouse USB",
                new BigDecimal("49.90"),
                5);
    }

    @Test
    void deveSomarEntradasAoEstoqueExistente() {
        Produto produto = novoProduto();

        produto.registrarEntrada(10);
        produto.registrarEntrada(5);

        assertEquals(15, produto.getQuantidadeEstoque().intValue());
    }

    @Test
    void deveDescontarSaidaDoEstoque() {
        Produto produto = novoProduto();
        produto.registrarEntrada(10);

        produto.registrarSaida(3);

        assertEquals(7, produto.getQuantidadeEstoque().intValue());
    }

    @Test
    void devePermitirRetirarTodoOEstoque() {
        Produto produto = novoProduto();
        produto.registrarEntrada(10);

        produto.registrarSaida(10);

        assertEquals(0, produto.getQuantidadeEstoque().intValue());
    }

    @Test
    void deveRecusarSaldoInsuficienteSemAlterarEstoque() {
        Produto produto = novoProduto();
        produto.registrarEntrada(7);

        assertThrows(
                IllegalStateException.class,
                () -> produto.registrarSaida(8));

        assertEquals(7, produto.getQuantidadeEstoque().intValue());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void deveRecusarEntradaInvalidaSemAlterarEstoque(int quantidade) {
        Produto produto = novoProduto();
        produto.registrarEntrada(10);

        assertThrows(
                IllegalArgumentException.class,
                () -> produto.registrarEntrada(quantidade));

        assertEquals(10, produto.getQuantidadeEstoque().intValue());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void deveRecusarSaidaInvalidaSemAlterarEstoque(int quantidade) {
        Produto produto = novoProduto();
        produto.registrarEntrada(10);

        assertThrows(
                IllegalArgumentException.class,
                () -> produto.registrarSaida(quantidade));

        assertEquals(10, produto.getQuantidadeEstoque().intValue());
    }

    @Test
    void deveRecusarEntradaAcimaDoLimiteSemAlterarEstoque() {
        Produto produto = novoProduto();
        produto.registrarEntrada(Integer.MAX_VALUE);

        assertThrows(
                IllegalArgumentException.class,
                () -> produto.registrarEntrada(1));

        assertEquals(
                Integer.MAX_VALUE,
                produto.getQuantidadeEstoque().intValue());
    }
}