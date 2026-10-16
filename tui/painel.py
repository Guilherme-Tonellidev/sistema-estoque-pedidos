from textual.widgets import Button

from app import EstoqueApp
from pedidos import Pedidos


class PainelApp(EstoqueApp):
    SUB_TITLE = "Produtos, estoque e pedidos"

    async def on_mount(self):
        botao_saida = self.query_one("#saidas", Button)
        barra = botao_saida.parent

        await barra.mount(
            Button("Pedidos", id="pedidos", variant="primary"),
            before=botao_saida,
        )

    def atualizar_estoque_apos_pedidos(self, resultado):
        self.carregar()

    def on_button_pressed(self, evento: Button.Pressed):
        if evento.button.id == "pedidos":
            evento.stop()
            evento.prevent_default()

            self.push_screen(
                Pedidos(),
                self.atualizar_estoque_apos_pedidos,
            )

        # Os outros botões são tratados automaticamente por EstoqueApp.


if __name__ == "__main__":
    PainelApp().run()