package dev.champions.hyperbridge;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.List;

public final class BridgePrices {
    private static final Stats[] STATS={
        Stats.HP,Stats.ATTACK,Stats.DEFENCE,
        Stats.SPECIAL_ATTACK,Stats.SPECIAL_DEFENCE,Stats.SPEED
    };
    private BridgePrices(){}

    private static int val(List<Integer> values,int idx) {
        if(values==null||idx>=values.size())
            throw new IllegalArgumentException("필요한 숫자가 빠졌습니다");
        return values.get(idx);
    }
    private static long multiply(long a,long b) {
        return Math.multiplyExact(a,b);
    }
    private static long diff6(Pokemon p,List<Integer> values,boolean iv) {
        if(values==null||values.size()!=6)
            throw new IllegalArgumentException("6개 능력치를 입력해 주세요");
        long total=0;
        for(int i=0;i<6;i++){
            int target=values.get(i);
            if(target<0||target>(iv?31:252))
                throw new IllegalArgumentException("능력치 범위를 벗어났습니다");
            int before=iv?p.getIvs().getOrDefault(STATS[i]):p.getEvs().getOrDefault(STATS[i]);
            total+=Math.abs(target-before);
        }
        return total;
    }

    public static long quote(Pokemon p,int op,List<Integer> ints,String text) {
        BridgeEconomyConfig c=BridgeEconomyConfig.INSTANCE;
        return switch(op) {
            case 1 -> {
                int increase=val(ints,0);
                if(increase<=0||p.getLevel()+increase>100)
                    throw new IllegalArgumentException("레벨 상승 값이 잘못되었습니다");
                yield multiply(increase,c.levelPerPoint);
            }
            case 2 -> c.levelReset;
            case 3 -> multiply(diff6(p,ints,true),c.ivPerPoint);
            case 4 -> {
                int sum=0;
                for(int amount:ints)sum+=amount;
                if(sum>510)throw new IllegalArgumentException("노력치 합계 510 초과");
                yield multiply(diff6(p,ints,false),c.evPerPoint);
            }
            case 5 -> p.getNature().getName().getPath().equalsIgnoreCase(text)
                ?0:c.nature;
            case 6 -> {
                if(!c.allowAbilityEditing)
                    throw new IllegalStateException("특성 편집이 비활성화되었습니다");
                var selected=AbilityChoices.require(p,text);
                yield p.getAbility().getName().equalsIgnoreCase(selected.id())
                    ?0:(selected.hidden()?c.hiddenAbility:c.ability);
            }
            case 9 -> {
                int target=val(ints,0);
                if(target<0||target>255)
                    throw new IllegalArgumentException("친밀도 범위 초과");
                yield multiply(Math.abs(target-p.getFriendship()),c.friendshipPerPoint);
            }
            default -> throw new UnsupportedOperationException(
                "아직 결제·수정을 지원하지 않는 항목");
        };
    }
}