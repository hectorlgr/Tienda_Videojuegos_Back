package com.ferreteria.venta_service.repository;

import com.ferreteria.venta_service.model.EstadoSolicitudPedido;
import com.ferreteria.venta_service.model.SolicitudPedido;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudPedidoRepository extends JpaRepository<SolicitudPedido, Long> {
    Optional<SolicitudPedido> findByCompradorIdAndClaveIdempotencia(Long compradorId, String claveIdempotencia);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudPedido s where s.id = :id")
    Optional<SolicitudPedido> buscarParaActualizar(@Param("id") Long id);

    @Query("""
            select s from SolicitudPedido s where s.estado in :estados
            order by s.fechaActualizacion, s.id
            """)
    List<SolicitudPedido> buscarPendientes(@Param("estados") Collection<EstadoSolicitudPedido> estados,
            Pageable pagina);

    @Query("""
            select s from SolicitudPedido s where s.estado in :estados
            and (s.bloqueoHasta is null or s.bloqueoHasta <= :ahora)
            order by s.fechaActualizacion, s.id
            """)
    List<SolicitudPedido> buscarDisponibles(@Param("estados") Collection<EstadoSolicitudPedido> estados,
            @Param("ahora") Instant ahora, Pageable pagina);
}
