package fish.payara.trader.jfr.templates;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Generic latency-tracking JFR event.
 *
 * <p>Use this template for any operation whose tail latency matters. The {@code begin()/commit()} pattern auto-captures the operation's duration; you only
 * pass the contextual fields.
 *
 * <p>Renaming for your domain:
 * <ol>
 *   <li>Change the class name and {@code @Name} value (e.g. {@code "auth.token.validation"}).</li>
 *   <li>Replace the {@code operation} and {@code target} fields with domain-specific identifiers.</li>
 *   <li>Decide whether you want stack traces. For per-request events, no. For events that fire only on failure, yes.</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * LatencyEvent event = new LatencyEvent();
 * if (event.isEnabled()) {
 *     event.begin();
 *     try {
 *         doTheWork();
 *         event.success = true;
 *     } finally {
 *         event.operation = "validate-token";
 *         event.target = userId;
 *         event.commit();
 *     }
 * } else {
 *     doTheWork();
 * }
 * }</pre>
 */
@Name("app.latency")
@Label("Application Latency")
@Category({"Application", "Latency"})
@Description("Generic latency-tracking event template. Auto-captures duration via begin/commit.")
@StackTrace(false)
public class LatencyEvent extends Event {

    @Label("Operation")
    @Description("Logical operation name, stable across releases. Used as the primary group-by in JMC.")
    public String operation;

    @Label("Target")
    @Description("Domain identifier for what was operated on (user id, order id, file path).")
    public String target;

    @Label("Success")
    public boolean success;

    @Label("Error class")
    @Description("Set only when success is false; the simple name of the exception type, not its message.")
    public String errorClass;
}
