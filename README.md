# Codex Guardian

**A companion for accounts with one life.** Codex Guardian watches over a Hardcore Ironman, a Hardcore Group Ironman or any account you mark as one-life (Hardcore Worlds included). It reads your hitpoints, the place you stand in and what you carry, tells you plainly how much danger you are in and why, and keeps a journal of the run: every close call survived, every milestone, and what hurts you most.

It is the second plugin in the **Codex** suite, after [Codex Navigator](https://github.com/Nubles/OSRS-Codex-Navigator), and shares its look: warm ink, aged gold and the game's own fonts.

<p align="center">
  <img src="docs/screenshots/1-ward-danger.png" width="225" alt="Ward in danger">
  <img src="docs/screenshots/3-journal.png" width="225" alt="Run journal">
  <img src="docs/screenshots/4-perils.png" width="225" alt="Perils">
</p>

## What it does

- **Ward.** A dial from Calm to Peril with the single reason that matters most, then where you are and how risky it is, your readiness (food, a way out, a saving grace, prayer, cures), who is attacking you, and every factor behind Guardian's read.
- **Entry briefings.** Walk into a boss lair, a raid, a dangerous dungeon or the Wilderness and the in-game plaque checks your food, your teleport and your ring of life before the first hit lands. Safe minigames say so.
- **Wilderness sense.** A warning a few steps before the ditch, and teleports judged by level: most stop at 20, a glory and a few others at 30, nothing above.
- **Peril.** When hitpoints are critical and still falling, the plaque beats, the screen edge glows red and you get a notification. Low hitpoints can notify too.
- **Journal.** The story of the run, newest first: close calls survived with how low you went, risky places entered, milestones, disconnects in danger, a death or the loss of hardcore status.
- **Perils.** Close calls, the lowest you have been, the biggest hit taken, time spent in danger, and a ranking of what has dealt you the most damage and where it got close.

<p align="center">
  <img src="docs/screenshots/5-briefing.png" width="200" alt="Entry briefing">
  <img src="docs/screenshots/6-plaque-peril.png" width="200" alt="Plaque in peril">
</p>

## Rules posture

Guardian only reads what the client already shows you: your stats, your inventory and worn items, your position and who is attacking you. It warns about situations in general (low hitpoints, a dangerous place, no food, no way out) and never about what a particular boss is about to do. It does not click, change menus, send chat or touch the network. The journal is saved per account in your RuneLite profile and never leaves your computer.

## Hardcore Worlds

Hardcore Ironmen are recognised automatically. For any other one-life account, such as one on Hardcore Worlds, turn on **One-life account** in the plugin settings so the journal treats a death as the end of the run.

## Development

```bash
./gradlew build   # compile and run tests
./gradlew run     # launch RuneLite with the plugin loaded
```

The `docs/screenshots` images are rendered from the real panel and overlay code with a sample account.
