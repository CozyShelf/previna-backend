package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CupomResgatado;

// Resgate com a categoria atual do produto (só no cupom; nula na promoção ou no produto sem categoria), lidos na
// mesma consulta. A cópia do cupom no resgate não guarda a categoria, então ela vem do produto por join.
public record ResgateDaGestante(CupomResgatado resgate, Categoria categoriaDoProduto) {}
