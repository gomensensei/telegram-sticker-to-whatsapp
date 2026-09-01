package com.tool48.tgwabridge;

import java.util.ArrayList;
import java.util.List;

final class PackPartitioner {
    private PackPartitioner() {
    }

    static List<Integer> sizes(int count) {
        if (count < 1) {
            throw new IllegalArgumentException(
                "A sticker pack needs at least 1 sticker."
            );
        }
        List<Integer> result = new ArrayList<>();
        int remaining = count;
        while (remaining > 0) {
            int current = Math.min(30, remaining);
            result.add(current);
            remaining -= current;
        }
        return result;
    }
}
