# Build from Source

Use Python 3.11+ and JDK 21. The build compiles the changed ProjectE files against the original JAR and compiles the add-on against the result. Dependencies come from your own development or launcher setup.

Obtain the original `ProjectE-1.21.1-PE1.1.0.jar` from [ProjectE's official files](https://www.curseforge.com/minecraft/mc-mods/projecte/files). Its SHA-256 is `fec9389d8b70fcf87331a0bcc41b78ada79c1e6326ef02a7bb6db9a1d5a4c48b`.

Create a UTF-8 classpath file with one absolute JAR path per line. Put `neoforge-21.1.265-client.jar` first, followed by the mapped Minecraft 1.21.1 `client-1.21.1-20240808.144430-srg.jar`, the NeoForge universal JAR and loader libraries, Minecraft's libraries, JetBrains Annotations 24.1.0, TeamProjectE 1.0.0, ProjectE Integration 8.4.0 and Kaleidoscope Cookery 1.6.0 for Minecraft 1.21.1 NeoForge. The two recipe-integration libraries are compile inputs even when the integration is unused in-game.

```sh
python build.py --projecte /path/to/ProjectE-1.21.1-PE1.1.0.jar --classpath-file /path/to/classpath.txt
```

Pass `--javac /path/to/javac` if JDK 21 is outside your PATH. Output goes into `dist/`. Builds from source include license notices inside the JARs. The original ProjectE source is available at the commit in the [third-party notices](THIRD_PARTY.md).
