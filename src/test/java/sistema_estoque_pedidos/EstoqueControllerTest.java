package sistema_estoque_pedidos;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(EstoqueController.class)
@Import(TratadorDeErros.class)
class EstoqueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EstoqueService service;

    @Test
    void deveRegistrarEntradaERetornar201() throws Exception {
        RegistrarEntradaRequest request =
                new RegistrarEntradaRequest(10, "Recebimento inicial");

        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                1L, "ENTRADA", 10, 0, 10, "Recebimento inicial");

        when(service.registrarEntrada(1L, request))
                .thenReturn(movimentacao);

        mockMvc.perform(post("/produtos/1/entradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantidade": 10,
                                  "motivo": "Recebimento inicial"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.produtoId").value(1))
                .andExpect(jsonPath("$.tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.quantidade").value(10))
                .andExpect(jsonPath("$.saldoAnterior").value(0))
                .andExpect(jsonPath("$.saldoPosterior").value(10))
                .andExpect(jsonPath("$.motivo").value("Recebimento inicial"))
                .andExpect(jsonPath("$.dataMovimentacao").isNotEmpty());

        verify(service).registrarEntrada(1L, request);
    }

    @Test
    void deveRegistrarSaidaERetornar201() throws Exception {
        RegistrarSaidaRequest request =
                new RegistrarSaidaRequest(3, "Uso interno");

        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                1L, "SAIDA", 3, 10, 7, "Uso interno");

        when(service.registrarSaida(1L, request))
                .thenReturn(movimentacao);

        mockMvc.perform(post("/produtos/1/saidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantidade": 3,
                                  "motivo": "Uso interno"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produtoId").value(1))
                .andExpect(jsonPath("$.tipo").value("SAIDA"))
                .andExpect(jsonPath("$.quantidade").value(3))
                .andExpect(jsonPath("$.saldoAnterior").value(10))
                .andExpect(jsonPath("$.saldoPosterior").value(7))
                .andExpect(jsonPath("$.motivo").value("Uso interno"));

        verify(service).registrarSaida(1L, request);
    }

    @Test
    void deveRecusarEntradaComQuantidadeZero() throws Exception {
        mockMvc.perform(post("/produtos/1/entradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantidade": 0,
                                  "motivo": "Recebimento inicial"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").isNotEmpty())
                .andExpect(jsonPath("$.caminho")
                        .value("/produtos/1/entradas"));

        verifyNoInteractions(service);
    }

    @Test
    void deveRecusarSaidaComQuantidadeNegativa() throws Exception {
        mockMvc.perform(post("/produtos/1/saidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantidade": -1,
                                  "motivo": "Uso interno"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").isNotEmpty())
                .andExpect(jsonPath("$.caminho")
                        .value("/produtos/1/saidas"));

        verifyNoInteractions(service);
    }

    @Test
    void deveRecusarEntradaSemMotivo() throws Exception {
        mockMvc.perform(post("/produtos/1/entradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantidade": 10,
                                  "motivo": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").isNotEmpty())
                .andExpect(jsonPath("$.caminho")
                        .value("/produtos/1/entradas"));

        verifyNoInteractions(service);
    }

    @Test
    void deveRetornar409QuandoSaidaForRecusada() throws Exception {
        RegistrarSaidaRequest request =
                new RegistrarSaidaRequest(8, "Uso interno");

        when(service.registrarSaida(1L, request))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Estoque insuficiente. Saldo disponível: 7."));

        mockMvc.perform(post("/produtos/1/saidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantidade": 8,
                                  "motivo": "Uso interno"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.erro")
                        .value("Estoque insuficiente. Saldo disponível: 7."))
                .andExpect(jsonPath("$.caminho")
                        .value("/produtos/1/saidas"));

        verify(service).registrarSaida(1L, request);
    }

    @Test
    void deveRetornar404NaEntradaDeProdutoInexistente() throws Exception {
        RegistrarEntradaRequest request =
                new RegistrarEntradaRequest(10, "Recebimento inicial");

        when(service.registrarEntrada(999L, request))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Produto não encontrado."));

        mockMvc.perform(post("/produtos/999/entradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantidade": 10,
                                  "motivo": "Recebimento inicial"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro")
                        .value("Produto não encontrado."))
                .andExpect(jsonPath("$.caminho")
                        .value("/produtos/999/entradas"));

        verify(service).registrarEntrada(999L, request);
    }

    @Test
    void deveListarMovimentacoesERetornar200() throws Exception {
        MovimentacaoEstoque saida = new MovimentacaoEstoque(
                1L, "SAIDA", 3, 10, 7, "Uso interno");

        MovimentacaoEstoque entrada = new MovimentacaoEstoque(
                1L, "ENTRADA", 10, 0, 10, "Recebimento inicial");

        when(service.listarMovimentacoes(1L))
                .thenReturn(List.of(saida, entrada));

        mockMvc.perform(get("/produtos/1/movimentacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].produtoId").value(1))
                .andExpect(jsonPath("$[0].tipo").value("SAIDA"))
                .andExpect(jsonPath("$[0].quantidade").value(3))
                .andExpect(jsonPath("$[0].saldoPosterior").value(7))
                .andExpect(jsonPath("$[1].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$[1].quantidade").value(10));

        verify(service).listarMovimentacoes(1L);
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHaMovimentacoes() throws Exception {
        when(service.listarMovimentacoes(1L))
                .thenReturn(List.of());

        mockMvc.perform(get("/produtos/1/movimentacoes"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(service).listarMovimentacoes(1L);
    }

    @Test
    void deveRetornar404NoHistoricoDeProdutoInexistente() throws Exception {
        when(service.listarMovimentacoes(999L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Produto não encontrado."));

        mockMvc.perform(get("/produtos/999/movimentacoes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro")
                        .value("Produto não encontrado."))
                .andExpect(jsonPath("$.caminho")
                        .value("/produtos/999/movimentacoes"));

        verify(service).listarMovimentacoes(999L);
    }
}