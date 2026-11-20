package com.ferreteria.catalogo_service.Dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MovimientoStockRequestDto(@NotNull @Positive Integer cantidad) {
}
