package com.ferreteria.catalogo_service.service;

import com.ferreteria.catalogo_service.Dto.ProductoCrearRequestDto;
import com.ferreteria.catalogo_service.Dto.RawgImportacionRequestDto;
import com.ferreteria.catalogo_service.exception.BadRequestException;
import com.ferreteria.catalogo_service.exception.RawgException;
import com.ferreteria.catalogo_service.model.CategoriaProducto;
import com.ferreteria.catalogo_service.model.Producto;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RawgImportacionService {
    private final RawgService rawgService;
    private final ProductoService productoService;
    private final Validator validator;

    // Suspende incluso una transacción que pudiera iniciar un futuro consumidor.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Producto importar(Long rawgId, RawgImportacionRequestDto entrada) {
        if (rawgId == null || rawgId <= 0) {
            throw new BadRequestException("rawgId debe ser un entero positivo.");
        }
        var errores = validator.validate(entrada);
        if (!errores.isEmpty()) throw new ConstraintViolationException(errores);

        var juego = rawgService.detalle(rawgId);
        // yaImportado es informativo: otra variante NO impide esta importación.
        ProductoCrearRequestDto dto = new ProductoCrearRequestDto();
        dto.setCodigo(entrada.getCodigo());
        dto.setCategoria(CategoriaProducto.VIDEOJUEGO);
        dto.setNombre(juego.nombre());
        dto.setDescripcion(juego.descripcion());
        dto.setImagenUrl(juego.imagenUrl());
        dto.setGenero(juego.generos().isEmpty() ? null : String.join(", ", juego.generos()));
        // Una sola plataforma comercial elegida, no la lista de plataformas RAWG.
        dto.setPlataforma(entrada.getPlataforma());
        dto.setFormato(entrada.getFormato());
        dto.setPrecio(entrada.getPrecio());
        dto.setPrecioOferta(entrada.getPrecioOferta());
        dto.setEnOferta(entrada.getEnOferta());
        dto.setStock(entrada.getStock());
        dto.setStockCritico(entrada.getStockCritico());
        dto.setHabilitado(entrada.getHabilitado());

        if (!validator.validate(dto).isEmpty()) {
            throw new RawgException(HttpStatus.BAD_GATEWAY, "RAWG_METADATOS_INVALIDOS",
                    "Los metadatos de RAWG no cumplen los límites del catálogo.");
        }
        // Otro bean/proxy: la transacción de escritura empieza DESPUÉS del HTTP.
        return productoService.crearProductoImportado(dto, rawgId);
    }
}
