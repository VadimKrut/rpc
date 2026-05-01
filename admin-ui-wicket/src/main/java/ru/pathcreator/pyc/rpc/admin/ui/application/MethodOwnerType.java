package ru.pathcreator.pyc.rpc.admin.ui.application;

public enum MethodOwnerType {
    SERVICE("Сервисный метод"),
    SERVER("Серверный метод"),
    CLIENT("Клиентский метод");

    private final String displayName;

    MethodOwnerType(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}