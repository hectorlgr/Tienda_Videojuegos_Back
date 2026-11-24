package com.ferreteria.venta_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ferreteria.venta_service.exception.BadRequestException;
import com.ferreteria.venta_service.exception.ResourceNotFoundException;
import com.ferreteria.venta_service.model.Venta;
import com.ferreteria.venta_service.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
public class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;

    @InjectMocks
    private VentaService ventaService;

    private Venta ventaBase;

    @BeforeEach
    void setUp() {
        // Objeto de prueba
        ventaBase = new Venta();
        ventaBase.setId(1L);
        ventaBase.setUsuarioId(10L);
    }

    @Test
    void testObtenerTodas_RetornaListaVentas() {
        when(ventaRepository.findAll()).thenReturn(List.of(ventaBase));
        List<Venta> resultado = ventaService.obtenerTodas();
        assertEquals(1, resultado.size());
        verify(ventaRepository, times(1)).findAll();
    }

    @Test
    void testObtenerPorUsuario_RetornaListaVentas() {
        when(ventaRepository.findByUsuarioId(10L)).thenReturn(List.of(ventaBase));
        List<Venta> resultado = ventaService.obtenerPorUsuario(10L);
        assertEquals(1, resultado.size());
    }

    @Test
    void testObtenerPorId_Exito_RetornaVenta() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaBase));
        Venta resultado = ventaService.obtenerPorId(1L);
        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
    }

    @Test
    void testObtenerPorId_NoEncontrado_LanzaExcepcion() {
        when(ventaRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> ventaService.obtenerPorId(99L));
    }

    @Test
    void testObtenerPorRangoFechas_Exito() {
        LocalDate inicio = LocalDate.of(2024, 1, 1);
        LocalDate fin = LocalDate.of(2024, 1, 31);
        when(ventaRepository.findByFechaRango(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(ventaBase));

        List<Venta> resultado = ventaService.obtenerPorRangoFechas(inicio, fin);
        assertEquals(1, resultado.size());
    }

    @Test
    void testObtenerPorRangoFechas_FalloFechasInvertidas_LanzaExcepcion() {
        LocalDate inicio = LocalDate.of(2024, 2, 1);
        LocalDate fin = LocalDate.of(2024, 1, 31);
        assertThrows(BadRequestException.class, () -> ventaService.obtenerPorRangoFechas(inicio, fin));
    }
}