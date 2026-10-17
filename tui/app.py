from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation

import httpx
from rich.text import Text
from textual import work
from textual.app import App, ComposeResult
from textual.containers import Horizontal, VerticalScroll
from textual.screen import ModalScreen
from textual.widgets import Button, DataTable, Footer, Header, Input, Static


API = "http://localhost:8081"

class SessaoExpirada(ValueError):
    pass

# Guarda o token da sessão atual. Fica só na memória do programa.
sessao = {"token": None}

async def chamar_api(metodo, rota, dados=None):
    cabecalhos = {}

    if sessao["token"]:
        cabecalhos["Authorization"] = f"Bearer {sessao['token']}"

    try:
        async with httpx.AsyncClient(timeout=15, trust_env=False) as cliente:
            resposta = await cliente.request(
                metodo,
                f"{API}{rota}",
                json=dados,
                headers=cabecalhos,
            )
    except httpx.RequestError as erro:
        raise ValueError(
            "Falha de conexão. Confira se a API está ligada. "
            "Se estava gravando, consulte os dados antes de repetir."
        ) from erro

    # Um 401 fora do login significa que a sessão não vale mais.
    if resposta.status_code == 401 and not rota.startswith("/auth/"):
        sessao["token"] = None
        raise SessaoExpirada("Sua sessão expirou. Faça login novamente.")

    if not resposta.is_success:
        mensagem = f"A API retornou HTTP {resposta.status_code}."

        try:
            corpo = resposta.json()
            if isinstance(corpo, dict):
                mensagem = corpo.get("erro") or mensagem
        except ValueError:
            pass

        raise ValueError(mensagem)

    return resposta.json()


def inteiro(texto, minimo):
    try:
        numero = int(texto)
    except ValueError:
        raise ValueError("Informe uma quantidade inteira.")

    if not minimo <= numero <= 2147483647:
        raise ValueError(
            f"Informe um inteiro entre {minimo} e 2147483647."
        )

    return numero

def data_local(valor):
    if not valor:
        return "—"

    texto = str(valor).replace("Z", "+00:00")

    # Limita a fração de segundos a 6 dígitos, que é o que o Python aceita.
    if "." in texto:
        inicio, resto = texto.split(".", 1)
        digitos = ""

        for caractere in resto:
            if not caractere.isdigit():
                break
            digitos += caractere

        texto = f"{inicio}.{digitos[:6]}{resto[len(digitos):]}"

    try:
        momento = datetime.fromisoformat(texto)
    except ValueError:
        return str(valor)

    if momento.tzinfo is None:
        momento = momento.replace(tzinfo=timezone.utc)

    return momento.astimezone().strftime("%d/%m/%Y %H:%M:%S")

class Formulario(ModalScreen):
    BINDINGS = [("escape", "voltar", "Voltar")]

    def __init__(self, operacao, produto=None):
        super().__init__()
        self.operacao = operacao
        self.produto = produto
        self.enviando = False

    def compose(self) -> ComposeResult:
        with VerticalScroll(id="formulario"):
            if self.operacao == "novo":
                yield Static("CADASTRAR PRODUTO", classes="titulo")

                yield Static("Código")
                yield Input(id="codigo", max_length=50)

                yield Static("Nome")
                yield Input(id="nome", max_length=120)

                yield Static("Preço — exemplo: 49,90")
                yield Input(id="preco")

                yield Static("Estoque mínimo")
                yield Input(value="0", id="minimo")
            else:
                titulo = (
                    "ENTRADA DE ESTOQUE"
                    if self.operacao == "entradas"
                    else "SAÍDA DE ESTOQUE"
                )

                yield Static(titulo, classes="titulo")

                yield Static(
                    f"{self.produto['codigo']} — {self.produto['nome']}",
                    markup=False,
                )

                yield Static(
                    f"Saldo consultado: "
                    f"{self.produto['quantidadeEstoque']} unidades"
                )

                yield Static("Quantidade")
                yield Input(id="quantidade")

                yield Static("Motivo")
                yield Input(id="motivo", max_length=255)

            yield Static("", id="erro", markup=False)

            with Horizontal(classes="botoes"):
                yield Button(
                    "Confirmar",
                    id="confirmar",
                    variant="primary",
                )
                yield Button("Voltar", id="voltar")

    def valor(self, campo):
        return self.query_one(f"#{campo}", Input).value.strip()

    def montar_dados(self):
        if self.operacao != "novo":
            motivo = self.valor("motivo")

            if not motivo:
                raise ValueError("Informe o motivo.")

            return {
                "quantidade": inteiro(self.valor("quantidade"), 1),
                "motivo": motivo,
            }

        codigo = self.valor("codigo")
        nome = self.valor("nome")

        if not codigo or not nome:
            raise ValueError("Informe o código e o nome.")

        try:
            preco = Decimal(self.valor("preco").replace(",", "."))
        except InvalidOperation:
            raise ValueError("Informe um preço válido.")

        if (
            not preco.is_finite()
            or not 0 <= preco <= Decimal("9999999999.99")
        ):
            raise ValueError("Preço fora do limite permitido.")

        if preco != preco.quantize(Decimal("0.01")):
            raise ValueError("O preço aceita até duas casas decimais.")

        return {
            "codigo": codigo,
            "nome": nome,
            "preco": str(preco),
            "estoqueMinimo": inteiro(self.valor("minimo"), 0),
        }

    def action_voltar(self):
        if not self.enviando:
            self.dismiss(False)

    async def on_button_pressed(self, evento: Button.Pressed):
        evento.stop()

        if evento.button.id == "voltar":
            self.action_voltar()
            return

        if evento.button.id != "confirmar" or self.enviando:
            return

        erro = self.query_one("#erro", Static)

        try:
            dados = self.montar_dados()
        except ValueError as problema:
            erro.update(str(problema))
            return

        self.enviando = True

        for botao in self.query(Button):
            botao.disabled = True

        erro.update("Enviando...")

        rota = "/produtos"

        if self.operacao != "novo":
            rota = f"/produtos/{self.produto['id']}/{self.operacao}"

        try:
            await chamar_api("POST", rota, dados)
        except ValueError as problema:
            erro.update(str(problema))
        else:
            self.dismiss(True)
        finally:
            self.enviando = False

            for botao in self.query(Button):
                botao.disabled = False


class Historico(ModalScreen):
    BINDINGS = [("escape", "voltar", "Voltar")]

    DEFAULT_CSS = """
    Historico {
        align: center middle;
        background: $background 75%;
    }

    #painel-historico {
        width: 95%;
        height: 85%;
        padding: 1 2;
        border: round #65baff;
        background: #182438;
    }

    #status-historico {
        height: auto;
        margin: 1 0;
        color: #ffbf69;
    }

    #tabela-historico {
        height: 1fr;
    }
    """

    def __init__(self, produto):
        super().__init__()
        self.produto = produto

    def compose(self) -> ComposeResult:
        with VerticalScroll(id="painel-historico"):
            yield Static("HISTÓRICO DE ESTOQUE", classes="titulo")

            yield Static(
                f"{self.produto['codigo']} — {self.produto['nome']}",
                markup=False,
            )

            yield Static(
                "Carregando movimentações...",
                id="status-historico",
                markup=False,
            )

            yield DataTable(
                id="tabela-historico",
                cursor_type="row",
            )

            with Horizontal(classes="botoes"):
                yield Button("Voltar", id="fechar-historico")

    def on_mount(self):
        tabela = self.query_one("#tabela-historico", DataTable)

        tabela.add_columns(
            "ID",
            "Tipo",
            "Quantidade",
            "Antes",
            "Depois",
            "Motivo",
            "Data e hora",
        )

        tabela.focus()
        self.carregar_historico()

    @work(exclusive=True)
    async def carregar_historico(self):
        status = self.query_one("#status-historico", Static)
        tabela = self.query_one("#tabela-historico", DataTable)
        tabela.clear()

        try:
            movimentacoes = await chamar_api(
                "GET",
                f"/produtos/{self.produto['id']}/movimentacoes",
            )
        except ValueError as problema:
            status.update(str(problema))
            return

        if not movimentacoes:
            status.update("Este produto ainda não possui movimentações.")
            return

        for movimentacao in movimentacoes:
            tabela.add_row(
                str(movimentacao["id"]),
                Text(movimentacao["tipo"]),
                str(movimentacao["quantidade"]),
                str(movimentacao["saldoAnterior"]),
                str(movimentacao["saldoPosterior"]),
                Text(movimentacao["motivo"]),
                Text(data_local(movimentacao["dataMovimentacao"])),
            )

        status.update(
            f"{len(movimentacoes)} movimentação(ões) • "
            "Até 50 registros, do mais recente para o mais antigo."
        )

    def action_voltar(self):
        self.dismiss()

    def on_button_pressed(self, evento: Button.Pressed):
        evento.stop()

        if evento.button.id == "fechar-historico":
            self.action_voltar()

class TelaLogin(ModalScreen):
    DEFAULT_CSS = """
    TelaLogin {
        align: center middle;
        background: $background 80%;
    }

    #janela-login {
        width: 60;
        max-width: 95%;
        height: auto;
        padding: 1 2;
        border: round #65baff;
        background: #182438;
    }

    #erro-login {
        height: auto;
        color: #ffbf69;
        margin-top: 1;
    }
    """

    def compose(self) -> ComposeResult:
        with VerticalScroll(id="janela-login"):
            yield Static("ENTRAR NO ESTOQUE", classes="titulo")

            yield Static("Usuário")
            yield Input(id="usuario-login", max_length=50)

            yield Static("Senha")
            yield Input(id="senha-login", password=True)

            yield Static("", id="erro-login", markup=False)

            with Horizontal(classes="botoes"):
                yield Button("Entrar", id="entrar", variant="primary")
                yield Button("Sair", id="sair-login")

    def on_mount(self):
        self.query_one("#usuario-login", Input).focus()

    def on_input_submitted(self, evento: Input.Submitted):
        evento.stop()

        if evento.input.id == "senha-login":
            self.tentar_entrar()

    def on_button_pressed(self, evento: Button.Pressed):
        evento.stop()

        if evento.button.id == "entrar":
            self.tentar_entrar()
        elif evento.button.id == "sair-login":
            self.app.exit()

    def bloquear(self, valor):
        for botao in self.query(Button):
            botao.disabled = valor

    @work(exclusive=True)
    async def tentar_entrar(self):
        erro = self.query_one("#erro-login", Static)
        usuario = self.query_one("#usuario-login", Input).value.strip()
        senha = self.query_one("#senha-login", Input).value

        if not usuario or not senha:
            erro.update("Informe o usuário e a senha.")
            return

        self.bloquear(True)
        erro.update("Entrando...")

        try:
            resposta = await chamar_api(
                "POST",
                "/auth/login",
                {"usuario": usuario, "senha": senha},
            )
        except ValueError as problema:
            erro.update(str(problema))
            self.bloquear(False)
        else:
            sessao["token"] = resposta["token"]
            self.dismiss(True)

class EstoqueApp(App):
    TITLE = "Estoque e Pedidos"
    SUB_TITLE = "Produtos e movimentações"

    BINDINGS = [
        ("f5", "atualizar", "Atualizar"),
        ("ctrl+q", "quit", "Sair"),
    ]

    CSS = """
    Screen {
        background: #101827;
    }

    #resumo {
        height: auto;
        padding: 1 2;
        color: #8bd5ff;
    }

    #produtos {
        height: 1fr;
        margin: 0 2;
        border: round #466582;
    }

    .botoes {
        height: auto;
        padding: 1;
        align-horizontal: center;
    }

    Button {
        margin: 0 1;
        min-width: 14;
    }

    Formulario {
        align: center middle;
        background: $background 75%;
    }

    #formulario {
        width: 72;
        max-width: 95%;
        height: auto;
        max-height: 95%;
        padding: 1 2;
        border: round #65baff;
        background: #182438;
    }

    .titulo {
        text-style: bold;
        color: #8bd5ff;
        margin-bottom: 1;
    }

    Input {
        margin-bottom: 1;
    }

    #erro {
        height: auto;
        color: #ffbf69;
        margin-top: 1;
    }
    """

    def __init__(self):
        super().__init__()
        self.produtos = {}

    def compose(self) -> ComposeResult:
        yield Header()

        yield Static(
            "Conectando à API...",
            id="resumo",
            markup=False,
        )

        yield DataTable(id="produtos", cursor_type="row")

        with Horizontal(classes="botoes"):
            yield Button("Cadastrar", id="novo", variant="primary")
            yield Button("Entrada", id="entradas", variant="success")
            yield Button("Saída", id="saidas", variant="warning")
            yield Button("Histórico", id="historico")
            yield Button("Atualizar", id="atualizar")

        yield Footer()

    def on_mount(self):
        tabela = self.query_one("#produtos", DataTable)

        tabela.add_columns(
            "ID",
            "Código",
            "Produto",
            "Preço (R$)",
            "Estoque",
            "Mínimo",
        )

        tabela.focus()
        self.pedir_login(self.carregar)

    def pedir_login(self, depois):
        self.push_screen(TelaLogin(), lambda _ok: depois())

    @work(exclusive=True)
    async def carregar(self):
        tabela = self.query_one("#produtos", DataTable)
        resumo = self.query_one("#resumo", Static)

        tabela.disabled = True
        tabela.clear()
        self.produtos = {}
        resumo.update("Consultando produtos...")

        try:
            produtos = await chamar_api("GET", "/produtos")
        except SessaoExpirada:
            resumo.update("Sessão expirada. Faça login novamente.")
            self.pedir_login(self.carregar)
        except ValueError as problema:
            resumo.update(str(problema))
        else:
            for produto in produtos:
                chave = str(produto["id"])
                self.produtos[chave] = produto

                preco = f"{Decimal(str(produto['preco'])):.2f}"

                tabela.add_row(
                    chave,
                    Text(produto["codigo"]),
                    Text(produto["nome"]),
                    preco.replace(".", ","),
                    str(produto["quantidadeEstoque"]),
                    str(produto["estoqueMinimo"]),
                    key=chave,
                )

            resumo.update(
                f"{len(produtos)} produto(s) • "
                "Selecione com ↑ ↓. Use Tab para acessar os botões."
            )
        finally:
            tabela.disabled = False

    def action_atualizar(self):
        if len(self.screen_stack) == 1:
            self.carregar()

    def apos_formulario(self, gravado):
        if gravado:
            self.notify(
                "Operação concluída pela API.",
                title="Sucesso",
            )
            self.carregar()

    def on_button_pressed(self, evento: Button.Pressed):
        operacao = evento.button.id

        if operacao == "atualizar":
            self.carregar()
            return

        if operacao == "novo":
            self.push_screen(
                Formulario("novo"),
                self.apos_formulario,
            )
            return

        if operacao not in ("entradas", "saidas", "historico"):
            return

        tabela = self.query_one("#produtos", DataTable)

        if tabela.disabled or tabela.row_count == 0:
            self.notify(
                "Carregue e selecione um produto.",
                severity="warning",
            )
            return

        chave = str(tabela.get_row_at(tabela.cursor_row)[0])
        produto = self.produtos[chave]

        if operacao == "historico":
            self.push_screen(Historico(produto))
            return

        self.push_screen(
            Formulario(operacao, produto),
            self.apos_formulario,
        )


if __name__ == "__main__":
    EstoqueApp().run()