package ru.pathcreator.pyc.rpc.platform.ffi;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

public final class NativeForeign {

    private static final Arena SHARED_ARENA = Arena.ofShared();
    private static final Linker LINKER = Linker.nativeLinker();

    private NativeForeign() {
    }

    public static Linker linker() {
        return LINKER;
    }

    public static SymbolLookup libraryLookup(final String... libraryCandidates) {
        IllegalStateException last = null;
        for (final String library : libraryCandidates) {
            try {
                return SymbolLookup.libraryLookup(library, SHARED_ARENA);
            } catch (final IllegalArgumentException | IllegalStateException e) {
                last = new IllegalStateException("Cannot load native library candidate: " + library, e);
            }
        }
        throw last == null
                ? new IllegalStateException("No native library candidates provided")
                : new IllegalStateException("Cannot load any native library candidate", last);
    }

    public static Optional<MemorySegment> findAny(
            final SymbolLookup lookup,
            final String... symbolCandidates
    ) {
        for (final String symbol : symbolCandidates) {
            final Optional<MemorySegment> found = lookup.find(symbol);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public static MemorySegment requireAny(
            final SymbolLookup lookup,
            final String... symbolCandidates
    ) {
        return findAny(lookup, symbolCandidates).orElseThrow(
                () -> new IllegalStateException("Missing native symbol candidates: " + String.join(", ", symbolCandidates))
        );
    }

    public static MethodHandle downcall(
            final SymbolLookup lookup,
            final FunctionDescriptor descriptor,
            final String... symbolCandidates
    ) {
        return LINKER.downcallHandle(requireAny(lookup, symbolCandidates), descriptor);
    }

    public static Optional<MethodHandle> downcallOptional(
            final SymbolLookup lookup,
            final FunctionDescriptor descriptor,
            final String... symbolCandidates
    ) {
        return findAny(lookup, symbolCandidates).map(symbol -> LINKER.downcallHandle(symbol, descriptor));
    }
}