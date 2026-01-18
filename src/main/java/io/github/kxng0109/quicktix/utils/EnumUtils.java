package io.github.kxng0109.quicktix.utils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class EnumUtils {
    /**
     * Converts a String value to a specific Enum type.
     * Checks both the Constant Name (UPCOMING) and the Display Name ("Upcoming").
     *
     * @param enumClass The class of the Enum (e.g., EventStatus.class)
     * @param value     The string value to search for (e.g., "upcoming")
     * @param <T>       The generic Enum type
     * @return The matching Enum constant
     * @throws IllegalArgumentException if no match is found
     */
    public static <T extends Enum<T>> T toEnum(Class<T> enumClass, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value cannot be null or blank for Enum: " + enumClass.getSimpleName());
        }

        // 1. Loop through all constants in the Enum
        for (T constant : enumClass.getEnumConstants()) {

            // Check A: Does it match the code name? (e.g., "UPCOMING")
            if (constant.name().equalsIgnoreCase(value)) {
                return constant;
            }

            // Check B: Does it match the pretty display name? (e.g., "Upcoming")
            // We use a helper method to safely get the display name without forcing an interface
            String displayName = getDisplayName(constant);
            if (displayName != null && displayName.equalsIgnoreCase(value)) {
                return constant;
            }
        }

        // If we get here, nothing matched.
        String allowedValues = Arrays.stream(enumClass.getEnumConstants())
                                     .map(e -> getDisplayName(e) != null ? getDisplayName(e) : e.name())
                                     .collect(Collectors.joining(", "));

        throw new IllegalArgumentException(
                "Invalid value '" + value + "' for " + enumClass.getSimpleName() +
                        ". Allowed values are: [" + allowedValues + "]");
    }

    // Helper: Uses Reflection to call 'getDisplayName()' if it exists
    private static String getDisplayName(Object enumConstant) {
        try {
            Method method = enumConstant.getClass().getMethod("getDisplayName");
            return (String) method.invoke(enumConstant);
        } catch (Exception e) {
            // If the Enum doesn't have a display name, that's fine, just return null.
            return null;
        }
    }
}
