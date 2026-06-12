# MRVPatcher

MRVPatcher is an APK patching tool that lets you use [ChatHeadEnabler](https://github.com/NeonOrbit/ChatHeadEnabler)
in Messenger (along with other Xposed modules), on non-rooted devices.

Supported Android versions: 9 to 17+  
Android app alternative: [MRVPatchManager](https://github.com/NeonOrbit/MRVPatchManager)

### Patching Instructions
- Download and Install [JDK 21+](https://adoptium.net/temurin/releases/?version=21)
- Download the [MRVPatcher](https://github.com/NeonOrbit/MRVPatcher/releases/latest) tool
- Rename the tool to `MRVPatcher.jar`
- Download a Messenger APK from [ApkMirror](https://www.apkmirror.com/apk/facebook-2/messenger)
- Rename the APK to `Messenger.apk`
- Place the patcher and APK in the same folder.
- Open a terminal (or command prompt) in that folder.
- Run the following command:
```shell
java -jar MRVPatcher.jar Messenger.apk
```
- A patched file named "Messenger-mrv.apk" will be created.
- Repeat the same steps for any other Facebook apps you want to patch.

List of available options:
```
java -jar MRVPatcher.jar --help
Usage: MRVPatcher [options] apks
  Options:
    -h, --help
    -o, --output
      Output directory
    -ks, --keystore
      Sign using an external keystore file
    -ksp, --ks-prompt
      Prompt for the keystore alias details
    -p, --patch
      Forcefully patch apps that do not require patching
    --fix-conf
      Fix apk-conflict installation issue [If unable to remove the preinstalled fb apps]
    --mask-pkg
      Mask package name [In case you are unable to remove the preinstalled messenger app]
    --sign-only
      Skip the patching process (apk signing only)
    --fallback
      Use fallback mode [In case default patched apps do not work]
    --modules
      Allow third-party modules [package names separated by commas]
Experimental:
    --no-restriction
      Remove patching restrictions (Patch or mask any apps)
    --load-on-all
      Load modules for all patched apps (Default: Messenger only)
    --key-args
      Pass keystore info in command args [Args order: path pass alias alias-pass]
    -v, --verbose
      Verbose output
```
For a detailed explanation, please refer to the [xda thread](https://forum.xda-developers.com/t/4331215).

## Credits

- LSPatch from LSPosed
- Vector from JingMatrix
- ManifestEditor from WindySha
- Apksigner and Apkzlib from AOSP

## License

MRVPatcher is licensed under the **GNU General Public License v3 (GPL-3)** (http://www.gnu.org/copyleft/gpl.html).
