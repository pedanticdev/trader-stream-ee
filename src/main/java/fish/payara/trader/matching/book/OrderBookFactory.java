package fish.payara.trader.matching.book;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class OrderBookFactory {

    private final ConcurrentHashMap<String, OrderBook> books = new ConcurrentHashMap<>();

    public OrderBook getOrCreate(String symbol) {
        return books.computeIfAbsent(symbol, k -> new PriceTimeOrderBook(k));
    }

    public OrderBook get(String symbol) {
        return books.get(symbol);
    }

    public void clearAll() {
        books.values().forEach(OrderBook::clear);
        books.clear();
    }
}
