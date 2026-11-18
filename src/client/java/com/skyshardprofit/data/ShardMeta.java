package com.skyshardprofit.data;

import java.util.List;

public record ShardMeta(
        String code,
        String name,
        String rarity,
        String category,
        List<String> families,
        int fuseAmount
) {
    public ShardMeta {
        families = List.copyOf(families);
    }
}
