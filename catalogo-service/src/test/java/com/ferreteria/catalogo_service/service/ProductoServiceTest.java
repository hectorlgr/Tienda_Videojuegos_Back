package com.ferreteria.catalogo_service.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.ferreteria.catalogo_service.model.Producto;
import com.ferreteria.catalogo_service.repository.ProductoRepository;
import com.ferreteria.catalogo_service.exception.BadRequestException;
import com.ferreteria.catalogo_service.exception.ResourceNotFoundException;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private ProductoService productoService;

    private Producto productoBase;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(webClientBuilder.build()).thenReturn(webClient);
        lenient().when(webClient.put()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        productoBase = new Producto();
        productoBase.setId(1L);
        productoBase.setNombre("Martillo");
        productoBase.setDescripcion("Herramienta");
        productoBase.setMarca("Stanley");
        productoBase.setPrecio(15000);
        productoBase.setHabilitado(true);
    }

    @Test
    void testObtenerTodos_Exito() {
        when(productoRepository.findByHabilitadoTrue()).thenReturn(List.of(productoBase));
        List<Producto> resultado = productoService.obtenerTodos();
        assertEquals(1, resultado.size());
        verify(productoRepository, times(1)).findByHabilitadoTrue();
    }

    @Test
    void testObtenerPorId_Exito() {
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(productoBase));
        Producto resultado = productoService.obtenerPorId(1L);
        assertNotNull(resultado);
        assertEquals("Martillo", resultado.getNombre());
    }

    @Test
    void testObtenerPorId_NoEncontrado_LanzaExcepcion() {
        when(productoRepository.findByIdAndHabilitadoTrue(99L)).thenReturn(Optional.empty());
        ResourceNotFoundException excepcion = assertThrows(ResourceNotFoundException.class, () -> {
            productoService.obtenerPorId(99L);
        });
        assertEquals("Error: Producto no encontrado con el ID 99", excepcion.getMessage());
    }

    @Test
    void testGuardarProducto_HabilitaPorDefecto() {
        when(productoRepository.save(any(Producto.class))).thenReturn(productoBase);
        Producto resultado = productoService.guardarProducto(productoBase);
        assertTrue(resultado.getHabilitado());
        verify(productoRepository, times(1)).save(productoBase);
    }

    @Test
    void testEliminarProducto_DeshabilitaYNotificaInventario() {
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(productoBase));
        when(productoRepository.save(any(Producto.class))).thenReturn(productoBase);

        productoService.eliminarProducto(1L);

        assertFalse(productoBase.getHabilitado());
        verify(productoRepository, times(1)).save(productoBase);
        verify(webClientBuilder, times(1)).build(); // Verifica que se llamó a inventario
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEliminarProducto_FalloInventario_LanzaExcepcion() {
        when(productoRepository.findByIdAndHabilitadoTrue(1L)).thenReturn(Optional.of(productoBase));
        when(productoRepository.save(any(Producto.class))).thenReturn(productoBase);

        // Simulamos que el WebClient falla
        when(responseSpec.bodyToMono(Void.class)).thenThrow(new RuntimeException("Servicio de inventario caído"));

        assertThrows(BadRequestException.class, () -> {
            productoService.eliminarProducto(1L);
        });
    }

    @Test
    void testMarcarComoAgotado_Exito_DeshabilitaProducto() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(productoBase));
        when(productoRepository.save(any(Producto.class))).thenReturn(productoBase);

        productoService.marcarComoAgotado(1L);

        assertFalse(productoBase.getHabilitado());
        verify(productoRepository, times(1)).save(productoBase);
    }

    @Test
    void testMarcarComoAgotado_YaEstabaDeshabilitado_NoHaceNada() {
        productoBase.setHabilitado(false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(productoBase));

        productoService.marcarComoAgotado(1L);

        // Como ya estaba en false, NUNCA debe llamar a save()
        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void testMarcarComoAgotado_NoEncontrado_LanzaExcepcion() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> productoService.marcarComoAgotado(99L));
    }

    @Test
    void testHabilitarProducto_Exito_RehabilitaProducto() {
        productoBase.setHabilitado(false); // Viene deshabilitado
        when(productoRepository.findById(1L)).thenReturn(Optional.of(productoBase));
        when(productoRepository.save(any(Producto.class))).thenReturn(productoBase);

        productoService.habilitarProducto(1L);

        assertTrue(productoBase.getHabilitado());
        verify(productoRepository, times(1)).save(productoBase);
    }

    @Test
    void testHabilitarProducto_YaHabilitado_NoHaceNada() {
        // Ya está habilitado = true
        when(productoRepository.findById(1L)).thenReturn(Optional.of(productoBase));

        productoService.habilitarProducto(1L);

        // Como ya estaba true, NUNCA debe llamar a save() por el if/else
        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void testHabilitarProducto_NoEncontrado_LanzaExcepcion() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> productoService.habilitarProducto(99L));
    }
}