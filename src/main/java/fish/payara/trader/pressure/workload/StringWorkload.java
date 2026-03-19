package fish.payara.trader.pressure.workload;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class StringWorkload extends AbstractCpuWorkload {

    private final Pattern pricePattern = Pattern.compile("\\$([0-9]+\\.[0-9]{2})");
    private final Pattern symbolPattern = Pattern.compile("\\b[A-Z]{3,5}\\b");
    private final Pattern datePattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private final Pattern quantityPattern = Pattern.compile("(\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?)");

    @Override
    public String name() {
        return "STRING";
    }

    @Override
    public void execute(int iterations) {
        int numThreads = config.threadsPerWorkload();
        executeMultiThreaded(iterations, numThreads, this::executeSingleIteration);
    }

    private void executeSingleIteration(ThreadLocalRandom rng) {
        int count = config.payloadSizeBytes() / 256;
        if (count < 1) {
            count = 1;
        }

        int op = rng.nextInt(4);
        switch (op) {
        case 0 -> executeRegexWork(count, rng);
        case 1 -> executeSubstringWork(count, rng);
        case 2 -> executeConcatWork(count, rng);
        case 3 -> executeInternWork(count, rng);
        default -> throw new AssertionError("Unexpected op: " + op);
        }
    }

    void executeRegexWork(int count, ThreadLocalRandom rng) {
        for (int i = 0; i < count; i++) {
            String input = generateRandomTradeString(rng);
            bytesAllocated.addAndGet(input.length() * 2L);

            findMatches(pricePattern, input);
            findMatches(symbolPattern, input);
            findMatches(datePattern, input);
            findMatches(quantityPattern, input);

            operationsCompleted.incrementAndGet();
        }
    }

    private void findMatches(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String match = matcher.group();
            bytesAllocated.addAndGet(match.length() * 2L);
        }
    }

    void executeSubstringWork(int count, ThreadLocalRandom rng) {
        for (int i = 0; i < count; i++) {
            String base = generateRandomTradeString(rng);
            bytesAllocated.addAndGet(base.length() * 2L);

            for (int j = 0; j < 10; j++) {
                int start = rng.nextInt(0, base.length() / 2);
                int end = rng.nextInt(start + 1, base.length());
                String sub = base.substring(start, end);
                bytesAllocated.addAndGet(sub.length() * 2L);
            }

            operationsCompleted.incrementAndGet();
        }
    }

    void executeConcatWork(int count, ThreadLocalRandom rng) {
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 50; j++) {
                sb.append(generateRandomTradeString(rng));
                if (j < 49) {
                    sb.append(" | ");
                }
            }
            String result = sb.toString();
            bytesAllocated.addAndGet(result.length() * 2L + 50L * 24L);
            operationsCompleted.incrementAndGet();
        }
    }

    void executeInternWork(int count, ThreadLocalRandom rng) {
        for (int i = 0; i < count; i++) {
            String symbol = "SYM-" + rng.nextInt(1000);
            bytesAllocated.addAndGet(symbol.length() * 2L);
            symbol.intern();

            String currency = "CUR-" + rng.nextInt(100);
            bytesAllocated.addAndGet(currency.length() * 2L);
            currency.intern();

            String exchange = "EXCH-" + rng.nextInt(50);
            bytesAllocated.addAndGet(exchange.length() * 2L);
            exchange.intern();

            operationsCompleted.incrementAndGet();
        }
    }

    String generateRandomTradeString(ThreadLocalRandom rng) {
        int size = 1024 + rng.nextInt(9 * 1024);
        StringBuilder sb = new StringBuilder(size);
        sb.append("SYM-").append(rng.nextInt(1000)).append(" ");
        sb.append("$").append(String.format("%.2f", rng.nextDouble(1.0, 1000.0))).append(" ");
        sb.append(String.format("%,d", rng.nextInt(100, 10_000))).append(" ");
        sb.append("2026-").append(String.format("%02d-%02d", rng.nextInt(1, 13), rng.nextInt(1, 29))).append(" ");
        sb.append(rng.nextBoolean() ? "BUY" : "SELL").append(" ");

        int remaining = size - sb.length();
        for (int i = 0; i < remaining; i++) {
            sb.append((char) ('a' + rng.nextInt(26)));
        }

        return sb.toString();
    }
}
