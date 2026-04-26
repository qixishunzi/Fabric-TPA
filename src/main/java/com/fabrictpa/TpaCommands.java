package com.fabrictpa;

import java.util.Set;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TpaCommands {
    private static final SimpleCommandExceptionType HOME_NOT_SET = new SimpleCommandExceptionType(Component.literal("家尚未设置。"));
    private static final SimpleCommandExceptionType PLAYER_ONLY = new SimpleCommandExceptionType(Component.literal("该命令只能由玩家执行。"));
    private static final SimpleCommandExceptionType HOME_WORLD_MISSING = new SimpleCommandExceptionType(Component.literal("家的目标维度不存在。"));

    private TpaCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(Commands.literal("home")
                .executes(TpaCommands::teleportToHome))
            .then(Commands.literal("set")
                .requires(TpaCommands::isAdmin)
                .then(Commands.literal("home")
                    .executes(TpaCommands::setHome)))
            .then(Commands.argument("destination", EntityArgument.player())
                .requires(TpaCommands::isPlayer)
                .executes(TpaCommands::teleportSelfToEntity))
            .then(Commands.argument("location", Vec3Argument.vec3())
                .requires(TpaCommands::isPlayer)
                .executes(TpaCommands::teleportSelfToLocation)));
    }

    private static boolean isPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.OWNERS));
    }

    private static int setHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        ServerLevel world = (ServerLevel) player.level();
        Vec3 pos = player.position();
        FabricTpaMod.getHomeStorage(context.getSource().getServer()).setHome(player);
        context.getSource().sendSuccess(
            () -> Component.literal("已将家设置在坐标 ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(formatWithDimension(world, pos)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("。").withStyle(ChatFormatting.YELLOW)),
            false
        );
        FabricTpaMod.LOGGER.info("{} set home at {} in {}.", player.getName().getString(), formatCoordinates(pos), formatDimensionEnglish(world.dimension()));
        return Command.SINGLE_SUCCESS;
    }

    private static int teleportToHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        HomeStorage storage = FabricTpaMod.getHomeStorage(context.getSource().getServer());
        HomeStorage.SavedLocation home = storage.getHome().orElseThrow(HOME_NOT_SET::create);

        ServerLevel world;
        try {
            world = storage.getWorld(home);
        } catch (IllegalStateException exception) {
            throw HOME_WORLD_MISSING.create();
        }

        teleportEntity(player, world, home.pos(), home.yaw(), home.pitch());
        broadcast(
            context,
            coloredMessage(player.getName().getString(), "家", formatWithDimension(world, home.pos())),
            String.format("%s teleported home at %s in %s.", player.getName().getString(), formatCoordinates(home.pos()), formatDimensionEnglish(world.dimension()))
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int teleportSelfToEntity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        ServerPlayer destination = EntityArgument.getPlayer(context, "destination");
        teleportToEntity(player, destination);
        broadcast(
            context,
            coloredMessage(player.getName().getString(), destination.getName().getString(), formatWithDimension((ServerLevel) destination.level(), destination.position())),
            String.format(
                "%s teleported to %s at %s in %s.",
                player.getName().getString(),
                destination.getName().getString(),
                formatCoordinates(destination.position()),
                formatDimensionEnglish(destination.level().dimension())
            )
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int teleportSelfToLocation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        Vec3 location = getLocation(context, "location");
        ServerLevel world = (ServerLevel) player.level();
        teleportEntity(player, world, location, player.getYRot(), player.getXRot());
        broadcast(
            context,
            coloredCoordinateMessage(player.getName().getString(), formatWithDimension(world, location)),
            String.format("%s teleported to %s in %s.", player.getName().getString(), formatCoordinates(location), formatDimensionEnglish(world.dimension()))
        );
        return Command.SINGLE_SUCCESS;
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            return context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            throw PLAYER_ONLY.create();
        }
    }

    private static Vec3 getLocation(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return Vec3Argument.getVec3(context, name);
    }

    private static void teleportToEntity(Entity target, Entity destination) {
        teleportEntity(target, (ServerLevel) destination.level(), destination.position(), destination.getYRot(), destination.getXRot());
    }

    private static void teleportEntity(Entity entity, ServerLevel world, Vec3 pos, float yaw, float pitch) {
        entity.teleportTo(world, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, false);
    }

    private static void broadcast(CommandContext<CommandSourceStack> context, Component message, String consoleMessage) {
        for (ServerPlayer targetPlayer : context.getSource().getServer().getPlayerList().getPlayers()) {
            targetPlayer.sendSystemMessage(message);
        }
        FabricTpaMod.LOGGER.info(consoleMessage);
    }

    private static Component coloredMessage(String playerName, String targetName, String location) {
        MutableComponent message = Component.empty();
        message.append(Component.literal(playerName).withStyle(ChatFormatting.AQUA));
        message.append(Component.literal(" 传送到了 ").withStyle(ChatFormatting.YELLOW));
        message.append(Component.literal(targetName).withStyle(ChatFormatting.GREEN));
        message.append(Component.literal("，坐标 ").withStyle(ChatFormatting.YELLOW));
        message.append(Component.literal(location).withStyle(ChatFormatting.GOLD));
        message.append(Component.literal("。").withStyle(ChatFormatting.YELLOW));
        return message;
    }

    private static Component coloredCoordinateMessage(String playerName, String location) {
        MutableComponent message = Component.empty();
        message.append(Component.literal(playerName).withStyle(ChatFormatting.AQUA));
        message.append(Component.literal(" 传送到了坐标 ").withStyle(ChatFormatting.YELLOW));
        message.append(Component.literal(location).withStyle(ChatFormatting.GOLD));
        message.append(Component.literal("。").withStyle(ChatFormatting.YELLOW));
        return message;
    }

    private static String formatWithDimension(ServerLevel world, Vec3 pos) {
        return formatCoordinates(pos) + "，维度 " + formatDimension(world.dimension());
    }

    private static String formatCoordinates(Vec3 pos) {
        var blockPos = net.minecraft.core.BlockPos.containing(pos);
        return "(" + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ() + ")";
    }

    private static String formatDimension(ResourceKey<Level> dimension) {
        var id = dimension.identifier();
        if (Level.OVERWORLD.identifier().equals(id)) {
            return "主世界";
        }
        if (Level.NETHER.identifier().equals(id)) {
            return "下界";
        }
        if (Level.END.identifier().equals(id)) {
            return "末地";
        }
        return id.toString();
    }

    private static String formatDimensionEnglish(ResourceKey<Level> dimension) {
        var id = dimension.identifier();
        if (Level.OVERWORLD.identifier().equals(id)) {
            return "overworld";
        }
        if (Level.NETHER.identifier().equals(id)) {
            return "nether";
        }
        if (Level.END.identifier().equals(id)) {
            return "the_end";
        }
        return id.toString();
    }
}
