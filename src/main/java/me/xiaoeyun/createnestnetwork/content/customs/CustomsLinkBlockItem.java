package me.xiaoeyun.createnestnetwork.content.customs;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;

import net.minecraft.world.level.block.Block;

/**
 * Inherits the whole vanilla Stock Link flow: use on a component of the network
 * this link should join to tune it, use in the air to clear, sneak-use to
 * place. Extending the vanilla item class also enrolls the held item in
 * {@code LogisticallyLinkedClientHandler}'s network highlight.
 */
public class CustomsLinkBlockItem extends LogisticallyLinkedBlockItem {

    public CustomsLinkBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

}
