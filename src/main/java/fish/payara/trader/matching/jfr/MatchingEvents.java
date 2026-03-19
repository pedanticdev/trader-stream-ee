package fish.payara.trader.matching.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR events for Order Matching Engine monitoring.
 */
@Category("Matching")
@Label("Order Matching")
public class MatchingEvents {

    @Name("order.submitted")
    @Label("Order Submitted")
    @StackTrace(false)
    public static class OrderSubmitted extends Event {
        public long orderId;
        public String symbol;
        public String side;
        public String type;
        public long quantity;
        public long price;
    }

    @Name("order.matched")
    @Label("Order Matched")
    @StackTrace(false)
    public static class OrderMatched extends Event {
        public long orderId;
        public String symbol;
        public String side;
        public long filledQuantity;
        public long remainingQuantity;
        public String status;
    }

    @Name("stop.triggered")
    @Label("Stop Order Triggered")
    @StackTrace(false)
    public static class StopTriggered extends Event {
        public long orderId;
        public String symbol;
        public String side;
        public long stopPrice;
    }

    @Name("order.canceled")
    @Label("Order Canceled")
    @StackTrace(false)
    public static class OrderCanceled extends Event {
        public long orderId;
        public String reason;
    }
}
