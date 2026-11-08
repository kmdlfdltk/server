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
            if(!item.isSatisfiedBy(p.getAspects()))continue;
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
        if(choices.isEmpty())addChoices(p.getSpecies().getAbilities(),p,choices);
        return List.copyOf(choices);
    }

    public static Choice require(Pokemon p,String id) {
        String normalized=id==null?"":id.trim().toLowerCase(Locale.ROOT);
        return forPokemon(p).stream()
            .filter(x->x.id().equals(normalized))
            .findFirst()
            .orElseThrow(()->new IllegalArgumentException(
                "해당 포켓몬이 가질 수 없는 특성: "+normalized));
    }

    public static void set(Pokemon p, Choice choice) {
        Ability ability=choice.candidate().getTemplate()
            .create(false,choice.candidate().getPriority());
        p.updateAbility(ability);
        if(!p.getAbility().getName().equalsIgnoreCase(choice.id()))
            throw new IllegalStateException("특성 변경이 적용되지 않았습니다");
    }
}