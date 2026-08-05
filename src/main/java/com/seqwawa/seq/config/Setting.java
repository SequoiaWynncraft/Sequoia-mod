package com.seqwawa.seq.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class Setting<T> {
    private final String name;
    private final String category;
    @Setter
    private T value;
    private final T defaultValue;
    private BooleanSupplier visibilityCondition = () -> true;
    private BooleanSupplier enabledCondition = () -> true;
    private String displayName;
    private String description;
    private String section;
    private Setting<?> parentSetting;

    protected Setting(String name, String category, T defaultValue) {
        this.name = name;
        this.category = category;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public void reset() {
        this.value = defaultValue;
    }

    public void setVisibilityCondition(BooleanSupplier visibilityCondition) {
        this.visibilityCondition = Objects.requireNonNull(visibilityCondition);
    }

    public boolean isVisible() {
        return visibilityCondition.getAsBoolean();
    }

    /** Keeps a setting visible while making its control unavailable. */
    public void setEnabledCondition(BooleanSupplier enabledCondition) {
        this.enabledCondition = Objects.requireNonNull(enabledCondition);
    }

    /**
     * A parent establishes both visual indentation and an interaction dependency. A
     * Boolean child is enabled only while its complete parent chain is enabled and on.
     */
    public void setParentSetting(Setting<?> parentSetting) {
        Objects.requireNonNull(parentSetting, "parentSetting");
        for (Setting<?> cursor = parentSetting; cursor != null; cursor = cursor.parentSetting) {
            if (cursor == this) {
                throw new IllegalArgumentException("Setting parent relationships cannot contain a cycle");
            }
        }
        this.parentSetting = parentSetting;
    }

    public boolean isEnabled() {
        if (!enabledCondition.getAsBoolean()) {
            return false;
        }
        if (parentSetting == null) {
            return true;
        }
        if (!parentSetting.isEnabled()) {
            return false;
        }
        return !(parentSetting instanceof BooleanSetting parent) || Boolean.TRUE.equals(parent.getValue());
    }

    /** Number of parent rows this setting should be indented beneath. */
    public int getIndentLevel() {
        int depth = 0;
        for (Setting<?> cursor = parentSetting; cursor != null; cursor = cursor.parentSetting) {
            depth++;
        }
        return depth;
    }

    /** Human-facing metadata used by the settings screen and search. */
    public void setPresentation(String displayName, String description, String section) {
        this.displayName = normalizePresentationText(displayName);
        this.description = normalizePresentationText(description);
        this.section = normalizePresentationText(section);
    }

    private static String normalizePresentationText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public abstract JsonElement serialize();

    public abstract void deserialize(JsonElement element);

    // --- Subclasses ---

    public static class BooleanSetting extends Setting<Boolean> {
        public BooleanSetting(String name, String category, boolean defaultValue) {
            super(name, category, defaultValue);
        }

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getValue());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                setValue(element.getAsBoolean());
            }
        }
    }

    public static class IntSetting extends Setting<Integer> {
        @Getter private final int min;
        @Getter private final int max;
        @Getter private final int increment;
        private boolean allowOutOfRangeManualInput;

        public IntSetting(String name, String category, int defaultValue, int min, int max) {
            this(name, category, defaultValue, min, max, 1);
        }

        public IntSetting(String name, String category, int defaultValue, int min, int max, int increment) {
            super(name, category, defaultValue);
            this.min = min;
            this.max = max;
            this.increment = increment;
        }

        @Override
        public void setValue(Integer value) {
            super.setValue(Math.max(min, Math.min(max, value)));
        }

        public void setValueFromManualInput(Integer value) {
            if (allowOutOfRangeManualInput) {
                super.setValue(value);
            } else {
                setValue(value);
            }
        }

        public IntSetting allowOutOfRangeManualInput() {
            allowOutOfRangeManualInput = true;
            return this;
        }

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getValue());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                int value = element.getAsInt();
                if (allowOutOfRangeManualInput) {
                    setValueFromManualInput(value);
                } else {
                    setValue(value);
                }
            }
        }
    }

    public static class DoubleSetting extends Setting<Double> {
        @Getter private final double min;
        @Getter private final double max;
        @Getter private final double increment;
        private boolean allowOutOfRangeManualInput;

        public DoubleSetting(String name, String category, double defaultValue, double min, double max) {
            this(name, category, defaultValue, min, max, 0.1);
        }

        public DoubleSetting(String name, String category, double defaultValue, double min, double max, double increment) {
            super(name, category, defaultValue);
            this.min = min;
            this.max = max;
            this.increment = increment;
        }

        @Override
        public void setValue(Double value) {
            super.setValue(Math.max(min, Math.min(max, value)));
        }

        public void setValueFromManualInput(Double value) {
            if (allowOutOfRangeManualInput) {
                super.setValue(value);
            } else {
                setValue(value);
            }
        }

        public DoubleSetting allowOutOfRangeManualInput() {
            allowOutOfRangeManualInput = true;
            return this;
        }

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getValue());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                double value = element.getAsDouble();
                if (allowOutOfRangeManualInput) {
                    setValueFromManualInput(value);
                } else {
                    setValue(value);
                }
            }
        }
    }

    public static class FloatSetting extends Setting<Float> {
        @Getter private final float min;
        @Getter private final float max;
        @Getter private final float increment;
        private boolean allowOutOfRangeManualInput;

        public FloatSetting(String name, String category, float defaultValue, float min, float max) {
            this(name, category, defaultValue, min, max, 0.1f);
        }

        public FloatSetting(String name, String category, float defaultValue, float min, float max, float increment) {
            super(name, category, defaultValue);
            this.min = min;
            this.max = max;
            this.increment = increment;
        }

        @Override
        public void setValue(Float value) {
            super.setValue(Math.max(min, Math.min(max, value)));
        }

        public void setValueFromManualInput(Float value) {
            if (allowOutOfRangeManualInput) {
                super.setValue(value);
            } else {
                setValue(value);
            }
        }

        public FloatSetting allowOutOfRangeManualInput() {
            allowOutOfRangeManualInput = true;
            return this;
        }

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getValue());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                float value = element.getAsFloat();
                if (allowOutOfRangeManualInput) {
                    setValueFromManualInput(value);
                } else {
                    setValue(value);
                }
            }
        }
    }

    public static class StringSetting extends Setting<String> {
        public StringSetting(String name, String category, String defaultValue) {
            super(name, category, defaultValue);
        }

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getValue());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                setValue(element.getAsString());
            }
        }
    }

    public static class ColorSetting extends Setting<Integer> {
        private static final int RGB_MASK = 0xFFFFFF;

        public ColorSetting(String name, String category, int defaultRgb) {
            super(name, category, defaultRgb & RGB_MASK);
        }

        @Override
        public void setValue(Integer value) {
            if (value != null) {
                super.setValue(value & RGB_MASK);
            }
        }

        public boolean setHexValue(String hexValue) {
            Integer parsed = parseHex(hexValue);
            if (parsed == null) {
                return false;
            }
            setValue(parsed);
            return true;
        }

        public String getHexValue() {
            return String.format(Locale.ROOT, "#%06X", getValue());
        }

        public static boolean isValidHex(String hexValue) {
            return parseHex(hexValue) != null;
        }

        public static String normalizeHex(String hexValue) {
            Integer parsed = parseHex(hexValue);
            return parsed == null ? null : String.format(Locale.ROOT, "#%06X", parsed);
        }

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getHexValue());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                setHexValue(element.getAsString());
            }
        }

        private static Integer parseHex(String hexValue) {
            if (hexValue == null) {
                return null;
            }

            String normalized = hexValue.trim();
            if (normalized.startsWith("#")) {
                normalized = normalized.substring(1);
            }
            if (normalized.length() != 6) {
                return null;
            }

            for (int i = 0; i < normalized.length(); i++) {
                if (Character.digit(normalized.charAt(i), 16) < 0) {
                    return null;
                }
            }

            return Integer.parseInt(normalized, 16);
        }
    }

    @Getter
    public static class ChoiceSetting extends StringSetting {
        private List<String> options;
        private final Consumer<String> changeListener;

        public ChoiceSetting(
                String name,
                String category,
                String defaultValue,
                List<String> options,
                Consumer<String> changeListener) {
            super(name, category, defaultValue);
            this.options = List.copyOf(options);
            if (this.options.isEmpty() || !this.options.contains(defaultValue)) {
                throw new IllegalArgumentException("Choice setting options must contain the default value");
            }
            this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
        }

        @Override
        public void setValue(String value) {
            if (!options.contains(value)) {
                return;
            }
            super.setValue(value);
            changeListener.accept(value);
        }

        public void addOption(String value) {
            if (value == null || options.contains(value)) {
                return;
            }
            java.util.ArrayList<String> updated = new java.util.ArrayList<>(options);
            updated.add(value);
            options = List.copyOf(updated);
        }

        @Override
        public void reset() {
            setValue(getDefaultValue());
        }
    }

    @Getter
    public static class EnumSetting<E extends Enum<E>> extends Setting<E> {
        private final Class<E> enumClass;

        public EnumSetting(String name, String category, E defaultValue, Class<E> enumClass) {
            super(name, category, defaultValue);
            this.enumClass = enumClass;
        }

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getValue().name());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                try {
                    setValue(Enum.valueOf(enumClass, element.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }
}
