package com.famy.shared.files;

import com.famy.shared.Ids;
import com.famy.shared.config.FamyProperties;
import com.famy.shared.error.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Archivos subidos (comprobantes de pago, más adelante fotos de recetas). Los metadatos van en {@code file_object} y el
 * contenido en disco bajo {@code FAMY_DATA_DIR/files/<familia>/<id>}. El tipo se detecta por los primeros bytes, no
 * por lo que diga el cliente, y solo se aceptan imágenes y PDF.
 */
@Component
public class FileStorage {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    public record StoredFile(UUID id, UUID familyId, String filename, String contentType, long sizeBytes,
            Instant createdAt) {
    }

    private final JdbcClient jdbc;
    private final Path root;

    public FileStorage(JdbcClient jdbc, FamyProperties props) {
        this.jdbc = jdbc;
        this.root = Path.of(props.dataDir(), "files").toAbsolutePath().normalize();
    }

    /** Guarda el archivo; si la transacción se revierte, se borra del disco. */
    public StoredFile store(UUID familyId, UUID uploadedBy, String filename, byte[] content) {
        if (content.length == 0) {
            throw ApiException.badRequest("empty_file", "El archivo está vacío.");
        }
        if (content.length > MAX_BYTES) {
            throw ApiException.badRequest("file_too_large", "El archivo no puede pasar de 10 MB.");
        }
        String type = sniff(content).orElseThrow(() -> ApiException.badRequest("unsupported_file",
                "Solo se aceptan imágenes (JPG, PNG, WEBP, GIF) o PDF."));
        UUID id = Ids.newId();
        String key = familyId + "/" + id;
        Path path = resolve(key);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteQuietly(path);
                    }
                }
            });
        }
        String name = cleanName(filename);
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO file_object (id, family_id, storage_key, filename, content_type, size_bytes, sha256,
                                         uploaded_by, created_at)
                VALUES (:id, :familyId, :key, :filename, :type, :size, :sha, :by, :now)""")
                .param("id", id).param("familyId", familyId).param("key", key).param("filename", name)
                .param("type", type).param("size", (long) content.length).param("sha", sha256(content))
                .param("by", uploadedBy).param("now", Timestamp.from(now))
                .update();
        return new StoredFile(id, familyId, name, type, content.length, now);
    }

    public Optional<StoredFile> find(UUID familyId, UUID fileId) {
        return jdbc.sql("""
                SELECT id, family_id, filename, content_type, size_bytes, created_at FROM file_object
                WHERE id = :id AND family_id = :familyId""")
                .param("id", fileId).param("familyId", familyId)
                .query((rs, n) -> new StoredFile(rs.getObject("id", UUID.class),
                        rs.getObject("family_id", UUID.class), rs.getString("filename"),
                        rs.getString("content_type"), rs.getLong("size_bytes"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    public Path path(StoredFile file) {
        return resolve(file.familyId() + "/" + file.id());
    }

    /** Borra metadatos y, tras el commit, el contenido. */
    public void delete(UUID familyId, UUID fileId) {
        find(familyId, fileId).ifPresent(f -> {
            jdbc.sql("DELETE FROM file_object WHERE id = :id").param("id", fileId).update();
            Path path = path(f);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteQuietly(path);
                    }
                });
            } else {
                deleteQuietly(path);
            }
        });
    }

    /** Tipo real por la firma de los primeros bytes. */
    static Optional<String> sniff(byte[] b) {
        if (startsWith(b, 0, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
            return Optional.of("application/pdf");
        }
        if (startsWith(b, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(b, 0, 0x47, 0x49, 0x46, 0x38)) {
            return Optional.of("image/gif");
        }
        if (startsWith(b, 0, 0x52, 0x49, 0x46, 0x46) && startsWith(b, 8, 0x57, 0x45, 0x42, 0x50)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] b, int offset, int... sig) {
        if (b.length < offset + sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((b[offset + i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Ruta fuera del almacenamiento");
        }
        return p;
    }

    private static String cleanName(String filename) {
        if (filename == null) {
            return null;
        }
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (base.isEmpty()) {
            return null;
        }
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Queda un archivo huérfano; no afecta a los datos.
        }
    }
}
