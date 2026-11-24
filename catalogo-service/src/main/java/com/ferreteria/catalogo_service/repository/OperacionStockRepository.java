package com.ferreteria.catalogo_service.repository;

import com.ferreteria.catalogo_service.model.OperacionStock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperacionStockRepository extends JpaRepository<OperacionStock, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OperacionStock o where o.operacionId = :id")
    Optional<OperacionStock> findByIdForUpdate(@Param("id") String id);
}
