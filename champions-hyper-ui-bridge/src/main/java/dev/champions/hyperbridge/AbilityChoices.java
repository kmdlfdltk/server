package dev.champions.hyperbridge;

import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.abilities.AbilityPool;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AbilityChoices {
    public record Choice(String id, boolean hidden, PotentialAbility candidate) {}
    private AbilityChoices() {}

    private static void addChoices(
            AbilityPool pool, Pokemon p, List<Choice> choices) {
        for(PotentialAbility item:pool) {
            // Hidden abilities are selectable even if the current Pokemon has no
            // hidden-ability aspect/coordinate yet. They still must be
            // explicitly present in this species/form ability pool.
            if(!item.isSatisfiedBy(p.getAspects()) && !(item instanceof HiddenAbility))
                continue;
            String id=item.getTemplate().getName().toLowerCase(Locale.ROOT);
            boolean hidden=item instanceof HiddenAbility;
            boolean found=false;
            for (int i=0;i<choices.size();i++){
                Choice previous=choices.get(i);
                if(previous.id().equals(id)){
                    // The hidden flag wins if a form defines this ability as hidden.
                    if(hidden && !previous.hidden())
                        choices.set(i,new Choice(id,true,item));
                    found=true;break;
                }
            }
            if(!found)choices.add(new Choice(id,hidden,item));
        }
    }

    public static List<Choice> forPokemon(Pokemon p) {
        List<Choice> choices=new ArrayList<>();
        addChoices(p.getForm().getAbilities(),p,choices);
        // Base forms can inherit options from the species ability pool.
        // This also recovers hidden abilities omitted by a sparse form override.
        if(choices.isEmpty() ||
           p.getForm().getName().equalsIgnoreCase("standard") ||
           p.getForm().getName().equalsIgnoreCase("normal")) {
            addChoices(p.getSpecies().getAbilities(),p,choices);
        }
        return List.copyOf(choices);
    }

    public static Choice require(Pokemon p,String id) {
        String normalized=id==null?"":id.trim().toLowerCase(Locale.ROOT);
        if(normalized.startsWith("h:"))normalized=normalized.substring(2);
        if(normalized.startsWith("hidden:"))normalized=normalized.substring(7);
        if(normalized.startsWith("cobblemon:"))normalized=normalized.substring(10);
        final String wanted=normalized;
        return forPokemon(p).stream()
            .filter(x->x.id().equals(wanted))
            .findFirst()
            .orElseThrow(()->new IllegalArgumentException(
                "해당 포켓몬이 가질 수 없는 특성: "+wanted));
    }

    public static void set(Pokemon p, Choice choice) {
        Ability ability=choice.candidate().getTemplate()
            .create(false,choice.candidate().getPriority());
        p.updateAbility(ability);
        if(!p.getAbility().getName().equalsIgnoreCase(choice.id()))
            throw new IllegalStateException("특성 변경이 적용되지 않았습니다");
    }
}