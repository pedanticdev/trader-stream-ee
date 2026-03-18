package fish.payara.trader.dto;

import fish.payara.trader.risk.model.ExposureSummary;
import fish.payara.trader.risk.model.RiskSnapshot;
import fish.payara.trader.risk.model.StressResult;
import fish.payara.trader.risk.model.VarResult;
import java.util.List;

/**
 * Aggregated risk response DTO combining per-symbol risk, exposure, VaR, and stress test results.
 */
public record RiskResponse(List<RiskSnapshot> positions, ExposureSummary exposure, VarResult historicalVar, VarResult parametricVar,
                List<StressResult> stressResults) {
}
