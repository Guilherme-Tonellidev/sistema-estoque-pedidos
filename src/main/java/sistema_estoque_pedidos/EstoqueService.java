package sistema_estoque_pedidos;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EstoqueService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;

    public EstoqueService(
            ProdutoRepository produtoRepository,
            MovimentacaoEstoqueRepository movimentacaoRepository) {
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    @Transactional
    public MovimentacaoEstoque registrarEntrada(
            Long produtoId,
            RegistrarEntradaRequest request) {

        Produto produto = produtoRepository
                .buscarParaAtualizarEstoque(produtoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Produto não encontrado."));

        int saldoAnterior = produto.getQuantidadeEstoque();

        try {
            produto.registrarEntrada(request.quantidade());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage());
        }

        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                produto.getId(),
                "ENTRADA",
                request.quantidade(),
                saldoAnterior,
                produto.getQuantidadeEstoque(),
                request.motivo());

        // O JPA grava a alteração do produto na mesma transação.
        return movimentacaoRepository.saveAndFlush(movimentacao);
    }

    @Transactional
    public MovimentacaoEstoque registrarSaida(
            Long produtoId,
            RegistrarSaidaRequest request) {

        Produto produto = produtoRepository
                .buscarParaAtualizarEstoque(produtoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Produto não encontrado."));

        int saldoAnterior = produto.getQuantidadeEstoque();

        try {
            produto.registrarSaida(request.quantidade());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage());
        }

        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                produto.getId(),
                "SAIDA",
                request.quantidade(),
                saldoAnterior,
                produto.getQuantidadeEstoque(),
                request.motivo());

        // Saldo e histórico são confirmados juntos.
        return movimentacaoRepository.saveAndFlush(movimentacao);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoEstoque> listarMovimentacoes(Long produtoId) {
        if (!produtoRepository.existsById(produtoId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Produto não encontrado.");
        }

        return movimentacaoRepository
                .findTop50ByProdutoIdOrderByDataMovimentacaoDescIdDesc(produtoId);
    }
}