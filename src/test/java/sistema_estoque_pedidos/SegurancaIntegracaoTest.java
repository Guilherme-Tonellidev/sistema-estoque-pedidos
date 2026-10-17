package sistema_estoque_pedidos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegurancaIntegracaoTest {

    private static final String SENHA = "senha-de-teste-123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder codificador;

    private String nomeUsuario;
    private Long usuarioId;

    @BeforeEach
    void criarUsuario() {
        nomeUsuario = "teste-" + UUID.randomUUID().toString().substring(0, 8);

        Usuario salvo = usuarios.save(new Usuario(
                nomeUsuario,
                codificador.encode(SENHA)));

        usuarioId = salvo.getId();
    }

    @AfterEach
    void removerUsuario() {
        usuarios.deleteById(usuarioId);
    }

    @Test
    void rotaProtegidaSemTokenRespondeNaoAutorizado() throws Exception {
        mvc.perform(get("/produtos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro")
                        .value("Faça login para acessar este recurso."));
    }

    @Test
    void tokenInvalidoRespondeNaoAutorizado() throws Exception {
        mvc.perform(get("/produtos")
                        .header("Authorization", "Bearer token-qualquer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginComSenhaErradaRespondeNaoAutorizado() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"usuario": "%s", "senha": "errada"}
                                """.formatted(nomeUsuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro")
                        .value("Usuário ou senha inválidos."));
    }

    @Test
    void loginComSenhaCertaPermiteAcessarRotaProtegida() throws Exception {
        String token = fazerLogin();

        mvc.perform(get("/produtos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String fazerLogin() throws Exception {
        MvcResult resultado = mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"usuario": "%s", "senha": "%s"}
                                """.formatted(nomeUsuario, SENHA)))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(
                resultado.getResponse().getContentAsString(),
                "$.token");

        assertNotNull(token);
        assertFalse(token.isBlank());

        return token;
    }
}