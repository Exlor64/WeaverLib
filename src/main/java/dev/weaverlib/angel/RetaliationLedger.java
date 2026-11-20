package dev.weaverlib.angel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bonus belongs to the attacker who inflicted the damage; any successful outgoing hit consumes the effect. */
public final class RetaliationLedger {
    private final Map<UUID,Double> damage=new HashMap<>();
    public void record(UUID attacker,double amount){if(Double.isFinite(amount) && amount>0)damage.merge(attacker,amount,CelestialRules::retaliation);}
    public double bonus(UUID target){return damage.getOrDefault(target,0D);}
    public double total(){return damage.values().stream().mapToDouble(Double::doubleValue).sum();}
    public double consume(UUID target){double result=bonus(target);damage.clear();return result;}
}
