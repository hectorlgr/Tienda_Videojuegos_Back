package com.ferreteria.catalogo_service.controller;

import com.ferreteria.catalogo_service.Dto.RawgDtos;
import com.ferreteria.catalogo_service.service.RawgService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/productos/rawg")
@RequiredArgsConstructor
public class RawgController {
    private final RawgService rawgService;

    @GetMapping("/buscar")
    public RawgDtos.Busqueda buscar(@RequestParam(required = false) String query,
            @RequestParam(defaultValue = "1") int page) {
        return rawgService.buscar(query, page);
    }

    @GetMapping("/{rawgId}")
    public RawgDtos.Detalle detalle(@PathVariable long rawgId) {
        return rawgService.detalle(rawgId);
    }
}
