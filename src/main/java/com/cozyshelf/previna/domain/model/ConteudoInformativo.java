package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Conteúdo educativo cadastrado pelo admin. Nasce rascunho; só aparece no app depois de publicado.
// Despublicar tira o conteúdo do app sem apagá-lo.
@Entity
@Table(name = "conteudo_informativo")
public class ConteudoInformativo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long autorId;
    private String titulo;

    @Enumerated(EnumType.STRING)
    private CategoriaConteudo categoria;

    @Enumerated(EnumType.STRING)
    private TipoConteudo tipo;

    private String resumo;
    private String corpo;

    // Caminho da própria API, quando a foto foi enviada, ou endereço externo (vídeo, link)
    private String midiaUrl;

    private boolean publicado;
    private LocalDateTime criadoEm;
    private LocalDateTime publicadoEm;

    protected ConteudoInformativo() {
    }

    public ConteudoInformativo(Long autorId, String titulo, CategoriaConteudo categoria, TipoConteudo tipo,
                               String resumo, String corpo, String midiaUrl) {
        this.autorId = autorId;
        this.titulo = titulo;
        this.categoria = categoria;
        this.tipo = tipo;
        this.resumo = resumo;
        this.corpo = corpo;
        this.midiaUrl = midiaUrl;
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getTitulo() {
        return titulo;
    }

    public CategoriaConteudo getCategoria() {
        return categoria;
    }

    public TipoConteudo getTipo() {
        return tipo;
    }

    public String getResumo() {
        return resumo;
    }

    public String getCorpo() {
        return corpo;
    }

    public String getMidiaUrl() {
        return midiaUrl;
    }

    public boolean isPublicado() {
        return publicado;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getPublicadoEm() {
        return publicadoEm;
    }

    public void atualizar(String titulo, CategoriaConteudo categoria, TipoConteudo tipo, String resumo,
                          String corpo, String midiaUrl) {
        this.titulo = titulo;
        this.categoria = categoria;
        this.tipo = tipo;
        this.resumo = resumo;
        this.corpo = corpo;
        this.midiaUrl = midiaUrl;
    }

    public void alterarMidia(String midiaUrl) {
        this.midiaUrl = midiaUrl;
    }

    // Publicar grava a data; despublicar a limpa, para o conteúdo voltar a ser um rascunho.
    // Republicar depois conta como publicação nova: é a data que a gestante vê.
    public void alterarPublicacao(boolean publicado) {
        this.publicado = publicado;
        this.publicadoEm = publicado ? LocalDateTime.now() : null;
    }
}
