$api = "http://localhost:8081"
$script:token = $null

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

function Obter-MensagemApi {
    param($Erro)

    # No Windows PowerShell 5.1 o corpo do erro pode nao vir em ErrorDetails,
    # entao ele tambem e lido direto da resposta.
    $corpo = $null

    if ($Erro.ErrorDetails -and $Erro.ErrorDetails.Message) {
        $corpo = $Erro.ErrorDetails.Message
    }

    if (-not $corpo -and $Erro.Exception.Response) {
        try {
            $leitor = New-Object System.IO.StreamReader(
                $Erro.Exception.Response.GetResponseStream())
            $corpo = $leitor.ReadToEnd()
        } catch {
            $corpo = $null
        }
    }

    if (-not $corpo) {
        return $null
    }

    try {
        $erroApi = $corpo | ConvertFrom-Json
        return $erroApi.erro
    } catch {
        return $null
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

    if ($script:token) {
        $parametros.Headers = @{
            Authorization = "Bearer $($script:token)"
        }
    }

    if ($null -ne $Dados) {
        $json = ConvertTo-Json -InputObject $Dados -Depth 10
        $parametros.ContentType = "application/json; charset=utf-8"
        $parametros.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }

    try {
        return Invoke-RestMethod @parametros
    } catch {
        $status = 0

        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }

        if ($status -eq 0) {
            throw "Falha de conexao. Confira se a API esta ligada. Se estava gravando, consulte os dados antes de repetir."
        }

        # Sessao expirada: o login e pedido de novo antes do menu.
        # A operacao nao e repetida automaticamente, para nao gravar duas vezes.
        if ($status -eq 401 -and $Rota -ne "/auth/login") {
            $script:token = $null
            throw "Sua sessao expirou. Faca login novamente."
        }

        $mensagem = Obter-MensagemApi $_

        if (-not $mensagem) {
            $mensagem = "A API retornou HTTP $status."
        }

        throw $mensagem
    }
}

function Fazer-Login {
    Clear-Host

    Write-Host "===================================="
    Write-Host "       ENTRAR NO ESTOQUE"
    Write-Host "===================================="
    Write-Host ""

    while ($true) {
        $usuario = Ler-Texto "Usuario"
        $senhaSegura = Read-Host "Senha" -AsSecureString
        $senha = [System.Net.NetworkCredential]::new("", $senhaSegura).Password

        try {
            $resposta = Chamar-Api -Metodo Post -Rota "/auth/login" `
                -Dados @{ usuario = $usuario; senha = $senha }

            $script:token = $resposta.token
            Write-Host "Login realizado." -ForegroundColor Green
            Start-Sleep -Seconds 1
            return
        } catch {
            Write-Host "Nao foi possivel entrar: $($_.Exception.Message)" `
                -ForegroundColor Red
            Write-Host ""
        }
    }
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
    if (-not $script:token) {
        Fazer-Login
    }

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
                        produtoId  = $produtoId
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
        Write-Host ""
        Write-Host "Nao foi possivel concluir: $($_.Exception.Message)" `
            -ForegroundColor Red
    }

    Write-Host ""
    Read-Host "Pressione ENTER para voltar ao menu" | Out-Null
}