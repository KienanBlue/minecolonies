package com.minecolonies.coremod.colony.workorders;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.workorder.IWorkOrderBuild;
import com.minecolonies.api.colony.workorder.WorkOrderType;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.LanguageHandler;
import com.minecolonies.api.util.Log;
import com.minecolonies.coremod.colony.buildings.AbstractBuilding;
import com.minecolonies.coremod.colony.buildings.BuildingBuilder;
import com.minecolonies.coremod.colony.jobs.JobBuilder;
import com.minecolonies.structures.Structures;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Represents one building order to complete.
 * Has his own structure for the building.
 */
public class AbstractWorkOrderBuild extends AbstractWorkOrder implements IWorkOrderBuild
{
    private static final String TAG_BUILDING       = "building";
    private static final String TAG_UPGRADE_LEVEL  = "upgradeLevel";
    private static final String TAG_UPGRADE_NAME   = "upgrade";
    private static final String TAG_WORKORDER_NAME = "workOrderName";
    private static final String TAG_IS_CLEARED     = "cleared";
    private static final String TAG_IS_REQUESTED   = "requested";
    private static final String TAG_IS_MIRRORED    = "mirrored";

    private static final String TAG_SCHEMATIC_NAME    = "structureName";
    private static final String TAG_SCHEMATIC_MD5     = "schematicMD5";
    private static final String TAG_BUILDING_ROTATION = "buildingRotation";

    protected boolean  isMirrored;
    protected BlockPos buildingLocation;
    protected int      buildingRotation;
    protected String   structureName;
    protected String   md5;
    protected boolean  cleared;
    protected String   workOrderName;
    private   int      upgradeLevel;
    private   String   upgradeName;
    private boolean hasSentMessageForThisWorkOrder = false;
    private boolean requested;

    /**
     * Unused constructor for reflection.
     */
    public AbstractWorkOrderBuild()
    {
        super();
    }

    /**
     * Create a new AbstractWorkOrder.
     *
     * @param building the building to build.
     * @param level    the level it should have.
     */
    public AbstractWorkOrderBuild(@NotNull final AbstractBuilding building, final int level)
    {
        super();
        this.buildingLocation = building.getLocation().getInDimensionLocation();
        this.upgradeLevel = level;
        this.upgradeName = building.getSchematicName() + level;
        this.buildingRotation = building.getRotation();
        this.isMirrored = building.getTileEntity() == null ? building.isMirrored() : building.getTileEntity().isMirrored();
        this.cleared = level > 1;
        this.requested = false;

        //normalize the structureName
        Structures.StructureName sn = new Structures.StructureName(Structures.SCHEMATICS_PREFIX, building.getStyle(), this.getUpgradeName());
        if (building.getTileEntity() != null && !building.getTileEntity().getStyle().isEmpty())
        {
            final String previousStructureName = sn.toString();
            sn = new Structures.StructureName(Structures.SCHEMATICS_PREFIX, building.getTileEntity().getStyle(), this.getUpgradeName());
            Log.getLogger().info("AbstractWorkOrderBuild at location " + this.buildingLocation + " is using " + sn + " instead of " + previousStructureName);
        }


        this.structureName = sn.toString();
        this.workOrderName = this.structureName;
        this.md5 = Structures.getMD5(this.structureName);
    }

    /**
     * Returns the name after upgrade.
     *
     * @return Name after upgrade.
     */
    private String getUpgradeName()
    {
        return upgradeName;
    }

    @Override
    public String getName()
    {
        return workOrderName;
    }

    @Override
    public int getUpgradeLevel()
    {
        return upgradeLevel;
    }

    @Override
    public BlockPos getBuildingLocation()
    {
        return buildingLocation;
    }

    @Override
    public String getStructureName()
    {
        return structureName;
    }

    @Override
    public int getRotation()
    {
        return buildingRotation;
    }

    @Override
    public boolean isCleared()
    {
        return cleared;
    }

    @Override
    public void setCleared(final boolean cleared)
    {
        this.cleared = cleared;
    }

    @Override
    public boolean isRequested()
    {
        return requested;
    }

    @Override
    public void setRequested(final boolean requested)
    {
        this.requested = requested;
    }

    @Override
    public boolean isMirrored()
    {
        return isMirrored;
    }

    @Override
    public boolean isDecoration()
    {
        return false;
    }

    /**
     * Read the AbstractWorkOrder data from the NBTTagCompound.
     *
     * @param compound NBT Tag compound.
     */
    @Override
    public void readFromNBT(@NotNull final NBTTagCompound compound)
    {
        super.readFromNBT(compound);
        buildingLocation = BlockPosUtil.readFromNBT(compound, TAG_BUILDING);
        final Structures.StructureName sn = new Structures.StructureName(compound.getString(TAG_SCHEMATIC_NAME));
        structureName = sn.toString();
        if (!(this instanceof AbstractWorkOrderBuildDecoration))
        {
            upgradeLevel = compound.getInteger(TAG_UPGRADE_LEVEL);
            upgradeName = compound.getString(TAG_UPGRADE_NAME);
        }

        workOrderName = compound.getString(TAG_WORKORDER_NAME);
        cleared = compound.getBoolean(TAG_IS_CLEARED);
        md5 = compound.getString(TAG_SCHEMATIC_MD5);
        if (!Structures.hasMD5(structureName))
        {
            // If the schematic move we can use the MD5 hash to find it
            final Structures.StructureName newSN = Structures.getStructureNameByMD5(md5);
            if (newSN == null)
            {
                Log.getLogger().error("AbstractWorkOrderBuild.readFromNBT: Could not find " + structureName);
            }
            else
            {
                Log.getLogger().warn("AbstractWorkOrderBuild.readFromNBT: replace " + sn + " by " + newSN);
                structureName = newSN.toString();
            }
        }

        buildingRotation = compound.getInteger(TAG_BUILDING_ROTATION);
        requested = compound.getBoolean(TAG_IS_REQUESTED);
        isMirrored = compound.getBoolean(TAG_IS_MIRRORED);
    }

    /**
     * Save the Work Order to an NBTTagCompound.
     *
     * @param compound NBT tag compound.
     */
    @Override
    public void writeToNBT(@NotNull final NBTTagCompound compound)
    {
        super.writeToNBT(compound);
        BlockPosUtil.writeToNBT(compound, TAG_BUILDING, buildingLocation);
        if (!(this instanceof AbstractWorkOrderBuildDecoration))
        {
            compound.setInteger(TAG_UPGRADE_LEVEL, upgradeLevel);
            compound.setString(TAG_UPGRADE_NAME, upgradeName);
        }
        if (workOrderName != null)
        {
            compound.setString(TAG_WORKORDER_NAME, workOrderName);
        }
        compound.setBoolean(TAG_IS_CLEARED, cleared);
        if (md5 != null)
        {
            compound.setString(TAG_SCHEMATIC_MD5, md5);
        }
        if (structureName == null)
        {
            Log.getLogger().error("AbstractWorkOrderBuild.writeToNBT: structureName should not be null!!!");
        }
        else
        {
            compound.setString(TAG_SCHEMATIC_NAME, structureName);
        }
        compound.setInteger(TAG_BUILDING_ROTATION, buildingRotation);
        compound.setBoolean(TAG_IS_REQUESTED, requested);
        compound.setBoolean(TAG_IS_MIRRORED, isMirrored);
    }

    /**
     * Is this AbstractWorkOrder still valid?  If not, it will be deleted.
     *
     * @param colony The colony that owns the Work Order.
     * @return True if the building for this work order still exists.
     */
    @Override
    public boolean isValid(@NotNull final IColony colony)
    {
        return colony.getBuilding(buildingLocation) != null;
    }

    @NotNull
    @Override
    protected WorkOrderType getType()
    {
        return WorkOrderType.BUILD;
    }

    @Override
    protected String getValue()
    {
        return upgradeName;
    }

    /**
     * Attempt to fulfill the Work Order.
     * Override this with an implementation for the Work Order to find a Citizen to perform the job
     * <p>
     * finds the first suitable builder for this job.
     *
     * @param colony The colony that owns the Work Order.
     */
    @Override
    public void attemptToFulfill(@NotNull final IColony colony)
    {
        boolean sendMessage = true;
        boolean hasBuilder = false;

        Collection<ICitizenData> citizens = colony.getCitizens().values();
        for (@NotNull final ICitizenData citizen : citizens)
        {
            final JobBuilder job = (JobBuilder) citizen.getJob();

            if (job == null || citizen.getWorkBuilding() == null)
            {
                continue;
            }

            hasBuilder = true;

            final int builderLevel = citizen.getWorkBuilding().getBuildingLevel();

            // don't send a message if we have a valid worker that is busy.
            if (canBuildHut(builderLevel, citizen, colony))
            {
                sendMessage = false;
            }

            if (job.hasWorkOrder())
            {
                continue;
            }

            //  A Build AbstractWorkOrder may be fulfilled by a Builder as long as any ONE of the following is true:
            //  - The Builder's Work AbstractBuilding is built
            //  - OR the AbstractWorkOrder is for the Builder's Work AbstractBuilding
            //  - OR the AbstractWorkOrder is for the TownHall
            if (canBuildHut(builderLevel, citizen, colony))
            {
                job.setWorkOrder(this);
                this.setClaimedBy(citizen);
                return;
            }
        }

        sendBuilderMessage(colony, hasBuilder, sendMessage);
    }

    /**
     * Checks if a builder may accept this workOrder.
     *
     * @param builderLevel the builder level.
     * @return true if he is able to.
     */
    private boolean canBuildHut(final int builderLevel, @NotNull final ICitizenData citizen, @NotNull final IColony colony)
    {
        return builderLevel >= upgradeLevel || builderLevel == BuildingBuilder.MAX_BUILDING_LEVEL
                 || (citizen.getWorkBuilding() != null && citizen.getWorkBuilding().getID().equals(buildingLocation))
                 || isLocationTownhall(colony, buildingLocation);
    }

    private void sendBuilderMessage(@NotNull final IColony colony, final boolean hasBuilder, final boolean sendMessage)
    {
        if (hasSentMessageForThisWorkOrder)
        {
            return;
        }

        if (hasBuilder && sendMessage)
        {
            hasSentMessageForThisWorkOrder = true;
            LanguageHandler.sendPlayersMessage(colony.getMessageEntityPlayers(),
              "entity.builder.messageBuilderNecessary", Integer.toString(this.upgradeLevel));
        }

        if (!hasBuilder)
        {
            hasSentMessageForThisWorkOrder = true;
            LanguageHandler.sendPlayersMessage(colony.getMessageEntityPlayers(),
              "entity.builder.messageNoBuilder");
        }
    }

    private static boolean isLocationTownhall(@NotNull final IColony colony, final BlockPos buildingLocation)
    {
        return colony.hasTownHall() && colony.getTownHall() != null && colony.getTownHall().getID().equals(buildingLocation);
    }
}
