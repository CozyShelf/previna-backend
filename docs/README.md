# Diagramas

Cada diagrama tem a sua pasta, com o fonte (`.puml`, PlantUML) e a imagem (`.png`) gerada a partir dele, com o mesmo nome da pasta:

- `domain/`: modelo lógico do banco de dados
- `use-cases/`: casos de uso por perfil (admin, empresa, gestante, médico)
- `sequence-diagrams/`: diagramas de sequência dos fluxos
- `implementation-diagrams/`: fluxos internos da API (atividades por camada)

Para gerar a imagem de novo depois de editar o fonte: `java -jar plantuml.jar -tpng <pasta>/<nome>.puml` (PlantUML 1.2024.7). O modelo lógico e os casos de uso usam o layout `smetana`, que não precisa do Graphviz.

No modelo lógico, as tabelas em cinza ainda não têm migração (resgate de cupom e auditoria); as demais seguem as migrações V1 a V14.

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
- **Crédito único por agendamento:** a resolução trava a linha do agendamento, então a segunda confirmação espera,
  encontra o desfecho já registrado e recebe `409`. `transacao_pontos.agendamento_id` é `UNIQUE` como última barreira.
- **Saldo de pontos:** `gestante.saldo_pontos` é atualizado junto com o extrato, com a gestante travada (a mesma trava
  do resgate de cupom). Sem ela, créditos simultâneos de agendamentos diferentes leriam o mesmo saldo e perderiam pontos.
- **Auditoria pendente:** os diagramas de cadastro e de confirmação marcam `log_auditoria` como previsto: ainda não
  tem migração nem é gravada. Por ora o rastro de quem resolveu está em `agendamento.confirmado_por` e
  `agendamento.data_confirmacao`.
- **Regra de negócio violada:** `409 Conflict` (`ConflitoException`); dados inválidos, `400 Bad Request`. A API não usa 422.
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
- **Promoção e cupom:** a promoção vale para todos os produtos da empresa em uma categoria (fraldas, roupas ou remédios); o cupom vale para um produto específico, sem quantidade, e a empresa o ativa ou desativa.
- **Validação de cupom:** cupom de outra empresa tem a mesma resposta do código inexistente (404); o cupom já resgatado continua válido se a empresa desativar o cupom depois.
