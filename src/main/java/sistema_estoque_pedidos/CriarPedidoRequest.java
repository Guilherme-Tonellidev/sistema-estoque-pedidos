package sistema_estoque_pedidos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CriarPedidoRequest(

        @NotBlank(message = "Informe o nome do cliente.")
        @Size(
                max = 120,
                message = "O nome do cliente deve ter até 120 caracteres.")
        String cliente,

        @NotEmpty(message = "Informe pelo menos um item.")
        @Size(
                max = 100,
                message = "O pedido pode ter até 100 produtos diferentes.")
        List<
                @NotNull(message = "O item não pode ser nulo.")
                @Valid ItemRequest> itens) {

    public CriarPedidoRequest {
        cliente = cliente == null ? null : cliente.strip();
    }

    public record ItemRequest(

            @NotNull(message = "Informe o produto.")
            @Positive(message = "O ID do produto deve ser maior que zero.")
            Long produtoId,

            @NotNull(message = "Informe a quantidade.")
            @Positive(message = "A quantidade deve ser maior que zero.")
            Integer quantidade) {
    }
}