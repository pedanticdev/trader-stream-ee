package fish.payara.trader.util;

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
}
