<div align="justify">

# CoronaPoker MOD packs

CoronaPoker can load one external MOD from a `mod` directory next to the
application JAR (or executable installation directory). A MOD is a directory,
not an archive: its activation marker is `mod/mod.xml`, and it is discovered on
the next launch.

MOD media is decorative. It never replaces game rules, networking, the
cryptographic deck or any other authoritative state.

---

## 1. Minimal descriptor

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mod adults="false" fusion_sounds="false" fusion_cinematics="false">
  <name>My CoronaPoker MOD</name>
  <version>1.0</version>
</mod>
```

- `adults="true"` shows the one-time adult-content acknowledgement.
- `fusion_sounds="true"` appends the bundled comedy sounds to the external
  files. With `false`, a non-empty external category replaces that bundled
  category.
- `fusion_cinematics="true"` combines bundled and external all-in GIFs. With
  `false`, the external all-in set replaces the bundled list.
- Optional `<updateurl>` and `<updatepassword>` values describe a MOD update
  endpoint. Only `http` and `https` URLs are accepted.

Malformed XML, unreadable files and paths that escape the MOD directory are
rejected. Missing optional media falls back to the official asset where the
corresponding renderer supports a fallback.

---

## 2. Directory layout

```text
mod/
├─ mod.xml
├─ mod.png
├─ decks/
│  └─ mydeck/
│     ├─ hq/
│     │  ├─ 2_C.jpg ... A_T.jpg
│     │  └─ trasera.jpg
│     └─ gif/
│        └─ shuffle.gif
├─ sounds/
│  ├─ misc/
│  │  └─ call.wav ...
│  └─ joke/
│     ├─ es/
│     │  ├─ allin/*.wav
│     │  ├─ fold/*.wav
│     │  ├─ showdown/*.wav
│     │  ├─ loser/*.wav
│     │  └─ winner/*.wav
│     └─ en/...
└─ cinematics/
   ├─ allin/
   │  ├─ example.gif
   │  └─ example.wav
   └─ misc/
      └─ recover.gif ...
```

`mod.png` is the optional image shown with the active MOD information.

### Card decks

Every immediate directory under `mod/decks` becomes a selectable deck. Card
faces use `<rank>_<suit>.jpg`, with ranks `2` through `10`, `J`, `Q`, `K`, `A`
and CoronaPoker suit codes `C`, `D`, `P`, `T`. Put the high-quality set and
`trasera.jpg` in `hq/`. The renderer also accepts card images directly in the
deck directory as a fallback. `gif/shuffle.gif` supplies its shuffle animation.
If absent, the official Goliat shuffle is used.

The hand generator uses the same selected deck and the same `hq` assets as the
table.

### Game sounds

Files below `mod/sounds` shadow bundled resources with the same relative path.
For example, `mod/sounds/misc/call.wav` replaces the call sound and
`mod/sounds/misc/background_music.mp3` replaces table music. Common names can
be found in the bundled `src/main/resources/sounds/misc` directory.

Comedy categories are language-specific WAV directories under
`sounds/joke/<language>/`: `allin`, `fold`, `showdown`, `loser`, and `winner`.
An empty external category leaves the bundled category untouched.

### Cinematics

All-in animations are GIF files under `cinematics/allin`. A WAV with the same
base name is used as that animation's companion audio. Auxiliary cinematics
can shadow a bundled file under `cinematics/misc` by keeping the same filename.

The bundled all-in filenames are `casinoroyale.gif`, `hulk.gif`, `joker.gif`,
`nicolas_cage.gif`, `nicolas_cage2.gif`, `rounders.gif`, `terminator2.gif`,
`training_day.gif`, and `wallstreet.gif`.

---

## 3. Distribution checklist

1. Keep `mod.xml` at the root of the `mod` directory.
2. Test both Spanish and English when supplying language-specific audio.
3. Include every card face plus `trasera.jpg` in a custom deck.
4. Keep filenames stable and use PNG/JPEG/GIF/WAV/MP3 only where the matching
   slot expects that format.
5. Distribute the complete `mod` directory. Recipients should place it beside
   CoronaPoker and restart the game.

Custom bot names, avatars, difficulty and AI personality are configured by the
host in **New game → Bots → Use custom bots → Manage bots**. They are stored in
the user's CoronaPoker preferences rather than in a MOD pack. See
[`BOTS.md`](BOTS.md#13-custom-bot-profiles).

</div>
