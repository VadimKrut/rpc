package ru.pathcreator.pyc.rpc.serialization.support.reflect;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public final class ReflectionSupport {

    private ReflectionSupport() {
    }

    public static <T> Constructor<T> noArgsConstructor(final Class<T> type) {
        try {
            final Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("No accessible no-args constructor for " + type.getName(), e);
        }
    }

    public static <T> T instantiate(final Constructor<T> constructor) {
        try {
            return constructor.newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate " + constructor.getDeclaringClass().getName(), e);
        }
    }

    public static VarHandle varHandle(final Class<?> owner, final String fieldName, final Class<?> fieldType) {
        try {
            final Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
            return lookup.findVarHandle(owner, fieldName, fieldType);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot resolve field " + owner.getName() + "." + fieldName, e);
        }
    }
}