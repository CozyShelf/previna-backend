package com.cozyshelf.previna.domain.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Filtros opcionais das listagens: sem valor, não restringem nada
public final class Filtros {
    private static final String SO_NUMEROS = "[\\d.\\-/\\s]+";

    private Filtros() {
    }

    // Trecho de algum dos campos, sem diferenciar maiúsculas nem acentos; termo só de números também procura no documento, quando há
    // shortcut: like '%termo%' percorre a tabela inteira; criar índice trigram (pg_trgm) quando passar de dezenas de milhares de registros
    public static <T> Specification<T> busca(String termo, String campoDocumento, String... camposTexto) {
        return (raiz, consulta, cb) -> {
            if (termo == null || termo.isBlank()) {
                return cb.conjunction();
            }
            String limpo = termo.trim();
            Expression<String> padrao = semAcento(cb, cb.literal("%" + limpo.toLowerCase() + "%"));
            List<Predicate> alternativas = new ArrayList<>();
            for (String campo : camposTexto) {
                alternativas.add(cb.like(semAcento(cb, cb.lower(raiz.get(campo))), padrao));
            }
            String digitos = limpo.replaceAll("\\D", "");
            if (campoDocumento != null && limpo.matches(SO_NUMEROS) && !digitos.isEmpty()) {
                alternativas.add(cb.like(raiz.get(campoDocumento), "%" + digitos + "%"));
            }
            return cb.or(alternativas.toArray(Predicate[]::new));
        };
    }

    public static <T> Specification<T> igual(String campo, Object valor) {
        return (raiz, consulta, cb) -> valor == null ? cb.conjunction() : cb.equal(raiz.get(campo), valor);
    }

    // Data e hora entre os dias inicial e final, os dois inteiros
    // shortcut: os dias são contados no fuso da JVM (UTC no container), até 3 h de diferença de Brasília nas bordas; converter do fuso da gestante se o filtro precisar ser exato na virada do dia
    public static <T> Specification<T> noPeriodo(String campo, LocalDate inicio, LocalDate fim) {
        return (raiz, consulta, cb) -> {
            List<Predicate> limites = new ArrayList<>();
            if (inicio != null) {
                limites.add(cb.greaterThanOrEqualTo(raiz.get(campo), inicio.atStartOfDay()));
            }
            if (fim != null) {
                limites.add(cb.lessThan(raiz.get(campo), fim.plusDays(1).atStartOfDay()));
            }
            return cb.and(limites.toArray(Predicate[]::new));
        };
    }

    // Função da extensão unaccent (migration V6)
    private static Expression<String> semAcento(CriteriaBuilder cb, Expression<String> texto) {
        return cb.function("unaccent", String.class, texto);
    }
}
