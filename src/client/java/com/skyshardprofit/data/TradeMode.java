package com.skyshardprofit.data;

public enum TradeMode {
    BUY_ORDER_TO_SELL_OFFER("Buy Order → Sell Offer"),
    INSTANT_BUY_TO_INSTANT_SELL("Instant Buy → Instant Sell");

    private final String label;

    TradeMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public TradeMode next() {
        return this == BUY_ORDER_TO_SELL_OFFER
                ? INSTANT_BUY_TO_INSTANT_SELL
                : BUY_ORDER_TO_SELL_OFFER;
    }
}
