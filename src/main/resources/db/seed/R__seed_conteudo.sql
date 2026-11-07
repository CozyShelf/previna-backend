-- Conteúdos educativos de exemplo, já publicados, nos tipos TEXTO, LINK e VIDEO e nas três categorias.
-- Sem exemplo de FOTO: a foto vem de arquivo enviado pelo admin, que o seed não tem como gravar.
-- Vídeo é sempre endereço de link, aberto no navegador pelo app.
-- Só no profile dev: db/seed não é carregado em produção.
-- O arquivo inteiro é guardado por NOT EXISTS porque não há chave natural para ON CONFLICT e a
-- migração é repeatable: ela roda de novo a cada alteração do arquivo.
-- O corpo vai entre $txt$: as quebras de linha do arquivo viram quebras de linha no texto, sem escapes.
INSERT INTO conteudo_informativo
    (autor_id, titulo, categoria, tipo, resumo, corpo, midia_url, publicado, criado_em, publicado_em)
SELECT admin.id, c.titulo, c.categoria, c.tipo, c.resumo, c.corpo, c.midia_url, TRUE, NOW(), NOW()
FROM (SELECT id FROM usuario WHERE login = 'admin') admin,
     (VALUES
        (
            'Alimentação na gestação: o que colocar no prato',
            'DICA', 'TEXTO',
            'Comer bem na gravidez é comer de tudo um pouco, em porções menores e mais vezes ao dia.',
$txt$Não é preciso "comer por dois". O que muda na gestação é a qualidade do que vai no prato, não o dobro da quantidade.

Monte metade do prato com legumes, verduras e frutas; um quarto com arroz, macarrão, batata ou mandioca; e um quarto com feijão, carne, ovo ou frango. Prefira os alimentos frescos aos ultraprocessados.

O ferro e o ácido fólico merecem atenção: feijão, lentilha, carnes vermelhas e folhas verde-escuras ajudam. Tome o suplemento que o seu médico receitou, mesmo comendo bem.

Beba cerca de 2 litros de água por dia e evite álcool por completo. Se estiver com enjoo, coma pouco e com mais frequência, e deixe algo seco (torrada, biscoito água e sal) ao lado da cama para antes de levantar.$txt$,
            NULL
        ),
        (
            'Exercícios seguros durante a gravidez',
            'DICA', 'TEXTO',
            'Trinta minutos de atividade leve na maior parte dos dias ajudam no sono, nas dores e no parto.',
$txt$Gravidez saudável não é gravidez parada. Caminhada, hidroginástica, natação e alongamento são seguros na maioria das gestações e ajudam a controlar o peso, o inchaço, a dor nas costas e a ansiedade.

A regra prática é o teste da conversa: se você consegue falar uma frase inteira enquanto se exercita, o ritmo está bom.

Evite esportes com risco de queda ou pancada (bicicleta na rua, vôlei, futebol, artes marciais), mergulho e exercícios deitada de barriga para cima depois do primeiro trimestre.

Pare e procure o serviço de saúde se sentir falta de ar fora do normal, dor no peito, contrações, sangramento ou perda de líquido. Antes de começar qualquer atividade, converse com o seu médico no pré-natal.$txt$,
            NULL
        ),
        (
            'Sinais de alerta: quando procurar o serviço de saúde na hora',
            'AVISO', 'TEXTO',
            'Alguns sinais não esperam a próxima consulta. Diante de qualquer um deles, procure atendimento imediatamente.',
$txt$Procure a maternidade ou a unidade de saúde IMEDIATAMENTE se tiver:

- Sangramento pela vagina, em qualquer quantidade
- Perda de líquido pela vagina
- Dor de cabeça forte que não passa, visão embaçada ou pontos luminosos
- Inchaço repentino no rosto, nas mãos ou nas pernas
- Febre
- Dor forte ou constante na barriga
- Vômitos que não param
- O bebê parar de mexer ou mexer bem menos do que o habitual
- Contrações regulares antes das 37 semanas

Não espere para ver se melhora e não vá sozinha se puder evitar. Leve sempre a sua caderneta da gestante: ela conta à equipe tudo o que já foi feito no seu pré-natal.$txt$,
            NULL
        ),
        (
            'Pré-natal odontológico também é pré-natal',
            'NOTICIA', 'TEXTO',
            'Tratar os dentes na gravidez é seguro e recomendado. Gengiva inflamada tem relação com parto prematuro.',
$txt$Ainda é comum ouvir que grávida não pode ir ao dentista. É o contrário: a consulta odontológica faz parte do pré-natal e é oferecida gratuitamente nas unidades básicas de saúde.

A alteração hormonal da gestação deixa a gengiva mais sensível e sangrando com facilidade. Quando essa inflamação não é tratada, ela está associada a parto prematuro e a bebês com baixo peso.

Limpeza, restauração e tratamento de cárie podem ser feitos com segurança, de preferência no segundo trimestre. O anestésico odontológico é seguro na gestação — avise sempre que está grávida e de quantas semanas.

Escove os dentes depois das refeições e use fio dental todos os dias. Se houver enjoo ao escovar, tente uma escova menor e espere cerca de 30 minutos após o vômito para escovar.$txt$,
            NULL
        ),
        (
            'Calendário de vacinação da gestante',
            'NOTICIA', 'LINK',
            'Quais vacinas tomar em cada fase da gestação, segundo o Ministério da Saúde.',
            NULL,
            'https://www.gov.br/saude/pt-br/vacinacao/calendario'
        ),
        (
            'Direitos da gestante no trabalho',
            'DICA', 'LINK',
            'Licença-maternidade, estabilidade no emprego, consultas em horário de trabalho e pausas para amamentar.',
            NULL,
            'https://www.gov.br/trabalho-e-emprego/pt-br/servicos/trabalhador'
        ),
        (
            'Importância de iniciar o pré-natal no início da gravidez',
            'DICA', 'VIDEO',
            'Começar cedo é o que permite acompanhar o bebê e tratar a tempo o que aparecer.',
            NULL,
            'https://www.youtube.com/watch?v=ZBKOK8MV5Vk'
        ),
        (
            'Como funciona o acompanhamento pré-natal',
            'DICA', 'VIDEO',
            'O que a equipe faz em cada consulta, do começo ao fim da gestação.',
            NULL,
            'https://www.youtube.com/watch?v=KHdN2i999ag'
        ),
        (
            'Importância das consultas e acompanhamento da gestante',
            'DICA', 'VIDEO',
            'Por que não faltar às consultas marcadas, mesmo se estiver tudo bem.',
            NULL,
            'https://www.youtube.com/watch?v=PSgljFSSs4o'
        ),
        (
            'Alimentação saudável durante a gestação',
            'DICA', 'VIDEO',
            'O que colocar no prato, o que evitar e como lidar com o enjoo.',
            NULL,
            'https://youtu.be/H6csbDXOGj8'
        ),
        (
            'Exames importantes durante o pré-natal',
            'DICA', 'VIDEO',
            'Quais exames são pedidos em cada trimestre e para que serve cada um.',
            NULL,
            'https://www.youtube.com/results?search_query=exames+importantes+pre+natal+gestantes'
        ),
        (
            'Vacinação durante a gravidez',
            'NOTICIA', 'VIDEO',
            'As vacinas recomendadas na gestação e quando tomar cada uma.',
            NULL,
            'https://www.youtube.com/results?search_query=vacinas+recomendadas+para+gestantes+ministerio+da+saude'
        ),
        (
            'Prevenção da sífilis e outras infecções na gestação',
            'AVISO', 'VIDEO',
            'Como se proteger, por que testar e por que tratar junto com o parceiro.',
            NULL,
            'https://www.youtube.com/watch?v=ZBKOK8MV5Vk'
        ),
        (
            'Saúde mental e emocional durante a gravidez',
            'DICA', 'VIDEO',
            'Ansiedade, medo e tristeza também fazem parte, e também têm cuidado.',
            NULL,
            'https://www.youtube.com/results?search_query=saude+mental+da+gestante+fiocruz'
        ),
        (
            'Sinais de alerta durante a gravidez',
            'AVISO', 'VIDEO',
            'Os sinais que pedem atendimento na hora, sem esperar a próxima consulta.',
            NULL,
            'https://www.youtube.com/results?search_query=sinais+de+alerta+na+gestacao+hospital'
        ),
        (
            'Cuidados com a pressão arterial e prevenção da pré-eclâmpsia',
            'AVISO', 'VIDEO',
            'Por que a pressão é medida em toda consulta e o que fazer se subir.',
            NULL,
            'https://www.youtube.com/watch?v=KHdN2i999ag'
        )
     ) AS c(titulo, categoria, tipo, resumo, corpo, midia_url)
WHERE NOT EXISTS (SELECT 1 FROM conteudo_informativo);
