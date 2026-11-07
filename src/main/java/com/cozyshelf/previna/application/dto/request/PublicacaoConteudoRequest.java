package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.NotNull;

public record PublicacaoConteudoRequest(@NotNull Boolean publicado) {}
