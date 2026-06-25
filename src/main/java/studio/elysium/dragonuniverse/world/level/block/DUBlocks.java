package studio.elysium.dragonuniverse.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.fluids.DUFluids;
import studio.elysium.dragonuniverse.world.item.DUItems;
import studio.elysium.dragonuniverse.world.level.block.Block.ChairBlock;
import studio.elysium.dragonuniverse.world.level.block.Block.CustomSaplingBlock;
import studio.elysium.dragonuniverse.world.level.block.Block.FlammableRotatedPillarBlock;
import studio.elysium.dragonuniverse.world.level.block.grower.DUTreeGrowers;

import java.util.function.Function;

public class DUBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DragonUniverse.MODID);

    public static final DeferredBlock<Block> EXAMPLE_BLOCK = registerBlock("example_block",
            properties -> new Block(properties.strength(4.0F)));

    public static final DeferredBlock<Block> CHAIR = registerBlock("chair",
            properties -> new ChairBlock(properties.sound(SoundType.WOOD).strength(1.25F).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LiquidBlock> EXAMPLE_WATER_LIQUID_BLOCK = BLOCKS.registerBlock("example_water_liquid_block",
            properties -> new LiquidBlock(DUFluids.EXAMPLE_WATER_SOURCE.get(),
                    properties.mapColor(MapColor.WATER).replaceable().noCollision().strength(100.0F)
                            .pushReaction(PushReaction.DESTROY).noLootTable().liquid().sound(SoundType.EMPTY)));

    public static final DeferredBlock<Block> EXAMPLE_LOG = registerBlock("example_log",
            properties -> new FlammableRotatedPillarBlock(properties.sound(SoundType.WOOD).strength(2.0F).ignitedByLava()));
    public static final DeferredBlock<Block> EXAMPLE_WOOD = registerBlock("example_wood",
            properties -> new FlammableRotatedPillarBlock(properties.sound(SoundType.WOOD).strength(2.0F).ignitedByLava()));
    public static final DeferredBlock<Block> STRIPPED_EXAMPLE_LOG = registerBlock("stripped_example_log",
            properties -> new FlammableRotatedPillarBlock(properties.sound(SoundType.WOOD).strength(2.0F).ignitedByLava()));
    public static final DeferredBlock<Block> STRIPPED_EXAMPLE_WOOD = registerBlock("stripped_example_wood",
            properties -> new FlammableRotatedPillarBlock(properties.sound(SoundType.WOOD).strength(2.0F).ignitedByLava()));
    public static final DeferredBlock<Block> EXAMPLE_PLANKS = registerBlock("example_planks",
            properties -> new Block(properties.sound(SoundType.WOOD).strength(2.0F).ignitedByLava()) {
                @Override
                public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return true;
                }

                @Override
                public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 20;
                }

                @Override
                public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 5;
                }
            });
    public static final DeferredBlock<Block> EXAMPLE_LEAVES = registerBlock("example_leaves",
            properties -> new UntintedParticleLeavesBlock(0.1F, ParticleTypes.CHERRY_LEAVES,
                    properties.mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(SoundType.CHERRY_LEAVES).noOcclusion()
                            .isValidSpawn(Blocks::ocelotOrParrot).isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false)
                            .ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor((state, level, pos) -> false)) {
                @Override
                public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return true;
                }

                @Override
                public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 60;
                }

                @Override
                public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 30;
                }
            });
    public static final DeferredBlock<Block> EXAMPLE_SAPLING = registerBlock("example_sapling",
            properties -> new CustomSaplingBlock(DUTreeGrowers.EXAMPLE, properties.mapColor(MapColor.PLANT).noCollision()
                    .randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY), () -> Blocks.STONE));
    public static final DeferredBlock<Block> POTTED_EXAMPLE_SAPLING = BLOCKS.registerBlock("potted_example_sapling",
            properties -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, EXAMPLE_SAPLING, properties.noOcclusion().instabreak().pushReaction(PushReaction.DESTROY)));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Function<BlockBehaviour.Properties, T> func) {
        DeferredBlock<T> ret = BLOCKS.registerBlock(name, func);
        registerBlockItem(name, ret);
        return ret;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        DUItems.ITEMS.registerItem(name, properties -> new BlockItem(block.get(), properties.useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
