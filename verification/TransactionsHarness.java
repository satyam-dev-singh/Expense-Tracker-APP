import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Offline JVM mirror of Phase 3 list/detail rules (run: java verification/TransactionsHarness.java).
 * Mirrors TransactionsViewModel helpers 1:1: day-group labels (Today/Yesterday/short date),
 * inclusive amount-range predicates (currency-aware parse), the blank-vs-typed search
 * decision, edit-prefill format/parse round-trip, and YearMonth navigation rollover.
 * Any failure exits 1. On a dev machine run ./gradlew testDebugUnitTest instead.
 */
public class TransactionsHarness {

    static int passed = 0;
    static List<String> failures = new ArrayList<>();

    // --- mirror: day label (TransactionsViewModel.dayLabel) ---
    static String dayLabel(LocalDate date, LocalDate today) {
        if (date.equals(today)) return "Today";
        if (date.equals(today.minusDays(1))) return "Yesterday";
        return date.format(DateTimeFormatter.ofPattern("EEE, d MMM"));
    }

    // --- mirror: filter amount parse (blank -> null filter; malformed -> null) ---
    static Long parseFilterAmount(String input, String currencyCode) {
        if (input == null || input.isBlank()) return null;
        try {
            int fd = Currency.getInstance(currencyCode).getDefaultFractionDigits();
            String cleaned = input.trim()
                    .replaceAll("\\.(?=[^0-9]|$)", "")
                    .replaceAll("[^0-9,\\.\\-]", "")
                    .replace(",", "");
            return new java.math.BigDecimal(cleaned).movePointRight(fd).longValueExact();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // --- mirror: filter predicate (type/category/min/max all in-memory) ---
    record Tx(String id, String type, long amountMinor, String categoryId, String merchant, LocalDate date) {}
    static boolean matches(Tx tx, String type, String categoryId, Long min, Long max) {
        if (type != null && !tx.type().equals(type)) return false;
        if (categoryId != null && (tx.categoryId() == null || !tx.categoryId().equals(categoryId))) return false;
        if (min != null && tx.amountMinor() < min) return false;
        if (max != null && tx.amountMinor() > max) return false;
        return true;
    }

    // --- mirror: search decision (blank query -> month scope; typed -> all-time search) ---
    static String searchScope(String query) {
        return query.isBlank() ? "month" : "all-time";
    }

    // --- mirror: edit prefill round-trip (format -> parse -> same minor units) ---
    static long prefillRoundTrip(long minorUnits, String currencyCode) {
        NumberFormat f = NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("en-IN"));
        f.setCurrency(Currency.getInstance(currencyCode));
        String formatted = f.format(new java.math.BigDecimal(minorUnits).movePointLeft(
                Currency.getInstance(currencyCode).getDefaultFractionDigits()));
        Long parsed = parseFilterAmount(formatted, currencyCode);
        if (parsed == null) throw new IllegalStateException("prefill did not parse: " + formatted);
        return parsed;
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
        LocalDate today = LocalDate.of(2024, 3, 10);

        // Day labels.
        eq("today label", "Today", dayLabel(today, today));
        eq("yesterday label", "Yesterday", dayLabel(LocalDate.of(2024, 3, 9), today));
        eq("older label", "Fri, 1 Mar", dayLabel(LocalDate.of(2024, 3, 1), today));
        eq("leap day label", "Thu, 29 Feb", dayLabel(LocalDate.of(2024, 2, 29), today));

        // Amount range predicates (inclusive bounds).
        Tx tea = new Tx("t1", "EXPENSE", 35_000, "default-food", "Tea stall", today);
        Tx metro = new Tx("t2", "EXPENSE", 12_000, "default-transport", "Metro", today);
        Tx salary = new Tx("t3", "INCOME", 5_000_000, null, "Salary", today);
        check("min inclusive", matches(tea, null, null, parseFilterAmount("350", "INR"), null));
        check("max inclusive", matches(tea, null, null, null, parseFilterAmount("350", "INR")));
        check("below min excluded", !matches(metro, null, null, parseFilterAmount("350", "INR"), null));
        check("above max excluded", !matches(salary, null, null, null, parseFilterAmount("400", "INR")));
        check("type filter", matches(tea, "EXPENSE", null, null, null) && !matches(salary, "EXPENSE", null, null, null));
        check("category filter", matches(tea, null, "default-food", null, null) && !matches(metro, null, "default-food", null, null));
        check("uncategorized excluded by category filter", !matches(salary, null, "default-food", null, null));
        check("no filters matches all", matches(tea, null, null, null, null) && matches(salary, null, null, null, null));

        // Malformed filter input -> ignored (null), never a crash.
        eq("malformed min ignored", null, parseFilterAmount("abc", "INR"));
        eq("malformed multi-dot ignored", null, parseFilterAmount("1.2.3", "INR"));
        eq("blank filter", null, parseFilterAmount("  ", "INR"));
        eq("grouped amount parses", 10_000_050L, parseFilterAmount("₹1,00,000.50", "INR"));

        // Search decision.
        eq("blank query -> month", "month", searchScope(""));
        eq("spaces query -> month", "month", searchScope("   "));
        eq("typed query -> all-time", "all-time", searchScope("chai"));

        // Edit prefill round-trip: what the field shows must parse back exactly.
        check("prefill round-trip 350.00", prefillRoundTrip(35_000, "INR") == 35_000L);
        check("prefill round-trip 0.99", prefillRoundTrip(99, "INR") == 99L);
        check("prefill round-trip large", prefillRoundTrip(10_000_000_000_000L, "INR") == 10_000_000_000_000L);

        // Month navigation rollover.
        eq("dec -> jan next", YearMonth.of(2025, 1), YearMonth.of(2024, 12).plusMonths(1));
        eq("jan -> prev dec", YearMonth.of(2023, 12), YearMonth.of(2024, 1).minusMonths(1));

        System.out.println();
        System.out.println("PASSED: " + passed + " assertions");
        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures.size());
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
        System.out.println("ALL TRANSACTION-UI CHECKS PASSED");
    }
}
