# Diagramas

Cada diagrama tem a sua pasta, com o fonte (`.puml`, PlantUML) e a imagem (`.png`) gerada a partir dele, com o mesmo nome da pasta:

- `domain/`: modelo lógico do banco de dados
- `use-cases/`: casos de uso por perfil (admin, empresa, gestante, médico)
- `sequence-diagrams/`: diagramas de sequência dos fluxos
- `implementation-diagrams/`: fluxos internos da API (atividades por camada)

Para gerar a imagem de novo depois de editar o fonte: `java -jar plantuml.jar -tpng <pasta>/<nome>.puml` (PlantUML 1.2024.7). O modelo lógico e os casos de uso usam o layout `smetana`, que não precisa do Graphviz.

No modelo lógico, as tabelas em cinza ainda não têm migração (agendamento, pontos, resgate, conteúdos e auditoria); as demais seguem as migrações V1 a V10.

## Decisões

- **Status do agendamento:** `AGENDADO`, que passa a `REALIZADO` ou `CANCELADO`.
- **Regra de negócio violada:** `409 Conflict` (`ConflitoException`); dados inválidos, `400 Bad Request`. A API não usa 422.
- **Usuário do token:** as rotas da gestante e da empresa não levam o id do usuário no caminho (`/gestantes/pontuacao`, `/empresas/produtos`); recurso de outro usuário responde como inexistente (404).
- **Quem publica conteúdo educativo:** o admin (usuário interno do Previna), registrado em `conteudo_informativo.autor_id`. *A confirmar.*
- **Cálculo dos pontos:** `arredondar(tipo_atendimento.pontos_base × dados_socioeconomicos.fator_ponderacao)`, com o fator gravado em `transacao_pontos.fator_aplicado`. O fator é neutro (1,00) até a regra de ponderação ser definida.
- **Promoção e cupom:** a promoção vale para todos os produtos da empresa em uma categoria (fraldas, roupas ou remédios); o cupom vale para um produto específico, sem quantidade, e a empresa o ativa ou desativa.
- **Validação de cupom:** cupom de outra empresa tem a mesma resposta do código inexistente (404); o cupom já resgatado continua válido se a empresa desativar o cupom depois.
