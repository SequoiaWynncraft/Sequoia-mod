package com.seqwawa.seq.utils.rendering;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/** Owns backend initialization, frame boundaries, and backend resources. */
public interface UiRenderBackend extends AutoCloseable {
    boolean initialize();

    boolean isAvailable();

    void renderFrame(UiRenderMetrics metrics, List<Consumer<UiCanvas>> commands);

    boolean registerFont(String name, ByteBuffer data);

    boolean registerFont(String name, String filePath);

    UiTextMetrics measureText(String text, String font, float size);

    UiImage createImage(ByteBuffer data, boolean nearestNeighbor);

    void deleteImage(UiImage image);

    @Override
    void close();
}
