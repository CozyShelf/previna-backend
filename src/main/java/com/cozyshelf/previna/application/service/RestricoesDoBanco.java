package com.cozyshelf.previna.application.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

// Traduz a violação de uma restrição do banco (UNIQUE, EXCLUDE) pelo nome dela, para quem a usa como regra de negócio
// responder com a mensagem própria em vez do 409 genérico do GlobalExceptionHandler
final class RestricoesDoBanco {
    private RestricoesDoBanco() {
    }

    // O nome vem do Hibernate, que o tira do texto do PostgreSQL. Com o servidor em outro idioma o extrator não
    // reconhece o texto, mas o nome entre aspas continua na mensagem; por isso as mensagens também são conferidas
    static boolean violou(DataIntegrityViolationException e, String restricao) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && restricao.equalsIgnoreCase(violacao.getConstraintName())) {
                return true;
            }
            if (causa.getMessage() != null && causa.getMessage().contains("\"" + restricao + "\"")) {
                return true;
            }
        }
        return false;
    }
}
