package sistema_estoque_pedidos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;

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

@WebMvcTest(PedidoController.class)
@Import(TratadorDeErros.class)
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService service;

    @Test
    void deveCriarPedidoERetornar201ComLocalizacao() throws Exception {
        Pedido pedido = criarPedido();

        when(service.cadastrar(any(CriarPedidoRequest.class)))
                .thenReturn(PedidoResposta.de(pedido));

        String json = """
                {
                  "cliente": "Cliente de teste",
                  "itens": [
                    {
                      "produtoId": 1,
                      "quantidade": 2
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/pedidos/1"))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cliente").value("Cliente de teste"))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].produtoId").value(1))
                .andExpect(jsonPath("$.itens[0].quantidade").value(2))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(49.90))
                .andExpect(jsonPath("$.itens[0].subtotal").value(99.80))
                .andExpect(jsonPath("$.total").value(99.80));

        verify(service).cadastrar(new CriarPedidoRequest(
                "Cliente de teste",
                List.of(new CriarPedidoRequest.ItemRequest(1L, 2))));
    }

    @Test
    void deveRecusarPedidoSemItensSemChamarService() throws Exception {
        String json = """
                {
                  "cliente": "Cliente de teste",
                  "itens": []
                }
                """;

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro")
                        .value("Informe pelo menos um item."))
                .andExpect(jsonPath("$.caminho").value("/pedidos"));

        verifyNoInteractions(service);
    }

    @Test
    void deveConfirmarPedidoERetornar200() throws Exception {
        Pedido pedido = criarPedido();
        pedido.confirmar();

        when(service.confirmar(1L))
                .thenReturn(PedidoResposta.de(pedido));

        mockMvc.perform(patch("/pedidos/1/confirmacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.dataConfirmacao").isNotEmpty())
                .andExpect(jsonPath("$.total").value(99.80));

        verify(service).confirmar(1L);
    }

    @Test
    void deveCancelarPedidoERetornar200() throws Exception {
        Pedido pedido = criarPedido();
        pedido.confirmar();
        pedido.cancelar();

        when(service.cancelar(1L))
                .thenReturn(PedidoResposta.de(pedido));

        mockMvc.perform(patch("/pedidos/1/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.dataConfirmacao").isNotEmpty())
                .andExpect(jsonPath("$.dataCancelamento").isNotEmpty())
                .andExpect(jsonPath("$.total").value(99.80));

        verify(service).cancelar(1L);
    }

    @Test
    void deveRetornar404QuandoPedidoNaoExiste() throws Exception {
        when(service.buscarPorId(999L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pedido não encontrado."));

        mockMvc.perform(get("/pedidos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro")
                        .value("Pedido não encontrado."))
                .andExpect(jsonPath("$.caminho").value("/pedidos/999"));

        verify(service).buscarPorId(999L);
    }

    @Test
    void deveRetornar409QuandoConfirmacaoForRecusada() throws Exception {
        String mensagem =
                "Somente pedidos em rascunho podem ser confirmados.";

        when(service.confirmar(1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        mensagem));

        mockMvc.perform(patch("/pedidos/1/confirmacao"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.erro").value(mensagem))
                .andExpect(jsonPath("$.caminho")
                        .value("/pedidos/1/confirmacao"));

        verify(service).confirmar(1L);
    }

    @Test
    void deveRetornar409QuandoCancelamentoForRecusado() throws Exception {
        when(service.cancelar(1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "O pedido já está cancelado."));

        mockMvc.perform(patch("/pedidos/1/cancelamento"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.erro")
                        .value("O pedido já está cancelado."))
                .andExpect(jsonPath("$.caminho")
                        .value("/pedidos/1/cancelamento"));

        verify(service).cancelar(1L);
    }

    private Pedido criarPedido() {
        Pedido pedido = new Pedido("Cliente de teste");

        pedido.adicionarItem(
                1L,
                2,
                new BigDecimal("49.90"));

        // Simula o ID gerado pelo banco.
        ReflectionTestUtils.setField(pedido, "id", 1L);

        return pedido;
    }
}