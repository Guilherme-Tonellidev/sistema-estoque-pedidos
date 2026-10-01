package sistema_estoque_pedidos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PedidoResposta(
        Long id,
        String cliente,
        StatusPedido status,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataConfirmacao,
        OffsetDateTime dataCancelamento,
        List<ItemResposta> itens,
        BigDecimal total) {

    public static PedidoResposta de(Pedido pedido) {
        List<ItemResposta> itens = pedido.getItens()
                .stream()
                .map(item -> new ItemResposta(
                        item.getId(),
                        item.getProdutoId(),
                        item.getQuantidade(),
                        item.getPrecoUnitario(),
                        item.getSubtotal()))
                .toList();

        return new PedidoResposta(
                pedido.getId(),
                pedido.getCliente(),
                pedido.getStatus(),
                pedido.getDataCriacao(),
                pedido.getDataConfirmacao(),
                pedido.getDataCancelamento(),
                itens,
                pedido.getTotal());
    }

    public record ItemResposta(
            Long id,
            Long produtoId,
            Integer quantidade,
            BigDecimal precoUnitario,
            BigDecimal subtotal) {
    }
}