package com.tool48.tgwabridge;

import java.util.ArrayList;
import java.util.List;

final class PackPartitioner {
    private PackPartitioner() {
    }

    static List<Integer> sizes(int count) {
        if (count < 3) {
            throw new IllegalArgumentException(
                "A WhatsApp pack needs at least 3 stickers."
            );
        }
        List<Integer> result = new ArrayList<>();
        int parts = (count + 29) / 30;
        int remaining = count;
        for (int index = 0; index < parts; index++) {
            int partsAfter = parts - index - 1;
            int current = Math.min(30, remaining - partsAfter * 3);
            result.add(current);
            remaining -= current;
        }
        return result;
    }
}
