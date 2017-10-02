package com.minecolonies.coremod.blocks;

import com.minecolonies.api.IAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.configuration.Configurations;
import com.minecolonies.api.util.BlockPosUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

/**
 * Hut for the town hall.
 * Sets the working range for the town hall in the constructor
 */
public class BlockHutTownHall extends AbstractBlockHut
{
    public BlockHutTownHall()
    {
        super();
        //Sets the working range to whatever the config is set to
        this.workingRange = Configurations.workingRangeTownHall;
    }

    @NotNull
    @Override
    public String getName()
    {
        return "blockHutTownHall";
    }

    @Override
    public void onBlockPlacedBy(
                                 @NotNull final World worldIn, @NotNull final BlockPos pos, final IBlockState state, final EntityLivingBase placer, final ItemStack stack)
    {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);

        if (worldIn.isRemote)
        {
            return;
        }

        if (placer.getActiveHand().equals(EnumHand.MAIN_HAND))
        {
            final IColony colony = IAPI.Holder.getApi().getServerColonyManager().getControllerForWorld(worldIn).getClosestColony(pos);

            if ((colony == null
                   || BlockPosUtil.getDistance2D(colony.getCenter(), pos) >= Configurations.workingRangeTownHall * 2 + Configurations.townHallPadding)
                  && placer instanceof EntityPlayer)
            {
                IAPI.Holder.getApi().getServerColonyManager().getControllerForWorld(worldIn).createColony(pos, (EntityPlayer) placer);
            }
        }
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
    }
}
