# API de Estoque e Pedidos

API REST de portfólio para cadastrar produtos, controlar estoque e gerenciar pedidos. Desenvolvida por **Guilherme Tonelli**, com foco em back-end e um menu PowerShell para utilizar a API pelo terminal.

## Tecnologias

Java 21 · Spring Boot · Spring Data JPA · Jakarta Validation · PostgreSQL 17 · Flyway · Swagger/OpenAPI · Maven Wrapper · JUnit 5

## Funcionalidades

- Cadastro e consulta de produtos com código único, preço e estoque mínimo.
- Entradas e saídas com motivo e histórico dos 50 registros mais recentes.
- Bloqueio de quantidades inválidas e estoque insuficiente.
- Pedidos com itens, preços registrados e total calculado.
- Confirmação e cancelamento com atualização do estoque.

Produtos começam com estoque zero. Pedidos começam em `RASCUNHO`; a confirmação desconta os itens do estoque. Cancelar um pedido confirmado devolve os itens; cancelar um rascunho não altera o saldo. Confirmações e cancelamentos repetidos são recusados.

As alterações de pedido, saldo e histórico são realizadas em transações.

## Executar

Requisitos: **JDK 21**, **PostgreSQL em execução** e **Git**. Os comandos são para PowerShell no Windows.

### 1. Clonar o projeto

```powershell
git clone https://github.com/Guilherme-Tonellidev/sistema-estoque-pedidos.git
cd sistema-estoque-pedidos
```

### 2. Preparar os bancos

No SQL Shell (`psql`), conectado como administrador, execute uma vez:

```sql
CREATE ROLE estoque_app LOGIN;
```

Defina a senha:

```text
\password estoque_app
```

Crie os bancos da aplicação e dos testes:

```sql
CREATE DATABASE estoque_db OWNER estoque_app;
CREATE DATABASE estoque_test_db OWNER estoque_app;
```

Se já existirem, utilize-os. A configuração considera o PostgreSQL em `localhost:5432`. O Flyway cria as tabelas automaticamente.

### 3. Iniciar a API

Dentro da pasta do projeto:

```powershell
$senhaEstoque = Read-Host "Senha do estoque_app" -AsSecureString
$env:ESTOQUE_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $senhaEstoque).Password
.\mvnw.cmd spring-boot:run
```

A API fica disponível em `http://localhost:8081`. Informe a senha novamente ao abrir outro terminal; não a salve no repositório. Para encerrar a API, use `Ctrl + C`.

## Usar a aplicação

**Menu:** com a API ligada, abra outro PowerShell na pasta do projeto e execute:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\menu.ps1
```

Escolha uma opção para cadastrar produtos, registrar entradas e saídas, criar, confirmar ou cancelar pedidos e consultar os dados. As operações ficam salvas no banco da aplicação. O cancelamento de pedidos não desfaz saídas manuais.

**Swagger:** acesse a [documentação interativa](http://localhost:8081/swagger-ui.html) para consultar os endpoints e enviar requisições. Selecione uma operação, clique em **Try it out** e depois em **Execute**.

## Testes

A suíte possui **46 testes aprovados localmente.**:

- **19 unitários:** regras de produtos e pedidos.
- **22 da camada HTTP:** 5 de produtos, 7 de pedidos e 10 de estoque, cobrindo validações e respostas de sucesso e erro, com serviços simulados.
- **4 de integração:** confirmação, estoque insuficiente, devolução e cancelamento repetido.
- **1 de inicialização:** contexto Spring.

Com o PostgreSQL ligado e a variável `ESTOQUE_DB_PASSWORD` definida no terminal:

```powershell
.\mvnw.cmd test
```

Os testes com Spring usam o banco separado `estoque_test_db`. Não é necessário iniciar a API antes.

Para executar apenas os testes unitários, sem banco:

```powershell
.\mvnw.cmd "-Dtest=ProdutoTest,PedidoTest" test
```

## Próximas etapas

- Ampliar os testes de endpoints e adicionar testes de concorrência.
- Autenticação, consulta de estoque baixo e paginação.
- Publicação da API.

## Autor

**Guilherme Tonelli**

[GitHub](https://github.com/Guilherme-Tonellidev)
[LinkedIn](https://www.linkedin.com/in/guilherme-tonellidev)