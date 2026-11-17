package com.brisastock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReporteVentaDiarioDTO(
        LocalDate fecha,
        String vendedor,
        long cantidadVentas,
        BigDecimal total) {
}
