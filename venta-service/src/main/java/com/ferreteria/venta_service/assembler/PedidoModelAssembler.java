package com.ferreteria.venta_service.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.ferreteria.venta_service.Dto.PedidoResponseDto;
import com.ferreteria.venta_service.controller.PedidoConsultaController;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class PedidoModelAssembler
        implements RepresentationModelAssembler<PedidoResponseDto, EntityModel<PedidoResponseDto>> {
    @Override
    public EntityModel<PedidoResponseDto> toModel(PedidoResponseDto pedido) {
        return EntityModel.of(pedido,
                linkTo(methodOn(PedidoConsultaController.class).obtenerPorId(pedido.id(), null)).withSelfRel(),
                linkTo(methodOn(PedidoConsultaController.class).obtenerMisPedidos(null)).withRel("mis-pedidos"));
    }

    public EntityModel<PedidoResponseDto> toAdminModel(PedidoResponseDto pedido) {
        return EntityModel.of(pedido,
                linkTo(methodOn(PedidoConsultaController.class).obtenerPorIdAdministrativo(pedido.id())).withSelfRel(),
                linkTo(methodOn(PedidoConsultaController.class).obtenerTodos()).withRel("todos-los-pedidos"));
    }
}
