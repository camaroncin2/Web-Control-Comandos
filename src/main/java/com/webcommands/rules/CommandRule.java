package com.webcommands.rules;

import java.util.ArrayList;
import java.util.List;

/** Stores blocking rules for a single root command. */
public class CommandRule {

    /** Block command execution for ALL players (overrides the other flags). */
    public boolean blockedForAll;

    /** Block command execution for players with OP level >= 1. */
    public boolean blockedForOp;

    /** Block command execution for specific player names (case-insensitive match). */
    public List<String> blockedUsernames;

    public CommandRule() {
        this.blockedForAll = false;
        this.blockedForOp = false;
        this.blockedUsernames = new ArrayList<>();
    }

    public CommandRule(boolean blockedForAll, boolean blockedForOp, List<String> blockedUsernames) {
        this.blockedForAll = blockedForAll;
        this.blockedForOp = blockedForOp;
        this.blockedUsernames = blockedUsernames != null ? new ArrayList<>(blockedUsernames) : new ArrayList<>();
    }

    /** Returns true if this rule has no actual restrictions (safe to remove). */
    public boolean isEmpty() {
        return !blockedForAll && !blockedForOp && (blockedUsernames == null || blockedUsernames.isEmpty());
    }
}
