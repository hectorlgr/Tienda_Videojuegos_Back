package com.ferreteria.venta_service.repository;

import com.ferreteria.venta_service.model.Pedido;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Persistencia de pedidos nuevos y consultas; sin borrado ni repositorios heredados.
public interface PedidoRepository extends Repository<Pedido, Long> {
    Pedido saveAndFlush(Pedido pedido);

    @Query("select distinct p from Pedido p left join fetch p.detalles where p.operacionStockId = :operacion")
    Optional<Pedido> buscarPorOperacionStock(@Param("operacion") String operacion);

    Optional<Pedido> findByCompradorIdAndClaveIdempotencia(Long compradorId, String claveIdempotencia);

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
