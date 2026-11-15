package dev.weaverlib.spell;

import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.weaverlib.rune.RuneSpell;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record InscribedSpell(UUID id, Either<Spell, RuneSpell> spell, String words, boolean efficient,
                             Optional<Boolean> addedBattery, Optional<Integer> originalStackSize) {
    public static final Codec<InscribedSpell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(InscribedSpell::id),
        Codec.either(Spell.CODEC, RuneSpell.CODEC).fieldOf("spell").forGetter(InscribedSpell::spell),
        Codec.string(1, 2048).fieldOf("words").forGetter(InscribedSpell::words),
        Codec.BOOL.fieldOf("efficient").forGetter(InscribedSpell::efficient),
        Codec.BOOL.optionalFieldOf("added_battery").forGetter(InscribedSpell::addedBattery),
        Codec.intRange(1, 99).optionalFieldOf("original_stack_size").forGetter(InscribedSpell::originalStackSize)
    ).apply(instance, InscribedSpell::new));

    public InscribedSpell(UUID id, Either<Spell, RuneSpell> spell, String words, boolean efficient) {
        this(id, spell, words, efficient, Optional.empty(), Optional.empty());
    }

    public double multiplier() {
        return efficient ? 1.2 : 1.5;
    }

    public int cost(float normalVis) {
        return (int) Math.ceil(normalVis * multiplier());
    }
}
