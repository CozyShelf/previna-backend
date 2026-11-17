package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// Cupom trocado pela gestante ou promoção resgatada por ela: os dois têm código, status e validade iguais, então ficam
// na mesma tabela (tipo diz qual é). Guarda uma cópia do que foi resgatado (produto ou título e categoria, empresa,
// desconto, validade e pontos): a empresa pode editar o cupom, a promoção ou o produto depois sem mudar o resgate.
// O CHECK cupom_resgatado_tipo_coerente (V22) amarra quais colunas cada tipo preenche.
@Entity
@Table(name = "cupom_resgatado")
public class CupomResgatado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TipoResgate tipo;

    // De onde o resgate veio (só um dos dois, conforme o tipo); o que se mostra à gestante é a cópia abaixo, e não o
    // cupom ou a promoção como estão hoje
    @ManyToOne(fetch = FetchType.LAZY)
    private Cupom cupom;

    @ManyToOne(fetch = FetchType.LAZY)
    private Promocao promocao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestante_id")
    private Gestante gestante;

    private String codigo;

    @Enumerated(EnumType.STRING)
    private StatusCupomResgatado status;

    private LocalDateTime dataResgate;
    private LocalDateTime dataUso;

    // Chave que o app manda a cada confirmação de resgate (header Idempotency-Key); nula nos resgates de versões
    // antigas do app. Única por gestante: o toque repetido devolve este resgate em vez de fazer outro
    private UUID chaveIdempotencia;

    // ---- Cópia no momento do resgate ----
    // Só do cupom; nulos na promoção, que vale para a categoria inteira e não custa pontos
    private Long produtoId;
    private String produtoNome;
    private Integer pontosGastos;
    // Só da promoção
    private String titulo;
    @Enumerated(EnumType.STRING)
    private Categoria categoria;
    // Dos dois
    private Long empresaId;
    private String empresaNome;
    // Percentual sobre o preço
    private BigDecimal desconto;
    // No cupom, a validade dele; na promoção, a data de fim
    private LocalDate dataValidade;

    protected CupomResgatado() {
    }

    public CupomResgatado(Cupom cupom, EmpresaParceira empresa, Gestante gestante, String codigo,
                          UUID chaveIdempotencia) {
        this(TipoResgate.CUPOM, empresa, gestante, codigo, chaveIdempotencia);
        this.cupom = cupom;
        this.produtoId = cupom.getProduto().getId();
        this.produtoNome = cupom.getProduto().getNome();
        this.empresaId = empresa.getId();
        this.empresaNome = empresa.getRazaoSocial();
        this.desconto = cupom.getDesconto();
        this.dataValidade = cupom.getDataValidade();
        this.pontosGastos = cupom.getPontosNecessarios();
    }

    // Resgate de promoção: sem produto e sem pontos; vale até o fim da promoção
    public CupomResgatado(Promocao promocao, EmpresaParceira empresa, Gestante gestante, String codigo,
                          UUID chaveIdempotencia) {
        this(TipoResgate.PROMOCAO, empresa, gestante, codigo, chaveIdempotencia);
        this.promocao = promocao;
        this.titulo = promocao.getTitulo();
        this.categoria = promocao.getCategoria();
        this.desconto = promocao.getDesconto();
        this.dataValidade = promocao.getDataFim();
    }

    private CupomResgatado(TipoResgate tipo, EmpresaParceira empresa, Gestante gestante, String codigo,
                           UUID chaveIdempotencia) {
        this.tipo = tipo;
        this.gestante = gestante;
        this.codigo = codigo;
        this.chaveIdempotencia = chaveIdempotencia;
        this.status = StatusCupomResgatado.ATIVO;
        this.dataResgate = LocalDateTime.now();
        this.empresaId = empresa.getId();
        this.empresaNome = empresa.getRazaoSocial();
    }

    public Long getId() {
        return id;
    }

    public TipoResgate getTipo() {
        return tipo;
    }

    public Cupom getCupom() {
        return cupom;
    }

    public Promocao getPromocao() {
        return promocao;
    }

    public Gestante getGestante() {
        return gestante;
    }

    public String getCodigo() {
        return codigo;
    }

    public StatusCupomResgatado getStatus() {
        return status;
    }

    public LocalDateTime getDataResgate() {
        return dataResgate;
    }

    public LocalDateTime getDataUso() {
        return dataUso;
    }

    public UUID getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    public String getProdutoNome() {
        return produtoNome;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public String getEmpresaNome() {
        return empresaNome;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public LocalDate getDataValidade() {
        return dataValidade;
    }

    // Nulo na promoção
    public Integer getPontosGastos() {
        return pontosGastos;
    }

    public String getTitulo() {
        return titulo;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    // A mesma frase do cupom, com os dados do resgate: "10% de desconto em Fralda RN" no cupom e
    // "15% de desconto em Fraldas" na promoção
    public String getDescricao() {
        return Cupom.descricao(desconto, tipo == TipoResgate.PROMOCAO ? categoria.getNome() : produtoNome);
    }
}
