# UPDATE

## 2026-11-02 — Advancement ala Triumph, tetap pakai Lua

- `advancement.removeVanillaAdvancement(id)` / `remove_vanilla(id)`: menyembunyikan satu advancement vanilla/mod dengan file tombstone same-id (tanpa `display`, criteria `minecraft:impossible`). Advancement hilang dari GUI dan tidak bisa diselesaikan; hapus pemanggilan untuk mengembalikannya.
- `advancement.removeVanillaTree(id)` / `remove_vanilla_tree(id)`: menyembunyikan satu tree penuh (root + semua descendant). Cocok untuk mengganti tab vanilla, misalnya `minecraft:story/root`.
- `advancement.removeAllVanilla()` / `remove_all_vanilla()`: menyembunyikan semua advancement namespace `minecraft` sekaligus. Ini mode "replace vanilla completely" ala Triumph; setelah itu buat tree sendiri dengan `advancement.add`.
- Definisi custom selalu menang atas tombstone, jadi id vanilla boleh dipakai ulang untuk replacement. Manifest `.moontweaker` (custom) dan `.moontweaker-vanilla` (tombstone) memisahkan file generated supaya pembersihan tidak salah hapus.
- `/moontweaker reload` sekarang juga memanggil `PlayerList.reloadResources()`, jadi pemain online langsung menerima tree baru (sebelumnya file berubah tapi client masih memakai tree lama).
- `display.x` / `display.y` diterima tapi diabaikan MC 1.12.2; posisi tree dihitung otomatis dari relasi `parent`.
- Docs: section 13 (`DOCS.md`) diperluas; contoh removal ditambahkan ke `lua_scripts/server/example.lua`.
- Verifikasi: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew compileJava` BUILD SUCCESSFUL, plus test harness untuk parser tombstone dan perluasan tree.

## 2026-11-01 — Migrasi Kotlin ke Java

Ya, sudah di-update. Perubahan belum ter-commit (62 file modified + `src/main/java/` untracked) di atas commit `2f969f9` ("update docs", 2026-10-29).

Ringkasan perubahan:

- Semua source `src/main/kotlin/com/moontweaker/**` (14 file Kotlin) dihapus.
- Porting penuh ke Java: `src/main/java/com/moontweaker/**` (14 file Java, class sama: `MoonTweaker`, `MoonTweakerApi`, `ScriptManager`, `RecipeApi`, `LootApi`, `LootTableHandler`, `RegistryApi`, `RegistryHandler`, `ExpansionApi`, `EventApi`, `GameplayEvents`, `RecipeRegistryHook`, `MoonTweakerCommand`).
- `build.gradle`: plugin `org.jetbrains.kotlin.jvm` dan `kotlin { jvmToolchain(8) }` dihapus; dependency `Forgelin-Continuous:2.4.10.0` dihapus. Tinggal RetroFuturagaGradle + LuaJ 3.0.1.
- `gradle.properties`: `kotlin.stdlib.default.dependency=false` dihapus.
- Subproject `LunarLoot/`, `MoonExpansion/`, `MoonRegistry/` ikut tersentuh (build file & source lama ikut dihapus; README utama sudah menyatu jadi satu mod).

Catatan: README.md dan DOCS.md sudah dibetulkan ke Java (implementation, requirements, instalasi, build config, component map .kt→.java, troubleshooting, credits).

## 2026-11-01 — Fitur custom advancements via Lua (Triumph-style)

- Baru: `src/main/java/com/moontweaker/advancement/AdvancementApi.java`. API Lua global `advancement` dengan `advancement.add("modid:path", { ... })` dan `advancement.remove("modid:path")`; tabel Lua dikonversi ke JSON advancement vanilla (display/criteria/requirements/rewards, `icon` string otomatis jadi `{ item = ... }`), divalidasi lewat parser vanilla (`AdvancementManager.GSON`).
- Apply: saat `FMLServerStartedEvent` dan setiap `/moontweaker reload` — JSON ditulis ke `<world>/data/advancements/<ns>/<path>.json` (manifest `.moontweaker` untuk menghapus yang sudah tidak dipakai), lalu `server.getAdvancementManager().reload()`. Vanilla yang sync ke client; `/reload` vanilla juga aman karena file tetap terbaca saat reload.
- Wiring: `ScriptManager` (install global + reset saat stage `server`), `MoonTweaker.serverStarted`, `MoonTweakerCommand` case `reload`.
- Contoh: `lua_scripts/server/example.lua` (commented). README: bullet fitur + project layout. DOCS.md: section baru "13. Advancement API" (section lama 13–20 jadi 14–21), plus pembaruan lifecycle/matrix/internals.
- Verifikasi: `./gradlew compileJava` (JDK 25) BUILD SUCCESSFUL.
