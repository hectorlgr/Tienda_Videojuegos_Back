package com.ferreteria.catalogo_service.Dto;

import com.ferreteria.catalogo_service.model.CategoriaProducto;
import com.ferreteria.catalogo_service.model.Producto;
import com.ferreteria.catalogo_service.model.FormatoProducto;
import com.ferreteria.catalogo_service.model.ProductoImagen;
import java.util.Comparator;
import java.util.List;
import org.springframework.hateoas.server.core.Relation;

@Relation(itemRelation = "producto", collectionRelation = "productos")
public record ProductoResponseDto(
        Long id, String nombre, CategoriaProducto categoria, String descripcion,
        String marca, String plataforma, String genero, String imagenUrl,
        Integer precio, Integer precioOferta, Boolean enOferta, Integer stock,
        Boolean habilitado, Long rawgId, Integer precioEfectivo, boolean disponible,
        String codigo, String descripcionCorta, FormatoProducto formato, String ecosistema,
        String compatibilidad, String franquicia, Integer stockCritico, List<String> imagenes) {

    public static ProductoResponseDto from(Producto producto) {
        return new ProductoResponseDto(
                producto.getId(), producto.getNombre(), producto.getCategoria(), producto.getDescripcion(),
                producto.getMarca(), producto.getPlataforma(), producto.getGenero(), producto.getImagenUrl(),
                producto.getPrecio(), producto.getPrecioOferta(), producto.getEnOferta(), producto.getStock(),
                producto.getHabilitado(), producto.getRawgId(), producto.calcularPrecioEfectivo(),
                 Boolean.TRUE.equals(producto.getHabilitado()) && producto.getStock() > 0,
                 producto.getCodigo(), producto.getDescripcionCorta(), producto.getFormato(),
                 producto.getEcosistema(), producto.getCompatibilidad(), producto.getFranquicia(),
                 producto.getStockCritico(), producto.getImagenesAdicionales().stream()
                         .sorted(Comparator.comparing(ProductoImagen::getOrden))
                         .map(ProductoImagen::getUrl).toList());
    }
}
