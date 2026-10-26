package com.cozyshelf.previna.application.dto.request;

import com.cozyshelf.previna.domain.model.Categoria;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// desconto é percentual e vale para os produtos da categoria; o fim igual ou depois do início é conferido no serviço
public record PromocaoRequest(
        @NotBlank @Size(max = 150) String titulo,
        @Size(max = 1000) String descricao,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal desconto,
        @NotNull LocalDate dataInicio,
        @NotNull @FutureOrPresent LocalDate dataFim,
        @NotNull Categoria categoria
) {}
