package ru.pathcreator.pyc.rpc.platform;

public enum OperatingSystem {

    WINDOWS,
    LINUX,
    MACOS,
    OTHER;

    public static OperatingSystem current() {
        final String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return WINDOWS;
        }
        if (osName.contains("linux")) {
            return LINUX;
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return MACOS;
        }
        return OTHER;
    }
}