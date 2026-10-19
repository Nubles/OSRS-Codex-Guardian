# Codex Guardian

**A companion for accounts with one life.** Codex Guardian watches over a Hardcore Ironman, a Hardcore Group Ironman or any account you mark as one-life (Hardcore Worlds included). It reads your hitpoints, the place you stand in and what you carry, tells you plainly how much danger you are in and why, and keeps a journal of the run: every close call survived, every milestone, and what hurts you most.

It is the second plugin in the **Codex** suite, after [Codex Navigator](https://github.com/Nubles/OSRS-Codex-Navigator), and shares its look: warm ink, aged gold and the game's own fonts.

<p align="center">
  <img src="docs/screenshots/1-ward-danger.png" width="225" alt="Ward in danger">
  <img src="docs/screenshots/3-journal.png" width="225" alt="Run journal">
  <img src="docs/screenshots/4-perils.png" width="225" alt="Perils">
</p>

## What it does

- **Ward.** A dial from Calm to Peril with the single reason that matters most and, in danger, the one thing to do now: teleport out with the item you carry, eat, walk south to a level your teleport works at, or run. Below it: who is attacking you, where you are and how risky it is, and one ledger of your readiness (food, a way out, a last resort, prayer, cures).
- **Before you go.** Somewhere quiet, the Ward checks the trip ahead: food against what you plan to take, a way out, the protection your destination is known for (dragonfire, venom, a wyvern's breath, smoke, a burning floor, cave horrors) and a last resort. No ring of life on, but two in the bank? It says so. Walk away from a bank and the plaque runs the same check once, worst problems first.
- **Codex Quartermaster aware.** With [Codex Quartermaster](https://github.com/Nubles/OSRS-Codex-Quartermaster) installed, the check follows the kit you chose there: its destination, its food, and the survival supplies it packs (prayer, anti-venom, antifire, brews, teleport tabs). Without it, Guardian checks with its own sense.
- **Entry briefings.** Walk into a boss lair, a raid, a dangerous dungeon or the Wilderness and the in-game plaque checks your food, your teleport and your ring of life before the first hit lands. Safe minigames say so.
- **Wilderness sense.** A briefing a few steps before the ditch, and teleports judged by level: most stop at 20, a glory and a few others at 30, nothing above. Walk past your best teleport's reach and the plaque says so at once, with a notification, and counts the tiles back south to where it works again (or to the ditch, when nothing will). The level is read from the game's own Wilderness text, underground included.
- **Peril.** Guardian calls peril whenever a death could come in the next few ticks: hitpoints critical under fire, one more hit like your worst recent one would kill you (two attackers landing together count as one hit), or your hitpoints run out within about twelve seconds at the current pace. The plaque beats with what to do, the screen edge glows red and you get a notification. A ring of life is noted but never talks the read down: one hit from above its threshold skips it.
- **Journal.** The story of the run, newest first: close calls survived with how low you went, risky places entered, milestones, disconnects in danger, a death or the loss of hardcore status (even one that happened on another client, noticed at the next login). A death in a safe minigame is recorded as safe and does not end the run.
- **Perils.** Close calls, the lowest you have been, the biggest hit taken, time spent in danger, and a ranking of what has dealt you the most damage and where it got close.

<p align="center">
  <img src="docs/screenshots/5-briefing.png" width="200" alt="Entry briefing">
  <img src="docs/screenshots/6-plaque-peril.png" width="200" alt="Plaque in peril">
  <img src="docs/screenshots/7-plaque-limit.png" width="200" alt="Past your teleport's reach">
</p>

## Rules posture

Guardian only reads what the client already shows you: your stats, your inventory and worn items, your position and who is attacking you. It warns about situations in general (low hitpoints, a dangerous place, no food, no way out) and never about what a particular boss is about to do. It does not click, change menus, send chat or touch the network. The journal is saved per account in your RuneLite profile and never leaves your computer. It reads Codex Quartermaster's saved kit and bank record, if present, but never changes them.

## Hardcore Worlds

Hardcore Ironmen are recognised automatically. Blighted food and potions only count inside the Wilderness, where they can be used. For any other one-life account, such as one on Hardcore Worlds, press **Mark one-life** in the panel's Journal tab so the journal treats a death as the end of the run. The choice belongs to that account alone, so a main and a one-life account on the same RuneLite profile each keep their own. If you used the old global "One-life account" setting, every account that already had a journal keeps it.

## Development

```bash
./gradlew build   # compile and run tests
./gradlew run     # launch RuneLite with the plugin loaded
```

The `docs/screenshots` images are rendered from the real panel and overlay code with a sample account.
