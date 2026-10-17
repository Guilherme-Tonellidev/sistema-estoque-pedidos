from decimal import Decimal

from rich.text import Text
from textual import work
from textual.containers import Horizontal, VerticalScroll
from textual.screen import ModalScreen
from textual.widgets import Button, DataTable, Input, Select, Static

from app import chamar_api, data_local, inteiro


def dinheiro(valor):
    return f"R$ {Decimal(str(valor)):.2f}".replace(".", ",")


class TelaPedido(ModalScreen):
    BINDINGS = [("escape", "voltar", "Voltar")]

    DEFAULT_CSS = """
    TelaPedido {
        align: center middle;
        background: $background 75%;
    }

    TelaPedido .janela-pedido {
        width: 95%;
        height: 90%;
        padding: 1 2;
        border: round #65baff;
        background: #182438;
    }

    TelaPedido .mensagem-pedido {
        height: auto;
        margin: 1 0;
        color: #ffbf69;
    }

    TelaPedido .tabela-pedido {
        height: 1fr;
        min-height: 5;
    }

    TelaPedido Button {
        min-width: 12;
        margin: 0 1;
    }

    TelaPedido Select {
        margin-bottom: 1;
    }

    #itens-novos {
        height: 8;
    }

    #total-previsto {
        height: auto;
        margin-top: 1;
        color: #8bd5ff;
    }

    #texto-detalhes {
        height: auto;
        margin-bottom: 1;
    }
    """

    def __init__(self):
        super().__init__()
        self.ocupado = False

    def action_voltar(self):
        if not self.ocupado:
            self.dismiss(False)

    def bloquear(self, valor):
        self.ocupado = valor
        for botao in self.query(Button):
            botao.disabled = valor


class Confirmacao(ModalScreen):
    BINDINGS = [("escape", "voltar", "Voltar")]

    DEFAULT_CSS = """
    Confirmacao {
        align: center middle;
        background: $background 80%;
    }

    #confirmacao-pedido {
        width: 70;
        max-width: 95%;
        height: auto;
        max-height: 90%;
        padding: 1 2;
        border: round #ffbf69;
        background: #182438;
    }
    """

    def __init__(self, mensagem):
        super().__init__()
        self.mensagem = mensagem

    def compose(self):
        with VerticalScroll(id="confirmacao-pedido"):
            yield Static("CONFIRMAR OPERAÇÃO", classes="titulo")
            yield Static(self.mensagem, markup=False)

            with Horizontal(classes="botoes"):
                yield Button("Prosseguir", id="prosseguir", variant="warning")
                yield Button("Voltar", id="desistir")

    def action_voltar(self):
        self.dismiss(False)

    def on_button_pressed(self, evento: Button.Pressed):
        evento.stop()
        self.dismiss(evento.button.id == "prosseguir")


class NovoPedido(TelaPedido):
    def __init__(self):
        super().__init__()
        self.produtos = {}
        self.itens = {}

    def compose(self):
        with VerticalScroll(classes="janela-pedido"):
            yield Static("NOVO PEDIDO", classes="titulo")

            yield Static("Cliente")
            yield Input(id="cliente-pedido", max_length=120)

            yield Static("Produto")
            yield Select(
                [],
                prompt="Selecione um produto",
                id="produto-pedido",
            )

            yield Static("Quantidade")
            yield Input(value="1", id="quantidade-pedido")

            with Horizontal(classes="botoes"):
                yield Button("Adicionar item", id="adicionar-item")
                yield Button("Remover item", id="remover-item")

            yield DataTable(
                id="itens-novos",
                cursor_type="row",
            )

            yield Static("Total estimado: R$ 0,00", id="total-previsto")
            yield Static(
                "Carregando produtos...",
                id="mensagem-novo",
                classes="mensagem-pedido",
                markup=False,
            )

            with Horizontal(classes="botoes"):
                yield Button(
                    "Criar rascunho",
                    id="salvar-pedido",
                    variant="primary",
                )
                yield Button("Voltar", id="voltar-novo")

    def on_mount(self):
        self.query_one("#itens-novos", DataTable).add_columns(
            "ID", "Produto", "Quantidade", "Preço", "Subtotal"
        )
        self.carregar_produtos()

    @work(exclusive=True)
    async def carregar_produtos(self):
        mensagem = self.query_one("#mensagem-novo", Static)

        try:
            produtos = await chamar_api("GET", "/produtos")
        except ValueError as erro:
            mensagem.update(str(erro))
            return

        self.produtos = {p["id"]: p for p in produtos}

        self.query_one("#produto-pedido", Select).set_options(
            [
                (
                    Text(
                        f"{p['codigo']} — {p['nome']} "
                        f"• saldo {p['quantidadeEstoque']} "
                        f"• {dinheiro(p['preco'])}"
                    ),
                    p["id"],
                )
                for p in produtos
            ]
        )

        mensagem.update(
            "Adicione os itens. O estoque será descontado na confirmação."
            if produtos
            else "Cadastre um produto na tela de estoque primeiro."
        )

    def atualizar_itens(self):
        tabela = self.query_one("#itens-novos", DataTable)
        tabela.clear()
        total = Decimal("0")

        for produto_id, quantidade in self.itens.items():
            produto = self.produtos[produto_id]
            preco = Decimal(str(produto["preco"]))
            subtotal = preco * quantidade
            total += subtotal

            tabela.add_row(
                str(produto_id),
                Text(produto["nome"]),
                str(quantidade),
                dinheiro(preco),
                dinheiro(subtotal),
                key=str(produto_id),
            )

        self.query_one("#total-previsto", Static).update(
            f"Total estimado: {dinheiro(total)}"
        )

    async def on_button_pressed(self, evento: Button.Pressed):
        evento.stop()

        if self.ocupado:
            return

        acao = evento.button.id
        mensagem = self.query_one("#mensagem-novo", Static)

        if acao == "voltar-novo":
            self.action_voltar()
            return

        if acao == "adicionar-item":
            produto_id = self.query_one("#produto-pedido", Select).value

            if produto_id not in self.produtos:
                mensagem.update("Selecione um produto.")
                return

            try:
                quantidade = inteiro(
                    self.query_one("#quantidade-pedido", Input).value.strip(),
                    1,
                )

                if produto_id in self.itens:
                    raise ValueError(
                        "Esse produto já está no pedido. "
                        "Remova o item para alterar a quantidade."
                    )

                if len(self.itens) >= 100:
                    raise ValueError("O pedido aceita até 100 produtos.")

            except ValueError as erro:
                mensagem.update(str(erro))
                return

            self.itens[produto_id] = quantidade
            self.atualizar_itens()
            mensagem.update("Item adicionado.")
            return

        if acao == "remover-item":
            tabela = self.query_one("#itens-novos", DataTable)

            if tabela.row_count == 0:
                mensagem.update("Não há itens para remover.")
                return

            produto_id = int(tabela.get_row_at(tabela.cursor_row)[0])
            del self.itens[produto_id]
            self.atualizar_itens()
            mensagem.update("Item removido.")
            return

        if acao != "salvar-pedido":
            return

        cliente = self.query_one("#cliente-pedido", Input).value.strip()

        if not cliente:
            mensagem.update("Informe o nome do cliente.")
            return

        if not self.itens:
            mensagem.update("Adicione pelo menos um item.")
            return

        dados = {
            "cliente": cliente,
            "itens": [
                {"produtoId": produto_id, "quantidade": quantidade}
                for produto_id, quantidade in self.itens.items()
            ],
        }

        self.bloquear(True)
        mensagem.update("Criando pedido...")

        try:
            pedido = await chamar_api("POST", "/pedidos", dados)
        except ValueError as erro:
            mensagem.update(str(erro))
            self.bloquear(False)
        else:
            self.ocupado = False
            self.app.notify(
                f"Pedido #{pedido['id']} criado em rascunho. "
                f"Total: {dinheiro(pedido['total'])}",
                title="Pedido criado",
            )
            self.dismiss(True)


class DetalhePedido(TelaPedido):
    def __init__(self, pedido_id):
        super().__init__()
        self.pedido_id = pedido_id
        self.pedido = None

    def compose(self):
        with VerticalScroll(classes="janela-pedido"):
            yield Static(
                f"PEDIDO #{self.pedido_id}",
                classes="titulo",
            )
            yield Static("", id="texto-detalhes", markup=False)
            yield DataTable(
                id="itens-detalhes",
                classes="tabela-pedido",
                cursor_type="row",
            )
            yield Static(
                "Carregando pedido...",
                id="mensagem-detalhes",
                classes="mensagem-pedido",
                markup=False,
            )

            with Horizontal(classes="botoes"):
                yield Button(
                    "Confirmar pedido",
                    id="confirmar-pedido",
                    variant="success",
                    disabled=True,
                )
                yield Button(
                    "Cancelar pedido",
                    id="cancelar-pedido",
                    variant="warning",
                    disabled=True,
                )
                yield Button("Atualizar", id="atualizar-detalhes")
                yield Button("Voltar", id="voltar-detalhes")

    def on_mount(self):
        self.query_one("#itens-detalhes", DataTable).add_columns(
            "Produto ID", "Quantidade", "Preço registrado", "Subtotal"
        )
        self.carregar()

    def mostrar(self):
        pedido = self.pedido

        self.query_one("#texto-detalhes", Static).update(
            f"Cliente: {pedido['cliente']}\n"
            f"Status: {pedido['status']}\n"
            f"Total: {dinheiro(pedido['total'])}\n"
            f"Criação: {data_local(pedido['dataCriacao'])}\n"
            f"Confirmação: {data_local(pedido.get('dataConfirmacao'))}\n"
            f"Cancelamento: {data_local(pedido.get('dataCancelamento'))}"
        )

        tabela = self.query_one("#itens-detalhes", DataTable)
        tabela.clear()

        for item in pedido["itens"]:
            tabela.add_row(
                str(item["produtoId"]),
                str(item["quantidade"]),
                dinheiro(item["precoUnitario"]),
                dinheiro(item["subtotal"]),
            )

    def liberar_botoes(self):
        self.bloquear(False)

        self.query_one("#confirmar-pedido", Button).disabled = (
            self.pedido is None
            or self.pedido["status"] != "RASCUNHO"
        )
        self.query_one("#cancelar-pedido", Button).disabled = (
            self.pedido is None
            or self.pedido["status"] == "CANCELADO"
        )

    @work(exclusive=True)
    async def carregar(self):
        self.bloquear(True)
        mensagem = self.query_one("#mensagem-detalhes", Static)
        mensagem.update("Consultando pedido...")

        try:
            self.pedido = await chamar_api(
                "GET", f"/pedidos/{self.pedido_id}"
            )
        except ValueError as erro:
            self.pedido = None
            mensagem.update(str(erro))
        else:
            self.mostrar()
            mensagem.update("Pedido atualizado.")
        finally:
            self.liberar_botoes()

    def on_button_pressed(self, evento: Button.Pressed):
        evento.stop()

        if self.ocupado:
            return

        acao = evento.button.id

        if acao == "voltar-detalhes":
            self.action_voltar()
            return

        if acao == "atualizar-detalhes":
            self.carregar()
            return

        if self.pedido is None:
            return

        if acao == "confirmar-pedido":
            operacao = "confirmacao"
            texto = (
                f"Confirmar o pedido #{self.pedido_id}?\n\n"
                "A API verificará o saldo de todos os itens. "
                "Se houver estoque suficiente, as quantidades "
                "serão descontadas."
            )
        elif acao == "cancelar-pedido":
            operacao = "cancelamento"
            texto = (
                f"Cancelar o pedido #{self.pedido_id}?\n\n"
                "Se estiver confirmado, os itens voltarão ao estoque. "
                "Se estiver em rascunho, não haverá movimentação. "
                "O pedido cancelado não poderá ser confirmado novamente."
            )
        else:
            return

        def resposta(aceitou):
            if aceitou:
                self.executar_operacao(operacao)

        self.app.push_screen(Confirmacao(texto), resposta)

    @work(exclusive=True)
    async def executar_operacao(self, operacao):
        self.bloquear(True)
        mensagem = self.query_one("#mensagem-detalhes", Static)
        mensagem.update("Enviando operação...")

        try:
            self.pedido = await chamar_api(
                "PATCH",
                f"/pedidos/{self.pedido_id}/{operacao}",
            )
        except ValueError as erro:
            mensagem.update(str(erro))
        else:
            self.mostrar()
            mensagem.update("Operação concluída pela API.")
            self.app.notify(
                f"Pedido #{self.pedido_id}: {self.pedido['status']}",
                title="Sucesso",
            )
        finally:
            self.liberar_botoes()


class Pedidos(TelaPedido):
    def __init__(self):
        super().__init__()
        self.pedidos = {}

    def compose(self):
        with VerticalScroll(classes="janela-pedido"):
            yield Static("PEDIDOS", classes="titulo")
            yield Static(
                "Carregando pedidos...",
                id="mensagem-lista",
                classes="mensagem-pedido",
                markup=False,
            )
            yield DataTable(
                id="tabela-pedidos",
                classes="tabela-pedido",
                cursor_type="row",
            )

            with Horizontal(classes="botoes"):
                yield Button(
                    "Novo pedido",
                    id="novo-pedido",
                    variant="primary",
                )
                yield Button("Abrir pedido", id="abrir-pedido")
                yield Button("Atualizar", id="atualizar-pedidos")
                yield Button("Voltar", id="voltar-pedidos")

    def on_mount(self):
        tabela = self.query_one("#tabela-pedidos", DataTable)
        tabela.add_columns("ID", "Cliente", "Status", "Total")
        tabela.focus()
        self.carregar()

    @work(exclusive=True)
    async def carregar(self):
        tabela = self.query_one("#tabela-pedidos", DataTable)
        mensagem = self.query_one("#mensagem-lista", Static)

        tabela.disabled = True
        tabela.clear()
        self.pedidos = {}
        mensagem.update("Consultando pedidos...")

        try:
            pedidos = await chamar_api("GET", "/pedidos")
        except ValueError as erro:
            mensagem.update(str(erro))
        else:
            for pedido in pedidos:
                chave = str(pedido["id"])
                self.pedidos[chave] = pedido

                tabela.add_row(
                    chave,
                    Text(pedido["cliente"]),
                    pedido["status"],
                    dinheiro(pedido["total"]),
                    key=chave,
                )

            mensagem.update(
                f"{len(pedidos)} pedido(s). "
                "Selecione e abra para consultar, confirmar ou cancelar."
            )
        finally:
            tabela.disabled = False

    def atualizar_apos_tela(self, resultado):
        self.carregar()

    def on_button_pressed(self, evento: Button.Pressed):
        evento.stop()
        acao = evento.button.id

        if acao == "voltar-pedidos":
            self.action_voltar()
            return

        if acao == "atualizar-pedidos":
            self.carregar()
            return

        if acao == "novo-pedido":
            self.app.push_screen(
                NovoPedido(),
                self.atualizar_apos_tela,
            )
            return

        if acao != "abrir-pedido":
            return

        tabela = self.query_one("#tabela-pedidos", DataTable)

        if tabela.disabled or tabela.row_count == 0:
            self.app.notify(
                "Selecione um pedido carregado.",
                severity="warning",
            )
            return

        pedido_id = int(tabela.get_row_at(tabela.cursor_row)[0])

        self.app.push_screen(
            DetalhePedido(pedido_id),
            self.atualizar_apos_tela,
        )