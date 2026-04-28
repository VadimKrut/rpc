package ru.pathcreator.pyc.rpc.serialization.debug;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import ru.pathcreator.pyc.rpc.serialization.debug.support.DebugRoundTripCase;
import ru.pathcreator.pyc.rpc.serialization.debug.support.DebugRoundTripRuntime;
import ru.pathcreator.pyc.rpc.serialization.debug.support.DebugSampleData;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class SbeSupportedTypesTest {

    private static DebugRoundTripRuntime runtime;

    @BeforeAll
    static void setUp() throws Exception {
        runtime = new DebugRoundTripRuntime(Path.of("target", "supported-sbe"));
        runtime.recreateOutputDirectories();
    }

    @TestFactory
    Stream<DynamicTest> supportedTypesRoundTrip() {
        final List<DebugRoundTripCase<?>> roundTripCases = DebugSampleData.supportedCases();
        return roundTripCases.stream().map(roundTripCase ->
                DynamicTest.dynamicTest(roundTripCase.type().getSimpleName(), () -> {
                    final DebugRoundTripRuntime.RoundTripResult result = runtime.verify(roundTripCase);
                    assertFalse(
                            result.hasMeasureMismatch(),
                            () -> result.type().getSimpleName()
                                  + " measure mismatch: measured=" + result.measuredSize()
                                  + "B, written=" + result.writtenSize() + "B"
                    );
                })
        );
    }
}