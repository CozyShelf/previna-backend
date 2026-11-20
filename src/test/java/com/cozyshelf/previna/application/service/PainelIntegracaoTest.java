package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.response.ListaCurtaResponse;
import com.cozyshelf.previna.application.dto.response.PainelAdminResponse;
import com.cozyshelf.previna.application.dto.response.PainelEmpresaResponse;
import com.cozyshelf.previna.application.dto.response.PainelMedicoResponse;
import com.cozyshelf.previna.domain.model.AcaoAuditoria;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.CategoriaConteudo;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.ConteudoInformativo;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Perfil;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.model.TipoConteudo;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.ConteudoInformativoRepository;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

// O painel da tela Início contra o PostgreSQL de verdade: os limites de data, os status, o total maior que a lista
// curta, a ordem e o isolamento por médico e por empresa só se provam no banco. Roda só com PREVINA_TEST_DB_URL (ver
// ResgateCupomIntegracaoTest e o README).
//
// O banco é compartilhado e não é limpo. O painel do médico sem verTodasGestantes e o da empresa só enxergam os dados
// criados pelo próprio teste (um médico e uma empresa novos), então os números são conferidos exatos. Os números
// globais (o médico que vê todas e o admin) são conferidos pela diferença entre o antes e o depois de criar os dados,
// os dois lidos na mesma transação REPEATABLE READ, que é desfeita no fim (emFotografia): o que outros testes ou outras
// execuções gravarem ao mesmo tempo fica fora da fotografia e não muda a diferença.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PREVINA_TEST_DB_URL", matches = ".+")
class PainelIntegracaoTest {
    // Prefixo 8 nos documentos separa estes dos gerados pelos outros testes de integração
    private static final AtomicLong SEQUENCIA =
            new AtomicLong(System.currentTimeMillis() % 1_000_000_000L + 800_000_000L);
    private static final LocalDate HOJE = LocalDate.now();
    private static final ZoneId FUSO_DO_BANCO = ZoneId.of("America/Sao_Paulo");

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PREVINA_TEST_DB_URL"));
    }

    @Autowired
    private PainelMedicoService painelMedico;
    @Autowired
    private PainelEmpresaService painelEmpresa;
    @Autowired
    private PainelAdminService painelAdmin;
    @Autowired
    private CupomService cupomService;
    @Autowired
    private PromocaoService promocaoService;
    @Autowired
    private MedicoRepository medicoRepository;
    @Autowired
    private GestanteRepository gestanteRepository;
    @Autowired
    private TipoAtendimentoRepository tipoRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private EmpresaParceiraRepository empresaRepository;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private CupomRepository cupomRepository;
    @Autowired
    private PromocaoRepository promocaoRepository;
    @Autowired
    private CupomResgatadoRepository resgateRepository;
    @Autowired
    private ConteudoInformativoRepository conteudoRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    // ---- Dados ----

    private static long proximo() {
        return SEQUENCIA.incrementAndGet();
    }

    private Medico medico(boolean verTodas) {
        long n = proximo();
        return medicoRepository.save(new Medico("Dr. Painel " + n, "8%010d".formatted(n), null, "CRM-P" + n,
                "medpainel" + n, "hash", verTodas));
    }

    private Gestante gestante(Medico medico, String nome) {
        long n = proximo();
        return gestanteRepository.save(new Gestante(nome, "8%010d".formatted(n), null, "gespainel" + n, "hash",
                medico, LocalDate.of(1995, 1, 1), "11999999999", HOJE.minusDays(60)));
    }

    private TipoAtendimento tipo(Medico medico) {
        return tipoRepository.save(new TipoAtendimento(medico.getId(), "Ultrassom " + proximo(),
                CategoriaAtendimento.EXAME, 30));
    }

    private Agendamento agendado(Gestante gestante, TipoAtendimento tipo, LocalDate data) {
        return agendamentoRepository.save(new Agendamento(gestante, tipo, gestante.getMedico(), data, null));
    }

    private Agendamento resolvido(Gestante gestante, TipoAtendimento tipo, LocalDate data, StatusAgendamento status) {
        Agendamento agendamento = new Agendamento(gestante, tipo, gestante.getMedico(), data, null);
        agendamento.registrarDesfecho(status, gestante.getMedico());
        return agendamentoRepository.save(agendamento);
    }

    private EmpresaParceira empresa() {
        long n = proximo();
        return empresaRepository.save(new EmpresaParceira("Loja Painel " + n, "8%013d".formatted(n), null, null,
                null, "emppainel" + n, "hash"));
    }

    private Produto produto(EmpresaParceira empresa, String nome, Categoria categoria) {
        return produtoRepository.save(new Produto(empresa.getId(), nome, null, categoria, null));
    }

    private Cupom cupom(Produto produto, LocalDate validade, boolean ativo) {
        Cupom cupom = new Cupom(produto.getEmpresaId(), produto, new BigDecimal("10"), validade, 50);
        cupom.alterarAtivo(ativo);
        return cupomRepository.save(cupom);
    }

    private Promocao promocao(EmpresaParceira empresa, Categoria categoria, LocalDate inicio, LocalDate fim,
                              boolean ativa) {
        Promocao promocao = new Promocao(empresa.getId(), "Semana do bebê", null, new BigDecimal("15"), inicio, fim,
                categoria);
        promocao.alterarAtivo(ativa);
        return promocaoRepository.save(promocao);
    }

    private static String codigo() {
        return "PN%010d".formatted(proximo());
    }

    // Resgate de cupom com o status, a validade copiada e a data de uso definidos direto no banco (cupom_resgatado não
    // tem gatilho; o serviço só chega a esses estados com o tempo passando)
    private CupomResgatado resgate(Cupom cupom, EmpresaParceira empresa, Gestante gestante,
                                   StatusCupomResgatado status, LocalDate validade, LocalDateTime dataUso) {
        CupomResgatado resgate = resgateRepository.save(new CupomResgatado(cupom, empresa, gestante, codigo(), null));
        return ajustar(resgate, status, validade, dataUso);
    }

    private CupomResgatado resgate(Promocao promocao, EmpresaParceira empresa, Gestante gestante,
                                   StatusCupomResgatado status, LocalDate validade, LocalDateTime dataUso) {
        CupomResgatado resgate = resgateRepository.save(
                new CupomResgatado(promocao, empresa, gestante, codigo(), null));
        return ajustar(resgate, status, validade, dataUso);
    }

    private CupomResgatado ajustar(CupomResgatado resgate, StatusCupomResgatado status, LocalDate validade,
                                   LocalDateTime dataUso) {
        jdbc.update("UPDATE cupom_resgatado SET status = ?, data_validade = ?, data_uso = ? WHERE id = ?",
                status.name(), validade, dataUso == null ? null : Timestamp.valueOf(dataUso), resgate.getId());
        return resgate;
    }

    // O instante que a API mostra para uma data e hora gravada direto no banco: o Hibernate lê as colunas TIMESTAMP no
    // fuso de hibernate.jdbc.time_zone (o de Brasília), e não no da JVM do teste, que no contêiner é UTC
    private static Instant doBanco(LocalDateTime gravada) {
        return gravada.atZone(FUSO_DO_BANCO).toInstant();
    }

    private long logsDe(Long idUsuario) {
        return jdbc.queryForObject("SELECT count(*) FROM log_auditoria WHERE usuario_id = ?", Long.class, idUsuario);
    }

    // Antes e depois na mesma fotografia do banco (REPEATABLE READ), desfeita no fim: nada do que o teste cria fica
    // gravado, e nada do que outros gravam no meio entra na conta
    private <T> T emFotografia(Supplier<T> corpo) {
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        transacao.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transacao.execute(status -> {
            status.setRollbackOnly();
            return corpo.get();
        });
    }

    private static List<Long> idsAgendamentos(ListaCurtaResponse<PainelMedicoResponse.AgendamentoPendente> lista) {
        return lista.itens().stream().map(PainelMedicoResponse.AgendamentoPendente::agendamentoId).toList();
    }

    private static List<Long> ids(List<Agendamento> agendamentos) {
        return agendamentos.stream().map(Agendamento::getId).toList();
    }

    // ---- Médico ----

    @Test
    void medicoVeOsLimitesDeDataEOsStatusSoDasSuasGestantes() {
        Medico medico = medico(false);
        Gestante ana = gestante(medico, "Ana");
        Gestante bruna = gestante(medico, "Bruna");
        Gestante carla = gestante(medico, "Carla");
        Gestante duda = gestante(medico, "Duda");
        TipoAtendimento tipo = tipo(medico);

        Agendamento ontem = agendado(ana, tipo, HOJE.minusDays(1));
        agendado(ana, tipo, HOJE); // hoje: está na agenda do dia, nem atrasado nem próximo
        Agendamento amanha = agendado(ana, tipo, HOJE.plusDays(1));
        Agendamento daquiASete = agendado(ana, tipo, HOJE.plusDays(7));
        agendado(ana, tipo, HOJE.plusDays(8)); // depois da janela de 7 dias
        // Resolvidos não contam como pendentes, mesmo na janela
        resolvido(ana, tipo, HOJE.minusDays(2), StatusAgendamento.REALIZADO);
        resolvido(ana, tipo, HOJE.plusDays(2), StatusAgendamento.NAO_COMPARECEU);
        // A Bruna só tem resolvidos e a Carla nada: as duas estão sem pendente. A Duda tem o de hoje
        resolvido(bruna, tipo, HOJE.minusDays(3), StatusAgendamento.REALIZADO);
        resolvido(bruna, tipo, HOJE.minusDays(4), StatusAgendamento.NAO_COMPARECEU);
        agendado(duda, tipo, HOJE);

        // Gestante de outro médico, com atrasado, próximo e sem pendente: não aparece
        Medico outro = medico(false);
        Gestante deOutro = gestante(outro, "Aaron de outro médico");
        TipoAtendimento tipoDoOutro = tipo(outro);
        agendado(deOutro, tipoDoOutro, HOJE.minusDays(1));
        agendado(deOutro, tipoDoOutro, HOJE.plusDays(1));
        gestante(outro, "Abigail de outro médico");

        PainelMedicoResponse painel = painelMedico.painel(medico.getId());

        assertEquals(4, painel.gestantesAcompanhadas());
        assertEquals(1, painel.atrasados().total());
        assertEquals(List.of(ontem.getId()), idsAgendamentos(painel.atrasados()));
        var item = painel.atrasados().itens().getFirst();
        assertEquals(new PainelMedicoResponse.AgendamentoPendente(ontem.getId(), ana.getId(), "Ana", tipo.getNome(),
                CategoriaAtendimento.EXAME, HOJE.minusDays(1)), item);
        assertEquals(2, painel.proximos7Dias().total());
        assertEquals(List.of(amanha.getId(), daquiASete.getId()), idsAgendamentos(painel.proximos7Dias()));
        assertEquals(2, painel.semAgendamentoPendente().total());
        assertEquals(List.of(new PainelMedicoResponse.GestanteSemPendente(bruna.getId(), "Bruna"),
                new PainelMedicoResponse.GestanteSemPendente(carla.getId(), "Carla")),
                painel.semAgendamentoPendente().itens());
        // Só leitura: nada vai para o log de auditoria
        assertEquals(0, logsDe(medico.getId()));

        // O outro médico vê só as dele
        PainelMedicoResponse doOutro = painelMedico.painel(outro.getId());
        assertEquals(2, doOutro.gestantesAcompanhadas());
        assertEquals(1, doOutro.atrasados().total());
        assertEquals(1, doOutro.proximos7Dias().total());
        assertEquals(1, doOutro.semAgendamentoPendente().total());
        assertEquals("Abigail de outro médico", doOutro.semAgendamentoPendente().itens().getFirst().gestanteNome());
    }

    @Test
    void medicoRecebeOsCincoPrimeirosEmOrdemEOTotalCompleto() {
        Medico medico = medico(false);
        Gestante gestante = gestante(medico, "Maria");
        TipoAtendimento tipo = tipo(medico);
        TipoAtendimento outroTipo = tipo(medico);

        // Atrasados cadastrados fora de ordem; dois no mesmo dia, desempatados pela ordem de cadastro
        List<Agendamento> atrasados = new ArrayList<>();
        for (int dias : new int[]{3, 1, 7, 5, 2, 6, 4}) {
            atrasados.add(agendado(gestante, tipo, HOJE.minusDays(dias)));
        }
        Agendamento empateSeteDias = agendado(gestante, outroTipo, HOJE.minusDays(7));
        List<Agendamento> proximos = new ArrayList<>();
        for (int dias : new int[]{3, 1, 7, 5, 2, 6, 4}) {
            proximos.add(agendado(gestante, tipo, HOJE.plusDays(dias)));
        }
        // Sete gestantes sem pendente, cadastradas fora da ordem de nome; duas com o mesmo nome
        List<Gestante> semPendente = new ArrayList<>();
        for (String nome : new String[]{"Gabi", "Bia", "Fabi", "Bia", "Dani", "Eva", "Cris"}) {
            semPendente.add(gestante(medico, nome));
        }

        PainelMedicoResponse painel = painelMedico.painel(medico.getId());

        assertEquals(8, painel.gestantesAcompanhadas());
        assertEquals(8, painel.atrasados().total());
        assertEquals(List.of(atrasados.get(2).getId(), empateSeteDias.getId(), atrasados.get(5).getId(),
                atrasados.get(3).getId(), atrasados.get(6).getId()), idsAgendamentos(painel.atrasados()));
        assertEquals(7, painel.proximos7Dias().total());
        assertEquals(ids(List.of(proximos.get(1), proximos.get(4), proximos.get(0), proximos.get(6),
                proximos.get(3))), idsAgendamentos(painel.proximos7Dias()));
        assertEquals(7, painel.semAgendamentoPendente().total());
        assertEquals(List.of(semPendente.get(1).getId(), semPendente.get(3).getId(), semPendente.get(6).getId(),
                        semPendente.get(4).getId(), semPendente.get(5).getId()),
                painel.semAgendamentoPendente().itens().stream()
                        .map(PainelMedicoResponse.GestanteSemPendente::gestanteId).toList());
    }

    @Test
    void medicoComVerTodasGestantesVeTambemAsDosOutros() {
        long[] diferencas = emFotografia(() -> {
            Medico todas = medico(true);
            PainelMedicoResponse antes = painelMedico.painel(todas.getId());

            // Gestantes de outro médico, nenhuma do médico que vê todas
            Medico outro = medico(false);
            Gestante comPendentes = gestante(outro, "Helena");
            gestante(outro, "Iris");
            TipoAtendimento tipo = tipo(outro);
            agendado(comPendentes, tipo, HOJE.minusDays(1));
            agendado(comPendentes, tipo, HOJE.plusDays(1));
            agendado(comPendentes, tipo, HOJE.plusDays(8));

            PainelMedicoResponse depois = painelMedico.painel(todas.getId());
            return new long[]{
                    depois.gestantesAcompanhadas() - antes.gestantesAcompanhadas(),
                    depois.atrasados().total() - antes.atrasados().total(),
                    depois.proximos7Dias().total() - antes.proximos7Dias().total(),
                    depois.semAgendamentoPendente().total() - antes.semAgendamentoPendente().total()};
        });

        assertArrayEquals(new long[]{2, 1, 1, 1}, diferencas);
    }

    // ---- Empresa ----

    @Test
    void empresaVeOsLimitesEOsStatusSoDosSeusDados() {
        EmpresaParceira empresa = empresa();
        Medico medico = medico(false);
        Gestante gestante = gestante(medico, "Maria");

        Produto chupeta = produto(empresa, "Chupeta", null);
        Produto fralda = produto(empresa, "Fralda RN", Categoria.FRALDAS);
        Produto mamadeira = produto(empresa, "Mamadeira", null);
        Produto lenco = produto(empresa, "Lenço", Categoria.FRALDAS);
        Produto babador = produto(empresa, "Babador", null);
        Produto body = produto(empresa, "Body", Categoria.ROUPAS);
        Produto xarope = produto(empresa, "Xarope", Categoria.REMEDIOS);

        Cupom vencido = cupom(chupeta, HOJE.minusDays(1), true);
        Cupom venceHoje = cupom(fralda, HOJE, true);
        Cupom venceEmSete = cupom(mamadeira, HOJE.plusDays(7), true);
        cupom(lenco, HOJE.plusDays(8), true); // ativo, mas fora dos 7 dias
        cupom(babador, HOJE.plusDays(1), false); // desativado

        Promocao roupas = promocao(empresa, Categoria.ROUPAS, HOJE, HOJE, true); // começa e termina hoje: vigente
        promocao(empresa, Categoria.FRALDAS, HOJE.minusDays(5), HOJE.minusDays(1), true); // terminou ontem
        promocao(empresa, Categoria.REMEDIOS, HOJE.plusDays(1), HOJE.plusDays(5), true); // começa amanhã
        promocao(empresa, Categoria.FRALDAS, HOJE, HOJE.plusDays(3), false); // desativada

        LocalDateTime agora = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        resgate(venceHoje, empresa, gestante, StatusCupomResgatado.ATIVO, HOJE, null); // aguardando: vale até hoje
        resgate(roupas, empresa, gestante, StatusCupomResgatado.ATIVO, HOJE.plusDays(1), null); // aguardando
        resgate(vencido, empresa, gestante, StatusCupomResgatado.ATIVO, HOJE.minusDays(1), null); // vencido, ainda ATIVO
        resgate(venceHoje, empresa, gestante, StatusCupomResgatado.EXPIRADO, HOJE.minusDays(1), null);
        CupomResgatado usadoAgora = resgate(venceHoje, empresa, gestante, StatusCupomResgatado.UTILIZADO, HOJE,
                agora.minusHours(1));
        CupomResgatado usado29Dias = resgate(venceEmSete, empresa, gestante, StatusCupomResgatado.UTILIZADO, HOJE,
                agora.minusDays(29));
        CupomResgatado usado31Dias = resgate(venceHoje, empresa, gestante, StatusCupomResgatado.UTILIZADO, HOJE,
                agora.minusDays(31)); // fora dos 30 dias, mas entre as últimas validações

        // Outra empresa, com de tudo um pouco, e a mesma gestante: nada disso conta
        EmpresaParceira outra = empresa();
        Produto daOutra = produto(outra, "Fralda RN", Categoria.FRALDAS);
        Cupom cupomDaOutra = cupom(daOutra, HOJE.plusDays(1), true);
        produto(outra, "Body", null);
        promocao(outra, Categoria.ROUPAS, HOJE, HOJE.plusDays(1), true);
        resgate(cupomDaOutra, outra, gestante, StatusCupomResgatado.ATIVO, HOJE.plusDays(1), null);
        resgate(cupomDaOutra, outra, gestante, StatusCupomResgatado.UTILIZADO, HOJE.plusDays(1), agora);

        PainelEmpresaResponse painel = painelEmpresa.painel(empresa.getId());

        assertEquals(3, painel.cuponsAtivos());
        assertEquals(1, painel.promocoesVigentes());
        assertEquals(2, painel.resgatesAguardandoUso());
        assertEquals(2, painel.validacoes30Dias());
        assertEquals(new ListaCurtaResponse<>(2, List.of(
                new PainelEmpresaResponse.CupomVencendo(venceHoje.getId(), "Fralda RN", HOJE),
                new PainelEmpresaResponse.CupomVencendo(venceEmSete.getId(), "Mamadeira", HOJE.plusDays(7)))),
                painel.cuponsVencendo7Dias());
        // Fralda, Mamadeira e Lenço têm cupom válido; Body, a promoção de roupas. Babador (cupom desativado),
        // Chupeta (cupom vencido) e Xarope (promoção que ainda não começou) ficam sem oferta
        assertEquals(new ListaCurtaResponse<>(3, List.of(
                new PainelEmpresaResponse.ProdutoSemOferta(babador.getId(), "Babador"),
                new PainelEmpresaResponse.ProdutoSemOferta(chupeta.getId(), "Chupeta"),
                new PainelEmpresaResponse.ProdutoSemOferta(xarope.getId(), "Xarope"))),
                painel.produtosSemOferta());
        assertEquals(List.of(usadoAgora.getId(), usado29Dias.getId(), usado31Dias.getId()),
                painel.ultimasValidacoes().stream().map(PainelEmpresaResponse.Validacao::resgateId).toList());
        var ultima = painel.ultimasValidacoes().getFirst();
        assertEquals(new PainelEmpresaResponse.Validacao(usadoAgora.getId(), usadoAgora.getCodigo(),
                TipoResgate.CUPOM, null, "Fralda RN",
                doBanco(agora.minusHours(1))), ultima);
        assertEquals(0, logsDe(empresa.getId()));

        // A outra empresa vê só os dela
        PainelEmpresaResponse daOutraEmpresa = painelEmpresa.painel(outra.getId());
        assertEquals(1, daOutraEmpresa.cuponsAtivos());
        assertEquals(1, daOutraEmpresa.promocoesVigentes());
        assertEquals(1, daOutraEmpresa.resgatesAguardandoUso());
        assertEquals(1, daOutraEmpresa.validacoes30Dias());
        assertEquals(1, daOutraEmpresa.cuponsVencendo7Dias().total());
        assertEquals(1, daOutraEmpresa.produtosSemOferta().total());
        assertEquals(1, daOutraEmpresa.ultimasValidacoes().size());
    }

    @Test
    void validacaoDePromocaoTemTituloESemProduto() {
        EmpresaParceira empresa = empresa();
        Gestante gestante = gestante(medico(false), "Maria");
        Promocao promocao = promocao(empresa, Categoria.ROUPAS, HOJE, HOJE.plusDays(3), true);
        LocalDateTime uso = LocalDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.MICROS);
        CupomResgatado usado = resgate(promocao, empresa, gestante, StatusCupomResgatado.UTILIZADO,
                HOJE.plusDays(3), uso);

        var validacao = painelEmpresa.painel(empresa.getId()).ultimasValidacoes().getFirst();

        assertEquals(new PainelEmpresaResponse.Validacao(usado.getId(), usado.getCodigo(), TipoResgate.PROMOCAO,
                "Semana do bebê", null, doBanco(uso)), validacao);
    }

    @Test
    void empresaRecebeOsCincoPrimeirosEmOrdemEOTotalCompleto() {
        EmpresaParceira empresa = empresa();
        Gestante gestante = gestante(medico(false), "Maria");
        Produto comCupom = produto(empresa, "Fralda RN", Categoria.FRALDAS);

        // Vencendo nos 7 dias, cadastrados fora de ordem; dois no mesmo dia, desempatados pelo id
        List<Cupom> vencendo = new ArrayList<>();
        for (int dias : new int[]{6, 0, 4, 2, 7, 3, 2}) {
            vencendo.add(cupom(comCupom, HOJE.plusDays(dias), true));
        }
        // Produtos sem oferta, fora da ordem de nome
        List<Produto> semOferta = new ArrayList<>();
        for (String nome : new String[]{"Touca", "Babador", "Meia", "Chupeta", "Pomada", "Lenço", "Body"}) {
            semOferta.add(produto(empresa, nome, null));
        }
        // Validações em instantes fora de ordem; duas no mesmo instante, desempatadas pelo id (a maior primeiro)
        LocalDateTime agora = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        List<CupomResgatado> usados = new ArrayList<>();
        for (int horas : new int[]{5, 1, 7, 3, 1, 6, 2}) {
            usados.add(resgate(vencendo.getFirst(), empresa, gestante, StatusCupomResgatado.UTILIZADO, HOJE,
                    agora.minusHours(horas)));
        }

        PainelEmpresaResponse painel = painelEmpresa.painel(empresa.getId());

        assertEquals(7, painel.cuponsAtivos());
        assertEquals(7, painel.cuponsVencendo7Dias().total());
        assertEquals(List.of(vencendo.get(1).getId(), vencendo.get(3).getId(), vencendo.get(6).getId(),
                        vencendo.get(5).getId(), vencendo.get(2).getId()),
                painel.cuponsVencendo7Dias().itens().stream().map(PainelEmpresaResponse.CupomVencendo::cupomId)
                        .toList());
        assertEquals(7, painel.produtosSemOferta().total());
        assertEquals(List.of("Babador", "Body", "Chupeta", "Lenço", "Meia"),
                painel.produtosSemOferta().itens().stream().map(PainelEmpresaResponse.ProdutoSemOferta::nome)
                        .toList());
        assertEquals(7, painel.validacoes30Dias());
        assertEquals(List.of(usados.get(4).getId(), usados.get(1).getId(), usados.get(6).getId(),
                        usados.get(3).getId(), usados.get(0).getId()),
                painel.ultimasValidacoes().stream().map(PainelEmpresaResponse.Validacao::resgateId).toList());
    }

    // ---- Admin ----

    @Test
    void adminContaPorDiferencaComOsStatus() {
        long[] diferencas = emFotografia(() -> {
            PainelAdminResponse antes = painelAdmin.painel();

            Medico medico = medico(false);
            medico(true);
            EmpresaParceira ativa = empresa();
            empresa();
            empresa();
            Gestante gestanteAtiva = gestante(medico, "Maria");
            gestante(medico, "Joana");
            jdbc.update("UPDATE usuario SET status_conta = 'ATIVA' WHERE id IN (?, ?)", ativa.getId(),
                    gestanteAtiva.getId());
            for (boolean publicado : new boolean[]{true, true, false}) {
                ConteudoInformativo conteudo = new ConteudoInformativo(medico.getId(), "Pré-natal em dia",
                        CategoriaConteudo.DICA, TipoConteudo.TEXTO, null, "Vá a todas as consultas.", null);
                conteudo.alterarPublicacao(publicado);
                conteudoRepository.save(conteudo);
            }

            PainelAdminResponse depois = painelAdmin.painel();
            return new long[]{
                    depois.medicos() - antes.medicos(),
                    depois.empresas().total() - antes.empresas().total(),
                    depois.empresas().primeiroAcessoPendente() - antes.empresas().primeiroAcessoPendente(),
                    depois.gestantes().total() - antes.gestantes().total(),
                    depois.gestantes().primeiroAcessoPendente() - antes.gestantes().primeiroAcessoPendente(),
                    depois.conteudos().publicados() - antes.conteudos().publicados(),
                    depois.conteudos().rascunhos() - antes.conteudos().rascunhos(),
                    depois.empresasPendentes().total() - antes.empresasPendentes().total()};
        });

        assertArrayEquals(new long[]{2, 3, 2, 2, 1, 2, 1, 2}, diferencas);
    }

    @Test
    void adminVeAsEmpresasPendentesDaMaisAntigaEmDiante() {
        record Resultado(long diferenca, List<PainelAdminResponse.EmpresaPendente> itens,
                         List<PainelAdminResponse.EmpresaPendente> esperados) {}

        Resultado resultado = emFotografia(() -> {
            long antes = painelAdmin.painel().empresasPendentes().total();
            // Pré-cadastradas num passado que nenhum outro dado tem, para serem as primeiras da lista; fora de ordem,
            // duas no mesmo instante (o id desempata). A mais antiga de todas já fez o primeiro acesso: não entra
            LocalDateTime base = LocalDateTime.of(1990, 1, 1, 10, 0);
            List<EmpresaParceira> pendentes = new ArrayList<>();
            List<LocalDateTime> criadas = new ArrayList<>();
            for (int dias : new int[]{4, 1, 6, 2, 1, 5, 3}) {
                EmpresaParceira empresa = empresa();
                LocalDateTime criadaEm = base.plusDays(dias);
                jdbc.update("UPDATE usuario SET criado_em = ? WHERE id = ?", Timestamp.valueOf(criadaEm),
                        empresa.getId());
                pendentes.add(empresa);
                criadas.add(criadaEm);
            }
            EmpresaParceira jaAtiva = empresa();
            jdbc.update("UPDATE usuario SET criado_em = ?, status_conta = 'ATIVA' WHERE id = ?",
                    Timestamp.valueOf(base), jaAtiva.getId());
            // O UPDATE foi direto no banco: sem limpar, as empresas sairiam com o criado_em antigo, do contexto
            entityManager.clear();

            PainelAdminResponse depois = painelAdmin.painel();
            List<PainelAdminResponse.EmpresaPendente> esperados = new ArrayList<>();
            for (int i : new int[]{1, 4, 3, 6, 0}) {
                esperados.add(new PainelAdminResponse.EmpresaPendente(pendentes.get(i).getId(),
                        pendentes.get(i).getRazaoSocial(),
                        doBanco(criadas.get(i))));
            }
            return new Resultado(depois.empresasPendentes().total() - antes, depois.empresasPendentes().itens(),
                    esperados);
        });

        assertEquals(7, resultado.diferenca());
        assertEquals(resultado.esperados(), resultado.itens());
    }

    @Test
    void adminVeAsDezUltimasAtividadesSemNenhumDadoDaGestante() throws Exception {
        record Resultado(List<PainelAdminResponse.Atividade> atividades, List<PainelAdminResponse.Atividade> esperadas,
                         String json, String nomeGestante, String cpfGestante) {}

        Resultado resultado = emFotografia(() -> {
            Medico medico = medico(false);
            Gestante gestante = gestante(medico, "Gestante Sigilosa " + proximo());
            EmpresaParceira empresa = empresa();
            LocalDateTime base = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

            // Onze linhas: a primeira (a mais antiga) fica de fora das dez
            record Linha(AcaoAuditoria acao, Long usuario, String nome, Perfil perfil, Integer pontos) {}
            List<Linha> linhas = List.of(
                    new Linha(AcaoAuditoria.AGENDAMENTO_CRIADO, medico.getId(), medico.getNome(), Perfil.MEDICO, null),
                    new Linha(AcaoAuditoria.AGENDAMENTO_CRIADO, medico.getId(), medico.getNome(), Perfil.MEDICO, null),
                    new Linha(AcaoAuditoria.AGENDAMENTO_REALIZADO, medico.getId(), medico.getNome(), Perfil.MEDICO,
                            50),
                    new Linha(AcaoAuditoria.AGENDAMENTO_NAO_COMPARECEU, medico.getId(), medico.getNome(),
                            Perfil.MEDICO, null),
                    new Linha(AcaoAuditoria.CUPOM_RESGATADO, gestante.getId(), null, Perfil.GESTANTE, 100),
                    new Linha(AcaoAuditoria.PROMOCAO_RESGATADA, gestante.getId(), null, Perfil.GESTANTE, null),
                    new Linha(AcaoAuditoria.RESGATE_VALIDADO, empresa.getId(), empresa.getRazaoSocial(),
                            Perfil.EMPRESA_PARCEIRA, null),
                    new Linha(AcaoAuditoria.AGENDAMENTO_CRIADO, medico.getId(), medico.getNome(), Perfil.MEDICO, null),
                    new Linha(AcaoAuditoria.CUPOM_RESGATADO, gestante.getId(), null, Perfil.GESTANTE, 80),
                    new Linha(AcaoAuditoria.RESGATE_VALIDADO, empresa.getId(), empresa.getRazaoSocial(),
                            Perfil.EMPRESA_PARCEIRA, null),
                    new Linha(AcaoAuditoria.AGENDAMENTO_REALIZADO, medico.getId(), medico.getNome(), Perfil.MEDICO,
                            30));
            List<PainelAdminResponse.Atividade> esperadas = new ArrayList<>();
            for (int i = 0; i < linhas.size(); i++) {
                Linha linha = linhas.get(i);
                LocalDateTime quando = base.plusSeconds(i);
                jdbc.update("INSERT INTO log_auditoria (data_hora, acao, usuario_id, gestante_id, pontos)"
                                + " VALUES (?, ?, ?, ?, ?)", Timestamp.valueOf(quando), linha.acao().name(),
                        linha.usuario(), gestante.getId(), linha.pontos());
                Instant instante = doBanco(quando);
                esperadas.addFirst(new PainelAdminResponse.Atividade(instante, linha.acao(), linha.nome(),
                        linha.perfil(), linha.pontos()));
            }

            PainelAdminResponse painel = painelAdmin.painel();
            try {
                return new Resultado(painel.atividadeRecente(), esperadas.subList(0, 10),
                        objectMapper.writeValueAsString(painel), gestante.getNome(), gestante.getCpf());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertEquals(resultado.esperadas(), resultado.atividades());
        // Nem o nome nem o CPF da gestante aparecem em lugar nenhum do painel do admin
        assertFalse(resultado.json().contains(resultado.nomeGestante()));
        assertFalse(resultado.json().contains(resultado.cpfGestante()));
        assertFalse(resultado.json().contains("gestanteId"));
    }

    // Ponta a ponta: os resgates feitos pela própria gestante, pelos serviços de verdade, gravam o log com ela como quem
    // fez; o painel mostra o perfil GESTANTE sem o nome, e os pontos do cupom positivos, como no log (a tela põe o sinal)
    @Test
    void resgateFeitoPelaGestanteApareceSemONomeDela() {
        List<PainelAdminResponse.Atividade> atividades = emFotografia(() -> {
            Gestante gestante = gestante(medico(false), "Gestante Sigilosa " + proximo());
            jdbc.update("UPDATE gestante SET saldo_pontos = 500 WHERE usuario_id = ?", gestante.getId());
            EmpresaParceira empresa = empresa();
            Cupom cupom = cupom(produto(empresa, "Fralda RN", Categoria.FRALDAS), HOJE.plusDays(5), true);
            Promocao promocao = promocao(empresa, Categoria.ROUPAS, HOJE, HOJE.plusDays(5), true);
            entityManager.clear();

            cupomService.resgatar(cupom.getId(), gestante.getId(), UUID.randomUUID());
            promocaoService.resgatar(promocao.getId(), gestante.getId(), UUID.randomUUID());

            return painelAdmin.painel().atividadeRecente().subList(0, 2);
        });

        assertEquals(List.of(AcaoAuditoria.PROMOCAO_RESGATADA, AcaoAuditoria.CUPOM_RESGATADO),
                atividades.stream().map(PainelAdminResponse.Atividade::acao).toList());
        for (PainelAdminResponse.Atividade atividade : atividades) {
            assertEquals(Perfil.GESTANTE, atividade.perfil());
            assertNull(atividade.usuarioNome());
        }
        assertNull(atividades.get(0).pontos());
        assertEquals(50, atividades.get(1).pontos());
    }
}
