# API de Estoque e Pedidos

API REST para cadastrar produtos, controlar estoque e gerenciar pedidos. Projeto de portfólio com foco em back-end: regras de negócio em transação, controle de concorrência, autenticação JWT e três formas de uso (Swagger, menu PowerShell e interface em terminal).

**Stack:** Java 21 · Spring Boot 4 · Spring Data JPA · Spring Security (JWT) · PostgreSQL 17 · Flyway · Jakarta Validation · Swagger/OpenAPI · JUnit 5. Clientes em Python (Textual, httpx) e PowerShell.

## Arquitetura

O código segue três camadas. O `Controller` recebe a requisição e valida a entrada. O `Service` aplica as regras de negócio e controla a transação. O `Repository` acessa o banco.

Erros de regra viram `ResponseStatusException` e são padronizados pelo `TratadorDeErros`. Todas as respostas de erro, inclusive 401 e 403, seguem o formato `{ "status", "erro", "caminho" }`.

O schema é versionado com Flyway em `src/main/resources/db/migration`. O Hibernate só valida o schema (`ddl-auto=validate`), sem alterá-lo.

## Regras de negócio

- Produtos começam com estoque zero. Toda mudança de saldo gera uma movimentação com saldo anterior e posterior, o que permite auditar o histórico.
- Pedidos nascem como `RASCUNHO` e não alteram o estoque.
- Confirmar um pedido verifica o saldo de todos os itens antes de descontar qualquer um. Se um item não tem saldo, nada é alterado.
- Cancelar um pedido confirmado devolve os itens. Cancelar um rascunho não movimenta o estoque.
- Confirmar ou cancelar duas vezes é recusado.
- O preço do item é registrado no pedido, então alterações futuras no produto não afetam pedidos antigos.

## Decisões técnicas

**Concorrência.** Saídas e confirmações bloqueiam o produto com `PESSIMISTIC_WRITE`. Assim, dois pedidos disputando o mesmo estoque não geram saldo negativo nem desconto duplicado. Os produtos de um pedido são bloqueados em ordem de id, o que evita deadlock quando dois pedidos têm itens em ordem diferente.

**Autenticação.** `POST /auth/login` devolve um token JWT assinado com HMAC-SHA256. As senhas ficam como hash BCrypt. Todas as rotas exigem o cabeçalho `Authorization: Bearer <token>`, exceto o login e o Swagger. A validade do token é definida em `estoque.jwt.validade-horas`.

**Administrador inicial.** Ao subir a API com a tabela `usuarios` vazia, o usuário administrador é criado a partir de `ESTOQUE_ADMIN_USUARIO` e `ESTOQUE_ADMIN_SENHA`.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/login` | Gera o token |
| GET | `/produtos` | Lista produtos |
| POST | `/produtos` | Cadastra produto |
| GET | `/produtos/estoque-baixo` | Produtos no limite mínimo ou abaixo dele |
| GET | `/produtos/{id}` | Consulta produto |
| POST | `/produtos/{id}/entradas` | Registra entrada |
| POST | `/produtos/{id}/saidas` | Registra saída |
| GET | `/produtos/{id}/movimentacoes` | Últimas 50 movimentações |
| POST | `/pedidos` | Cria pedido em rascunho |
| GET | `/pedidos` | Lista pedidos |
| GET | `/pedidos/{id}` | Consulta pedido |
| PATCH | `/pedidos/{id}/confirmacao` | Confirma e desconta o estoque |
| PATCH | `/pedidos/{id}/cancelamento` | Cancela e devolve os itens, se estava confirmado |

## Como executar

**Requisitos:** JDK 21, PostgreSQL em execução e Git. Os comandos são para PowerShell no Windows.

### 1. Clonar o projeto

```powershell
git clone https://github.com/Guilherme-Tonellidev/sistema-estoque-pedidos.git
cd sistema-estoque-pedidos
```

### 2. Preparar o banco

No `psql`, conectado como administrador, execute uma vez:

```sql
CREATE ROLE estoque_app LOGIN;
CREATE DATABASE estoque_db OWNER estoque_app;
CREATE DATABASE estoque_test_db OWNER estoque_app;
```

Defina a senha do usuário com `\password estoque_app`. Se os bancos já existirem, use-os. A aplicação conecta em `localhost:5432`, e o Flyway cria as tabelas automaticamente.

### 3. Subir a API

```powershell
$senhaEstoque = Read-Host "Senha do estoque_app" -AsSecureString
$env:ESTOQUE_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $senhaEstoque).Password

$env:ESTOQUE_JWT_SECRET = -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 48 | ForEach-Object { [char]$_ })

$env:ESTOQUE_ADMIN_USUARIO = "admin"
$senhaAdmin = Read-Host "Senha do admin" -AsSecureString
$env:ESTOQUE_ADMIN_SENHA = [System.Net.NetworkCredential]::new("", $senhaAdmin).Password

.\mvnw.cmd spring-boot:run
```

A API fica disponível em `http://localhost:8081`. As variáveis precisam ser definidas em cada terminal novo. O segredo JWT muda a cada execução, então tokens gerados antes deixam de valer. Não grave senhas nem o segredo no repositório. Depois da primeira execução, o administrador já existe e as variáveis `ESTOQUE_ADMIN_*` deixam de ser necessárias.

Para encerrar a API, use `Ctrl + C`.

### 4. Usar os clientes

**Swagger:** `http://localhost:8081/swagger-ui.html` documenta todos os endpoints. Para as rotas protegidas, envie o token no cabeçalho `Authorization`.

**Menu PowerShell:** com a API ligada, abra outro PowerShell na pasta do projeto:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\menu.ps1
```

O menu pede usuário e senha ao abrir e pede o login de novo quando a sessão expira.

**Interface em terminal:** requer Python 3.13 (versão usada no desenvolvimento).

```powershell
cd tui
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python painel.py
```

A interface também pede login ao abrir. Use as setas para selecionar, `Tab` para os botões, `Esc` para voltar, `F5` para atualizar e `Ctrl+Q` para sair. Ela consome a API e não acessa o banco diretamente.

## Testes

Com o PostgreSQL ligado e `ESTOQUE_DB_PASSWORD` definida no terminal:

```powershell
.\mvnw.cmd test
```

Os testes com Spring usam o banco `estoque_test_db` e o perfil `test`. Não é preciso subir a API antes. São **57 testes**:

- 19 unitários de regras de produtos e pedidos
- 22 da camada HTTP, com serviços simulados
- 4 de integração de pedidos
- 1 de contexto Spring
- 5 de concorrência
- 2 de estoque baixo
- 4 de segurança

Para rodar só os unitários, sem banco:

```powershell
.\mvnw.cmd "-Dtest=ProdutoTest,PedidoTest" test
```

## Autor

**Guilherme Tonelli** 
· [GitHub](https://github.com/Guilherme-Tonellidev) 
· [LinkedIn](https://www.linkedin.com/in/guilherme-tonellidev)