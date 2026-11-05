# previna-backend

API do **Previna**, sistema que incentiva gestantes de primeira viagem a irem às consultas e aos exames do pré-natal.
A gestante ganha pontos a cada consulta ou exame confirmado pelo médico e troca os pontos por cupons de desconto das
empresas parceiras.

## Stack

- Java 21 + [Spring Boot 3.5](https://spring.io/projects/spring-boot) (Web, Data JPA, Security, Validation, Mail)
- PostgreSQL + [Flyway](https://documentation.red-gate.com/fd) para as migrações
- JWT ([jjwt](https://github.com/jwtk/jjwt)) para autenticação
- [springdoc-openapi](https://springdoc.org) para a documentação da API (Swagger UI)
- JUnit 5 + Mockito + Spring Security Test; JaCoCo para cobertura

## Perfis de usuário

| Perfil             | Usa                  | O que faz                                                                    |
| ------------------ | -------------------- | ---------------------------------------------------------------------------- |
| `ADMIN`            | Portal web           | Cadastra médicos e pré-cadastra empresas parceiras                           |
| `MEDICO`           | Portal web           | Pré-cadastra gestantes, agenda exames e consultas e confirma a realização    |
| `EMPRESA_PARCEIRA` | Portal web           | Cadastra produtos, promoções e cupons                                        |
| `GESTANTE`         | App mobile           | Acompanha exames e consultas, consulta os pontos e resgata cupons            |

Contas pré-cadastradas recebem uma senha temporária; o token emitido com ela tem o papel `PRIMEIRO_ACESSO_<perfil>`
e só serve para concluir o primeiro acesso. A gestante e a empresa nunca são lidas do caminho da URL, e sim do token.

## Como rodar

O jeito recomendado é pelo repositório [`previna-development`](../previna-development), que sobe API, banco, portal,
app e MailHog com Docker Compose:

```bash
cd ../previna-development
docker compose up
```

A API fica em `http://localhost/backend` (o nginx remove o prefixo `/backend`) e o Swagger UI em
`http://localhost/backend/swagger-ui.html`.

Para rodar só a API, com um PostgreSQL local:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

O profile `dev` carrega também os dados de exemplo de `db/seed` (administrador e médico).

## Variáveis de ambiente

| Variável                                | Padrão                                  | Uso                                         |
| --------------------------------------- | --------------------------------------- | ------------------------------------------- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD`      | `jdbc:postgresql://localhost:5432/previna`, `previna`, `previna` | Banco de dados    |
| `JWT_SECRET`                            | valor de desenvolvimento                | Segredo do JWT (**troque em produção**, ≥ 32 bytes) |
| `JWT_EXPIRACAO_MINUTOS`                 | `60`                                    | Validade do token do portal                 |
| `JWT_EXPIRACAO_GESTANTE_DIAS`           | `30`                                    | Validade do token do app (sessão no celular)|
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM` | MailHog local (`localhost:1025`) | Envio de e-mails       |
| `PORTAL_URL`                            | `http://localhost`                      | Link do portal no e-mail de pré-cadastro    |
| `SERVER_PORT`                           | `8080`                                  | Porta HTTP                                  |

## Estrutura

```
src/main/java/com/cozyshelf/previna/
  controller/       endpoints REST e tratamento global de erros
  application/
    service/        regras de negócio (uma classe por agregado)
    dto/            requisições e respostas da API
    exception/      erros de negócio (404, 400, 409)
  domain/
    model/          entidades JPA
    repository/     repositórios Spring Data e filtros das listagens
  infrastructure/
    security/       JWT, filtro de autenticação e autorização antes da validação
    config/         Spring Security e Spring MVC
    email/          envio de e-mails
src/main/resources/
  db/migration/     migrações Flyway (V1 a V13)
  db/seed/          dados de exemplo, só no profile dev
docs/               modelo lógico, casos de uso e diagramas de sequência (ver docs/README.md)
```

Erros seguem um padrão: dados inválidos → `400`, recurso inexistente ou de outro usuário → `404`, regra de negócio
violada → `409`. As decisões de arquitetura estão em [`docs/README.md`](docs/README.md).

## Testes

```bash
mvn test
```

- **Unidade** (`application/service`, `domain`): regras de negócio com Mockito, sem Spring nem banco.
- **Controller** (`@WebMvcTest`, com a `SecurityConfig` e o filtro JWT de verdade). `MatrizAutorizacaoTest` confere
  cada rota contra os perfis possíveis (anônimo, administrador, médico, gestante, empresa e os dois primeiros acessos)
  e falha se aparecer rota nova fora da matriz: ao criar um endpoint, adicione uma linha em `ROTAS`.
- **Integração com PostgreSQL** (`ResgateCupomIntegracaoTest`, `FiltrosIntegracaoTest`): a trava contra resgates
  simultâneos (`SELECT ... FOR UPDATE`), o rollback, a consulta por validade e a busca sem acento só se provam no
  banco. Rodam só com a variável `PREVINA_TEST_DB_URL` apontando para um banco **descartável** (as migrações são
  aplicadas nele); sem ela, são pulados.

No ambiente do `previna-development`:

```bash
docker compose exec db psql -U previna -d postgres -c "CREATE DATABASE previna_test"   # uma vez
docker compose exec -e PREVINA_TEST_DB_URL=jdbc:postgresql://db:5432/previna_test backend mvn test
```

## CI

`.github/workflows/ci.yml` roda em todo PR e em todo push na `main`: sobe um PostgreSQL 18 de serviço, define
`PREVINA_TEST_DB_URL` e roda `mvn verify` (testes de unidade, de controller e de integração). O JaCoCo reprova o build
se a cobertura de linhas ou de branches ficar abaixo dos pisos `cobertura.linhas` e `cobertura.branches` do `pom.xml`;
suba os pisos quando a cobertura subir. O relatório fica em `target/site/jacoco/` e nos artefatos da execução.
