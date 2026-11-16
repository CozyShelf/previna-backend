package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.NotNull;

// Ativa ou desativa um cupom ou uma promoção
public record AtivacaoRequest(@NotNull Boolean ativo) {}
