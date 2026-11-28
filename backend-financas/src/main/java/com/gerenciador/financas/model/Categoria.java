package com.gerenciador.financas.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    // Dono da categoria. Cada usuário só enxerga e altera as próprias categorias.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Arquivada: some da listagem padrão e não pode ser usada em transações novas.
    // As transações antigas continuam com ela.
    @Column(nullable = false)
    @Builder.Default
    private boolean arquivada = false;

    // A "Sem categoria" do usuário (uma por usuário): recebe as transações criadas sem categoria.
    // Não pode ser arquivada, renomeada nem excluída.
    @Column(name = "sem_categoria", nullable = false)
    @Builder.Default
    private boolean semCategoria = false;

    // Sem cascade/orphanRemoval: excluir uma categoria nunca pode apagar as transações dela.
    // A regra de negócio (409 se houver transações) fica no CategoriaService.
    @OneToMany(mappedBy = "categoria")
    @Builder.Default
    private List<Transacao> transacoes = new ArrayList<>();
}
