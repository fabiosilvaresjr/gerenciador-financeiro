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

    // Sem cascade/orphanRemoval: excluir uma categoria nunca pode apagar as transações dela.
    // A regra de negócio (409 se houver transações) fica no CategoriaService.
    @OneToMany(mappedBy = "categoria")
    @Builder.Default
    private List<Transacao> transacoes = new ArrayList<>();
}
