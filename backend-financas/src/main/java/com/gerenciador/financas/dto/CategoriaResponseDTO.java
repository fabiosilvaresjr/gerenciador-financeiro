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

    public static CategoriaResponseDTO fromEntity(Categoria categoria) {
        return CategoriaResponseDTO.builder()
                .id(categoria.getId())
                .nome(categoria.getNome())
                .build();
    }
}
