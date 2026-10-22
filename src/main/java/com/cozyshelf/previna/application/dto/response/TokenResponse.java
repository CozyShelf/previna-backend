package com.cozyshelf.previna.application.dto.response;

public record TokenResponse(
    String token,
    boolean primeiroAcesso,
    boolean tutorialPendente
) {}
