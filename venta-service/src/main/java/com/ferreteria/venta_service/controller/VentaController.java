package com.ferreteria.venta_service.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ferreteria.venta_service.assembler.VentaModelAssembler;
import com.ferreteria.venta_service.model.Venta;
import com.ferreteria.venta_service.service.VentaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
@Tag(name = "Procesamiento de Ventas", description = "API principal para la gestión de compras, carritos y facturación")
public class VentaController {

        private static final Logger logger = LoggerFactory.getLogger(VentaController.class);

        private final VentaService ventaService;
        private final VentaModelAssembler assembler;

        // GET: Obtener todas las ventas
        @Operation(summary = "Obtener historial general de ventas", description = "Retorna una lista con todas las transacciones de venta registradas en la ferretería.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Historial obtenido exitosamente")
        })
        @GetMapping
        public ResponseEntity<CollectionModel<EntityModel<Venta>>> obtenerTodas() {
                logger.info("GET /api/ventas - Solicitud para listar todas las ventas");
                List<EntityModel<Venta>> ventasModel = ventaService.obtenerTodas().stream()
                                .map(assembler::toModel)
                                .collect(Collectors.toList());

                WebMvcLinkBuilder linkSelf = linkTo(methodOn(this.getClass()).obtenerTodas());
                return ResponseEntity.ok(CollectionModel.of(ventasModel, linkSelf.withSelfRel()));
        }

        // GET: Obtener ventas por ID de usuario
        @Operation(summary = "Obtener compras de un usuario", description = "Filtra y retorna todas las transacciones de venta asociadas a un ID de cliente específico.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Ventas del usuario obtenidas exitosamente")
        })
        @GetMapping("/usuario/{usuarioId}")
        public ResponseEntity<CollectionModel<EntityModel<Venta>>> obtenerPorUsuario(
                        @Parameter(description = "ID del usuario comprador", example = "5") @PathVariable Long usuarioId) {
                logger.info("GET /api/ventas/usuario/{} - Solicitud para listar ventas por usuario", usuarioId);
                List<EntityModel<Venta>> ventasModel = ventaService.obtenerPorUsuario(usuarioId).stream()
                                .map(assembler::toModel)
                                .collect(Collectors.toList());

                WebMvcLinkBuilder linkSelf = linkTo(methodOn(this.getClass()).obtenerPorUsuario(usuarioId));
                return ResponseEntity.ok(CollectionModel.of(ventasModel, linkSelf.withSelfRel()));
        }

        // GET: Obtener ventas por rango de fechas
        @Operation(summary = "Filtrar ventas por fechas", description = "Genera un reporte de ventas realizadas entre dos fechas específicas (formato ISO YYYY-MM-DD).")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Reporte generado correctamente")
        })
        @GetMapping("/rango-fechas")
        public ResponseEntity<CollectionModel<EntityModel<Venta>>> obtenerPorRangoFechas(
                        @Parameter(description = "Fecha inicial del rango", example = "2024-01-01") @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate fechaInicio,
                        @Parameter(description = "Fecha final del rango", example = "2024-12-31") @RequestParam("fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate fechaFin) {
                logger.info("GET /api/ventas/rango-fechas - Solicitud de búsqueda. Desde: {} Hasta: {}", fechaInicio,
                                fechaFin);

                List<EntityModel<Venta>> ventasModel = ventaService.obtenerPorRangoFechas(fechaInicio, fechaFin)
                                .stream()
                                .map(assembler::toModel)
                                .collect(Collectors.toList());

                WebMvcLinkBuilder linkSelf = linkTo(
                                methodOn(this.getClass()).obtenerPorRangoFechas(fechaInicio, fechaFin));
                return ResponseEntity.ok(CollectionModel.of(ventasModel, linkSelf.withSelfRel()));
        }

        // GET: Obtener venta por ID
        @Operation(summary = "Obtener detalles de una venta", description = "Retorna la información completa de una transacción específica, incluyendo sus ítems, totales e impuestos.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Venta encontrada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Venta.class))),
                        @ApiResponse(responseCode = "404", description = "Venta no encontrada", content = @Content)
        })
        @GetMapping("/{id}")
        public ResponseEntity<EntityModel<Venta>> obtenerPorId(
                        @Parameter(description = "ID de la transacción", example = "1024") @PathVariable Long id) {
                logger.info("GET /api/ventas/{} - Solicitud para obtener venta por ID", id);
                Venta venta = ventaService.obtenerPorId(id);

                return ResponseEntity.ok(assembler.toModel(venta)); // HATEOAS delegado al Assembler
        }
}