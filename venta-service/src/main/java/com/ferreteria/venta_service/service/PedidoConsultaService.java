package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.Dto.PedidoResponseDto;
import com.ferreteria.venta_service.exception.ResourceNotFoundException;
import com.ferreteria.venta_service.repository.PedidoRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PedidoConsultaService {
    private final PedidoRepository pedidoRepository;

    public List<PedidoResponseDto> obtenerPropios(Long compradorId) {
        Objects.requireNonNull(compradorId, "Se requiere la identidad JWT validada");
        return pedidoRepository.buscarPorComprador(compradorId).stream()
                .map(pedido -> PedidoResponseDto.from(pedido, false)).toList();
    }

    public PedidoResponseDto obtenerPropioPorId(Long id, Long compradorId) {
        Objects.requireNonNull(compradorId, "Se requiere la identidad JWT validada");
        return pedidoRepository.buscarPropioPorId(id, compradorId)
                .map(pedido -> PedidoResponseDto.from(pedido, false))
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
    }

    public List<PedidoResponseDto> obtenerTodos() {
        return pedidoRepository.buscarTodos().stream()
                .map(pedido -> PedidoResponseDto.from(pedido, true)).toList();
    }

    public PedidoResponseDto obtenerPorIdAdministrativo(Long id) {
        return pedidoRepository.buscarPorId(id)
                .map(pedido -> PedidoResponseDto.from(pedido, true))
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
    }
}
