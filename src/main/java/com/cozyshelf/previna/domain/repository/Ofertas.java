package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.TipoOferta;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Catálogo de ofertas da gestante. Um produto tem oferta se tiver ao menos um cupom válido (ativo e dentro da
// validade) ou uma promoção vigente da mesma empresa na categoria dele; produto sem categoria nunca tem promoção.
// Empresa sem produto com oferta não aparece.
// shortcut: a promoção ainda não tem situação (ativo); entra em promocaoVigente e em PromocaoRepository.vigentes
// quando a coluna existir (#17)
public final class Ofertas {
    private Ofertas() {
    }

    // Filtros do catálogo; nulos não restringem. pontosAte: só produtos com algum cupom válido que custe até isso
    // (o saldo da gestante). dia: o de hoje, para a validade do cupom e a vigência da promoção.
    public record Filtro(String busca, Categoria categoria, Long empresaId, TipoOferta tipo, Integer pontosAte,
                         LocalDate dia) {
        public Filtro daEmpresa(Long idEmpresa) {
            return new Filtro(busca, categoria, idEmpresa, tipo, pontosAte, dia);
        }
    }

    public static Specification<Produto> produtos(Filtro filtro) {
        return (produto, consulta, cb) -> comOferta(produto, consulta, cb, filtro);
    }

    // Empresas com ao menos um produto que passe nos filtros
    public static Specification<EmpresaParceira> empresas(Filtro filtro) {
        return (empresa, consulta, cb) -> {
            Subquery<Long> produtos = consulta.subquery(Long.class);
            Root<Produto> produto = produtos.from(Produto.class);
            produtos.select(produto.get("id")).where(cb.equal(produto.get("empresaId"), empresa.get("id")),
                    comOferta(produto, produtos, cb, filtro));
            return cb.exists(produtos);
        };
    }

    private static Predicate comOferta(Root<Produto> produto, AbstractQuery<?> consulta, CriteriaBuilder cb,
                                       Filtro filtro) {
        List<Predicate> condicoes = new ArrayList<>();
        condicoes.add(switch (filtro.tipo()) {
            case null -> cb.or(cupomValido(produto, consulta, cb, filtro.dia(), null),
                    promocaoVigente(produto, consulta, cb, filtro.dia()));
            case CUPOM -> cupomValido(produto, consulta, cb, filtro.dia(), null);
            case PROMOCAO -> promocaoVigente(produto, consulta, cb, filtro.dia());
        });
        if (filtro.pontosAte() != null) {
            condicoes.add(cupomValido(produto, consulta, cb, filtro.dia(), filtro.pontosAte()));
        }
        if (filtro.categoria() != null) {
            condicoes.add(cb.equal(produto.get("categoria"), filtro.categoria()));
        }
        if (filtro.empresaId() != null) {
            condicoes.add(cb.equal(produto.get("empresaId"), filtro.empresaId()));
        }
        if (filtro.busca() != null && !filtro.busca().isBlank()) {
            String trecho = filtro.busca().trim();
            condicoes.add(cb.or(Filtros.contem(cb, produto.get("nome"), trecho),
                    empresaComNome(produto, consulta, cb, trecho)));
        }
        return cb.and(condicoes.toArray(Predicate[]::new));
    }

    // Ativo e com a validade não vencida (vale até o fim do dia da validade), opcionalmente até um custo
    private static Predicate cupomValido(Root<Produto> produto, AbstractQuery<?> consulta, CriteriaBuilder cb,
                                         LocalDate dia, Integer pontosAte) {
        Subquery<Long> cupons = consulta.subquery(Long.class);
        Root<Cupom> cupom = cupons.from(Cupom.class);
        List<Predicate> condicoes = new ArrayList<>(List.of(
                cb.equal(cupom.get("produto"), produto),
                cb.isTrue(cupom.get("ativo")),
                cb.greaterThanOrEqualTo(cupom.get("dataValidade"), dia)));
        if (pontosAte != null) {
            condicoes.add(cb.le(cupom.get("pontosNecessarios"), pontosAte));
        }
        cupons.select(cupom.get("id")).where(condicoes.toArray(Predicate[]::new));
        return cb.exists(cupons);
    }

    // Mesma empresa e mesma categoria do produto, com o dia entre o início e o fim (os dois inclusive)
    private static Predicate promocaoVigente(Root<Produto> produto, AbstractQuery<?> consulta, CriteriaBuilder cb,
                                             LocalDate dia) {
        Subquery<Long> promocoes = consulta.subquery(Long.class);
        Root<Promocao> promocao = promocoes.from(Promocao.class);
        promocoes.select(promocao.get("id")).where(
                cb.equal(promocao.get("empresaId"), produto.get("empresaId")),
                cb.equal(promocao.get("categoria"), produto.get("categoria")),
                cb.lessThanOrEqualTo(promocao.get("dataInicio"), dia),
                cb.greaterThanOrEqualTo(promocao.get("dataFim"), dia));
        // Igualdade com nulo já é falsa no SQL; explícito para a regra ficar à vista
        return cb.and(cb.isNotNull(produto.get("categoria")), cb.exists(promocoes));
    }

    private static Predicate empresaComNome(Root<Produto> produto, AbstractQuery<?> consulta, CriteriaBuilder cb,
                                            String trecho) {
        Subquery<Long> empresas = consulta.subquery(Long.class);
        Root<EmpresaParceira> empresa = empresas.from(EmpresaParceira.class);
        empresas.select(empresa.get("id")).where(cb.equal(empresa.get("id"), produto.get("empresaId")),
                Filtros.contem(cb, empresa.get("razaoSocial"), trecho));
        return cb.exists(empresas);
    }
}
