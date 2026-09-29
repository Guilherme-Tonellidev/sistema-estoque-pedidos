package sistema_estoque_pedidos;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "produtos")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(name = "quantidade_estoque", nullable = false)
    private Integer quantidadeEstoque;

    @Column(name = "estoque_minimo", nullable = false)
    private Integer estoqueMinimo;

    protected Produto() {
    }

    public Produto(
            String codigo,
            String nome,
            BigDecimal preco,
            Integer estoqueMinimo) {
        this.codigo = codigo;
        this.nome = nome;
        this.preco = preco;
        this.quantidadeEstoque = 0;
        this.estoqueMinimo = estoqueMinimo;
    }

    public void registrarEntrada(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade deve ser maior que zero.");
        }

        long novoSaldo = (long) quantidadeEstoque + quantidade;

        if (novoSaldo > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "A entrada ultrapassa o limite de estoque do produto.");
        }

        quantidadeEstoque = (int) novoSaldo;
    }

    public void registrarSaida(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade deve ser maior que zero.");
        }

        if (quantidade > quantidadeEstoque) {
            throw new IllegalStateException(
                    "Estoque insuficiente. Saldo disponível: "
                            + quantidadeEstoque + ".");
        }

        quantidadeEstoque -= quantidade;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public Integer getQuantidadeEstoque() {
        return quantidadeEstoque;
    }

    public Integer getEstoqueMinimo() {
        return estoqueMinimo;
    }
}