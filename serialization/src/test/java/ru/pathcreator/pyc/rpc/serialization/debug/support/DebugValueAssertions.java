package ru.pathcreator.pyc.rpc.serialization.debug.support;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.*;

public final class DebugValueAssertions {

    private DebugValueAssertions() {
    }

    public static void assertDeepEquals(
            final Object expected,
            final Object actual,
            final String path
    ) {
        if (expected == actual) {
            return;
        }
        if (expected == null || actual == null) {
            fail(path, expected, actual);
        }
        if (expected instanceof Optional<?> expectedOptional) {
            if (!(actual instanceof Optional<?>)) {
                fail(path, expected, actual);
            }
            final Optional<?> actualOptional = (Optional<?>) actual;
            if (expectedOptional.isEmpty() != actualOptional.isEmpty()) {
                fail(path, expected, actual);
            }
            if (expectedOptional.isPresent()) {
                assertDeepEquals(expectedOptional.get(), actualOptional.get(), path + ".value");
            }
            return;
        }
        final Class<?> expectedClass = expected.getClass();
        if (expectedClass.isArray()) {
            assertArrayEquals(expected, actual, path);
            return;
        }
        if (expected instanceof Collection<?> expectedCollection) {
            if (!(actual instanceof Collection<?>)) {
                fail(path, expected, actual);
            }
            final Collection<?> actualCollection = (Collection<?>) actual;
            assertCollectionEquals(expectedCollection, actualCollection, path);
            return;
        }
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?>)) {
                fail(path, expected, actual);
            }
            final Map<?, ?> actualMap = (Map<?, ?>) actual;
            assertMapEquals(expectedMap, actualMap, path);
            return;
        }
        if (expectedClass.isRecord()) {
            assertRecordEquals(expected, actual, path);
            return;
        }
        if (!Objects.equals(expected, actual)) {
            fail(path, expected, actual);
        }
    }

    private static void assertArrayEquals(
            final Object expected,
            final Object actual,
            final String path
    ) {
        if (actual == null || !actual.getClass().isArray()) {
            fail(path, expected, actual);
        }
        final int expectedLength = Array.getLength(expected);
        final int actualLength = Array.getLength(actual);
        if (expectedLength != actualLength) {
            fail(path + ".length", expectedLength, actualLength);
        }
        for (int i = 0; i < expectedLength; i++) {
            assertDeepEquals(Array.get(expected, i), Array.get(actual, i), path + "[" + i + "]");
        }
    }

    private static void assertCollectionEquals(
            final Collection<?> expected,
            final Collection<?> actual,
            final String path
    ) {
        if (expected.size() != actual.size()) {
            fail(path + ".size", expected.size(), actual.size());
        }
        final Iterator<?> expectedIterator = expected.iterator();
        final Iterator<?> actualIterator = actual.iterator();
        int index = 0;
        while (expectedIterator.hasNext() && actualIterator.hasNext()) {
            assertDeepEquals(expectedIterator.next(), actualIterator.next(), path + "[" + index + "]");
            index++;
        }
    }

    private static void assertMapEquals(
            final Map<?, ?> expected,
            final Map<?, ?> actual,
            final String path
    ) {
        if (expected.size() != actual.size()) {
            fail(path + ".size", expected.size(), actual.size());
        }
        final Iterator<? extends Map.Entry<?, ?>> expectedIterator = expected.entrySet().iterator();
        final Iterator<? extends Map.Entry<?, ?>> actualIterator = actual.entrySet().iterator();
        int index = 0;
        while (expectedIterator.hasNext() && actualIterator.hasNext()) {
            final Map.Entry<?, ?> expectedEntry = expectedIterator.next();
            final Map.Entry<?, ?> actualEntry = actualIterator.next();
            assertDeepEquals(expectedEntry.getKey(), actualEntry.getKey(), path + "[" + index + "].key");
            assertDeepEquals(expectedEntry.getValue(), actualEntry.getValue(), path + "[" + index + "].value");
            index++;
        }
    }

    private static void assertRecordEquals(
            final Object expected,
            final Object actual,
            final String path
    ) {
        if (actual == null || !actual.getClass().equals(expected.getClass())) {
            fail(path + ".type", expected.getClass().getName(), actual == null ? null : actual.getClass().getName());
        }
        for (final RecordComponent component : expected.getClass().getRecordComponents()) {
            try {
                final Object expectedValue = component.getAccessor().invoke(expected);
                final Object actualValue = component.getAccessor().invoke(actual);
                assertDeepEquals(expectedValue, actualValue, path + "." + component.getName());
            } catch (final ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to compare record component " + component.getName(), e);
            }
        }
    }

    private static void fail(
            final String path,
            final Object expected,
            final Object actual
    ) {
        throw new IllegalStateException("Mismatch at " + path + ": expected=" + render(expected) + ", actual=" + render(actual));
    }

    private static String render(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            final int length = Array.getLength(value);
            final List<String> rendered = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                rendered.add(render(Array.get(value, i)));
            }
            return rendered.toString();
        }
        return String.valueOf(value);
    }
}