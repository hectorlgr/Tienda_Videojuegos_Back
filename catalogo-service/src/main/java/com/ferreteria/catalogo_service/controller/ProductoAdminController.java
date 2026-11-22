package com.ferreteria.catalogo_service.controller;

import com.ferreteria.catalogo_service.Dto.ProductoResponseDto;
import com.ferreteria.catalogo_service.model.Producto;
import com.ferreteria.catalogo_service.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/admin/productos")
@RequiredArgsConstructor
@Tag(name = "Catálogo administrativo", description = "Consulta de productos, incluidos los deshabilitados")
public class ProductoAdminController {
    private final ProductoService productoService;

    @GetMapping
    @Operation(summary = "Listar todos los productos para administración")
    public ResponseEntity<CollectionModel<EntityModel<ProductoResponseDto>>> obtenerTodos() {
        var productos = productoService.obtenerTodosAdministrativo().stream()
                .map(this::toModel).toList();
        return ResponseEntity.ok(CollectionModel.of(productos,
                linkTo(methodOn(ProductoAdminController.class).obtenerTodos()).withSelfRel())
                .withFallbackType(EntityModel.class, ProductoResponseDto.class));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar cualquier producto existente para administración")
    public ResponseEntity<EntityModel<ProductoResponseDto>> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(toModel(productoService.obtenerPorIdAdministrativo(id)));
    }

    private EntityModel<ProductoResponseDto> toModel(Producto producto) {
        return EntityModel.of(ProductoResponseDto.from(producto),
                linkTo(methodOn(ProductoAdminController.class).obtenerPorId(producto.getId())).withSelfRel(),
                linkTo(methodOn(ProductoAdminController.class).obtenerTodos()).withRel("todos-los-productos"));
    }
}
