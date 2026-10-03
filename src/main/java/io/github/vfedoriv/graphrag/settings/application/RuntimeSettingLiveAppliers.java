package io.github.vfedoriv.graphrag.settings.application;

import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;

public final class RuntimeSettingLiveAppliers {

    private final LoggingSystem loggingSystem;

    public RuntimeSettingLiveAppliers(LoggingSystem loggingSystem) {
        this.loggingSystem = loggingSystem;
    }

    public void rootLoggingLevel(Object value) {
        loggingSystem.setLogLevel(null, LogLevel.valueOf(String.valueOf(value)));
    }
}
