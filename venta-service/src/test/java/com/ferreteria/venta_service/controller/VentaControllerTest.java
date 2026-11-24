package com.ferreteria.venta_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ferreteria.venta_service.assembler.VentaModelAssembler;
import com.ferreteria.venta_service.exception.GlobalExceptionHandler;
import com.ferreteria.venta_service.model.Venta;
import com.ferreteria.venta_service.service.VentaService;

@ExtendWith(MockitoExtension.class)
public class VentaControllerTest {

    private MockMvc mockMvc;

    @Mock
    private VentaService ventaService;

    private Venta ventaBase;

    @BeforeEach
    void setUp() {
        // Instanciamos el Assembler real, igual que en SoporteControllerTest
        VentaModelAssembler assembler = new VentaModelAssembler();

        VentaController controller = new VentaController(ventaService, assembler);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ventaBase = new Venta();
        ventaBase.setId(1L);
        ventaBase.setUsuarioId(10L);
        ventaBase.setTotal(15000);
    }

    @Test
    public void testObtenerTodas_Exito() throws Exception {
        // GIVEN
        when(ventaService.obtenerTodas()).thenReturn(List.of(ventaBase));

        // WHEN & THEN
        mockMvc.perform(get("/api/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.links[0].href").exists()); // Validamos que inyecte enlaces generales

        verify(ventaService, times(1)).obtenerTodas();
    }

    @Test
    public void testObtenerPorUsuario_Exito() throws Exception {
        // GIVEN
        when(ventaService.obtenerPorUsuario(10L)).thenReturn(List.of(ventaBase));

        // WHEN & THEN
        mockMvc.perform(get("/api/ventas/usuario/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.links[0].href").exists());

        verify(ventaService, times(1)).obtenerPorUsuario(10L);
    }

    @Test
    public void testObtenerPorRangoFechas_Exito() throws Exception {
        // GIVEN
        when(ventaService.obtenerPorRangoFechas(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(ventaBase));

        // WHEN & THEN
        mockMvc.perform(get("/api/ventas/rango-fechas")
                .param("fechaInicio", "2024-01-01")
                .param("fechaFin", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L));

        verify(ventaService, times(1)).obtenerPorRangoFechas(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    public void testObtenerPorId_Exito() throws Exception {
        // GIVEN
        when(ventaService.obtenerPorId(1L)).thenReturn(ventaBase);

        // WHEN & THEN
        mockMvc.perform(get("/api/ventas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.links[0].rel").exists()); // Verifica HATEOAS inyectado por el Assembler real

        verify(ventaService, times(1)).obtenerPorId(1L);
    }
}