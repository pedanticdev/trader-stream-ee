package fish.payara.trader.pressure.workload;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@ApplicationScoped
public class CompressionWorkload extends AbstractCpuWorkload {

    @Override
    public String name() {
        return "COMPRESSION";
    }

    @Override
    public void execute(int iterations) {
        int numThreads = config.threadsPerWorkload();
        executeMultiThreaded(iterations, numThreads, this::executeSingleIteration);
    }

    private void executeSingleIteration(ThreadLocalRandom rng) {
        int size = config.payloadSizeBytes();
        byte[] payload = generatePayload(size, rng);
        byte[] compressed = compress(payload);
        byte[] recovered = decompress(compressed);

        verify(payload, recovered);

        bytesAllocated.addAndGet((long) payload.length + compressed.length + recovered.length);
        operationsCompleted.incrementAndGet();
    }

    byte[] generatePayload(int size, ThreadLocalRandom rng) {
        byte[] data = new byte[size];
        rng.nextBytes(data);
        return data;
    }

    byte[] compress(byte[] data) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length)) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos) {
                {
                    def.setLevel(Deflater.BEST_COMPRESSION);
                }
            }) {
                gzip.write(data);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Compression failed", e);
        }
    }

    byte[] decompress(byte[] compressed) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
                        GZIPInputStream gzip = new GZIPInputStream(bis);
                        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Decompression failed", e);
        }
    }

    void verify(byte[] original, byte[] recovered) {
        if (original.length != recovered.length) {
            throw new AssertionError("Decompressed length mismatch: expected " + original.length + " got " + recovered.length);
        }
        for (int i = 0; i < original.length; i++) {
            if (original[i] != recovered[i]) {
                throw new AssertionError("Decompressed data mismatch at index " + i);
            }
        }
    }
}
