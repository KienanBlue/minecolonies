package com.minecolonies.coremod.colony.buildings;

import com.minecolonies.api.client.colony.IColonyView;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.reference.ModAchievements;
import com.minecolonies.blockout.views.Window;
import com.minecolonies.coremod.client.gui.WindowHomeBuilding;
import com.minecolonies.coremod.colony.CitizenData;
import com.minecolonies.coremod.colony.Colony;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The class of the citizen hut.
 */
public class BuildingHome extends AbstractBuildingHut
{
    private static final String            TAG_RESIDENTS = "residents";
    private static final String            CITIZEN       = "Citizen";
    @NotNull
    private final        List<ICitizenData> residents     = new ArrayList<>();
    private              boolean           isFoodNeeded  = false;

    /**
     * Instantiates a new citizen hut.
     *
     * @param c the colony.
     * @param l the location.
     */
    public BuildingHome(final Colony c, final BlockPos l)
    {
        super(c, l);
    }

    @Override
    public void readFromNBT(@NotNull final NBTTagCompound compound)
    {
        super.readFromNBT(compound);

        residents.clear();

        final int[] residentIds = compound.getIntArray(TAG_RESIDENTS);
        for (final int citizenId : residentIds)
        {
            final CitizenData citizen = (CitizenData) getColony().getCitizen(citizenId);
            if (citizen != null)
            {
                // Bypass addResident (which marks dirty)
                residents.add(citizen);
                citizen.setHomeBuilding(this);
            }
        }
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return CITIZEN;
    }

    @Override
    public void writeToNBT(@NotNull final NBTTagCompound compound)
    {
        super.writeToNBT(compound);

        if (!residents.isEmpty())
        {
            @NotNull final int[] residentIds = new int[residents.size()];
            for (int i = 0; i < residents.size(); ++i)
            {
                residentIds[i] = residents.get(i).getId();
            }
            compound.setIntArray(TAG_RESIDENTS, residentIds);
        }
    }

    @Override
    public void onDestroyed()
    {
        residents.stream()
          .filter(citizen -> citizen != null)
          .forEach(citizen -> citizen.setHomeBuilding(null));
        residents.clear();
        super.onDestroyed();
    }

    @Override
    public void onWorldTick(@NotNull final TickEvent.WorldTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        if (residents.size() < getMaxInhabitants())
        {
            // 'Capture' as many citizens into this house as possible
            addHomelessCitizens();
        }
    }

    @Override
    public int getMaxInhabitants()
    {
        return getBuildingLevel();
    }

    /**
     * Looks for a homeless citizen to add to the current building Calls.
     * {@link #addResident(ICitizenData)}
     */
    private void addHomelessCitizens()
    {
        for (@NotNull final ICitizenData citizen : getColony().getCitizens().values())
        {
            // Move the citizen to a better hut
            if (citizen.getHomeBuilding() != null && citizen.getHomeBuilding().getBuildingLevel() < this.getBuildingLevel())
            {
                // The citizen can move to this hut to improve conditions
                citizen.getHomeBuilding().removeCitizen(citizen);
            }
            if (citizen.getHomeBuilding() == null)
            {
                addResident(citizen);

                if (residents.size() >= getMaxInhabitants())
                {
                    break;
                }
            }
        }
    }

    @Override
    public void removeCitizen(@NotNull final ICitizenData citizen)
    {
        if (residents.contains(citizen))
        {
            citizen.setHomeBuilding(null);
            residents.remove(citizen);
        }
    }

    /**
     * Adds the citizen to the building.
     *
     * @param citizen Citizen to add.
     */
    private void addResident(@NotNull final ICitizenData citizen)
    {
        residents.add(citizen);
        citizen.setHomeBuilding(this);

        markDirty();
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return 5;
    }

    @Override
    public void serializeToView(@NotNull final ByteBuf buf)
    {
        super.serializeToView(buf);

        buf.writeInt(residents.size());
        for (@NotNull final ICitizenData citizen : residents)
        {
            buf.writeInt(citizen.getId());
        }
    }

    @Override
    public void onUpgradeComplete(final int newLevel)
    {
        super.onUpgradeComplete(newLevel);

        if (newLevel == 1)
        {
            this.getColony().triggerAchievement(ModAchievements.achievementBuildingColonist);
        }
        if (newLevel >= this.getMaxBuildingLevel())
        {
            this.getColony().triggerAchievement(ModAchievements.achievementUpgradeColonistMax);
        }
    }

    @Override
    public void setBuildingLevel(final int level)
    {
        super.setBuildingLevel(level);
        getColony().calculateMaxCitizens();
    }

    /**
     * Check food requirements of the building.
     *
     * @return true of false.
     */
    public boolean isFoodNeeded()
    {
        return isFoodNeeded;
    }

    /**
     * Set food requirements for the building.
     *
     * @param foodNeeded set true if required.
     */
    public void setFoodNeeded(final boolean foodNeeded)
    {
        isFoodNeeded = foodNeeded;
    }

    /**
     * Returns whether the citizen has this as home or not.
     *
     * @param citizen Citizen to check.
     * @return True if citizen lives here, otherwise false.
     */
    public boolean hasResident(final CitizenData citizen)
    {
        return residents.contains(citizen);
    }

    /**
     * The view of the citizen hut.
     */
    public static class View extends AbstractBuildingHut.View
    {
        @NotNull
        private final List<Integer> residents = new ArrayList<>();

        /**
         * Creates an instance of the citizen hut window.
         *
         * @param c the colonyView.
         * @param l the position the hut is at.
         */
        public View(final IColonyView c, @NotNull final BlockPos l, @NotNull final IToken id)
        {
            super(c, l, id);
        }

        @NotNull
        public List<Integer> getResidents()
        {
            return Collections.unmodifiableList(residents);
        }

        @NotNull
        @Override
        public Window getWindow()
        {
            return new WindowHomeBuilding(this);
        }

        @Override
        public void deserialize(@NotNull final ByteBuf buf)
        {
            super.deserialize(buf);

            final int numResidents = buf.readInt();
            for (int i = 0; i < numResidents; ++i)
            {
                residents.add(buf.readInt());
            }
        }
    }
}
