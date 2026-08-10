# Notepadmm

[中文](readme.md)

[![OSCS Status](https://www.oscs1024.com/platform/badge/jzlhll/Notepadmm.svg?size=small)](https://www.oscs1024.com/project/jzlhll/Notepadmm?ref=badge_small)
![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS-orange)
![Version](https://img.shields.io/badge/version-v1.6.0-green)

Notepadmm is a desktop editor for large text files and log analysis. It was created because macOS lacked a convenient multi-line search tool comparable to Notepad++. The project is built with JDK 17, JavaFX 21, Java, Kotlin, RichTextFX, and JFoenix, and runs on Windows and macOS.

## Key features

- Multi-pattern search highlighting: assign different background and text colors to keywords or regular expressions for quick log analysis.
- Standard search: supports regular expressions, case sensitivity, whole-word matching, the 10 most recent searches, and a detachable results window.
- Large-text viewing and editing: supports word wrapping, font and font-size adjustments, text encoding selection, blank-line insertion, and other common operations.
- Workspace: browse files from the directory tree on the left.
- Syntax highlighting and simple image previews.

![Multi-pattern search highlighting](previews/advance_search.png)

![General editing](previews/normal.png)

![Standard search](previews/normal_search.png)

![Syntax highlighting](previews/colors.png)

## Gradle tasks

### Run the development build

After installing JDK 17, run the following command from the project root:

```shell
./gradlew :app:run
```

On Windows, use:

```bat
gradlew.bat :app:run
```

`:app:run` compiles `BaseParty`, `BaseUiLibs`, and `app`, assembles the module path and required VM options, and launches the application directly. It is intended for development and debugging and does not create an installer.

### Create a distribution

Before packaging for the first time, copy `local.properties.example` to `local.properties` and set the `packageJdk.*` JDK path required by the selected target. macOS packaging also requires a code-signing certificate whose name matches `packageMacSigningKey` in `gradle.properties`. Run only one target task at a time. macOS targets can only be built on macOS, and Windows targets can only be built on Windows.

| Gradle task | Purpose |
| --- | --- |
| `mainShAllMacArm64` | Prepares a macOS Apple Silicon (ARM64) distribution and generates `buildRoot/jpackageCmd.sh`. |
| `mainShAllMacX64` | Prepares a macOS Intel (x64) distribution and generates `buildRoot/jpackageCmd.sh`. |
| `mainShAllWindowsArm64` | Prepares a Windows ARM64 distribution and generates installer and portable jpackage scripts. The target JDK must include Windows ARM64 JavaFX. |
| `mainShAllWindowsX64` | Prepares a Windows x64 distribution and generates installer and portable jpackage scripts. |

For example, on an Apple Silicon Mac:

```shell
./gradlew mainShAllMacArm64
./buildRoot/jpackageCmd.sh
```

On Windows x64:

```bat
gradlew.bat mainShAllWindowsX64
buildRoot\jpackageCmdExe.bat
```

On Windows, `jpackageCmdExe.bat` creates an `.exe` installer, while `jpackageCmdGreenExe.bat` creates a portable application directory. On macOS, `jpackageCmd.sh` creates a `.dmg`. All final artifacts are written to `dist`. The four `mainShAll...` tasks prepare the module JARs, third-party dependencies, and resources; analyze and create a minimal JRE; obfuscate the main application JAR; and generate the platform-specific jpackage scripts. They do not execute the generated scripts.

The Gradle Wrapper manages dependencies, the module path, and runtime options. The manual Maven, module-path, and VM arguments from the previous documentation are no longer required. When adding a third-party dependency or project module, see the [build notes](docs/编译注意事项.md).

## Known issues

- File associations still have room for improvement.
- The Windows packaging tools still have some limitations. Notepad++ remains the better choice for basic text editing on Windows.
