# Diagramas

Cada diagrama tem a sua pasta, com o fonte (`.puml`, PlantUML) e a imagem (`.png`) gerada a partir dele, com o mesmo nome da pasta:

- `domain/`: modelo lógico do banco de dados
- `use-cases/`: casos de uso por perfil (admin, empresa, gestante, médico)
- `sequence-diagrams/`: diagramas de sequência dos fluxos
- `implementation-diagrams/`: fluxos internos da API (atividades por camada)

Para gerar a imagem de novo depois de editar o fonte: `java -jar plantuml.jar -tpng <pasta>/<nome>.puml` (PlantUML 1.2024.7). O modelo lógico e os casos de uso usam o layout `smetana`, que não precisa do Graphviz.

O modelo lógico segue as migrações V1 a V24.

## Decisões

- **Status do agendamento:** `AGENDADO`, que passa a `REALIZADO` ou `NAO_COMPARECEU`. O terceiro status era
  `CANCELADO` nos diagramas, mas cancelar (desmarcar antes) e não comparecer (faltar) são fatos diferentes, e só
  o segundo tem tela: o médico registra o não comparecimento. Não existe cancelamento de agendamento por ora.
- **Catálogo de tipos de atendimento:** os 21 tipos iniciais vão na migração `V11`, e não em `db/seed`, porque
  `db/seed` só é carregado no profile `dev` e o catálogo precisa existir em produção. Na `V12`, o médico passa a
  cadastrar os próprios tipos: `criado_por` nulo é o tipo padrão, visível para todos.
- **Exclusão do tipo de atendimento:** lógica (`V14`). O médico não escolhe mais ativo/inativo no formulário:
  excluir grava `tipo_atendimento.ativo = FALSE`, e a linha fica porque `agendamento` tem chave estrangeira
  para ela e o histórico da gestante perderia o nome do exame. O tipo excluído some da gestão e da lista de
  escolha, e responde `404` como um inexistente. O nome volta a ficar livre: a UNIQUE `(nome, criado_por)`
  virou índice único parcial, só entre os tipos com `ativo`.
- **Agendamento pendente único:** a gestante tem no máximo um `AGENDADO` por tipo e data prevista (índice único
  parcial `agendamento_pendente_unico`, `V21`). Sem ele, duas requisições iguais ao mesmo tempo (duplo clique, duas
  abas, dois médicos) gravavam dois pendentes idênticos, e cada confirmação creditava os pontos do mesmo exame. O
  segundo cadastro, ou o item repetido na própria lista, recebe `409` "Essa gestante já tem esse exame ou consulta
  pendente nessa data", e nada do cadastro é gravado. Não há consulta antes de gravar: as duas requisições passariam
  juntas por ela; o índice decide. O mesmo exame em outra data continua permitido, e na mesma data também, depois que
  o anterior saiu de `AGENDADO`. Nenhuma outra escrita volta um agendamento a `AGENDADO` ou muda gestante, tipo ou data.
- **Crédito único por agendamento:** a resolução trava a linha do agendamento, então a segunda confirmação espera,
  encontra o desfecho já registrado e recebe `409`. `transacao_pontos.agendamento_id` é `UNIQUE` como última barreira.
- **Saldo de pontos:** `gestante.saldo_pontos` é atualizado junto com o extrato, com a gestante travada (a mesma trava
  do resgate de cupom). Sem ela, créditos simultâneos de agendamentos diferentes leriam o mesmo saldo e perderiam pontos.
  O saldo só muda por `UPDATE` atômico (`saldo_pontos = saldo_pontos ± pontos`; o débito só com saldo): a entidade
  `Gestante` não grava a coluna, senão editar o perfil gravaria o saldo lido no começo por cima de um crédito comitado
  no meio. O CHECK `saldo_pontos >= 0` (`V19`) é a última barreira.
- **Duplo toque no resgate:** o app manda o header `Idempotency-Key` (UUID novo a cada confirmação). A mesma chave da
  mesma gestante devolve `200` com o resgate já feito e o saldo atual, sem novo débito; chave nova, `201`; chave que
  não é UUID, `400`. Sem o header (versões antigas do app), cada chamada é um resgate — resgatar o mesmo cupom mais de
  uma vez é permitido. A busca pela chave é feita com a gestante travada; o UNIQUE `(gestante_id, chave_idempotencia)`
  (`V20`) é a última barreira.
- **Promoção resgatável:** a promoção não custa pontos, e cada gestante resgata cada promoção uma vez só
  (`POST /promocoes/{id}/resgatar`, com o mesmo header `Idempotency-Key` do cupom). O resgate gera um código único,
  com os status do cupom (`ATIVO`, `UTILIZADO`, `EXPIRADO`), válido até a `data_fim` da promoção; não há débito nem
  `transacao_pontos`, então a promoção não aparece no extrato. Cupom e promoção do mesmo produto são resgates
  independentes, e o cupom continua podendo ser resgatado N vezes enquanto houver pontos. Respostas: `201` resgate
  novo; `200` mesma chave da mesma gestante; `400` chave que não é UUID; `404` promoção inexistente; `409`
  "Promoção indisponível" (desativada, antes do início ou depois do fim, o mesmo critério do catálogo) e `409`
  "Promoção já resgatada" (a gestante já a resgatou com outra chave ou sem chave). Quem decide o "uma vez" é o índice
  único `cupom_resgatado_promocao_uma_vez (gestante_id, promocao_id)` (`V22`), e não uma consulta antes: o resgate grava
  com flush e traduz a violação pelo nome do índice. No detalhe do produto (`GET /ofertas/produtos/{id}`), cada
  promoção traz `resgatada`, montado com uma consulta só.
- **Tabela única de resgates:** cupom e promoção resgatados ficam em `cupom_resgatado`, com a coluna `tipo` (`CUPOM` ou
  `PROMOCAO`, `V22`). Os dois têm código, status, validade, empresa e desconto, e a empresa vai validar os dois códigos
  do mesmo jeito; duas tabelas duplicariam a geração do código, a validação e a lista "meus resgates". Cada tipo guarda a
  própria cópia: o cupom, `cupom_id`, produto e pontos; a promoção, `promocao_id`, título e categoria. O CHECK
  `cupom_resgatado_tipo_coerente` amarra quais colunas cada tipo preenche, e a resposta do resgate traz o `tipo`. O
  índice único só vale para a promoção: no cupom `promocao_id` é nulo, e nulos não colidem.
- **Meus cupons (Acessar Cupons Resgatados):** `GET /gestantes/cupons` lista os cupons e as promoções resgatados pela
  gestante do token (`PaginaResponse<ResgateResponse>`), e `GET /gestantes/cupons/{id}` traz um resgate; só o perfil
  `GESTANTE` (o token do primeiro acesso recebe `403`). Filtros opcionais, com os nomes dos de `/ofertas`: `busca`
  (trecho do nome da empresa, do produto do cupom ou do título da promoção, sem diferenciar maiúsculas nem acentos,
  como a busca das ofertas), `categoria` (`FRALDAS`, `ROUPAS`, `REMEDIOS`), `tipo` (`CUPOM`, `PROMOCAO`), `status`
  (`ATIVO`, `UTILIZADO`, `EXPIRADO`), `pagina` e `tamanho` (padrão 20, até 100). A categoria da promoção é a copiada
  no resgate; a do cupom é a atual do produto, por join com `produto` (a cópia do cupom não guarda categoria, e o
  produto sem categoria não entra em nenhum filtro de categoria). **Ordem:** `ATIVO` primeiro; `UTILIZADO` e
  `EXPIRADO` juntos depois; em cada grupo, do resgate mais recente, com o id como desempate (`ORDER BY CASE`, no
  banco, porque a lista é paginada). Uma consulta para a página (resgate e categoria do produto juntos) e uma para o
  total. Na resposta, `titulo` é a frase do desconto no cupom ("10% de desconto em Fralda RN") e o título na promoção;
  na promoção `produto` é nulo e `pontosGastos` é 0; `dataUso` é nula até a validação. Os nulos vão no JSON. Resgate
  de outra gestante responde `404` "Resgate não encontrado", igual ao inexistente, para não revelar ids.
- **Busca literal:** em todas as buscas por texto (listagens do portal, `/ofertas` e `/gestantes/cupons`), `%`, `_` e
  `\` digitados são procurados como texto: `Filtros.padraoContem` os escapa e o `LIKE` usa `ESCAPE '\'`. Buscar
  "50%" acha "Fralda 50% off", e não tudo que tem "50"; "_" não casa qualquer caractere.
- **Expiração na leitura:** não há tarefa agendada; antes de listar, os resgates `ATIVO` vencidos
  (`data_validade < hoje`; a validade vale até o fim do dia) da gestante viram `EXPIRADO`, e antes do detalhe, só o
  resgate pedido. É um `UPDATE` só, condicionado a `status = 'ATIVO'`, sem ler as linhas antes para decidir: a
  validação do código na loja (também um `UPDATE` condicionado a `ATIVO`) nunca é sobrescrita, e o `UTILIZADO`
  vencido continua `UTILIZADO`. Duas listagens simultâneas da mesma gestante não se atrapalham: a segunda espera a
  trava de linha da primeira, reavalia o `WHERE`, que já não casa, e lê o que a primeira gravou. Por isso a transação
  da leitura não é `readOnly`.
- **Validar Cupom (código na loja):** `POST /empresas/cupons/validar`, só o perfil `EMPRESA_PARCEIRA`, corpo
  `{"codigo": "AB12CD34"}`, vale para cupom e promoção. O código é aceito com espaços nas pontas e em minúsculas (o
  gerado é sempre maiúsculo); vazio ou em branco, `400`. A troca para `UTILIZADO` é **um `UPDATE` só**:
  `SET status = 'UTILIZADO', data_uso = :agora WHERE codigo = :codigo AND empresa_id = :empresa AND status = 'ATIVO'
  AND data_validade >= :hoje`, sem ler a linha antes para decidir, sem trava em memória. Duas validações simultâneas
  do mesmo código: a segunda espera a trava de linha da primeira, reavalia o `WHERE`, que já não casa, e recebe
  `409`. Só quando o `UPDATE` não mudou nada o resgate é relido, para dizer por quê: não achou (inexistente ou de
  outra empresa: a mesma consulta, filtrada pela empresa do token) `404` `RESGATE_NAO_ENCONTRADO`; `UTILIZADO`,
  `409` `RESGATE_JA_UTILIZADO` com a `dataUso` do primeiro uso no corpo; `EXPIRADO`, ou `ATIVO` vencido (que antes
  de responder vira `EXPIRADO`, com o `UPDATE` também condicionado a `ATIVO`; o `409` não desfaz a transação,
  `noRollbackFor`, senão o `EXPIRADO` se perdia), `409` `RESGATE_EXPIRADO`. O `UTILIZADO`
  vencido continua "já utilizado". `:agora` e `:hoje` vêm da aplicação (a mesma hora da `data_resgate` e da expiração
  na leitura), e não do `now()` do banco. Vale a cópia do resgate (D2): cupom ou promoção desativados ou editados
  depois não invalidam o código, e a resposta traz o desconto e a validade copiados. O `200`
  (`ValidacaoCupomResponse`) traz `id`, `tipo`, `codigo`, `titulo` (só promoção), `produtoNome` (só cupom),
  `categoria` e `desconto` (como em "meus cupons"), `validade`, `dataResgate`, `dataUso` e `gestanteNome` — da
  gestante, só o nome. Os nulos vão no JSON. Depois da validação, o app da gestante vê o resgate `UTILIZADO` com a
  mesma `dataUso`.
- **Uma promoção ativa por categoria em cada período (D12):** a empresa não tem duas promoções ativas da mesma
  categoria com períodos que se cruzam (os dois dias inclusos: uma que termina no dia 10 e outra que começa no 11 não
  se cruzam). A exclusão `promocao_sem_sobreposicao` (`V23`, GiST com `btree_gist`, só entre as `ativo`) barra no
  banco; cadastro, edição e ativação respondem `409` "Já existe promoção ativa dessa categoria nesse período", e
  desativar nunca conflita. Antes de gravar, a empresa é travada (`SELECT ... FOR UPDATE`): sem essa fila, dois
  cadastros sobrepostos simultâneos entram os dois no índice, cada um espera o outro na conferência da exclusão e o
  PostgreSQL derruba um com `deadlock detected`, que viraria `500`. A regra continua sendo a exclusão; a fila só faz o
  perdedor receber a violação dela.
- **Log de auditoria (RF18):** a tabela `log_auditoria` (`V24`) registra quem fez o quê nas confirmações de atendimento
  e nas movimentações de pontos. Cada linha tem `data_hora`, `acao`, `usuario_id` (quem fez), `gestante_id` (sempre
  preenchida: a do agendamento ou do resgate) e, conforme a ação, `agendamento_id`, `transacao_pontos_id` (o crédito
  ou o débito) e `cupom_resgatado_id`; `pontos` é uma cópia dos pontos da transação, para ler o log sem juntar
  tabelas. Ações:

  | `acao` | Quando | `usuario_id` | Ligada a |
  |---|---|---|---|
  | `AGENDAMENTO_CRIADO` | `POST /agendamentos`, uma linha por item do cadastro | médico | agendamento |
  | `AGENDAMENTO_REALIZADO` | `PUT /agendamentos/{id}/confirmar` | médico | agendamento, crédito e `pontos` |
  | `AGENDAMENTO_NAO_COMPARECEU` | `PUT /agendamentos/{id}/nao-compareceu` | médico | agendamento |
  | `CUPOM_RESGATADO` | `POST /cupons/{id}/resgatar`, só o resgate novo (`201`) | gestante | cupom resgatado, débito e `pontos` |
  | `PROMOCAO_RESGATADA` | `POST /promocoes/{id}/resgatar`, só o resgate novo (`201`) | gestante | cupom resgatado (`pontos` nulo) |
  | `RESGATE_VALIDADO` | `POST /empresas/cupons/validar`, só no `200` | empresa | cupom resgatado |

  Garantias:
  - **Mesma transação da operação:** os serviços chamam a `Auditoria` explicitamente, depois de gravar a operação (sem
    AOP nem listeners). A `Auditoria` é `@Transactional(propagation = MANDATORY)`: sem a transação de quem chama, a
    chamada falha em vez de gravar sozinha. Se a gravação do log falhar, a operação inteira é desfeita (sem desfecho,
    crédito, débito, resgate ou validação), e um log nunca fica gravado sem a operação.
  - **Só os caminhos que dão certo gravam:** os `400`, `404` e `409` não geram linha, nem a repetição do resgate com a
    mesma `Idempotency-Key` (`200`), que devolve o resgate já registrado. Na validação, o log é o último passo do
    caminho do `200`: o `409` `RESGATE_EXPIRADO` comita o `EXPIRADO` (`noRollbackFor`), mas não é uma validação e não
    grava log; uma falha ao gravar o log não é `ConflitoException`, então o `noRollbackFor` não a cobre e a validação
    é desfeita.
  - **Uma linha por operação, também com concorrência:** o log vai depois da trava ou do `UPDATE` condicionado que
    decide a operação. Duas confirmações simultâneas do mesmo agendamento gravam um crédito e uma linha
    `AGENDAMENTO_REALIZADO` (a segunda espera a trava do agendamento e recebe `409`); duas validações simultâneas do
    mesmo código gravam uma linha `RESGATE_VALIDADO`.
  - **Hora da operação:** `data_hora` é a mesma hora da aplicação gravada na operação (`data_confirmacao` no desfecho,
    `data_resgate` no resgate, `data_uso` na validação, a hora do cadastro no agendamento criado), e não o `now()` do
    banco.
  - **Só inserção:** o gatilho `log_auditoria_somente_insercao` barra `UPDATE` e `DELETE` no banco; a entidade
    `LogAuditoria` é `@Immutable`, e o `LogAuditoriaRepository` só expõe `save`, `saveAll` e a leitura do painel do
    admin (`recentes`). Os testes de integração
    não limpam o banco (os dados são únicos por teste), então o gatilho não atrapalha.

  O log começa vazio (sem preencher o histórico de antes da `V24`); a única leitura é a `atividadeRecente` do painel do
  admin (abaixo). O rastro de quem resolveu cada agendamento continua também em `agendamento.confirmado_por` e
  `agendamento.data_confirmacao`.
- **Painel da tela Início do portal:** três rotas `GET`, uma por perfil, cada uma com o `@PreAuthorize` só do seu
  (os outros perfis e o primeiro acesso recebem `403`; sem token, `401`), com o usuário vindo do token. São só leitura
  (`@Transactional(readOnly = true)`) e não gravam log. Cada número é um `count` no banco e cada lista curta é a página 0
  de tamanho 5 de uma consulta ordenada: `{ "total": <contagem completa>, "itens": [<no máximo 5>] }`, para a tela
  mostrar "e mais N". "Hoje" e "agora" são `LocalDate.now()` e `LocalDateTime.now()` da aplicação (fuso de Brasília).
  Datas saem `"aaaa-mm-dd"`; instantes, em ISO UTC, como os outros DTOs (`ResgateResponse.instante`). Diagrama em
  `sequence-diagrams/painel-inicio`.

  `GET /painel/medico` (`MEDICO`), no alcance do médico, o mesmo da agenda do dia e da listagem de gestantes: as
  gestantes que ele acompanha ou, com `verTodasGestantes`, todas.

  | Campo | O que conta | Ordem dos itens |
  |---|---|---|
  | `gestantesAcompanhadas` | gestantes no alcance | — |
  | `atrasados` | agendamentos `AGENDADO` com `dataPrevista` antes de hoje | mais antigo primeiro (o id desempata) |
  | `proximos7Dias` | `AGENDADO` de amanhã até hoje + 7, inclusive (hoje está na agenda do dia) | mais próximo primeiro |
  | `semAgendamentoPendente` | gestantes no alcance sem nenhum `AGENDADO` | nome (o id desempata) |

  Não existe gestante inativa no modelo (não há exclusão nem desativação), então "gestantes ativas no alcance" são
  todas as do alcance, com ou sem o primeiro acesso feito.

  `GET /painel/empresa` (`EMPRESA_PARCEIRA`), tudo só da empresa do token:

  | Campo | O que conta | Ordem dos itens |
  |---|---|---|
  | `cuponsAtivos` | cupons ativos com `dataValidade >= hoje` (o cupom válido do `/ofertas`) | — |
  | `promocoesVigentes` | promoções ativas com hoje entre `dataInicio` e `dataFim`, os dois inclusive | — |
  | `resgatesAguardandoUso` | resgates (cupom e promoção) `ATIVO` com a validade copiada `>= hoje`; o vencido que ainda está `ATIVO` no banco (a expiração é na leitura) não conta | — |
  | `validacoes30Dias` | resgates `UTILIZADO` com `dataUso` nos últimos 30 dias, contados do instante atual | — |
  | `cuponsVencendo7Dias` | cupons válidos com `dataValidade` de hoje até hoje + 7 | vence primeiro (o id desempata) |
  | `produtosSemOferta` | produtos sem cupom válido e sem promoção vigente da categoria deles: o contrário do critério do `/ofertas` (`Ofertas.semOferta`) | nome (o id desempata) |
  | `ultimasValidacoes` | lista simples, até 5 resgates `UTILIZADO`, da cópia do resgate (`titulo` só na promoção, `produtoNome` só no cupom; o outro vai `null`), sem dado da gestante | `dataUso` mais recente primeiro |

  Os critérios são os mesmos constantes das consultas do catálogo e da validação (`CupomRepository.VALIDO`,
  `PromocaoRepository.VIGENTE`, `CupomResgatadoRepository.AGUARDANDO_USO`), para não divergirem.

  `GET /painel/admin` (`ADMIN`):

  | Campo | O que conta |
  |---|---|
  | `medicos` | médicos cadastrados |
  | `empresas` / `gestantes` | `total` cadastradas e `primeiroAcessoPendente` (conta ainda com `PRIMEIRO_ACESSO_PENDENTE`) |
  | `conteudos` | conteúdos educativos `publicados` e `rascunhos` |
  | `empresasPendentes` | empresas com `PRIMEIRO_ACESSO_PENDENTE`, da pré-cadastrada há mais tempo (`preCadastradaEm` = `usuario.criado_em`) |
  | `atividadeRecente` | lista simples: as 10 últimas linhas do `log_auditoria`, da mais recente, com `dataHora`, `acao`, `usuarioNome`, `perfil` e `pontos` |

  A `atividadeRecente` segue a ordem de inserção (o id, pela chave primária), e não `data_hora`, que não tem índice
  próprio; as duas coincidem, porque `data_hora` é a hora da operação gravada na mesma transação. Nenhum dado da
  gestante sai para o admin (ele não tem acesso a gestantes, e o atendimento é dado de saúde): nem `gestante_id`, nem o
  nome dela quando quem fez foi ela (`CUPOM_RESGATADO` e `PROMOCAO_RESGATADA` vêm com `perfil` `GESTANTE` e
  `usuarioNome` `null`, decidido na própria consulta). `pontos` é a cópia do log, sempre positiva (crédito no
  `AGENDAMENTO_REALIZADO`, débito no `CUPOM_RESGATADO`); a tela põe o sinal pela ação. Nas outras ações, `null`.
- **Regra de negócio violada:** `409 Conflict` (`ConflitoException`); dados inválidos, `400 Bad Request`. A API não usa 422.
- **Código estável nos erros:** os `409` (e os `404`) que os fronts tratam de forma própria levam, ao lado de
  `message`, o campo `codigo` com o nome de uma constante de `CodigoErro`, por exemplo
  `{"message": "Promoção já resgatada", "codigo": "PROMOCAO_JA_RESGATADA"}`. Os demais erros não trazem o campo (nem
  `"codigo": null`): o corpo continua `{"message": "..."}`. O código já utilizado leva também `dataUso` (ISO-8601,
  como as datas de "meus cupons"):
  `{"message": "Código já utilizado", "codigo": "RESGATE_JA_UTILIZADO", "dataUso": "2026-11-19T14:05:00Z"}`; nos
  outros erros o campo não aparece. **Os fronts decidem pelo `codigo`, nunca pelo texto de
  `message`**, que é só para exibir e pode mudar; renomear uma constante, por outro lado, quebra o contrato. Códigos:

  | `codigo` | Quando | `message` |
  |---|---|---|
  | `PROMOCAO_JA_RESGATADA` | resgate de promoção que a gestante já resgatou | "Promoção já resgatada" |
  | `PROMOCAO_INDISPONIVEL` | resgate de promoção desativada ou fora do período | "Promoção indisponível" |
  | `PROMOCAO_SOBREPOSTA` | cadastro, edição ou ativação de promoção que cruza outra ativa da mesma categoria | "Já existe promoção ativa dessa categoria nesse período" |
  | `CUPOM_INDISPONIVEL` | resgate de cupom desativado ou fora da validade | "Cupom indisponível" |
  | `PONTOS_INSUFICIENTES` | resgate de cupom sem saldo (na conferência ou no débito) | "Pontos insuficientes" |
  | `AGENDAMENTO_PENDENTE_REPETIDO` | agendamento igual a um pendente da gestante na mesma data | "Essa gestante já tem esse exame ou consulta pendente nessa data" |
  | `RESGATE_NAO_ENCONTRADO` (`404`) | validação de código inexistente ou de outra empresa (a mesma resposta) | "Código não encontrado" |
  | `RESGATE_JA_UTILIZADO` | validação de código já validado; o corpo traz `dataUso` | "Código já utilizado" |
  | `RESGATE_EXPIRADO` | validação de código vencido (gravado como `EXPIRADO` antes de responder) | "Código expirado" |
- **Autorização antes da validação:** o `@PreAuthorize` de cada rota é conferido antes de o corpo ser lido e validado
  (`AutorizacaoAntesDaValidacao`): papel errado recebe `403` mesmo com corpo inválido, sem ver as regras de validação.
- **Primeiro acesso:** o token da senha temporária tem o papel `PRIMEIRO_ACESSO_<perfil>` (`PRIMEIRO_ACESSO_GESTANTE`,
  `PRIMEIRO_ACESSO_EMPRESA_PARCEIRA`): cada tipo de conta só conclui o próprio primeiro acesso.
  A conclusão lê a conta com `SELECT ... FOR UPDATE`: a segunda conclusão simultânea espera a primeira e recebe `409`
  "Primeiro acesso já concluído", em vez de sobrescrever as credenciais.
- **Edições concorrentes:** quem grava cupom, promoção ou conteúdo (editar, ativar/desativar, publicar/despublicar,
  excluir) lê o registro com `SELECT ... FOR UPDATE`. Sem `@Version`, o Hibernate grava todas as colunas, e a edição
  gravaria o `ativo`/`publicado` que leu por cima de uma desativação comitada no meio. As leituras não travam.
- **Usuário do token:** as rotas da gestante e da empresa não levam o id do usuário no caminho (`/gestantes/pontuacao`, `/empresas/produtos`); recurso de outro usuário responde como inexistente (404).
- **Quem publica conteúdo educativo:** só o admin (usuário interno do Previna), registrado em
  `conteudo_informativo.autor_id`. O médico não publica.
- **Rota dos conteúdos:** `/conteudos` serve aos dois perfis, como nos diagramas de sequência. O admin vê também
  os rascunhos, com busca e filtros; a gestante só os publicados, e um rascunho responde a ela como inexistente (404).
- **Situação do conteúdo:** o booleano `publicado` (rascunho/publicado) com `publicado_em`, no lugar de um enum,
  como em `cupom.ativo`. Despublicar limpa a data e tira o conteúdo do app sem apagá-lo.
- **Obrigatório por tipo de conteúdo:** `TEXTO` exige `corpo`; `FOTO`, `VIDEO` e `LINK` exigem mídia. A regra fica no
  `ConteudoService` (400), e não num CHECK: a foto enviada só ganha `midia_url` depois do INSERT, porque o id entra na URL.
- **Mídia do conteúdo:** foto enviada vai para `imagem_conteudo` (BYTEA), como `imagem_produto`, com `midia_url`
  apontando para `/conteudos/{id}/imagem`; vídeo e link são só endereço. Só o tipo `FOTO` aceita arquivo, e o
  `Imagem` recusa o que não for PNG, JPEG ou WebP pelos primeiros bytes: arquivo em `VIDEO`, `LINK` ou `TEXTO`
  responde `400`. Não há upload de arquivo de vídeo. O endereço precisa começar com `http://` ou `https://`, senão
  um `javascript:` ou um caminho `/...` chegaria ao app como link da própria API. No app, o player toca arquivo de
  vídeo (`.mp4`, `.m3u8`…); página de vídeo abre no navegador.
- **Ordem dos conteúdos na gestante:** aleatória, semeada pelo app a cada carregamento
  (`order by md5(id || semente)`). `order by random()` sorteia de novo a cada página e faria um conteúdo aparecer em
  duas e outro em nenhuma.
- **Consulta pendente:** é a de status `AGENDADO`, mesmo com a data prevista já passada — o que define é o médico não
  ter registrado o desfecho. `/gestantes/agendamentos?pendentes=true|false` separa a tela inicial do histórico do app.
- **Cálculo dos pontos:** `arredondar(tipo_atendimento.pontos_base × dados_socioeconomicos.fator_ponderacao)`, com o fator gravado em `transacao_pontos.fator_aplicado`. O fator é neutro (1,00) até a regra de ponderação ser definida.
- **Promoção e cupom:** a promoção vale para todos os produtos da empresa em uma categoria (fraldas, roupas ou remédios), do início ao fim (os dois dias inclusos), e se resgata sem pontos, uma vez por gestante; o cupom vale para um produto específico, sem quantidade, custa pontos e pode ser resgatado várias vezes. A empresa ativa ou desativa os dois.
- **Catálogo de ofertas da gestante:** `/ofertas` lista as empresas, e cada uma os produtos com oferta: cupom válido
  (ativo e dentro da validade) ou promoção vigente da mesma empresa na categoria do produto. Produto sem categoria
  pode ter cupom, mas nunca promoção. Os filtros (busca, categoria, empresa, tipo, "cabe no meu saldo") ficam no
  servidor porque o app pagina com rolagem infinita. Produto sem oferta responde como inexistente (404).
- **Cópia do cupom no resgate:** `cupom_resgatado` guarda produto, empresa, desconto, validade e pontos do momento do
  resgate (`V17`). A resposta do resgate e o extrato vêm dessa cópia: editar o cupom ou renomear o produto depois não
  muda o que a gestante resgatou. `cupom_id` continua, para saber de onde o resgate veio.
- **Validação de cupom:** cupom de outra empresa tem a mesma resposta do código inexistente (404); o cupom já resgatado continua válido se a empresa desativar o cupom depois (ver "Validar Cupom" acima).
