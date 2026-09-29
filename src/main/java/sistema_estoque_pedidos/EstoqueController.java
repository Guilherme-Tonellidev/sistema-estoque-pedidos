package sistema_estoque_pedidos;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/produtos/{produtoId}")
public class EstoqueController {

    private final EstoqueService service;

    public EstoqueController(EstoqueService service) {
        this.service = service;
    }

    @PostMapping("/entradas")
    public ResponseEntity<MovimentacaoEstoque> registrarEntrada(
            @PathVariable("produtoId") Long produtoId,
            @Valid @RequestBody RegistrarEntradaRequest request) {

        MovimentacaoEstoque movimentacao =
                service.registrarEntrada(produtoId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(movimentacao);
    }

    @PostMapping("/saidas")
    public ResponseEntity<MovimentacaoEstoque> registrarSaida(
            @PathVariable("produtoId") Long produtoId,
            @Valid @RequestBody RegistrarSaidaRequest request) {

        MovimentacaoEstoque movimentacao =
                service.registrarSaida(produtoId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(movimentacao);
    }

    @GetMapping("/movimentacoes")
    public List<MovimentacaoEstoque> listarMovimentacoes(
            @PathVariable("produtoId") Long produtoId) {

        return service.listarMovimentacoes(produtoId);
    }
}