package kz.jondeu.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import kz.jondeu.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = FileStorage.class, ignored = LocalFileStorage.class)
public class LocalFileStorage implements FileStorage {
    private final Path root;

    public LocalFileStorage(AppProperties props) throws IOException {
        root = Paths.get(props.storage().localDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return p;
    }

    @Override
    public void put(String key, InputStream data, long size, String contentType) throws IOException {
        Path p = resolve(key);
        Files.createDirectories(p.getParent());
        Files.copy(data, p, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override public Resource get(String key) { return new FileSystemResource(resolve(key)); }

    @Override public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key)); }
}
