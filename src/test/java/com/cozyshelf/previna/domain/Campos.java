package com.cozyshelf.previna.domain;

import java.lang.reflect.Field;

// Utilitário só de teste: grava campos que a aplicação não define (o id, que vem do banco, e o fator de
// ponderação, hoje fixo em 1,00 no modelo). Mantém os testes sem precisar de banco nem de setter em produção.
public final class Campos {
    private Campos() {
    }

    public static <T> T definir(T alvo, String campo, Object valor) {
        for (Class<?> classe = alvo.getClass(); classe != null; classe = classe.getSuperclass()) {
            try {
                Field encontrado = classe.getDeclaredField(campo);
                encontrado.setAccessible(true);
                encontrado.set(alvo, valor);
                return alvo;
            } catch (NoSuchFieldException procuraNaSuperclasse) {
                // segue para a superclasse: id fica em Usuario, não em Gestante
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Campo inexistente: " + campo);
    }
}
