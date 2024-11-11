package org.lsposed.lspatch.share;

import java.util.Set;

public final class ConstantsM {
  public static final class DEX_KEYS {
    public static final String CLS_ORCA_PKG_PROVIDER = "orca-pkg-provider";
  }

  public static final String DEFAULT_TARGET_PACKAGE = "com.facebook.orca";
  public static final String VALID_FB_PACKAGE_PREFIX = "com.facebook.";
  public static final String VALID_IG_PACKAGE_PREFIX = "com.instagram.";
  public static final String VALID_WA_PACKAGE = "com.whatsapp";
  public static final String MASK_PREFIX = "mrv.masked.";

  public static String maskPackage(String pkg) {
    return pkg.startsWith(MASK_PREFIX) ? pkg : MASK_PREFIX + pkg;
  }

  public static String maskPackagedString(String arg) {
    return maskPackage(arg.startsWith(".") ? arg.substring(1) : arg);
  }

  public static String maskFbPackagedString(String arg) {
    return arg.contains(VALID_FB_PACKAGE_PREFIX) ? maskPackagedString(arg) : arg;
  }

  public static String removeMask(String pkg) {
    return pkg.startsWith(MASK_PREFIX) ? pkg.replace(MASK_PREFIX, "") : pkg;
  }

  public static boolean isTargetPackage(String pkg) {
    return pkg.equals(DEFAULT_TARGET_PACKAGE);
  }

  public static boolean isInvalidPackage(String pkg) {
    return !pkg.startsWith(VALID_FB_PACKAGE_PREFIX) &&
           !pkg.startsWith(VALID_IG_PACKAGE_PREFIX) &&
           !pkg.startsWith(MASK_PREFIX) && !pkg.contains(VALID_WA_PACKAGE);
  }

  public static final Set<String> DEFAULT_FB_PACKAGES = Set.of(
      "com.facebook.orca",
      "com.facebook.katana",
      "com.facebook.lite",
      "com.facebook.mlite",
      "com.facebook.pages.app"
  );

  public static final Set<String> DEFAULT_IG_PACKAGES = Set.of(
          "com.instagram.android",
          "com.instagram.lite"
  );

  public static boolean isSignatureHardcoded(String pkg) {
    return DEFAULT_FB_PACKAGES.contains(pkg) || DEFAULT_IG_PACKAGES.contains(pkg);
  }

  public static String getSignature(String pkg) {
    if (DEFAULT_FB_PACKAGES.contains(pkg)) return DEFAULT_FB_SIGNATURE;
    if (DEFAULT_IG_PACKAGES.contains(pkg)) return DEFAULT_IG_SIGNATURE;
    throw new IllegalArgumentException(pkg);
  }

  public static final String DEFAULT_FB_SIGNATURE = "30820268308201d102044a9c4610300d06092a864886f7" +
      "0d0101040500307a310b3009060355040613025553310b3009060355040813024341311230100603550407130950" +
      "616c6f20416c746f31183016060355040a130f46616365626f6f6b204d6f62696c653111300f060355040b130846" +
      "616365626f6f6b311d301b0603550403131446616365626f6f6b20436f72706f726174696f6e3020170d30393038" +
      "33313231353231365a180f32303530303932353231353231365a307a310b3009060355040613025553310b300906" +
      "0355040813024341311230100603550407130950616c6f20416c746f31183016060355040a130f46616365626f6f" +
      "6b204d6f62696c653111300f060355040b130846616365626f6f6b311d301b0603550403131446616365626f6f6b" +
      "20436f72706f726174696f6e30819f300d06092a864886f70d010101050003818d0030818902818100c207d51df8" +
      "eb8c97d93ba0c8c1002c928fab00dc1b42fca5e66e99cc3023ed2d214d822bc59e8e35ddcf5f44c7ae8ade50d7e0" +
      "c434f500e6c131f4a2834f987fc46406115de2018ebbb0d5a3c261bd97581ccfef76afc7135a6d59e8855ecd7eac" +
      "c8f8737e794c60a761c536b72b11fac8e603f5da1a2d54aa103b8a13c0dbc10203010001300d06092a864886f70d" +
      "0101040500038181005ee9be8bcbb250648d3b741290a82a1c9dc2e76a0af2f2228f1d9f9c4007529c446a70175c" +
      "5a900d5141812866db46be6559e2141616483998211f4a673149fb2232a10d247663b26a9031e15f84bc1c74d141" +
      "ff98a02d76f85b2c8ab2571b6469b232d8e768a7f7ca04f7abe4a775615916c07940656b58717457b42bd928a2";

  public static final String DEFAULT_IG_SIGNATURE = "3082024d308201b6a00302010202044f31d2cb300d0609" +
      "2a864886f70d0101050500306a310b3009060355040613025553311330110603550408130a43616c69666f726e69" +
      "61311630140603550407130d53616e204672616e636973636f31163014060355040a130d496e7374616772616d20" +
      "496e63311630140603550403130d4b6576696e2053797374726f6d3020170d3132303230383031343133315a180f" +
      "32313132303131353031343133315a306a310b3009060355040613025553311330110603550408130a43616c6966" +
      "6f726e6961311630140603550407130d53616e204672616e636973636f31163014060355040a130d496e73746167" +
      "72616d20496e63311630140603550403130d4b6576696e2053797374726f6d30819f300d06092a864886f70d0101" +
      "01050003818d003081890281810089ebcac015660b42a5c080bf694c52e29e9df83a4c94964b022ca38d2ba2157d" +
      "8e4650955c787906ac344bdb8b7d202a92231403d48e9e2f0df3cb917cfa9b9741314c85052673d42ad00f2c251b" +
      "e4a6b012fb9d5b33131b0e5ca0b9193856dc311dc65dc45f97d2632e72bec2b4964adfd5d30675d5d372fbaf1135" +
      "9a7afb550203010001300d06092a864886f70d0101050500038181002aefd84526b570192967b679a685bcdc12cf" +
      "40030589594d04d885cfa8a311372fb93f2c1c8ba636f061aeb87207f5a1ad26fe58747c30714f1e9b918ab2e090" +
      "d5250307655eeab5fede1e6409316c5d29779c037b550f29bcad40fa70c947b616cc05daa5532c0ecc3ece773a71" +
      "f37287a4ac32f2bd7feede847cbac5671969";

  @SuppressWarnings("unused")
  public static final String DEFAULT_MRV_SIGNATURE = "30820315308201fda003020102020470cc0d35300d060" +
      "92a864886f70d01010b0500303b310b3009060355040613025553310b3009060355040813024341310c300a06035" +
      "5040a13034d52563111300f0603550403130846616365626f6f6b301e170d3231303932343139353433355a170d3" +
      "436303931383139353433355a303b310b3009060355040613025553310b3009060355040813024341310c300a060" +
      "355040a13034d52563111300f0603550403130846616365626f6f6b30820122300d06092a864886f70d010101050" +
      "00382010f003082010a0282010100f4ae2aa5cdb81121fe2ca93270afdd488f5b2fd5db74c39a8d9f93ebc483ccc" +
      "cbc83ee1c8b06570032339b9d38520e8356e99e0efdd23a3897eee3a692776d061676d042e353b5b8bce00db3a15" +
      "b5fc10cef61158f240c87b93a25805ffb01c209a47f40c859f1f62e959684d7183a1fe2a3342464c06f00091fb05" +
      "d7251fd08c8e8bb8efad3a2557a0af0f578f096303ddd803dd85e11c8eda5f7cb08523a0322d96020495264389cc" +
      "70e3d4efd3d97036bfb0a14217f34dca013457a5197a71a87b28421708e988c56d7148c93746afe4bacae0862dc0" +
      "61de5862aca7c21b9397bf9db3ccb11d4ca50ffcf60dbb30fe82df223273807aef7ed8c4498db99e70203010001a" +
      "321301f301d0603551d0e041604141639ece5fadb405c7a29b6cb837c608432b2b32d300d06092a864886f70d010" +
      "10b050003820101003965a69e1d98611089592d16a6a4e81de52f04b492e78d20d33c3e983896d02d9310c662256" +
      "c7cca080ce3a65d89bd574815268917154b51633053a7464282df464c8d174cf4a9faf20a0c74d6ccce080b6475d" +
      "c709980d04092ae727006f334d4baa318348fdba1d8a58864e89b1d3efe04d76781b012f45ac72fb77798325d775" +
      "d84ec8b4e03899bbeaf31d91385a0b5d12d0d14677b003b12fb3cb87d35363e8e738c2722a17502a69c36fd66f64" +
      "861bb666a0967d9fb9337d3b9fe23618f99e1fb97b82b62909ab5b7db21554c82e6621ddd99b3c4ffa838c3df63b" +
      "c61e684722bd0b501c7d592545f9c6ce08cfa23729a516961ca549a87313f98e098ce";
}
