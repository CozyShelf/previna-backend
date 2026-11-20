package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CupomResgatado;

// Resgate lido pela empresa na validação do código, com a categoria atual do produto (só no cupom, como em
// ResgateDaGestante) e o nome da gestante, na mesma consulta
public record ResgateDaEmpresa(CupomResgatado resgate, Categoria categoriaDoProduto, String gestanteNome) {}
