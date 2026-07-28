package me.xiaoeyun.createnestnetwork.registry;

import static me.xiaoeyun.createnestnetwork.CreateNestNetwork.registrate;

import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;

import me.xiaoeyun.createnestnetwork.content.customs.CustomsGateBlock;
import me.xiaoeyun.createnestnetwork.content.customs.CustomsLinkBlock;
import me.xiaoeyun.createnestnetwork.content.customs.CustomsLinkBlockItem;
import me.xiaoeyun.createnestnetwork.content.proxy.StockProxyerBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

// addLayer is deprecated for removal upstream, but this Registrate version
// offers no replacement and Create pins it, so the warning is not actionable.
@SuppressWarnings("removal")
public class CnnBlocks {

    // Borrowing a block's model is not enough to look like it: Create sets its
    // render layers in code rather than in the model json, so anything parented
    // to one of its models still lands on the default solid layer, where alpha
    // is ignored and a glass panel comes out black. Each entry below therefore
    // mirrors the properties of the Create block it stands in for.

    // No item: a proxyer only ever exists by converting a tuned Stock Ticker,
    // and it impersonates one whenever an item form is asked for -- so it also
    // has to mine, sound and render like one. Mirrors AllBlocks.STOCK_TICKER.
    public static final BlockEntry<StockProxyerBlock> STOCK_PROXYER = registrate()
        .block("stock_proxyer", StockProxyerBlock::new)
        .initialProperties(SharedProperties::softMetal)
        .properties(p -> p.sound(SoundType.GLASS))
        .addLayer(() -> RenderType::cutoutMipped)
        .register();

    // Mirrors BuilderTransformers.packager(), which the Packager and the
    // Repackager both use. noOcclusion matters as much as the render layer:
    // the model is not a full cube, and claiming otherwise darkens whatever
    // sits against it.
    public static final BlockEntry<CustomsGateBlock> CUSTOMS_GATE = registrate()
        .block("customs_gate", CustomsGateBlock::new)
        .initialProperties(SharedProperties::softMetal)
        .properties(p -> p.noOcclusion()
            .isRedstoneConductor(($1, $2, $3) -> false))
        .properties(p -> p.mapColor(MapColor.TERRACOTTA_BLUE)
            .sound(SoundType.NETHERITE_BLOCK))
        .addLayer(() -> RenderType::cutoutMipped)
        .simpleItem()
        .register();

    // Mirrors AllBlocks.STOCK_LINK, which needs no render layer of its own: its
    // model is a forge:composite whose children declare their own render types,
    // and ours are copies that kept them. noOcclusion is the one addition --
    // the link is nowhere near a full cube, and Create leaving it out is not a
    // reason for us to.
    public static final BlockEntry<CustomsLinkBlock> CUSTOMS_LINK = registrate()
        .block("customs_link", CustomsLinkBlock::new)
        .initialProperties(SharedProperties::softMetal)
        .properties(p -> p.mapColor(MapColor.TERRACOTTA_BLUE)
            .sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion())
        .item(CustomsLinkBlockItem::new)
        .build()
        .register();

    public static void register() {}
}
