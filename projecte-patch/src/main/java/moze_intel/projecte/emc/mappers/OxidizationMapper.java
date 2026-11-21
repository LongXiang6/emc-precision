package moze_intel.projecte.emc.mappers;

import moze_intel.projecte.PECore;
import moze_intel.projecte.api.mapper.EMCMapper;
import moze_intel.projecte.api.mapper.IEMCMapper;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.config.PEConfigTranslations;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.IRegistryExtension;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

@EMCMapper
public class OxidizationMapper implements IEMCMapper<NormalizedSimpleStack, Long> {
    @Override
    public void addMappings(IMappingCollector<NormalizedSimpleStack, Long> mapper, ReloadableServerResources resources,
                            RegistryAccess access, ResourceManager resourceManager) {
        var blocks = access.registryOrThrow(Registries.BLOCK);
        var entries = ((IRegistryExtension<Block>) blocks).getDataMap(NeoForgeDataMaps.OXIDIZABLES);
        int conversions = 0;
        int skipped = 0;
        for (var entry : entries.entrySet()) {
            var source = blocks.get(entry.getKey());
            var target = entry.getValue().nextOxidationStage();
            if (source == null || source.asItem() == Items.AIR || target.asItem() == Items.AIR
                    || source.asItem() == target.asItem()) {
                skipped++;
                continue;
            }
            var unweathered = NSSItem.createItem(source);
            var weathered = NSSItem.createItem(target);
            mapper.addConversion(1, weathered, EMCHelper.intMapOf(unweathered, 1));
            mapper.addConversion(1, unweathered, EMCHelper.intMapOf(weathered, 1));
            conversions += 2;
        }
        PECore.LOGGER.info("Oxidization EMC conversions: {} registered, {} without distinct items skipped",
                           conversions, skipped);
    }

    @Override
    public String getName() {
        return PEConfigTranslations.MAPPING_OXIDATION_MAPPER.title();
    }

    @Override
    public String getTranslationKey() {
        return PEConfigTranslations.MAPPING_OXIDATION_MAPPER.getTranslationKey();
    }

    @Override
    public String getDescription() {
        return PEConfigTranslations.MAPPING_OXIDATION_MAPPER.tooltip();
    }
}
