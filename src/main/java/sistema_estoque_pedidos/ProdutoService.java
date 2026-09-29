package sistema_estoque_pedidos;

import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProdutoService {

    private final ProdutoRepository repository;

    public ProdutoService(ProdutoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Produto cadastrar(CriarProdutoRequest request) {
        if (repository.existsByCodigo(request.codigo())) {
            throw codigoDuplicado();
        }

        Produto produto = new Produto(
                request.codigo(),
                request.nome(),
                request.preco(),
                request.estoqueMinimo());

        try {
            return repository.saveAndFlush(produto);
        } catch (DataIntegrityViolationException exception) {
            if (violouCodigoUnico(exception)) {
                throw codigoDuplicado();
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<Produto> listar() {
        return repository.findAll(Sort.by("id").ascending());
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Produto não encontrado."));
    }

    private ResponseStatusException codigoDuplicado() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Já existe um produto com esse código.");
    }

    private boolean violouCodigoUnico(Throwable exception) {
        Throwable causa = exception;

        while (causa != null) {
            if (causa instanceof ConstraintViolationException violacao
                    && "uk_produtos_codigo".equalsIgnoreCase(
                            violacao.getConstraintName())) {
                return true;
            }

            causa = causa.getCause();
        }

        return false;
    }
}