package org.lsposed.patch;

import org.lsposed.lspatch.share.ConstantsM;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.neonorbit.dexplore.DexFactory;
import io.github.neonorbit.dexplore.DexOptions;
import io.github.neonorbit.dexplore.Dexplore;
import io.github.neonorbit.dexplore.filter.ClassFilter;
import io.github.neonorbit.dexplore.filter.DexFilter;
import io.github.neonorbit.dexplore.filter.MethodFilter;
import io.github.neonorbit.dexplore.filter.ReferenceTypes;
import io.github.neonorbit.dexplore.result.ClassData;
import io.github.neonorbit.dexplore.result.MethodData;

public final class DexFetcher {
    public static Map<String, List<String>> prefetch(File file) {
        DexOptions options = DexOptions.getDefault(); options.rootDexOnly = true;
        Dexplore dexplore = DexFactory.load(file.getAbsolutePath(), options);
        Map<String, List<String>> results = new HashMap<>();
        results.put(ConstantsM.DEX_KEYS.CLS_ORCA_AUTO_BACK_INIT, orcaAutoBackupInit(dexplore));
        return results;
    }

    // In the masked version, the class constructor throws an exception if context.getPackageName() returns the masked package.
    //   Solution: Hook getPackageName() to return the original package during the execution of the class constructor.
    // CMD: dexplore s Messenger.apk -rt sm -ref 'autobackupprefs' 'com.facebook.orca' 'getPackageName' 'java.util.NoSuchElementException'
    private static List<String> orcaAutoBackupInit(Dexplore dexplore) {
        return dexplore.findClasses(DexFilter.MATCH_ALL,
            ClassFilter.builder()
                .defaultSuperClass().noInterfaces()
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL)
                .setReferenceTypes(ReferenceTypes.builder().addString().addMethodWithDetails().build())
                .setReferenceFilter(pool ->
                    pool.stringsContain("autobackupprefs") && pool.stringsContain("com.facebook.orca") &&
                        pool.contains("getPackageName") && pool.contains("java.util.NoSuchElementException")
                ).build(),
            1
        ).stream().map(ClassData::getClazz).collect(Collectors.toList());
    }

    // dexplore s Messenger.apk -m m -mdv 'f:public+static,r:java.lang.String,p:int' -rt s -ref "com.facebook.orca"
    @SuppressWarnings("unused")  // ignored
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
        ).stream().map(MethodData::getClazz).collect(Collectors.toList());
    }
}
