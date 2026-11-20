package com.ferreteria.catalogo_service.service;

import com.ferreteria.catalogo_service.exception.ResourceNotFoundException;
import com.ferreteria.catalogo_service.model.Producto;
import com.ferreteria.catalogo_service.repository.ProductoRepository;
import com.ferreteria.catalogo_service.Dto.ProductoCrearRequestDto;
import com.ferreteria.catalogo_service.Dto.ProductoRequestDto;
import com.ferreteria.catalogo_service.model.CategoriaProducto;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {

    // Declarar el Logger
    private static final Logger logger = LoggerFactory.getLogger(ProductoService.class);

    private final ProductoRepository productoRepository;
    private final Validator validator;

    public List<Producto> obtenerTodos(CategoriaProducto categoria) {
        return categoria == null ? obtenerTodos()
                : productoRepository.findByCategoriaAndHabilitadoTrue(categoria);
    }

    public Producto crearProducto(ProductoCrearRequestDto dto) {
        var errores = validator.validate(dto);
        if (!errores.isEmpty()) {
            throw new ConstraintViolationException(errores);
        }
        // MySQL genera el ID. rawgId se reserva para la futura importación.
        Producto producto = new Producto();
        aplicarCamposEditables(producto, dto);
        return guardarProducto(producto);
    }

    private void aplicarCamposEditables(Producto producto, ProductoCrearRequestDto dto) {
        // Lista explícita: nunca asignar id ni rawgId desde la edición manual.
        producto.setNombre(dto.getNombre());
        producto.setCategoria(dto.getCategoria());
        producto.setDescripcion(dto.getDescripcion());
        producto.setMarca(dto.getMarca());
        producto.setPlataforma(dto.getPlataforma());
        producto.setGenero(dto.getGenero());
        producto.setImagenUrl(dto.getImagenUrl());
        producto.setPrecio(dto.getPrecio());
        producto.setPrecioOferta(dto.getPrecioOferta());
        producto.setEnOferta(dto.getEnOferta());
        producto.setStock(dto.getStock());
        producto.setHabilitado(dto.getHabilitado());
    }

    // Método para obtener todos los productos habilitados
    public List<Producto> obtenerTodos() {
        logger.info("Consultando todos los productos en la base de datos");
        return productoRepository.findByHabilitadoTrue();
    }

    // Método para obtener un producto por su ID, con manejo de excepción si no se
    // encuentra
    public Producto obtenerPorId(Long id) {
        logger.info("Buscando producto activo con ID: {}", id);
        return productoRepository.findByIdAndHabilitadoTrue(id)
                .orElseThrow(() -> {
                    logger.warn("Búsqueda fallida: El producto con ID {} no existe o está deshabilitado", id);
                    return new ResourceNotFoundException("Error: Producto no encontrado con el ID " + id);
                });
    }

    // Método para guardar un nuevo producto
    public Producto guardarProducto(Producto producto) {
        logger.info("Registrando nuevo producto: {}", producto.getNombre());
        logger.debug("Precio a registrar: {}", producto.getPrecio());

        var errores = validator.validate(producto);
        if (!errores.isEmpty()) {
            throw new ConstraintViolationException(errores);
        }
        Producto productoGuardado = productoRepository.save(producto);
        logger.debug("Producto guardado temporalmente con ID interno: {}", productoGuardado.getId());

        return productoGuardado;
    }

    // Edición administrativa local, también para productos ocultos.
    @Transactional
    public Producto actualizarProducto(Long id, ProductoRequestDto dto) {
        logger.info("Iniciando actualización para el producto ID: {}", id);
        var erroresEntrada = validator.validate(dto);
        if (!erroresEntrada.isEmpty()) {
            throw new ConstraintViolationException(erroresEntrada);
        }
        Producto productoExistente = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID " + id));
        aplicarCamposEditables(productoExistente, dto);
        var erroresProducto = validator.validate(productoExistente);
        if (!erroresProducto.isEmpty()) {
            throw new ConstraintViolationException(erroresProducto);
        }
        return productoRepository.save(productoExistente);
    }

    // Baja lógica local e idempotente; conserva stock y demás datos.
    @Transactional
    public void eliminarProducto(Long id) {
        logger.info("Solicitando baja lógica del producto ID: {}", id);
        Producto productoExistente = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID " + id));
        if (Boolean.TRUE.equals(productoExistente.getHabilitado())) {
            productoExistente.setHabilitado(false);
            productoRepository.save(productoExistente);
        }
    }

    // Método para que otros servicios deshabiliten productos automáticamente
    public void marcarComoAgotado(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        if (producto.getHabilitado()) {
            producto.setHabilitado(false);
            productoRepository.save(producto);
            logger.info("Producto ID: {} deshabilitado automáticamente por falta de stock", id);
        }
    }

    // Método para reactivar un producto (por ejemplo, cuando vuelve a tener stock)
    public void habilitarProducto(Long id) {
        logger.info("Iniciando habilitación para el producto ID: {}", id);

        Producto producto = productoRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Producto no encontrado en la BD general con ID: " + id));

        if (!producto.getHabilitado()) {
            producto.setHabilitado(true);
            productoRepository.save(producto);
            logger.info("Producto ID: {} reactivado y visible en el catálogo", id);
        } else {
            logger.info("El producto ID: {} ya estaba habilitado", id);
        }
    }
}
