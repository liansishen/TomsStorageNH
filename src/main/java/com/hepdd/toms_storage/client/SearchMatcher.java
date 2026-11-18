package com.hepdd.toms_storage.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import cpw.mods.fml.common.Loader;

public final class SearchMatcher {

    private static final String NECH_MODID = "nech";
    private static final int MAX_FALLBACK_SOURCE_LENGTH = 256;
    private static final int DIVISION_BASE = 1000;
    private static final char[] POSTFIXES = "KMGTPE".toCharArray();
    private static final DecimalFormat FORMAT;
    private static final NecAdapter NEC = createNecAdapter();

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator('.');
        FORMAT = new DecimalFormat(".#;0.#", symbols);
    }

    private SearchMatcher() {}

    public static boolean matchesName(String sourceText, String searchText, Pattern fallbackPattern) {
        return matches(sourceText, searchText, fallbackPattern, false);
    }

    public static boolean matchesTooltip(String sourceText, String searchText, Pattern fallbackPattern) {
        return matches(sourceText, searchText, fallbackPattern, true);
    }

    public static SearchQuery parseSearchQuery(String rawText) {
        if (rawText == null || rawText.isEmpty()) return SearchQuery.EMPTY;
        if (rawText.startsWith("@")) {
            String body = rawText.substring(1);
            if (body.isEmpty()) return SearchQuery.EMPTY;
            return new SearchQuery(SearchTarget.MOD_ONLY, body, compilePattern(body));
        }
        if (rawText.startsWith("#")) {
            String body = rawText.substring(1);
            if (body.isEmpty()) return SearchQuery.EMPTY;
            return new SearchQuery(SearchTarget.TOOLTIP_ONLY, body, compilePattern(body));
        }
        return new SearchQuery(SearchTarget.NAME_AND_TOOLTIP, rawText, compilePattern(rawText));
    }

    public static boolean matchesSearch(String displayName, Supplier<List<String>> modTextsSupplier,
        Supplier<List<String>> tooltipSupplier, SearchQuery query) {
        if (query == null || query.isEmpty()) return true;
        if (query.target == SearchTarget.MOD_ONLY) {
            List<String> modTexts = modTextsSupplier == null ? Collections.emptyList() : modTextsSupplier.get();
            return matchesMod(modTexts, query.pattern);
        }
        if (query.target == SearchTarget.NAME_AND_TOOLTIP && matchesName(displayName, query.queryText, query.pattern)) {
            return true;
        }
        List<String> tooltip = tooltipSupplier == null ? Collections.emptyList() : tooltipSupplier.get();
        return matchesTooltipLines(tooltip, query);
    }

    public static boolean matchesTooltipLines(List<String> tooltip, SearchQuery query) {
        if (tooltip == null || tooltip.isEmpty()) return false;
        for (int i = 0; i < tooltip.size(); i++) {
            if (matchesTooltip(tooltip.get(i), query.queryText, query.pattern)) return true;
        }
        return false;
    }

    public static boolean matchesMod(List<String> modTexts, Pattern pattern) {
        if (modTexts == null || pattern == null) return false;
        for (int i = 0; i < modTexts.size(); i++) {
            if (patternMatches(pattern, modTexts.get(i))) return true;
        }
        return false;
    }

    public static Pattern compilePattern(String text) {
        if (text == null || text.isEmpty()) return null;
        if (isPotentiallyUnsafeRegex(text)) {
            return Pattern.compile(Pattern.quote(text.toLowerCase(Locale.ROOT)), Pattern.CASE_INSENSITIVE);
        }
        try {
            return Pattern.compile(text.toLowerCase(Locale.ROOT), Pattern.CASE_INSENSITIVE);
        } catch (RuntimeException ignored) {
            return Pattern.compile(Pattern.quote(text.toLowerCase(Locale.ROOT)), Pattern.CASE_INSENSITIVE);
        }
    }

    public static String formatNumber(long number) {
        if (number <= 9999) return Long.toString(number);
        long base = number;
        double last = base * 1000.0D;
        int exponent = -1;
        while (Long.toString(base)
            .length() + 1 > 4 && exponent + 1 < POSTFIXES.length) {
            last = base;
            base /= DIVISION_BASE;
            exponent++;
        }
        String withPrecision = FORMAT.format(last / DIVISION_BASE) + POSTFIXES[exponent];
        String withoutPrecision = base + String.valueOf(POSTFIXES[exponent]);
        return withPrecision.length() <= 4 ? withPrecision : withoutPrecision;
    }

    private static boolean patternMatches(Pattern pattern, String text) {
        if (text == null) return false;
        String bounded = text.length() > 256 ? text.substring(0, 256) : text;
        try {
            return pattern.matcher(bounded)
                .find();
        } catch (RuntimeException | StackOverflowError ignored) {
            return false;
        }
    }

    private static boolean isPotentiallyUnsafeRegex(String text) {
        return text.contains("(?") || text.matches(".*\\\\[1-9].*") || text.matches(".*\\)[+*{].*");
    }

    private static boolean matches(String sourceText, String searchText, Pattern fallbackPattern, boolean tooltip) {
        if (sourceText == null) return false;
        if (searchText == null || searchText.isEmpty()) return true;
        String boundedText = sourceText.length() > MAX_FALLBACK_SOURCE_LENGTH
            ? sourceText.substring(0, MAX_FALLBACK_SOURCE_LENGTH)
            : sourceText;
        if (NEC != null && NEC.contains(boundedText, searchText, tooltip)) return true;
        if (fallbackPattern == null) return false;
        try {
            return fallbackPattern.matcher(boundedText)
                .find();
        } catch (RuntimeException | StackOverflowError ignored) {
            return false;
        }
    }

    private static NecAdapter createNecAdapter() {
        try {
            if (!Loader.isModLoaded(NECH_MODID)) return null;
            Class<?> apiClass = Class.forName("com.asdflj.nech.API");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle instanceGetter = lookup.findStaticGetter(apiClass, "INSTANCE", apiClass)
                .asType(MethodType.methodType(Object.class));
            MethodHandle rawContains = lookup.findVirtual(
                apiClass,
                "contains",
                MethodType.methodType(boolean.class, CharSequence.class, CharSequence.class, boolean.class));
            Object instance = instanceGetter.invokeExact();
            if (instance != null) {
                MethodHandle bound = rawContains.bindTo(instance)
                    .asType(MethodType.methodType(boolean.class, String.class, String.class, boolean.class));
                return new NecAdapter(bound, null, null);
            }
            MethodHandle unbound = rawContains
                .asType(MethodType.methodType(boolean.class, Object.class, String.class, String.class, boolean.class));
            return new NecAdapter(null, instanceGetter, unbound);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public enum SearchTarget {
        NAME_AND_TOOLTIP,
        MOD_ONLY,
        TOOLTIP_ONLY
    }

    public static final class SearchQuery {

        public static final SearchQuery EMPTY = new SearchQuery(SearchTarget.NAME_AND_TOOLTIP, "", null);

        public final SearchTarget target;
        public final String queryText;
        public final Pattern pattern;

        private SearchQuery(SearchTarget target, String queryText, Pattern pattern) {
            this.target = target;
            this.queryText = queryText;
            this.pattern = pattern;
        }

        public boolean isEmpty() {
            return queryText == null || queryText.isEmpty() || pattern == null;
        }
    }

    private static final class NecAdapter {

        private final MethodHandle boundContains;
        private final MethodHandle instanceGetter;
        private final MethodHandle unboundContains;

        private NecAdapter(MethodHandle boundContains, MethodHandle instanceGetter, MethodHandle unboundContains) {
            this.boundContains = boundContains;
            this.instanceGetter = instanceGetter;
            this.unboundContains = unboundContains;
        }

        private boolean contains(String sourceText, String searchText, boolean tooltip) {
            try {
                if (boundContains != null) {
                    return (boolean) boundContains.invokeExact(sourceText, searchText, tooltip);
                }
                Object instance = instanceGetter.invokeExact();
                return instance != null
                    && (boolean) unboundContains.invokeExact(instance, sourceText, searchText, tooltip);
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
