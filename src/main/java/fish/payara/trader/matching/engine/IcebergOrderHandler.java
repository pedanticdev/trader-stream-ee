package fish.payara.trader.matching.engine;

import fish.payara.trader.matching.model.Order;
import fish.payara.trader.matching.model.OrderType;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class IcebergOrderHandler {

    private static final Logger LOGGER = Logger.getLogger(IcebergOrderHandler.class.getName());

    private final ConcurrentHashMap<Long, Long> hiddenQuantities = new ConcurrentHashMap<>();

    public void track(Order order) {
        if (order.type() == OrderType.ICEBERG && order.displayQuantity() > 0) {
            long totalQty = order.quantity();
            long displayed = Math.min(order.displayQuantity(), totalQty);
            long hidden = totalQty - displayed;
            hiddenQuantities.put(order.orderId(), hidden);
            LOGGER.fine(() -> "Tracking iceberg order " + order.orderId() + ": displayed=" + displayed + " hidden=" + hidden);
        }
    }

    public long revealNextSlice(Order order) {
        if (order.type() != OrderType.ICEBERG) {
            return order.leavesQty();
        }

        long hidden = hiddenQuantities.getOrDefault(order.orderId(), 0L);
        if (hidden <= 0) {
            return order.leavesQty();
        }

        long slice = Math.min(order.displayQuantity(), hidden);
        hiddenQuantities.put(order.orderId(), hidden - slice);
        LOGGER.fine(() -> "Revealing iceberg slice " + slice + " for order " + order.orderId());
        return slice;
    }

    public long remainingHidden(long orderId) {
        return hiddenQuantities.getOrDefault(orderId, 0L);
    }

    public void removeTracking(long orderId) {
        hiddenQuantities.remove(orderId);
    }

    public void clear() {
        hiddenQuantities.clear();
    }
}
