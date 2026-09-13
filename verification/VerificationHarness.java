import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Offline JVM verification harness for Left (run with: java verification/VerificationHarness.java).
 * Mirrors the Kotlin production code 1:1 for the financially critical logic:
 *  Money <-> core/utils/Money.kt | MonthRange <-> core/common/MonthRange.kt
 *  Calculations <-> core/domain/CalculationUseCases.kt
 * Every assertion mirrors a unit test in app/src/test. Any failure exits 1.
 * Useful only in environments without an Android SDK; on a dev machine run
 * ./gradlew testDebugUnitTest instead.
 */
public class VerificationHarness {

    static int passed = 0;
    static List<String> failures = new ArrayList<>();

    // ---------------- Mirror of Money.kt ----------------
    static final class Money implements Comparable<Money> {
        final long minorUnits;
        private Money(long minorUnits) { this.minorUnits = minorUnits; }
        static final Money ZERO = new Money(0L);
        static Money ofMinorUnits(long v) { return new Money(v); }
        Money plus(Money o) { return new Money(Math.addExact(minorUnits, o.minorUnits)); }
        Money minus(Money o) { return new Money(Math.subtractExact(minorUnits, o.minorUnits)); }
        boolean isZero() { return minorUnits == 0L; }
        boolean isPositive() { return minorUnits > 0L; }
        @Override public int compareTo(Money o) { return Long.compare(minorUnits, o.minorUnits); }

        static Money parse(String input, String currencyCode) {
            int fd = fractionDigits(currencyCode); // validates currency first
            String cleaned = input.trim()
                    .replaceAll("\\.(?=[^0-9]|$)", "") // dots that are not decimal separators ("Rs. 10")
                    .replaceAll("[^0-9,\\.\\-]", "")
                    .replace(",", "");
            if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".") || cleaned.equals("-.")) {
                throw new IllegalArgumentException("Empty or non-numeric amount: '" + input + "'");
            }
            final BigDecimal decimal;
            try {
                decimal = new BigDecimal(cleaned);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Malformed amount: '" + input + "'", e);
            }
            try {
                return new Money(decimal.movePointRight(fd).longValueExact());
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Too many decimals or too large", e);
            }
        }
    }

    static boolean isValidCurrencyCode(String code) {
        if (code.length() != 3) return false;
        for (char c : code.toCharArray()) if (c < 'A' || c > 'Z') return false;
        try { Currency.getInstance(code); return true; } catch (IllegalArgumentException e) { return false; }
    }
    static int fractionDigits(String code) { return Currency.getInstance(code).getDefaultFractionDigits(); }

    // ---------------- Mirror of MonthRange.kt ----------------
    record MonthRange(YearMonth yearMonth) {
        LocalDate startDate() { return yearMonth.atDay(1); }
        LocalDate endExclusiveDate() { return yearMonth.plusMonths(1).atDay(1); }
        long startEpochDay() { return startDate().toEpochDay(); }
        long endExclusiveEpochDay() { return endExclusiveDate().toEpochDay(); }
        boolean contains(LocalDate d) { return !d.isBefore(startDate()) && d.isBefore(endExclusiveDate()); }
        static MonthRange of(int year, int month) {
            if (month < 1 || month > 12) throw new IllegalArgumentException("month must be in 1..12, was " + month);
            return new MonthRange(YearMonth.of(year, month));
        }
        static MonthRange current(Clock clock) { return new MonthRange(YearMonth.now(clock)); }
    }

    // ---------------- Mirror of calculation use cases ----------------
    enum Type { EXPENSE, INCOME }
    record Tx(Type type, long amountMinor, LocalDate date) {}

    static long monthlySum(List<Tx> txs, Type type, int year, int month) {
        MonthRange range = MonthRange.of(year, month);
        long total = 0L;
        for (Tx tx : txs) {
            if (tx.type() == type && range.contains(tx.date())) total = Math.addExact(total, tx.amountMinor());
        }
        return total;
    }
    static long remainingMoney(List<Tx> txs, int year, int month) {
        return Math.subtractExact(monthlySum(txs, Type.INCOME, year, month), monthlySum(txs, Type.EXPENSE, year, month));
    }
    static Long budgetRemaining(List<Tx> txs, Long budgetMinor, int year, int month) {
        if (budgetMinor == null) return null;
        return Math.subtractExact(budgetMinor, monthlySum(txs, Type.EXPENSE, year, month));
    }
    static Double budgetUsagePercentage(List<Tx> txs, Long budgetMinor, int year, int month) {
        if (budgetMinor == null || budgetMinor <= 0) return null;
        long expenses = monthlySum(txs, Type.EXPENSE, year, month);
        return BigDecimal.valueOf(expenses).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(budgetMinor), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // ---------------- Tiny assertion framework ----------------
    static void check(String name, boolean condition) {
        if (condition) { passed++; }
        else { failures.add(name); System.out.println("FAIL: " + name); }
    }
    static void eqLong(String name, long expected, long actual) {
        check(name + " (expected " + expected + ", got " + actual + ")", expected == actual);
    }
    static void eqObj(String name, Object expected, Object actual) {
        check(name + " (expected " + expected + ", got " + actual + ")",
                expected == null ? actual == null : expected.equals(actual));
    }
    static void closeTo(String name, double expected, Double actual, double delta) {
        check(name + " (expected " + expected + ", got " + actual + ")",
                actual != null && Math.abs(actual - expected) <= delta);
    }
    static void throwsIllegal(String name, Runnable r) {
        try { r.run(); check(name + " (no exception thrown)", false); }
        catch (IllegalArgumentException | ArithmeticException e) { passed++; }
    }

    public static void main(String[] args) {
        // ---- Money: integer arithmetic / zero / large / decimal input ----
        eqLong("money plus", 35_075L, Money.ofMinorUnits(10_050).plus(Money.ofMinorUnits(25_025)).minorUnits);
        eqLong("money minus", -14_975L, Money.ofMinorUnits(10_050).minus(Money.ofMinorUnits(25_025)).minorUnits);
        check("zero identity", Money.ofMinorUnits(500).plus(Money.ZERO).minorUnits == 500L);
        check("zero flags", Money.ZERO.isZero() && !Money.ZERO.isPositive());
        eqLong("large exact", 19_999_999_999_999L,
                Money.ofMinorUnits(10_000_000_000_000L).plus(Money.ofMinorUnits(10_000_000_000_000L)).minus(Money.ofMinorUnits(1)).minorUnits);
        throwsIllegal("overflow detected", () -> Money.ofMinorUnits(Long.MAX_VALUE).plus(Money.ofMinorUnits(1)));
        eqLong("parse 100.50 -> 10050", 10_050L, Money.parse("100.50", "INR").minorUnits);
        eqLong("parse 250", 25_000L, Money.parse("250", "INR").minorUnits);
        eqLong("parse 1,00,000.50 indian grouping", 10_000_050L, Money.parse("₹1,00,000.50", "INR").minorUnits);
        eqLong("parse 1,234.56 western grouping", 123_456L, Money.parse("1,234.56", "INR").minorUnits);
        eqLong("parse negative", -5_000L, Money.parse("-50", "INR").minorUnits);
        eqLong("parse Rs. prefix", 1_000L, Money.parse("Rs. 10", "INR").minorUnits);
        throwsIllegal("reject 10.005 INR (no silent rounding)", () -> Money.parse("10.005", "INR"));
        throwsIllegal("reject blank", () -> Money.parse("   ", "INR"));
        throwsIllegal("reject abc", () -> Money.parse("abc", "INR"));
        throwsIllegal("reject 1.2.3", () -> Money.parse("1.2.3", "INR"));
        eqLong("JPY whole", 100L, Money.parse("100", "JPY").minorUnits);
        throwsIllegal("reject 100.5 JPY", () -> Money.parse("100.5", "JPY"));
        check("currency INR valid", isValidCurrencyCode("INR"));
        check("currency lowercase invalid", !isValidCurrencyCode("inr"));
        check("currency XYZ invalid", !isValidCurrencyCode("XYZ"));
        check("currency 2-letter invalid", !isValidCurrencyCode("IN"));

        // ---- MonthRange: rollover / leap / lengths / boundaries / timezone ----
        MonthRange dec = MonthRange.of(2024, 12);
        eqObj("dec start", LocalDate.of(2024, 12, 1), dec.startDate());
        eqObj("dec->jan rollover", LocalDate.of(2025, 1, 1), dec.endExclusiveDate());
        check("dec contains 31st", dec.contains(LocalDate.of(2024, 12, 31)));
        check("dec excludes jan 1", !dec.contains(LocalDate.of(2025, 1, 1)));
        MonthRange feb24 = MonthRange.of(2024, 2);
        eqLong("feb2024 has 29 days", 29L, feb24.endExclusiveEpochDay() - feb24.startEpochDay());
        check("feb2024 contains leap day", feb24.contains(LocalDate.of(2024, 2, 29)));
        eqLong("feb2023 has 28 days", 28L, MonthRange.of(2023, 2).endExclusiveEpochDay() - MonthRange.of(2023, 2).startEpochDay());
        eqLong("january 31 days", 31L, MonthRange.of(2024, 1).endExclusiveEpochDay() - MonthRange.of(2024, 1).startEpochDay());
        eqLong("april 30 days", 30L, MonthRange.of(2024, 4).endExclusiveEpochDay() - MonthRange.of(2024, 4).startEpochDay());
        MonthRange mar = MonthRange.of(2024, 3);
        check("march start inclusive", mar.contains(LocalDate.of(2024, 3, 1)));
        check("march 31 inclusive", mar.contains(LocalDate.of(2024, 3, 31)));
        check("april 1 exclusive", !mar.contains(LocalDate.of(2024, 4, 1)));
        check("feb 29 excluded from march", !mar.contains(LocalDate.of(2024, 2, 29)));
        Instant boundary = Instant.parse("2024-02-29T19:00:00Z");
        eqObj("timezone UTC sees Feb", YearMonth.of(2024, 2), MonthRange.current(Clock.fixed(boundary, ZoneOffset.UTC)).yearMonth());
        eqObj("timezone IST sees Mar", YearMonth.of(2024, 3), MonthRange.current(Clock.fixed(boundary, ZoneId.of("Asia/Kolkata"))).yearMonth());
        throwsIllegal("month 0 rejected", () -> MonthRange.of(2024, 0));
        throwsIllegal("month 13 rejected", () -> MonthRange.of(2024, 13));

        // ---- Calculations: PRD reference scenario ----
        List<Tx> prd = new ArrayList<>();
        prd.add(new Tx(Type.INCOME, 5_000_000, LocalDate.of(2024, 3, 5)));   // Rs 50,000
        prd.add(new Tx(Type.EXPENSE, 2_000_000, LocalDate.of(2024, 3, 7)));   // Rs 20,000
        eqLong("PRD income 50000", 5_000_000L, monthlySum(prd, Type.INCOME, 2024, 3));
        eqLong("PRD expenses 20000", 2_000_000L, monthlySum(prd, Type.EXPENSE, 2024, 3));
        eqLong("PRD remaining 30000", 3_000_000L, remainingMoney(prd, 2024, 3));
        eqObj("PRD budget remaining 10000", 1_000_000L, budgetRemaining(prd, 3_000_000L, 2024, 3));
        closeTo("PRD usage 66.67%", 66.67, budgetUsagePercentage(prd, 3_000_000L, 2024, 3), 0.001);

        // ---- Calculations: edge cases ----
        List<Tx> none = List.of();
        eqLong("no transactions income zero", 0L, monthlySum(none, Type.INCOME, 2024, 3));
        eqLong("no transactions remaining zero", 0L, remainingMoney(none, 2024, 3));
        List<Tx> onlyExp = List.of(new Tx(Type.EXPENSE, 150_000, LocalDate.of(2024, 3, 2)));
        eqLong("only expenses -> negative remaining", -150_000L, remainingMoney(onlyExp, 2024, 3));
        List<Tx> onlyInc = List.of(new Tx(Type.INCOME, 250_000, LocalDate.of(2024, 3, 2)));
        eqLong("only income -> remaining = income", 250_000L, remainingMoney(onlyInc, 2024, 3));
        List<Tx> multi = List.of(
                new Tx(Type.EXPENSE, 10_000, LocalDate.of(2024, 3, 1)),
                new Tx(Type.EXPENSE, 20_000, LocalDate.of(2024, 3, 2)),
                new Tx(Type.EXPENSE, 30_000, LocalDate.of(2024, 3, 3)));
        eqLong("multiple categories all count", 60_000L, monthlySum(multi, Type.EXPENSE, 2024, 3));
        List<Tx> spread = List.of(
                new Tx(Type.EXPENSE, 1_000, LocalDate.of(2024, 2, 29)),
                new Tx(Type.EXPENSE, 2_000, LocalDate.of(2024, 3, 15)),
                new Tx(Type.EXPENSE, 4_000, LocalDate.of(2024, 4, 1)));
        eqLong("march only", 2_000L, monthlySum(spread, Type.EXPENSE, 2024, 3));
        eqLong("february only", 1_000L, monthlySum(spread, Type.EXPENSE, 2024, 2));
        eqLong("april only", 4_000L, monthlySum(spread, Type.EXPENSE, 2024, 4));
        List<Tx> leap = List.of(new Tx(Type.INCOME, 50_000, LocalDate.of(2024, 2, 29)));
        eqLong("leap day in february", 50_000L, monthlySum(leap, Type.INCOME, 2024, 2));
        eqLong("leap day not in march", 0L, monthlySum(leap, Type.INCOME, 2024, 3));
        eqObj("no budget -> null remaining", null, budgetRemaining(prd, null, 2024, 3));
        eqObj("no budget -> null usage", null, budgetUsagePercentage(prd, null, 2024, 3));
        eqObj("zero budget -> null usage", null, budgetUsagePercentage(prd, 0L, 2024, 3));
        eqObj("zero budget -> negative remaining", -2_000_000L, budgetRemaining(prd, 0L, 2024, 3));
        eqObj("unused budget fully remaining", 3_000_000L, budgetRemaining(none, 3_000_000L, 2024, 3));
        closeTo("unused budget zero usage", 0.0, budgetUsagePercentage(none, 3_000_000L, 2024, 3), 0.0001);
        closeTo("overspend 150%", 150.0, budgetUsagePercentage(
                List.of(new Tx(Type.EXPENSE, 1_500_000, LocalDate.of(2024, 3, 10))), 1_000_000L, 2024, 3), 0.0001);

        System.out.println();
        System.out.println("PASSED: " + passed + " assertions");
        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures.size());
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
        System.out.println("ALL CHECKS PASSED");
    }
}
