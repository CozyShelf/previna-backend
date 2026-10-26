package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// Vale para um produto da empresa (conferido no serviço); desconto é percentual
public record CupomRequest(
        @NotNull Long produto,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal desconto,
        @NotNull @FutureOrPresent LocalDate dataValidade,
        @NotNull @Positive Integer pontosNecessarios
) {}
