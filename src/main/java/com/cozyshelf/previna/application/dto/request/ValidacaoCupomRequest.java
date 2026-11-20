package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.NotBlank;

// Código que a gestante mostrou na loja, digitado ou lido do QR Code pelo portal. Vale para cupom e promoção.
// Espaços nas pontas e minúsculas são aceitos: o serviço normaliza antes de procurar
public record ValidacaoCupomRequest(
        @NotBlank String codigo
) {}
