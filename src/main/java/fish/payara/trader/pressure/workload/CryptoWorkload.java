package fish.payara.trader.pressure.workload;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.MessageDigest;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class CryptoWorkload extends AbstractCpuWorkload {

    private static final int PAYLOAD_SIZE = 4096;
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Override
    public String name() {
        return "CRYPTO";
    }

    @Override
    public void execute(int iterations) {
        int numThreads = config.threadsPerWorkload();
        executeMultiThreaded(iterations, numThreads, this::executeSingleIteration);
    }

    private void executeSingleIteration(ThreadLocalRandom rng) {
        byte[] key256 = new byte[32];
        byte[] key128 = new byte[16];
        rng.nextBytes(key256);
        rng.nextBytes(key128);

        byte[] payload = new byte[PAYLOAD_SIZE];
        rng.nextBytes(payload);

        sha256Digest(payload);
        bytesAllocated.addAndGet(32L);
        operationsCompleted.incrementAndGet();

        hmacSha256(key256, payload);
        bytesAllocated.addAndGet(32L);
        operationsCompleted.incrementAndGet();

        byte[] iv = new byte[GCM_IV_LENGTH];
        rng.nextBytes(iv);
        byte[] ciphertext = aesGcmEncrypt(key128, iv, payload);
        bytesAllocated.addAndGet(ciphertext.length + 16L);
        operationsCompleted.incrementAndGet();

        byte[] recovered = aesGcmDecrypt(key128, iv, ciphertext);
        bytesAllocated.addAndGet(recovered.length);

        if (payload.length != recovered.length) {
            throw new AssertionError("AES-GCM round-trip length mismatch");
        }
        for (int i = 0; i < payload.length; i++) {
            if (payload[i] != recovered[i]) {
                throw new AssertionError("AES-GCM round-trip data mismatch at index " + i);
            }
        }
        operationsCompleted.incrementAndGet();
    }

    byte[] sha256Digest(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 digest failed", e);
        }
    }

    byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    byte[] aesGcmEncrypt(byte[] key, byte[] iv, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), spec);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM encrypt failed", e);
        }
    }

    byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM decrypt failed", e);
        }
    }
}
