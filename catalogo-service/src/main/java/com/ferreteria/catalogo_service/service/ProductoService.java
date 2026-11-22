package com.ferreteria.catalogo_service.service;

import com.ferreteria.catalogo_service.exception.ResourceNotFoundException;
import com.ferreteria.catalogo_service.exception.BadRequestException;
import com.ferreteria.catalogo_service.exception.ConflictException;
import com.ferreteria.catalogo_service.model.Producto;
import com.ferreteria.catalogo_service.model.ProductoImagen;
import com.ferreteria.catalogo_service.model.ProductoCompleto;
import jakarta.validation.groups.Default;
import org.hibernate.Hibernate;
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

    @Transactional
    public Producto crearProducto(ProductoCrearRequestDto dto) {
        var errores = validator.validate(dto);
        if (!errores.isEmpty()) {
            throw new ConstraintViolationException(errores);
        }
        comprobarCodigo(dto.getCodigo(), null);
        // MySQL genera el ID. rawgId se reserva para la futura importación.
        Producto producto = new Producto();
        aplicarCamposEditables(producto, dto);
        agregarImagenes(producto, dto.getImagenes());
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
        producto.setCodigo(dto.getCodigo());
        producto.setDescripcionCorta(dto.getDescripcionCorta());
        producto.setFormato(dto.getFormato());
        producto.setEcosistema(dto.getEcosistema());
        producto.setCompatibilidad(dto.getCompatibilidad());
        producto.setFranquicia(dto.getFranquicia());
        producto.setStockCritico(dto.getStockCritico());
    }

    private void comprobarCodigo(String codigo, Long id) {
        boolean existe = id == null ? productoRepository.existsByCodigo(codigo)
                : productoRepository.existsByCodigoAndIdNot(codigo, id);
        if (existe) {
            throw new ConflictException("El código SKU ya pertenece a otro producto.");
        }
    }

    private void agregarImagenes(Producto producto, List<String> urls) {
        if (urls == null) return;
        for (int i = 0; i < urls.size(); i++) {
            ProductoImagen imagen = new ProductoImagen();
            imagen.setProducto(producto);
            imagen.setUrl(urls.get(i));
            imagen.setOrden(i + 1);
            producto.getImagenesAdicionales().add(imagen);
        }
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
        if (producto.getId() != null) {
            throw new BadRequestException("El alta no admite un ID existente; utilice la edición del producto.");
        }
        logger.info("Registrando nuevo producto: {}", producto.getNombre());
        logger.debug("Precio a registrar: {}", producto.getPrecio());

        var errores = validator.validate(producto, Default.class, ProductoCompleto.class);
        if (!errores.isEmpty()) {
            throw new ConstraintViolationException(errores);
        }
        Producto productoGuardado = productoRepository.saveAndFlush(producto);
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
        Producto productoExistente = obtenerParaActualizar(id);
        if (!productoExistente.getStock().equals(dto.getStockEsperado())) {
            throw new ConflictException("El stock cambió desde la consulta. Recargue el producto antes de editar.");
        }
        comprobarCodigo(dto.getCodigo(), id);
        // Cargar dentro de la transacción: el DTO se construye fuera del servicio.
        Hibernate.initialize(productoExistente.getImagenesAdicionales());
        if (dto.getImagenes() != null) {
            productoExistente.getImagenesAdicionales().clear();
            // orphanRemoval debe borrar antes de reutilizar las posiciones 1..4.
            // El flush NO confirma: cualquier fallo posterior revierte todo.
            productoRepository.flush();
            agregarImagenes(productoExistente, dto.getImagenes());
        }
        aplicarCamposEditables(productoExistente, dto);
        var erroresProducto = validator.validate(productoExistente, Default.class, ProductoCompleto.class);
        if (!erroresProducto.isEmpty()) {
            throw new ConstraintViolationException(erroresProducto);
        }
        return productoRepository.saveAndFlush(productoExistente);
    }

    // Baja lógica local e idempotente; conserva stock y demás datos.
    @Transactional
    public void eliminarProducto(Long id) {
        logger.info("Solicitando baja lógica del producto ID: {}", id);
        Producto productoExistente = obtenerParaActualizar(id);
        if (Boolean.TRUE.equals(productoExistente.getHabilitado())) {
            productoExistente.setHabilitado(false);
            productoRepository.save(productoExistente);
        }
    }

    private Producto obtenerParaActualizar(Long id) {
        return productoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con el ID " + id));
    }

    @Transactional
    public Producto incrementarStock(Long id, Integer cantidad) {
        validarCantidad(cantidad);
        Producto producto = obtenerParaActualizar(id);
        long nuevoStock = (long) producto.getStock() + cantidad;
        if (nuevoStock > Integer.MAX_VALUE) {
            throw new ConflictException("La entrada supera el máximo de stock permitido.");
        }
        producto.setStock((int) nuevoStock);
        Hibernate.initialize(producto.getImagenesAdicionales());
        return productoRepository.save(producto);
    }

    // Operación local reutilizable; la futura integración con Ventas debe autenticar
    // al consumidor y definir su contrato de compra, reintentos y disponibilidad.
    @Transactional
    public Producto descontarStock(Long id, Integer cantidad) {
        validarCantidad(cantidad);
        Producto producto = obtenerParaActualizar(id);
        if (producto.getStock() < cantidad) {
            throw new ConflictException("Stock insuficiente para el producto ID " + id);
        }
        producto.setStock(producto.getStock() - cantidad);
        Hibernate.initialize(producto.getImagenesAdicionales());
        return productoRepository.save(producto);
    }

    private void validarCantidad(Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new BadRequestException("La cantidad debe ser un entero positivo.");
        }
    }

    // Método heredado: no se utiliza desde los movimientos de stock.
    @Transactional
    public void marcarComoAgotado(Long id) {
        Producto producto = obtenerParaActualizar(id);

        if (producto.getHabilitado()) {
            producto.setHabilitado(false);
            productoRepository.save(producto);
            logger.info("Producto ID: {} deshabilitado automáticamente por falta de stock", id);
        }
    }

    // Método para reactivar un producto (por ejemplo, cuando vuelve a tener stock)
    @Transactional
    public void habilitarProducto(Long id) {
        logger.info("Iniciando habilitación para el producto ID: {}", id);

        Producto producto = obtenerParaActualizar(id);

        if (!producto.getHabilitado()) {
            producto.setHabilitado(true);
            productoRepository.save(producto);
            logger.info("Producto ID: {} reactivado y visible en el catálogo", id);
        } else {
            logger.info("El producto ID: {} ya estaba habilitado", id);
        }
    }
}
