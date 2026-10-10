package dev.xingclient.event;

import net.minecraft.entity.decoration.EndCrystalEntity;

/** Raised after an end crystal has been added to the client world. */
public record CrystalSpawnEvent(EndCrystalEntity crystal) implements ClientEvent {}
