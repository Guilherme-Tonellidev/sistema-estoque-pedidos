package sistema_estoque_pedidos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ProdutoController.class)
@Import(TratadorDeErros.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProdutoService service;

    private static final String PRODUTO_VALIDO = """
            {
              "codigo": "MOUSE-001",
              "nome": "Mouse USB",
              "preco": 49.90,
              "estoqueMinimo": 5
            }
            """;

    @Test
    void deveCadastrarProdutoERetornar201ComLocalizacao() throws Exception {
        Produto produto = new Produto(
                "MOUSE-001",
                "Mouse USB",
                new BigDecimal("49.90"),
                5);

        // Simula o ID que seria gerado pelo banco.
        ReflectionTestUtils.setField(produto, "id", 1L);

        when(service.cadastrar(any(CriarProdutoRequest.class)))
                .thenReturn(produto);

        mockMvc.perform(post("/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUTO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/produtos/1"))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.codigo").value("MOUSE-001"))
                .andExpect(jsonPath("$.nome").value("Mouse USB"))
                .andExpect(jsonPath("$.preco").value(49.90))
                .andExpect(jsonPath("$.estoqueMinimo").value(5))
                .andExpect(jsonPath("$.quantidadeEstoque").value(0));

        verify(service).cadastrar(new CriarProdutoRequest(
                "MOUSE-001",
                "Mouse USB",
                new BigDecimal("49.90"),
                5));
    }

    @Test
    void deveRecusarPrecoNegativoSemChamarService() throws Exception {
        String json = """
                {
                  "codigo": "MOUSE-001",
                  "nome": "Mouse USB",
                  "preco": -1.00,
                  "estoqueMinimo": 5
                }
                """;

        mockMvc.perform(post("/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").isNotEmpty())
                .andExpect(jsonPath("$.caminho").value("/produtos"));

        verifyNoInteractions(service);
    }

    @Test
    void deveRecusarJsonInvalidoSemChamarService() throws Exception {
        mockMvc.perform(post("/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").isNotEmpty())
                .andExpect(jsonPath("$.caminho").value("/produtos"));

        verifyNoInteractions(service);
    }

    @Test
    void deveRetornar404QuandoProdutoNaoExiste() throws Exception {
        when(service.buscarPorId(999L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Produto não encontrado."));

        mockMvc.perform(get("/produtos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro")
                        .value("Produto não encontrado."))
                .andExpect(jsonPath("$.caminho")
                        .value("/produtos/999"));

        verify(service).buscarPorId(999L);
    }

    @Test
    void deveRetornar409QuandoServiceRecusaCodigoDuplicado()
            throws Exception {

        when(service.cadastrar(any(CriarProdutoRequest.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Código de produto já cadastrado."));

        mockMvc.perform(post("/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUTO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.erro")
                        .value("Código de produto já cadastrado."))
                .andExpect(jsonPath("$.caminho").value("/produtos"));

        verify(service).cadastrar(any(CriarProdutoRequest.class));
    }
}