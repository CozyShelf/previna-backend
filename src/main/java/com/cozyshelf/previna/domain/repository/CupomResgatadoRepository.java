package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CupomResgatadoRepository extends JpaRepository<CupomResgatado, Long> {
    boolean existsByCodigo(String codigo);

    // Resgate já feito com a mesma chave de idempotência: o toque repetido no app
    Optional<CupomResgatado> findByGestanteIdAndChaveIdempotencia(Long gestanteId, UUID chaveIdempotencia);

    // Quais destas promoções a gestante já resgatou, numa consulta só para todas
    @Query("select r.promocao.id from CupomResgatado r where r.gestante.id = :gestante and r.promocao.id in :promocoes")
    Set<Long> promocoesResgatadas(@Param("gestante") Long idGestante, @Param("promocoes") Collection<Long> promocoes);

    // ---- Meus cupons (resgates da gestante) ----

    String ATIVO = "com.cozyshelf.previna.domain.model.StatusCupomResgatado.ATIVO";
    String EXPIRADO = "com.cozyshelf.previna.domain.model.StatusCupomResgatado.EXPIRADO";
    String PROMOCAO = "com.cozyshelf.previna.domain.model.TipoResgate.PROMOCAO";

    // Vencido e ainda ATIVO vira EXPIRADO, num UPDATE só e condicionado ao status: não lê antes para decidir, então
    // a validação do código na loja (também condicionada a ATIVO) nunca é sobrescrita. Duas listagens simultâneas não
    // se atrapalham: a segunda espera a trava de linha da primeira e reavalia o WHERE, que já não casa
    String EXPIRAR = "update CupomResgatado r set r.status = " + EXPIRADO
            + " where r.gestante.id = :gestante and r.status = " + ATIVO + " and r.dataValidade < :hoje";

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(EXPIRAR)
    int expirarVencidos(@Param("gestante") Long idGestante, @Param("hoje") LocalDate hoje);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(EXPIRAR + " and r.id = :id")
    int expirarSeVencido(@Param("id") Long id, @Param("gestante") Long idGestante, @Param("hoje") LocalDate hoje);

    // Categoria do cupom: a atual do produto (a cópia não a guarda); a da promoção: a copiada
    String DA_GESTANTE = " from CupomResgatado r left join Produto p on p.id = r.produtoId"
            + " where r.gestante.id = :gestante";
    String CATEGORIA = "case when r.tipo = " + PROMOCAO + " then r.categoria else p.categoria end";
    // Filtros opcionais (nulo não restringe). busca: o padrão '%trecho%' já em minúsculas, comparado sem acento com o
    // nome da empresa, o do produto (cupom) e o título (promoção), como a busca das ofertas (Filtros.contem)
    // (unaccent é a função da extensão da V6; os casts dizem ao Hibernate que ela recebe e devolve texto, senão a busca
    // nula iria ao banco como bytea)
    String BUSCA = "cast(function('unaccent', cast(:busca as String)) as String)";
    String FILTROS = " and (:tipo is null or r.tipo = :tipo)"
            + " and (:status is null or r.status = :status)"
            + " and (:categoria is null or " + CATEGORIA + " = :categoria)"
            + " and (:busca is null"
            + " or cast(function('unaccent', lower(r.empresaNome)) as String) like " + BUSCA
            + " or cast(function('unaccent', lower(r.produtoNome)) as String) like " + BUSCA
            + " or cast(function('unaccent', lower(r.titulo)) as String) like " + BUSCA + ")";
    // ATIVO primeiro; UTILIZADO e EXPIRADO juntos depois. Em cada grupo, do resgate mais recente; o id desempata, e
    // nenhum resgate aparece em duas páginas
    String ORDEM = " order by case when r.status = " + ATIVO + " then 0 else 1 end, r.dataResgate desc, r.id desc";

    // Uma consulta para a página (resgate e categoria do produto juntos, sem N+1) e uma para o total
    @Query(value = "select new com.cozyshelf.previna.domain.repository.ResgateDaGestante(r, p.categoria)"
            + DA_GESTANTE + FILTROS + ORDEM,
            countQuery = "select count(r)" + DA_GESTANTE + FILTROS)
    Page<ResgateDaGestante> daGestante(@Param("gestante") Long idGestante, @Param("tipo") TipoResgate tipo,
                                       @Param("status") StatusCupomResgatado status,
                                       @Param("categoria") Categoria categoria, @Param("busca") String busca,
                                       Pageable pagina);

    // De outra gestante não volta: responde como inexistente
    @Query("select new com.cozyshelf.previna.domain.repository.ResgateDaGestante(r, p.categoria)"
            + DA_GESTANTE + " and r.id = :id")
    Optional<ResgateDaGestante> daGestante(@Param("id") Long id, @Param("gestante") Long idGestante);
}
