package com.minecolonies.api.colony.buildings;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.handlers.IColonyEventHandler;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.ai.item.handling.ItemStorage;
import com.minecolonies.api.tileentities.TileEntityColonyBuilding;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * A building in a colony. Can request objects in the request system and process events that happen in the world.
 */
public interface IBuilding<B extends IBuilding> extends IRequester, IColonyEventHandler
{

    /**
     * Destroys the building.
     */
    void destroy();

    /**
     * Returns the tile entity that belongs to the colony building.
     *
     * @return {@link TileEntity} object of the building.
     */
    <T extends TileEntityColonyBuilding> T getTileEntity();

    /**
     * Sets the tile entity for the building.
     *
     * @param te {@link TileEntityColonyBuilding} that will fill the {@code tileEntity} field.
     */
    void setTileEntity(TileEntityColonyBuilding te);

    /**
     * Returns the colony of the building.
     *
     * @return {@link IColony} of the current object.
     */
    @NotNull
    IColony<B> getColony();

    /**
     * Marks the instance and the building dirty.
     */
    void markDirty();

    /**
     * Override this method if you want to keep an amount of items in inventory.
     * When the inventory is full, everything get's dumped into the building chest.
     * But you can use this method to hold some stacks back.
     *
     * @return a list of objects which should be kept.
     */
    Map<ItemStorage, Integer> getRequiredItemsAndAmount();

    /**
     * Checks if this building have a work order.
     *
     * @return true if the building is building, upgrading or repairing.
     */
    boolean hasWorkOrder();

    /**
     * Requests an upgrade for the current building.
     */
    void requestUpgrade();

    /**
     * Method to remove a citizen.
     *
     * @param citizen Citizen to be removed.
     */
    void removeCitizen(ICitizenData citizen);

    /**
         * Requests a repair for the current building.
         */
    void requestRepair();

    /**
     * Remove the work order for the building.
     * <p>
     * Remove either the upgrade or repair work order
     */
    void removeWorkOrder();

    /**
     * Returns the rotation of the current building.
     *
     * @return integer value of the rotation.
     */
    int getRotation();

    /**
         * Sets the rotation of the current building.
         *
         * @param rotation integer value of the rotation.
         */
    void setRotation(int rotation);

    /**
     * Children must return their max building level.
     *
     * @return Max building level.
     */
    int getMaxBuildingLevel();

    /**
         * Returns the style of the current building.
         *
         * @return String representation of the current building-style
         */
    String getStyle();

    /**
     * Sets the style of the building.
     *
     * @param style String value of the style.
     */
    void setStyle(String style);

    /**
     * Checks if this building is at its max level.
     *
     * @return true if the building is at its max level.
     */
    boolean isBuildingMaxLevel();

    /**
     * Returns the {@link BlockPos} of the current object, also used as ID.
     *
     * @return {@link BlockPos} of the current object.
     */
    IToken getID();

    boolean isBuilding();

    boolean isRepairing();

    /**
     * Called upon completion of an upgrade process.
     *
     * @param newLevel The new level.
     */
    void onUpgradeComplete(int newLevel);

    /**
         * Returns the mirror of the current building.
         *
         * @return boolean value of the mirror.
         */
    boolean isMirrored();

    /**
     * Sets the mirror of the current building.
     */
    void setMirror();

    /**
     * Returns the level of the current object.
     *
     * @return Level of the current object.
     */
    int getBuildingLevel();

    /**
     * Sets the current level of the building.
     *
     * @param level Level of the building.
     */
    void setBuildingLevel(int level);

    /**
     * Get all additional containers which belong to the building.
     *
     * @return a copy of the list to avoid currentModification exception.
     */
    List<BlockPos> getAdditionalContainers();

    /**
     * Check if the worker needs anything. Tool or item.
     * Basically checks if the worker has open requests, regardless of by whom they will be fullfilled.
     *
     * @return true if so.
     */
    boolean needsAnything();

    /**
     * Check if any items are needed at the moment.
     *
     * @return true if so.
     */
    boolean areItemsNeeded();

    /**
     * Check if the worker needs a tool of the given type.
     *
     * @param toolClass The type of tool requested.
     * @return True if so.
     */
    boolean requiresTool(String toolClass);

    /**
     * Method used to create a requests through this building.
     * Should be used when the given citizen belongs to this hut and once the delivery of the result to this hut.
     *
     * @param citizenData The citizen that requests the request.
     * @param requested   The object that the citizen requests.
     * @param <Request>   The type of request that the citizen makes.
     */
    <Request> void createRequest(@NotNull ICitizenData citizenData, @NotNull Request requested);

    /**
     * Method used to check for a given worker if he has open request.
     *
     * @param citizen The worker to check for.
     * @return True when the given worker has open requests, false when not.
     */
    boolean hasWorkerOpenRequests(@NotNull ICitizenData citizen);

    /**
     * Method used to check if a given worker has open request of a given type.
     *
     * @param citizenData The citizen to check for.
     * @param requestType The class of the type to check for.
     * @param <Request>   The type to check for.
     * @return True when the citizen has open requests of the given type, false when not.
     */
    <Request> boolean hasWorkerOpenRequestsOfType(@NotNull ICitizenData citizenData, Class<Request> requestType);

    /**
     * Method to get the open requests for a given building.
     *
     * @return An {@link ImmutableList} with tokens of the open requests.
     */
    ImmutableList<IToken> getOpenRequests(@NotNull ICitizenData data);

    /**
     * Method to get the open requests of a specific type for a given citizen.
     *
     * @param citizenData The {@link ICitizenData} of the citizen to look for.
     * @param requestType The class of the type of request to look for.
     * @param <Request>   The type of request to look for.
     * @return An {@link ImmutableList} with requests of the given type that are open for the given citizen.
     */
    <Request> ImmutableList<IRequest<Request>> getOpenRequestsOfType(@NotNull ICitizenData citizenData, Class<Request> requestType);

    /**
     * Method to get the list of request that have been fulfilled for this building and are waiting pickup.
     *
     * @param data The {@link ICitizenData} as the citizen to get the list of completed requests for.
     * @return The list of completed yet not picked up request (So the IRequest is in state completed, yet not in received status)
     */
    ImmutableList<IToken> getCompletedRequestsForCitizen(@NotNull ICitizenData data);

    /**
     * Method to mark a completed request as accepted and picked up by the citizen.
     *
     * @param data  The {@link ICitizenData} that represents the citizen that accepted a completed request.
     * @param token The {@link IToken} of the completed request that got accepted.
     * @throws IllegalArgumentException Thrown when the IToken is not known as a completed request.
     */
    void markRequestAsAccepted(@NotNull ICitizenData data, @NotNull IToken token) throws IllegalArgumentException;
}
