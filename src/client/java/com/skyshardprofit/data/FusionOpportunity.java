package com.skyshardprofit.data;

public record FusionOpportunity(
        String outputName,
        String outputRarity,
        int outputAmount,
        String firstInputName,
        String secondInputName,
        int firstInputAmount,
        int secondInputAmount,
        double firstInputUnitPrice,
        double secondInputUnitPrice,
        double firstInputCost,
        double secondInputCost,
        double inputCost,
        double outputGross,
        double outputNet,
        double profit,
        double roiPercent,
        String firstProductId,
        String secondProductId,
        String outputProductId
) {
}
