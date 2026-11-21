# EMC Precision

EMC Precision adds one decimal place to ProjectE on Minecraft Java Edition 1.21.1 with NeoForge. Amounts are truncated toward zero at the first decimal place. For example, 12.59 becomes 12.5, and 0.09 contributes 0 EMC. It tracks fractional balances through transmutation, condensers, and TeamProjectE.

The automatic pricer keeps existing EMC values and estimates missing values from recipe inputs and yields, same-material variants, tags, and category references. ProjectE's explicit exclusions still apply. The estimates are intended as starting values for modpacks. Set a ProjectE custom value to override an estimate. Set `autoPriceMissingItems=false` in `config/emc_precision-common.toml` to disable estimates.

## Installation

Use Java 21 and Minecraft 1.21.1 with NeoForge. NeoForge 21.1.265 was used for the full modpack test.

1. Download the two JARs from the [release](https://github.com/LongXiang6/emc-precision/releases). Install `emc_precision-1.2.0.jar` and `ProjectE-1.21.1-PE1.1.0-precision.jar` together.
2. Remove the standard ProjectE JAR from the same `mods` folder before launching. The companion JAR provides the same `projecte` mod ID and supplies the decimal API.
3. Install [TeamProjectE 1.0.0 for NeoForge 1.21.1](https://modrinth.com/mod/teamprojecte/versions). Use the same set of JARs on dedicated servers and their clients.

ProjectE Integration and Kaleidoscope Cookery enable the additional Cookery recipe mapper when both are available. Devices using ProjectE's legacy integer interface retain its integer granularity.

## Verification

The 1.2.0 test in a 204-mod NeoForge profile registered 11,699 items. The pricer added values to 3,516 items and preserved all 7,820 existing prices. A Create slab priced at 0.5 EMC was learned, transmuted, saved, and reloaded. The release JARs have the same SHA-256 values as the installed pair. See `SHA256SUMS.txt` in the release.

## Source and feedback

`src/main/java` and `src/main/resources` contain the add-on. `projecte-patch` contains the changed Java files relative to ProjectE commit `f432b0c66837759fb0731c9144dc53176b949c5d` and the original license. [Build instructions](BUILD.md) list the exact bytecode and dependency inputs.

Report issues with an item ID, the EMC you observed, reproduction steps, and the Minecraft, NeoForge, and mod versions. Use [Issues](https://github.com/LongXiang6/emc-precision/issues) for the add-on or the decimal patch.

The add-on and original changes use MIT. The companion is an unofficial build of [ProjectE](https://github.com/sinkillerj/ProjectE), credited to its upstream contributors, with a shaded Apache Commons Math dependency. See [third-party notices](THIRD_PARTY.md).

## 中文

为 ProjectE 提供一位小数 EMC，多余小数位直接舍去。对缺价物品按配方、同材质变体、标签和类别估价，保留已有价格及 ProjectE 的明确禁价。安装时把原 ProjectE JAR 换成发行页的 `precision` 补丁 JAR，并装上 EMC Precision 与 TeamProjectE 1.0.0。适用 Minecraft 1.21.1 NeoForge。
