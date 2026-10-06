package com.terracraft.geo.world;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Décodeur minimal de tuiles vectorielles Mapbox (MVT 2.1, protobuf), sans dépendance.
 *
 * <p>Les géométries sont rendues en coordonnées de tuile normalisées [0, 1] (avec le léger
 * débordement du tampon de découpe), y vers le bas, exactement comme Web Mercator.
 */
final class MvtDecoder {
    static final int POINT = 1;
    static final int LINE = 2;
    static final int POLYGON = 3;

    record Feature(String layer, int type, Map<String, Object> tags, List<double[]> parts) {
        String string(String key) {
            Object value = tags.get(key);
            return value == null ? "" : value.toString();
        }

        double number(String key, double fallback) {
            Object value = tags.get(key);
            return value instanceof Number n ? n.doubleValue() : fallback;
        }

        boolean bool(String key) {
            Object value = tags.get(key);
            return Boolean.TRUE.equals(value) || (value instanceof Number n && n.intValue() != 0);
        }
    }

    private final byte[] data;
    private int pos;
    private int limit;

    private MvtDecoder(byte[] data) {
        this.data = data;
        this.limit = data.length;
    }

    static List<Feature> decode(byte[] tile) {
        MvtDecoder reader = new MvtDecoder(tile);
        List<Feature> features = new ArrayList<>();
        while (reader.pos < reader.limit) {
            int key = (int) reader.varint();
            if (key >>> 3 == 3 && (key & 7) == 2) {
                int length = (int) reader.varint();
                reader.layer(reader.pos + length, features);
            } else {
                reader.skip(key & 7);
            }
        }
        return features;
    }

    private void layer(int end, List<Feature> out) {
        int outerLimit = limit;
        limit = end;
        String name = "";
        int extent = 4096;
        List<String> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<int[]> featureRanges = new ArrayList<>();
        while (pos < limit) {
            int key = (int) varint();
            int field = key >>> 3;
            if (field == 1 && (key & 7) == 2) {
                name = string();
            } else if (field == 2 && (key & 7) == 2) {
                int length = (int) varint();
                featureRanges.add(new int[]{pos, pos + length});
                pos += length;
            } else if (field == 3 && (key & 7) == 2) {
                keys.add(string());
            } else if (field == 4 && (key & 7) == 2) {
                int length = (int) varint();
                values.add(value(pos + length));
            } else if (field == 5 && (key & 7) == 0) {
                extent = (int) varint();
            } else {
                skip(key & 7);
            }
        }
        // Les features peuvent précéder keys/values dans le flux : on les décode ensuite.
        for (int[] range : featureRanges) {
            pos = range[0];
            limit = range[1];
            Feature feature = feature(name, extent, keys, values);
            if (feature != null) {
                out.add(feature);
            }
        }
        pos = end;
        limit = outerLimit;
    }

    private Feature feature(String layer, int extent, List<String> keys, List<Object> values) {
        int type = 0;
        int[] tags = new int[0];
        int[] geometry = new int[0];
        while (pos < limit) {
            int key = (int) varint();
            int field = key >>> 3;
            if (field == 2 && (key & 7) == 2) {
                tags = packed();
            } else if (field == 3 && (key & 7) == 0) {
                type = (int) varint();
            } else if (field == 4 && (key & 7) == 2) {
                geometry = packed();
            } else {
                skip(key & 7);
            }
        }
        if (type == POINT || type == 0) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < tags.length; i += 2) {
            if (tags[i] < keys.size() && tags[i + 1] < values.size()) {
                map.put(keys.get(tags[i]), values.get(tags[i + 1]));
            }
        }
        return new Feature(layer, type, map, geometry(geometry, extent));
    }

    /** Commandes MoveTo/LineTo/ClosePath → parties (lignes ou anneaux fermés). */
    private static List<double[]> geometry(int[] commands, int extent) {
        List<double[]> parts = new ArrayList<>();
        double[] current = null;
        int n = 0;
        int x = 0;
        int y = 0;
        int i = 0;
        while (i < commands.length) {
            int command = commands[i] & 7;
            int count = commands[i] >>> 3;
            i++;
            if (command == 7) {
                if (current != null && n >= 2) {
                    current = ensure(current, n + 2);
                    current[n++] = current[0];
                    current[n++] = current[1];
                }
                continue;
            }
            for (int c = 0; c < count && i + 1 < commands.length; c++) {
                x += zigzag(commands[i++]);
                y += zigzag(commands[i++]);
                if (command == 1) {
                    if (current != null && n >= 4) {
                        parts.add(Arrays.copyOf(current, n));
                    }
                    current = new double[16];
                    n = 0;
                }
                if (current != null) {
                    current = ensure(current, n + 2);
                    current[n++] = (double) x / extent;
                    current[n++] = (double) y / extent;
                }
            }
        }
        if (current != null && n >= 4) {
            parts.add(Arrays.copyOf(current, n));
        }
        return parts;
    }

    private static double[] ensure(double[] array, int size) {
        return size <= array.length ? array : Arrays.copyOf(array, Math.max(size, array.length * 2));
    }

    private static int zigzag(int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    private Object value(int end) {
        Object result = null;
        while (pos < end) {
            int key = (int) varint();
            switch (key >>> 3) {
                case 1 -> result = string();
                case 2 -> result = Float.intBitsToFloat(fixed32());
                case 3 -> result = Double.longBitsToDouble(fixed64());
                case 4, 5 -> result = varint();
                case 6 -> {
                    long v = varint();
                    result = (v >>> 1) ^ -(v & 1);
                }
                case 7 -> result = varint() != 0;
                default -> skip(key & 7);
            }
        }
        pos = end;
        return result;
    }

    private int[] packed() {
        int length = (int) varint();
        int end = pos + length;
        int[] out = new int[Math.max(4, length)];
        int n = 0;
        while (pos < end) {
            out[n++] = (int) varint();
        }
        return Arrays.copyOf(out, n);
    }

    private String string() {
        int length = (int) varint();
        String s = new String(data, pos, length, StandardCharsets.UTF_8);
        pos += length;
        return s;
    }

    private long varint() {
        long result = 0;
        int shift = 0;
        while (true) {
            byte b = data[pos++];
            result |= (long) (b & 0x7F) << shift;
            if (b >= 0) {
                return result;
            }
            shift += 7;
        }
    }

    private int fixed32() {
        int v = (data[pos] & 0xFF) | (data[pos + 1] & 0xFF) << 8 | (data[pos + 2] & 0xFF) << 16 | (data[pos + 3] & 0xFF) << 24;
        pos += 4;
        return v;
    }

    private long fixed64() {
        long lo = fixed32() & 0xFFFFFFFFL;
        long hi = fixed32() & 0xFFFFFFFFL;
        return lo | hi << 32;
    }

    private void skip(int wireType) {
        switch (wireType) {
            case 0 -> varint();
            case 1 -> pos += 8;
            case 2 -> pos += (int) varint();
            case 5 -> pos += 4;
            default -> throw new IllegalStateException("type protobuf inconnu " + wireType);
        }
    }
}
