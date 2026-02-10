package org.sequoia.seq.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class Setting<T> {
    private final String name;
    private final String category;
    @Setter
    private T value;
    private final T defaultValue;

    protected Setting(String name, String category, T defaultValue) {
        this.name = name;
        this.category = category;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public void reset() {
        this.value = defaultValue;
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

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getValue());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                setValue(element.getAsInt());
            }
        }
    }

    public static class DoubleSetting extends Setting<Double> {
        @Getter private final double min;
        @Getter private final double max;
        @Getter private final double increment;

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

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getValue());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                setValue(element.getAsDouble());
            }
        }
    }

    public static class FloatSetting extends Setting<Float> {
        @Getter private final float min;
        @Getter private final float max;
        @Getter private final float increment;

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

        @Override
        public JsonElement serialize() {
            return new JsonPrimitive(getValue());
        }

        @Override
        public void deserialize(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                setValue(element.getAsFloat());
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
