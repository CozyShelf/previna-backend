package com.cozyshelf.previna.application.dto.request;

import com.cozyshelf.previna.domain.model.Escolaridade;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PrimeiroAcessoRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,50}") String novoLogin,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d).*") String novaSenha,
        @AssertTrue boolean consentimentoLgpd,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal rendaFamiliar,
        @Min(1) @Max(50) Integer pessoasDomicilio,
        Escolaridade escolaridade,
        Boolean beneficiariaProgramaSocial
) {}
