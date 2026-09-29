# Damage Indicator — example mod

A complete Stein Loader mod that uses the new (experimental) API of Stein Loader 0.1.15. Read it top to bottom: it is
short, and every part is something you will want in your own mod.

| Feature | API used |
|---|---|
| Damage numbers over any entity that loses health, rising and fading | `onHealthChanged`, `onRenderWorld` |
| The last hit you took, as a HUD element the player moves and scales | `HudElement`, `Hud.register`, `Entities` |
| A key that puts on the best armour you carry, one piece per tick | `Inventory.equip`, `armorPiece`, `armorValue` |
| Optional: no red flash on other entities | `onEntityHurt` |
| A Panel page: toggle, colour, **slider** (duration), **key picker** | `onPage`, `Option.slider`, `Option.key` |
| Settings saved as JSON in `config/damageindicator.json` | Gson (comes with the game) |

## Build

```sh
java -jar stein-sdk.jar setup      # once
java -jar stein-sdk.jar install    # builds build/DamageIndicator.steinmod and copies it into .minecraft/mods
```

In game: turn on **Last hit taken** in the HUD editor, and open the mod's page on the Panel (Right Shift).

## Notes

- Other players' health reaches you only if the server sends it: many servers send full health for everyone, and then
  only your own hits have numbers. `onEntityHurt` still fires for everyone.
- The armour key clicks at most once per tick. Servers watch for inhuman click speed — keep your mods polite.

MIT licensed, like the API: copy anything you like.
