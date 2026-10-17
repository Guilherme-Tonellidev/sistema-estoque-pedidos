package sistema_estoque_pedidos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class CriadorAdministrador implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(CriadorAdministrador.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final String usuario;
    private final String senha;

    public CriadorAdministrador(
            UsuarioRepository usuarios,
            PasswordEncoder codificador,
            @Value("${estoque.admin.usuario:}") String usuario,
            @Value("${estoque.admin.senha:}") String senha) {

        this.usuarios = usuarios;
        this.codificador = codificador;
        this.usuario = usuario;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) {
            return;
        }

        if (usuario.isBlank() || senha.isBlank()) {
            log.warn("Nenhum usuário cadastrado. Defina ESTOQUE_ADMIN_USUARIO "
                    + "e ESTOQUE_ADMIN_SENHA para criar o administrador.");
            return;
        }

        usuarios.save(new Usuario(usuario, codificador.encode(senha)));
        log.info("Administrador '{}' criado.", usuario);
    }
}