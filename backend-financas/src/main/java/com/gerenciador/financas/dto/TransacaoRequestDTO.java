package com.gerenciador.financas.dto;

import com.gerenciador.financas.model.TipoTransacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class TransacaoRequestDTO {

    @NotBlank(message = "A descrição da transação é obrigatória")
    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
    private String descricao;

    @NotNull(message = "O valor da transação é obrigatório")
    @Positive(message = "O valor da transação deve ser positivo")
    private BigDecimal valor;

    @NotNull(message = "A data da transação é obrigatória")
    private LocalDate data;

    @NotNull(message = "O tipo da transação é obrigatório (RECEITA ou DESPESA)")
    private TipoTransacao tipo;

    @NotNull(message = "O ID da categoria é obrigatório")
    private Long categoriaId;
}
