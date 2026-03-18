package fish.payara.trader.matching.history;

public record ExecutionHistoryQuery(String symbol, Long fromTimestamp, Long toTimestamp, Integer limit) {

    public ExecutionHistoryQuery {
        if (limit == null || limit <= 0) {
            limit = 100;
        }
    }

    public static ExecutionHistoryQuery all() {
        return new ExecutionHistoryQuery(null, null, null, 100);
    }

    public static ExecutionHistoryQuery forSymbol(String symbol) {
        return new ExecutionHistoryQuery(symbol, null, null, 100);
    }

    public static ExecutionHistoryQuery forSymbol(String symbol, int limit) {
        return new ExecutionHistoryQuery(symbol, null, null, limit);
    }
}
