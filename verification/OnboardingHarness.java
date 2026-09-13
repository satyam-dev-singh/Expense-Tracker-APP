import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Offline JVM mirror of the Phase 2 onboarding rules (run: java verification/OnboardingHarness.java).
 * Mirrors CompleteOnboarding 1:1: validation-before-write, skip semantics,
 * write ordering (completion flag LAST), and next-occurrence date math.
 * Any failure exits 1. On a dev machine run ./gradlew testDebugUnitTest instead.
 */
public class OnboardingHarness {

    static int passed = 0;
    static List<String> failures = new ArrayList<>();

    // --- mirror of the domain logic ---
    record Result(String name, String currencyCode, Long incomeMinor, String frequency,
                  Long budgetMinor, Set<String> archiveIds) {}

    static LocalDate nextOccurrence(String frequency, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        return switch (frequency) {
            case "WEEKLY" -> today.plusWeeks(1);
            case "BIWEEKLY" -> today.plusWeeks(2);
            case "MONTHLY" -> today.plusMonths(1);
            case "QUARTERLY" -> today.plusMonths(3);
            case "YEARLY" -> today.plusYears(1);
            default -> null; // ONE_TIME
        };
    }

    static boolean isValidCurrency(String code) {
        if (code.length() != 3) return false;
        for (char c : code.toCharArray()) if (c < 'A' || c > 'Z') return false;
        try { java.util.Currency.getInstance(code); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    /** Mirrors CompleteOnboarding.invoke: throws IllegalArgumentException on invalid input. */
    static List<String> complete(Result r, Clock clock) {
        List<String> events = new ArrayList<>();
        // Validate BEFORE any write.
        if (r.name().trim().isEmpty()) throw new IllegalArgumentException("BlankName");
        if (!isValidCurrency(r.currencyCode())) throw new IllegalArgumentException("InvalidCurrency");
        if (r.incomeMinor() != null && r.incomeMinor() <= 0) throw new IllegalArgumentException("NonPositiveAmount");
        if (r.budgetMinor() != null && r.budgetMinor() <= 0) throw new IllegalArgumentException("NonPositiveAmount");

        events.add("categories"); // ensureDefaultCategories (idempotent)
        events.add("profile");
        if (r.budgetMinor() != null) {
            java.time.YearMonth current = java.time.YearMonth.now(clock);
            events.add("budget:" + current.getYear() + "-" + current.getMonthValue());
        }
        if (r.incomeMinor() != null) {
            LocalDate next = nextOccurrence(r.frequency(), clock);
            events.add("income:" + next);
        }
        r.archiveIds().forEach(id -> events.add("archive:" + id));
        events.add("currency-flag");
        events.add("completed-flag"); // always last
        return events;
    }

    // --- tiny assertion framework ---
    static void check(String name, boolean condition) {
        if (condition) passed++;
        else { failures.add(name); System.out.println("FAIL: " + name); }
    }
    static void eq(String name, Object expected, Object actual) {
        check(name + " (expected " + expected + ", got " + actual + ")",
                expected == null ? actual == null : expected.equals(actual));
    }
    static void throwsIllegal(String name, Runnable r) {
        try { r.run(); check(name + " (no exception)", false); }
        catch (IllegalArgumentException e) { passed++; }
    }

    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"));
        Result full = new Result("Satyam", "INR", 5_000_000L, "MONTHLY", 3_000_000L, Set.of("default-travel"));
        Result skipped = new Result("Satyam", "INR", null, "MONTHLY", null, Set.of());

        // Validation before any write.
        throwsIllegal("blank name rejected", () -> complete(new Result("  ", "INR", null, "MONTHLY", null, Set.of()), clock));
        throwsIllegal("invalid currency rejected", () -> complete(new Result("S", "XYZ", null, "MONTHLY", null, Set.of()), clock));
        throwsIllegal("lowercase currency rejected", () -> complete(new Result("S", "inr", null, "MONTHLY", null, Set.of()), clock));
        throwsIllegal("zero income rejected", () -> complete(new Result("S", "INR", 0L, "MONTHLY", null, Set.of()), clock));
        throwsIllegal("negative budget rejected", () -> complete(new Result("S", "INR", null, "MONTHLY", -1L, Set.of()), clock));
        throwsIllegal("zero budget rejected", () -> complete(new Result("S", "INR", null, "MONTHLY", 0L, Set.of()), clock));

        // Happy path: exact write order, completion flag last.
        eq("happy path write order",
                List.of("categories", "profile", "budget:2024-3", "income:2024-04-10",
                        "archive:default-travel", "currency-flag", "completed-flag"),
                complete(full, clock));
        eq("flag is last", "completed-flag", complete(full, clock).getLast());

        // Skip semantics: no income/budget writes at all.
        eq("skipped optionals write order",
                List.of("categories", "profile", "currency-flag", "completed-flag"),
                complete(skipped, clock));

        // Budget month derives from the injected clock (Dec 31 IST stays December).
        Clock newYearsEve = Clock.fixed(Instant.parse("2024-12-31T10:00:00Z"), ZoneId.of("Asia/Kolkata"));
        check("dec 31 budget in december", complete(full, newYearsEve).contains("budget:2024-12"));
        Clock newYear = Clock.fixed(Instant.parse("2025-01-01T00:30:00Z"), ZoneId.of("Asia/Kolkata"));
        check("jan 1 budget in january", complete(full, newYear).contains("budget:2025-1"));

        // Next-occurrence math per frequency.
        eq("weekly +7d", LocalDate.of(2024, 3, 17), nextOccurrence("WEEKLY", clock));
        eq("biweekly +14d", LocalDate.of(2024, 3, 24), nextOccurrence("BIWEEKLY", clock));
        eq("monthly +1m", LocalDate.of(2024, 4, 10), nextOccurrence("MONTHLY", clock));
        eq("quarterly +3m", LocalDate.of(2024, 6, 10), nextOccurrence("QUARTERLY", clock));
        eq("yearly +1y", LocalDate.of(2025, 3, 10), nextOccurrence("YEARLY", clock));
        eq("one-time null", null, nextOccurrence("ONE_TIME", clock));

        // Month-end edge: Jan 31 + 1 month lands on the last day of February (2024 leap).
        Clock jan31 = Clock.fixed(Instant.parse("2024-01-31T08:00:00Z"), ZoneId.of("Asia/Kolkata"));
        eq("jan 31 monthly rolls to feb 29 (leap)", LocalDate.of(2024, 2, 29), nextOccurrence("MONTHLY", jan31));
        Clock jan31NonLeap = Clock.fixed(Instant.parse("2023-01-31T08:00:00Z"), ZoneId.of("Asia/Kolkata"));
        eq("jan 31 monthly rolls to feb 28 (non-leap)", LocalDate.of(2023, 2, 28), nextOccurrence("MONTHLY", jan31NonLeap));

        System.out.println();
        System.out.println("PASSED: " + passed + " assertions");
        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures.size());
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
        System.out.println("ALL ONBOARDING CHECKS PASSED");
    }
}
