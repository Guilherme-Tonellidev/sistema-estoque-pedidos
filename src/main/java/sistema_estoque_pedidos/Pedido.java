package sistema_estoque_pedidos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusPedido status;

    @Column(name = "data_criacao", nullable = false)
    private OffsetDateTime dataCriacao;

    @Column(name = "data_confirmacao")
    private OffsetDateTime dataConfirmacao;

    @Column(name = "data_cancelamento")
    private OffsetDateTime dataCancelamento;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<ItemPedido> itens = new ArrayList<>();

    protected Pedido() {
    }

    public Pedido(String cliente) {
        if (cliente == null || cliente.isBlank()) {
            throw new IllegalArgumentException(
                    "Informe o nome do cliente.");
        }

        String clienteNormalizado = cliente.strip();

        if (clienteNormalizado.length() > 120) {
            throw new IllegalArgumentException(
                    "O nome do cliente deve ter até 120 caracteres.");
        }

        this.cliente = clienteNormalizado;
        this.status = StatusPedido.RASCUNHO;
        this.dataCriacao = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void adicionarItem(
            Long produtoId,
            int quantidade,
            BigDecimal precoUnitario) {

        if (status != StatusPedido.RASCUNHO) {
            throw new IllegalStateException(
                    "Somente pedidos em rascunho podem receber itens.");
        }

        boolean produtoRepetido = itens.stream()
                .anyMatch(item -> item.getProdutoId().equals(produtoId));

        if (produtoRepetido) {
            throw new IllegalArgumentException(
                    "O mesmo produto não pode aparecer duas vezes no pedido.");
        }

        itens.add(new ItemPedido(
                this,
                produtoId,
                quantidade,
                precoUnitario));
    }

    public void confirmar() {
        if (status != StatusPedido.RASCUNHO) {
            throw new IllegalStateException(
                    "Somente pedidos em rascunho podem ser confirmados.");
        }

        if (itens.isEmpty()) {
            throw new IllegalStateException(
                    "O pedido precisa ter pelo menos um item.");
        }

        status = StatusPedido.CONFIRMADO;
        dataConfirmacao = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void cancelar() {
        if (status == StatusPedido.CANCELADO) {
            throw new IllegalStateException(
                    "O pedido já está cancelado.");
        }

        status = StatusPedido.CANCELADO;
        dataCancelamento = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public BigDecimal getTotal() {
        return itens.stream()
                .map(ItemPedido::getSubtotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public OffsetDateTime getDataCriacao() {
        return dataCriacao;
    }

    public OffsetDateTime getDataConfirmacao() {
        return dataConfirmacao;
    }

    public OffsetDateTime getDataCancelamento() {
        return dataCancelamento;
    }

    public List<ItemPedido> getItens() {
        return List.copyOf(itens);
    }
}