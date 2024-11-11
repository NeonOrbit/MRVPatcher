package org.lsposed.patch;

import org.lsposed.lspatch.share.ConstantsM;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.neonorbit.dexplore.DexFactory;
import io.github.neonorbit.dexplore.DexOptions;
import io.github.neonorbit.dexplore.Dexplore;
import io.github.neonorbit.dexplore.filter.ClassFilter;
import io.github.neonorbit.dexplore.filter.DexFilter;
import io.github.neonorbit.dexplore.filter.MethodFilter;
import io.github.neonorbit.dexplore.filter.ReferenceTypes;
import io.github.neonorbit.dexplore.result.MethodData;

public final class DexFetcher {
    public static Map<String, List<String>> prefetch(File file) {
        DexOptions options = DexOptions.getDefault(); options.rootDexOnly = true;
        Dexplore dexplore = DexFactory.load(file.getAbsolutePath(), options);
        Map<String, List<String>> results = new HashMap<>();
        results.put(ConstantsM.DEX_KEYS.CLS_ORCA_PKG_PROVIDER, orcaPackageConstantProviders(dexplore));
        return results;
    }

    // dexplore s base.apk -m m -cdv 'f:public,s:java.lang.Object,i:' -mdv 'f:public+static,r:java.lang.String,p:int' -rt s -ref "com.facebook.orca"
    private static List<String> orcaPackageConstantProviders(Dexplore dexplore) {
        return dexplore.findMethods(DexFilter.MATCH_ALL,
            ClassFilter.builder()
                .setModifiers(Modifier.PUBLIC)
                .defaultSuperClass()
                .noInterfaces()
                .build(),
            MethodFilter.builder()
                .setModifiers(Modifier.PUBLIC | Modifier.STATIC)
                .setParamList(Collections.singletonList("int"))
                .setReturnType(String.class.getName())
                .setReferenceTypes(ReferenceTypes.STRINGS_ONLY)
                .setReferenceFilter(pool -> pool.stringsContain("com.facebook.orca"))
                .build(),
            -1
        ).stream().map(MethodData::getClazz).toList();
    }
}
