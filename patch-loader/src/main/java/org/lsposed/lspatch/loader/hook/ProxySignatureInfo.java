package org.lsposed.lspatch.loader.hook;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;

import org.lsposed.lspatch.loader.LSPApplication;
import org.lsposed.lspatch.share.ExtraConfig;
import org.lsposed.lspatch.share.ConstantsM;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ProxySignatureInfo implements Parcelable.Creator<PackageInfo> {
    private static final Map<String, String> signatures = new HashMap<>();
    private final Parcelable.Creator<PackageInfo> original = PackageInfo.CREATOR;

    private final Context context;
    private final String requester;

    public ProxySignatureInfo(Context context) {
        this.context = context;
        this.requester = context.getPackageName();
    }

    @Override
    public PackageInfo createFromParcel(Parcel source) {
        PackageInfo packageInfo = original.createFromParcel(source);
        replaceSignature(packageInfo);
        return packageInfo;
    }

    @Override
    public PackageInfo[] newArray(int size) {
        return original.newArray(size);
    }

    @SuppressWarnings("deprecation")
    public void replaceSignature(PackageInfo packageInfo) {
        String replacement;
        String pkg = packageInfo.packageName;
        String spoof = requester.equals(pkg) ? "self" : pkg;
        if (ConstantsM.isInvalidPackage(pkg) || (replacement = findReplacement(packageInfo)) == null) {
            return;
        }
        if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
            packageInfo.signatures[0] = new Signature(replacement);
            Log.i(LSPApplication.TAG, "Signature[" + requester + " => " + spoof + "]");
        }
        if (packageInfo.signingInfo != null) {
            Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
            if (signaturesArray != null && signaturesArray.length > 0) {
                signaturesArray[0] = new Signature(replacement);
                Log.i(LSPApplication.TAG, "SigningInfo[" + requester + " => " + spoof + "]");
            }
        }
    }

    private String findReplacement(PackageInfo packageInfo) {
        String replacement = null;
        if (hasSignatures(packageInfo)) {
            String pkg = packageInfo.packageName;
            if (ConstantsM.isSignatureHardcoded(pkg)) {
                replacement = ConstantsM.getSignature(pkg);
            } else if (signatures.containsKey(pkg)) {
                replacement = signatures.get(pkg);
            } else {
                replacement = extractSignature(pkg);
                signatures.put(pkg, replacement);
            }
        }
        return replacement;
    }

    private String extractSignature(String packageName) {
        try {
            var metaData = context.getPackageManager().getApplicationInfo(
                packageName, PackageManager.GET_META_DATA
            ).metaData;
            if (metaData == null) return null;
            String encoded = metaData.getString(ExtraConfig.KEY);
            if (encoded != null) {
                Log.i(LSPApplication.TAG, "Extracting signature from: " + packageName);
                var decoded = Base64.decode(encoded, Base64.DEFAULT);
                var json = new String(decoded, StandardCharsets.UTF_8);
                return new Gson().fromJson(json, ExtraConfig.class).signature;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @SuppressWarnings("deprecation")
    private boolean hasSignatures(PackageInfo info) {
        return (info.signatures != null && info.signatures.length > 0) || info.signingInfo != null;
    }
}
