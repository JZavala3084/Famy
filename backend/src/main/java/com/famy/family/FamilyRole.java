package com.famy.family;

/** Roles de una familia (ver docs/06-familias.md#roles-y-permisos). */
public enum FamilyRole {
    OWNER,
    ADMIN,
    MEMBER,
    CHILD;

    public boolean isManager() {
        return this == OWNER || this == ADMIN;
    }
}
