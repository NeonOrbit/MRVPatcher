# MRVPatcher

MRVPatcher is an APK patching tool that enables you to use [ChatHeadEnabler](https://github.com/NeonOrbit/ChatHeadEnabler) 
(along with other Xposed modules) on non-rooted devices.

Android version of the patcher: [MRVPatchManager](https://github.com/NeonOrbit/MRVPatchManager)

### Patching Instructions
- Download the [MRVPatcher](https://github.com/NeonOrbit/MRVPatcher/releases/latest) tool
- Download and Install [Java JDK 17+](https://adoptium.net/temurin/releases/?variant=openjdk17&jvmVariant=hotspot)
- Download a Messenger APK from [ApkMirror](https://www.apkmirror.com/apk/facebook-2/messenger)
- Move the Patcher and the APK to a separate folder.
- Open a terminal (or command prompt) in that folder.
- Run the following command:
```shell
java -jar MRVPatcher.jar Messenger.apk
```
- A new file named "Messenger-mrv.apk" will be generated.
- Repeat these steps for other Facebook apps as well.

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
      Fix apk-conflicts [In case you are unable to remove the preinstalled fb apps]
    --mask-pkg
      Mask package name [In case you are unable to remove the preinstalled messenger app]
    --sign-only
      Skip the patching process (apk signing only)
    --fallback
      Use fallback mode [In case default patched apps do not work]
    --modules
      Allow third-party modules [package names separated by comma]
```
For a detailed explanation, please refer to the [xda thread](https://forum.xda-developers.com/t/4331215).

## Credits

- Based on LSPatch

## License

MRVPatcher is licensed under the **GNU General Public License v3 (GPL-3)** (http://www.gnu.org/copyleft/gpl.html).
