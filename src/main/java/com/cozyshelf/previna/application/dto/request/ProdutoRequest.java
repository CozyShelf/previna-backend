package com.cozyshelf.previna.application.dto.request;

import com.cozyshelf.previna.domain.model.Categoria;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// Chega em multipart, junto com a imagem; só o nome é obrigatório. preco é o preço normal, sem cupom nem promoção
public record ProdutoRequest(
        @NotBlank @Size(max = 150) String nome,
        @Size(max = 1000) String descricao,
        Categoria categoria,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal preco
) {}
