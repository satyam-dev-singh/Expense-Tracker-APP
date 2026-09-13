import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline JVM mirror of the Phase 4 budgeting rules (run: java verification/BudgetHarness.java).
 * Mirrors BudgetUseCases.kt 1:1: budget status thresholds (80% warn / 100% exceeded),
 * daily allowance (days remaining including today, integer floor, zero-clamped
 * overspend, current-month gating, leap months), and rollover prefill decisions.
 * Any failure exits 1. On a dev machine run ./gradlew testDebugUnitTest instead.
 */
public class BudgetHarness {

    static int passed = 0;
    static List<String> failures = new ArrayList<>();

    // --- mirror: evaluateBudgetStatus ---
    static final double WARNING_THRESHOLD = 80.0;
    enum Status { ON_TRACK, WARNING, EXCEEDED }
    static Status evaluateBudgetStatus(double usagePercent) {
        if (usagePercent >= 100.0) return Status.EXCEEDED;
        if (usagePercent >= WARNING_THRESHOLD) return Status.WARNING;
        return Status.ON_TRACK;
    }

    // --- mirror: dailyAllowance (null budget / non-current month -> null; overspend -> 0) ---
    static Long dailyAllowance(Long budgetMinor, long expensesMinor, LocalDate today, YearMonth month) {
        if (budgetMinor == null) return null;
        if (!YearMonth.from(today).equals(month)) return null;
        long remaining = Math.subtractExact(budgetMinor, expensesMinor);
        long safe = Math.max(remaining, 0L);
        long daysRemaining = month.lengthOfMonth() - today.getDayOfMonth() + 1L;
        return safe / daysRemaining;
    }

    // --- mirror: rollover prefill decision (BudgetViewModel.preloadMonth) ---
    record Prefill(String input, boolean notice) {}
    static Prefill rolloverPrefill(Long existingMinor, Long previousMinor) {
        if (existingMinor != null) return new Prefill(existingMinor.toString(), false);
        if (previousMinor != null) return new Prefill(previousMinor.toString(), true);
        return new Prefill("", false);
    }

    static void check(String name, boolean condition) {
        if (condition) passed++;
        else { failures.add(name); System.out.println("FAIL: " + name); }
    }
    static void eq(String name, Object expected, Object actual) {
        check(name + " (expected " + expected + ", got " + actual + ")",
                expected == null ? actual == null : expected.equals(actual));
    }

    public static void main(String[] args) {
        LocalDate mar10 = LocalDate.of(2024, 3, 10);

        // Status thresholds.
        eq("0% on track", Status.ON_TRACK, evaluateBudgetStatus(0.0));
        eq("79.99 on track", Status.ON_TRACK, evaluateBudgetStatus(79.99));
        eq("80 warns", Status.WARNING, evaluateBudgetStatus(80.0));
        eq("99.99 warns", Status.WARNING, evaluateBudgetStatus(99.99));
        eq("100 exceeded", Status.EXCEEDED, evaluateBudgetStatus(100.0));
        eq("150 exceeded", Status.EXCEEDED, evaluateBudgetStatus(150.0));

        // Daily allowance.
        eq("march 10 -> 22 days left", 1_000_000L / 22,
                dailyAllowance(3_000_000L, 2_000_000L, mar10, YearMonth.of(2024, 3)));
        eq("first day -> whole month", 100_000L,
                dailyAllowance(3_100_000L, 0L, LocalDate.of(2024, 3, 1), YearMonth.of(2024, 3)));
        eq("last day -> remainder", 50_000L,
                dailyAllowance(3_000_000L, 2_950_000L, LocalDate.of(2024, 3, 31), YearMonth.of(2024, 3)));
        eq("leap feb 10 -> 20 days", 145_000L,
                dailyAllowance(2_900_000L, 0L, LocalDate.of(2024, 2, 10), YearMonth.of(2024, 2)));
        eq("overspent clamps to zero", 0L,
                dailyAllowance(1_000_000L, 1_500_000L, mar10, YearMonth.of(2024, 3)));
        eq("exactly spent clamps to zero", 0L,
                dailyAllowance(1_000_000L, 1_000_000L, mar10, YearMonth.of(2024, 3)));
        eq("no budget -> null", null,
                dailyAllowance(null, 1L, mar10, YearMonth.of(2024, 3)));
        eq("past month -> null", null,
                dailyAllowance(1_000_000L, 0L, mar10, YearMonth.of(2024, 2)));
        eq("future month -> null", null,
                dailyAllowance(1_000_000L, 0L, mar10, YearMonth.of(2024, 4)));
        eq("zero budget -> zero allowance", 0L,
                dailyAllowance(0L, 0L, mar10, YearMonth.of(2024, 3)));

        // Rollover prefill decisions.
        eq("existing budget wins, no notice", new Prefill("3000000", false),
                rolloverPrefill(3_000_000L, 2_500_000L));
        eq("rollover prefills with notice", new Prefill("2500000", true),
                rolloverPrefill(null, 2_500_000L));
        eq("neither -> blank, no notice", new Prefill("", false),
                rolloverPrefill(null, null));

        System.out.println();
        System.out.println("PASSED: " + passed + " assertions");
        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures.size());
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
        System.out.println("ALL BUDGET CHECKS PASSED");
    }
}
