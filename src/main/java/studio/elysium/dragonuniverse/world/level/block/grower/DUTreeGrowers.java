package studio.elysium.dragonuniverse.world.level.block.grower;

import net.minecraft.world.level.block.grower.TreeGrower;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.level.levelgen.DUConfiguredFeatures;

import java.util.Optional;

public class DUTreeGrowers {
    public static final TreeGrower EXAMPLE = new TreeGrower(DragonUniverse.MODID + ":example",
            Optional.empty(), Optional.of(DUConfiguredFeatures.EXAMPLE_TREE_KEY), Optional.empty());
}
