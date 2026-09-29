package sistema_estoque_pedidos;

import java.math.BigDecimal;
import java.util.Locale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CriarProdutoRequest(

        @NotBlank(message = "Informe o código.")
        @Size(max = 50, message = "O código deve ter até 50 caracteres.")
        String codigo,

        @NotBlank(message = "Informe o nome.")
        @Size(max = 120, message = "O nome deve ter até 120 caracteres.")
        String nome,

        @NotNull(message = "Informe o preço.")
        @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
        @Digits(
                integer = 10,
                fraction = 2,
                message = "O preço deve ter até 10 dígitos inteiros e 2 decimais.")
        BigDecimal preco,

        @NotNull(message = "Informe o estoque mínimo.")
        @PositiveOrZero(message = "O estoque mínimo não pode ser negativo.")
        Integer estoqueMinimo) {

    public CriarProdutoRequest {
        codigo = codigo == null
                ? null
                : codigo.strip().toUpperCase(Locale.ROOT);

        nome = nome == null ? null : nome.strip();
    }
}