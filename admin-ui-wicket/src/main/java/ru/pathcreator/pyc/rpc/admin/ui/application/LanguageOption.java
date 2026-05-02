package ru.pathcreator.pyc.rpc.admin.ui.application;

import java.io.Serializable;

public record LanguageOption(
        String code,
        String label
) implements Serializable {
}