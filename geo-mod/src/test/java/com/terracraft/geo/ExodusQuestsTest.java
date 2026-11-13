package com.terracraft.geo;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExodusQuestsTest {
    @Test void campaignCanBeCompletedWithoutACycle() {
        Set<String> done=new HashSet<>();
        Map<String,Long> facts=new HashMap<>();
        ExodusQuests.ALL.forEach(q -> q.goals().forEach(g -> facts.merge(g.fact(),g.target(),Math::max)));
        for(int pass=0;pass<19;pass++) for(var q:ExodusQuests.ALL)
            if(ExodusQuests.ready(q,done,facts)) done.add(q.id());
        assertEquals(19,ExodusQuests.ALL.size());
        assertEquals(19,done.size());
        assertEquals(Set.of(1,2,3,4,5),new HashSet<>(ExodusQuests.ALL.stream().map(ExodusQuests.Quest::act).toList()));
    }
    @Test void cityIsRequiredBeforeRaidButNotBehindIt() {
        Set<String> done=new HashSet<>(Set.of("q1_1_ashes"));
        assertTrue(ExodusQuests.unlocked(ExodusQuests.find("q1_3_town"),done));
        assertFalse(ExodusQuests.unlocked(ExodusQuests.find("q1_2_night"),done));
        done.add("q1_3_town");
        assertTrue(ExodusQuests.unlocked(ExodusQuests.find("q1_2_night"),done));
        assertTrue(ExodusQuests.unlocked(ExodusQuests.find("q1_4_trade"),done));
        assertFalse(ExodusQuests.unlocked(ExodusQuests.find("q2_1_oil_hunt"),done));
    }
    @Test void possessionAndFutureEvidenceDoNotBypassPrerequisites() {
        var q=ExodusQuests.find("q2_3_refining");
        Map<String,Long> facts=new HashMap<>(Map.of("gasoline",10000L,"kerosene",999L));
        assertFalse(ExodusQuests.ready(q,Set.of("q2_2_iron_blood"),facts));
        facts.put("kerosene",1000L);
        assertFalse(ExodusQuests.ready(q,Set.of(),facts));
        assertTrue(ExodusQuests.ready(q,Set.of("q2_2_iron_blood"),facts));
        assertFalse(ExodusQuests.ready(q,Set.of("q2_2_iron_blood",q.id()),facts));
    }
    @Test void pendingRewardAndCoreIdentitySurviveRestart() {
        var state=new ExodusState(); UUID player=UUID.randomUUID(); var j=state.journal(player);
        j.pending="q1_1_ashes";j.core=UUID.randomUUID().toString();j.contribution="ville";
        j.facts.put("loot",1L);j.done.add("q1_3_town");
        Gson gson=new Gson();var restored=gson.fromJson(gson.toJson(state),ExodusState.class).journal(player);
        assertEquals(j.pending,restored.pending);assertEquals(j.core,restored.core);
        assertEquals(j.contribution,restored.contribution);assertEquals(j.facts,restored.facts);
        assertEquals(j.done,restored.done);
    }
    @Test void regionalRestorationHasAnExactBoundary() {
        var r=new ExodusState.Region();r.x=-100;r.z=20;
        assertFalse(ExodusState.contains(r,-100,20));r.active=true;
        assertTrue(ExodusState.contains(r,-36,20));
        assertFalse(ExodusState.contains(r,-35,20));
        assertFalse(ExodusState.contains(r,-36,84));
    }
    @Test void bookPaginationPreservesEveryWordAndBlankLine() {
        String section="TERRA EXODUS\n\n"+"Réparer les machines et préserver l’oxygène. ".repeat(70);
        var pages=BookPages.paginate(List.of(section));
        assertTrue(pages.size()>1);
        for(String page:pages) {
            assertTrue(page.split("\\n",-1).length<=13);
            for(String line:page.split("\\n",-1)) assertTrue(line.length()<=18,line);
        }
        assertEquals(section.replaceAll("\\s+"," ").trim(),String.join(" ",pages).replaceAll("\\s+"," ").trim());
        assertTrue(pages.getFirst().startsWith("TERRA EXODUS\n\n"));
    }
    @Test void bookRefusesSilentTruncation() {
        assertThrows(IllegalArgumentException.class,() -> BookPages.paginate(Collections.nCopies(101,"Page")));
    }
}
