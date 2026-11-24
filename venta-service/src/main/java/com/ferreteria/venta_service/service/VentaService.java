package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.model.Venta;
import com.ferreteria.venta_service.repository.VentaRepository;
import com.ferreteria.venta_service.exception.ResourceNotFoundException;
import com.ferreteria.venta_service.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VentaService {

    // Declarar el Logger
    private static final Logger logger = LoggerFactory.getLogger(VentaService.class);

    private final VentaRepository ventaRepository;

    // Método para obtener todas las ventas
    public List<Venta> obtenerTodas() {
        logger.info("Listando todas las ventas desde la base de datos");
        return ventaRepository.findAll();
    }

    // Método para obtener ventas por ID de usuario
    public List<Venta> obtenerPorUsuario(Long usuarioId) {
        logger.info("Buscando ventas asociadas al usuario ID: {}", usuarioId);
        return ventaRepository.findByUsuarioId(usuarioId);
    }

    // Método para obtener una venta por ID
    public Venta obtenerPorId(Long id) {
        logger.info("Buscando venta con ID: {}", id);
        return ventaRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("No se encontró ninguna venta con el ID: {}", id);
                    return new ResourceNotFoundException("Venta no encontrada con ID: " + id);
                });
    }

    // Método para obtener ventas por rango de fechas
    public List<Venta> obtenerPorRangoFechas(java.time.LocalDate inicio, java.time.LocalDate fin) {
        logger.info("Procesando rango de fechas: {} al {}", inicio, fin);

        if (inicio.isAfter(fin)) {
            throw new BadRequestException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }

        // Convertir LocalDate a LocalDateTime para la consulta en la BD
        java.time.LocalDateTime fechaInicioCompleta = inicio.atStartOfDay(); // 00:00:00
        java.time.LocalDateTime fechaFinCompleta = fin.atTime(java.time.LocalTime.MAX); // 23:59:59.999

        logger.debug("Buscando en BD entre {} y {}", fechaInicioCompleta, fechaFinCompleta);

        return ventaRepository.findByFechaRango(fechaInicioCompleta, fechaFinCompleta);
    }
}