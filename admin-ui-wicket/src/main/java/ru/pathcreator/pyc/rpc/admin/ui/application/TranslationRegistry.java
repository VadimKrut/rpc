package ru.pathcreator.pyc.rpc.admin.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

public final class TranslationRegistry {

    private static final String INDEX_RESOURCE =
            "ru/pathcreator/pyc/rpc/admin/ui/i18n/languages.json";

    private final String defaultLanguageCode;
    private final List<LanguageOption> languages;
    private final Map<String, Map<String, String>> bundles;

    public TranslationRegistry() {
        this(Thread.currentThread().getContextClassLoader());
    }

    TranslationRegistry(final ClassLoader classLoader) {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = this.readJson(classLoader, mapper, INDEX_RESOURCE);
        final JsonNode languagesNode = root.path("languages");

        this.defaultLanguageCode = root.path("defaultLanguage").isMissingNode()
                ? "en"
                : root.path("defaultLanguage").asText();
        final List<LanguageOption> options = new ArrayList<>();
        for (final JsonNode languageNode : languagesNode) {
            options.add(new LanguageOption(languageNode.path("code").asText(), languageNode.path("label").asText()));
        }
        this.languages = List.copyOf(options);

        final Map<String, Map<String, String>> resolvedBundles = new LinkedHashMap<>();
        for (final JsonNode languageNode : languagesNode) {
            resolvedBundles.put(
                    languageNode.path("code").asText(),
                    this.readBundle(classLoader, mapper, languageNode.path("resource").asText())
            );
        }
        this.bundles = Collections.unmodifiableMap(resolvedBundles);
    }

    public String defaultLanguageCode() {
        return this.defaultLanguageCode;
    }

    public List<LanguageOption> languages() {
        return this.languages;
    }

    public boolean supports(final String code) {
        return code != null && this.bundles.containsKey(code);
    }

    public Locale locale(final String code) {
        return Locale.forLanguageTag(this.supports(code) ? code : this.defaultLanguageCode);
    }

    public String text(final String code, final String key) {
        final Map<String, String> preferred = this.bundles.get(code);
        if (preferred != null && preferred.containsKey(key)) {
            return preferred.get(key);
        }
        final Map<String, String> fallback = this.bundles.get(this.defaultLanguageCode);
        if (fallback != null && fallback.containsKey(key)) {
            return fallback.get(key);
        }
        return key;
    }

    private JsonNode readJson(final ClassLoader classLoader,
                              final ObjectMapper mapper,
                              final String resource) {
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing translation resource: " + resource);
            }
            return mapper.readTree(input);
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to read translation resource " + resource, exception);
        }
    }

    private Map<String, String> readBundle(final ClassLoader classLoader,
                                           final ObjectMapper mapper,
                                           final String resource) {
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing translation bundle: " + resource);
            }
            return Collections.unmodifiableMap(
                    mapper.readValue(input, new TypeReference<LinkedHashMap<String, String>>() {
                    })
            );
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to read translation bundle " + resource, exception);
        }
    }
}