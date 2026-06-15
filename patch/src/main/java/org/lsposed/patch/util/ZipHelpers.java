package org.lsposed.patch.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipHelpers {
    private static final int BUFFER_SIZE = 128 * 1024;

    public static void fastExtract(File file, File dest, ExecutorService executor, Consumer<Long> update) throws IOException {
        AtomicBoolean aborted = new AtomicBoolean(false);
        ArrayList<CompletableFuture<?>> futures = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (aborted.get()) break;
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    createDirs(new File(dest, entry.getName()));
                    continue;
                }
                futures.add(CompletableFuture.runAsync(() -> {
                    if (aborted.get() || Thread.interrupted()) return;
                    update.accept(entry.getSize());
                    try {
                        File out = new File(dest, entry.getName());
                        createDirs(out.getParentFile());
                        try (InputStream is = zip.getInputStream(entry);
                             OutputStream os = new BufferedOutputStream(Files.newOutputStream(out.toPath()), BUFFER_SIZE)) {
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int len; while ((len = is.read(buffer)) != -1) {
                                os.write(buffer, 0, len);
                            }
                        }
                    } catch (IOException e) {
                        aborted.set(true);
                        throw new UncheckedIOException(e);
                    }
                }, executor));
            }
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (CompletionException e) {
                futures.forEach(f -> f.cancel(true));
                throw new IOException("Extraction failed", e.getCause());
            }
        }
    }

    private static void createDirs(File dir) throws IOException {
        if (dir.mkdirs() || dir.exists()) return;
        throw new IOException("Failed to create dir: " + dir.getPath());
    }
}
