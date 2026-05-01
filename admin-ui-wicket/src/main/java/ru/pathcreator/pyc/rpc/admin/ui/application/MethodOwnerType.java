package ru.pathcreator.pyc.rpc.admin.ui.application;

public enum MethodOwnerType {
    SERVICE("owner.service"),
    SERVER("owner.server"),
    CLIENT("owner.client");

    private final String messageKey;

    MethodOwnerType(final String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return this.messageKey;
    }
}