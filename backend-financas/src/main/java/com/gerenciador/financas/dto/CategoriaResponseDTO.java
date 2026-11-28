package com.gerenciador.financas.dto;

import com.gerenciador.financas.model.Categoria;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaResponseDTO {

    private Long id;
    private String nome;
    private boolean arquivada;
    // true só na "Sem categoria" do usuário (o front pode esconder os botões de arquivar e excluir).
    private boolean semCategoria;

    public static CategoriaResponseDTO fromEntity(Categoria categoria) {
        return CategoriaResponseDTO.builder()
                .id(categoria.getId())
                .nome(categoria.getNome())
                .arquivada(categoria.isArquivada())
                .semCategoria(categoria.isSemCategoria())
                .build();
    }
}
