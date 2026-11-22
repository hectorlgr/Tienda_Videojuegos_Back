package com.ferreteria.catalogo_service.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.ferreteria.catalogo_service.model.Producto;
import com.ferreteria.catalogo_service.model.CategoriaProducto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    @Query("select p.rawgId from Producto p where p.rawgId in :ids")
    List<Long> findRawgIdsExistentes(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> findByIdForUpdate(@Param("id") Long id);

    // Devuelve solo los productos que tienen habilitado = true
    @EntityGraph(attributePaths = "imagenesAdicionales")
    List<Producto> findByHabilitadoTrue();

    @EntityGraph(attributePaths = "imagenesAdicionales")
    List<Producto> findByCategoriaAndHabilitadoTrue(CategoriaProducto categoria);

    // Buscar por ID pero solo si está habilitado
    @EntityGraph(attributePaths = "imagenesAdicionales")
    java.util.Optional<Producto> findByIdAndHabilitadoTrue(Long id);
}
