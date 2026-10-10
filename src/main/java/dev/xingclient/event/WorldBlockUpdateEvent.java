package dev.xingclient.event;

/** Raised after a server block update has been applied to the client world. */
public record WorldBlockUpdateEvent() implements ClientEvent {}
