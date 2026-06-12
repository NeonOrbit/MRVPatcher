//
// Created by VIP on 2021/4/25.
//

#include "bypass_sig.h"

#include "../src/native_api.h"
#include "elf_util.h"
#include "logging.h"
#include "native_util.h"
#include "patch_loader.h"
#include "utils/hook_helper.hpp"
#include "utils/jni_helper.hpp"

using lsplant::operator""_sym;

namespace lspd {

    std::string apkPath;
    std::string redirectPath;

    inline static constexpr auto kLibCName = "libc.so";

    std::unique_ptr<const SandHook::ElfImg> &GetC(bool release = false) {
        static std::unique_ptr<const SandHook::ElfImg> kImg = nullptr;
        if (release) {
            kImg.reset();
        } else if (!kImg) {
            kImg = std::make_unique<SandHook::ElfImg>(kLibCName);
        }
        return kImg;
    }

    inline static auto __openat_ =
            "__openat"_sym.hook->*[]<lsplant::Backup auto backup>(int fd, const char *pathname, int flag,
                                                                  int mode) static -> int {
                if (pathname == apkPath) {
                    LOGD("Redirect openat from {} to {}", pathname, redirectPath);
                    return backup(fd, redirectPath.c_str(), flag, mode);
                }
                return backup(fd, pathname, flag, mode);
            };

    bool HookOpenat(const lsplant::HookHandler &handler) { return handler(__openat_); }

    LSP_DEF_NATIVE_METHOD(void, SigBypass, enableOpenatHook, jstring origApkPath,
                          jstring cacheApkPath) {
        auto r = HookOpenat(lsplant::InitInfo{
                .inline_hooker =
                [](auto t, auto r) {
                    void *bk = nullptr;
                    return HookInline(t, r, &bk) == 0 ? bk : nullptr;
                },
                .art_symbol_resolver = [](auto symbol) { return GetC()->getSymbAddress(symbol); },
        });
        if (!r) {
            LOGE("Hook __openat fail");
            return;
        }
        lsplant::JUTFString str1(env, origApkPath);
        lsplant::JUTFString str2(env, cacheApkPath);
        apkPath = str1.get();
        redirectPath = str2.get();
        LOGD("apkPath {}", apkPath.c_str());
        LOGD("redirectPath {}", redirectPath.c_str());
        GetC(true);
    }

    static JNINativeMethod gMethods[] = {
            LSP_NATIVE_METHOD(SigBypass, enableOpenatHook, "(Ljava/lang/String;Ljava/lang/String;)V")};

    void RegisterBypass(JNIEnv *env) { REGISTER_LSP_NATIVE_METHODS(SigBypass); }

}  // namespace lspd