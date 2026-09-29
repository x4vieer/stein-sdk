# Stein Loader — Mod Developer Guide

Stein Loader is a mod platform for the **Minecraft 1.8.9 client**, running on the Stein Bridge: Java 25, LWJGL 3 and a
faster renderer that does not change how the game looks. Mods are client-side and work on any vanilla server.

This guide covers everything you need to write, build and ship a mod.

> **The API is experimental.** It works and it is what our own mods use, but anything marked `@Experimental` may still
> change between Stein Loader versions. Tell us what you miss — this is the time to shape it.

**Download** `stein-sdk.jar` and `stein-mod-template.zip` from the
[latest release](../../releases/latest). **Example:** [`examples/damage-indicator`](examples/damage-indicator) is a
complete mod — damage numbers over entities, a HUD element, a Panel page with a slider and a key picker, and a key that
puts on your best armour. **Licence:** the API, the template and the examples are [MIT](LICENSE).

- [How it differs from Forge](#how-it-differs-from-forge)
- [Quick start](#quick-start)
- [Project layout](#project-layout)
- [steinmod.json](#steinmodjson)
- [Game names and private members](#game-names-and-private-members)
- [Events](#events)
- [Services](#services)
- [Rules of the road](#rules-of-the-road)
- [Troubleshooting](#troubleshooting)

## How it differs from Forge

| | Forge 1.8.9 | Stein Loader |
|---|---|---|
| Runs on | Java 8, LWJGL 2 | Java 25, LWJGL 3 |
| Side | client and server | client only (any vanilla server) |
| How mods change the game | event bus + coremods (ASM) | a fixed set of events; **no coremods, no Mixin** |
| Performance | stock renderer | faster renderer; your GL calls get its batching automatically |

The last two rows are the trade-off. Mods cannot rewrite game bytecode, so two mods never fight over the same code,
and the engine underneath can keep changing without breaking you. In exchange, you can only change the game where the
Loader gives you an event. If you need one that does not exist, ask for it (see [Troubleshooting](#troubleshooting)).

## Quick start

You need **JDK 25** (the game runs on Java 25) and **Minecraft 1.8.9** installed. If you have never played 1.8.9, the
SDK downloads the game from Mojang, the same way the launcher does.

Your mod is **Java 25**, not the Java 8 of Forge mods: the SDK compiles with `--release 25`, and records, sealed types,
pattern matching in `switch`, text blocks and `var` all work in game.

```sh
# 1. Once: prepare the game for compiling (readable names, see below).
java -jar stein-sdk.jar setup

# 2. Start from the template.
unzip stein-mod-template.zip -d my-mod && cd my-mod

# 3. Build: produces build/ExampleMod.steinmod (the "name" without spaces)
java -jar stein-sdk.jar build

# 4. Or build and copy into .minecraft/mods in one go.
java -jar stein-sdk.jar install
```

Then start the game with the **Stein Loader** profile. Your mod shows up in the mod list (the Stein Loader tab on the
pause menu), with the reason if it did not load.

`setup` uses the Minecraft jar from your own installation (checked against Mojang's checksum) and downloads the MCP
mappings from the Forge maven. It builds the compile jars **on your machine**, in `~/.stein-sdk` (or
`STEIN_SDK_HOME`). Nothing from Mojang or MCP is shipped inside the SDK — and you must not ship it in your mod either.
Use `--mc <folder>` if your `.minecraft` is somewhere else.

### IDE

Run `java -jar stein-sdk.jar classpath` and add the listed jars as libraries of your project (IntelliJ: *Project
Structure → Libraries*; Eclipse: *Build Path → Add External JARs*). Attach `stein-api-sources.jar` (next to
`stein-api.jar`) as the sources of `stein-api.jar` to get the API documentation in the editor. Set the project SDK to
Java 25. Build with the SDK command, not the IDE: the SDK also translates the names back to the game's.

## Project layout

```
my-mod/
  src/main/java/...               your code
  src/main/resources/
    steinmod.json                 the manifest (required)
    pack.mcmeta                   if you have assets
    assets/<your-id>/...          textures, lang files, sounds
  libs/                           extra jars to compile against (optional)
  build/<Name>.steinmod           the output
```

A `.steinmod` is a jar with another extension (old Forge tries to load every `.jar` in the mods folder). Everything in
`src/main/resources` goes into it as is; `assets/` becomes a resource pack, like a Forge mod.

## steinmod.json

```json
{
  "id": "examplemod",
  "name": "Example Mod",
  "version": "1.0.0",
  "author": "You",
  "description": "Shown in the mod list.",
  "entry": "com.example.examplemod.ExampleMod",
  "loader": "0.1.14",
  "depends": {"othermod": "1.2.0"},
  "optional": {"steincombat": "0.1.12"},
  "access": ["net/minecraft/client/gui/GuiPlayerTabOverlay.drawPing"]
}
```

| Key | Required | Meaning |
|---|---|---|
| `id` | yes | Unique id: lowercase, no spaces. Other mods and the config use it. |
| `name`, `version`, `description` | | Shown in the mod list. `version` is compared as numbers (`1.10.0` > `1.9.0`). |
| `author` / `authors` | | A string, or a list of strings. |
| `entry` | yes | The class implementing `SteinMod` (or a list of them). Needs a public no-argument constructor. |
| `loader` | yes | The **minimum Stein Loader version** your mod needs. Use `0.1.14` or newer: the Loader adapts your mod to the engine when it loads it. |
| `depends` | | Mods you cannot run without, `{"id": "minimum version"}`. Without them your mod does not load, and the list says why. They load before you. |
| `optional` | | Mods you use when present. If present, they must be at least that version; they load before you. |
| `access` | | Protected game methods you call from outside their class hierarchy (see below). Compile-time only. |

## Game names and private members

You write your mod with the **MCP stable_22** names — `Minecraft.getMinecraft()`, `mc.thePlayer`,
`GuiScreen.drawScreen` — the same names as Forge 1.8.9 mods and tutorials. The SDK translates them to the game's
obfuscated names when it builds, and checks that every game class, field and method you use exists. Overridden methods
(`initGui`, `drawScreen`, `actionPerformed` in your `GuiScreen`) are translated too.

**Private fields and methods are available directly.** The compile jar has them opened, so you write:

```java
int fps = Minecraft.debugFPS;                  // private static in the game
overlay.header                                  // private field of GuiPlayerTabOverlay
mc.session = new Session(...);                  // private *final* field — writing works too
```

In the game those members stay private; when your mod loads, the Loader links each access to the member once, and
the JIT treats it as a direct access. **Do not use reflection with names in strings** (`getDeclaredField("...")`):
strings are not translated, so it breaks in the real game.

Two limits:

- **Protected methods** stay protected in the compile jar, so you can override them as usual (`protected void
  keyTyped`). To *call* one from outside its hierarchy, list it in `access` as `"net/minecraft/…/Class.methodName"`,
  with the name you write in your code.
- **Constructors and non-public classes** cannot be opened: `new SomePrivateClass()` will not work.

## Events

Implement `dev.xavier.stein.loader.api.SteinMod` and override what you need; every method has a default that does
nothing. Game types arrive as `Object` (the API does not depend on the game's classes): cast them to what the comment
says. All events run on the game thread, in dependency order. An exception in your event is logged and the game — and
the other mods — go on.

| Event | When | Forge 1.8.9 equivalent |
|---|---|---|
| `beforeStartGame()` | before `Minecraft.startGame`, nothing loaded yet | — |
| `afterStartGame()` | window, resources and options ready — **register keys and HUD elements here** | `FMLInitializationEvent` |
| `onTickEnd()` | end of each client tick (20 per second) | `ClientTickEvent` (END) |
| `onKeyInput()`, `onMouseInput()` | after each keyboard / mouse event the game handled | `KeyInputEvent`, `MouseInputEvent` |
| `onOverlay(pt)` | after the HUD is drawn | `RenderGameOverlayEvent.Post` (ALL) |
| `onOverlayTop(pt)` | after everything, on top of the HUD grid | — |
| `onRenderWorld(pt)` | world drawn (before the hand), camera matrix | `RenderWorldLastEvent` |
| `beforeDrawScreen`, `afterDrawScreen` | around the open screen's `drawScreen` | `DrawScreenEvent.Pre/Post` |
| `onGuiInit(screen, buttons)` | a screen was built; add your buttons | `InitGuiEvent.Post` |
| `onGuiAction(screen, button)` | a button was clicked; return `true` to take it | `ActionPerformedEvent.Pre` |
| `onGuiMouseInput(screen)` | mouse event on a screen; return `true` to take it | `GuiScreenEvent.MouseInputEvent.Pre` |
| `onChat(message)` | chat message from the server; return another, or `null` to drop | `ClientChatReceivedEvent` |
| `onSendChat(message)` | what the player sends (chat or command); return another, or `null` to not send — client commands | `ClientChatEvent` |
| `onCustomPayload(channel, bytes)` | plugin message from the server | custom payload / `FMLNetworkEvent` |
| `onPlaySound(sound)` | a sound about to play; return another, or `null` to mute | `PlaySoundEvent` |
| `onJoinGame()` | joined a server or world | `ClientConnectedToServerEvent` |
| `onAttackEntity(target)` | the player's attack, before it is sent; `true` cancels | `AttackEntityEvent` |
| `onInteractEntity(target)` | the player right-clicks an entity, before it is sent; `true` cancels (the held item is used instead, as when clicking the air) | `EntityInteractEvent` |
| `onWindowClick(window, slot, button, mode)` | any inventory click (game or `Inventory`), before it is sent; `true` cancels | `GuiContainer` mouse events, roughly |
| `onSlotChanged(window, slot, stack)`, `onWindowItems(window)` | the server changed a slot / sent a whole window, already applied | — |
| `onEntityHurt(entity)` | any entity you see took damage (you too): the red flash, shake and sound come from here; `true` removes that effect on your client only | `LivingHurtEvent`, roughly (client side) |
| `onEntityDeath(entity)` | an entity died, before its death animation | `LivingDeathEvent`, roughly (client side) |
| `onHealthChanged(entity, oldHealth, newHealth, oldAbsorption, newAbsorption)` | health or absorption changed, already applied — damage is the drop, healing the rise | — |
| `onFov`, `onFovModifier` | field of view of the frame / the player's FOV factor | `FOVModifier`, `FOVUpdateEvent` |
| `onTextureStitch(map)`, `onModelsReloaded()` | texture atlas built / models ready | `TextureStitchEvent.Pre`, `ModelBakeEvent` |
| `onItemModel`, `onBlockModel`, `onArmorTexture` | swap an item model, a block model (chunk threads!), an armor texture | — |
| `onHideEntity`, `onGhostEntity` | do not draw an entity / draw it as a ghost | `RenderLivingEvent.Pre` (cancel) |
| `onPlayerList`, `onPlayerListName`, `onNametag` | the Tab list, a name in it, the name above a head | — |
| `onPage(page)`, `configScreen(parent)` | your page on the Panel (Right Shift) / your config screen | `IModGuiFactory` |

The Javadoc of each method (in `stein-api-sources.jar`) says what it costs and what to watch for. Anything marked
`@Experimental` may still change in a minor Loader version; the rest of the API does not break mods built against
it.

## Services

| Class | What for |
|---|---|
| `Keys.register(binding)` | a `KeyBinding` in the Controls screen, keeping the player's key choice |
| `Hud.register(element)` | a `HudElement` on the HUD grid: the player moves, scales and toggles it; you size and draw it |
| `Page`, `Option` | your options on the Panel (`onPage`): toggles, cycles, colours, actions, lists, screens, sliders (`Option.slider`), key pickers (`Option.key`) |
| `Panel.open(parent, page)` | open the Panel on your page, e.g. from a button |
| `Net.sendPayload(channel, bytes)` | a plugin message to the server |
| `Net.set(proxy)` | route the connection (proxies) |
| `Mods.isLoaded(id)`, `Mods.version(id)` | other mods |
| `Inventory` | read the player inventory and the open window; move items with real clicks (`move`, `quickMove`, `swapHotbar`, `drop`, `equip`, `unequip`, raw `click`), open or closed inventory — see below |
| `Targeting` | who is under the crosshair (`raycast`, all entities in order), and attack / swing / interact from your mod |
| `Entities` | health, max health, absorption, armour and hurt time of any entity; `self()` is you |
| `Game.partialTicks()`, `Game.itemData(stack)`, … | small helpers — see the Javadoc |

### Inventory

`Inventory`, `Targeting`, `Entities` and the events next to them are new in the Loader library **0.1.15** (players get it with Stein Loader 0.1.22) and still `@Experimental`: a mod using them sets `"loader": "0.1.15"`.

Two ways to point at a place. A **slot** is the slot number in the *current window* — the open chest, furnace or
crafting table, or, with none open, the player's own inventory (window 0, which exists even with the inventory
closed); it is what the game's click uses, and it changes from window to window. An **inventory index** is the fixed
position in the player's inventory: 0-8 hotbar, 9-35 main, 36-39 armour (36 boots … 39 helmet). `slotOf(index)` and
`inventoryIndexOf(slot)` translate.

Every action is a real click (`PlayerControllerMP.windowClick`): the game applies it at once and the server gets the
same packet a player's click sends. Nothing waits — several actions go out in the same tick — so pacing is your call,
from `onTickEnd`. Many servers watch for inhuman click speed.

```java
// Auto armour, one piece per tick: put on the best chestplate we carry.
@Override
public void onTickEnd() {
    int best = -1, bestValue = Inventory.armorValue(Inventory.armor(Inventory.CHESTPLATE));
    for (int i = 0; i < Inventory.ARMOR_START; i++) {
        Object s = Inventory.stack(i);
        if (Inventory.armorPiece(s) == Inventory.CHESTPLATE && Inventory.armorValue(s) > bestValue) {
            best = i;
            bestValue = Inventory.armorValue(s);
        }
    }
    if (best >= 0 && !Inventory.isContainerOpen()) {
        Inventory.equip(best);   // shift-click if the slot is empty, swap otherwise
    }
}
```

### Targeting

`Targeting.raycast()` lists every entity on the crosshair line within attack reach (3 blocks, 6 in creative), nearest
first — the same test the game uses to aim, but without stopping at the first one. `Ray.blockDistance` is where the
line hits a block: the game does not aim at entities behind it. `attack(entity)` sends the game's own attack (it does
not go through `onAttackEntity`), `swing()` swings the arm; the click does both.

```java
// Protect allies, and let the hit go through them to whoever is behind.
@Override
public boolean onAttackEntity(Object target) {
    if (!isAlly(target)) {
        return false;                       // not an ally: the normal hit goes on
    }
    Targeting.Ray ray = Targeting.raycast();
    for (Targeting.Hit h : ray.hits) {
        if (h.distance >= ray.blockDistance) {
            break;                          // behind a wall
        }
        if (!isAlly(h.entity)) {
            Targeting.attack(h.entity);     // the arm already swung with the click
            break;
        }
    }
    return true;                            // the ally is never hit
}
```

### Damage

`onEntityHurt` fires when anyone you can see takes a hit — it is the server's "hurt" signal, the one behind the red
flash — and `onHealthChanged` tells how much, from the health the server syncs (yours always; others' only if the
server sends it — many servers send full health for everyone). `Entities` reads health, absorption and armour.

```java
// Damage indicator: remember each hit, draw it over the entity for a second (in onRenderWorld).
@Override
public void onHealthChanged(Object entity, float oldHealth, float newHealth, float oldAbs, float newAbs) {
    float damage = (oldHealth + oldAbs) - (newHealth + newAbs);
    if (damage > 0) {
        hits.add(new Hit((Entity) entity, damage, System.currentTimeMillis()));
    }
}

// No red flash on allies.
@Override
public boolean onEntityHurt(Object entity) {
    return isAlly(entity);
}
```

### Talking to another mod

All mods share one class loader. Declare the other mod in `depends` (or `optional`), put a jar of its API classes in
your `libs/` (built with readable names, like your own code — only for compiling, it is not copied into your mod),
and call it directly. With `optional`, check `Mods.isLoaded("theirid")` before touching any of its
classes: a class is only looked up when the code using it runs.

## Rules of the road

- **Stay fast.** Some events run for every entity or item every frame (`onHideEntity`, `onItemModel`, …): answer
  quickly and do not allocate there. `onBlockModel` runs on the chunk-building threads, several at once: no shared
  mutable state without care, and no OpenGL. The F3 screen shows what each mod costs per frame ("Mod time"), counting
  its per-frame events and its HUD elements — keep yours low.
- **Leave GL state as you found it** in drawing events. You can call OpenGL and `GlStateManager` as usual: the Loader
  adapts your calls to the engine (LWJGL 3, its matrix stack and model batching) when it loads your mod.
- **Save your settings** under `config/<your-id>.json` in the game folder (`mc.mcDataDir`).
- **Be honest with servers.** Many servers forbid combat advantages. The Loader tells the server which mods are loaded.

## Troubleshooting

- **"reference(s) to the game that do not exist"** at build: a name that is not in 1.8.9, or a typo. The message
  names the class and member.
- **Mod greyed out in the list:** the reason is next to it — missing dependency, Loader too old, broken manifest.
- **Mod failed to load:** the reason is in the list, the stack trace in `logs/latest.log`.
- **Missing an event?** Tell us where in the game you need to hook in and what the Forge equivalent is. Events are how
  the Loader grows.
