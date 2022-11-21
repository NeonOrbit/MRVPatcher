# MRVPatcher

MRVPatcher is a patching tool to use [ChatHeadEnabler](https://github.com/NeonOrbit/ChatHeadEnabler) on non-rooted devices.

Android version of the patcher: [MRVPatch Manager](https://github.com/NeonOrbit/MRVPatchManager)

Check [xda thread](https://forum.xda-developers.com/t/4331215) for details.

### Patching Instruction
- Download [MRVPatcher](https://github.com/NeonOrbit/MRVPatcher/releases/latest) tool
- Download and Install [Java JDK 11+](https://adoptium.net/?variant=openjdk11&jvmVariant=hotspot)
- Download Messenger apk from [ApkMirror](https://www.apkmirror.com/apk/facebook-2/messenger)
- Move the MRVPatcher and Messenger to a separate folder.
- Open terminal (or cmd) on that folder.
- Run command:
```shell
java -jar MRVPatcher.jar Messenger.apk
```
- A new file Messenger-mrv.apk will be produced.
- Follow the same instructions for any other Facebook apps.

All available options:
```shell
java -jar MRVPatcher.jar --help
```

## Credits

- Based on LSPatch

## License

MRVPatcher is licensed under the **GNU General Public License v3 (GPL-3)** (http://www.gnu.org/copyleft/gpl.html).
