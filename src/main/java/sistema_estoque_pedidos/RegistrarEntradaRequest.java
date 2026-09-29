package sistema_estoque_pedidos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistrarEntradaRequest(

        @NotNull(message = "Informe a quantidade.")
        @Positive(message = "A quantidade deve ser maior que zero.")
        Integer quantidade,

        @NotBlank(message = "Informe o motivo da entrada.")
        @Size(max = 255, message = "O motivo deve ter até 255 caracteres.")
        String motivo) {

    public RegistrarEntradaRequest {
        motivo = motivo == null ? null : motivo.strip();
    }
}