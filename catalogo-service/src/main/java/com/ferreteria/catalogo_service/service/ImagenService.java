package com.ferreteria.catalogo_service.service;

import com.ferreteria.catalogo_service.config.ImagenMultipartConfig;
import com.ferreteria.catalogo_service.exception.BadRequestException;
import com.ferreteria.catalogo_service.exception.ImagenStorageException;
import com.ferreteria.catalogo_service.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImagenService {
    private static final long MAX_PIXELES = 12_000_000;
    private static final int MAX_PNG = 20 * 1024 * 1024;
    private static final String NOMBRE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png";
    private final Path directorio;
    private final String basePublica;

    public ImagenService(
            @Value("${CATALOGO_IMAGENES_DIR:${user.home}/CheckPointStore/imagenes}") String directorio,
            @Value("${CATALOGO_PUBLIC_BASE_URL:http://localhost:9090}") String basePublica) {
        try {
            this.directorio = Path.of(directorio).normalize();
            URI base = URI.create(basePublica);
            if (!this.directorio.isAbsolute() || base.getHost() == null
                    || !Set.of("http", "https").contains(base.getScheme())
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            this.basePublica = basePublica.replaceAll("/+$", "");
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Configuración de almacenamiento de imágenes inválida.");
        }
    }

    public String subir(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("El campo archivo debe contener una imagen.");
        }
        if (archivo.getSize() > ImagenMultipartConfig.MAX_ARCHIVO) {
            throw new MaxUploadSizeExceededException(ImagenMultipartConfig.MAX_ARCHIVO);
        }
        byte[] png = decodificar(archivo);
        String nombre = UUID.randomUUID() + ".png";
        Path temporal = null;
        try {
            Files.createDirectories(directorio);
            Path raiz = directorio.toRealPath();
            temporal = Files.createTempFile(raiz, ".subida-", ".tmp");
            Files.write(temporal, png);
            // Sin REPLACE_EXISTING: nunca sobrescribir, ni siquiera ante colisión.
            Files.move(temporal, raiz.resolve(nombre));
            return basePublica + "/api/productos/imagenes/" + nombre;
        } catch (IOException | SecurityException ex) {
            throw new ImagenStorageException();
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException | SecurityException ignored) {
                    // Solo temporal propio; no exponer rutas en logs/excepciones.
                }
            }
        }
    }

    private byte[] decodificar(MultipartFile archivo) {
        ImageReader reader = null;
        try (var input = archivo.getInputStream();
                var imagen = new MemoryCacheImageInputStream(input);
                var output = new ByteArrayOutputStream()) {
            var readers = ImageIO.getImageReaders(imagen);
            if (!readers.hasNext()) throw imagenInvalida();
            reader = readers.next();
            String formato = reader.getFormatName().toLowerCase(Locale.ROOT);
            if (!Set.of("jpeg", "jpg", "png", "webp").contains(formato)) throw imagenInvalida();
            reader.setInput(imagen, true, true);
            int ancho = reader.getWidth(0);
            int alto = reader.getHeight(0);
            if (ancho <= 0 || alto <= 0 || (long) ancho * alto > MAX_PIXELES) {
                throw new BadRequestException("La imagen supera el límite de 12 millones de píxeles.");
            }
            // Un decoder que detecte corrupción recuperable no debe aceptarla.
            reader.addIIOReadWarningListener((source, warning) -> { throw imagenInvalida(); });
            BufferedImage decoded = reader.read(0);
            if (decoded == null) throw imagenInvalida();
            // Copiar píxeles a una imagen nueva: descarta EXIF, texto y cargas anexas.
            BufferedImage clean = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
            var graphics = clean.createGraphics();
            try {
                graphics.drawImage(decoded, 0, 0, null);
            } finally {
                graphics.dispose();
                decoded.flush();
            }
            try {
                if (!ImageIO.write(clean, "png", output)) throw imagenInvalida();
            } finally {
                clean.flush();
            }
            if (output.size() > MAX_PNG) {
                throw new BadRequestException("La imagen procesada supera el límite permitido.");
            }
            return output.toByteArray();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw imagenInvalida();
        } finally {
            if (reader != null) reader.dispose();
        }
    }

    public byte[] obtener(String nombre) {
        if (!nombre.matches(NOMBRE)) throw noEncontrada();
        Path archivo = directorio.resolve(nombre);
        try {
            if (!Files.isRegularFile(archivo, LinkOption.NOFOLLOW_LINKS)) throw noEncontrada();
            // NOFOLLOW también en la apertura: rechazar sustitución por enlace simbólico.
            try (var input = Files.newInputStream(archivo, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_PNG + 1);
                if (bytes.length > MAX_PNG) throw new ImagenStorageException();
                return bytes;
            }
        } catch (java.nio.file.NoSuchFileException ex) {
            throw noEncontrada();
        } catch (IOException | SecurityException ex) {
            throw new ImagenStorageException();
        }
    }

    private BadRequestException imagenInvalida() {
        return new BadRequestException("El archivo no es una imagen JPEG, PNG o WebP válida.");
    }

    private ResourceNotFoundException noEncontrada() {
        return new ResourceNotFoundException("Imagen no encontrada.");
    }
}
