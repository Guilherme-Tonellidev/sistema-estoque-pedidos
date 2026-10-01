package sistema_estoque_pedidos;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "itens_pedido")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(
            name = "preco_unitario",
            nullable = false,
            precision = 12,
            scale = 2)
    private BigDecimal precoUnitario;

    protected ItemPedido() {
    }

    ItemPedido(
            Pedido pedido,
            Long produtoId,
            int quantidade,
            BigDecimal precoUnitario) {

        if (pedido == null) {
            throw new IllegalArgumentException(
                    "O item deve pertencer a um pedido.");
        }

        if (produtoId == null || produtoId <= 0) {
            throw new IllegalArgumentException(
                    "Informe um produto válido.");
        }

        if (quantidade <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade deve ser maior que zero.");
        }

        if (precoUnitario == null || precoUnitario.signum() < 0) {
            throw new IllegalArgumentException(
                    "O preço unitário não pode ser negativo.");
        }

        BigDecimal precoNormalizado;

        try {
            precoNormalizado = precoUnitario.setScale(
                    2,
                    RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "O preço unitário deve ter até 2 casas decimais.");
        }

        if (precoNormalizado.precision() > 12) {
            throw new IllegalArgumentException(
                    "O preço unitário ultrapassa o limite permitido.");
        }

        this.pedido = pedido;
        this.produtoId = produtoId;
        this.quantidade = quantidade;
        this.precoUnitario = precoNormalizado;
    }

    public BigDecimal getSubtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public Long getId() {
        return id;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }
}