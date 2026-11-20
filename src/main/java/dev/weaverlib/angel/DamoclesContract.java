package dev.weaverlib.angel;

import java.util.UUID;

/** A verdict is claimed once, before any damage callback can recurse into the other partner. */
public final class DamoclesContract {
    private final UUID first,second;
    private boolean resolved;
    public DamoclesContract(UUID first,UUID second){this.first=first;this.second=second;}
    public boolean violate(UUID attacker){if(resolved || !first.equals(attacker) && !second.equals(attacker))return false;resolved=true;return true;}
}
