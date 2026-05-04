package com.founderlink.auth.entity;

/**
 * Identifies how a user authenticated / registered.
 * LOCAL  → standard email + password registration
 * GOOGLE → Google OAuth sign-in (password field is a random UUID, never used)
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
