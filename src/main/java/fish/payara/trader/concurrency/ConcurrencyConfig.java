package fish.payara.trader.concurrency;

import jakarta.enterprise.concurrent.ManagedExecutorDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Configuration for Jakarta Concurrency resources.
 * Defines a ManagedExecutorService that uses Virtual Threads (Project Loom).
 *
 * NOTE: The 'virtual' and 'qualifiers' attributes were added in Jakarta Concurrency 3.1 (Jakarta EE 11).
 * Payara 6 uses Jakarta EE 10, so these features are not yet available.
 * Commented out for compatibility - application falls back to default platform thread executor.
 */
//@ApplicationScoped
//@ManagedExecutorDefinition(
//    name = "java:module/concurrent/VirtualThreadExecutor",
//    virtual = true,
//    qualifiers = {VirtualThreadExecutor.class}
//)
public class ConcurrencyConfig {
}
