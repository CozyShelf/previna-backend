# Diagramas

Cada diagrama tem a sua pasta, com o fonte (`.puml`, PlantUML) e a imagem (`.png`) gerada a partir dele, com o mesmo nome da pasta:

- `domain/`: modelo lógico do banco de dados
- `use-cases/`: casos de uso por perfil (admin, empresa, gestante, médico)
- `sequence-diagrams/`: diagramas de sequência dos fluxos
- `implementation-diagrams/`: fluxos internos da API (atividades por camada)

Para gerar a imagem de novo depois de editar o fonte: `java -jar plantuml.jar -tpng <pasta>/<nome>.puml` (PlantUML 1.2024.7). O modelo lógico e os casos de uso usam o layout `smetana`, que não precisa do Graphviz.

No modelo lógico, as tabelas em cinza ainda não têm migração (conteúdos e auditoria); as demais seguem as migrações V1 a V13.

## Decisões

- **Status do agendamento:** `AGENDADO`, que passa a `REALIZADO` ou `NAO_COMPARECEU`. O terceiro status era
  `CANCELADO` nos diagramas, mas cancelar (desmarcar antes) e não comparecer (faltar) são fatos diferentes, e só
  o segundo tem tela: o médico registra o não comparecimento. Não existe cancelamento de agendamento por ora.
- **Catálogo de tipos de atendimento:** os 21 tipos iniciais vão na migração `V11`, e não em `db/seed`, porque
  `db/seed` só é carregado no profile `dev` e o catálogo precisa existir em produção. Na `V12`, o médico passa a
  cadastrar os próprios tipos: `criado_por` nulo é o tipo padrão, visível para todos.
- **Crédito único por agendamento:** a resolução trava a linha do agendamento, então a segunda confirmação espera,
  encontra o desfecho já registrado e recebe `409`. `transacao_pontos.agendamento_id` é `UNIQUE` como última barreira.
- **Saldo de pontos:** `gestante.saldo_pontos` é atualizado junto com o extrato, com a gestante travada (a mesma trava
  do resgate de cupom). Sem ela, créditos simultâneos de agendamentos diferentes leriam o mesmo saldo e perderiam pontos.
- **Auditoria pendente:** os diagramas de cadastro e de confirmação marcam `log_auditoria` como previsto: ainda não
  tem migração nem é gravada. Por ora o rastro de quem resolveu está em `agendamento.confirmado_por` e
  `agendamento.data_confirmacao`.
- **Regra de negócio violada:** `409 Conflict` (`ConflitoException`); dados inválidos, `400 Bad Request`. A API não usa 422.
- **Autorização antes da validação:** o `@PreAuthorize` de cada rota é conferido antes de o corpo ser lido e validado
  (`AutorizacaoAntesDaValidacao`): papel errado recebe `403` mesmo com corpo inválido, sem ver as regras de validação.
- **Primeiro acesso:** o token da senha temporária tem o papel `PRIMEIRO_ACESSO_<perfil>` (`PRIMEIRO_ACESSO_GESTANTE`,
  `PRIMEIRO_ACESSO_EMPRESA_PARCEIRA`): cada tipo de conta só conclui o próprio primeiro acesso.
- **Usuário do token:** as rotas da gestante e da empresa não levam o id do usuário no caminho (`/gestantes/pontuacao`, `/empresas/produtos`); recurso de outro usuário responde como inexistente (404).
- **Quem publica conteúdo educativo:** o admin (usuário interno do Previna), registrado em `conteudo_informativo.autor_id`. *A confirmar.*
- **Cálculo dos pontos:** `arredondar(tipo_atendimento.pontos_base × dados_socioeconomicos.fator_ponderacao)`, com o fator gravado em `transacao_pontos.fator_aplicado`. O fator é neutro (1,00) até a regra de ponderação ser definida.
- **Promoção e cupom:** a promoção vale para todos os produtos da empresa em uma categoria (fraldas, roupas ou remédios); o cupom vale para um produto específico, sem quantidade, e a empresa o ativa ou desativa.
- **Validação de cupom:** cupom de outra empresa tem a mesma resposta do código inexistente (404); o cupom já resgatado continua válido se a empresa desativar o cupom depois.
