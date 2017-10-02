package com.minecolonies.coremod.commands;

import com.minecolonies.api.IAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Get the Colony ID and Citizen ID out of a command.
 */

public final class GetColonyAndCitizen
{

    private static final String ONLY_NUMBERS                = "Please only use numbers for the %s ID!";
    private static final String TOO_MANY_ARGUMENTS          = "Too many arguments!";
    private static final String UNKNOWN_ERROR               = "Unknown Error!";
    private static final String NOT_FOUND                   = "%s not found!";
    private static final String NO_COLONY                   = "You haven't got a colony!";
    private static final int    SHORT_ARGUMENT_LENGTH       = 1;
    private static final int    NORMAL_ARGUMENT_LENGTH      = 2;
    private static final int    NAME_ARGUMENT_LENGTH        = 3;
    private static final int    ID_AND_NAME_ARGUMENT_LENGTH = 4;
    private static final int    TOO_MANY_ARGUMENTS_LENGTH   = 5;
    private static final int    ARGUMENT_ZERO               = 0;
    private static final int    ARGUMENT_ONE                = 1;
    private static final int    ARGUMENT_TWO                = 2;
    private static final int    ARGUMENT_THREE              = 3;
    private static final int    STANDARD_CITIZEN_ID         = 0;

    private GetColonyAndCitizen()
    {
        throw new IllegalAccessError("Utility Class");
    }

    /**
     * Getting the colony ID.
     *
     * @param mayorID The ID of the mayor.
     * @param world   The world.
     * @param args    The arguments.
     * @return Return colony ID.
     */
    public static IToken getColonyId(@NotNull final UUID mayorID, @NotNull final World world, @NotNull final String... args)
    {
        IToken colonyId;
        if (args.length == NORMAL_ARGUMENT_LENGTH || args.length == ID_AND_NAME_ARGUMENT_LENGTH)
        {
            try
            {
                colonyId = StandardFactoryController.getInstance().getNewInstance(UUID.randomUUID(), args[ARGUMENT_ZERO]);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(String.format(ONLY_NUMBERS, "colony"));
            }
        }
        else if (args.length == SHORT_ARGUMENT_LENGTH || args.length == NAME_ARGUMENT_LENGTH)
        {
            final IColony colony = IAPI.Holder.getApi().getServerColonyManager().getControllerForWorld(world).getColonyByOwner(mayorID);
            if (colony == null)
            {
                throw new IllegalArgumentException(NO_COLONY);
            }
            else
            {
                colonyId = colony.getID();
            }
        }
        else if (args.length >= TOO_MANY_ARGUMENTS_LENGTH)
        {
            throw new IllegalArgumentException(TOO_MANY_ARGUMENTS);
        }
        else
        {
            throw new IllegalArgumentException(UNKNOWN_ERROR);
        }

        if (colonyId != null && IAPI.Holder.getApi().getServerColonyManager().getControllerForWorld(world).getColony(colonyId) == null)
        {
            throw new IllegalArgumentException(String.format(NOT_FOUND, "Colony"));
        }

        return colonyId;
    }

    /**
     * Getting the citizen ID.
     *
     * @param colonyId The colony ID for getting the citizen ID
     * @param world the world.
     * @param args     The given arguments
     * @return Returns citizen ID
     */
    public static int getCitizenId(@NotNull final IToken colonyId, final World world, @NotNull final String... args)
    {
        int citizenId;
        String citizenName;
        citizenId = STANDARD_CITIZEN_ID;

        if(colonyId == null)
        {
            return citizenId;
        }

        final IColony colony = IAPI.Holder.getApi().getServerColonyManager().getControllerForWorld(world).getColony(colonyId);

        if (args.length == NORMAL_ARGUMENT_LENGTH)
        {
            try
            {
                citizenId = Integer.parseInt(args[ARGUMENT_ONE]);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(String.format(ONLY_NUMBERS, "citizen"));
            }
        }
        else if (args.length == SHORT_ARGUMENT_LENGTH)
        {
            try
            {
                citizenId = Integer.parseInt(args[ARGUMENT_ZERO]);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(String.format(ONLY_NUMBERS, "citizen"));
            }
        }
        else if (args.length == NAME_ARGUMENT_LENGTH)
        {
            citizenName = args[ARGUMENT_ZERO] + " " + args[ARGUMENT_ONE] + " " + args[ARGUMENT_TWO];
            for (int i = 1; i <= colony.getCitizens().size(); i++)
            {
                if (colony.getCitizen(i).getName() != null && colony.getCitizen(i).getName().equals(citizenName))
                {
                    citizenId = i;
                }
            }
        }
        else if (args.length == ID_AND_NAME_ARGUMENT_LENGTH)
        {
            citizenName = args[ARGUMENT_ONE] + " " + args[ARGUMENT_TWO] + " " + args[ARGUMENT_THREE];
            for (int i = 1; i <= colony.getCitizens().size(); i++)
            {
                if (colony.getCitizen(i).getName().equals(citizenName))
                {
                    citizenId = i;
                }
            }
        }
        else if (args.length >= TOO_MANY_ARGUMENTS_LENGTH)
        {
            throw new IllegalArgumentException(TOO_MANY_ARGUMENTS);
        }
        else
        {
            throw new IllegalArgumentException(UNKNOWN_ERROR);
        }
        if (citizenId >= 0 && colony.getCitizen(citizenId) == null)
        {
            throw new IllegalArgumentException(String.format(NOT_FOUND, "Citizen"));
        }
        return citizenId;
    }
}

