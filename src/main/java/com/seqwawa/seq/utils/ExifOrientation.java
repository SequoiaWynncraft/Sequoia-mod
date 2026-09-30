package com.seqwawa.seq.utils;

import java.awt.image.BufferedImage;

/**
 * The way up of a photo, as its EXIF data records it. A phone stores a photo as its
 * sensor saw it and notes how it was held; Discord turns it upright, and so must chat,
 * or a portrait photo lies on its side.
 * <p>
 * Orientations are EXIF's: 1 upright, 2 mirrored, 3 upside down, 4 mirrored upside
 * down, 5 to 8 the same four turned a quarter, which swaps width and height.
 */
final class ExifOrientation {

    static final int UPRIGHT = 1;

    private static final int ORIENTATION_TAG = 0x0112;

    private ExifOrientation() {}

    /** The orientation a JPEG's EXIF data records, or {@link #UPRIGHT} when there is none. */
    static int of(byte[] jpeg) {
        try {
            return read(jpeg);
        } catch (RuntimeException malformed) {
            return UPRIGHT;
        }
    }

    /** Whether turning a picture this way swaps its width and height. */
    static boolean swapsSides(int orientation) {
        return orientation >= 5 && orientation <= 8;
    }

    /** {@code image} turned upright from {@code orientation}. */
    static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation <= UPRIGHT || orientation > 8) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] source = image.getRGB(0, 0, width, height, null, 0, width);
        boolean swap = swapsSides(orientation);
        int outWidth = swap ? height : width;
        int outHeight = swap ? width : height;
        int[] upright = new int[source.length];
        for (int y = 0; y < outHeight; y++) {
            for (int x = 0; x < outWidth; x++) {
                // Which source pixel lands on (x, y) once the picture is turned.
                int sourceX;
                int sourceY;
                switch (orientation) {
                    case 2 -> { sourceX = width - 1 - x; sourceY = y; }
                    case 3 -> { sourceX = width - 1 - x; sourceY = height - 1 - y; }
                    case 4 -> { sourceX = x; sourceY = height - 1 - y; }
                    case 5 -> { sourceX = y; sourceY = x; }
                    case 6 -> { sourceX = y; sourceY = height - 1 - x; }
                    case 7 -> { sourceX = width - 1 - y; sourceY = height - 1 - x; }
                    default -> { sourceX = width - 1 - y; sourceY = x; }
                }
                upright[y * outWidth + x] = source[sourceY * width + sourceX];
            }
        }
        BufferedImage turned = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
        turned.setRGB(0, 0, outWidth, outHeight, upright, 0, outWidth);
        return turned;
    }

    /**
     * Walks a JPEG's segments to its EXIF block, then the first directory of the TIFF
     * structure inside it to the orientation entry.
     */
    private static int read(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return UPRIGHT;
        }
        int offset = 2;
        while (offset + 4 <= jpeg.length && (jpeg[offset] & 0xFF) == 0xFF) {
            int marker = jpeg[offset + 1] & 0xFF;
            int length = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
            if (marker == 0xDA || length < 2) {
                // Image data starts: no EXIF block came before it.
                return UPRIGHT;
            }
            int body = offset + 4;
            if (marker == 0xE1 && body + 6 <= jpeg.length && isExifHeader(jpeg, body)) {
                return orientation(jpeg, body + 6, Math.min(jpeg.length, offset + 2 + length));
            }
            offset += 2 + length;
        }
        return UPRIGHT;
    }

    private static boolean isExifHeader(byte[] bytes, int at) {
        return bytes[at] == 'E' && bytes[at + 1] == 'x' && bytes[at + 2] == 'i' && bytes[at + 3] == 'f'
                && bytes[at + 4] == 0 && bytes[at + 5] == 0;
    }

    private static int orientation(byte[] bytes, int tiff, int end) {
        boolean littleEndian = bytes[tiff] == 'I' && bytes[tiff + 1] == 'I';
        int directory = tiff + readInt(bytes, tiff + 4, littleEndian);
        int entries = readShort(bytes, directory, littleEndian);
        for (int index = 0; index < entries; index++) {
            int entry = directory + 2 + index * 12;
            if (entry + 12 > end) {
                break;
            }
            if (readShort(bytes, entry, littleEndian) == ORIENTATION_TAG) {
                int value = readShort(bytes, entry + 8, littleEndian);
                return value >= 1 && value <= 8 ? value : UPRIGHT;
            }
        }
        return UPRIGHT;
    }

    private static int readShort(byte[] bytes, int at, boolean littleEndian) {
        int first = bytes[at] & 0xFF;
        int second = bytes[at + 1] & 0xFF;
        return littleEndian ? second << 8 | first : first << 8 | second;
    }

    private static int readInt(byte[] bytes, int at, boolean littleEndian) {
        int high = readShort(bytes, littleEndian ? at + 2 : at, littleEndian);
        int low = readShort(bytes, littleEndian ? at : at + 2, littleEndian);
        return high << 16 | low;
    }
}
