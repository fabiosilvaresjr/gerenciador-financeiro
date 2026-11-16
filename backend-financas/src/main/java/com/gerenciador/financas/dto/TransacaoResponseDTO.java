package com.gerenciador.financas.dto;

import com.gerenciador.financas.model.TipoTransacao;
import com.gerenciador.financas.model.Transacao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransacaoResponseDTO {

    private Long id;
    private String descricao;
    private BigDecimal valor;
    private LocalDate data;
    private TipoTransacao tipo;
    private CategoriaResponseDTO categoria;

    public static TransacaoResponseDTO fromEntity(Transacao transacao) {
        return TransacaoResponseDTO.builder()
                .id(transacao.getId())
                .descricao(transacao.getDescricao())
                .valor(transacao.getValor())
                .data(transacao.getData())
                .tipo(transacao.getTipo())
                .categoria(transacao.getCategoria() != null ? CategoriaResponseDTO.fromEntity(transacao.getCategoria()) : null)
                .build();
    }
}
