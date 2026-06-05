package org.magicghostvu.actorvt;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingTest {

    @Test
    void log4j2IsWiredToSlf4j() {
        Logger logger = LoggerFactory.getLogger("test-log4j2");
        // With log4j-slf4j-impl on the classpath the backing instance is a Log4jLogger,
        // not the fallback NOPLogger or SimpleLogger.
        String implClass = logger.getClass().getName();
        assertTrue(implClass.contains("log4j") || implClass.contains("Log4j"),
                "Expected a log4j2-backed SLF4J logger, got: " + implClass);
    }

    @Test
    void loggingAtAllLevelsDoesNotThrow() {
        Logger logger = LoggerFactory.getLogger("test-log4j2-levels");
        // Verifies log4j2.xml is loaded and no misconfiguration throws at runtime.
        logger.debug("debug message from test");
        logger.info("info message from test");
        logger.warn("warn message from test");
        logger.error("error message from test");
    }
}
