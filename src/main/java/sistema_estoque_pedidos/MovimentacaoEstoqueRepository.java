package sistema_estoque_pedidos;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimentacaoEstoqueRepository
        extends JpaRepository<MovimentacaoEstoque, Long> {

    List<MovimentacaoEstoque>
            findTop50ByProdutoIdOrderByDataMovimentacaoDescIdDesc(Long produtoId);
}