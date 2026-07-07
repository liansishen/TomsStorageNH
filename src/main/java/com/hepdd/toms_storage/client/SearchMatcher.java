package com.hepdd.toms_storage.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import cpw.mods.fml.common.Loader;

final class SearchMatcher {

    private static final String NECH_MODID = "nech";
    private static final NecAdapter NEC = createNecAdapter();

    private SearchMatcher() {}

    public static boolean matchesName(String sourceText, String searchText, Pattern fallbackPattern) {
        return matches(sourceText, searchText, fallbackPattern, false);
    }

    public static boolean matchesTooltip(String sourceText, String searchText, Pattern fallbackPattern) {
        return matches(sourceText, searchText, fallbackPattern, true);
    }

    private static boolean matches(String sourceText, String searchText, Pattern fallbackPattern, boolean tooltip) {
        if (sourceText == null) return false;
        if (searchText == null || searchText.isEmpty()) return true;
        if (NEC != null && NEC.contains(sourceText, searchText, tooltip)) return true;
        return fallbackPattern != null && fallbackPattern.matcher(sourceText)
            .find();
    }

    private static NecAdapter createNecAdapter() {
        if (!Loader.isModLoaded(NECH_MODID)) return null;
        try {
            Class<?> apiClass = Class.forName("com.asdflj.nech.API");
            Field api = apiClass.getField("INSTANCE");
            Method contains = apiClass.getMethod("contains", CharSequence.class, CharSequence.class, boolean.class);
            return new NecAdapter(api, contains);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static final class NecAdapter {

        private final Field api;
        private final Method contains;

        private NecAdapter(Field api, Method contains) {
            this.api = api;
            this.contains = contains;
        }

        private boolean contains(String sourceText, String searchText, boolean tooltip) {
            try {
                Object instance = api.get(null);
                return instance != null && (Boolean) contains.invoke(instance, sourceText, searchText, tooltip);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
    }
}
