package com.ferreteria.catalogo_service.Dto;

import java.util.List;

public final class RawgDtos {
    private RawgDtos() {}

    public record Juego(Long rawgId, String nombre, String imagenUrl,
            List<String> plataformas, boolean yaImportado) {}

    public record Busqueda(List<Juego> resultados, int page, int pageSize,
            long total, boolean haySiguiente) {}

    public record Detalle(Long rawgId, String nombre, String descripcion,
            String imagenUrl, List<String> generos, List<String> plataformas,
            boolean yaImportado) {}
}
