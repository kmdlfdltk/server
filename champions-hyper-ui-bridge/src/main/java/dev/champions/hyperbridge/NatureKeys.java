package dev.champions.hyperbridge;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Accepts exact English Cobblemon nature IDs, namespace IDs and Korean labels. */
public final class NatureKeys {
    private static final Set<String> VALID=Set.of(
        "hardy","lonely","brave","adamant","naughty","bold","docile",
        "relaxed","impish","lax","timid","hasty","serious","jolly",
        "naive","modest","mild","quiet","bashful","rash","calm",
        "gentle","sassy","careful","quirky");
    private static final Map<String,String> KO=Map.ofEntries(
        Map.entry("노력","hardy"),
        Map.entry("외로움","lonely"),
        Map.entry("용감","brave"),
        Map.entry("고집","adamant"),
        Map.entry("개구쟁이","naughty"),
        Map.entry("대담","bold"),
        Map.entry("온순","docile"),
        Map.entry("무사태평","relaxed"),
        Map.entry("장난꾸러기","impish"),
        Map.entry("촐랑","lax"),
        Map.entry("겁쟁이","timid"),
        Map.entry("성급","hasty"),
        Map.entry("성실","serious"),
        Map.entry("명랑","jolly"),
        Map.entry("천진난만","naive"),
        Map.entry("조심","modest"),
        Map.entry("의젓","mild"),
        Map.entry("냉정","quiet"),
        Map.entry("수줍음","bashful"),
        Map.entry("덜렁","rash"),
        Map.entry("차분","calm"),
        Map.entry("얌전","gentle"),
        Map.entry("건방","sassy"),
        Map.entry("신중","careful"),
        Map.entry("변덕","quirky")
    );
    private NatureKeys(){}

    public static String require(String input) {
        if(input==null)throw new IllegalArgumentException("성격 선택 값이 비어 있습니다");
        String key=input.trim().toLowerCase(Locale.ROOT);
        if(key.startsWith("cobblemon:"))key=key.substring("cobblemon:".length());
        if(key.startsWith("nature."))key=key.substring("nature.".length());
        key=KO.getOrDefault(key,key);
        if(!VALID.contains(key))throw new IllegalArgumentException(
            "지원되지 않는 성격: "+input);
        return key;
    }
}
