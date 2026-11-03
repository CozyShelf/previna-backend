package com.cozyshelf.previna.application.dto.request;

import com.cozyshelf.previna.domain.model.TipoTransacao;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// Filtros opcionais do extrato; o período inclui os dias inicial e final
public record ExtratoRequest(
        TipoTransacao tipo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
) {
    @AssertTrue(message = "A data inicial deve ser igual ou anterior à final")
    public boolean isPeriodoValido() {
        return inicio == null || fim == null || !inicio.isAfter(fim);
    }
}
