package com.seqwawa.seq.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Objects;
import java.util.function.BooleanSupplier;
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
