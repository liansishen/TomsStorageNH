package com.hepdd.toms_storage.inventory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

final class StorageCompatibility {

    private static final ClassValue<ConcurrentHashMap<String, Method>> PUBLIC_METHODS = new ClassValue<ConcurrentHashMap<String, Method>>() {

        @Override
        protected ConcurrentHashMap<String, Method> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final ClassValue<ConcurrentHashMap<String, Method>> PRIVATE_METHODS = new ClassValue<ConcurrentHashMap<String, Method>>() {

        @Override
        protected ConcurrentHashMap<String, Method> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final ClassValue<ConcurrentHashMap<String, Method>> INT_SETTERS = new ClassValue<ConcurrentHashMap<String, Method>>() {

        @Override
        protected ConcurrentHashMap<String, Method> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final ClassValue<ConcurrentHashMap<String, Field>> PUBLIC_FIELDS = new ClassValue<ConcurrentHashMap<String, Field>>() {

        @Override
        protected ConcurrentHashMap<String, Field> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private StorageCompatibility() {}

    static Object call(Object target, String name) {
        Class<?> targetClass = target.getClass();
        Method method = PUBLIC_METHODS.get(targetClass)
            .computeIfAbsent(name, key -> resolvePublicMethod(targetClass, key));
        return invoke(target, method);
    }

    static Object callPrivate(Object target, String name) {
        Class<?> targetClass = target.getClass();
        Method method = PRIVATE_METHODS.get(targetClass)
            .computeIfAbsent(name, key -> resolvePrivateMethod(targetClass, key));
        return invoke(target, method);
    }

    static void setCount(Object target, String name, int count) {
        Class<?> targetClass = target.getClass();
        Method method = INT_SETTERS.get(targetClass)
            .computeIfAbsent(name, key -> resolveIntSetter(targetClass, key));
        invoke(target, method, count);
    }

    static Object field(Object target, String name) {
        Class<?> targetClass = target.getClass();
        Field field = PUBLIC_FIELDS.get(targetClass)
            .computeIfAbsent(name, key -> resolvePublicField(targetClass, key));
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read storage field: " + targetClass.getName() + "." + name, e);
        }
    }

    static void setField(Object target, String name, Object value) {
        Class<?> targetClass = target.getClass();
        Field field = PUBLIC_FIELDS.get(targetClass)
            .computeIfAbsent(name, key -> resolvePublicField(targetClass, key));
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write storage field: " + targetClass.getName() + "." + name, e);
        }
    }

    private static Method resolvePublicMethod(Class<?> targetClass, String name) {
        try {
            Method method = targetClass.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Missing storage method: " + targetClass.getName() + "." + name, e);
        }
    }

    private static Method resolvePrivateMethod(Class<?> targetClass, String name) {
        for (Class<?> type = targetClass; type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                // Private storage methods belong to the declaring superclass.
            }
        }
        throw new IllegalStateException("Missing storage method: " + targetClass.getName() + "." + name);
    }

    private static Method resolveIntSetter(Class<?> targetClass, String name) {
        try {
            Method method = targetClass.getMethod(name, int.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Missing storage method: " + targetClass.getName() + "." + name, e);
        }
    }

    private static Field resolvePublicField(Class<?> targetClass, String name) {
        try {
            Field field = targetClass.getField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Cannot access storage field: " + targetClass.getName() + "." + name, e);
        }
    }

    private static Object invoke(Object target, Method method, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot call storage method: " + method, e);
        }
    }
}
