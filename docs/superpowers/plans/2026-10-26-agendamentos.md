# Agendamentos (Exames e Consultas) — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O médico cadastra exames e consultas para a gestante no portal web e confirma a realização ou o não comparecimento; a gestante vê os seus no app, sem alterar nada.

**Architecture:** Backend ganha duas entidades (`TipoAtendimento`, `Agendamento`), um `AgendamentoService` com as regras dos diagramas e um `PontuacaoService` que credita pontos na confirmação, tudo nas camadas já existentes (`controller` → `application/service` → `domain/repository`). O web ganha uma página de detalhe da gestante (`/gestantes/:id`) com a lista agrupada por status e o formulário de cadastro no padrão de `FormProduto`. O mobile troca o placeholder da aba Início pela lista somente leitura.

**Tech Stack:** Spring Boot 3.5 / Java 21 / Flyway / PostgreSQL · React 19 + Vite + Tailwind v4 + Vitest · Expo SDK 57 + Expo Router. Nenhuma dependência nova em nenhuma camada.

**Spec:** `docs/sequence-diagrams/cadastro-exame-consulta/cadastro-exame-consulta.puml`, `docs/sequence-diagrams/confirmar-realizacao-exame/confirmar-realizacao-exame.puml`, `docs/implementation-diagrams/confirmacao-pontos/confirmacao-pontos.puml`, `docs/domain/dominio/dominio.puml`, `docs/README.md` (seção "Decisões") e o enunciado desta tarefa.

## Global Constraints

- Nomenclatura em português nas três camadas, como todo o código existente. Não traduzir identificadores.
- Erro de regra de negócio → `409` (`ConflitoException`); dado inválido → `400` (`DadosInvalidosException`); inexistente → `404` (`NaoEncontradoException`). A API **não** usa 422. Corpo de erro sempre `Response(message)`.
- Recurso de outro usuário responde **igual a inexistente** (`404`), nunca `403`.
- Listagens paginadas usam `ListagemRequest` + `PaginaResponse`, com ordenação fixa no servidor e desempate por `id`.
- Mudança de schema só por migração `V<n>__*.sql` nova. Nunca editar migração aplicada. `ddl-auto: validate`.
- `db/seed/R__*.sql` só carrega no profile `dev` (`application-dev.yml`). Dado de referência que precisa existir em produção vai na migração.
- Nenhuma dependência nova: backend usa JUnit 5 + Mockito (já no `spring-boot-starter-test`), web usa Vitest + Testing Library, mobile não tem runner de teste configurado.
- Web: `@/` → `src/`; formulário usa `Form` + `SecaoFormulario` + `InputGroup`/`SelectGroup` + `useValidacao`, e toda chamada HTTP passa por `lib/api.ts`.
- Mobile: rotas só em `src/app/`; componentes, libs e constantes fora. Token nunca sai de `lib/sessao.ts`.

## Review Focus

Classes de entrada que a spec implica mas que nenhum fluxo dos diagramas exercita. Cada linha tem o teste que a fixa, na tarefa indicada:

1. **Confirmar duas vezes em paralelo (duplo clique / duas abas) credita pontos duas vezes.** Esperado: o segundo pedido falha e o saldo sobe uma vez só. → UNIQUE em `transacao_pontos.agendamento_id` (Tarefa 1) + teste em Tarefa 6.
2. **`GET /gestantes/{id}` com `id` de uma gestante de outro médico.** Esperado: `404`, idêntico a id inexistente — sem vazar que a gestante existe. → Tarefa 4.
3. **`POST /agendamentos` com `tipoAtendimento` existente porém `ativo = false`.** Esperado: `400` "Tipo de atendimento inválido" (o diagrama trata inexistente e inativo no mesmo ramo). → Tarefa 3.
4. **Gestante sem `dados_socioeconomicos` (pré-cadastrada, primeiro acesso ainda pendente) na confirmação.** Esperado: fator 1,00, crédito normal, sem `NullPointerException`. → Tarefa 5.
5. **`dataPrevista` muito distante (ex.: ano 3000) ou gestante já com parto previsto no passado.** Esperado: aceitar — não há regra de limite superior; apenas o passado é recusado. Fixado explicitamente para que ninguém invente um limite. → Tarefa 3.

---

## 1. O que os diagramas dizem, e onde divergem do enunciado

### 1.1 Entendimento dos diagramas

**`cadastro-exame-consulta`** — O médico abre a tela, que busca `GET /tipos-atendimento?categoria={EXAME|CONSULTA}` (só os ativos). Envia `POST /agendamentos` com `idGestante`, `idTipoAtendimento`, `dataPrevista`, `observacao`. Validações, em ordem: campo obrigatório ausente ou `dataPrevista` no passado → `400`; gestante inexistente **ou não acompanhada pelo médico** → `404`; tipo inexistente **ou inativo** → `400`. Sucesso grava com `atribuido_por = idMedico` e `status = AGENDADO`, registra log e devolve `201`. Nota explícita: **nenhum ponto é creditado no cadastro**.

**`confirmar-realizacao-exame` + `confirmacao-pontos`** — `PUT /agendamentos/{id}/confirmar`, só perfil `MEDICO`. Busca o agendamento pelo id **e pelo médico do token**; não encontrado → `404`; status diferente de `AGENDADO` → `409` "Este agendamento já foi confirmado ou cancelado". Sucesso: `status = REALIZADO`, `confirmado_por`, `data_confirmacao = agora`; `PontuacaoService` calcula `arredondar(tipo_atendimento.pontos_base × dados_socioeconomicos.fator_ponderacao)`, grava `transacao_pontos` (`tipo = CREDITO`, `fator_aplicado`), soma em `gestante.saldo_pontos`; grava `log_auditoria`; tudo em **uma transação** (`@Transactional`) — ou tudo grava, ou nada. Fator é 1,00 hoje, e 1,00 também quando não há dados socioeconômicos. A tela responde "Presença confirmada e \<n\> pontos creditados".

### 1.2 Divergências entre os diagramas e o enunciado — **precisam da sua confirmação**

| # | Diagrama / docs | Enunciado | Recomendação |
|---|---|---|---|
| **D1** | Status `AGENDADO \| REALIZADO \| CANCELADO` (`dominio.puml` e `docs/README.md`, seção "Decisões") | "pendentes, realizados e **não comparecidos**"; ação de "registrar **não comparecimento**" | **Trocar o terceiro status para `NAO_COMPARECEU`.** "Cancelado" e "não compareceu" são fatos diferentes: cancelar é desmarcar antes, não comparecer é faltar. O enunciado só pede o segundo, e `CANCELADO` sem tela que cancele seria status morto. Exige atualizar `docs/README.md` e `dominio.puml` (Tarefa 12). **Se você quiser os dois status, diga agora** — muda migração, enum, agrupamento do web e do mobile. |
| **D2** | Ambos os diagramas creditam pontos na confirmação, com `transacao_pontos` e `saldo_pontos` | O escopo de back-end não cita pontos | **Incluir o crédito de pontos.** É a razão de existir da confirmação nos dois diagramas, e `gestante.saldo_pontos` já existe no schema sem nada que o alimente. Sem isso a feature fica meio pronta. Custo: uma tabela (`transacao_pontos`) e um serviço (Tarefa 5). |
| **D3** | Ambos gravam `log_auditoria` | Não citado | **Adiar.** Não tem comportamento visível, não tem migração, e puxaria tabela + enum de evento para esta entrega. Fica registrado como pendência no `docs/README.md` (Tarefa 12). |
| **D4** | Nenhum diagrama tem endpoint de não comparecimento | "registrar não comparecimento" | Criar `PUT /agendamentos/{id}/nao-compareceu`, espelhando `confirmar`, **sem creditar pontos**. |
| **D5** | `GET /agendamentos?status=AGENDADO` — lista do médico, de todas as gestantes | A lista aparece **dentro da tela da gestante** | **Usar `GET /gestantes/{id}/agendamentos`.** A tela do enunciado é por gestante; a lista geral do médico não tem tela que a consuma (YAGNI). |
| **D6** | "gestante não acompanhada pelo médico → 404" | — | O código já tem a permissão `medico.ver_todas_gestantes`, que deixa um médico ver gestantes de outros (`GestanteService.listar`). O diagrama é mais estrito. **Recomendo reusar a regra que já existe**: quem tem `verTodasGestantes` age sobre qualquer gestante; os demais, só sobre as suas. Confirme — se preferir a regra do diagrama, a permissão passa a valer só para leitura. |
| **D7** | — | — | **Lista de agendamentos da gestante não será paginada.** Agrupar por status com paginação quebra o agrupamento (um status pode ficar partido entre páginas) e o volume é de dezenas por gestação. Diverge da convenção `PaginaResponse` do projeto; por isso está aqui. |
| **D8** | — | "Seed com tipos iniciais" | `db/seed/` **só carrega no profile `dev`**. Tipos de atendimento são dado de referência que precisa existir em produção, então vão **na migração `V11`**, não em `R__seed_*.sql`. |

### 1.3 Dois itens do escopo já estão prontos

- **"Listar gestantes com paginação e filtros" (back-end) já existe:** `GET /gestantes` em `controller/GestanteController.java:46`, com `ListagemRequest` (busca por nome e CPF, `pagina`, `tamanho`), filtro `situacao`, filtro `somenteMinhas` e a regra de `ver_todas_gestantes`. **Nenhuma tarefa deste plano a altera.**
- **"Tela de listagem de gestantes com paginação e filtragem" (web) já existe:** `src/pages/GestantesPage.tsx` + `components/sistema/Listagem.tsx`, com busca com atraso de 300 ms, filtros e paginação na URL, estado vazio e estado "nenhum resultado". A única mudança é a da Tarefa 8: o nome da gestante vira link para o detalhe.

### 1.3 Ponto sem padrão claro no código atual

- **Mobile não tem tela de lista.** `components/tela.tsx` centraliza o conteúdo verticalmente (`justifyContent: 'center'`) e sempre mostra a `Logo` — foi feito para telas de autenticação. A lista precisa alinhar ao topo. Proposta: acrescentar a prop opcional `alinharTopo?: boolean` a `Tela` (Tarefa 11), em vez de criar um segundo wrapper.
- **Web não tem página de detalhe de registro.** Todas as páginas de `sistema` são listagem ou formulário. `GestantePage` é a primeira tela de leitura de um registro; usa `PageHeader` + seções em cartão, reaproveitando as classes de `SecaoFormulario`.

## 2. Modelo de dados

Migração **`V11__tipo_atendimento_agendamento.sql`** (cria as três tabelas e insere os tipos). Tabelas conforme `dominio.puml`, com estas diferenças, todas justificadas:

```sql
CREATE TABLE tipo_atendimento (
    id           BIGSERIAL    PRIMARY KEY,
    nome         VARCHAR(120) NOT NULL UNIQUE,
    categoria    VARCHAR(20)  NOT NULL CHECK (categoria IN ('EXAME', 'CONSULTA')),
    descricao    TEXT,
    pontos_base  INTEGER      NOT NULL CHECK (pontos_base > 0),
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE agendamento (
    id                   BIGSERIAL   PRIMARY KEY,
    gestante_id          BIGINT      NOT NULL REFERENCES gestante (usuario_id),
    tipo_atendimento_id  BIGINT      NOT NULL REFERENCES tipo_atendimento (id),
    atribuido_por        BIGINT      NOT NULL REFERENCES medico (usuario_id),
    confirmado_por       BIGINT      REFERENCES medico (usuario_id),
    data_prevista        DATE        NOT NULL,
    status               VARCHAR(20) NOT NULL CHECK (status IN ('AGENDADO', 'REALIZADO', 'NAO_COMPARECEU')),
    data_confirmacao     TIMESTAMP,
    observacao           TEXT,
    -- Pendente nunca tem desfecho; desfecho nunca fica sem data e sem quem registrou
    CHECK ((status = 'AGENDADO') = (data_confirmacao IS NULL AND confirmado_por IS NULL))
);
CREATE INDEX ON agendamento (gestante_id);

CREATE TABLE transacao_pontos (
    id              BIGSERIAL     PRIMARY KEY,
    gestante_id     BIGINT        NOT NULL REFERENCES gestante (usuario_id),
    -- UNIQUE: duas confirmações simultâneas do mesmo agendamento não creditam duas vezes
    agendamento_id  BIGINT        UNIQUE REFERENCES agendamento (id),
    tipo            VARCHAR(10)   NOT NULL CHECK (tipo IN ('CREDITO', 'DEBITO')),
    pontos          INTEGER       NOT NULL CHECK (pontos > 0),
    fator_aplicado  NUMERIC(4, 2),
    data_hora       TIMESTAMP     NOT NULL
);
CREATE INDEX ON transacao_pontos (gestante_id);
```

Diferenças em relação a `dominio.puml`, de propósito:
- `status` sem `CANCELADO` → ver **D1**.
- `transacao_pontos.cupom_resgatado_id` **omitida**: a tabela `cupom_resgatado` não existe ainda; a coluna entra com ela.
- `agendamento_id` **UNIQUE** (o diagrama só indica a FK) → Review Focus #1.
- `log_auditoria` **não criada** → ver **D3**.

### Enums (Java)

| Enum | Valores | Arquivo |
|---|---|---|
| `CategoriaAtendimento` | `EXAME`, `CONSULTA` | `domain/model/CategoriaAtendimento.java` — nome com sufixo porque `Categoria` já existe (produto) |
| `StatusAgendamento` | `AGENDADO`, `REALIZADO`, `NAO_COMPARECEU` | `domain/model/StatusAgendamento.java` |
| `TipoTransacao` | `CREDITO`, `DEBITO` | `domain/model/TipoTransacao.java` |

### Relacionamentos

`gestante 1—N agendamento` · `tipo_atendimento 1—N agendamento` · `medico 1—N agendamento` (por `atribuido_por` e por `confirmado_por`) · `gestante 1—N transacao_pontos` · `agendamento 1—0..1 transacao_pontos`.

## 3. Tipos de atendimento no dado inicial (dentro da `V11`)

| Categoria | Nome | `pontos_base` |
|---|---|---|
| CONSULTA | Consulta de pré-natal — 1º trimestre | 50 |
| CONSULTA | Consulta de pré-natal — 2º trimestre | 50 |
| CONSULTA | Consulta de pré-natal — 3º trimestre | 50 |
| CONSULTA | Consulta odontológica | 40 |
| CONSULTA | Consulta puerperal | 50 |
| EXAME | Hemograma completo | 25 |
| EXAME | Tipagem sanguínea e fator Rh | 25 |
| EXAME | Coombs indireto | 25 |
| EXAME | Glicemia de jejum | 25 |
| EXAME | Teste de tolerância à glicose (TOTG 75 g) | 30 |
| EXAME | Sorologia para sífilis (VDRL) | 25 |
| EXAME | Teste rápido de sífilis | 20 |
| EXAME | Sorologia para HIV | 25 |
| EXAME | Teste rápido de HIV | 20 |
| EXAME | Sorologia para hepatite B (HBsAg) | 25 |
| EXAME | Sorologia para hepatite C (anti-HCV) | 25 |
| EXAME | Sorologia para toxoplasmose (IgG e IgM) | 25 |
| EXAME | Urina tipo I (EAS) | 20 |
| EXAME | Urocultura com antibiograma | 20 |
| EXAME | Ultrassonografia obstétrica | 35 |
| EXAME | Ultrassonografia morfológica | 35 |

21 tipos, todos `ativo = true`, `descricao` nula. Os valores de `pontos_base` são uma proposta (consulta vale mais que exame simples; exame de imagem e TOTG no meio) — **confirme ou ajuste a tabela antes da Tarefa 1**.

## 4. Endpoints

| Método | Rota | Payload | Resposta | Permissão |
|---|---|---|---|---|
| GET | `/tipos-atendimento?categoria=EXAME\|CONSULTA` | — | `200` `TipoAtendimentoResponse[]` (só ativos, ordenados por nome; sem `categoria` devolve os dois) | `MEDICO` |
| GET | `/gestantes/{id}` | — | `200` `GestanteDetalheResponse` · `404` fora do alcance do médico | `MEDICO` |
| GET | `/gestantes/{id}/agendamentos` | — | `200` `AgendamentoResponse[]` (pendentes primeiro; ver ordenação abaixo) · `404` | `MEDICO` |
| POST | `/agendamentos` | `AgendamentoRequest{gestante, tipoAtendimento, dataPrevista, observacao}` | `201` `AgendamentoResponse` · `400` dados ou tipo inválido · `404` gestante | `MEDICO` |
| PUT | `/agendamentos/{id}/confirmar` | — | `200` `ConfirmacaoResponse` · `404` · `409` | `MEDICO` |
| PUT | `/agendamentos/{id}/nao-compareceu` | — | `200` `AgendamentoResponse` · `404` · `409` | `MEDICO` |
| GET | `/gestantes/agendamentos` | — | `200` `AgendamentoResponse[]` da gestante do token | `GESTANTE` |

Todas exigem token (`SecurityConfig` já fecha com `anyRequest().authenticated()`); nenhuma entra na lista de rotas públicas. Perfil errado → `403` pelo `@PreAuthorize`.

**Conflito de rota:** `/gestantes/agendamentos` (literal, `GESTANTE`) e `/gestantes/{id}` (variável, `MEDICO`) convivem porque o PathPattern do Spring prioriza o segmento literal — mesma situação de `/gestantes/perfil` e `/gestantes/foto`, que já existem. Fixado por teste na Tarefa 7.

**Ordenação de `/gestantes/{id}/agendamentos`:** `AGENDADO` primeiro, depois `REALIZADO` e `NAO_COMPARECEU`; dentro do grupo, `data_prevista` crescente e `id` como desempate. Ordenar por enum no banco é frágil (depende da ordem alfabética do texto), então a ordenação é feita em Java por um `Comparator` sobre `StatusAgendamento.ordinal()`.

### Formato dos DTOs

```java
// request
record AgendamentoRequest(@NotNull Long gestante, @NotNull Long tipoAtendimento,
                          @NotNull @FutureOrPresent LocalDate dataPrevista,
                          @Size(max = 1000) String observacao) {}
// responses
record TipoAtendimentoResponse(Long id, String nome, CategoriaAtendimento categoria, Integer pontosBase) {}
record AgendamentoResponse(Long id, String tipo, CategoriaAtendimento categoria, Integer pontosBase,
                           LocalDate dataPrevista, StatusAgendamento status,
                           LocalDateTime dataConfirmacao, String observacao) {}
record ConfirmacaoResponse(AgendamentoResponse agendamento, int pontosCreditados, int saldoPontos) {}
record GestanteDetalheResponse(Long id, String nome, String cpf, String telefone, String email,
                               LocalDate dataNascimento, LocalDate dum, LocalDate dataProvavelParto,
                               int saldoPontos, boolean primeiroAcessoPendente, String medico) {}
```

## 5. Arquivos

### Backend — criar

| Arquivo | Padrão de referência |
|---|---|
| `domain/model/CategoriaAtendimento.java`, `StatusAgendamento.java`, `TipoTransacao.java` | `domain/model/Categoria.java` |
| `domain/model/TipoAtendimento.java` | `domain/model/Produto.java` |
| `domain/model/Agendamento.java` | `domain/model/Cupom.java` (construtor sem id, setters trocados por métodos de domínio) |
| `domain/model/TransacaoPontos.java` | `domain/model/Cupom.java` |
| `domain/repository/TipoAtendimentoRepository.java` | `domain/repository/ProdutoRepository.java` |
| `domain/repository/AgendamentoRepository.java` | `domain/repository/CupomRepository.java` (`@EntityGraph` para o tipo) |
| `domain/repository/TransacaoPontosRepository.java` | `domain/repository/ProdutoRepository.java` |
| `application/dto/request/AgendamentoRequest.java` | `application/dto/request/CupomRequest.java` |
| `application/dto/response/TipoAtendimentoResponse.java`, `AgendamentoResponse.java`, `ConfirmacaoResponse.java`, `GestanteDetalheResponse.java` | `application/dto/response/CupomResponse.java`, `GestanteResumoResponse.java` |
| `application/service/AgendamentoService.java` | `application/service/CupomService.java` |
| `application/service/PontuacaoService.java` | `application/service/CredencialService.java` (serviço de apoio, sem controller) |
| `controller/AgendamentoController.java` | `controller/CupomController.java` |
| `controller/TipoAtendimentoController.java` | `controller/MedicoController.java` |
| `src/main/resources/db/migration/V11__tipo_atendimento_agendamento.sql` | `V8__cupom.sql` |
| `src/test/.../service/AgendamentoServiceTest.java`, `PontuacaoServiceTest.java` | `service/CupomServiceTest.java` |
| `src/test/.../dto/request/AgendamentoRequestTest.java` | `dto/request/PrimeiroAcessoRequestTest.java` |

### Backend — alterar

| Arquivo | O quê |
|---|---|
| `controller/GestanteController.java` | 3 endpoints novos: `GET /{id}`, `GET /{id}/agendamentos`, `GET /agendamentos` |
| `application/service/GestanteService.java` | `buscarDetalhe(Long idMedico, Long idGestante)` e o helper `gestanteDoMedico(...)` que aplica a regra **D6** |
| `domain/model/Gestante.java` | `getSaldoPontos()` e `creditarPontos(int)` |
| `domain/model/DadosSocioeconomicos.java` | `getFatorPonderacao()` (hoje o campo não tem getter) |
| `docs/README.md`, `docs/domain/dominio/dominio.puml` | status novo, tabelas saindo de "previsto", pendência do `log_auditoria` |

### Web — criar

| Arquivo | Padrão de referência |
|---|---|
| `src/pages/GestantePage.tsx` | `src/pages/ProdutoPage.tsx` (carrega por `useParams`, valida id numérico, mostra `Alerta` no erro) |
| `src/pages/CadastroAgendamentoPage.tsx` | `src/pages/CadastroCupomPage.tsx` |
| `src/components/agendamento/FormAgendamento.tsx` | `src/components/produto/FormProduto.tsx` (`Form` + `SecaoFormulario` + `useValidacao` + rodapé `bg-zinc-50`) |
| `src/components/agendamento/ListaAgendamentos.tsx` | `TabelaGestantes` em `src/pages/GestantesPage.tsx` |
| `src/components/agendamento/statusAgendamento.ts` | `src/components/produto/categorias.ts` (rótulos) + `src/components/sistema/SituacaoConta.tsx` (selo) |
| `src/components/gestante/DadosGestante.tsx` | `src/components/empresa/SecaoSobreEmpresa.tsx` |
| Testes `FormAgendamento.test.tsx`, `ListaAgendamentos.test.tsx`, `GestantePage.test.tsx` | `src/components/cupom/FormCupom.test.tsx` |

### Web — alterar

| Arquivo | O quê |
|---|---|
| `src/routes.tsx` | sob `gestantes`: `{ path: ':id', element: <GestantePage />, handle: titulo('Gestante') }` e `{ path: ':id/agendamentos/novo', element: <CadastroAgendamentoPage />, handle: titulo('Novo exame ou consulta') }` |
| `src/pages/GestantesPage.tsx` | nome da gestante vira `Link` para `/gestantes/{id}` |

### Mobile — criar / alterar

| Arquivo | O quê / referência |
|---|---|
| `src/components/agendamento.tsx` (criar) | Cartão de um agendamento; estilos no padrão de `src/components/alerta.tsx` (StyleSheet + tokens de `constants/theme`) |
| `src/app/(abas)/inicio.tsx` (alterar) | Troca o placeholder pela lista; busca com `get<Agendamento[]>('/gestantes/agendamentos')` no padrão de `app/(abas)/perfil.tsx` |
| `src/components/tela.tsx` (alterar) | prop `alinharTopo?: boolean` (ver 1.3) |

## 6. Tarefas

### Task 1: Schema, entidades e repositórios

**Files:**
- Create: `src/main/resources/db/migration/V11__tipo_atendimento_agendamento.sql`, `src/main/java/com/cozyshelf/previna/domain/model/{CategoriaAtendimento,StatusAgendamento,TipoTransacao,TipoAtendimento,Agendamento,TransacaoPontos}.java`, `src/main/java/com/cozyshelf/previna/domain/repository/{TipoAtendimentoRepository,AgendamentoRepository,TransacaoPontosRepository}.java`
- Modify: `src/main/java/com/cozyshelf/previna/domain/model/Gestante.java`, `src/main/java/com/cozyshelf/previna/domain/model/DadosSocioeconomicos.java`

**Interfaces:**
- Produces: `TipoAtendimento` com `getId()`, `getNome()`, `getCategoria()`, `getPontosBase()`, `isAtivo()`. `Agendamento(Gestante gestante, TipoAtendimento tipo, Medico atribuidoPor, LocalDate dataPrevista, String observacao)` — nasce `AGENDADO` — mais `getId()`, `getGestante()`, `getTipo()`, `getDataPrevista()`, `getStatus()`, `getDataConfirmacao()`, `getObservacao()`, `pendente()`, `registrarDesfecho(StatusAgendamento status, Medico confirmadoPor)`. `TransacaoPontos(Gestante gestante, Agendamento agendamento, TipoTransacao tipo, int pontos, BigDecimal fatorAplicado)`. `Gestante.getSaldoPontos()`, `Gestante.creditarPontos(int)`. `DadosSocioeconomicos.getFatorPonderacao()`. `AgendamentoRepository extends JpaRepository<Agendamento, Long>` com `List<Agendamento> findByGestanteId(Long gestanteId)` e `Optional<Agendamento> findById(Long id)` (ambos com `@EntityGraph(attributePaths = {"tipo", "gestante"})`). `TipoAtendimentoRepository` com `List<TipoAtendimento> findByAtivoTrueOrderByNomeAsc()` e `List<TipoAtendimento> findByCategoriaAndAtivoTrueOrderByNomeAsc(CategoriaAtendimento categoria)`.

- [ ] **Step 1: Escrever a migração `V11`**

Tabelas e `CREATE INDEX` exatamente como na seção 2, seguidas de um `INSERT INTO tipo_atendimento (nome, categoria, pontos_base) VALUES ...` com as 21 linhas da seção 3.

- [ ] **Step 2: Criar os três enums e as três entidades**

`@Enumerated(EnumType.STRING)` em `categoria`, `status` e `tipo`, como em `Produto.categoria`.

**Cada `@ManyToOne` precisa de `@JoinColumn` explícito.** `Gestante` e `Medico` têm a PK na coluna `usuario_id`, então o nome que o Hibernate geraria sozinho (`gestante_usuario_id`, `atribuido_por_usuario_id`) não bate com a migração e o `ddl-auto: validate` derruba a aplicação no boot. Mapear: `gestante` → `@JoinColumn(name = "gestante_id")`, `tipo` → `@JoinColumn(name = "tipo_atendimento_id")`, `atribuidoPor` → `@JoinColumn(name = "atribuido_por")`, `confirmadoPor` → `@JoinColumn(name = "confirmado_por")`, e em `TransacaoPontos` o mesmo para `gestante` e `agendamento`. Todos `fetch = FetchType.LAZY`, como em `Cupom.produto`. `Agendamento.registrarDesfecho` grava `status`, `confirmadoPor` e `dataConfirmacao = LocalDateTime.now()`; `pendente()` é `status == AGENDADO`. Construtores `protected` sem argumentos para o JPA, como em `Cupom`.

- [ ] **Step 3: Criar os três repositórios e os getters novos em `Gestante` e `DadosSocioeconomicos`**

- [ ] **Step 4: Subir a aplicação e conferir que o Flyway aplicou e o Hibernate validou**

Run: `cd previna-development && npm run restart:dev:backend && npm run logs:dev:backend`
Expected: log com `Migrating schema "public" to version "11 - tipo atendimento agendamento"` e `Started PrevinaApplication`, sem `SchemaManagementException`.

- [ ] **Step 5: Conferir o dado inicial**

Run: `docker compose exec db psql -U previna -d previna -c "SELECT categoria, count(*) FROM tipo_atendimento GROUP BY categoria ORDER BY categoria;"`
Expected: `CONSULTA | 5` e `EXAME | 16`.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V11__tipo_atendimento_agendamento.sql src/main/java/com/cozyshelf/previna/domain
git commit -m "feat: modelo de tipos de atendimento, agendamentos e transações de pontos"
```

---

### Task 2: Listar tipos de atendimento

**Files:**
- Create: `application/dto/response/TipoAtendimentoResponse.java`, `application/service/AgendamentoService.java`, `controller/TipoAtendimentoController.java`
- Test: `src/test/java/com/cozyshelf/previna/application/service/AgendamentoServiceTest.java`

**Interfaces:**
- Consumes: `TipoAtendimentoRepository` (Task 1).
- Produces: `AgendamentoService.listarTiposAtivos(CategoriaAtendimento categoria) -> List<TipoAtendimentoResponse>` — `categoria` nula devolve todos os ativos.

- [ ] **Step 1: Escrever os testes que falham**

```java
@Test void listaSoOsTiposAtivosDaCategoria() // repo mockado devolve 2 EXAMEs; assertEquals(2, resposta.size()) e verify(findByCategoriaAndAtivoTrueOrderByNomeAsc(EXAME))
@Test void semCategoriaListaTodosOsAtivos()  // verify(findByAtivoTrueOrderByNomeAsc()), nunca o método por categoria
```

- [ ] **Step 2: Rodar e verificar que falha**

Run: `mvn test -Dtest=AgendamentoServiceTest`
Expected: FAIL — `AgendamentoService` não existe.

- [ ] **Step 3: Implementar `listarTiposAtivos` e o `TipoAtendimentoController`**

Controller em `@RequestMapping("/tipos-atendimento")` com `@PreAuthorize("hasRole('MEDICO')")` na classe, como `MedicoController`; `@RequestParam(required = false) CategoriaAtendimento categoria`.

- [ ] **Step 4: Rodar e verificar que passa**

Run: `mvn test -Dtest=AgendamentoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cozyshelf/previna/application src/main/java/com/cozyshelf/previna/controller/TipoAtendimentoController.java src/test
git commit -m "feat: endpoint de tipos de atendimento ativos"
```

---

### Task 3: Cadastrar agendamento

**Files:**
- Create: `application/dto/request/AgendamentoRequest.java`, `application/dto/response/AgendamentoResponse.java`, `controller/AgendamentoController.java`
- Modify: `application/service/AgendamentoService.java`, `application/service/GestanteService.java`
- Test: `src/test/.../service/AgendamentoServiceTest.java`, `src/test/.../dto/request/AgendamentoRequestTest.java`

**Interfaces:**
- Consumes: `AgendamentoRepository`, `TipoAtendimentoRepository`, `MedicoRepository`, `GestanteRepository` (Task 1 e existentes).
- Produces: `AgendamentoService.agendar(AgendamentoRequest dados, Long idMedico) -> AgendamentoResponse`. `GestanteService.gestanteDoMedico(Long idMedico, Long idGestante) -> Gestante` (público; lança `NaoEncontradoException("Gestante não encontrada")`, aplicando a regra **D6**) — usado também pelas Tasks 4 e 6.

- [ ] **Step 1: Escrever os testes de validação do request**

```java
// AgendamentoRequestTest, @ParameterizedTest @CsvSource, no padrão de PrimeiroAcessoRequestTest
// (gestante, tipoAtendimento, dataPrevista, valido)
"1, 1, HOJE, true" · "1, 1, AMANHA, true" · "1, 1, ONTEM, false"
", 1, AMANHA, false" · "1, , AMANHA, false" · "1, 1, , false"
"1, 1, 3000-01-01, true"   // Review Focus #5: não há limite superior
```

- [ ] **Step 2: Escrever os testes de serviço que falham**

```java
@Test void cadastraComStatusAgendadoEMedicoDoToken()
// assertEquals(AGENDADO, salvo.getStatus()); assertSame(medico, salvo.getAtribuidoPor()); assertNull(resposta.dataConfirmacao())
@Test void recusaTipoInativo()        // Review Focus #3: tipo existe com ativo=false
// DadosInvalidosException "Tipo de atendimento inválido"; verify(agendamentoRepository, never()).save(any())
@Test void recusaTipoInexistente()    // mesma exceção e mesma mensagem
@Test void naoCadastraParaGestanteForaDoAlcanceDoMedico()
// gestanteDoMedico lança NaoEncontradoException "Gestante não encontrada"; nada é salvo
@Test void naoCreditaPontosNoCadastro()
// verify(transacaoPontosRepository, never()).save(any()); assertEquals(0, gestante.getSaldoPontos())
```

- [ ] **Step 3: Rodar e verificar que falham**

Run: `mvn test -Dtest=AgendamentoServiceTest+AgendamentoRequestTest`
Expected: FAIL — `agendar` não existe.

- [ ] **Step 4: Implementar `GestanteService.gestanteDoMedico` e `AgendamentoService.agendar`**

`gestanteDoMedico` carrega o `Medico`; com `podeVerTodasGestantes()` busca só por id, senão por id **e** médico — por isso `GestanteRepository` ganha `Optional<Gestante> findByIdAndMedicoId(Long id, Long medicoId)`. Ordem das checagens igual ao diagrama: gestante (`404`) antes do tipo (`400`).

- [ ] **Step 5: Implementar `AgendamentoController` com `POST /agendamentos`**

`@PreAuthorize("hasRole('MEDICO')")` na classe, `@RequestBody @Valid`, `@AuthenticationPrincipal UsuarioAutenticado medico`, `ResponseEntity.status(HttpStatus.CREATED)` — padrão de `CupomController`.

- [ ] **Step 6: Rodar e verificar que passam**

Run: `mvn test -Dtest=AgendamentoServiceTest+AgendamentoRequestTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: cadastro de exame ou consulta para a gestante"
```

---

### Task 4: Detalhe da gestante e sua lista de agendamentos

**Files:**
- Create: `application/dto/response/GestanteDetalheResponse.java`
- Modify: `application/service/GestanteService.java`, `application/service/AgendamentoService.java`, `controller/GestanteController.java`
- Test: `src/test/.../service/GestanteServiceTest.java`, `src/test/.../service/AgendamentoServiceTest.java`

**Interfaces:**
- Consumes: `GestanteService.gestanteDoMedico(Long, Long)` (Task 3).
- Produces: `GestanteService.buscarDetalhe(Long idMedico, Long idGestante) -> GestanteDetalheResponse`. `AgendamentoService.listarDaGestante(Long idMedico, Long idGestante) -> List<AgendamentoResponse>`.

- [ ] **Step 1: Escrever os testes que falham**

```java
// GestanteServiceTest
@Test void detalheTrazSaldoDePontosEMedicoResponsavel()
@Test void detalheDeGestanteDeOutroMedicoRespondeComoInexistente()  // Review Focus #2
// assertThrows(NaoEncontradoException.class, ...); mensagem "Gestante não encontrada" — a mesma de id inexistente
@Test void medicoComPermissaoVeGestanteDeOutroMedico()              // regra D6

// AgendamentoServiceTest
@Test void listaPendentesPrimeiroDepoisPorDataPrevista()
// repo devolve [REALIZADO 10/01, AGENDADO 20/01, NAO_COMPARECEU 05/01, AGENDADO 15/01]
// assertEquals(List.of(AGENDADO, AGENDADO, REALIZADO, NAO_COMPARECEU), status na ordem da resposta)
// e entre os dois AGENDADO, 15/01 antes de 20/01
@Test void gestanteSemAgendamentosDevolveListaVazia()               // assertEquals(List.of(), ...)
```

- [ ] **Step 2: Rodar e verificar que falham**

Run: `mvn test -Dtest=GestanteServiceTest+AgendamentoServiceTest`
Expected: FAIL

- [ ] **Step 3: Implementar `buscarDetalhe` e `listarDaGestante`**

Ordenação em Java com `Comparator.comparingInt((Agendamento a) -> a.getStatus().ordinal()).thenComparing(Agendamento::getDataPrevista).thenComparing(Agendamento::getId)` — a ordem do enum (`AGENDADO`, `REALIZADO`, `NAO_COMPARECEU`) é a ordem de exibição.

- [ ] **Step 4: Acrescentar `GET /gestantes/{id}` e `GET /gestantes/{id}/agendamentos` ao `GestanteController`**

Ambos `@PreAuthorize("hasRole('MEDICO')")`.

- [ ] **Step 5: Rodar e verificar que passam**

Run: `mvn test`
Expected: PASS, suíte inteira.

- [ ] **Step 6: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: detalhe da gestante com os seus exames e consultas"
```

---

### Task 5: Crédito de pontos

**Files:**
- Create: `application/service/PontuacaoService.java`
- Test: `src/test/java/com/cozyshelf/previna/application/service/PontuacaoServiceTest.java`

**Interfaces:**
- Consumes: `TransacaoPontosRepository`, `DadosSocioeconomicosRepository`, `Gestante.creditarPontos(int)`, `DadosSocioeconomicos.getFatorPonderacao()` (Task 1).
- Produces: `PontuacaoService.creditarPorAgendamento(Agendamento agendamento) -> int` (pontos creditados).

- [ ] **Step 1: Escrever os testes que falham**

```java
@Test void creditaPontosBaseVezesOFatorArredondado()
// pontosBase 25, fator 1.50 -> 38 (arredondamento HALF_UP de 37,5); gestante.getSaldoPontos() == 38
// transacao salva com tipo CREDITO, pontos 38, fatorAplicado 1.50 e o agendamento
@Test void usaFatorNeutroQuandoNaoHaDadosSocioeconomicos()   // Review Focus #4
// findById devolve Optional.empty(); pontosBase 25 -> 25 creditados, fatorAplicado BigDecimal.ONE
@Test void somaAoSaldoQueJaExistia()                          // saldo 100 + 25 = 125
```

- [ ] **Step 2: Rodar e verificar que falham**

Run: `mvn test -Dtest=PontuacaoServiceTest`
Expected: FAIL — `PontuacaoService` não existe.

- [ ] **Step 3: Implementar `creditarPorAgendamento`**

`BigDecimal.valueOf(pontosBase).multiply(fator).setScale(0, RoundingMode.HALF_UP).intValue()`; sem `@Transactional` próprio — roda dentro da transação de quem chama (Task 6), como pede o diagrama.

- [ ] **Step 4: Rodar e verificar que passam**

Run: `mvn test -Dtest=PontuacaoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cozyshelf/previna/application/service/PontuacaoService.java src/test
git commit -m "feat: crédito de pontos por agendamento confirmado"
```

---

### Task 6: Confirmar realização e registrar não comparecimento

**Files:**
- Create: `application/dto/response/ConfirmacaoResponse.java`
- Modify: `application/service/AgendamentoService.java`, `controller/AgendamentoController.java`
- Test: `src/test/.../service/AgendamentoServiceTest.java`

**Interfaces:**
- Consumes: `PontuacaoService.creditarPorAgendamento` (Task 5), `GestanteService.gestanteDoMedico` (Task 3).
- Produces: `AgendamentoService.confirmar(Long idAgendamento, Long idMedico) -> ConfirmacaoResponse`, `AgendamentoService.registrarNaoComparecimento(Long idAgendamento, Long idMedico) -> AgendamentoResponse`. Ambos `@Transactional`.

- [ ] **Step 1: Escrever os testes que falham**

```java
@Test void confirmaCreditaPontosEDevolveOSaldo()
// status REALIZADO, dataConfirmacao não nula, confirmadoPor = médico do token
// resposta.pontosCreditados() == 25 e resposta.saldoPontos() == 25
@Test void confirmarAgendamentoJaRealizadoDaConflito()
// ConflitoException "Este agendamento já foi confirmado ou cancelado"; verify(pontuacaoService, never()).creditarPorAgendamento(any())
@Test void confirmarAgendamentoNaoComparecidoDaConflito()
@Test void confirmarAgendamentoDeGestanteForaDoAlcanceDaNaoEncontrado()  // NaoEncontradoException "Agendamento não encontrado"
@Test void naoComparecimentoNaoCreditaPontos()
// status NAO_COMPARECEU, dataConfirmacao preenchida; verify(pontuacaoService, never()).creditarPorAgendamento(any())
@Test void naoComparecimentoEmItemJaResolvidoDaConflito()
```

- [ ] **Step 2: Escrever o teste da corrida de confirmação**  — Review Focus #1

```java
@Test void segundaConfirmacaoSimultaneaNaoCreditaDeNovo()
// transacaoPontosRepository.save lança DataIntegrityViolationException (UNIQUE de agendamento_id)
// assertThrows(DataIntegrityViolationException.class, ...) — o GlobalExceptionHandler já a traduz em 409
```

- [ ] **Step 3: Rodar e verificar que falham**

Run: `mvn test -Dtest=AgendamentoServiceTest`
Expected: FAIL

- [ ] **Step 4: Implementar `confirmar` e `registrarNaoComparecimento`**

Os dois compartilham um helper privado `resolver(Long idAgendamento, Long idMedico, StatusAgendamento desfecho) -> Agendamento`: busca, aplica a regra de alcance do médico (`404`), recusa quem não está pendente (`409`) e chama `registrarDesfecho`. Só `confirmar` chama o `PontuacaoService`.

- [ ] **Step 5: Acrescentar `PUT /agendamentos/{id}/confirmar` e `PUT /agendamentos/{id}/nao-compareceu`**

- [ ] **Step 6: Rodar e verificar que passam**

Run: `mvn test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: confirmação de realização e registro de não comparecimento"
```

---

### Task 7: Lista da gestante autenticada (mobile)

**Files:**
- Modify: `application/service/AgendamentoService.java`, `controller/GestanteController.java`
- Test: `src/test/.../service/AgendamentoServiceTest.java`

**Interfaces:**
- Produces: `AgendamentoService.listarDaGestanteAutenticada(Long idGestante) -> List<AgendamentoResponse>` — mesma ordenação da Task 4, sem checagem de médico.

- [ ] **Step 1: Escrever os testes que falham**

```java
@Test void gestanteVeApenasOsProprios()
// verify(agendamentoRepository).findByGestanteId(7L) com o id do token, nunca um id vindo de parâmetro
@Test void gestanteSemAgendamentosDevolveListaVaziaNoApp()
```

- [ ] **Step 2: Rodar e verificar que falham**

Run: `mvn test -Dtest=AgendamentoServiceTest`
Expected: FAIL

- [ ] **Step 3: Implementar e expor `GET /gestantes/agendamentos` com `@PreAuthorize("hasRole('GESTANTE')")`**

O id vem de `@AuthenticationPrincipal UsuarioAutenticado gestante`, nunca do caminho — convenção já usada em `/gestantes/perfil`.

- [ ] **Step 4: Rodar e verificar que passam**

Run: `mvn test`
Expected: PASS

- [ ] **Step 5: Conferir que a rota literal ganha de `/gestantes/{id}`**

Run: `docker compose restart backend` e, com um token de gestante, `curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer <token>" http://localhost/backend/gestantes/agendamentos`
Expected: `200` (e não `403`, que seria o `@PreAuthorize('MEDICO')` de `/gestantes/{id}` tendo capturado a rota).

- [ ] **Step 6: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: lista de exames e consultas da gestante autenticada"
```

---

### Task 8: Web — página de detalhe da gestante com a lista

**Files:**
- Create: `src/pages/GestantePage.tsx`, `src/components/gestante/DadosGestante.tsx`, `src/components/agendamento/ListaAgendamentos.tsx`, `src/components/agendamento/statusAgendamento.ts`
- Modify: `src/routes.tsx`, `src/pages/GestantesPage.tsx`
- Test: `src/pages/GestantePage.test.tsx`, `src/components/agendamento/ListaAgendamentos.test.tsx`

**Interfaces:**
- Consumes: `GET /gestantes/{id}` e `GET /gestantes/{id}/agendamentos` (Task 4).
- Produces: `interface Agendamento { id: number; tipo: string; categoria: 'EXAME' | 'CONSULTA'; pontosBase: number; dataPrevista: string; status: StatusAgendamento; dataConfirmacao?: string; observacao?: string }` e `type StatusAgendamento = 'AGENDADO' | 'REALIZADO' | 'NAO_COMPARECEU'` em `statusAgendamento.ts`, com `ROTULO_STATUS` (`Pendente`, `Realizado`, `Não compareceu`) e o componente `SeloStatus`. `ListaAgendamentos` recebe `{ agendamentos: Agendamento[]; onResolver: (id: number, status: StatusAgendamento) => void; resolvendo?: number }` (a ação chega na Task 10).

- [ ] **Step 1: Escrever os testes que falham**

```tsx
// ListaAgendamentos.test.tsx
it('agrupa por status com os pendentes primeiro')
// três cabeçalhos na ordem: /pendentes/i, /realizados/i, /não compareceram/i
it('não mostra um grupo vazio')   // sem NAO_COMPARECEU, queryByText(/não compareceram/i) é null

// GestantePage.test.tsx — fetch mockado no padrão de FormCupom.test.tsx
it('mostra os dados da gestante e os seus agendamentos')
it('mostra o estado vazio e o link de cadastro quando não há nenhum agendamento')
// findByText(/nenhum exame ou consulta cadastrado/i) e link "Cadastrar exame ou consulta" -> /gestantes/7/agendamentos/novo
it('avisa sem quebrar quando a API falha')  // 404 -> Alerta "Não foi possível carregar a gestante."
```

- [ ] **Step 2: Rodar e verificar que falham**

Run: `npx vitest run src/pages/GestantePage.test.tsx src/components/agendamento/ListaAgendamentos.test.tsx`
Expected: FAIL — módulos inexistentes.

- [ ] **Step 3: Implementar `statusAgendamento.ts`, `DadosGestante`, `ListaAgendamentos` e `GestantePage`**

`GestantePage` segue `ProdutoPage`: valida `/^\d+$/` no `id` antes de montar o caminho da API, mostra "Carregando…" e `Alerta` no erro. `PageHeader` com o nome da gestante e a ação `Link` "Cadastrar exame ou consulta" (`BUTTON_CLASSES.primary` + `<Icone nome="adicionar" />`). `ListaAgendamentos` renderiza uma `<section>` por grupo não vazio, no padrão de tabela de `TabelaGestantes`; `SeloStatus` copia as classes de pílula de `SituacaoConta` (âmbar para pendente, esmeralda para realizado, zinc para não compareceu). Data formatada com `formatarData` de `@/lib/datas`.

- [ ] **Step 4: Ligar as rotas e o link da listagem**

- [ ] **Step 5: Rodar os testes, o lint e o typecheck**

Run: `npm test && npm run lint && npm run typecheck`
Expected: PASS nos três.

- [ ] **Step 6: Commit**

```bash
git add src/pages src/components src/routes.tsx
git commit -m "feat: tela de detalhe da gestante com exames e consultas"
```

---

### Task 9: Web — formulário de cadastro

**Files:**
- Create: `src/pages/CadastroAgendamentoPage.tsx`, `src/components/agendamento/FormAgendamento.tsx`
- Test: `src/components/agendamento/FormAgendamento.test.tsx`

**Interfaces:**
- Consumes: `GET /tipos-atendimento` (Task 2), `POST /agendamentos` (Task 3), `useValidacao`, `Form`, `SecaoFormulario`, `InputGroup`, `SelectGroup`.

- [ ] **Step 1: Escrever os testes que falham**

```tsx
it('cadastra para a gestante da rota com o tipo escolhido')
// seleciona Categoria "Exame" -> a lista de Tipo passa a trazer só os EXAME
// POST em /agendamentos com body { gestante: 7, tipoAtendimento: 3, dataPrevista: <amanhã>, observacao: 'Jejum de 8h' }
// e navega para /gestantes/7 com a mensagem "Exame cadastrado com sucesso."
it('exige tipo e data, e recusa data no passado, sem chamar a API')
// "Escolha o exame ou a consulta." · "Informe a data prevista." · "A data não pode ser no passado."
// nenhuma chamada com method POST
it('mostra erro quando o servidor recusa os dados')  // 400 -> Alerta
```

- [ ] **Step 2: Rodar e verificar que falham**

Run: `npx vitest run src/components/agendamento/FormAgendamento.test.tsx`
Expected: FAIL

- [ ] **Step 3: Implementar `FormAgendamento`**

Estrutura idêntica a `FormProduto`: `Form` com `divide-y ... rounded-lg border`, uma `SecaoFormulario` ("Exame ou consulta"), rodapé `bg-zinc-50` com `Link` "Cancelar" para `/gestantes/{id}` e `Button` de envio. Campos: `SelectGroup` "Categoria" (`Exame`/`Consulta`, filtra a lista seguinte), `SelectGroup` "Tipo", `InputGroup` "Data prevista" (`type="date"`, `min={hoje()}` de `@/lib/datas`), `InputGroup` "Observação" (opcional, `maxLength={1000}`). `VALIDACOES: Validacoes<'tipoAtendimento' | 'dataPrevista'>` com as mensagens acima. Corpo vai como JSON (`post`), não `FormData` — não há arquivo.

- [ ] **Step 4: Implementar `CadastroAgendamentoPage`**

`PageHeader` ("Cadastrar exame ou consulta") + `FormAgendamento`, no padrão de `CadastroCupomPage`.

- [ ] **Step 5: Rodar testes, lint e typecheck**

Run: `npm test && npm run lint && npm run typecheck`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/pages/CadastroAgendamentoPage.tsx src/components/agendamento
git commit -m "feat: formulário de cadastro de exame ou consulta"
```

---

### Task 10: Web — confirmar realização e não comparecimento

**Files:**
- Modify: `src/components/agendamento/ListaAgendamentos.tsx`, `src/pages/GestantePage.tsx`
- Test: `src/pages/GestantePage.test.tsx`

**Interfaces:**
- Consumes: `PUT /agendamentos/{id}/confirmar` e `PUT /agendamentos/{id}/nao-compareceu` (Task 6).

- [ ] **Step 1: Escrever os testes que falham**

```tsx
it('confirma a realização e move o item para os realizados')
// clica em "Confirmar realização" na linha pendente; PUT em /agendamentos/12/confirmar
// resposta { pontosCreditados: 25, saldoPontos: 125 } -> findByText(/presença confirmada e 25 pontos creditados/i)
// e o item passa a aparecer sob "Realizados"
it('registra o não comparecimento sem falar em pontos')
// PUT em /agendamentos/12/nao-compareceu; queryByText(/pontos/i) é null na mensagem
it('só oferece as ações nos pendentes')
// na linha de um REALIZADO, queryByRole('button', { name: /confirmar realização/i }) é null
it('avisa quando o item já foi resolvido em outra aba')
// 409 -> Alerta "Este exame ou consulta já foi resolvido. Recarregue a página."
```

- [ ] **Step 2: Rodar e verificar que falham**

Run: `npx vitest run src/pages/GestantePage.test.tsx`
Expected: FAIL

- [ ] **Step 3: Implementar as ações**

Duas colunas de botão só no grupo pendente: `Button` primário "Confirmar realização" e secundário "Não compareceu". `GestantePage` guarda a lista em estado e troca o item resolvido pela resposta do servidor; `resolvendo` desabilita os dois botões da linha enquanto o `PUT` está em voo. Mensagem de sucesso com `accessibilityLiveRegion` equivalente (`role="status"`).

- [ ] **Step 4: Rodar testes, lint e typecheck**

Run: `npm test && npm run lint && npm run typecheck`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/pages/GestantePage.tsx src/components/agendamento src/pages/GestantePage.test.tsx
git commit -m "feat: confirmar realização e registrar não comparecimento na tela da gestante"
```

---

### Task 11: Mobile — lista somente leitura

**Files:**
- Create: `src/components/agendamento.tsx`
- Modify: `src/app/(abas)/inicio.tsx`, `src/components/tela.tsx`

**Interfaces:**
- Consumes: `GET /gestantes/agendamentos` (Task 7), `get` de `@/lib/api`, tokens de `@/constants/theme`.
- Produces: `CartaoAgendamento({ agendamento }: { agendamento: Agendamento })`.

- [ ] **Step 1: Acrescentar a prop `alinharTopo?: boolean` a `Tela`**

Quando verdadeira, `contentContainerStyle` deixa de usar `justifyContent: 'center'`. Nenhuma tela existente passa a prop, então o visual delas não muda.

- [ ] **Step 2: Implementar `CartaoAgendamento`**

`View` com borda `Cores.borda`, raio `Raio` e padding `Espaco.quatro`; nome do tipo em `Fontes.seminegrito`, data em `Fontes.regular`/`Cores.textoSecundario`, e o selo de status com os mesmos três rótulos do web (`Pendente`, `Realizado`, `Não compareceu`). Sem `Pressable`: a tela é só leitura.

- [ ] **Step 3: Reescrever `inicio.tsx`**

Mantém a chamada a `lembrarSessao()` que já existe. Busca a lista no padrão de `perfil.tsx` (`useEffect` + `get` + `catch` que preenche `erro`), e renderiza: `Alerta` no erro; "Carregando…" enquanto não chegou; "Você ainda não tem exames ou consultas cadastrados. Seu médico vai cadastrá-los nas suas consultas de pré-natal." quando a lista vem vazia; senão os cartões, já na ordem que o backend devolve. `Tela` com `titulo="Meus exames e consultas"`, `alinharTopo` e `edges={['top', 'left', 'right']}`.

- [ ] **Step 4: Lint e typecheck**

Run: `npx expo lint && npx tsc --noEmit`
Expected: PASS nos dois.

- [ ] **Step 5: Conferir no aparelho**

Com o backend no ar e uma gestante com agendamentos, abrir o app pelo Expo Go e checar os três estados (lista, vazio, erro com o backend parado). Não há runner de teste no mobile — esta é a verificação possível.

- [ ] **Step 6: Commit**

```bash
git add src/app/\(abas\)/inicio.tsx src/components/agendamento.tsx src/components/tela.tsx
git commit -m "feat: lista de exames e consultas no app da gestante"
```

---

### Task 12: Atualizar os diagramas e as decisões

**Files:**
- Modify: `docs/README.md`, `docs/domain/dominio/dominio.puml`

- [ ] **Step 1: Atualizar `dominio.puml`**

`tipo_atendimento`, `agendamento` e `transacao_pontos` deixam de ter o estereótipo `<<previsto>>`; `agendamento.status` passa a `AGENDADO | REALIZADO | NAO_COMPARECEU`; `transacao_pontos` perde `cupom_resgatado_id` (volta com a tabela). A legenda passa a citar as migrações V1 a V11.

- [ ] **Step 2: Atualizar a seção "Decisões" do `docs/README.md`**

Trocar "Status do agendamento: `AGENDADO`, que passa a `REALIZADO` ou `CANCELADO`" pelo status novo, com uma linha dizendo por quê (**D1**); registrar que `log_auditoria` continua pendente (**D3**) e que os tipos de atendimento vivem na migração, não no seed, porque `db/seed` é só de `dev` (**D8**).

- [ ] **Step 3: Regerar as imagens**

Run: `java -jar plantuml.jar -tpng docs/domain/dominio/dominio.puml`
Expected: `dominio.png` regravado, sem erro do PlantUML.

O `plantuml.jar` **não está versionado no repositório** — `docs/README.md` cita a versão 1.2024.7, que precisa ser baixada à parte. Sem o jar, commitar só o `.puml` e dizer no commit que a imagem está pendente; o modelo lógico usa o layout `smetana`, então não é preciso Graphviz.

- [ ] **Step 4: Commit**

```bash
git add docs/README.md docs/domain
git commit -m "docs: agendamentos no modelo lógico e nas decisões"
```

## 7. Estratégia de testes

Segue o que cada repositório já usa; nada novo entra.

**Backend** — JUnit 5 + Mockito puro, sem contexto Spring, no padrão de `CupomServiceTest`: repositórios mockados, `ArgumentCaptor` para conferir o que foi gravado, `verify(..., never())` para provar que nada foi salvo nos caminhos de erro. Validação de DTO em teste próprio com o `Validator` do Jakarta e `@ParameterizedTest @CsvSource`, no padrão de `PrimeiroAcessoRequestTest`. As regras de `@PreAuthorize` **não** são testadas com `spring-security-test` porque nenhum controller do projeto tem teste hoje; a cobertura delas vem da conferência manual da Task 7 Step 5. Rodar tudo: `mvn test`; um caso só: `mvn test -Dtest=AgendamentoServiceTest#confirmaCreditaPontosEDevolveOSaldo`.

**Web** — Vitest + Testing Library, arquivo `*.test.tsx` ao lado do componente, `fetch` substituído por `vi.spyOn(globalThis, 'fetch')` e `MemoryRouter` com as rotas que a navegação usa, exatamente como `FormCupom.test.tsx`. Consultas por papel e rótulo acessível (`getByLabelText`, `getByRole`), nunca por classe CSS. Rodar: `npm test`; um arquivo: `npx vitest run src/components/agendamento/FormAgendamento.test.tsx`.

**Mobile** — não há runner configurado e o plano não adiciona um (seria dependência nova). A verificação é `npx expo lint`, `npx tsc --noEmit` e o roteiro manual da Task 11 Step 5. **Se você quiser teste automatizado no app, isso é uma decisão à parte** (jest-expo + @testing-library/react-native) e vira plano próprio.

**Cobertura dos casos de borda do enunciado:**

| Caso de borda | Onde |
|---|---|
| Gestante sem nenhum agendamento | Task 4 Step 1 (serviço), Task 8 Step 1 (web, estado vazio), Task 11 Step 3 (mobile) |
| Filtros de listagem sem resultados | Já coberto por `Listagem` (bloco "Nenhum resultado para a busca e os filtros escolhidos" + botão de limpar), sem código novo |
| Confirmar item que não está pendente | Task 6 Step 1 (409 nos dois sentidos), Task 10 Step 1 (web mostra o aviso) |
| Tipo inexistente ou inativo | Task 3 Step 2 |
| Data no passado | Task 3 Step 1 (DTO) e Task 9 Step 1 (web) |
| Gestante acessando dados de outra | Task 7 Step 1 (id vem só do token) |
| Perfil sem `MEDICO` nos endpoints de gestão | `@PreAuthorize` em cada rota (Tasks 2, 3, 4, 6) + conferência da Task 7 Step 5 |

## 8. Riscos e pendências

**Riscos**

1. **A corrida de confirmação depende do UNIQUE, não de lock.** Duas requisições simultâneas podem ambas ler `AGENDADO`; a segunda só quebra ao gravar a transação de pontos, e o `GlobalExceptionHandler` devolve `409` com a mensagem genérica "Dados já cadastrados", não a mensagem específica. Aceitável (o saldo nunca duplica), mas a mensagem ao médico fica ruim nesse caso raro. Alternativa, se incomodar: `@Lock(PESSIMISTIC_WRITE)` na busca do agendamento.
2. **`saldo_pontos` passa a ser um total derivado mantido à mão.** Se um dia houver débito (resgate de cupom) fora do `PontuacaoService`, saldo e extrato divergem. Mitigação adotada: todo crédito passa por `PontuacaoService`, e `transacao_pontos` guarda o histórico completo para reconciliar.
3. **Sem `log_auditoria` (D3), uma confirmação indevida não deixa rastro além de `confirmado_por` e `data_confirmacao`.** Para o pré-natal isso provavelmente basta por ora, mas é uma lacuna consciente.
4. **`GET /gestantes/{id}/agendamentos` sem paginação (D7)** fica pesado se uma gestante acumular centenas de itens. Com até ~40 por gestação não é problema; se virar, entra `PaginaResponse` e o agrupamento muda de lugar.

**Pendências que precisam da sua confirmação antes da Tarefa 1**

- **D1** — `NAO_COMPARECEU` no lugar de `CANCELADO`, ou os dois?
- **D2** — Incluir o crédito de pontos nesta entrega?
- **D6** — Médico com `verTodasGestantes` pode cadastrar e confirmar para gestante de outro médico, ou a permissão vale só para leitura?
- **Seção 3** — Os 21 tipos e os `pontos_base` propostos estão bons?
- **D7** — Lista por gestante sem paginação, ok?

**Pendências menores, decididas no plano mas reversíveis:** D4 (rota `nao-compareceu`), D5 (rota aninhada em vez de `GET /agendamentos`), D8 (tipos na migração), e a prop `alinharTopo` em `Tela` no mobile.
