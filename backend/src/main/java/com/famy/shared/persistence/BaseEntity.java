package com.famy.shared.persistence;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Base para entidades con id asignado por la aplicación (UUID v7): evita el SELECT previo de
 * {@code merge} indicando a Spring Data cuándo la entidad es nueva.
 */
@MappedSuperclass
public abstract class BaseEntity<ID> implements Persistable<ID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
