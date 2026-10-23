package com.cozyshelf.previna.domain.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

// Filtros opcionais das listagens: sem valor, não restringem nada
public final class Filtros {
    private static final String SO_NUMEROS = "[\\d.\\-/\\s]+";

    private Filtros() {
    }

    // Trecho de algum dos campos, sem diferenciar maiúsculas nem acentos; termo só de números também procura no documento
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
            if (limpo.matches(SO_NUMEROS) && !digitos.isEmpty()) {
                alternativas.add(cb.like(raiz.get(campoDocumento), "%" + digitos + "%"));
            }
            return cb.or(alternativas.toArray(Predicate[]::new));
        };
    }

    public static <T> Specification<T> igual(String campo, Object valor) {
        return (raiz, consulta, cb) -> valor == null ? cb.conjunction() : cb.equal(raiz.get(campo), valor);
    }

    // Função da extensão unaccent (migration V6)
    private static Expression<String> semAcento(CriteriaBuilder cb, Expression<String> texto) {
        return cb.function("unaccent", String.class, texto);
    }
}
