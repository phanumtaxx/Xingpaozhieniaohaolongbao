package dev.xingclient.manager;

public enum ActionResource {
    MAIN_HAND, OFF_HAND, INVENTORY, ROTATION,
    BLOCK_PLACE_PACKET, ITEM_USE_PACKET, ATTACK_PACKET, MINE_PACKET;

    static int mask(ActionResource... resources) {
        int mask = 0;
        for (ActionResource resource : resources) mask |= 1 << resource.ordinal();
        return mask;
    }
}
