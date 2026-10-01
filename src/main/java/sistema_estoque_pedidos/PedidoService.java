package sistema_estoque_pedidos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;

    public PedidoService(
            PedidoRepository pedidoRepository,
            ProdutoRepository produtoRepository,
            MovimentacaoEstoqueRepository movimentacaoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    @Transactional
    public PedidoResposta cadastrar(CriarPedidoRequest request) {
        Set<Long> ids = new HashSet<>();

        for (CriarPedidoRequest.ItemRequest item : request.itens()) {
            if (!ids.add(item.produtoId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "O mesmo produto não pode aparecer duas vezes no pedido.");
            }
        }

        Map<Long, Produto> produtos = produtoRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(
                        Produto::getId,
                        Function.identity()));

        for (CriarPedidoRequest.ItemRequest item : request.itens()) {
            if (!produtos.containsKey(item.produtoId())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Produto " + item.produtoId() + " não encontrado.");
            }
        }

        Pedido pedido = new Pedido(request.cliente());

        for (CriarPedidoRequest.ItemRequest item : request.itens()) {
            Produto produto = produtos.get(item.produtoId());

            pedido.adicionarItem(
                    produto.getId(),
                    item.quantidade(),
                    produto.getPreco());
        }

        Pedido salvo = pedidoRepository.saveAndFlush(pedido);

        return PedidoResposta.de(salvo);
    }

    @Transactional
    public PedidoResposta confirmar(Long id) {
        Pedido pedido = buscarPedidoParaAtualizar(id);

        if (pedido.getStatus() != StatusPedido.RASCUNHO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Somente pedidos em rascunho podem ser confirmados.");
        }

        List<ItemPedido> itens = itensOrdenados(pedido);

        if (itens.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O pedido precisa ter pelo menos um item.");
        }

        Map<Long, Produto> produtos = bloquearProdutos(itens);

        for (ItemPedido item : itens) {
            Produto produto = produtos.get(item.getProdutoId());

            if (item.getQuantidade() > produto.getQuantidadeEstoque()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Estoque insuficiente para o produto "
                                + produto.getCodigo()
                                + ". Saldo disponível: "
                                + produto.getQuantidadeEstoque() + ".");
            }
        }

        for (ItemPedido item : itens) {
            Produto produto = produtos.get(item.getProdutoId());
            int saldoAnterior = produto.getQuantidadeEstoque();

            produto.registrarSaida(item.getQuantidade());

            registrarMovimentacao(
                    produto,
                    "SAIDA",
                    item.getQuantidade(),
                    saldoAnterior,
                    "Confirmação do pedido #" + pedido.getId());
        }

        pedido.confirmar();
        pedidoRepository.flush();

        return PedidoResposta.de(pedido);
    }

    @Transactional
    public PedidoResposta cancelar(Long id) {
        Pedido pedido = buscarPedidoParaAtualizar(id);

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O pedido já está cancelado.");
        }

        if (pedido.getStatus() == StatusPedido.CONFIRMADO) {
            List<ItemPedido> itens = itensOrdenados(pedido);
            Map<Long, Produto> produtos = bloquearProdutos(itens);

            // Confere todas as devoluções antes de alterar os saldos.
            for (ItemPedido item : itens) {
                Produto produto = produtos.get(item.getProdutoId());

                long saldoAposDevolucao =
                        (long) produto.getQuantidadeEstoque()
                                + item.getQuantidade();

                if (saldoAposDevolucao > Integer.MAX_VALUE) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "A devolução ultrapassa o limite de estoque "
                                    + "do produto " + produto.getCodigo() + ".");
                }
            }

            for (ItemPedido item : itens) {
                Produto produto = produtos.get(item.getProdutoId());
                int saldoAnterior = produto.getQuantidadeEstoque();

                produto.registrarEntrada(item.getQuantidade());

                registrarMovimentacao(
                        produto,
                        "ENTRADA",
                        item.getQuantidade(),
                        saldoAnterior,
                        "Cancelamento do pedido #" + pedido.getId());
            }
        }

        // Rascunhos são cancelados sem movimentar o estoque.
        pedido.cancelar();
        pedidoRepository.flush();

        return PedidoResposta.de(pedido);
    }

    @Transactional(readOnly = true)
    public List<PedidoResposta> listar() {
        return pedidoRepository.findAll(Sort.by("id").ascending())
                .stream()
                .map(PedidoResposta::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResposta buscarPorId(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pedido não encontrado."));

        return PedidoResposta.de(pedido);
    }

    private Pedido buscarPedidoParaAtualizar(Long id) {
        return pedidoRepository.buscarParaAtualizar(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pedido não encontrado."));
    }

    private List<ItemPedido> itensOrdenados(Pedido pedido) {
        return pedido.getItens()
                .stream()
                .sorted(Comparator.comparing(ItemPedido::getProdutoId))
                .toList();
    }

    private Map<Long, Produto> bloquearProdutos(List<ItemPedido> itens) {
        Map<Long, Produto> produtos = new HashMap<>();

        for (ItemPedido item : itens) {
            Produto produto = produtoRepository
                    .buscarParaAtualizarEstoque(item.getProdutoId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Produto " + item.getProdutoId()
                                    + " não encontrado."));

            produtos.put(produto.getId(), produto);
        }

        return produtos;
    }

    private void registrarMovimentacao(
            Produto produto,
            String tipo,
            int quantidade,
            int saldoAnterior,
            String motivo) {

        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                produto.getId(),
                tipo,
                quantidade,
                saldoAnterior,
                produto.getQuantidadeEstoque(),
                motivo);

        movimentacaoRepository.save(movimentacao);
    }
}