package com.example.damageindicator;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import org.lwjgl.input.Keyboard;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.SteinMod;

/**
 * Example mod for the Stein Loader API (0.1.15, experimental).
 *
 * <ul>
 *   <li><b>Damage numbers</b> over any entity that loses health ({@code onHealthChanged}), drawn in the world
 *       ({@code onRenderWorld}) like a nametag, rising and fading.</li>
 *   <li><b>Last hit you took</b>, as a HUD element the player moves and scales ({@link LastHitElement}).</li>
 *   <li><b>Best armour key</b>: puts on the best armour you carry, one piece per tick ({@code Inventory}).</li>
 *   <li><b>No red flash</b> on others, optional ({@code onEntityHurt}).</li>
 *   <li>A <b>Panel page</b> with a toggle, a colour, a slider and a key picker; settings saved as JSON.</li>
 * </ul>
 */
public final class DamageIndicator implements SteinMod {

    static final String ID = "damageindicator";

    /** Settings, saved in config/damageindicator.json. */
    static final class Config {
        boolean numbers = true;
        String color = "c";          // a game colour code (Option.color)
        double seconds = 1.5;        // how long a number stays (Option.slider)
        boolean hideFlash = false;   // no red flash on others
        int armorKey = Keyboard.KEY_B;
    }

    static Config config = new Config();

    /** A number floating over an entity. */
    private record Hit(double x, double y, double z, String text, long at) {
    }

    private final List<Hit> hits = new ArrayList<>();
    static float lastDamage;
    static long lastDamageAt;
    private boolean keyWasDown;
    private int armorLeft;   // pieces still to try after a key press

    // --- setup ---

    @Override
    public void afterStartGame() {
        load();
        Hud.register(new LastHitElement());
    }

    // --- damage ---

    @Override
    public void onHealthChanged(Object entity, float oldHealth, float newHealth, float oldAbsorption,
            float newAbsorption) {
        float damage = (oldHealth + oldAbsorption) - (newHealth + newAbsorption);
        if (damage <= 0) {
            return;   // healing
        }
        if (entity == Entities.self()) {
            lastDamage = damage;
            lastDamageAt = System.currentTimeMillis();
            return;   // your own hits go to the HUD, not over your head
        }
        if (!config.numbers) {
            return;
        }
        Entity e = (Entity) entity;
        String text = damage == Math.rint(damage) ? String.valueOf((int) damage) : String.format("%.1f", damage);
        hits.add(new Hit(e.posX, e.posY + e.height + 0.5, e.posZ, "-" + text + " ❤",
                System.currentTimeMillis()));
    }

    @Override
    public boolean onEntityHurt(Object entity) {
        // Only the effect on your screen: the entity still takes the hit on the server.
        return config.hideFlash && entity != Entities.self();
    }

    @Override
    public void onRenderWorld(float partialTicks) {
        if (hits.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity cam = mc.getRenderViewEntity();
        double cx = cam.lastTickPosX + (cam.posX - cam.lastTickPosX) * partialTicks;
        double cy = cam.lastTickPosY + (cam.posY - cam.lastTickPosY) * partialTicks;
        double cz = cam.lastTickPosZ + (cam.posZ - cam.lastTickPosZ) * partialTicks;
        long now = System.currentTimeMillis();
        long life = (long) (config.seconds * 1000);
        FontRenderer font = mc.fontRendererObj;
        int rgb = Option.rgb(config.color);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        for (Iterator<Hit> it = hits.iterator(); it.hasNext(); ) {
            Hit h = it.next();
            float age = (now - h.at()) / (float) life;
            if (age >= 1) {
                it.remove();
                continue;
            }
            int alpha = (int) (255 * (1 - age * age));
            GlStateManager.pushMatrix();
            // Rises half a block over its life, and always faces the camera, like a nametag.
            GlStateManager.translate(h.x() - cx, h.y() + age * 0.5 - cy, h.z() - cz);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0, 1, 0);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1, 0, 0);
            GlStateManager.scale(-0.03F, -0.03F, 0.03F);
            font.drawStringWithShadow(h.text(), -font.getStringWidth(h.text()) / 2.0F, 0,
                    (Math.max(alpha, 5) << 24) | rgb);
            GlStateManager.popMatrix();
        }
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    // --- best armour key ---

    @Override
    public void onTickEnd() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean down = config.armorKey != 0 && mc.currentScreen == null && Keyboard.isKeyDown(config.armorKey);
        if (down && !keyWasDown) {
            armorLeft = 4;
        }
        keyWasDown = down;
        // One click per tick at most: servers watch for inhuman click speed.
        while (armorLeft > 0 && mc.thePlayer != null) {
            int piece = --armorLeft;
            int best = bestFor(piece);
            if (best >= 0 && Inventory.equip(best)) {
                break;
            }
        }
    }

    /** The inventory index of armour better than what is worn on that piece, or -1. */
    private static int bestFor(int piece) {
        int best = -1;
        int bestValue = Inventory.armorValue(Inventory.armor(piece));
        for (int i = 0; i < Inventory.ARMOR_START; i++) {
            Object s = Inventory.stack(i);
            if (Inventory.armorPiece(s) == piece && Inventory.armorValue(s) > bestValue) {
                best = i;
                bestValue = Inventory.armorValue(s);
            }
        }
        return best;
    }

    // --- Panel ---

    @Override
    public void onPage(Page page) {
        page.title("Damage Indicator")
            .section("Numbers")
            .option(Option.toggle("Damage numbers", () -> config.numbers, v -> { config.numbers = v; save(); }))
            .option(Option.color("Colour", () -> config.color, c -> { config.color = c; save(); }))
            .option(Option.slider("Duration", 0.5, 5, 0.25, () -> config.seconds, v -> { config.seconds = v; save(); },
                    v -> String.format("%.2f s", v)))
            .option(Option.toggle("No red flash on others", () -> config.hideFlash,
                    v -> { config.hideFlash = v; save(); }))
            .section("Armour")
            .option(Option.key("Put on best armour", () -> config.armorKey, k -> { config.armorKey = k; save(); }))
            .restore(() -> { config = new Config(); save(); });
    }

    // --- settings file ---

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/" + ID + ".json");
    }

    private static void load() {
        File f = file();
        if (!f.isFile()) {
            return;
        }
        try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            Config c = GSON.fromJson(r, Config.class);
            if (c != null) {
                config = c;
            }
        } catch (Exception e) {
            System.out.println("[" + ID + "] could not read " + f + ": " + e);
        }
    }

    static void save() {
        File f = file();
        f.getParentFile().mkdirs();
        try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(config, w);
        } catch (Exception e) {
            System.out.println("[" + ID + "] could not write " + f + ": " + e);
        }
    }
}
