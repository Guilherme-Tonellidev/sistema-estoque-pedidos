package sistema_estoque_pedidos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class EstoqueConcorrenciaTest extends ConcorrenciaTestBase {

    @Autowired
    private EstoqueService estoqueService;

    @Test
    void saidasSimultaneasNaoPerdemAtualizacoesNemDeixamSaldoNegativo()
            throws Exception {

        Long produtoId = criarProdutoComEstoque(100);

        List<Boolean> resultados = ExecucaoSimultanea.repetir(50, () -> {
            try {
                estoqueService.registrarSaida(
                        produtoId,
                        new RegistrarSaidaRequest(3, "Teste de concorrência"));
                return true;
            } catch (ResponseStatusException exception) {
                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                return false;
            }
        });

        long sucessos = resultados.stream().filter(r -> r).count();

        // 100 / 3 = 33 saídas cabem, sobra 1 unidade.
        assertEquals(33, sucessos);
        assertEquals(1, saldo(produtoId));
        assertEquals(33, quantidadeMovimentacoes(produtoId));
        verificarCadeiaDeSaldos(produtoId, 100);
    }
}