package org.lsposed.patch;

import static org.lsposed.lspatch.share.Constants.CONFIG_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.LOADER_DEX_PATH;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.PROXY_APP_COMPONENT_FACTORY;

import com.android.apksig.ApkSigner;
import com.android.apksig.apk.ApkFormatException;
import com.android.tools.build.apkzlib.sign.SigningExtension;
import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.NestedZip;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.android.tools.build.apkzlib.zip.compress.DeflateExecutionCompressor;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wind.meditor.core.ManifestEditor;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;
import com.wind.meditor.utils.PermissionType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspatch.share.ConstantsM;
import org.lsposed.lspatch.share.ExtraConfig;
import org.lsposed.lspatch.share.PatchConfig;
import org.lsposed.patch.util.ApkSignatureHelper;
import org.lsposed.patch.util.ManifestParser;
import org.lsposed.patch.util.ZipHelpers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nonnull;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "ResultOfMethodCallIgnored"})
public final class LSPatch {
    @SuppressWarnings("unused")
    public static class PatchError extends Error {
        public PatchError(String message) {
            super(message);
        }

        public PatchError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Parameter(description = "apks") @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private List<String> apkPaths = new ArrayList<>();

    @Parameter(names = {"-h", "--help"}, order = 0, help = true, description = "Print this message")
    private boolean help = false;

    @Parameter(names = {"-o", "--output"}, order = 1, help = true, description = "Output directory")
    private String outputPath;

    @Parameter(names = {"-f", "--force"}, order = 2, help = true, description = "Force overwrite output file")
    private boolean forceOverwrite = false;

    @Parameter(names = {"-ks", "--keystore"}, order = 3, help = true, description = "Sign with external keystore file")
    private String userKey = null;

    @Parameter(names = {"-ksp", "--ks-prompt"}, order = 4, help = true, description = "Prompt for keystore alias details")
    private boolean userKeyPrompt = false;

    @Parameter(names = {"-p", "--patch"}, order = 5, help = true, description = "Forcefully patch apps that do not require patching")
    private boolean patchForcibly = false;

    @Parameter(names = {"--fix-conf"}, order = 6, help = true, description = "Fix apk-conflicts [If unable to remove other fb apps]")
    private boolean fixConflict = false;

    @Parameter(names = {"--mask-pkg"}, order = 7, help = true, description = "Mask package name [If unable to remove Messenger itself]")
    private boolean maskPackage = false;

    @Parameter(names = {"--sign-only"}, order = 8, help = true, description = "Skip patching (apk signing only)")
    private boolean signForcibly = false;

    @Parameter(names = {"--fallback"}, order = 9, help = true, description = "Use fallback mode (slow) [If default patched apps fail]")
    private boolean fallbackMode = false;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(names = {"--modules"}, order = 10, variableArity = true, help = true, description = "Allow third-party modules [package names]")
    private List<String> modules = new ArrayList<>();

    // @Parameter(names = {"--res-hook"}, order = 11, help = true, description = "Enable resource hook (disabled by default) [unnecessary]")

    @Parameter(names = {"--load-on-all"}, order = 12, hidden = true, description = "Load modules for all patched apps, not just Messenger. [unnecessary]")
    private boolean loadOnAll = false;

    @Parameter(names = {"--no-restriction"}, hidden = true, description = "[Internal Option] Patch any apps")
    private boolean noRestriction = false;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(names = {"--key-args"}, hidden = true, variableArity = true, description = "[Internal Option] keystore [path,pass,alias,pass]")
    private List<String> internalKeystore = new ArrayList<>();

    @Parameter(names = {"--out-file"}, hidden = true, description = "[Internal Option] absolute path for output")
    private String internalOutputPath = null;

    @Parameter(names = {"--temp-dir"}, hidden = true, description = "[Internal Option] temp directory path")
    private String internalTempDir = null;

    @Parameter(names = {"--internal-patch"}, hidden = true, description = "[Internal Option] set by mrv-patch-manager")
    private boolean internal = false;

    @Parameter(names = {"-v", "--verbose"}, hidden = true, description = "[Internal Option] verbose")
    private boolean verbose = false;

    private boolean embedSignature = false;

    private static final List<String> DEFAULT_PATCHABLE_PACKAGE = ImmutableList.of(
        "com.facebook.orca",
        "com.facebook.katana"
    );

    private static boolean shouldPatch(String packageName) {
        return DEFAULT_PATCHABLE_PACKAGE.contains(packageName) || !(
                packageName.startsWith(ConstantsM.VALID_FB_PACKAGE_PREFIX) || packageName.endsWith("lite")
        );
    }

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";

    private static final Map<String, String> ARCH_LIBRARY_MAP = ImmutableMap.of(
        "arm",    "armeabi-v7a",
        "arm64",  "arm64-v8a",
        "x86",    "x86",
        "x86_64", "x86_64"
    );

    private static final String DEFAULT_SIGNING_KEY = "assets/mrvkey";

    private static final ZFileOptions Z_FILE_OPTIONS = new ZFileOptions().setAlignmentRule(
        AlignmentRules.compose(
            AlignmentRules.constantForSuffix(".so", 4096),
            AlignmentRules.constantForSuffix(ORIGINAL_APK_ASSET_PATH, 4096)
        )
    );

    private final static String PATCHED_SUFFIX = "-mrv";
    private final static String SIGNED_SUFFIX = "-signed";

    private KeyStore.PrivateKeyEntry signingKey;
    private static final String DEFAULT_SIGNER_NAME = "facebook";
    private static final char[] DEFAULT_KEYPASS = "123456".toCharArray();

    private Set<File> TEMPORARY_FILES_CLEANUP = new HashSet<>();

    private void registerCleanupHook() {
        if (!internal) {
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (!TEMPORARY_FILES_CLEANUP.isEmpty()) {
                        TEMPORARY_FILES_CLEANUP.forEach((f) -> {
                            if (f.isFile()) f.delete();
                            try (Stream<Path> walk = Files.walk(f.toPath())) {
                                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                            } catch (IOException ignored) {}
                        });
                    }
                }));
            } catch (Exception ignored) {}
        }
    }

    private static OutputLogger logger = new OutputLogger() {
        @Override
        public void v(@Nonnull String msg) { System.out.println(msg); }
        @Override
        public void d(@Nonnull String msg) { System.out.println(" -> " + msg); }
        @Override
        public void e(@Nonnull String msg) { System.err.println("\nError: " + msg + "\n"); }
    };

    private final JCommander jCommander;

    public LSPatch(String... args) {
        jCommander = JCommander.newBuilder().addObject(this).build();
        jCommander.setProgramName("MRVPatcher");
        jCommander.setExpandAtSign(false);
        jCommander.parse(args);
    }

    @SuppressWarnings("CallToPrintStackTrace")
    public static void main(String... args) {
        var verbose = false;
        try {
            LSPatch lsPatch = new LSPatch(args);
            lsPatch.help |= args.length == 0;
            verbose = lsPatch.verbose;
            lsPatch.doCommandLine();
        } catch (Throwable t) {
            if (t instanceof CancellationException) {
                throw ((CancellationException) t);
            } else if (verbose) {
                t.printStackTrace();
            } else {
                logger.e(getErrorMessage(t));
            }
        }
    }

    public void doCommandLine() throws Exception {
        registerCleanupHook();
        if (help) {
            jCommander.usage();
            return;
        }
        if (apkPaths == null || apkPaths.isEmpty()) {
            logger.e(" Please provide apk files");
            jCommander.usage();
            return;
        }
        if (!checkInputFiles()) return;
        if (patchForcibly && signForcibly) {
            logger.e(" --patch and --sign-only options can not be used together");
            return;
        }
        setupSigningKey();

        final boolean multiple = apkPaths.size() > 1;
        if (multiple && internalOutputPath != null) {
            throw new AssertionError();
        }
        final Map<String, String> results = new HashMap<>();

        for (var apk : apkPaths) {
            logger.v("\nSource: " + apk);
            results.put(apk, "[failed!] " + apk);

            final File srcApkFile = new File(apk).getAbsoluteFile();
            final String srcFileName = srcApkFile.getName();

            if (!srcApkFile.isFile()) {
                logger.e("'" + srcFileName + "' does not exist");
                if (multiple) logger.v("Skipping...");
                continue;
            }

            if (outputPath == null) outputPath = ".";
            final File outputDir = new File(outputPath);
            if (!outputDir.exists()) outputDir.mkdirs();

            logger.v("\nProgress:");
            logger.d("parsing manifest");

            int minSdk;
            String packageName;
            String appComponentFactory;
            String xApkBaseName = null;
            try (var zFile = ZFile.openReadOnly(srcApkFile)) {
                InputStream xApkManifest = null;
                StoredEntry manifestEntry = zFile.get(ANDROID_MANIFEST_XML);
                if (manifestEntry == null) {
                    StoredEntry xApkBase = findBaseApkFromXApk(zFile);
                    if (xApkBase != null) {
                        try (ZipInputStream zis = new ZipInputStream(xApkBase.open())) {
                            ZipEntry entry;
                            while ((entry = zis.getNextEntry()) != null) {
                                if (entry.getName().equals(ANDROID_MANIFEST_XML)) {
                                    xApkManifest = new ByteArrayInputStream(zis.readAllBytes());
                                    break;
                                }
                            }
                        }
                    }
                    if (xApkManifest == null) {
                        logger.e("Input file is not a valid apk/xapk file");
                        if (multiple) logger.v("Skipping...");
                        continue;
                    }
                    xApkBaseName = xApkBase.getCentralDirectoryHeader().getName();
                }
                try (var is = (manifestEntry != null ? manifestEntry.open() : xApkManifest)) {
                    var pair = ManifestParser.parseManifestFile(is);
                    if (pair == null || pair.packageName == null || pair.packageName.isEmpty()) {
                        logger.e("Failed to parse Manifest");
                        if (multiple) logger.v("Skipping...");
                        continue;
                    }
                    minSdk = pair.minSdkVersion;
                    packageName = pair.packageName;
                    appComponentFactory = pair.appComponentFactory;
                }
            } catch (IOException e) {
                if (multiple) {
                    logger.e(getErrorMessage(e));
                    logger.v("Skipping...");
                } else {
                    throw new RuntimeException(e);
                }
                continue;
            }

            final boolean signOnly = signForcibly || !(patchForcibly || shouldPatch(packageName));

            if (!signOnly) {
                String error = "";
                boolean skip = true;
                if (!noRestriction && ConstantsM.isInvalidPackage(packageName)) {
                    error = "Input file is not a valid facebook app";
                } else if (appComponentFactory == null || appComponentFactory.isEmpty()) {
                    error = "Missing required app component";
                } else if (appComponentFactory.equals(PROXY_APP_COMPONENT_FACTORY)) {
                    error = "Input file was previously patched";
                } else if (maskPackage && !noRestriction && !packageName.equals(ConstantsM.DEFAULT_TARGET_PACKAGE)) {
                    error = "[--mask-pkg] option should only be used for Messenger";
                } else {
                    skip = false;
                }
                if (skip) {
                    logger.e(error);
                    if (multiple) logger.v("Skipping...");
                    continue;
                }
                this.fixConflict |= maskPackage;
            }

            this.embedSignature = maskPackage || !ConstantsM.isSignatureHardcoded(packageName);

            final File outputFile = (internalOutputPath != null ? new File(internalOutputPath) :
                new File(outputDir, FilenameUtils.getBaseName(srcFileName) + getOutfileSuffix(srcFileName, signOnly))
            ).getAbsoluteFile();

            if (outputFile.exists() && (!forceOverwrite || !outputFile.delete())) {
                if (forceOverwrite) {
                    logger.e("Couldn't overwrite '" + outputFile.getName() + "'. Delete manually.");
                } else {
                    logger.e("'" + outputFile.getName() + "' already exists. Use -f to overwrite.");
                }
                logger.v("Aborting...");
                break;
            }

            try {
                var relative = getRelativePath(outputFile);
                if (xApkBaseName == null) {
                    if (signOnly) {
                        sign(srcApkFile, outputFile, packageName);
                    } else {
                        patch(srcApkFile, outputFile, packageName, appComponentFactory, minSdk);
                    }
                } else {
                    patchBundle(srcApkFile, outputFile, xApkBaseName, signOnly, packageName, appComponentFactory, minSdk);
                }
                results.replace(apk, (signOnly ? "[rsigned] " : "[patched] ") + relative);
                logger.v("Finished");
            } catch (PatchError error) {
                if (multiple) {
                    logger.e(getErrorMessage(error));
                    logger.v("Skipping...");
                } else {
                    throw error;
                }
            }
        }

        if (results.values().stream().anyMatch(r -> !r.startsWith("[failed!]"))) {
            logger.v("\nOutput:");
            results.values().stream().sorted().forEach(r -> logger.v(" " + r));
            logger.v("");
        }
    }

    public StoredEntry findBaseApkFromXApk(ZFile zFile) {
        StoredEntry base = null;
        StoredEntry manifestEntry = zFile.get("manifest.json");
        if (manifestEntry != null) {
            try (InputStream is = manifestEntry.open()) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                JsonArray splits = json.getAsJsonArray("split_apks");
                for (JsonElement e : splits) {
                    if ("base".equals(e.getAsJsonObject().get("id").getAsString())) {
                        if ((base = zFile.get(e.getAsJsonObject().get("file").getAsString())) != null) {
                            if (!base.getCentralDirectoryHeader().getName().endsWith(".apk")) base = null;
                        }
                        break;
                    }
                }
            } catch (Throwable e) {
                logger.v("XApk manifest parsing failed: " + e);
            }
        }
        if (base == null) base = zFile.get("base.apk");
        if (base == null) logger.v("Base apk not found");
        return base;
    }

    public void patchBundle(File bundle, File output, String baseName, boolean signOnly, String pkg, String appComponent, int minSdk) throws Exception {
        File temp = getTempDir(internalTempDir, bundle.getName());
        TEMPORARY_FILES_CLEANUP.add(temp);
        try (var executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1)) {
            logger.d("extracting bundle");
            ZipHelpers.fastExtract(bundle, temp, executor, (size) -> {
                if (internal && size > 100_000) logger.d("extracting bundle");  // simulate yield signal
            });
            File patchDir = new File(temp, "_mrv_patched_" + System.currentTimeMillis());
            if (patchDir.exists()) {
                throw new IllegalStateException("Patch dir conflicts!");
            }
            List<Path> allPaths;
            try (Stream<Path> walk = Files.walk(temp.toPath())) {
                allPaths = walk.filter(p -> !Files.isDirectory(p) && !p.toString().contains("META-INF")).toList();
            }
            if (!patchDir.mkdirs()) throw new IllegalStateException("Failed to create patch dir!");
            var baseApk = new File(temp, baseName);
            if (!baseApk.isFile()) {
                throw new IllegalStateException("Base apk not found!");
            }
            // Patch base apk
            if (signOnly) {
                sign(baseApk, new File(patchDir, baseName), pkg);
            } else {
                patch(baseApk, new File(patchDir, baseName), pkg, appComponent, minSdk);
            }
            logger.d("signing splits");
            for (Path path : allPaths) {
                String name = path.toFile().getName();
                if (name.endsWith(".apk") && !name.equals(baseName)) {
                    File out = new File(patchDir, name);
                    out.getParentFile().mkdirs();
                    sign(path.toFile(), out, pkg, true);
                }
            }
            logger.d("repacking bundle");
            var compressor = new DeflateExecutionCompressor(executor, Deflater.BEST_SPEED);
            try (ZFile dest = ZFile.openReadWrite(output, new ZFileOptions().setCompressor(compressor))) {
                for (Path path : allPaths) {
                    File file = path.toFile();
                    String name = temp.toPath().relativize(path).toString().replace("\\", "/");
                    if (name.isEmpty()) continue;
                    if (name.endsWith(".apk")) file = new File(patchDir, name);
                    if (internal && file.length() > 10_000) logger.d("repacking bundle");  // yield
                    try (FileInputStream fis = new FileInputStream(file)) {
                        dest.add(name, fis);
                    }
                }
                if (maskPackage) {
                    dest.add(ConstantsM.MASK_MARKER, new ByteArrayInputStream(ConstantsM.MASK_MARKER.getBytes(StandardCharsets.UTF_8)));
                }
            }
        } finally {
            try (Stream<Path> walk = Files.walk(temp.toPath())) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException ignored) {}
            TEMPORARY_FILES_CLEANUP.remove(temp);
        }
    }

    public void patch(File srcApkFile, File outputFile, String pkg, String appComponent, int minSdk) throws PatchError {
        logger.d("patching files");
        final File internalApk;
        try {
            internalApk = getTempFile(internalTempDir, srcApkFile.getName());
            internalApk.delete();
            if (!fallbackMode) {
                FileUtils.copyFile(srcApkFile, internalApk);
            }
            TEMPORARY_FILES_CLEANUP.add(internalApk);
        } catch (IOException e) {
            throw new PatchError("Failed to create temp file", e);
        }

        try (var dstZFile = ZFile.openReadWrite(internalApk, Z_FILE_OPTIONS);
             var srcZFile = (!fallbackMode ? ZFile.openReadOnly(srcApkFile) :
                 dstZFile.addNestedZip((ignore) -> ORIGINAL_APK_ASSET_PATH, srcApkFile, false)
             )
        ) {
            var manifest = Objects.requireNonNull(srcZFile.get(ANDROID_MANIFEST_XML));
            try (var is = new ByteArrayInputStream(patchManifest(srcApkFile, manifest.open(), pkg, minSdk))) {
                dstZFile.add(ANDROID_MANIFEST_XML, is);
            } catch (IOException e) {
                throw new PatchError("Failed to patch manifest", e);
            }

            ARCH_LIBRARY_MAP.forEach((arch, lib) -> {
                String asset = Constants.getLibraryPath(lib);
                String entry = Constants.getLibrarySoPath(arch);
                try (var is = getClass().getClassLoader().getResourceAsStream(asset)) {
                    dstZFile.add(entry, is, false);
                } catch (IOException e) {
                    throw new PatchError("Failed to attach native libs", e);
                }
            });

            try (var is = getClass().getClassLoader().getResourceAsStream(Constants.META_LOADER_DEX_PATH)) {
                String dexIndex = fallbackMode ? "" : String.valueOf(
                    srcZFile.entries().stream().filter(e -> {
                        String name = e.getCentralDirectoryHeader().getName();
                        return name.startsWith("classes") && name.endsWith(".dex");
                    }).count() + 1
                );
                dstZFile.add("classes" + dexIndex + ".dex", is);
            } catch (IOException e) {
                throw new PatchError("Failed to attach dex", e);
            }

            try (var is = getClass().getClassLoader().getResourceAsStream(LOADER_DEX_PATH)) {
                dstZFile.add(LOADER_DEX_PATH, is);
            } catch (IOException e) {
                throw new PatchError("Failed to attach assets", e);
            }

            var modules = this.modules.stream().map(String::trim).filter(it -> !it.isBlank()).collect(Collectors.toList());
            Map<String, List<String>> prefetches = maskPackage ? DexFetcher.prefetch(srcApkFile) : Collections.emptyMap();
            var config = new PatchConfig(
                appComponent, fallbackMode, fixConflict, maskPackage, loadOnAll, modules, prefetches
            );
            var configBytes = new Gson().toJson(config).getBytes(StandardCharsets.UTF_8);
            try (var is = new ByteArrayInputStream(configBytes)) {
                dstZFile.add(CONFIG_ASSET_PATH, is);
            } catch (IOException e) {
                throw new PatchError("Failed to save config", e);
            }

            if (fallbackMode) {
            logger.d("linking files");
                try {
                    registerApkSigner(dstZFile);
                } catch (IOException | GeneralSecurityException e) {
                    throw new PatchError("Failed to register apk signer", e);
                }
                NestedZip nested = (NestedZip) srcZFile;
                for (StoredEntry entry : nested.entries()) {
                    String name = entry.getCentralDirectoryHeader().getName();
                    if (name.startsWith("classes") && name.endsWith(".dex")) continue;
                    if (dstZFile.get(name) != null) continue;
                    if (name.equals("AndroidManifest.xml")) continue;
                    if (name.startsWith("META-INF") && (name.endsWith(".SF") || name.endsWith(".MF") || name.endsWith(".RSA"))) continue;
                    nested.addFileLink(name, name);
                }
            }

            logger.d("generating apk");
            dstZFile.realign();
            dstZFile.close();
            try {
                if (fallbackMode) {
                    outputFile.delete();
                    FileUtils.moveFile(internalApk, outputFile);
                } else {
                    signApkFileInternal(internalApk, outputFile);
                }
            } catch (IOException | ApkFormatException | GeneralSecurityException e) {
                throw new PatchError("Failed to generate apk", e);
            }
        } catch (IOException e) {
            throw new PatchError("Failed to patch apk", e);
        } finally {
            internalApk.delete();
            TEMPORARY_FILES_CLEANUP.remove(internalApk);
        }
    }

    public void sign(File srcApkFile, File outputFile, String packageName) throws PatchError {
        sign(srcApkFile, outputFile, packageName, false);
    }

    public void sign(File srcApkFile, File outputFile, String packageName, boolean silent) throws PatchError {
        File internalApk;
        try {
            internalApk = getTempFile(internalTempDir, srcApkFile.getName());
            internalApk.delete();
            TEMPORARY_FILES_CLEANUP.add(internalApk);
        } catch (IOException e) {
            throw new PatchError("Failed to create temp file", e);
        }
        try {
            FileUtils.copyFile(srcApkFile, internalApk);
            if (fixConflict) {
                try (var dstZFile = ZFile.openReadWrite(internalApk, Z_FILE_OPTIONS); var srcZFile = ZFile.openReadOnly(srcApkFile)) {
                    try (var is = Objects.requireNonNull(srcZFile.get(ANDROID_MANIFEST_XML)).open()) {
                        var patched = patchApkConflicts(is, packageName, silent);
                        dstZFile.add(ANDROID_MANIFEST_XML, new ByteArrayInputStream(patched));
                    } catch (IOException e) {
                        throw new PatchError("Failed to patch manifest", e);
                    }
                    dstZFile.realign();
                } catch (IOException e) {
                    throw new PatchError("Failed to patch apk", e);
                }
            }
            if (!silent) logger.d("signing apk");
            if (fixConflict || !fallbackMode) {
                signApkFileInternal(internalApk, outputFile);
            } else {
                try (var dstZFile = ZFile.openReadWrite(internalApk)) {
                    registerApkSigner(dstZFile);
                    dstZFile.realign();
                    dstZFile.close();
                    outputFile.delete();
                    FileUtils.moveFile(internalApk, outputFile);
                }
            }
        } catch (IOException | GeneralSecurityException | ApkFormatException e) {
            throw new PatchError("Failed to sign apk", e);
        } finally {
            internalApk.delete();
            TEMPORARY_FILES_CLEANUP.remove(internalApk);
        }
    }

    private byte[] patchApkConflicts(InputStream is, String pkg, boolean silent) throws IOException {
        var os = new ByteArrayOutputStream();
        var property = new ModificationProperty();
        if (fixConflict) {
            if (!silent) logger.d("patching issues");
            property.setPermissionMapper((type, permission) -> (type == PermissionType.DECLARED_PERMISSION) ?
                ConstantsM.maskPackagedString(permission) : ConstantsM.maskFbPackagedString(permission)
            );
        }
        if (maskPackage) {
            if (!silent) logger.d("masking package");
            property.addManifestAttribute(
                new AttributeItem(NodeValue.Manifest.PACKAGE, ConstantsM.maskPackage(pkg)).setNamespace(null)
            );
            property.setAuthorityMapper(ConstantsM::maskPackagedString);
        }
        new ManifestEditor(is, os, property).processManifest();
        return os.toByteArray();
    }

    private byte[] patchManifest(File srcApkFile, InputStream is, String pkg, int minSdk) throws IOException {
        ModificationProperty property = new ModificationProperty();
        property.addUsesPermission("android.permission.QUERY_ALL_PACKAGES");
        property.addApplicationAttribute(new AttributeItem("appComponentFactory", PROXY_APP_COMPONENT_FACTORY));
        if (minSdk != 0 && minSdk < 28) property.addUsesSdkAttribute(new AttributeItem(NodeValue.UsesSDK.MIN_SDK_VERSION, 28));
        if (fixConflict) {
            logger.d("patching issues");
            property.setPermissionMapper((type, permission) -> (type == PermissionType.DECLARED_PERMISSION) ?
                    ConstantsM.maskPackagedString(permission) : ConstantsM.maskFbPackagedString(permission)
            );
        }
        if (maskPackage) {
            logger.d("masking package");
            property.addManifestAttribute(
                    new AttributeItem(NodeValue.Manifest.PACKAGE, ConstantsM.maskPackage(pkg)).setNamespace(null)
            );
            property.setAuthorityMapper(ConstantsM::maskPackagedString);
        }
        if (embedSignature) {
            logger.d("adding metadata");
            try {
                var signature = ApkSignatureHelper.getApkSignInfo(srcApkFile.getAbsolutePath());
                var config = new Gson().toJson(new ExtraConfig(signature)).getBytes(StandardCharsets.UTF_8);
                var metadata = Base64.getEncoder().encodeToString(config);
                property.addMetaData(new ModificationProperty.MetaData(ExtraConfig.KEY, metadata));
            } catch (Throwable ignored) {}
        }
        var os = new ByteArrayOutputStream();
        new ManifestEditor(is, os, property).processManifest();
        is.close(); os.flush(); os.close();
        return os.toByteArray();
    }

    private void setupSigningKey() throws IOException, GeneralSecurityException {
        String keyAlias = null;
        char[] keyPass, keyAliasPass;
        if (!internalKeystore.isEmpty()) {
            if (internalKeystore.size() != 4) throw new IllegalArgumentException(
                    "Invalid keystore details"
            );
            userKey = internalKeystore.get(0);
            keyPass = internalKeystore.get(1).toCharArray();
            keyAlias = internalKeystore.get(2);
            keyAliasPass = internalKeystore.get(3).toCharArray();
        } else if (userKey != null) {
            if (!new File(userKey).exists()) {
                throw new KeyStoreException("Keystore file doesn't exist");
            }
            keyPass = System.console().readPassword("\nKeystore password: ");
            if (userKeyPrompt) {
                keyAlias = System.console().readLine("Keystore alias: ");
                keyAliasPass = System.console().readPassword("Keystore alias password: ");
                if (keyAliasPass.length == 0) keyAliasPass = keyPass;
            } else {
                keyAliasPass = keyPass;
            }
        } else {
            keyPass = DEFAULT_KEYPASS;
            keyAlias = DEFAULT_SIGNER_NAME;
            keyAliasPass = keyPass;
        }
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (var is = userKey != null ? new FileInputStream(new File(userKey).getAbsoluteFile()) :
                getClass().getClassLoader().getResourceAsStream(getDefaultKey())) {
            keyStore.load(is, keyPass);
        }
        if (keyAlias == null) keyAlias = keyStore.aliases().nextElement();
        signingKey = (KeyStore.PrivateKeyEntry) keyStore.getEntry(keyAlias, new KeyStore.PasswordProtection(keyAliasPass));
        if (signingKey == null) throw new KeyStoreException("Keystore entry not found: " + keyAlias);
    }

    private void signApkFileInternal(File srcApkFile, File outputFile) throws IOException, ApkFormatException, GeneralSecurityException {
        List<X509Certificate> cert = Arrays.asList((X509Certificate[]) signingKey.getCertificateChain());
        var config = new ApkSigner.SignerConfig.Builder(DEFAULT_SIGNER_NAME, signingKey.getPrivateKey(), cert).build();
        new ApkSigner.Builder(Collections.singletonList(config))
                     .setV1SigningEnabled(true)
                     .setV2SigningEnabled(true)
                     .setV3SigningEnabled(true)
                     .setInputApk(srcApkFile)
                     .setOutputApk(outputFile)
                     .build()
                     .sign();
    }

    private void registerApkSigner(ZFile zFile) throws IOException, GeneralSecurityException {
        new SigningExtension(SigningOptions.builder()
            .setMinSdkVersion(28)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setCertificates((X509Certificate[]) signingKey.getCertificateChain())
            .setKey(signingKey.getPrivateKey())
            .build()
        ).register(zFile);
    }

    private static String getDefaultKey() {
        try {
            Class.forName("android.os.Build");
            return DEFAULT_SIGNING_KEY + ".bks";
        } catch (ClassNotFoundException e) {
            return DEFAULT_SIGNING_KEY + ".jks";
        }
    }

    private boolean checkInputFiles() {
        for (var path : apkPaths) {
            if (!new File(path).isFile()) {
                if (path.startsWith("-")) logger.e(" Invalid option: " + path);
                else logger.e(" '" + path + "' does not exist");
                return false;
            }
        }
        return true;
    }

    private static File getTempFile(String dir, String name) throws IOException {
        return dir == null ? Files.createTempFile("mrv-" + name, "-internal").toFile() :
            Files.createTempFile(new File(dir).toPath(), "mrv-" + name, "-internal").toFile() ;
    }

    private static File getTempDir(String dir, String name) throws IOException {
        return dir == null ? Files.createTempDirectory("mrv-" + name).toFile() :
            Files.createTempDirectory(new File(dir).toPath(), "mrv-" + name).toFile() ;
    }

    private static String getOutfileSuffix(String name, boolean signOnly) {
        return (signOnly ? SIGNED_SUFFIX : PATCHED_SUFFIX) +
            (name.contains(".") ? name.substring(name.lastIndexOf('.')) : "");
    }

    private static String getRelativePath(File file) {
        try {
           return new File("").getAbsoluteFile().toPath().relativize(file.toPath()).toString();
        } catch (Throwable throwable) { return file.getPath(); }
    }

    private static String getErrorMessage(Throwable t) {
        String message = t.getMessage();
        Throwable cause = t.getCause();
        if (cause != null && cause.getMessage() != null) {
            if (message == null) message = "";
            message += " [" + cause.getClass().getSimpleName();
            message += " - " + cause.getMessage() + "]";
        }
        return message != null ? message : t.getClass().getSimpleName();
    }

    public static void setOutputLogger(OutputLogger logger) {
        LSPatch.logger = Objects.requireNonNull(logger);
    }
}
