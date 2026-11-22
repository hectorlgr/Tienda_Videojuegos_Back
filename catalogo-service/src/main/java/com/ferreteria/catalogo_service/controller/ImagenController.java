package com.ferreteria.catalogo_service.controller;

import com.ferreteria.catalogo_service.exception.BadRequestException;
import com.ferreteria.catalogo_service.service.ImagenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.time.Duration;

@RestController
@RequestMapping("/api/productos/imagenes")
@RequiredArgsConstructor
public class ImagenController {
    private final ImagenService imagenService;

    public record ImagenSubida(String imagenUrl) {}

    @PostMapping(value = "/subir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImagenSubida> subir(@RequestParam("archivo") MultipartFile archivo,
            MultipartHttpServletRequest request) {
        long archivos = request.getMultiFileMap().values().stream().mapToLong(java.util.List::size).sum();
        if (archivos != 1) throw new BadRequestException("Envíe una sola imagen en el campo archivo.");
        return ResponseEntity.status(HttpStatus.CREATED).body(new ImagenSubida(imagenService.subir(archivo)));
    }

    @GetMapping("/{nombre}")
    public ResponseEntity<byte[]> obtener(@PathVariable String nombre) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(imagenService.obtener(nombre));
    }
}
