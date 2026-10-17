package sistema_estoque_pedidos;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AutenticacaoService {

    public record Token(String valor, Instant expiraEm) {
    }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final JwtEncoder jwtEncoder;
    private final Duration validade;

    public AutenticacaoService(
            UsuarioRepository usuarios,
            PasswordEncoder codificador,
            JwtEncoder jwtEncoder,
            @Value("${estoque.jwt.validade-horas}") long validadeHoras) {

        this.usuarios = usuarios;
        this.codificador = codificador;
        this.jwtEncoder = jwtEncoder;
        this.validade = Duration.ofHours(validadeHoras);
    }

    public Token login(String usuario, String senha) {
        // Usuário inexistente e senha errada recebem a mesma mensagem.
        Usuario encontrado = usuarios.findByUsuario(usuario)
                .filter(u -> codificador.matches(senha, u.getSenhaHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuário ou senha inválidos."));

        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(validade);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(encontrado.getUsuario())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .build();

        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();

        String valor = jwtEncoder
                .encode(JwtEncoderParameters.from(cabecalho, claims))
                .getTokenValue();

        return new Token(valor, expiraEm);
    }
}