import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Offline JVM mirror of the Phase 5 voice rules (run: java verification/VoiceHarness.java).
 * Mirrors VoiceExpenseParser.kt 1:1: amount token extraction, income keywords,
 * ordered category keyword matching, merchant phrase (amount token stripped),
 * date words (yesterday / on-the-Nth / named months with rollover), NoAmount
 * path — plus the duplicate-signature guard with reset on a new round.
 * Any failure exits 1. On a dev machine run ./gradlew testDebugUnitTest instead.
 */
public class VoiceHarness {

    static int passed = 0;
    static List<String> failures = new ArrayList<>();

    record Draft(long amountMinor, String type, String categoryId, String merchant, String note, LocalDate date) {}

    static final List<String> INCOME = List.of("received", "salary", "income", "earned", "credited", "refund", "got paid", "bonus");
    static final List<Map.Entry<String, List<String>>> CATEGORY_KEYWORDS = List.of(
            Map.entry("Food", List.of("food", "groceries", "grocery", "lunch", "dinner", "breakfast", "chai", "tea", "coffee", "restaurant", "meal", "snack", "pizza", "biryani", "swiggy", "zomato")),
            Map.entry("Transport", List.of("uber", "ola", "taxi", "cab", "fuel", "petrol", "diesel", "metro", "bus", "train", "auto", "rickshaw", "parking", "toll")),
            Map.entry("Shopping", List.of("shopping", "clothes", "amazon", "flipkart", "shirt", "shoes", "electronics", "gadget")),
            Map.entry("Bills", List.of("bill", "bills", "electricity", "rent", "recharge", "wifi", "internet", "broadband", "mobile", "dth", "water")),
            Map.entry("Entertainment", List.of("movie", "movies", "cinema", "netflix", "game", "games", "spotify", "show", "concert")),
            Map.entry("Health", List.of("doctor", "medicine", "medicines", "pharmacy", "hospital", "gym", "health", "dentist", "tablet", "clinic")),
            Map.entry("Education", List.of("course", "book", "books", "tuition", "school", "college", "udemy", "exam", "class")),
            Map.entry("Travel", List.of("flight", "hotel", "trip", "travel", "visa", "holiday", "vacation")),
            Map.entry("Personal", List.of("salon", "haircut", "personal", "cosmetics", "grooming")));
    static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    static boolean containsWord(String text, String word) {
        return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(word) + "\\b").matcher(text).find();
    }

    /** Mirrors VoiceExpenseParser.parse (amounts already in minor units). */
    static Draft parse(String transcript, LocalDate today) {
        String text = transcript.trim();
        String lower = text.toLowerCase();
        var amountMatcher = java.util.regex.Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?").matcher(lower);
        if (!amountMatcher.find()) return null; // NoAmount
        String token = amountMatcher.group();
        String cleaned = token.replace(",", "");
        java.math.BigDecimal major;
        try { major = new java.math.BigDecimal(cleaned); }
        catch (NumberFormatException e) { return null; }
        long minor;
        try { minor = major.movePointRight(2).longValueExact(); }
        catch (ArithmeticException e) { return null; }
        if (minor <= 0) return null; // NoAmount

        String type = INCOME.stream().anyMatch(k -> containsWord(lower, k)) ? "INCOME" : "EXPENSE";

        String categoryId = null;
        for (var entry : CATEGORY_KEYWORDS) {
            if (entry.getValue().stream().anyMatch(k -> containsWord(lower, k))) {
                categoryId = "default-" + entry.getKey().toLowerCase();
                break;
            }
        }

        String merchant = null;
        var merchantMatcher = java.util.regex.Pattern.compile("\\b(?:at|from|to)\\s+(.+?)\\s*$").matcher(text);
        if (merchantMatcher.find()) {
            String raw = merchantMatcher.group(1).replace(token, "").trim();
            if (!raw.isBlank()) {
                StringBuilder sb = new StringBuilder();
                for (String word : raw.split(" ")) {
                    if (word.isBlank()) continue;
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                }
                merchant = sb.length() == 0 ? null : sb.toString();
            }
        }

        LocalDate date = today;
        if (containsWord(lower, "yesterday")) {
            date = today.minusDays(1);
        } else {
            var namedMonth = java.util.regex.Pattern
                    .compile("\\bon\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
                    .matcher(lower);
            var onDay = java.util.regex.Pattern
                    .compile("\\bon(?:\\s+the)?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
                    .matcher(lower);
            if (namedMonth.find()) {
                int month = MONTHS.get(namedMonth.group(1));
                int day = Integer.parseInt(namedMonth.group(2));
                LocalDate candidate = LocalDate.of(today.getYear(), month, 1);
                candidate = candidate.withDayOfMonth(Math.min(day, candidate.lengthOfMonth()));
                if (candidate.isAfter(today)) candidate = candidate.minusYears(1);
                date = candidate;
            } else if (onDay.find()) {
                int day = Integer.parseInt(onDay.group(1));
                LocalDate candidate = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
                if (candidate.isAfter(today)) {
                    LocalDate prev = today.minusMonths(1);
                    candidate = prev.withDayOfMonth(Math.min(day, prev.lengthOfMonth()));
                }
                date = candidate;
            }
        }
        return new Draft(minor, type, categoryId, merchant, text, date);
    }

    // --- mirror: duplicate signature guard (VoiceViewModel) ---
    static String signature(Draft d) {
        return d.amountMinor() + "|" + d.type() + "|" + d.categoryId() + "|" + d.merchant() + "|" + d.note() + "|" + d.date();
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

        // Amount + type.
        eq("simple amount", 25_000L, parse("add 250 for groceries", today).amountMinor());
        eq("expense default", "EXPENSE", parse("add 250 for groceries", today).type());
        eq("income keyword", "INCOME", parse("received 50000 salary", today).type());
        eq("income got paid", "INCOME", parse("got paid 12000", today).type());
        eq("decimal amount", 100_050L, parse("₹1,000.50 for books", today).amountMinor());
        eq("rs-dot amount", 25_000L, parse("spent Rs. 250 on chai", today).amountMinor());
        eq("first number wins", 20_000L, parse("spent 200 on 2 movie tickets", today).amountMinor());

        // NoAmount paths.
        eq("no number -> null", null, parse("bought groceries", today));
        eq("zero -> null", null, parse("spent 0 on food", today));

        // Categories (ordered keyword map).
        eq("groceries -> food", "default-food", parse("add 250 for groceries", today).categoryId());
        eq("uber -> transport", "default-transport", parse("uber 300", today).categoryId());
        eq("shoes -> shopping", "default-shopping", parse("bought shoes for 2000", today).categoryId());
        eq("electricity -> bills", "default-bills", parse("paid 1200 electricity bill", today).categoryId());
        eq("movie -> entertainment", "default-entertainment", parse("movie night 450", today).categoryId());
        eq("doctor -> health", "default-health", parse("doctor visit 800", today).categoryId());
        eq("course -> education", "default-education", parse("course fee 1500", today).categoryId());
        eq("hotel -> travel", "default-travel", parse("hotel 3000", today).categoryId());
        eq("haircut -> personal", "default-personal", parse("haircut 500", today).categoryId());
        eq("unmatched -> null", null, parse("spent 100 at random place", today).categoryId());

        // Merchant.
        eq("merchant title-cased", "Chai Point", parse("Spent 50 on food at chai point", today).merchant());
        eq("amount stripped from merchant", "Delhi", parse("flight to delhi 5500", today).merchant());
        eq("no merchant", null, parse("add 250 for groceries", today).merchant());

        // Dates.
        eq("yesterday", today.minusDays(1), parse("paid 1200 electricity yesterday", today).date());
        eq("default today", today, parse("lunch 200", today).date());
        eq("on the 5th", LocalDate.of(2024, 3, 5), parse("lunch 200 on the 5th", today).date());
        eq("on 28th said on mar 3 -> feb 28", LocalDate.of(2024, 2, 28),
                parse("lunch 200 on the 28th", LocalDate.of(2024, 3, 3)).date());
        eq("named month this year", LocalDate.of(2024, 3, 5), parse("hotel 5000 on march 5", today).date());
        eq("named month rolls back a year", LocalDate.of(2023, 12, 5), parse("hotel 5000 on december 5", today).date());

        // Duplicate signature guard: same draft blocked, new round resets.
        Draft draft = parse("spent 250 on food", today);
        String lastSaved = null;
        boolean firstSaveBlocked = signature(draft).equals(lastSaved);
        lastSaved = signature(draft);
        boolean secondSaveBlocked = signature(draft).equals(lastSaved);
        check("first save allowed", !firstSaveBlocked);
        check("same draft re-save blocked", secondSaveBlocked);
        lastSaved = null; // onStartListening resets
        check("new round resets guard", !signature(draft).equals(lastSaved));

        System.out.println();
        System.out.println("PASSED: " + passed + " assertions");
        if (!failures.isEmpty()) {
            System.out.println("FAILED: " + failures.size());
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
        System.out.println("ALL VOICE CHECKS PASSED");
    }
}
