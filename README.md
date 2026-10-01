# API de Estoque e Pedidos

API REST de portfólio desenvolvida com **Java, Spring Boot e PostgreSQL**. Permite gerenciar produtos, movimentar estoque e criar, confirmar e cancelar pedidos.

Projeto focado em **back-end**, com documentação interativa pelo Swagger UI.

## Tecnologias

Java 21 · Spring Boot 4.1.1 · Spring Data JPA · Jakarta Validation · PostgreSQL 17 · Flyway · springdoc-openapi 3.1.1 · Maven Wrapper · JUnit 5.

## Funcionalidades e regras

- **Produtos:** cadastro e consulta, código único normalizado para maiúsculas, preço e estoque mínimo não negativos. Estoque inicial zero.
- **Estoque:** entradas e saídas com quantidade positiva e motivo obrigatório. Retiradas acima do saldo são recusadas.
- **Histórico:** últimas 50 movimentações de cada produto, com quantidade, motivo, data e saldos anterior e posterior.
- **Pedidos:** de 1 a 100 produtos diferentes, quantidades positivas e preços copiados do cadastro na criação. Subtotais e total calculados com `BigDecimal`.
- **Consistência:** saldo, histórico e mudança de status são gravados na mesma transação. As operações de confirmação e cancelamento utilizam bloqueios no pedido e nos produtos.

### Fluxo dos pedidos

| Operação | Resultado |
|---|---|
| Criar | Gera um `RASCUNHO`, sem reservar ou descontar estoque |
| Confirmar | Valida todos os saldos, registra as saídas e muda para `CONFIRMADO` |
| Cancelar rascunho | Muda para `CANCELADO`, sem movimentar estoque |
| Cancelar confirmado | Devolve as quantidades, registra as entradas e muda para `CANCELADO` |

Confirmações e cancelamentos repetidos são recusados. Pedidos cancelados não podem ser confirmados. O cancelamento preserva os itens, os preços e a data de confirmação, quando existente.

## Como executar

Requisitos: **JDK compatível com Java 21, PostgreSQL em execução e Git**. O Maven Wrapper está incluído. Os comandos abaixo usam PowerShell no Windows.

### 1. Clonar

```powershell
git clone https://github.com/Guilherme-Tonellidev/sistema-estoque-pedidos.git
cd sistema-estoque-pedidos
```

### 2. Criar o banco

No SQL Shell (`psql`), conectado como administrador, execute uma vez:

```sql
CREATE ROLE estoque_app LOGIN;
```

Defina a senha:

```text
\password estoque_app
```

Crie o banco:

```sql
CREATE DATABASE estoque_db OWNER estoque_app;
```

Se o usuário e o banco já existirem, pule essa etapa.

### 3. Iniciar a API

No PowerShell, dentro da pasta do projeto:

```powershell
$senhaEstoque = Read-Host "Senha do estoque_app" -AsSecureString
$env:ESTOQUE_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $senhaEstoque).Password
.\mvnw.cmd spring-boot:run
```

A aplicação usa PostgreSQL em `localhost:5432`, banco `estoque_db` e usuário `estoque_app`. A senha fica na variável de ambiente do terminal; informe-a novamente ao abrir outro terminal.

O Flyway aplica as migrações automaticamente, e o Hibernate valida as tabelas. Encerre a aplicação com `Ctrl + C`.

## Documentação e endpoints

Com a aplicação rodando:

- [Swagger UI](http://localhost:8081/swagger-ui.html): visualizar e testar as rotas.
- [OpenAPI JSON](http://localhost:8081/v3/api-docs): especificação da API.

No Swagger, selecione a operação, clique em **Try it out**, preencha os campos e clique em **Execute**. As operações alteram o banco real da aplicação.

| Método | Rota | Função |
|---|---|---|
| POST | `/produtos` | Cadastrar produto |
| GET | `/produtos` | Listar produtos |
| GET | `/produtos/{id}` | Consultar produto |
| POST | `/produtos/{produtoId}/entradas` | Registrar entrada |
| POST | `/produtos/{produtoId}/saidas` | Registrar saída |
| GET | `/produtos/{produtoId}/movimentacoes` | Consultar histórico recente |
| POST | `/pedidos` | Criar pedido em rascunho |
| GET | `/pedidos` | Listar pedidos |
| GET | `/pedidos/{id}` | Consultar pedido |
| PATCH | `/pedidos/{id}/confirmacao` | Confirmar e descontar estoque |
| PATCH | `/pedidos/{id}/cancelamento` | Cancelar e devolver estoque, se confirmado |

Cadastros e movimentações recebem JSON. Confirmação e cancelamento não exigem corpo de requisição.

### Exemplos de requisição

**Produto — `POST /produtos`:**

```json
{
  "codigo": "MOUSE-001",
  "nome": "Mouse USB",
  "preco": 49.90,
  "estoqueMinimo": 5
}
```

**Entrada — `POST /produtos/{produtoId}/entradas`:**

```json
{
  "quantidade": 10,
  "motivo": "Recebimento inicial"
}
```

A saída utiliza os mesmos campos na rota `/produtos/{produtoId}/saidas`. Cada envio válido registra uma nova movimentação.

**Pedido — `POST /pedidos`:**

```json
{
  "cliente": "Cliente de teste",
  "itens": [
    {
      "produtoId": 1,
      "quantidade": 2
    }
  ]
}
```

Use o ID real do produto cadastrado. O preço é obtido no banco; não é enviado pelo cliente da API.

### Respostas

| Status | Situação |
|---|---|
| `200` | Consulta, confirmação ou cancelamento realizado |
| `201` | Produto, pedido ou movimentação criado |
| `400` | Dados inválidos |
| `404` | Produto ou pedido não encontrado |
| `409` | Código duplicado, saldo insuficiente, transição de status inválida ou devolução acima do limite de estoque |

Os erros tratados seguem este formato:

```json
{
  "status": 409,
  "erro": "O pedido já está cancelado.",
  "caminho": "/pedidos/1/cancelamento"
}
```

## Banco e organização

O código é dividido em controllers, services, repositories, entidades e objetos de requisição e resposta.

As migrações ficam em `src/main/resources/db/migration`:

| Versão | Estrutura |
|---|---|
| V1 | Produtos |
| V2 | Movimentações de estoque |
| V3 | Pedidos e itens |

Migrações já aplicadas devem ser preservadas. Alterações no banco recebem uma nova versão.

## Testes

**19 testes unitários aprovados:**

- `ProdutoTest`: 9 casos sobre entradas, saídas, saldo insuficiente, quantidades inválidas e limite de estoque.
- `PedidoTest`: 10 casos sobre totais, confirmação, cancelamento, operações repetidas e restrições de itens.

Execute:

```powershell
.\mvnw.cmd "-Dtest=ProdutoTest,PedidoTest" test
```

Esses testes não dependem do PostgreSQL. O comando executa apenas as duas classes; a suíte completa ainda não foi validada para rodar sem banco.

Os fluxos da API foram verificados manualmente, incluindo preservação do saldo e histórico em operações recusadas. Testes automatizados de integração e concorrência ainda estão pendentes.

## Próximas etapas

- Autenticação e controle de acesso.
- Consulta de estoque baixo e paginação das listagens.
- Testes de integração, concorrência e falhas durante transações.
- Integração contínua com GitHub Actions.
- Publicação da API.

## Autor

**Guilherme Tonelli**

[GitHub](https://github.com/Guilherme-Tonellidev) 
[LinkedIn](https://www.linkedin.com/in/guilherme-tonellidev)