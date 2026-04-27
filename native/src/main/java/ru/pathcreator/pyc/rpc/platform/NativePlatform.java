package ru.pathcreator.pyc.rpc.platform;

public final class NativePlatform {

    private NativePlatform() {
    }

    public static OperatingSystem currentOperatingSystem() {
        return OperatingSystem.current();
    }

    public static boolean isLinux() {
        return currentOperatingSystem() == OperatingSystem.LINUX;
    }

    public static boolean isWindows() {
        return currentOperatingSystem() == OperatingSystem.WINDOWS;
    }
}