package com.cafe.tablet;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.*;

import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

@Mod("cafetablet")
public class CafeTablet {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "cafetablet");
    public static final RegistryObject<Item> TABLET = ITEMS.register("tablet", () -> new TabletItem(new Item.Properties().stacksTo(1)));

    public CafeTablet() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(TABLET);
        });
        Data.load();
    }

    /** Sağ tık -> tablet ekranı */
    public static class TabletItem extends Item {
        public TabletItem(Properties p) { super(p); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level lvl, Player pl, net.minecraft.world.InteractionHand h) {
            if (lvl.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Minecraft.getInstance().setScreen(new TabletScreen()));
            return InteractionResultHolder.sidedSuccess(pl.getItemInHand(h), lvl.isClientSide);
        }
    }

    public static class Order { String customer; List<String> lines = new ArrayList<>(); String time; }

    /** Veri + sipariş üretici (ekran kapalıyken de çalışır) */
    @Mod.EventBusSubscriber(modid = "cafetablet", value = Dist.CLIENT)
    public static class Data {
        static List<String> customers = new ArrayList<>(List.of("Süleyman Abi", "Ayşe Hanım"));
        static List<String> foods = new ArrayList<>(List.of("Dürüm", "Karpuzlu İçecek", "Ayran"));
        static List<Order> pending = new ArrayList<>(), history = new ArrayList<>();
        static int min = 10, max = 20, ticks = 200, total = 200;
        static final Random R = new Random();
        static final Gson G = new GsonBuilder().setPrettyPrinting().create();
        static Path file() { return FMLPaths.CONFIGDIR.get().resolve("cafetablet.json"); }

        static void load() {
            try {
                if (!Files.exists(file())) return;
                JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
                customers = G.fromJson(o.get("customers"), new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                foods = G.fromJson(o.get("foods"), new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                history = G.fromJson(o.get("history"), new com.google.gson.reflect.TypeToken<List<Order>>(){}.getType());
                min = o.get("min").getAsInt(); max = o.get("max").getAsInt();
            } catch (Exception ignored) {}
        }
        static void save() {
            try {
                JsonObject o = new JsonObject();
                o.add("customers", G.toJsonTree(customers)); o.add("foods", G.toJsonTree(foods));
                o.add("history", G.toJsonTree(history)); o.addProperty("min", min); o.addProperty("max", max);
                Files.writeString(file(), G.toJson(o));
            } catch (Exception ignored) {}
        }
        static void generate() {
            if (customers.isEmpty() || foods.isEmpty()) return;
            Order o = new Order();
            o.customer = customers.get(R.nextInt(customers.size()));
            int n = 1 + R.nextInt(Math.min(2, foods.size()));
            List<String> pool = new ArrayList<>(foods); Collections.shuffle(pool, R);
            for (int i = 0; i < n; i++) o.lines.add((1 + R.nextInt(20)) + "x " + pool.get(i));
            pending.add(o);
        }
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) return;
            if (--ticks <= 0) { generate(); total = ticks = (min + R.nextInt(Math.max(1, max - min + 1))) * 20; }
        }
    }

    /** Modern beyaz arayüz: pill sekmeler, kart grid, ilerleme çubuğu */
    public static class TabletScreen extends Screen {
        static final int W = 420, H = 230, BG = 0xFFFFFFFF, CARD = 0xFFF4F5F7, INK = 0xFF111827, MU = 0xFF8B93A1, AC = 0xFF5B5BF0, OK = 0xFF16A34A;
        int tab = 0, L, T;
        EditBox cBox, fBox, minBox, maxBox;
        public TabletScreen() { super(Component.literal("Kafe Tablet")); }
        @Override protected void init() { build(); }

        void build() {
            clearWidgets();
            L = (width - W) / 2; T = (height - H) / 2;
            if (tab == 2) {
                int x = L + 16;
                cBox = new EditBox(font, x, T + 64, 130, 18, Component.literal("Kişi"));
                fBox = new EditBox(font, x, T + 104, 130, 18, Component.literal("Yemek"));
                minBox = new EditBox(font, x, T + 144, 40, 18, Component.literal("min")); minBox.setValue("" + Data.min);
                maxBox = new EditBox(font, x + 46, T + 144, 40, 18, Component.literal("max")); maxBox.setValue("" + Data.max);
                addRenderableWidget(cBox); addRenderableWidget(fBox); addRenderableWidget(minBox); addRenderableWidget(maxBox);
                addRenderableWidget(Button.builder(Component.literal("+ Kişi"), b -> add(Data.customers, cBox)).bounds(x + 134, T + 63, 48, 20).build());
                addRenderableWidget(Button.builder(Component.literal("- Kişi"), b -> rm(Data.customers)).bounds(x + 184, T + 63, 48, 20).build());
                addRenderableWidget(Button.builder(Component.literal("+ Yemek"), b -> add(Data.foods, fBox)).bounds(x + 134, T + 103, 48, 20).build());
                addRenderableWidget(Button.builder(Component.literal("- Yemek"), b -> rm(Data.foods)).bounds(x + 184, T + 103, 48, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Kaydet"), b -> {
                    try { int a = Math.max(1, Integer.parseInt(minBox.getValue())), c = Math.max(a, Integer.parseInt(maxBox.getValue()));
                        Data.min = a; Data.max = c; Data.save(); } catch (NumberFormatException ignored) {}
                }).bounds(x + 92, T + 143, 60, 20).build());
            }
        }
        void add(List<String> l, EditBox b) { String s = b.getValue().trim(); if (!s.isEmpty()) { l.add(s); b.setValue(""); Data.save(); } }
        void rm(List<String> l) { if (!l.isEmpty()) { l.remove(l.size() - 1); Data.save(); } }

        /** köşeleri yumuşatılmış dolgu */
        void round(GuiGraphics g, int x, int y, int w, int h, int c) { g.fill(x + 1, y, x + w - 1, y + h, c); g.fill(x, y + 1, x + w, y + h - 1, c); }
        void avatar(GuiGraphics g, String n, int x, int y) {
            round(g, x, y, 20, 20, 0xFF000000 | ((n.hashCode() * 0x9E3779B1) & 0x7F7F7F) | 0x303030);
            g.drawString(font, n.substring(0, 1), x + 7, y + 6, 0xFFFFFFFF);
        }
        String cut(String s, int w) { return font.plainSubstrByWidth(s, w); }
        int cardX(int i) { return L + 14 + (i % 2) * 196; }
        int cardY(int i) { return T + 44 + (i / 2) * 54; }

        @Override public boolean mouseClicked(double mx, double my, int btn) {
            for (int i = 0; i < 3; i++)
                if (mx >= L + 110 + i * 70 && mx < L + 174 + i * 70 && my >= T + 10 && my < T + 28) { tab = i; build(); return true; }
            if (tab == 0)
                for (int i = 0; i < Math.min(6, Data.pending.size()); i++) {
                    int bx = cardX(i) + 140, by = cardY(i) + 28;
                    if (mx >= bx && mx < bx + 40 && my >= by && my < by + 16) {
                        Order o = Data.pending.remove(i);
                        o.time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        Data.history.add(o); Data.save(); return true;
                    }
                }
            return super.mouseClicked(mx, my, btn);
        }

        @Override public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g);
            g.fill(L - 3, T - 3, L + W + 3, T + H + 3, 0xFFE5E7EB);
            g.fill(L, T, L + W, T + H, BG);
            g.drawString(font, "\u2615 Kafe", L + 14, T + 15, INK);
            String[] names = {"Siparişler", "Geçmiş", "Ayarlar"};
            for (int i = 0; i < 3; i++) {
                int x = L + 110 + i * 70;
                if (i == tab) round(g, x, T + 10, 64, 18, AC);
                g.drawCenteredString(font, names[i], x + 32, T + 15, i == tab ? 0xFFFFFFFF : MU);
            }
            int bw = (int) (70f * (1f - (float) Data.ticks / Math.max(1, Data.total)));
            g.drawString(font, "Sonraki", L + W - 112, T + 15, MU);
            g.fill(L + W - 76, T + 17, L + W - 6, T + 21, 0xFFE5E7EB);
            g.fill(L + W - 76, T + 17, L + W - 76 + bw, T + 21, AC);

            if (tab == 0) {
                if (Data.pending.isEmpty()) g.drawCenteredString(font, "Yeni sipariş bekleniyor...", L + W / 2, T + 100, MU);
                for (int i = 0; i < Math.min(6, Data.pending.size()); i++) {
                    Order o = Data.pending.get(i); int x = cardX(i), y = cardY(i);
                    round(g, x, y, 188, 48, CARD);
                    avatar(g, o.customer, x + 8, y + 7);
                    g.drawString(font, cut(o.customer, 140), x + 34, y + 7, INK);
                    g.drawString(font, cut(String.join(" + ", o.lines), 144), x + 34, y + 19, AC);
                    round(g, x + 140, y + 28, 40, 16, OK);
                    g.drawCenteredString(font, "\u2714", x + 160, y + 32, 0xFFFFFFFF);
                }
                if (Data.pending.size() > 6) g.drawString(font, "+" + (Data.pending.size() - 6) + " sipariş daha", L + 14, T + H - 14, MU);
            } else if (tab == 1) {
                if (Data.history.isEmpty()) g.drawCenteredString(font, "Henüz teslim edilen sipariş yok.", L + W / 2, T + 100, MU);
                for (int i = 0; i < Math.min(6, Data.history.size()); i++) {
                    Order o = Data.history.get(Data.history.size() - 1 - i); int x = cardX(i), y = cardY(i);
                    round(g, x, y, 188, 48, CARD);
                    avatar(g, o.customer, x + 8, y + 7);
                    g.drawString(font, "\u2714 " + cut(o.customer, 100), x + 34, y + 7, OK);
                    g.drawString(font, o.time == null ? "" : o.time, x + 140, y + 7, MU);
                    g.drawString(font, cut(String.join(" + ", o.lines), 144), x + 34, y + 25, INK);
                }
            } else {
                g.drawString(font, "Yeni kişi", L + 16, T + 52, MU);
                g.drawString(font, "Yeni yemek", L + 16, T + 92, MU);
                g.drawString(font, "Sipariş süresi (min / max sn)", L + 16, T + 132, MU);
                g.drawString(font, "KİŞİLER", L + 260, T + 44, MU);
                g.drawWordWrap(font, Component.literal(String.join(", ", Data.customers)), L + 260, T + 56, 150, INK);
                g.drawString(font, "YEMEKLER", L + 260, T + 110, MU);
                g.drawWordWrap(font, Component.literal(String.join(", ", Data.foods)), L + 260, T + 122, 150, INK);
            }
            super.render(g, mx, my, pt);
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
