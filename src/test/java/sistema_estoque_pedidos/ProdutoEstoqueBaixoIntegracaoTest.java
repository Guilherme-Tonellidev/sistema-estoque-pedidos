package sistema_estoque_pedidos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProdutoEstoqueBaixoIntegracaoTest extends ConcorrenciaTestBase {

    @Autowired
    private ProdutoService produtoService;

    @Test
    void incluiProdutosNoLimiteOuAbaixoEIgnoraOsAcimaDele() {
        Long abaixo = criarProduto(2, 5);
        Long noLimite = criarProduto(5, 5);
        Long acima = criarProduto(9, 5);

        List<Long> ids = produtoService.listarEstoqueBaixo()
                .stream()
                .map(Produto::getId)
                .toList();

        assertTrue(ids.contains(abaixo));
        assertTrue(ids.contains(noLimite));
        assertFalse(ids.contains(acima));
    }

    @Test
    void retornaosProdutosEmOrdemDeId() {
        Long primeiro = criarProduto(0, 3);
        Long segundo = criarProduto(1, 3);

        List<Long> ids = produtoService.listarEstoqueBaixo()
                .stream()
                .map(Produto::getId)
                .toList();

        assertTrue(ids.indexOf(primeiro) < ids.indexOf(segundo));
    }
}