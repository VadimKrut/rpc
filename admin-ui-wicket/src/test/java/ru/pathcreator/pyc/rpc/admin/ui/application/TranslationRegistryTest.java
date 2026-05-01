package ru.pathcreator.pyc.rpc.admin.ui.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranslationRegistryTest {

    @Test
    void shouldLoadConfiguredLanguages() {
        final TranslationRegistry registry = new TranslationRegistry();

        assertEquals("ru", registry.defaultLanguageCode());
        assertTrue(registry.supports("ru"));
        assertTrue(registry.supports("en"));
        assertTrue(registry.supports("de"));
        assertFalse(registry.supports("fr"));
        assertEquals(3, registry.languages().size());
    }

    @Test
    void shouldFallbackToDefaultLanguageWhenKeyOrCodeIsMissing() {
        final TranslationRegistry registry = new TranslationRegistry();

        assertEquals("Операционный обзор", registry.text("ru", "overview.pageTitle"));
        assertEquals("Operations Overview", registry.text("en", "overview.pageTitle"));
        assertEquals("Betriebsübersicht", registry.text("de", "overview.pageTitle"));
        assertEquals("Операционный обзор", registry.text("fr", "overview.pageTitle"));
        assertEquals("missing.key", registry.text("ru", "missing.key"));
    }
}