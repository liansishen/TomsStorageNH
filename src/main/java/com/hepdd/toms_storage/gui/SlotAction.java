package com.hepdd.toms_storage.gui;

public enum SlotAction {

    PULL_OR_PUSH_STACK,
    PULL_ONE,
    SHIFT_PULL,
    GET_HALF,
    GET_QUARTER,
    QUICK_DEPOSIT,
    PUSH_MATCHING_FROM_PLAYER,
    CLEAR_GRID;

    public static final SlotAction[] VALUES = values();
}
