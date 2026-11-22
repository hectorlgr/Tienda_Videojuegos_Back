package com.ferreteria.catalogo_service.controller;

import com.ferreteria.catalogo_service.Dto.RawgDtos;
import com.ferreteria.catalogo_service.service.RawgService;
import com.ferreteria.catalogo_service.service.RawgImportacionService;
import com.ferreteria.catalogo_service.Dto.RawgImportacionRequestDto;
import com.ferreteria.catalogo_service.Dto.ProductoResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/productos/rawg")
@RequiredArgsConstructor
public class RawgController {
    private final RawgService rawgService;
    private final RawgImportacionService importacionService;

    @PostMapping("/{rawgId}/importar")
    public ResponseEntity<ProductoResponseDto> importar(@PathVariable Long rawgId,
            @Valid @RequestBody RawgImportacionRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductoResponseDto.from(importacionService.importar(rawgId, dto)));
    }

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
