package kz.jondeu.media;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

/**
 * Binary storage abstraction. {@link LocalFileStorage} is used for development; an S3 / Cloudflare R2
 * implementation (S3-compatible API, signed URLs) plugs in by providing another bean. Metadata lives in PostgreSQL.
 */
public interface FileStorage {
    void put(String key, InputStream data, long size, String contentType) throws IOException;
    Resource get(String key) throws IOException;
    void delete(String key) throws IOException;
}
