package com.brisastock.service;

import com.brisastock.dto.ReporteVentaDiarioDTO;
import com.brisastock.exception.SolicitudInvalidaException;
import com.brisastock.model.EstadoVenta;
import com.brisastock.model.Producto;
import com.brisastock.model.Venta;
import com.brisastock.repository.ProductoRepository;
import com.brisastock.repository.VentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ReporteService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private VentaRepository ventaRepository;

    // Stock bajo: stock <= stockMinimo de cada producto (sin valores fijos)
    public List<Producto> obtenerStockBajo() {

        return productoRepository.findConStockBajo();
    }

    // Agotados
    public List<Producto> obtenerAgotados() {

        // Por stock real (<= 0), no por el texto de "estado"
        return productoRepository.findAgotados();
    }

    // Vencidos: marcados manualmente, con estado heredado "Vencido" o con fecha
    // válida anterior a hoy. Las fechas con formato antiguo no se consideran vencidas
    // (se filtra en memoria para no depender del formato guardado en la BD).
    public List<Producto> obtenerVencidos() {

        return productoRepository.findAll().stream()
                .filter(Producto::estaActivo)
                .filter(Producto::estaVencidoEfectivo)
                .toList();
    }

    /** Resume las ventas vigentes por día y por vendedor, con fechas inclusivas. */
    @Transactional(readOnly = true)
    public List<ReporteVentaDiarioDTO> obtenerVentasPorDiaYVendedor(
            LocalDate desde, LocalDate hasta) {

        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new SolicitudInvalidaException(
                    "La fecha final no puede ser anterior a la fecha inicial");
        }

        LocalDateTime inicio = desde == null ? null : desde.atStartOfDay();
        LocalDateTime finExclusivo = hasta == null ? null : hasta.plusDays(1).atStartOfDay();
        List<Venta> ventas;
        if (inicio != null && finExclusivo != null) {
            ventas = ventaRepository.findByFechaVentaGreaterThanEqualAndFechaVentaLessThan(
                    inicio, finExclusivo);
        } else if (inicio != null) {
            ventas = ventaRepository.findByFechaVentaGreaterThanEqual(inicio);
        } else if (finExclusivo != null) {
            ventas = ventaRepository.findByFechaVentaLessThan(finExclusivo);
        } else {
            ventas = ventaRepository.findAll();
        }

        Map<String, ResumenVenta> resumenes = new TreeMap<>();
        for (Venta venta : ventas) {
            if (venta.getEstado() == EstadoVenta.CANCELADA || venta.getFechaVenta() == null
                    || venta.getUsuario() == null || venta.getTotal() == null) {
                continue;
            }

            LocalDate fecha = venta.getFechaVenta().toLocalDate();
            if ((desde != null && fecha.isBefore(desde))
                    || (hasta != null && fecha.isAfter(hasta))) {
                continue;
            }

            String vendedor = venta.getUsuario().getUsername();
            String clave = fecha + "\u0000" + vendedor;
            resumenes.computeIfAbsent(clave, ignorado -> new ResumenVenta(fecha, vendedor))
                    .agregar(venta.getTotal());
        }

        return resumenes.values().stream()
                .map(ResumenVenta::aDto)
                .sorted(Comparator.comparing(ReporteVentaDiarioDTO::fecha)
                        .thenComparing(ReporteVentaDiarioDTO::vendedor))
                .toList();
    }

    private static final class ResumenVenta {
        private final LocalDate fecha;
        private final String vendedor;
        private long cantidad;
        private BigDecimal total = BigDecimal.ZERO;

        private ResumenVenta(LocalDate fecha, String vendedor) {
            this.fecha = fecha;
            this.vendedor = vendedor;
        }

        private void agregar(Double valor) {
            cantidad++;
            total = total.add(BigDecimal.valueOf(valor));
        }

        private ReporteVentaDiarioDTO aDto() {
            return new ReporteVentaDiarioDTO(fecha, vendedor, cantidad, total);
        }
    }
}
