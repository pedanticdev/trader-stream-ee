package fish.payara.trader.pressure.workload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class SerializationWorkload extends AbstractCpuWorkload {

    @Override
    public String name() {
        return "SERIALIZATION";
    }

    @Override
    public void execute(int iterations) {
        int numThreads = config.threadsPerWorkload();
        executeMultiThreaded(iterations, numThreads, this::executeSingleIteration);
    }

    private void executeSingleIteration(ThreadLocalRandom rng) {
        JsonObject original = buildJsonDocument(rng);

        String json = serializeToString(original);
        bytesAllocated.addAndGet(json.length() * 2L);

        JsonObject parsed = parseFromString(json);

        verify(original, parsed);
        operationsCompleted.incrementAndGet();
    }

    private JsonObject buildJsonDocument(ThreadLocalRandom rng) {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        builder.add("orderId", rng.nextLong(1_000_000_000L));
        builder.add("symbol", "SYM-" + rng.nextInt(1000));
        builder.add("side", rng.nextBoolean() ? "BUY" : "SELL");
        builder.add("quantity", rng.nextInt(1, 10_000));
        builder.add("price", rng.nextDouble(1.0, 1000.0));
        builder.add("timestamp", System.nanoTime());

        JsonArrayBuilder tags = Json.createArrayBuilder();
        for (int i = 0; i < 5; i++) {
            tags.add("tag-" + rng.nextInt(100));
        }
        builder.add("tags", tags);

        JsonObjectBuilder metadata = Json.createObjectBuilder();
        metadata.add("exchange", "EXCH-" + rng.nextInt(10));
        metadata.add("currency", "USD");
        metadata.add("settlementDate", "2026-03-18");
        metadata.add("commission", rng.nextDouble(0.01, 5.0));
        builder.add("metadata", metadata);

        builder.add("execType", "FILL");
        builder.add("leavesQty", 0);
        builder.add("cumQty", rng.nextInt(1, 10_000));
        builder.add("avgPrice", rng.nextDouble(1.0, 1000.0));
        builder.add("tradeId", rng.nextLong(1_000_000L));

        JsonArrayBuilder allocations = Json.createArrayBuilder();
        for (int i = 0; i < 3; i++) {
            allocations.add(Json.createObjectBuilder()
                            .add("account", "ACC-" + rng.nextInt(100))
                            .add("allocQty", rng.nextInt(100, 5000))
                            .add("allocPrice", rng.nextDouble(1.0, 1000.0)));
        }
        builder.add("allocations", allocations);

        builder.add("status", "ACCEPTED");
        builder.add("text", "Executed via matching engine v" + rng.nextInt(1, 10));
        builder.add("transactTime", System.nanoTime());
        builder.add("senderCompId", "SENDER-" + rng.nextInt(50));
        builder.add("targetCompId", "TARGET-" + rng.nextInt(50));
        builder.add("clOrdId", rng.nextLong(100_000L, 999_999L));
        builder.add("origClOrdId", rng.nextLong(100_000L, 999_999L));
        builder.add("minQty", rng.nextInt(1, 100));
        builder.add("maxFloor", rng.nextInt(100, 10_000));

        return builder.build();
    }

    String serializeToString(JsonObject json) {
        StringWriter writer = new StringWriter();
        try (JsonWriter jsonWriter = Json.createWriter(writer)) {
            jsonWriter.writeObject(json);
        }
        return writer.toString();
    }

    JsonObject parseFromString(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    void verify(JsonObject original, JsonObject parsed) {
        if (original.size() != parsed.size()) {
            throw new AssertionError("Parsed JSON field count mismatch");
        }
        if (!original.getString("symbol").equals(parsed.getString("symbol"))) {
            throw new AssertionError("Symbol mismatch after round-trip");
        }
    }
}
