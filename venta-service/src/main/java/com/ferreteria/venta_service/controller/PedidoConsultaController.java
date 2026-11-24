package com.ferreteria.venta_service.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.ferreteria.venta_service.Dto.PedidoResponseDto;
import com.ferreteria.venta_service.assembler.PedidoModelAssembler;
import com.ferreteria.venta_service.security.JwtUserId;
import com.ferreteria.venta_service.service.PedidoConsultaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Consulta de pedidos", description = "Lecturas de pedidos propios y administrativas")
public class PedidoConsultaController {
    private final PedidoConsultaService pedidoConsultaService;
    private final PedidoModelAssembler assembler;

    @GetMapping("/pedidos/mis-pedidos")
    public CollectionModel<EntityModel<PedidoResponseDto>> obtenerMisPedidos(@AuthenticationPrincipal Jwt jwt) {
        var pedidos = pedidoConsultaService.obtenerPropios(JwtUserId.getValidId(jwt)).stream()
                .map(assembler::toModel).toList();
        return CollectionModel.of(pedidos,
                linkTo(methodOn(PedidoConsultaController.class).obtenerMisPedidos(null)).withSelfRel())
                .withFallbackType(EntityModel.class, PedidoResponseDto.class);
    }

    @GetMapping("/pedidos/{id}")
    public EntityModel<PedidoResponseDto> obtenerPorId(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        if ("ADMINISTRADOR".equals(jwt.getClaimAsString("role"))) {
            return assembler.toAdminModel(pedidoConsultaService.obtenerPorIdAdministrativo(id));
        }
        return assembler.toModel(pedidoConsultaService.obtenerPropioPorId(id, JwtUserId.getValidId(jwt)));
    }

    @GetMapping("/admin/pedidos")
    public CollectionModel<EntityModel<PedidoResponseDto>> obtenerTodos() {
        var pedidos = pedidoConsultaService.obtenerTodos().stream().map(assembler::toAdminModel).toList();
        return CollectionModel.of(pedidos,
                linkTo(methodOn(PedidoConsultaController.class).obtenerTodos()).withSelfRel())
                .withFallbackType(EntityModel.class, PedidoResponseDto.class);
    }

    @GetMapping("/admin/pedidos/{id}")
    public EntityModel<PedidoResponseDto> obtenerPorIdAdministrativo(@PathVariable Long id) {
        return assembler.toAdminModel(pedidoConsultaService.obtenerPorIdAdministrativo(id));
    }
}
