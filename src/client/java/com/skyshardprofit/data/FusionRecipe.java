package com.skyshardprofit.data;

public record FusionRecipe(
        String outputCode,
        int outputAmount,
        String firstInputCode,
        String secondInputCode
) {
}
