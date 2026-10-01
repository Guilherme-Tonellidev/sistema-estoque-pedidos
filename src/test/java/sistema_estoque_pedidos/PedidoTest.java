package sistema_estoque_pedidos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PedidoTest {

    private Pedido pedidoComItem() {
        Pedido pedido = new Pedido("Cliente de teste");
        pedido.adicionarItem(1L, 2, new BigDecimal("49.90"));
        return pedido;
    }

    @Test
    void deveCalcularTotalComVariosProdutos() {
        Pedido pedido = pedidoComItem();
        pedido.adicionarItem(2L, 3, new BigDecimal("10.50"));

        assertEquals(2, pedido.getItens().size());
        assertEquals(
                new BigDecimal("99.80"),
                pedido.getItens().get(0).getSubtotal());
        assertEquals(
                new BigDecimal("31.50"),
                pedido.getItens().get(1).getSubtotal());
        assertEquals(new BigDecimal("131.30"), pedido.getTotal());
    }

    @Test
    void deveConfirmarRascunhoComItens() {
        Pedido pedido = pedidoComItem();

        assertEquals(StatusPedido.RASCUNHO, pedido.getStatus());
        assertNull(pedido.getDataConfirmacao());

        pedido.confirmar();

        assertEquals(StatusPedido.CONFIRMADO, pedido.getStatus());
        assertNotNull(pedido.getDataConfirmacao());
        assertNull(pedido.getDataCancelamento());
        assertEquals(new BigDecimal("99.80"), pedido.getTotal());
    }

    @Test
    void deveRecusarConfirmacaoSemItens() {
        Pedido pedido = new Pedido("Cliente de teste");

        assertThrows(IllegalStateException.class, pedido::confirmar);

        assertEquals(StatusPedido.RASCUNHO, pedido.getStatus());
        assertNull(pedido.getDataConfirmacao());
    }

    @Test
    void deveRecusarSegundaConfirmacaoSemAlterarData() {
        Pedido pedido = pedidoComItem();
        pedido.confirmar();
        OffsetDateTime dataOriginal = pedido.getDataConfirmacao();

        assertThrows(IllegalStateException.class, pedido::confirmar);

        assertEquals(StatusPedido.CONFIRMADO, pedido.getStatus());
        assertEquals(dataOriginal, pedido.getDataConfirmacao());
    }

    @Test
    void deveCancelarRascunhoSemDataDeConfirmacao() {
        Pedido pedido = pedidoComItem();

        pedido.cancelar();

        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        assertNotNull(pedido.getDataCancelamento());
        assertNull(pedido.getDataConfirmacao());
        assertEquals(new BigDecimal("99.80"), pedido.getTotal());
    }

    @Test
    void deveCancelarConfirmadoPreservandoDataEItens() {
        Pedido pedido = pedidoComItem();
        pedido.confirmar();
        OffsetDateTime dataOriginal = pedido.getDataConfirmacao();

        pedido.cancelar();

        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        assertEquals(dataOriginal, pedido.getDataConfirmacao());
        assertNotNull(pedido.getDataCancelamento());
        assertEquals(1, pedido.getItens().size());
        assertEquals(new BigDecimal("99.80"), pedido.getTotal());
    }

    @Test
    void deveRecusarSegundoCancelamentoSemAlterarData() {
        Pedido pedido = pedidoComItem();
        pedido.cancelar();
        OffsetDateTime dataOriginal = pedido.getDataCancelamento();

        assertThrows(IllegalStateException.class, pedido::cancelar);

        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        assertEquals(dataOriginal, pedido.getDataCancelamento());
    }

    @Test
    void deveRecusarConfirmacaoDePedidoCancelado() {
        Pedido pedido = pedidoComItem();
        pedido.cancelar();
        OffsetDateTime dataOriginal = pedido.getDataCancelamento();

        assertThrows(IllegalStateException.class, pedido::confirmar);

        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        assertNull(pedido.getDataConfirmacao());
        assertEquals(dataOriginal, pedido.getDataCancelamento());
    }

    @Test
    void deveRecusarProdutoRepetidoSemAlterarItensETotal() {
        Pedido pedido = pedidoComItem();

        assertThrows(
                IllegalArgumentException.class,
                () -> pedido.adicionarItem(
                        1L, 3, new BigDecimal("49.90")));

        assertEquals(1, pedido.getItens().size());
        assertEquals(new BigDecimal("99.80"), pedido.getTotal());
    }

    @Test
    void deveRecusarNovoItemAposConfirmacao() {
        Pedido pedido = pedidoComItem();
        pedido.confirmar();

        assertThrows(
                IllegalStateException.class,
                () -> pedido.adicionarItem(
                        2L, 1, new BigDecimal("20.00")));

        assertEquals(StatusPedido.CONFIRMADO, pedido.getStatus());
        assertEquals(1, pedido.getItens().size());
        assertEquals(new BigDecimal("99.80"), pedido.getTotal());
    }
}