package fish.payara.trader.util;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Utility methods for instance-level operations. Consolidates commonly-used code across the application.
 */
public final class InstanceUtils {

    private static final String PAYARA_INSTANCE_NAME_ENV = "PAYARA_INSTANCE_NAME";
    private static final String DEFAULT_INSTANCE_NAME = "standalone";

    private InstanceUtils() {
    }

    /**
     * Returns the Payara instance name from environment variable, or "standalone" if not set.
     */
    public static String getInstanceName() {
        String name = System.getenv(PAYARA_INSTANCE_NAME_ENV);
        return name != null ? name : DEFAULT_INSTANCE_NAME;
    }

    /**
     * Calculates percentile value from a sorted list of values.
     *
     * @param sortedValues
     *            list of values sorted in ascending order
     * @param percentile
     *            percentile to calculate (0.0 to 1.0)
     * @return the value at the requested percentile
     */
    public static long percentile(List<Long> sortedValues, double percentile) {
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * JVM metadata containing vendor, name, and garbage collector information.
     */
    public static final class JvmMetadata {
        private final String vendor;
        private final String name;
        private final String gcCollectors;
        private final boolean isAzulC4;

        public JvmMetadata(String vendor, String name, String gcCollectors, boolean isAzulC4) {
            this.vendor = vendor;
            this.name = name;
            this.gcCollectors = gcCollectors;
            this.isAzulC4 = isAzulC4;
        }

        public String vendor() {
            return vendor;
        }

        public String name() {
            return name;
        }

        public String gcCollectors() {
            return gcCollectors;
        }

        public boolean isAzulC4() {
            return isAzulC4;
        }
    }

    /**
     * Extracts JVM metadata from system properties and GC beans.
     *
     * @return JVM metadata including vendor, name, GC collectors, and C4 detection
     */
    public static JvmMetadata getJvmMetadata() {
        String vendor = System.getProperty("java.vm.vendor");
        String name = System.getProperty("java.vm.name");
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        String gcCollectors = gcBeans.stream().map(GarbageCollectorMXBean::getName).reduce((a, b) -> a + ", " + b).orElse("");

        boolean isAzulC4 = gcCollectors.toLowerCase().contains("c4") || name.toLowerCase().contains("zing");

        return new JvmMetadata(vendor, name, gcCollectors, isAzulC4);
    }
}
