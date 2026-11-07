package com.cozyshelf.previna.application.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.mockito.invocation.Invocation;
import org.springframework.data.jpa.domain.Specification;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

// Aplica um filtro (Specification) a um CriteriaBuilder simulado e anota o que ele pediria ao banco: as igualdades
// (campo = valor) e as buscas (campo like padrão). Prova, sem banco, que o serviço montou o filtro certo; se o SQL
// gerado funciona de fato é com o FiltrosIntegracaoTest.
final class FiltroAplicado {
    final Map<String, Object> igualdades = new LinkedHashMap<>();
    final Map<String, String> buscas = new LinkedHashMap<>();

    private FiltroAplicado() {
    }

    @SuppressWarnings("unchecked")
    static <T> FiltroAplicado de(Specification<T> filtro) {
        // Cada caminho (raiz.get("nome")) vira um mock com o nome do campo, para ser reconhecido depois
        Root<T> raiz = mock(Root.class);
        when(raiz.get(anyString())).thenAnswer(i -> mock(Path.class, (String) i.getArgument(0)));
        // lower() e unaccent() devolvem o próprio argumento: o like fica com o caminho do campo à esquerda
        CriteriaBuilder cb = mock(CriteriaBuilder.class, invocacao -> switch (invocacao.getMethod().getName()) {
            case "lower" -> invocacao.getArgument(0);
            case "function" -> invocacao.getArgument(2);
            case "literal" -> mock(Expression.class, String.valueOf((Object) invocacao.getArgument(0)));
            default -> RETURNS_DEFAULTS.answer(invocacao);
        });

        filtro.toPredicate(raiz, mock(CriteriaQuery.class), cb);

        FiltroAplicado aplicado = new FiltroAplicado();
        for (Invocation i : mockingDetails(cb).getInvocations()) {
            switch (i.getMethod().getName()) {
                case "equal" -> aplicado.igualdades.put(i.getArgument(0).toString(), i.getArgument(1));
                case "like" -> aplicado.buscas.put(i.getArgument(0).toString(), i.getArgument(1).toString());
                default -> {
                }
            }
        }
        return aplicado;
    }
}
