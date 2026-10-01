package sistema_estoque_pedidos;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @PostMapping
    @ApiResponse(
            responseCode = "201",
            description = "Pedido criado em rascunho.")
    public ResponseEntity<PedidoResposta> cadastrar(
            @Valid @RequestBody CriarPedidoRequest request) {

        PedidoResposta pedido = service.cadastrar(request);

        return ResponseEntity
                .created(URI.create("/pedidos/" + pedido.id()))
                .body(pedido);
    }

    @PatchMapping("/{id}/confirmacao")
    public PedidoResposta confirmar(@PathVariable("id") Long id) {
        return service.confirmar(id);
    }

    @PatchMapping("/{id}/cancelamento")
    public PedidoResposta cancelar(@PathVariable("id") Long id) {
        return service.cancelar(id);
    }

    @GetMapping
    public List<PedidoResposta> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public PedidoResposta buscarPorId(@PathVariable("id") Long id) {
        return service.buscarPorId(id);
    }
}