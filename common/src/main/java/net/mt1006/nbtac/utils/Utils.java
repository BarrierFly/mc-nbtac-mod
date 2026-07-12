package net.mt1006.nbtac.utils;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.mt1006.nbtac.NBTac;
import net.mt1006.nbtac.config.ModConfig;
import net.mt1006.nbtac.mixin.fields.ClientLevelFields;
import net.mt1006.nbtac.mixin.fields.EntitySelectorFields;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public class Utils
{
	private static final CommandSourceStack DUMMY_COMMAND_SOURCE_STACK =
			new CommandSourceStack(null, Vec3.ZERO, Vec2.ZERO, null, 0, null, null, null, null);

	public static String getNodeString(CommandContext<?> ctx, int pos)
	{
		return ctx.getNodes().get(pos).getNode().getName();
	}

	public static String getCommandName(CommandContext<?> ctx)
	{
		String name = getNodeString(ctx, 0);
		if (ModConfig.supportCommandNamespace.val && name.startsWith("minecraft:"))
		{
			return name.substring(10);
		}
		return name;
	}

	public static String getArgumentString(CommandContext<?> ctx, String argumentName)
	{
		Map<String, ParsedArgument<?, ?>> arguments;

		try { arguments = (Map<String, ParsedArgument<?, ?>>)Fields.commandContextArguments.get(ctx); }
		catch (Exception e) { return null; }

		ParsedArgument<?, ?> argument = arguments.get(argumentName);

		return argument != null ? argument.getRange().get(ctx.getInput()) : null;
	}

	public static String blockFromCoords(Coordinates coords)
	{
		if (!(coords instanceof WorldCoordinates)) { return null; }
		if (coords.isXRelative() || coords.isYRelative() || coords.isZRelative()) { return null; }
		BlockPos blockPos = coords.getBlockPos(DUMMY_COMMAND_SOURCE_STACK);

		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) { return null; }
		Block block = level.getBlockState(blockPos).getBlock();

		return "block/" + RegistryUtils.BLOCK.getKey(block);
	}

	public static @Nullable String entityFromEntitySelector(EntitySelector entitySelector)
	{
		return entityFromSelectorData(
				((EntitySelectorFields)entitySelector).getType(),
				((EntitySelectorFields)entitySelector).getEntityUUID(),
				((EntitySelectorFields)entitySelector).getPlayerName());
	}

	public static @Nullable String resolveEntityType(EntitySelector selector, String input, int cursorPos)
	{
		return resolveEntityType(
				((EntitySelectorFields)selector).getType(),
				((EntitySelectorFields)selector).getEntityUUID(),
				((EntitySelectorFields)selector).getPlayerName(),
				input, cursorPos);
	}

	public static @Nullable String resolveEntityType(EntityTypeTest<Entity, ?> typeTest, @Nullable UUID uuid,
		@Nullable String playerName, String input, int cursorPos)
	{
		String result = entityFromSelectorData(typeTest, uuid, playerName);
		if (result != null) { return result; }

		result = resolveExecuteAsContext(input, cursorPos);
		if (result != null) { return result; }

		if (Minecraft.getInstance().player != null)
		{
			return "entity/" + EntityType.getKey(EntityType.PLAYER);
		}
		return null;
	}

	public static @Nullable String resolveExecuteAsContext(String input, int cursorPos)
	{
		if (input == null) { return null; }
		int end = Math.min(cursorPos, input.length());
		if (end < 5) { return null; }

		int bracketDepth = 0;

		for (int i = end - 1; i >= 4; i--)
		{
			char c = input.charAt(i);
			if (c == ']' || c == '}') { bracketDepth++; }
			else if (c == '[' || c == '{')
			{
				if (bracketDepth > 0) { bracketDepth--; }
			}

			if (bracketDepth != 0) { continue; }

			if (c == ' ' && input.charAt(i - 1) == 's'
				&& input.charAt(i - 2) == 'a' && input.charAt(i - 3) == ' ')
			{
				if (!isExecuteAsContext(input, i - 3)) { continue; }

				String target = extractRawTarget(input, i + 1);
				if (target == null) { continue; }

				if (isSelfSelector(target)) { continue; }

				String entityType = entityTypeFromRawTarget(target);
				if (entityType != null) { return entityType; }
			}
		}

		return null;
	}

	private static @Nullable String extractRawTarget(String text, int start)
	{
		if (start >= text.length()) { return null; }
		while (start < text.length() && text.charAt(start) == ' ') { start++; }
		if (start >= text.length()) { return null; }

		int bracketDepth = 0;
		int i = start;

		if (text.charAt(start) == '@')
		{
			i++;
			if (i < text.length()) { i++; }
			while (i < text.length())
			{
				char c = text.charAt(i);
				if (c == '[') { bracketDepth++; }
				else if (c == ']')
				{
					if (bracketDepth == 0) { break; }
					bracketDepth--;
				}
				else if (c == ' ' && bracketDepth == 0) { break; }
				i++;
			}
		}
		else
		{
			while (i < text.length() && text.charAt(i) != ' ') { i++; }
		}

		if (i == start) { return null; }
		return text.substring(start, i);
	}

	private static boolean isSelfSelector(String target)
	{
		return target.length() >= 2 && target.charAt(0) == '@' && target.charAt(1) == 's'
			&& (target.length() == 2 || target.charAt(2) == '[');
	}

	private static boolean isExecuteAsContext(String text, int spaceBeforeAs)
	{
		int bracketDepth = 0;

		for (int i = spaceBeforeAs - 1; i >= 0; i--)
		{
			char c = text.charAt(i);
			if (c == ']' || c == '}') { bracketDepth++; }
			else if (c == '[' || c == '{')
			{
				if (bracketDepth > 0) { bracketDepth--; }
			}

			if (bracketDepth != 0) { continue; }

			if (i >= 3 && text.charAt(i) == ' '
				&& text.charAt(i - 1) == 'n' && text.charAt(i - 2) == 'u' && text.charAt(i - 3) == 'r'
				&& (i < 4 || text.charAt(i - 4) == ' '))
			{
				return false;
			}
			if (i == 2 && text.startsWith("run ")) { return false; }

			if (i >= 7 && text.charAt(i) == ' '
				&& text.substring(i - 7, i).equals("execute")
				&& (i < 8 || text.charAt(i - 8) == ' ' || text.charAt(i - 8) == '/'))
			{
				return true;
			}
			if (i == 6 && text.startsWith("execute ")) { return true; }
			if (i == 7 && text.startsWith("/execute ")) { return true; }
		}

		return false;
	}

	private static @Nullable String entityTypeFromRawTarget(String target)
	{
		if (target.isEmpty()) { return null; }

		if (target.charAt(0) == '@' && target.length() >= 2)
		{
			char selType = target.charAt(1);
			boolean bare = target.length() == 2 || target.charAt(2) == '[';

			if (bare && (selType == 'p' || selType == 'a' || selType == 'r'))
			{
				return "entity/" + EntityType.getKey(EntityType.PLAYER);
			}

			if (selType == 'e' && target.length() > 3 && target.charAt(2) == '[')
			{
				int typeIdx = target.indexOf("type=");
				if (typeIdx != -1)
				{
					int valStart = typeIdx + 5;
					int valEnd = valStart;
					while (valEnd < target.length() && target.charAt(valEnd) != ','
						&& target.charAt(valEnd) != ']') { valEnd++; }
					String typeVal = target.substring(valStart, valEnd).trim();
					if (typeVal.startsWith("!")) { typeVal = typeVal.substring(1); }
					if (typeVal.startsWith("#")) { return null; }
					ResourceLocation id = ResourceLocation.tryParse(typeVal);
					if (id != null) { return "entity/" + id; }
				}
				return null;
			}

			return null;
		}

		ClientLevel level = Minecraft.getInstance().level;
		if (level != null)
		{
			for (Player player : level.players())
			{
				if (player.getGameProfile().getName().equals(target))
				{
					return "entity/" + EntityType.getKey(EntityType.PLAYER);
				}
			}
		}

		return null;
	}

	public static @Nullable String entityFromSelectorData(EntityTypeTest<Entity, ?> typeTest, @Nullable UUID uuid, @Nullable String playerName)
	{
		if (typeTest instanceof EntityType)
		{
			return "entity/" + RegistryUtils.ENTITY_TYPE.getKey((EntityType<?>)typeTest);
		}

		ClientLevel clientLevel = Minecraft.getInstance().level;
		if (clientLevel == null) { return null; }

		if (uuid != null)
		{
			try
			{
				TransientEntitySectionManager<Entity> entityStorage = ((ClientLevelFields)clientLevel).getEntityStorage();

				Entity entity = entityStorage.getEntityGetter().get(uuid);
				if (entity == null) { return null; }

				return "entity/" + RegistryUtils.ENTITY_TYPE.getKey(entity.getType());
			}
			catch (Exception ignore) {}
		}

		if (playerName == null) { return null; }

		for (Player player : clientLevel.players())
		{
			if (player.getGameProfile().getName().equals(playerName))
			{
				return "entity/" + EntityType.getKey(EntityType.PLAYER);
			}
		}
		return null;
	}

	public static boolean isModPresent(String id)
	{
		if (NBTac.loaderInterface == null) { return false; }
		return NBTac.loaderInterface.isModPresent(id);
	}
}
