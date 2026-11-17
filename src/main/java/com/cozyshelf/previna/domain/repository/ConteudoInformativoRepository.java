package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.ConteudoInformativo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConteudoInformativoRepository
        extends JpaRepository<ConteudoInformativo, Long>, JpaSpecificationExecutor<ConteudoInformativo> {

    // SELECT ... FOR UPDATE para quem altera ou exclui: editar e publicar/despublicar ao mesmo tempo esperam um pelo
    // outro. Sem a trava, a edição gravava o publicado que leu por cima da publicação comitada no meio
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConteudoInformativo c where c.id = :id")
    Optional<ConteudoInformativo> findByIdParaAtualizar(@Param("id") Long id);

    // Rascunho responde como inexistente para a gestante
    Optional<ConteudoInformativo> findByIdAndPublicadoTrue(Long id);

    // Ordem aleatória semeada pelo cliente: muda a cada carregamento do app, mas é estável dentro do
    // mesmo carregamento. order by random() sorteia de novo a cada página e faria um conteúdo aparecer
    // em duas páginas e outro em nenhuma. O id desempata o md5.
    @Query(value = """
            SELECT * FROM conteudo_informativo
            WHERE publicado = TRUE
            ORDER BY md5(id || CAST(:semente AS TEXT)), id
            """,
            countQuery = "SELECT count(*) FROM conteudo_informativo WHERE publicado = TRUE",
            nativeQuery = true)
    Page<ConteudoInformativo> publicadosEmOrdemAleatoria(@Param("semente") String semente, Pageable pagina);
}
