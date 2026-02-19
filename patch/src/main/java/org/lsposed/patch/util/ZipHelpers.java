package org.lsposed.patch.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

public class ZipHelpers {
    private static final int BUFFER_SIZE = 128 * 1024;

    public static void fastExtract(File file, File dest, ExecutorService executor) throws IOException {
        var aborted = new AtomicBoolean(false);
        var futures = new ArrayList<CompletableFuture<?>>();
        try (ZipFile zip = new ZipFile(file)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (aborted.get()) break;
                var entry = entries.nextElement();
                if (entry.isDirectory()) {
                    new File(dest, entry.getName()).mkdirs();
                    continue;
                }
                futures.add(CompletableFuture.runAsync(() -> {
                    if (aborted.get() || Thread.interrupted()) return;
                    try {
                        File out = new File(dest, entry.getName());
                        out.getParentFile().mkdirs();
                        try (var is = zip.getInputStream(entry);
                             var os = new BufferedOutputStream(new FileOutputStream(out), BUFFER_SIZE)) {
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
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            } catch (CompletionException e) {
                futures.forEach(f -> f.cancel(true));
                throw new IOException("Extraction failed", e.getCause());
            }
        }
    }
}
