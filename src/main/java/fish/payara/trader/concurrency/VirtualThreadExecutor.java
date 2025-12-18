package fish.payara.trader.concurrency;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * Qualifier for Virtual Thread ManagedExecutorService
 * NOTE: Virtual threads via @ManagedExecutorDefinition requires Jakarta EE 11 (Concurrency 3.1+)
 * Commented out for Jakarta EE 10 compatibility - falls back to default executor
 */
//@Qualifier
//@Retention(RUNTIME)
//@Target({METHOD, FIELD, PARAMETER, TYPE})
public @interface VirtualThreadExecutor {
}
