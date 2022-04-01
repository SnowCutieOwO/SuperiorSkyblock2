package com.bgsoftware.superiorskyblock.hooks.provider;

import com.bgsoftware.common.reflection.ReflectMethod;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.hooks.PricesProvider;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.key.dataset.KeyMap;
import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopItem;
import net.brcdev.shopgui.shop.ShopManager;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Set;

public final class PricesProvider_ShopGUIPlus implements PricesProvider {

    private static final ReflectMethod<Set<Shop>> GET_SHOPS_METHOD = new ReflectMethod<>(ShopManager.class, Set.class, "getShops");

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();
    private static final ShopGuiPlugin shopPlugin = ShopGuiPlugin.getInstance();
    private static final KeyMap<Double> cachedPrices = new KeyMap<>();

    public PricesProvider_ShopGUIPlus() {
        SuperiorSkyblockPlugin.log("Using ShopGUIPlus as a prices provider.");
    }

    @Override
    public BigDecimal getPrice(Key key) {
        double price = cachedPrices.getOrDefault(key, 0D);

        if (price == 0) {
            for (Shop shop : getShops()) {
                for (ShopItem shopItem : shop.getShopItems()) {
                    if (Key.of(shopItem.getItem()).equals(key)) {
                        double shopPrice;

                        switch (plugin.getSettings().getSyncWorth()) {
                            case BUY:
                                //noinspection deprecation
                                shopPrice = shopItem.getBuyPriceForAmount(1);
                                break;
                            case SELL:
                                //noinspection deprecation
                                shopPrice = shopItem.getSellPriceForAmount(1);
                                break;
                            default:
                                shopPrice = 0;
                                break;
                        }

                        if (shopPrice > price) {
                            price = shopPrice;
                            cachedPrices.put(key, price);
                        }
                    }
                }
            }
        }

        return BigDecimal.valueOf(price);
    }

    @Override
    public Key getBlockKey(Key blockKey) {
        return cachedPrices.getKey((com.bgsoftware.superiorskyblock.key.Key) blockKey, null);
    }

    private Collection<Shop> getShops() {
        return GET_SHOPS_METHOD.isValid() ? GET_SHOPS_METHOD.invoke(shopPlugin.getShopManager()) :
                shopPlugin.getShopManager().shops.values();
    }

}
