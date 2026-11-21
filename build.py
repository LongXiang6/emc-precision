"""Build EMC Precision and patch a hash-pinned ProjectE 1.1.0 JAR."""

import argparse
from hashlib import file_digest
import os
from pathlib import Path
from shutil import copytree
import subprocess
from tempfile import TemporaryDirectory
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parent
UPSTREAM_SHA256 = "fec9389d8b70fcf87331a0bcc41b78ada79c1e6326ef02a7bb6db9a1d5a4c48b"
PROJECTE_NAME = "ProjectE-1.21.1-PE1.1.0-precision.jar"
ADDON_NAME = "emc_precision-1.2.0.jar"


def compile_java(javac, sources, classpath, destination):
    destination.mkdir()
    subprocess.run(
        [
            javac, "-encoding", "UTF-8", "-nowarn", "-proc:none",
            "-J-Duser.language=en", "-classpath", os.pathsep.join(map(str, classpath)),
            "-d", str(destination), *map(str, sorted(sources)),
        ],
        check=True,
    )


def patch_projecte(original, amended_classes, destination):
    replacements = {
        file.relative_to(amended_classes).as_posix(): file
        for file in amended_classes.rglob("*.class")
    }
    with ZipFile(original) as base, ZipFile(destination, "x", ZIP_DEFLATED) as result:
        for info in base.infolist():
            change = replacements.pop(info.filename, None)
            result.writestr(info, change.read_bytes() if change else base.read(info))
        for name, file in sorted(replacements.items()):
            if not name.startswith("moze_intel/projecte/emc/DecimalEmc"):
                raise ValueError(f"Unexpected new class {name}")
            result.write(file, name)
        license_text = (ROOT / "projecte-patch/LICENSE").read_text(encoding="utf-8")
        result.writestr("META-INF/LICENSE-projecte.txt", license_text)
        for license_file in sorted((ROOT / "licenses").glob("*.txt")):
            result.write(license_file, f"META-INF/{license_file.name}")


def build(args):
    with args.projecte.open("rb") as stream:
        if file_digest(stream, "sha256").hexdigest() != UPSTREAM_SHA256:
            raise ValueError("Expected the original ProjectE 1.1.0 NeoForge 1.21.1 JAR")
    classpath = [
        Path(line.strip()).resolve(strict=True)
        for line in args.classpath_file.read_text(encoding="utf-8-sig").splitlines()
        if line.strip()
    ]
    if args.output.exists():
        raise FileExistsError(args.output)
    with TemporaryDirectory() as temp:
        staged = Path(temp)
        patches = []
        mappers = []
        for source in sorted((ROOT / "projecte-patch/src/main/java").rglob("*.java")):
            target = staged / source.name
            java = source.read_text(encoding="utf-8").replace(
                "import org.apache.commons.math3.",
                "import moze_intel.projecte.shaded.org.apache.commons.math3.",
            )
            target.write_text(java, encoding="utf-8")
            (mappers if source.parent.name == "mappers" else patches).append(target)
        core_classes = staged / "projecte"
        compile_java(args.javac, patches, [*classpath, args.projecte], core_classes)
        # Match the original mapper patch, where Registry needs the explicit extension cast.
        unpatched_classpath = [
            path for path in classpath
            if not (path.name.startswith("neoforge-") and path.name.endswith("-client.jar"))
        ]
        mapper_classes = staged / "mappers"
        compile_java(args.javac, mappers, [*unpatched_classpath, args.projecte], mapper_classes)
        copytree(mapper_classes, core_classes, dirs_exist_ok=True)
        core = staged / PROJECTE_NAME
        patch_projecte(args.projecte, core_classes, core)
        addon_classes = staged / "addon"
        compile_java(
            args.javac, (ROOT / "src/main/java").rglob("*.java"),
            [*classpath, core], addon_classes,
        )
        addon = staged / ADDON_NAME
        with ZipFile(addon, "x", ZIP_DEFLATED) as jar:
            for folder in (addon_classes, ROOT / "src/main/resources"):
                for file in sorted(folder.rglob("*")):
                    if file.is_file():
                        jar.write(file, file.relative_to(folder).as_posix())
            jar.write(ROOT / "LICENSE", "META-INF/LICENSE-emc-precision.txt")
        args.output.mkdir()
        for artifact in (core, addon):
            output = args.output / artifact.name
            output.write_bytes(artifact.read_bytes())
            print(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--projecte", type=Path, required=True)
    parser.add_argument("--classpath-file", type=Path, required=True)
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--output", type=Path, default=ROOT / "dist")
    build(parser.parse_args())
