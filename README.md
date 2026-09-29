# API de Estoque e Pedidos

Projeto de portfólio com foco em **desenvolvimento back-end**, desenvolvido por **Guilherme Tonelli** com Java, Spring Boot e PostgreSQL.

A API permite cadastrar produtos e controlar entradas e saídas de estoque com histórico. O projeto explora validação de dados, regras de negócio, transações, persistência e testes automatizados.

O módulo de pedidos será implementado nas próximas etapas. O escopo deste repositório é a API, sem uma interface própria de front-end.

## Tecnologias

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Jakarta Validation
- PostgreSQL 17
- Flyway
- Maven Wrapper
- JUnit 5
- Git e GitHub

## Funcionalidades implementadas

- Cadastro de produtos com código, nome, preço e estoque mínimo.
- Normalização do código para maiúsculas e remoção de espaços nas extremidades.
- Bloqueio de códigos duplicados.
- Listagem de produtos por ID crescente.
- Consulta de produto por ID.
- Entrada de estoque com quantidade e motivo.
- Saída de estoque com quantidade e motivo.
- Bloqueio de retiradas acima do saldo disponível.
- Histórico das 50 movimentações mais recentes de cada produto.
- Registro de data, quantidade e saldos anterior e posterior.
- Respostas de erro padronizadas.

## Regras de negócio

### Produtos

- Todo produto começa com estoque zero.
- O código é obrigatório, único e possui até 50 caracteres.
- O nome é obrigatório e possui até 120 caracteres.
- O preço não pode ser negativo.
- O preço admite até 10 dígitos inteiros e 2 casas decimais.
- O estoque mínimo é obrigatório e não pode ser negativo.

### Movimentações de estoque

- A quantidade movimentada deve ser maior que zero.
- O motivo é obrigatório e possui até 255 caracteres.
- Uma saída pode zerar o estoque, mas não deixá-lo negativo.
- Entradas que ultrapassem o limite de um inteiro de 32 bits são recusadas.
- Movimentações recusadas não devem alterar o saldo nem gerar histórico.

A atualização do saldo e a gravação do histórico acontecem na mesma transação. A consulta usada para movimentar o estoque aplica bloqueio de escrita no produto, coordenando operações simultâneas sobre o mesmo item.

O banco também possui restrições para proteger os valores e a coerência entre os saldos registrados no histórico.

## Organização do código

Os arquivos Java utilizam o pacote `sistema_estoque_pedidos`.

| Componente | Responsabilidade |
|---|---|
| Controllers | Disponibilizar os endpoints da API |
| Services | Coordenar as operações e transações |
| Repositories | Acessar os dados persistidos |
| Entidades | Representar produtos e movimentações |
| Requests | Definir e validar os dados recebidos |
| Tratador de erros | Padronizar as respostas dos erros tratados |
| Migrações Flyway | Controlar a evolução da estrutura do banco |
| Testes | Verificar as regras de saldo do produto |

## Executando localmente

### Pré-requisitos

- JDK compatível com Java 21.
- PostgreSQL instalado e em execução.
- Git.
- Acesso à internet para baixar as dependências na primeira execução.

O projeto inclui Maven Wrapper, portanto não exige uma instalação separada do Maven.

Os comandos abaixo são para PowerShell no Windows.

### 1. Clonar o repositório

```powershell
git clone https://github.com/Guilherme-Tonellidev/sistema-estoque-pedidos.git
cd sistema-estoque-pedidos
```

### 2. Preparar o PostgreSQL

Para uma instalação nova, conecte-se pelo SQL Shell (`psql`) com um usuário administrador.

Crie o usuário da aplicação:

```sql
CREATE ROLE estoque_app LOGIN;
```

Defina a senha pelo comando do `psql`:

```text
\password estoque_app
```

Crie o banco:

```sql
CREATE DATABASE estoque_db OWNER estoque_app;
```

Essa preparação é feita apenas uma vez. Se o usuário e o banco já existirem, utilize-os.

### 3. Informar a senha e iniciar

No PowerShell, dentro da pasta do projeto:

```powershell
$senhaEstoque = Read-Host "Senha do estoque_app" -AsSecureString
$env:ESTOQUE_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $senhaEstoque).Password
.\mvnw.cmd spring-boot:run
```

A senha é fornecida por variável de ambiente e não deve ser gravada no repositório. Ao abrir outro terminal, informe-a novamente antes de iniciar a aplicação.

### Configuração local

| Item | Valor |
|---|---|
| URL da API | `http://localhost:8081` |
| Endereço do PostgreSQL | `localhost:5432` |
| Banco | `estoque_db` |
| Usuário | `estoque_app` |
| Variável da senha | `ESTOQUE_DB_PASSWORD` |

O Flyway aplica as migrações pendentes na inicialização. O Hibernate valida a estrutura das tabelas com `ddl-auto=validate`.

Para encerrar a aplicação, pressione `Ctrl + C` no terminal em que ela está executando.

## Documentação interativa

Com a aplicação em execução, acesse:

- [Swagger UI](http://localhost:8081/swagger-ui.html): documentação e execução de requisições pelo navegador.
- [OpenAPI JSON](http://localhost:8081/v3/api-docs): especificação da API.

Para testar uma rota, abra a operação, clique em **Try it out**, preencha os campos necessários e clique em **Execute**.

As operações executadas pelo Swagger utilizam o banco configurado na aplicação. Cadastros e movimentações válidos alteram os dados.

## Endpoints

| Método | Rota | Operação |
|---|---|---|
| POST | `/produtos` | Cadastrar produto |
| GET | `/produtos` | Listar produtos |
| GET | `/produtos/{id}` | Consultar produto |
| POST | `/produtos/{produtoId}/entradas` | Registrar entrada |
| POST | `/produtos/{produtoId}/saidas` | Registrar saída |
| GET | `/produtos/{produtoId}/movimentacoes` | Consultar histórico recente |

As requisições de cadastro e movimentação utilizam `Content-Type: application/json`.

### Cadastro de produto

Envie para `POST /produtos`:

```json
{
  "codigo": "MOUSE-001",
  "nome": "Mouse USB",
  "preco": 49.90,
  "estoqueMinimo": 5
}
```

O estoque inicial será zero. Utilize o ID retornado nas próximas operações.

### Entrada de estoque

Exemplo para um produto de ID `1`, usando `POST /produtos/1/entradas`:

```json
{
  "quantidade": 10,
  "motivo": "Recebimento inicial"
}
```

### Saída de estoque

Exemplo usando `POST /produtos/1/saidas`:

```json
{
  "quantidade": 3,
  "motivo": "Retirada para uso interno"
}
```

Cada envio válido de uma movimentação cria um novo registro e altera o saldo. Repetir uma requisição não é tratado como a mesma operação.

### Histórico

Consulte `GET /produtos/1/movimentacoes` para obter as movimentações recentes do produto de ID `1`.

Cada registro contém:

- ID da movimentação.
- ID do produto.
- Tipo: `ENTRADA` ou `SAIDA`.
- Quantidade movimentada.
- Saldo anterior.
- Saldo posterior.
- Motivo.
- Data e hora.

A consulta retorna até 50 registros, ordenados por data decrescente e, em caso de empate, por ID decrescente.

## Respostas da API

| Status | Significado |
|---|---|
| `200 OK` | Consulta realizada |
| `201 Created` | Produto ou movimentação criado |
| `400 Bad Request` | Dados inválidos |
| `404 Not Found` | Produto não encontrado |
| `409 Conflict` | Código duplicado ou estoque insuficiente |

Os erros tratados pela aplicação utilizam os campos `status`, `erro` e `caminho`.

Exemplo de saldo insuficiente:

```json
{
  "status": 409,
  "erro": "Estoque insuficiente. Saldo disponível: 7.",
  "caminho": "/produtos/1/saidas"
}
```

Exemplo de quantidade inválida:

```json
{
  "status": 400,
  "erro": "A quantidade deve ser maior que zero.",
  "caminho": "/produtos/1/entradas"
}
```

## Migrações do banco

| Migração | Finalidade |
|---|---|
| `V1__criar_tabela_produtos.sql` | Criar a tabela de produtos e suas restrições |
| `V2__criar_movimentacoes_estoque.sql` | Criar o histórico de movimentações, suas restrições e índice |

Os arquivos ficam em `src/main/resources/db/migration`.

Migrações já aplicadas devem ser preservadas. Mudanças posteriores no banco devem receber novos arquivos de migração.

## Testes automatizados

A classe `ProdutoTest` possui **9 casos de teste** cobrindo:

- Soma de entradas ao estoque existente.
- Desconto de uma saída.
- Retirada de todo o saldo.
- Recusa de saída acima do saldo.
- Recusa de entradas com quantidade zero ou negativa.
- Recusa de saídas com quantidade zero ou negativa.
- Recusa de entrada acima do limite suportado.
- Preservação do saldo nas operações recusadas.

Execute:

```powershell
.\mvnw.cmd "-Dtest=ProdutoTest" test
```

Esses testes não iniciam o Spring, não dependem do PostgreSQL e não alteram os dados da aplicação.

O comando executa somente `ProdutoTest`. A suíte completa do projeto ainda não foi validada para execução independente do banco.

### Verificações manuais realizadas

- Cadastro e consulta de produto.
- Recusa de código duplicado com status `409`.
- Recusa de preço negativo com status `400`.
- Entrada de estoque com atualização de saldo e histórico.
- Recusa de entrada com quantidade zero, preservando saldo e histórico.
- Saída de estoque com atualização de saldo e histórico.
- Recusa de saída acima do saldo com status `409`, preservando saldo e histórico.

A persistência, o histórico e as operações simultâneas ainda não possuem cobertura automatizada de integração.

## Estado atual e próximas etapas

A versão atual disponibiliza uma API de produtos e estoque, com entradas, saídas, histórico de movimentações e testes unitários das regras de saldo.

Próximas etapas:

- Módulo de pedidos e itens.
- Confirmação e cancelamento de pedidos com atualização do estoque.
- Autenticação e controle de acesso.
- Consulta de produtos com estoque baixo.
- Paginação da listagem de produtos e do histórico completo.
- Testes automatizados de integração, incluindo concorrência e consistência entre saldo e histórico.
- Execução de testes pelo GitHub Actions.
- Publicação da API em um ambiente de hospedagem.

O desenvolvimento de uma interface front-end está fora do escopo deste projeto.

## Autor

**Guilherme Tonelli**

- [GitHub](https://github.com/Guilherme-Tonellidev)
- [LinkedIn](https://www.linkedin.com/in/guilherme-tonellidev)