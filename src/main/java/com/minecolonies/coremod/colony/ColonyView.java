package com.minecolonies.coremod.colony;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IWorkManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.handlers.IColonyEventHandler;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.Player;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.colony.requestsystem.IRequestManager;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.configuration.Configurations;
import com.minecolonies.api.entity.ai.citizen.farmer.Field;
import com.minecolonies.api.entity.ai.citizen.farmer.IScarecrow;
import com.minecolonies.api.tileentities.TileEntityColonyBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MathUtils;
import com.minecolonies.coremod.MineColonies;
import com.minecolonies.coremod.colony.buildings.AbstractBuilding;
import com.minecolonies.coremod.colony.buildings.BuildingTownHall;
import com.minecolonies.coremod.colony.permissions.Permissions;
import com.minecolonies.coremod.colony.workorders.AbstractWorkOrder;
import com.minecolonies.coremod.network.messages.PermissionsMessage;
import com.minecolonies.coremod.network.messages.TownHallRenameMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Client side representation of the Colony.
 */
public final class ColonyView implements IColony<AbstractBuilding.View>
{
    //  General Attributes
    private final IToken id;
    private final Map<Integer, WorkOrderView>          workOrders  = new HashMap<>();
    //  Administration/permissions
    @NotNull
    private final Permissions.View                     permissions = new Permissions.View();
    @NotNull
    private final Map<BlockPos, AbstractBuilding.View> buildings   = new HashMap<>();
    //  Citizenry
    @NotNull
    private final Map<Integer, CitizenDataView>        citizens    = new HashMap<>();
    private       String                               name        = "Unknown";
    private int      dimensionId;
    private BlockPos center;
    /**
     * Defines if workers are hired manually or automatically.
     */
    private boolean manualHiring = false;
    //  Buildings
    @Nullable
    private BuildingTownHall.View townHall;
    private int maxCitizens = 0;

    /**
     * The Positions which players can freely interact.
     */
    private Set<BlockPos> freePositions = new HashSet<>();

    /**
     * The Blocks which players can freely interact with.
     */
    private Set<Block> freeBlocks = new HashSet<>();

    /**
     * The overall hapiness of the colony
     */
    private double overallHappiness = 5;

    /**
     * The achievements that the colony hsa received.
     */
    private List<Achievement> colonyAchievements = Lists.newArrayList();

    /**
     * Base constructor for a colony.
     *
     * @param id The current id for the colony.
     */
    private ColonyView(final IToken id)
    {
        this.id = id;
    }

    /**
     * Create a ColonyView given a UUID and NBTTagCompound.
     *
     * @param id Id of the colony view.
     * @return the new colony view.
     */
    @NotNull
    public static ColonyView createFromNetwork(final IToken id)
    {
        return new ColonyView(id);
    }

    /**
     * Populate an NBT compound for a network packet representing a ColonyView.
     *
     * @param colony            Colony to write data about.
     * @param buf               {@link ByteBuf} to write data in.
     * @param isNewSubScription true if this is a new subscription.
     */
    public static void serializeNetworkData(@NotNull final IColony<AbstractBuilding> colony, @NotNull final ByteBuf buf, final boolean isNewSubScription)
    {
        //  General Attributes
        ByteBufUtils.writeUTF8String(buf, colony.getName());
        buf.writeInt(colony.getDimension());
        BlockPosUtil.writeToByteBuf(buf, colony.getCenter());
        buf.writeBoolean(colony.isManualHiring());
        //  Citizenry
        buf.writeInt(colony.getMaxCitizens());

        final Set<Block> freeBlocks = colony.getFreeBlocks();
        final Set<BlockPos> freePos = colony.getFreePositions();

        buf.writeInt(freeBlocks.size());
        for (final Block block : freeBlocks)
        {
            ByteBufUtils.writeUTF8String(buf, block.getRegistryName().toString());
        }

        buf.writeInt(freePos.size());
        for (final BlockPos block : freePos)
        {
            BlockPosUtil.writeToByteBuf(buf, block);
        }
        buf.writeDouble(colony.getOverallHappiness());

        List<Achievement> colonyAchievements = colony.getAchievements();
        buf.writeInt(colonyAchievements.size());
        colonyAchievements.forEach(a ->
        {
            ByteBufUtils.writeUTF8String(buf, a.statId);
        });

        //  Citizens are sent as a separate packet
    }

    /**
     * Get a AbstractBuilding.View for a given building (by coordinate-id) using
     * raw x,y,z.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @param z z-coordinate.
     * @return {@link AbstractBuilding.View} of a AbstractBuilding for the given
     * Coordinates/ID, or null.
     */
    public AbstractBuilding.View getBuilding(final int x, final int y, final int z)
    {
        return getBuilding(new BlockPos(x, y, z));
    }

    /**
     * Returns a map of players in the colony. Key is the UUID, value is {@link
     * Player}
     *
     * @return Map of UUID's and {@link Player}
     */
    @NotNull
    public Map<UUID, Player> getPlayers()
    {
        return permissions.getPlayers();
    }

    /**
     * Sets a specific permission to a rank. If the permission wasn't already
     * set, it sends a message to the server.
     *
     * @param rank   Rank to get the permission.
     * @param action Permission to get.
     */
    public void setPermission(final Rank rank, @NotNull final Action action)
    {
        if (permissions.setPermission(rank, action))
        {
            MineColonies.getNetwork().sendToServer(new PermissionsMessage.Permission(this, PermissionsMessage.MessageType.SET_PERMISSION, rank, action));
        }
    }

    /**
     * removes a specific permission to a rank. If the permission was set, it
     * sends a message to the server.
     *
     * @param rank   Rank to remove permission from.
     * @param action Action to remove permission of.
     */
    public void removePermission(final Rank rank, @NotNull final Action action)
    {
        if (permissions.removePermission(rank, action))
        {
            MineColonies.getNetwork().sendToServer(new PermissionsMessage.Permission(this, PermissionsMessage.MessageType.REMOVE_PERMISSION, rank, action));
        }
    }

    /**
     * Toggles a specific permission to a rank. Sends a message to the server.
     *
     * @param rank   Rank to toggle permission of.
     * @param action Action to toggle permission of.
     */
    public void togglePermission(final Rank rank, @NotNull final Action action)
    {
        permissions.togglePermission(rank, action);
        MineColonies.getNetwork().sendToServer(new PermissionsMessage.Permission(this, PermissionsMessage.MessageType.TOGGLE_PERMISSION, rank, action));
    }

    /**
     * Getter for the workOrders.
     *
     * @return a unmodifiable Collection of the workOrders.
     */
    public Collection<WorkOrderView> getWorkOrders()
    {
        return Collections.unmodifiableCollection(workOrders.values());
    }

    /**
     * Populate a ColonyView from the network data.
     *
     * @param buf               {@link ByteBuf} to read from.
     * @param isNewSubscription Whether this is a new subscription of not.
     * @return null == no response.
     */
    @Nullable
    public IMessage handleColonyViewMessage(@NotNull final ByteBuf buf, final boolean isNewSubscription)
    {
        //  General Attributes
        name = ByteBufUtils.readUTF8String(buf);
        dimensionId = buf.readInt();
        center = BlockPosUtil.readFromByteBuf(buf);
        manualHiring = buf.readBoolean();
        //  Citizenry
        maxCitizens = buf.readInt();

        if (isNewSubscription)
        {
            citizens.clear();
            townHall = null;
            buildings.clear();
        }

        freePositions = new HashSet<>();
        freeBlocks = new HashSet<>();

        final int blockListSize = buf.readInt();
        for (int i = 0; i < blockListSize; i++)
        {
            freeBlocks.add(Block.getBlockFromName(ByteBufUtils.readUTF8String(buf)));
        }

        final int posListSize = buf.readInt();
        for (int i = 0; i < posListSize; i++)
        {
            freePositions.add(BlockPosUtil.readFromByteBuf(buf));
        }
        this.overallHappiness = buf.readDouble();

        int achievementCount = buf.readInt();
        colonyAchievements = new ArrayList<>(achievementCount);
        for (int i = 0; i < achievementCount; i++)
        {
            final StatBase statBase = StatList.getOneShotStat(ByteBufUtils.readUTF8String(buf));
            if (statBase instanceof Achievement)
            {
                colonyAchievements.add((Achievement) statBase);
            }
        }

        return null;
    }

    /**
     * Update permissions.
     *
     * @param buf buffer containing permissions.
     * @return null == no response
     */
    @Nullable
    public IMessage handlePermissionsViewMessage(@NotNull final ByteBuf buf)
    {
        permissions.deserialize(buf);
        return null;
    }

    /**
     * Update a ColonyView's workOrders given a network data ColonyView update
     * packet. This uses a full-replacement - workOrders do not get updated and
     * are instead overwritten.
     *
     * @param buf Network data.
     * @return null == no response.
     */
    @Nullable
    public IMessage handleColonyViewWorkOrderMessage(final ByteBuf buf)
    {
        @Nullable final WorkOrderView workOrder = AbstractWorkOrder.createWorkOrderView(buf);
        if (workOrder != null)
        {
            workOrders.put(workOrder.getId(), workOrder);
        }

        return null;
    }

    /**
     * Update a ColonyView's citizens given a network data ColonyView update
     * packet. This uses a full-replacement - citizens do not get updated and
     * are instead overwritten.
     *
     * @param id  ID of the citizen.
     * @param buf Network data.
     * @return null == no response.
     */
    @Nullable
    public IMessage handleColonyViewCitizensMessage(final int id, final ByteBuf buf)
    {
        final CitizenDataView citizen = CitizenData.createCitizenDataView(id, buf);
        if (citizen != null)
        {
            citizens.put(citizen.getId(), citizen);
        }

        return null;
    }

    /**
     * Remove a citizen from the ColonyView.
     *
     * @param citizen citizen ID.
     * @return null == no response.
     */
    @Nullable
    public IMessage handleColonyViewRemoveCitizenMessage(final int citizen)
    {
        citizens.remove(citizen);
        return null;
    }

    /**
     * Remove a building from the ColonyView.
     *
     * @param buildingId location of the building.
     * @return null == no response.
     */
    @Nullable
    public IMessage handleColonyViewRemoveBuildingMessage(final BlockPos buildingId)
    {
        final AbstractBuilding.View building = buildings.remove(buildingId);
        if (townHall == building)
        {
            townHall = null;
        }
        return null;
    }

    /**
     * Remove a workOrder from the ColonyView.
     *
     * @param workOrderId id of the workOrder.
     * @return null == no response
     */
    @Nullable
    public IMessage handleColonyViewRemoveWorkOrderMessage(final int workOrderId)
    {
        workOrders.remove(workOrderId);

        return null;
    }

    /**
     * Update a ColonyView's buildings given a network data ColonyView update
     * packet. This uses a full-replacement - buildings do not get updated and
     * are instead overwritten.
     *
     * @param buildingId location of the building.
     * @param buf        buffer containing ColonyBuilding information.
     * @return null == no response.
     */
    @Nullable
    public IMessage handleColonyBuildingViewMessage(final BlockPos buildingLocation, final IToken buildingId, @NotNull final ByteBuf buf)
    {
        @Nullable final AbstractBuilding.View building = AbstractBuilding.createBuildingView(this, buildingLocation, buildingId, buf);
        if (building != null)
        {
            buildings.put(building.getLocation().getInDimensionLocation(), building);

            if (building instanceof BuildingTownHall.View)
            {
                townHall = (BuildingTownHall.View) building;
            }
        }

        return null;
    }

    /**
     * Update a players permissions.
     *
     * @param player player username.
     */
    public void addPlayer(final String player)
    {
        MineColonies.getNetwork().sendToServer(new PermissionsMessage.AddPlayer(this, player));
    }

    /**
     * Remove player from colony permissions.
     *
     * @param player the UUID of the player to remove.
     */
    public void removePlayer(final UUID player)
    {
        MineColonies.getNetwork().sendToServer(new PermissionsMessage.RemovePlayer(this, player));
    }

    @Override
    public void spawnCitizen()
    {
        //NOOP on Client side.
    }

    @Override
    public BlockPos getCenter()
    {
        return center;
    }

    @Override
    public String getName()
    {
        return name;
    }

    /**
     * Sets the name of the view.
     *
     * @param name Name of the view.
     */
    public void setName(final String name)
    {
        this.name = name;
        MineColonies.getNetwork().sendToServer(new TownHallRenameMessage(this, name));
    }

    @Override
    public void markDirty()
    {
        //NOOP on the Client Side
    }

    @NotNull
    @Override
    public Permissions.View getPermissions()
    {
        return permissions;
    }

    @Override
    public boolean isCoordInColony(@NotNull final World w, @NotNull final BlockPos pos)
    {
        //  Perform a 2D distance calculation, so pass center.posY as the Y
        return w.provider.getDimension() == dimensionId
                 && BlockPosUtil.getDistanceSquared(center, new BlockPos(pos.getX(), center.getY(), pos.getZ())) <= MathUtils.square(Configurations.workingRangeTownHall);
    }

    @Override
    public long getDistanceSquared(@NotNull final BlockPos pos)
    {
        return BlockPosUtil.getDistanceSquared2D(center, pos);
    }

    @Override
    public boolean hasTownHall()
    {
        return townHall != null;
    }

    /**
     * Returns the ID of the view.
     *
     * @return ID of the view.
     */
    @Override
    public IToken getID()
    {
        return id;
    }

    @Override
    public void readFromNBT(@NotNull final NBTTagCompound compound)
    {
//NOOP on the Client Side
    }

    @Override
    public void addBuilding(@NotNull final AbstractBuilding.View building)
    {
//NOOP on the Client Side
    }

    @Override
    public void addField(@NotNull final Field field)
    {
//NOOP on the Client Side
    }

    @Override
    public void writeToNBT(@NotNull final NBTTagCompound compound)
    {
//NOOP on the Client Side
    }

    /**
     * Returns the dimension ID of the view.
     *
     * @return dimension ID of the view.
     */
    public int getDimension()
    {
        return dimensionId;
    }

    @Override
    public void incrementStatistic(@NotNull final String statistic)
    {
//NOOP on the Client Side
    }

    @Override
    public int getStatisticAmount(@NotNull final String statistic)
    {
        return 0;
    }

    @Override
    public void incrementStatisticAmount(@NotNull final String statistic)
    {
//NOOP on the Client Side
    }

    @Override
    public void markBuildingsDirty()
    {
//NOOP on the Client Side
    }

    @Override
    public void updateSubscribers()
    {
//NOOP on the Client Side
    }

    @Override
    public void sendColonyViewPackets(@NotNull final Set<EntityPlayerMP> oldSubscribers, final boolean hasNewSubscribers)
    {
//NOOP on the Client Side
    }

    @Override
    public void sendPermissionsPackets(@NotNull final Set<EntityPlayerMP> oldSubscribers, final boolean hasNewSubscribers)
    {
//NOOP on the Client Side
    }

    @Override
    public void sendWorkOrderPackets(@NotNull final Set<EntityPlayerMP> oldSubscribers, final boolean hasNewSubscribers)
    {
//NOOP on the Client Side
    }

    @Override
    public void sendCitizenPackets(@NotNull final Set<EntityPlayerMP> oldSubscribers, final boolean hasNewSubscribers)
    {
//NOOP on the Client Side
    }

    @Override
    public void sendBuildingPackets(@NotNull final Set<EntityPlayerMP> oldSubscribers, final boolean hasNewSubscribers)
    {
//NOOP on the Client Side
    }

    @Override
    public void sendSchematicsPackets(final boolean hasNewSubscribers)
    {
//NOOP on the Client Side
    }

    @Override
    public void sendFieldPackets(final boolean hasNewSubscribers)
    {
//NOOP on the Client Side
    }

    @NotNull
    @Override
    public IWorkManager getWorkManager()
    {
        return null;
    }

    /**
     * Get a copy of the freePositions list.
     *
     * @return the list of free to interact positions.
     */
    public Set<BlockPos> getFreePositions()
    {
        return new HashSet<>(freePositions);
    }

    /**
     * Get a copy of the freeBlocks list.
     *
     * @return the list of free to interact blocks.
     */
    public Set<Block> getFreeBlocks()
    {
        return new HashSet<>(freeBlocks);
    }

    /**
     * Add a new free to interact position.
     *
     * @param pos position to add.
     */
    public void addFreePosition(@NotNull final BlockPos pos)
    {
        freePositions.add(pos);
    }

    /**
     * Add a new free to interact block.
     *
     * @param block block to add.
     */
    public void addFreeBlock(@NotNull final Block block)
    {
        freeBlocks.add(block);
    }

    /**
     * Remove a free to interact position.
     *
     * @param pos position to remove.
     */
    public void removeFreePosition(@NotNull final BlockPos pos)
    {
        freePositions.remove(pos);
    }

    /**
     * Remove a free to interact block.
     *
     * @param block state to remove.
     */
    public void removeFreeBlock(@NotNull final Block block)
    {
        freeBlocks.remove(block);
    }

    @Override
    public void updateOverallHappiness()
    {
//NOOP on the Client Side
    }

    @Override
    public void updateWayPoints()
    {
//NOOP on the Client Side
    }

    /**
     * returns the World the colony is in.
     *
     * @return the World the colony is in.
     */
    @Nullable
    @Override
    public World getWorld()
    {
        return Minecraft.getMinecraft().world;
    }

    @Override
    public void markFieldsDirty()
    {
//NOOP on the Client Side
    }

    @Override
    public void spawnCitizen(final ICitizenData data)
    {
//NOOP on the Client Side
    }

    /**
     * Returns the maximum amount of citizen in the colony.
     *
     * @return maximum amount of citizens.
     */
    public int getMaxCitizens()
    {
        return maxCitizens;
    }

    /**
     * Getter for the citizens map.
     *
     * @return a unmodifiable Map of the citizen.
     */
    public Map<Integer, ICitizenData> getCitizens()
    {
        return Collections.unmodifiableMap(citizens);
    }

    @NotNull
    @Override
    public List<EntityPlayer> getMessageEntityPlayers()
    {
        return null;
    }

    @Override
    public void checkAchievements()
    {

    }

    @Override
    public void markCitizensDirty()
    {

    }

    @Override
    public void triggerAchievement(@NotNull final Achievement achievement)
    {

    }

    @Override
    public void spawnCitizenIfNull(@NotNull final ICitizenData data)
    {

    }

    /**
     * Get the town hall View for this ColonyView.
     *
     * @return {@link BuildingTownHall.View} of the colony.
     */
    @Nullable
    public BuildingTownHall.View getTownHall()
    {
        return townHall;
    }

    @NotNull
    @Override
    public Map<BlockPos, Field> getFields()
    {
        return null;
    }

    @Override
    public Field getField(final BlockPos fieldId)
    {
        return null;
    }

    @Nullable
    @Override
    public Field getFreeField(final String owner)
    {
        return null;
    }

    /**
     * Get a AbstractBuilding.View for a given building (by coordinate-id) using
     * ChunkCoordinates.
     *
     * @param buildingId Coordinates/ID of the AbstractBuilding.
     * @return {@link AbstractBuilding.View} of a AbstractBuilding for the given
     * Coordinates/ID, or null.
     */
    public AbstractBuilding.View getBuilding(final BlockPos buildingId)
    {
        return buildings.get(buildingId);
    }

    /**
     * Gets the CitizenDataView for a citizen id.
     *
     * @param id the citizen id.
     * @return CitizenDataView for the citizen.
     */
    public ICitizenData getCitizen(final int id)
    {
        return citizens.get(id);
    }

    @Nullable
    @Override
    public <S extends AbstractBuilding.View> S getBuilding(final BlockPos buildingId, @NotNull final Class<S> type)
    {
        return null;
    }

    @Override
    public void addNewField(
                             final IScarecrow tileEntity, final InventoryPlayer inventoryPlayer, final BlockPos pos, final World world)
    {

    }

    @Nullable
    @Override
    public AbstractBuilding.View addNewBuilding(@NotNull final TileEntityColonyBuilding tileEntity)
    {
        return null;
    }

    @Override
    public void calculateMaxCitizens()
    {

    }

    @Override
    public void removeBuilding(@NotNull final AbstractBuilding.View building)
    {

    }

    /**
     * Getter for the manual hiring or not.
     *
     * @return the boolean true or false.
     */
    public boolean isManualHiring()
    {
        return manualHiring;
    }

    /**
     * Sets if workers should be hired manually.
     *
     * @param manualHiring true if manually.
     */
    public void setManualHiring(final boolean manualHiring)
    {
        this.manualHiring = manualHiring;
    }

    @Override
    public void removeCitizen(@NotNull final ICitizenData citizen)
    {

    }

    @Override
    public void removeWorkOrder(final int orderId)
    {

    }

    @Nullable
    @Override
    public ICitizenData getJoblessCitizen()
    {
        return null;
    }

    @Override
    public List<BlockPos> getDeliverymanRequired()
    {
        return null;
    }

    @Override
    public void onBuildingUpgradeComplete(@NotNull final IBuilding building, final int level)
    {

    }

    @NotNull
    @Override
    public List<Achievement> getAchievements()
    {
        return null;
    }

    @Override
    public void removeField(final BlockPos pos)
    {

    }

    @Override
    public void addWayPoint(final BlockPos point, final IBlockState block)
    {

    }

    @NotNull
    @Override
    public List<BlockPos> getWayPoints(@NotNull final BlockPos position, @NotNull final BlockPos target)
    {
        return null;
    }

    /**
     * Getter for the overall happiness.
     *
     * @return the happiness, a double.
     */
    public double getOverallHappiness()
    {
        return overallHappiness;
    }

    @Override
    public void increaseOverallHappiness(final double amount)
    {

    }

    @Override
    public void decreaseOverallHappiness(final double amount)
    {

    }

    /**
     * Returns the buildings in the colony
     *
     * @return The buildings in the colony
     */
    @NotNull
    @Override
    public ImmutableMap<BlockPos, IBuilding> getBuildings()
    {
        return ImmutableMap.copyOf(buildings);
    }

    /**
     * Returns the request manager for the colony.
     *
     * @return The request manager.
     */
    @NotNull
    @Override
    public IRequestManager getRequestManager()
    {
        throw new IllegalStateException("Request system does not exist on the client side yet!");
    }

    @NotNull
    @Override
    public IFactoryController getFactoryController()
    {
        return StandardFactoryController.getInstance();
    }

    @Override
    public void OnDeletion()
    {

    }

    @NotNull
    @Override
    public ImmutableCollection<IColonyEventHandler> getCombinedHandlers()
    {
        return null;
    }
}
