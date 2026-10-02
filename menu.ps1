$api = "http://localhost:8081"

function Ler-Texto {
    param([string]$Mensagem)

    while ($true) {
        $texto = (Read-Host $Mensagem).Trim()

        if ($texto.Length -gt 0) {
            return $texto
        }

        Write-Host "Preencha este campo." -ForegroundColor Yellow
    }
}

function Ler-Inteiro {
    param(
        [string]$Mensagem,
        [int]$Minimo = 1
    )

    while ($true) {
        $texto = Read-Host $Mensagem
        $numero = 0

        if ([int]::TryParse($texto, [ref]$numero)) {
            if ($numero -ge $Minimo) {
                return $numero
            }
        }

        Write-Host "Informe um inteiro a partir de $Minimo." `
            -ForegroundColor Yellow
    }
}

function Ler-Preco {
    while ($true) {
        $texto = (Read-Host "Preco, exemplo: 49,90").Trim()
        $texto = $texto.Replace(",", ".")
        $valor = [decimal]0

        $valido = [decimal]::TryParse(
            $texto,
            [System.Globalization.NumberStyles]::AllowDecimalPoint,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref]$valor
        )

        if ($valido) {
            if ($valor -ge 0 -and
                $valor -le 9999999999.99 -and
                [decimal]::Round($valor, 2) -eq $valor) {
                return $valor
            }
        }

        Write-Host "Informe um preco positivo ou zero, com ate 2 casas decimais." `
            -ForegroundColor Yellow
    }
}

function Chamar-Api {
    param(
        [string]$Metodo,
        [string]$Rota,
        [object]$Dados
    )

    $parametros = @{
        Uri         = "$api$Rota"
        Method      = $Metodo
        ErrorAction = "Stop"
        TimeoutSec  = 30
    }

    if ($null -ne $Dados) {
        $json = ConvertTo-Json -InputObject $Dados -Depth 10
        $parametros.ContentType = "application/json; charset=utf-8"
        $parametros.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }

    Invoke-RestMethod @parametros
}

function Mostrar-Resultado {
    param([object]$Resultado)

    ConvertTo-Json -InputObject $Resultado -Depth 10 |
        Out-Host
}

function Mostrar-Produtos {
    $produtos = @(Chamar-Api -Metodo Get -Rota "/produtos")

    if ($produtos.Count -eq 0) {
        Write-Host "Nenhum produto cadastrado."
        return
    }

    $produtos |
        Format-Table id, codigo, nome, preco,
            quantidadeEstoque, estoqueMinimo -AutoSize |
        Out-Host
}

function Mostrar-Saldo {
    param([int]$ProdutoId)

    $produto = Chamar-Api -Metodo Get -Rota "/produtos/$ProdutoId"

    Write-Host ""
    Write-Host ("Estoque atual de {0}: {1}" -f
        $produto.nome, $produto.quantidadeEstoque) `
        -ForegroundColor Cyan
}

while ($true) {
    Clear-Host

    Write-Host "===================================="
    Write-Host "       ESTOQUE E PEDIDOS"
    Write-Host "===================================="
    Write-Host "1 - Cadastrar produto"
    Write-Host "2 - Listar produtos e estoque"
    Write-Host "3 - Registrar entrada"
    Write-Host "4 - Registrar saida"
    Write-Host "5 - Criar pedido"
    Write-Host "6 - Confirmar pedido"
    Write-Host "7 - Cancelar pedido"
    Write-Host "8 - Consultar pedidos"
    Write-Host "9 - Consultar movimentacoes"
    Write-Host "0 - Sair"
    Write-Host ""

    $opcao = Read-Host "Escolha uma opcao"

    if ($opcao -eq "0") {
        Write-Host "Menu encerrado."
        break
    }

    try {
        switch ($opcao) {
            "1" {
                $dados = @{
                    codigo        = Ler-Texto "Codigo do produto"
                    nome          = Ler-Texto "Nome do produto"
                    preco         = Ler-Preco
                    estoqueMinimo = Ler-Inteiro "Estoque minimo" 0
                }

                $produto = Chamar-Api -Metodo Post `
                    -Rota "/produtos" -Dados $dados

                Write-Host "Produto cadastrado!" -ForegroundColor Green
                Mostrar-Resultado $produto
            }

            "2" {
                Mostrar-Produtos
            }

            "3" {
                Mostrar-Produtos
                $produtoId = Ler-Inteiro "ID do produto"

                $dados = @{
                    quantidade = Ler-Inteiro "Quantidade de entrada"
                    motivo     = Ler-Texto "Motivo da entrada"
                }

                $movimentacao = Chamar-Api -Metodo Post `
                    -Rota "/produtos/$produtoId/entradas" `
                    -Dados $dados

                Write-Host "Entrada registrada!" -ForegroundColor Green
                Mostrar-Resultado $movimentacao
                Mostrar-Saldo $produtoId
            }

            "4" {
                Mostrar-Produtos
                $produtoId = Ler-Inteiro "ID do produto"

                $dados = @{
                    quantidade = Ler-Inteiro "Quantidade de saida"
                    motivo     = Ler-Texto "Motivo da saida"
                }

                $movimentacao = Chamar-Api -Metodo Post `
                    -Rota "/produtos/$produtoId/saidas" `
                    -Dados $dados

                Write-Host "Saida registrada!" -ForegroundColor Green
                Mostrar-Resultado $movimentacao
                Mostrar-Saldo $produtoId
            }

            "5" {
                Mostrar-Produtos

                $cliente = Ler-Texto "Nome do cliente"
                $itens = @()

                do {
                    $produtoId = Ler-Inteiro "ID do produto"

                    if ($itens.produtoId -contains $produtoId) {
                        Write-Host "Este produto ja esta no pedido." `
                            -ForegroundColor Yellow
                        continue
                    }

                    $quantidade = Ler-Inteiro "Quantidade"

                    $itens += @{
                        produtoId = $produtoId
                        quantidade = $quantidade
                    }

                    $mais = Read-Host "Adicionar outro produto? (s/n)"
                } while ($mais -ne "n")

                $dados = @{
                    cliente = $cliente
                    itens   = $itens
                }

                $pedido = Chamar-Api -Metodo Post `
                    -Rota "/pedidos" -Dados $dados

                Write-Host "Pedido criado em RASCUNHO." `
                    -ForegroundColor Green
                Write-Host "Use a opcao 6 para confirmar e descontar o estoque."

                Mostrar-Resultado $pedido
            }

            "6" {
                $pedidoId = Ler-Inteiro "ID do pedido"

                $pedido = Chamar-Api -Metodo Patch `
                    -Rota "/pedidos/$pedidoId/confirmacao"

                Write-Host "Pedido confirmado!" -ForegroundColor Green
                Mostrar-Resultado $pedido
            }

            "7" {
                $pedidoId = Ler-Inteiro "ID do pedido"

                $pedido = Chamar-Api -Metodo Patch `
                    -Rota "/pedidos/$pedidoId/cancelamento"

                Write-Host "Pedido cancelado!" -ForegroundColor Green
                Mostrar-Resultado $pedido
            }

            "8" {
                $pedidoId = Ler-Inteiro "ID do pedido (0 para listar todos)" 0

                if ($pedidoId -eq 0) {
                    $pedidos = @(Chamar-Api -Metodo Get -Rota "/pedidos")

                    if ($pedidos.Count -eq 0) {
                        Write-Host "Nenhum pedido cadastrado."
                    } else {
                        $pedidos |
                            Format-Table id, cliente, status, total -AutoSize |
                            Out-Host
                    }
                } else {
                    $pedido = Chamar-Api -Metodo Get `
                        -Rota "/pedidos/$pedidoId"

                    Mostrar-Resultado $pedido
                }
            }

            "9" {
                $produtoId = Ler-Inteiro "ID do produto"

                $movimentacoes = @(
                    Chamar-Api -Metodo Get `
                        -Rota "/produtos/$produtoId/movimentacoes"
                )

                if ($movimentacoes.Count -eq 0) {
                    Write-Host "Este produto nao possui movimentacoes."
                } else {
                    $movimentacoes |
                        Format-Table id, tipo, quantidade,
                            saldoAnterior, saldoPosterior, motivo -Wrap |
                        Out-Host
                }
            }

            default {
                Write-Host "Opcao invalida." -ForegroundColor Yellow
            }
        }
    } catch {
        $mensagem = $_.Exception.Message

        if ($_.ErrorDetails.Message) {
            try {
                $erroApi = $_.ErrorDetails.Message | ConvertFrom-Json

                if ($erroApi.erro) {
                    $mensagem = $erroApi.erro
                }
            } catch {
                # Mantem a mensagem original se a resposta nao for JSON.
            }
        }

        Write-Host ""
        Write-Host "Nao foi possivel concluir: $mensagem" `
            -ForegroundColor Red
        Write-Host "Se houve falha de conexao, confira se a API esta ligada."
        Write-Host "Consulte os dados antes de repetir uma operacao."
    }

    Write-Host ""
    Read-Host "Pressione ENTER para voltar ao menu" | Out-Null
}