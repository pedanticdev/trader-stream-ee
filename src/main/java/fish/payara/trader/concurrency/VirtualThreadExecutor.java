package fish.payara.trader.concurrency;

/**
 * Qualifier for Virtual Thread ManagedExecutorService NOTE: Virtual threads
 * via @ManagedExecutorDefinition requires Jakarta EE 11 (Concurrency 3.1+) Commented out for
 * Jakarta EE 10 compatibility - falls back to default executor
 */
// @Qualifier
// @Retention(RUNTIME)
// @Target({METHOD, FIELD, PARAMETER, TYPE})
public @interface VirtualThreadExecutor {}
