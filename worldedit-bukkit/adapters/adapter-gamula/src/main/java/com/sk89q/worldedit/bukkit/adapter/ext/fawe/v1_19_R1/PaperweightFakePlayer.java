/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.bukkit.adapter.ext.fawe.v1_19_R1;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.PacketPlayInSettings;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.stats.Statistic;
import net.minecraft.world.ITileInventory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.TileEntitySign;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.OptionalInt;
import java.util.UUID;

class PaperweightFakePlayer extends EntityPlayer {
    private static final GameProfile FAKE_WORLDEDIT_PROFILE = new GameProfile(UUID.nameUUIDFromBytes("worldedit".getBytes()), "[WorldEdit]");
    private static final Vec3D ORIGIN = new Vec3D(0.0D, 0.0D, 0.0D);

    PaperweightFakePlayer(WorldServer world) {
        super(world.getServer(), world, FAKE_WORLDEDIT_PROFILE, null);
    }

    @Override
    public Vec3D position() {
        return ORIGIN;
    }

    @Override
    public void tick() {
    }

    @Override
    public void die(DamageSource damagesource) {
    }

    @Override
    public Entity changeDimension(WorldServer worldserver, TeleportCause cause) {
        return this;
    }

    @Override
    public OptionalInt openMenu(ITileInventory factory) {
        return OptionalInt.empty();
    }

    @Override
    public void updateOptions(PacketPlayInSettings packet) {
    }

    @Override
    public void displayClientMessage(IChatBaseComponent message, boolean actionBar) {
    }

    @Override
    public void awardStat(Statistic<?> stat, int amount) {
    }

    @Override
    public void awardStat(Statistic<?> stat) {
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return true;
    }

    @Override
    public void openTextEdit(TileEntitySign sign) {
    }
}
