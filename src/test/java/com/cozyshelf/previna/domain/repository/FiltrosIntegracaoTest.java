package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.TipoTransacao;
import com.cozyshelf.previna.domain.model.TransacaoPontos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

// Os filtros das listagens contra o PostgreSQL de verdade: unaccent, lower e like só se provam no banco (o teste
// de unidade com CriteriaBuilder simulado só confere que o filtro foi montado). Mesmo esquema do
// ResgateCupomIntegracaoTest: roda só com PREVINA_TEST_DB_URL apontando para um banco descartável.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PREVINA_TEST_DB_URL", matches = ".+")
class FiltrosIntegracaoTest {
    // Dados únicos por teste: o banco não é limpo entre os testes nem entre as execuções
    private static final AtomicLong SEQUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PREVINA_TEST_DB_URL"));
    }

    @Autowired
    private MedicoRepository medicoRepository;
    @Autowired
    private EmpresaParceiraRepository empresaRepository;
    @Autowired
    private GestanteRepository gestanteRepository;
    @Autowired
    private TransacaoPontosRepository transacaoRepository;

    // Sufixo só de letras, para o nome não cair na busca por documento e não colidir com outro teste
    private static String sufixo(long n) {
        StringBuilder letras = new StringBuilder();
        for (long resto = n; resto > 0; resto /= 26) {
            letras.append((char) ('a' + resto % 26));
        }
        return letras.toString();
    }

    // Prefixos 7 (CPF) e 8 (CNPJ) separam estes documentos dos gerados pelos outros testes de integração
    private Medico medico(String nome, long n) {
        return medicoRepository.save(new Medico(nome, "7%010d".formatted(n), null, "CRM-F" + n, "filtro" + n,
                "hash", false));
    }

    private List<Long> medicosEncontrados(String termo, Long... restritoA) {
        Specification<Medico> filtro = Filtros.busca(termo, "cpf", "nome", "crm", "email", "login");
        if (restritoA.length > 0) {
            filtro = filtro.and((raiz, consulta, cb) -> raiz.get("id").in((Object[]) restritoA));
        }
        return medicoRepository.findAll(filtro).stream().map(Medico::getId).toList();
    }

    // ---- Busca por texto ----

    @Test
    void buscaIgnoraAcentosEMaiusculasDosDoisLados() {
        long n = SEQUENCIA.incrementAndGet();
        String s = sufixo(n);
        Medico comAcento = medico("João Conceição " + s, n);
        Medico semAcento = medico("JOAO CONCEICAO " + s, SEQUENCIA.incrementAndGet());

        // Termo sem acento acha o nome com acento, e o contrário também
        assertEquals(List.of(comAcento.getId(), semAcento.getId()).stream().sorted().toList(),
                medicosEncontrados("joao conceicao " + s).stream().sorted().toList());
        assertEquals(List.of(comAcento.getId(), semAcento.getId()).stream().sorted().toList(),
                medicosEncontrados("JOÃO Conceição " + s.toUpperCase()).stream().sorted().toList());
    }

    @Test
    void buscaAchaTrechoNoMeioDoNomeComEspacosAparados() {
        long n = SEQUENCIA.incrementAndGet();
        String s = sufixo(n);
        Medico medico = medico("Ana Lúcia Souza " + s + " Prado", n);

        assertEquals(List.of(medico.getId()), medicosEncontrados("  lucia souza " + s + "  "));
        assertEquals(List.of(), medicosEncontrados("lucia " + s + " souza"));
    }

    @Test
    void buscaProcuraEmQualquerUmDosCampos() {
        long n = SEQUENCIA.incrementAndGet();
        Medico medico = medico("Bruno " + sufixo(n), n);

        // O login é "filtro<n>" e o CRM "CRM-F<n>": os dois têm letras, então não é busca por documento
        assertTrue(medicosEncontrados("FILTRO" + n).contains(medico.getId()));
        assertTrue(medicosEncontrados("crm-f" + n).contains(medico.getId()));
    }

    @Test
    void termoVazioOuEmBrancoNaoFiltra() {
        long n = SEQUENCIA.incrementAndGet();
        Medico medico = medico("Carla " + sufixo(n), n);

        assertEquals(List.of(medico.getId()), medicosEncontrados(null, medico.getId()));
        assertEquals(List.of(medico.getId()), medicosEncontrados("", medico.getId()));
        assertEquals(List.of(medico.getId()), medicosEncontrados("   ", medico.getId()));
    }

    // ---- Busca por documento ----

    @Test
    void cpfDigitadoComPontuacaoAchaODocumentoSoComDigitos() {
        long n = SEQUENCIA.incrementAndGet();
        Medico medico = medico("Daniela " + sufixo(n), n);
        String cpf = medico.getCpf();
        String formatado = "%s.%s.%s-%s".formatted(cpf.substring(0, 3), cpf.substring(3, 6), cpf.substring(6, 9),
                cpf.substring(9));

        assertEquals(List.of(medico.getId()), medicosEncontrados(formatado));
        // Trecho do meio, também com pontuação, como "123.456"
        assertTrue(medicosEncontrados(cpf.substring(3, 6) + "." + cpf.substring(6, 9)).contains(medico.getId()));
    }

    @Test
    void cnpjDigitadoComPontuacaoAchaAEmpresa() {
        long n = SEQUENCIA.incrementAndGet();
        String cnpj = "8%013d".formatted(n);
        EmpresaParceira empresa = empresaRepository.save(new EmpresaParceira("Farmácia " + sufixo(n), cnpj, null,
                null, null, "empresa.filtro" + n, "hash"));
        String formatado = "%s.%s.%s/%s-%s".formatted(cnpj.substring(0, 2), cnpj.substring(2, 5),
                cnpj.substring(5, 8), cnpj.substring(8, 12), cnpj.substring(12));

        List<Long> ids = empresaRepository.findAll(
                        Filtros.<EmpresaParceira>busca(formatado, "cnpj", "razaoSocial", "responsavel", "email"))
                .stream().map(EmpresaParceira::getId).toList();

        assertEquals(List.of(empresa.getId()), ids);
    }

    // Com letras no termo, os dígitos não são procurados no documento: "abc 123" não acha o CPF que contém 123
    @Test
    void termoComLetrasNaoProcuraNoDocumento() {
        long n = SEQUENCIA.incrementAndGet();
        Medico medico = medico("Eduarda " + sufixo(n), n);

        assertEquals(List.of(), medicosEncontrados("x" + medico.getCpf(), medico.getId()));
    }

    // ---- Período ----

    private TransacaoPontos transacao(Gestante gestante, LocalDateTime dataHora) {
        var transacao = new TransacaoPontos(gestante, null, TipoTransacao.CREDITO, 10, BigDecimal.ONE);
        return transacaoRepository.save(Campos.definir(transacao, "dataHora", dataHora));
    }

    @Test
    void periodoIncluiOsDiasInicialEFinalInteirosESoEles() {
        long n = SEQUENCIA.incrementAndGet();
        Medico medico = medico("Fernanda " + sufixo(n), n);
        long m = SEQUENCIA.incrementAndGet();
        Gestante gestante = gestanteRepository.save(new Gestante("Gabriela", "7%010d".formatted(m), null,
                "gestante.filtro" + m, "hash", medico, LocalDate.of(1995, 1, 1), "11999999999",
                LocalDate.of(2025, 1, 1)));
        LocalDate inicio = LocalDate.of(2025, 3, 10);
        LocalDate fim = LocalDate.of(2025, 3, 12);

        TransacaoPontos vespera = transacao(gestante, inicio.minusDays(1).atTime(LocalTime.of(23, 59, 59)));
        TransacaoPontos primeiroInstante = transacao(gestante, inicio.atStartOfDay());
        TransacaoPontos meio = transacao(gestante, LocalDateTime.of(2025, 3, 11, 12, 0));
        TransacaoPontos ultimoInstante = transacao(gestante, fim.atTime(23, 59, 59, 999_000_000));
        TransacaoPontos diaSeguinte = transacao(gestante, fim.plusDays(1).atStartOfDay());

        assertEquals(List.of(primeiroInstante.getId(), meio.getId(), ultimoInstante.getId()),
                transacoes(gestante, inicio, fim));
        // Só o início ou só o fim: o outro lado fica aberto
        assertEquals(List.of(primeiroInstante.getId(), meio.getId(), ultimoInstante.getId(), diaSeguinte.getId()),
                transacoes(gestante, inicio, null));
        assertEquals(List.of(vespera.getId(), primeiroInstante.getId(), meio.getId(), ultimoInstante.getId()),
                transacoes(gestante, null, fim));
        assertEquals(5, transacoes(gestante, null, null).size());
        // Um dia só: início igual ao fim
        assertEquals(List.of(meio.getId()), transacoes(gestante, meio.getDataHora().toLocalDate(),
                meio.getDataHora().toLocalDate()));
    }

    private List<Long> transacoes(Gestante gestante, LocalDate inicio, LocalDate fim) {
        Specification<TransacaoPontos> filtro = Specification.allOf(
                Filtros.igual("gestante", gestante),
                Filtros.noPeriodo("dataHora", inicio, fim));
        return transacaoRepository.findAll(filtro, Sort.by("dataHora")).stream().map(TransacaoPontos::getId).toList();
    }
}
