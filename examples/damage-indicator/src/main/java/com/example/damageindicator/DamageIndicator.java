package com.example.damageindicator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.ModConfig;
import dev.xavier.stein.loader.api.ModContext;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.SteinMod;

/**
 * Example mod for the Stein Loader API (0.1.16, experimental).
 *
 * <ul>
 *   <li><b>Damage numbers</b> over any entity that loses health ({@code onHealthChanged}), drawn in the world
 *       ({@code onRenderWorld}) like a nametag, rising and fading.</li>
 *   <li><b>Last hit you took</b>, as a HUD element the player moves and scales ({@link LastHitElement}).</li>
 *   <li>A <b>key</b> that turns the numbers on and off ({@code Keys}, {@code Chat.actionBar}).</li>
 *   <li>A <b>Panel page</b> with a toggle, a colour, a slider and a key picker; settings saved by the Loader
 *       ({@code ModConfig}).</li>
 * </ul>
 */
public final class DamageIndicator implements SteinMod {

    static final String ID = "damageindicator";

    /** Settings, saved in config/damageindicator.json. */
    static final class Config {
        boolean numbers = true;
        String color = "c";          // a game colour code (Option.color)
        double seconds = 1.5;        // how long a number stays (Option.slider)
        int toggleKey = Keys.KEY_N;  // Option.key
    }

    private final ModContext ctx = ModContext.of(this);
    private final ModConfig<Config> settings = ctx.config(Config.class);   // config/damageindicator.json
    static Config config = new Config();

    /** A number floating over an entity. */
    private record Hit(double x, double y, double z, String text, long at) {
    }

    private final List<Hit> hits = new ArrayList<>();
    static float lastDamage;
    static long lastDamageAt;
    private boolean keyWasDown;

    // --- setup ---

    @Override
    public void afterStartGame() {
        config = settings.get();
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

    // --- the toggle key ---

    @Override
    public void onTickEnd() {
        // Only in the world (no screen open), once per press.
        boolean down = config.toggleKey != 0 && !Screens.isOpen() && Keys.isDown(config.toggleKey);
        if (down && !keyWasDown) {
            config.numbers = !config.numbers;
            settings.save();
            Chat.actionBar("Damage numbers " + (config.numbers ? "on" : "off"));
        }
        keyWasDown = down;
    }

    // --- Panel ---

    @Override
    public void onPage(Page page) {
        page.title("Damage Indicator")
            .section("Numbers")
            .option(Option.toggle("Damage numbers", () -> config.numbers, v -> { config.numbers = v; settings.save(); }))
            .option(Option.color("Colour", () -> config.color, c -> { config.color = c; settings.save(); }))
            .option(Option.slider("Duration", 0.5, 5, 0.25, () -> config.seconds, v -> { config.seconds = v; settings.save(); },
                    v -> String.format("%.2f s", v)))
            .option(Option.key("Toggle key", () -> config.toggleKey, k -> { config.toggleKey = k; settings.save(); }))
            .restore(() -> { config = settings.reset(); settings.save(); });
    }
}
