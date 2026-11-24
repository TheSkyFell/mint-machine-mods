package com.mintmachine.aalopac;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The FTB Teams → OPAC team rules, on made-up parties. */
class PartyLogicTest {
    int fails = 0, passes = 0;
    void check(String name, Object got, Object want) {
        if (Objects.equals(got, want)) passes++; else { fails++; System.out.println("FAIL " + name + ": got " + got + ", want " + want); }
    }
    @Test
    void rules() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), carol = UUID.randomUUID(), dave = UUID.randomUUID(), loner = UUID.randomUUID();
        UUID red = UUID.randomUUID(), blue = UUID.randomUUID(), green = UUID.randomUUID();
        // alice+bob in red; carol in blue; dave in green. red allies blue (one way); green allies red.
        Map<UUID, UUID> member = Map.of(alice, red, bob, red, carol, blue, dave, green);
        Map<UUID, Set<UUID>> allies = Map.of(red, Set.of(blue), green, Set.of(red), blue, Set.of());
        PartyLogic.Parties p = new PartyLogic.Parties() {
            public Optional<UUID> partyOf(UUID pl) { return Optional.ofNullable(member.get(pl)); }
            public boolean listsAsAlly(UUID party, UUID other) { return allies.getOrDefault(party, Set.of()).contains(other); }
        };
        check("same party", PartyLogic.areSameTeam(p, alice, bob), true);
        check("different party", PartyLogic.areSameTeam(p, alice, carol), false);
        check("self", PartyLogic.areSameTeam(p, loner, loner), true);
        check("no party vs party", PartyLogic.areSameTeam(p, loner, alice), false);
        check("two loners", PartyLogic.areSameTeam(p, loner, UUID.randomUUID()), false);
        // owner alice (red) lists blue: carol is allied to alice's assets
        check("owner party allies player party", PartyLogic.areAllied(p, alice, carol), true);
        // owner carol (blue) does not list red: alice is NOT allied to carol's assets
        check("one-way alliance reversed", PartyLogic.areAllied(p, carol, alice), false);
        check("green lists red: owner dave, player bob", PartyLogic.areAllied(p, dave, bob), true);
        check("teammates are not allies", PartyLogic.areAllied(p, alice, bob), false);
        check("partyless player not allied", PartyLogic.areAllied(p, alice, loner), false);
        check("team id", PartyLogic.teamIdForPlayer(p, bob), Optional.of(red));
        check("team id none", PartyLogic.teamIdForPlayer(p, loner), Optional.empty());
        check("bucket party", PartyLogic.activeVehicleLimitBucket(p, bob), red);
        check("bucket self", PartyLogic.activeVehicleLimitBucket(p, loner), loner);
        // canControl(player, owner, allowTeam, allowAllies)
        check("control teammate allowed", PartyLogic.canControl(p, bob, alice, true, false), Optional.of(true));
        check("control teammate disallowed", PartyLogic.canControl(p, bob, alice, false, false), Optional.of(false));
        check("control ally allowed", PartyLogic.canControl(p, carol, alice, false, true), Optional.of(true));
        check("control ally disallowed", PartyLogic.canControl(p, carol, alice, true, false), Optional.of(false));
        check("control stranger", PartyLogic.canControl(p, loner, alice, true, true), Optional.of(false));

        // client view of bob's own party (red: alice, bob; lists blue)
        class Own implements PartyLogic.OwnParty {
            boolean loading; UUID id; Set<UUID> members; Set<UUID> ally;
            Own(boolean l, UUID i, Set<UUID> m, Set<UUID> al) { loading = l; id = i; members = m; ally = al; }
            public boolean loading() { return loading; }
            public Optional<UUID> id() { return Optional.ofNullable(id); }
            public boolean hasMember(UUID x) { return members.contains(x); }
            public boolean listsAsAlly(UUID x) { return ally.contains(x); }
        }
        Own bobView = new Own(false, red, Set.of(alice, bob), Set.of(blue));
        check("client: loading -> unknown", PartyLogic.canControlFromClient(new Own(true, red, Set.of(), Set.of()), alice, Optional.empty(), true, true), Optional.empty());
        check("client: no party -> no", PartyLogic.canControlFromClient(new Own(false, null, Set.of(), Set.of()), alice, Optional.empty(), true, true), Optional.of(false));
        check("client: teammate", PartyLogic.canControlFromClient(bobView, alice, Optional.empty(), true, false), Optional.of(true));
        check("client: teammate but team not allowed", PartyLogic.canControlFromClient(bobView, alice, Optional.empty(), false, true), Optional.of(false));
        check("client: teammate via terminal hint", PartyLogic.canControlFromClient(bobView, UUID.randomUUID(), Optional.of(red), true, false), Optional.of(true));
        check("client: ally via hint", PartyLogic.canControlFromClient(bobView, carol, Optional.of(blue), false, true), Optional.of(true));
        check("client: non-ally via hint", PartyLogic.canControlFromClient(bobView, dave, Optional.of(green), false, true), Optional.of(false));
        check("client: allies not allowed", PartyLogic.canControlFromClient(bobView, carol, Optional.of(blue), true, false), Optional.of(false));
        check("client: owner party unknown -> unknown", PartyLogic.canControlFromClient(bobView, carol, Optional.empty(), true, true), Optional.empty());
        System.out.println(passes + " passed, " + fails + " failed");
        assertEquals(0, fails, "failed checks (listed above)");
    }
}
