package sistema_estoque_pedidos;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    public record LoginRequest(
            @NotBlank(message = "Informe o usuário.") String usuario,
            @NotBlank(message = "Informe a senha.") String senha) {
    }

    public record LoginResposta(String token, String tipo, Instant expiraEm) {
    }

    private final AutenticacaoService autenticacao;

    public AuthController(AutenticacaoService autenticacao) {
        this.autenticacao = autenticacao;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResposta> login(
            @Valid @RequestBody LoginRequest request) {

        AutenticacaoService.Token token =
                autenticacao.login(request.usuario(), request.senha());

        return ResponseEntity.ok(new LoginResposta(
                token.valor(),
                "Bearer",
                token.expiraEm()));
    }
}