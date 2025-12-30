package fish.payara.trader.concurrency;

import jakarta.enterprise.concurrent.ManagedExecutorDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Configuration for Jakarta Concurrency resources. Defines a ManagedExecutorService that uses Virtual Threads (Project Loom).
 */
@ApplicationScoped
@ManagedExecutorDefinition(name = "java:module/concurrent/VirtualThreadExecutor", virtual = true, qualifiers = {VirtualThreadExecutor.class})
public class ConcurrencyConfig {
}
