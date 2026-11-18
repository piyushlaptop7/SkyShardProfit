package com.skyshardprofit.gui;

import com.skyshardprofit.data.FusionOpportunity;
import com.skyshardprofit.data.ShardFusionService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ShardProfitScreen extends Screen {
    private static final int PANEL_WIDTH = 800;
    private static final int PANEL_HEIGHT = 440;
    private static final int LIST_TOP = 86;
    private static final int ROW_HEIGHT = 34;
    private static final int ROWS_PER_PAGE = 9;

    private final ShardFusionService service;
    private EditBox searchBox;
    private Button modeButton;
    private Button taxButton;
    private String searchText = "";
    private boolean profitableOnly = true;
    private int page = 0;
    private int panelX;
    private int panelY;
    private List<FusionOpportunity> filtered = List.of();
    private List<FusionOpportunity> lastSource = List.of();
    private FusionOpportunity selected;

    public ShardProfitScreen(ShardFusionService service) {
        super(Component.literal("SkyShard Profit"));
        this.service = service;
    }

    @Override
    protected void init() {
        panelX = Math.max(8, (this.width - PANEL_WIDTH) / 2);
        panelY = Math.max(8, (this.height - PANEL_HEIGHT) / 2);

        searchBox = new EditBox(this.font, panelX + 12, panelY + 43, 150, 20, Component.literal("Search shards"));
        searchBox.setHint(Component.literal("Search shard or input..."));
        searchBox.setMaxLength(48);
        searchBox.setResponder(value -> {
            searchText = value;
            page = 0;
            rebuildFiltered();
        });
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> service.refreshPrices())
                .bounds(panelX + 170, panelY + 43, 62, 20).build());
        modeButton = this.addRenderableWidget(Button.builder(modeLabel(), button -> {
            service.cycleTradeMode();
            button.setMessage(modeLabel());
            page = 0;
        }).bounds(panelX + 238, panelY + 43, 174, 20).build());
        taxButton = this.addRenderableWidget(Button.builder(taxLabel(), button -> {
            service.cycleTaxPreset();
            button.setMessage(taxLabel());
        }).bounds(panelX + 417, panelY + 43, 62, 20).build());
        this.addRenderableWidget(Button.builder(profitFilterLabel(), button -> {
            profitableOnly = !profitableOnly;
            page = 0;
            rebuildFiltered();
            button.setMessage(profitFilterLabel());
        }).bounds(panelX + 486, panelY + 43, 92, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("←"), button -> {
            page = Math.max(0, page - 1);
            updateSelectionForPage();
        }).bounds(panelX + 584, panelY + 43, 28, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("→"), button -> {
            int maxPage = Math.max(0, (filtered.size() - 1) / ROWS_PER_PAGE);
            page = Math.min(maxPage, page + 1);
            updateSelectionForPage();
        }).bounds(panelX + 618, panelY + 43, 28, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
                .bounds(panelX + 658, panelY + 43, 72, 20).build());

        rebuildFiltered();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int right = panelX + PANEL_WIDTH;
        int bottom = panelY + PANEL_HEIGHT;
        graphics.fill(panelX, panelY, right, bottom, 0xF20C121B);
        graphics.fill(panelX, panelY, right, panelY + 31, 0xFF172432);
        graphics.fill(panelX + 490, panelY + 76, panelX + 491, bottom - 14, 0xFF354555);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
        syncResults();

        graphics.text(this.font, "SKYSHARD PROFIT", panelX + 14, panelY + 11, 0xFFFFFFFF, true);
        graphics.text(this.font, "Hypixel SkyBlock  •  Attribute Shard Fusion", panelX + 176, panelY + 11, 0xFFB8C7D6, false);
        String status = service.status();
        graphics.text(this.font, trim(status, 66), panelX + 14, panelY + 72, 0xFF9DB4C8, false);
        graphics.text(this.font, "Shards: " + service.shardCount() + "   Recipes priced: " + service.opportunityCount(),
                panelX + 390, panelY + 72, 0xFF9DB4C8, false);

        drawRecipeRows(graphics);
        drawSelectedRecipe(graphics);

        graphics.text(this.font,
                "Community recipe data • Price estimates only • Confirm the exact output in the Fusion Machine",
                panelX + 14, bottom - 19, 0xFF8798A8, false);

        if (!service.lastError().isBlank()) {
            graphics.text(this.font, "Note: " + trim(service.lastError(), 90), panelX + 14, bottom - 33, 0xFFFFC36B, false);
        }
    }

    private void drawRecipeRows(GuiGraphicsExtractor graphics) {
        int start = page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = start + row;
            int y = panelY + LIST_TOP + row * ROW_HEIGHT;
            if (index >= filtered.size()) break;
            FusionOpportunity opportunity = filtered.get(index);
            boolean isSelected = opportunity == selected;
            int rowColor = isSelected ? 0xFF253C50 : (row % 2 == 0 ? 0xAA17232E : 0xAA111C26);
            graphics.fill(panelX + 12, y, panelX + 475, y + ROW_HEIGHT - 2, rowColor);

            String profit = (opportunity.profit() >= 0 ? "+" : "") + coins(opportunity.profit());
            int profitColor = opportunity.profit() >= 0 ? 0xFF70E09A : 0xFFFF8585;
            graphics.text(this.font, trim(profit, 15), panelX + 20, y + 5, profitColor, true);
            graphics.text(this.font, trim(opportunity.outputName() + " ×" + opportunity.outputAmount(), 29),
                    panelX + 132, y + 5, 0xFFFFFFFF, true);
            String ingredients = opportunity.firstInputAmount() + "× " + opportunity.firstInputName()
                    + "  +  " + opportunity.secondInputAmount() + "× " + opportunity.secondInputName();
            graphics.text(this.font, trim(ingredients, 58), panelX + 20, y + 19, 0xFFB8C7D6, false);
        }

        if (filtered.isEmpty()) {
            graphics.text(this.font, service.isReady() ? "No matching recipes. Try another search or disable Profitable only." :
                    "Waiting for fusion recipes and Bazaar prices...", panelX + 22, panelY + LIST_TOP + 16, 0xFFD4DDE6, false);
        }
        int pageCount = Math.max(1, (filtered.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        graphics.text(this.font, "Page " + (page + 1) + " / " + pageCount, panelX + 365, panelY + 397, 0xFF9DB4C8, false);
    }

    private void drawSelectedRecipe(GuiGraphicsExtractor graphics) {
        int x = panelX + 507;
        int y = panelY + 91;
        FusionOpportunity item = selected;
        graphics.text(this.font, "FUSION DETAILS", x, y, 0xFF7DD9FF, true);
        y += 23;
        if (item == null) {
            graphics.text(this.font, "Select a fusion on the left.", x, y, 0xFFB8C7D6, false);
            return;
        }

        graphics.text(this.font, "OUTPUT", x, y, 0xFFB7C5D2, true);
        y += 13;
        graphics.text(this.font, item.outputAmount() + "× " + item.outputName() + " (" + item.outputRarity() + ")", x, y, 0xFFFFFFFF, true);
        y += 25;

        graphics.text(this.font, "INPUT SLOT 1", x, y, 0xFFB7C5D2, true);
        y += 13;
        graphics.text(this.font, item.firstInputAmount() + "× " + item.firstInputName(), x, y, 0xFFFFFFFF, false);
        y += 13;
        graphics.text(this.font, "Unit price: " + coins(item.firstInputUnitPrice()), x, y, 0xFFB8C7D6, false);
        y += 13;
        graphics.text(this.font, "Stack cost: " + coins(item.firstInputCost()), x, y, 0xFFB8C7D6, false);
        y += 22;

        graphics.text(this.font, "INPUT SLOT 2", x, y, 0xFFB7C5D2, true);
        y += 13;
        graphics.text(this.font, item.secondInputAmount() + "× " + item.secondInputName(), x, y, 0xFFFFFFFF, false);
        y += 13;
        graphics.text(this.font, "Unit price: " + coins(item.secondInputUnitPrice()), x, y, 0xFFB8C7D6, false);
        y += 13;
        graphics.text(this.font, "Stack cost: " + coins(item.secondInputCost()), x, y, 0xFFB8C7D6, false);
        y += 22;

        graphics.text(this.font, "TOTAL INPUT COST", x, y, 0xFFB7C5D2, true);
        y += 14;
        graphics.text(this.font, coins(item.inputCost()), x, y, 0xFFFFFFFF, true);
        y += 22;
        graphics.text(this.font, "Output sale before tax: " + coins(item.outputGross()), x, y, 0xFFB8C7D6, false);
        y += 14;
        graphics.text(this.font, "Output after tax: " + coins(item.outputNet()), x, y, 0xFFB8C7D6, false);
        y += 22;
        graphics.text(this.font, "ESTIMATED PROFIT", x, y, 0xFFB7C5D2, true);
        y += 15;
        int profitColor = item.profit() >= 0 ? 0xFF70E09A : 0xFFFF8585;
        graphics.text(this.font, (item.profit() >= 0 ? "+" : "") + coins(item.profit()), x, y, profitColor, true);
        graphics.text(this.font, String.format(Locale.US, "ROI %.2f%%", item.roiPercent()), x + 156, y, 0xFFFFFFFF, true);
        y += 24;

        graphics.text(this.font, "Put INPUT SLOT 1 first.", x, y, 0xFFFFD27A, true);
        y += 13;
        graphics.text(this.font, "The Fusion Machine lets you choose", x, y, 0xFFB8C7D6, false);
        y += 12;
        graphics.text(this.font, "from its available outputs; this mod", x, y, 0xFFB8C7D6, false);
        y += 12;
        graphics.text(this.font, "does not perform the fusion for you.", x, y, 0xFFB8C7D6, false);
    }

    private void syncResults() {
        List<FusionOpportunity> current = service.opportunities();
        if (current != lastSource) {
            lastSource = current;
            rebuildFiltered();
        }
    }

    private void rebuildFiltered() {
        List<FusionOpportunity> source = service.opportunities();
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        List<FusionOpportunity> result = new ArrayList<>();
        for (FusionOpportunity item : source) {
            if (profitableOnly && item.profit() <= 0) continue;
            if (!query.isEmpty()) {
                String haystack = (item.outputName() + " " + item.firstInputName() + " " + item.secondInputName())
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            result.add(item);
        }
        filtered = List.copyOf(result);
        int maxPage = Math.max(0, (filtered.size() - 1) / ROWS_PER_PAGE);
        page = Math.min(page, maxPage);
        updateSelectionForPage();
    }

    private void updateSelectionForPage() {
        int start = page * ROWS_PER_PAGE;
        if (filtered.isEmpty()) {
            selected = null;
        } else if (selected == null || !filtered.subList(start, Math.min(filtered.size(), start + ROWS_PER_PAGE)).contains(selected)) {
            selected = filtered.get(start);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int listX = panelX + 12;
            int listY = panelY + LIST_TOP;
            if (mouseX >= listX && mouseX <= panelX + 475 && mouseY >= listY
                    && mouseY < listY + ROWS_PER_PAGE * ROW_HEIGHT) {
                int row = (int) ((mouseY - listY) / ROW_HEIGHT);
                int index = page * ROWS_PER_PAGE + row;
                if (index >= 0 && index < filtered.size()) {
                    selected = filtered.get(index);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Component modeLabel() {
        return Component.literal(service.tradeMode().label());
    }

    private Component taxLabel() {
        String percentage = String.format(Locale.US, "%.3f", service.taxRate() * 100.0)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
        return Component.literal("Tax " + percentage + "%");
    }

    private Component profitFilterLabel() {
        return Component.literal("Profit only: " + (profitableOnly ? "ON" : "OFF"));
    }

    private static String coins(double value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(Math.round(value)) + " coins";
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }
}
