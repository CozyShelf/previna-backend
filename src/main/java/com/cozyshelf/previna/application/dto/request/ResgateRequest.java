package com.cozyshelf.previna.application.dto.request;

import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;

// Filtros de "meus cupons" (os resgates da gestante do token), todos opcionais, com os nomes dos de /ofertas.
// busca: trecho do nome da empresa, do produto (cupom) ou do título (promoção). A ordem é fixa no servidor.
public record ResgateRequest(
        @Size(max = 100) String busca,
        Categoria categoria,
        TipoResgate tipo,
        StatusCupomResgatado status,
        @PositiveOrZero Integer pagina,
        @Min(1) @Max(100) Integer tamanho
) {
    // Página e tamanho padrão das listagens; sem Sort, porque a consulta já traz o ORDER BY (ATIVO primeiro)
    public Pageable paginacao() {
        return new ListagemRequest(busca, pagina, tamanho).semOrdem();
    }

    // Padrão do LIKE em minúsculas, ou nulo sem busca (em branco também não restringe)
    public String padraoBusca() {
        return busca == null || busca.isBlank() ? null : "%" + busca.trim().toLowerCase() + "%";
    }
}
