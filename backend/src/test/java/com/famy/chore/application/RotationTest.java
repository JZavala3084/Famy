package com.famy.chore.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RotationTest {

    private final UUID ana = UUID.randomUUID();
    private final UUID luis = UUID.randomUUID();
    private final UUID sofi = UUID.randomUUID();

    @Test
    void advancesInOrderAndWrapsAround() {
        List<UUID> order = List.of(ana, luis, sofi);
        Set<UUID> active = Set.of(ana, luis, sofi);
        assertThat(ChoreService.nextInRotation(order, ana, active)).isEqualTo(luis);
        assertThat(ChoreService.nextInRotation(order, sofi, active)).isEqualTo(ana);
    }

    @Test
    void skipsFormerMembersAndStartsAtTheTopIfCurrentIsUnknown() {
        List<UUID> order = List.of(ana, luis, sofi);
        assertThat(ChoreService.nextInRotation(order, ana, Set.of(ana, sofi))).isEqualTo(sofi);
        assertThat(ChoreService.nextInRotation(order, UUID.randomUUID(), Set.of(luis, sofi))).isEqualTo(luis);
        assertThat(ChoreService.nextInRotation(order, ana, Set.of())).isEqualTo(ana);
    }
}
