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
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "ResultOfMethodCallIgnored"})
public final class LSPatch {
    @SuppressWarnings("unused")
    static class PatchError extends Error {
        public PatchError(String message, Throwable cause) {
            super(message, cause);
        }

        PatchError(String message) {
            super(message);
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

    @Parameter(names = {"-v, --verbose"}, hidden = true, description = "[Internal Option]")
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

    private final static String PATCHED_SUFFIX = "-mrv.apk";
    private final static String SIGNED_SUFFIX = "-signed.apk";

    private KeyStore.PrivateKeyEntry signingKey;
    private static final String DEFAULT_SIGNER_NAME = "facebook";
    private static final char[] DEFAULT_KEYPASS = "123456".toCharArray();

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
        jCommander.parse(args);
    }

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
            } else {
                logger.e(getError(t));
            }
            if (verbose) t.printStackTrace();
        }
    }

    public void doCommandLine() throws Exception {
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
            try (var zFile = ZFile.openReadOnly(srcApkFile)) {
                var manifestEntry = zFile.get(ANDROID_MANIFEST_XML);
                if (manifestEntry == null) {
                    logger.e("Input file is not a valid apk file");
                    if (multiple) logger.v("Skipping...");
                    continue;
                }
                try (var is = manifestEntry.open()) {
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
            } catch (IOException exception) {
                logger.e(getError(exception));
                if (multiple) logger.v("Skipping...");
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

            final File outputFile = (internalOutputPath != null ?
                new File(internalOutputPath) :
                new File(outputDir, FilenameUtils.getBaseName(srcFileName) + (signOnly ? SIGNED_SUFFIX : PATCHED_SUFFIX))
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
                if (signOnly) {
                    sign(srcApkFile, outputFile);
                    results.replace(apk, "[rsigned] " + relative);
                } else {
                    patch(srcApkFile, outputFile, packageName, appComponentFactory, minSdk);
                    results.replace(apk, "[patched] " + relative);
                }
                logger.v("Finished");
            } catch (PatchError error) {
                String err = error.getMessage();
                if (error.getCause() != null) {
                    err += " [" + error.getCause().getClass().getSimpleName();
                    err += " - " + error.getCause().getMessage() + "]";
                }
                logger.e(err);
                if (multiple) logger.v("Skipping...");
            }
        }

        if (results.values().stream().anyMatch(r -> !r.startsWith("[failed!]"))) {
            logger.v("\nOutput:");
            results.values().stream().sorted().forEach(r -> logger.v(" " + r));
            logger.v("");
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

            var mods = modules.stream().map(String::trim).filter(it -> !it.isBlank()).collect(Collectors.toList());
            var config = new PatchConfig(appComponent, fallbackMode, fixConflict, maskPackage, loadOnAll, mods);
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
                    signApk(internalApk, outputFile);
                }
            } catch (IOException | ApkFormatException | GeneralSecurityException e) {
                throw new PatchError("Failed to generate apk", e);
            }
        } catch (IOException e) {
            throw new PatchError("Failed to patch apk", e);
        } finally {
            internalApk.delete();
        }
    }

    public void sign(File srcApkFile, File outputFile) throws PatchError {
        File internalApk;
        try {
            internalApk = getTempFile(internalTempDir, srcApkFile.getName());
            internalApk.delete();
        } catch (IOException e) {
            throw new PatchError("Failed to create temp file", e);
        }
        try {
            FileUtils.copyFile(srcApkFile, internalApk);
            if (fixConflict) {
                logger.d("patching issues");
                try (var dstZFile = ZFile.openReadWrite(internalApk, Z_FILE_OPTIONS); var srcZFile = ZFile.openReadOnly(srcApkFile)) {
                    try (var is = Objects.requireNonNull(srcZFile.get(ANDROID_MANIFEST_XML)).open()) {
                        dstZFile.add(ANDROID_MANIFEST_XML, new ByteArrayInputStream(patchApkConflicts(is)));
                    } catch (IOException e) {
                        throw new PatchError("Failed to patch manifest", e);
                    }
                    dstZFile.realign();
                } catch (IOException e) {
                    throw new PatchError("Failed to patch apk", e);
                }
            }
            logger.d("signing apk");
            if (fixConflict || !fallbackMode) {
                signApk(internalApk, outputFile);
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
        }
    }

    private byte[] patchApkConflicts(InputStream is) throws IOException {
        var os = new ByteArrayOutputStream();
        new ManifestEditor(is, os, new ModificationProperty()
                .setPermissionMapper((type, permission) -> (type == PermissionType.DECLARED_PERMISSION) ?
                        ConstantsM.maskPackagedString(permission) : ConstantsM.maskFbPackagedString(permission)
                )
        ).processManifest();
        return os.toByteArray();
    }

    private byte[] patchManifest(File srcApkFile, InputStream is, String pkg, int minSdk) throws IOException {
        ModificationProperty property = new ModificationProperty();
        property.addUsesPermission("android.permission.QUERY_ALL_PACKAGES");
        property.addApplicationAttribute(new AttributeItem("appComponentFactory", PROXY_APP_COMPONENT_FACTORY));
        if (minSdk != 0 && minSdk < 28) property.addUsesSdkAttribute(new AttributeItem(NodeValue.UsesSDK.MIN_SDK_VERSION, "28"));
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

    private void signApk(File srcApkFile, File outputFile) throws IOException, ApkFormatException, GeneralSecurityException {
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

    private static String getRelativePath(File file) {
        try {
           return new File("").getAbsoluteFile().toPath().relativize(file.toPath()).toString();
        } catch (Throwable throwable) { return file.getPath(); }
    }

    private static String getError(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    public static void setOutputLogger(OutputLogger logger) {
        LSPatch.logger = Objects.requireNonNull(logger);
    }
}
