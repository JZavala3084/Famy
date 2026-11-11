package com.famy;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Verifica los límites del monolito modular (sin ciclos ni acceso a paquetes internos de otro módulo). */
class ModularityTest {

    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(FamyApplication.class).verify();
    }
}
