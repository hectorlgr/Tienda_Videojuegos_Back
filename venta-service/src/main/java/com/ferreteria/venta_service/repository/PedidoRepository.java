package com.ferreteria.venta_service.repository;

import com.ferreteria.venta_service.model.Pedido;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Superficie de solo lectura: no expone save/delete ni usa repositorios heredados.
public interface PedidoRepository extends Repository<Pedido, Long> {
    @Query("""
            select distinct p from Pedido p left join fetch p.detalles
            where p.compradorId = :compradorId
            order by p.fechaCreacion desc, p.id desc
            """)
    List<Pedido> buscarPorComprador(@Param("compradorId") Long compradorId);

    @Query("""
            select distinct p from Pedido p left join fetch p.detalles
            where p.id = :id and p.compradorId = :compradorId
            """)
    Optional<Pedido> buscarPropioPorId(@Param("id") Long id, @Param("compradorId") Long compradorId);

    @Query("""
            select distinct p from Pedido p left join fetch p.detalles
            order by p.fechaCreacion desc, p.id desc
            """)
    List<Pedido> buscarTodos();

    @Query("select distinct p from Pedido p left join fetch p.detalles where p.id = :id")
    Optional<Pedido> buscarPorId(@Param("id") Long id);
}
