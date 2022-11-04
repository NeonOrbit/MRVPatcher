package org.lsposed.lspatch.share;

public class Constants {
    public static final String TAG = "MRVPatch";

    public static final String MRV_DATA_DIR = "mrvdata";
    public static final String LSP_LIB_NAME = "liblspatch";
    public static final String MRV_ASSET_DIR = "assets/mrvdata";
    public static final String LIB_ASSET_DIR = MRV_ASSET_DIR + "/so";
    public static final String LOADER_DEX_PATH = MRV_ASSET_DIR + "/loader";
    public static final String META_LOADER_DEX_PATH = MRV_ASSET_DIR + "/metaloader";
    
    public static final String CONFIG_ASSET_PATH = MRV_ASSET_DIR + "/config.json";
    public static final String ORIGINAL_APK_ASSET_PATH = MRV_ASSET_DIR + "/origin.pkg";
    public static final String PROXY_APP_COMPONENT_FACTORY = "org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub";

    public static String getLibraryPath(String arch) {
        return LIB_ASSET_DIR + "/" + arch + "/" + LSP_LIB_NAME;
    }

    public static String getLibrarySoPath(String arch) {
        return LIB_ASSET_DIR + "/" + arch + "/" + LSP_LIB_NAME + ".so";
    }
}
