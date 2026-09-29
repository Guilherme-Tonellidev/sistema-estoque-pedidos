package sistema_estoque_pedidos;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "movimentacoes_estoque")
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "saldo_anterior", nullable = false)
    private Integer saldoAnterior;

    @Column(name = "saldo_posterior", nullable = false)
    private Integer saldoPosterior;

    @Column(nullable = false, length = 255)
    private String motivo;

    @Column(name = "data_movimentacao", nullable = false)
    private OffsetDateTime dataMovimentacao;

    protected MovimentacaoEstoque() {
    }

    public MovimentacaoEstoque(
            Long produtoId,
            String tipo,
            Integer quantidade,
            Integer saldoAnterior,
            Integer saldoPosterior,
            String motivo) {
        this.produtoId = produtoId;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.saldoAnterior = saldoAnterior;
        this.saldoPosterior = saldoPosterior;
        this.motivo = motivo;
        this.dataMovimentacao = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    public String getTipo() {
        return tipo;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public Integer getSaldoAnterior() {
        return saldoAnterior;
    }

    public Integer getSaldoPosterior() {
        return saldoPosterior;
    }

    public String getMotivo() {
        return motivo;
    }

    public OffsetDateTime getDataMovimentacao() {
        return dataMovimentacao;
    }
}