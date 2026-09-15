package dev.longyu.legacyforgebridge.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Reflection adapter for ViaVersion's runtime-relocated Gson classes.
 *
 * <p>ViaFabricPlus ships ViaVersion with Gson relocated under
 * {@code com.viaversion.viaversion.libs.gson}. The compile-time ViaVersion API exposes the normal
 * {@code com.google.gson} package, so Mixin callback descriptors must not statically reference
 * either JsonObject class. This adapter intentionally accepts {@link Object} and calls only the
 * stable Gson JsonObject/JsonElement methods we need.</p>
 */
public final class ViaJsonObjectAccess {
    private static final ClassValue<JsonObjectMethods> OBJECT_METHODS = new ClassValue<>() {
        @Override
        protected JsonObjectMethods computeValue(Class<?> type) {
            try {
                return new JsonObjectMethods(
                        type.getMethod("get", String.class),
                        type.getMethod("addProperty", String.class, String.class)
                );
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "ViaVersion JSON object does not expose expected Gson-compatible methods: " + type.getName(),
                        exception
                );
            }
        }
    };

    private static final ClassValue<Method> ELEMENT_GET_AS_STRING = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("getAsString");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "ViaVersion JSON element does not expose getAsString(): " + type.getName(),
                        exception
                );
            }
        }
    };

    private ViaJsonObjectAccess() {
    }

    public static String getStringProperty(Object jsonObject, String property) {
        Objects.requireNonNull(jsonObject, "jsonObject");
        Objects.requireNonNull(property, "property");

        try {
            Object element = OBJECT_METHODS.get(jsonObject.getClass()).get().invoke(jsonObject, property);
            if (element == null) {
                return null;
            }
            Object value = ELEMENT_GET_AS_STRING.get(element.getClass()).invoke(element);
            return value instanceof String string ? string : null;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access ViaVersion relocated Gson methods", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                    "ViaVersion relocated Gson method failed",
                    exception.getCause() != null ? exception.getCause() : exception
            );
        }
    }

    public static void setStringProperty(Object jsonObject, String property, String value) {
        Objects.requireNonNull(jsonObject, "jsonObject");
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(value, "value");

        try {
            OBJECT_METHODS.get(jsonObject.getClass()).addProperty().invoke(jsonObject, property, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access ViaVersion relocated Gson methods", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                    "ViaVersion relocated Gson method failed",
                    exception.getCause() != null ? exception.getCause() : exception
            );
        }
    }

    private record JsonObjectMethods(Method get, Method addProperty) {
    }
}
