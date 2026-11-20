package dev.weaverlib.angel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Single-use offers are addressed to one paladin and expire on the server's clock. */
public final class OathOfferBook {
    public record Offer(UUID lord,UUID paladin,long until) {}
    private final Map<UUID,Offer> offers=new HashMap<>();
    public UUID offer(UUID lord,UUID paladin,long now) {
        prune(now);offers.entrySet().removeIf(e->e.getValue().paladin.equals(paladin));
        UUID token=UUID.randomUUID();offers.put(token,new Offer(lord,paladin,now+600));return token;
    }
    public Offer claim(UUID token,UUID paladin,long now) {
        Offer offer=offers.get(token);
        if(offer==null || !offer.paladin.equals(paladin))return null;
        offers.remove(token);return now<=offer.until?offer:null;
    }
    public void prune(long now){offers.entrySet().removeIf(e->now>e.getValue().until);}
    public void clear(){offers.clear();}
}
