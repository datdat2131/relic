# AstraxRelic

Hệ thống **Di Vật** độc quyền cho AstraxBox / BoxMine NoHit. Paper **1.21.1**, Java 21.

## Tính năng V1
- 20 Di Vật, 5 slot trang bị, 6 bậc độ hiếm, Level 1–30
- 5 bộ Relic Set (Frostborn, Infernal, Astral, Fortune, Abyss)
- 3 nguyên liệu: **Shard** (chế tạo), **Dust** (nâng cấp), **Core** (mốc cấp 10/20, chế tạo hiếm)
- GUI `/relic`: trang bị, Codex, nâng cấp, tháo, tái chế, xem Set, xếp hạng
- Đào mỏ / hạ quái / hạ Boss rơi nguyên liệu; Di Vật tự tích lũy và thưởng
- SQLite (`plugins/AstraxRelic/astraxrelic.db`), PlaceholderAPI, config chỉnh mọi thứ

## Build bằng GitHub
1. Đưa toàn bộ file lên repo (đều nằm ở thư mục gốc).
2. Tạo file `.github/workflows/build.yml` (chép nội dung từ `build.yml`). Trên web GitHub: *Add file → Create new file*, gõ tên `.github/workflows/build.yml`.
3. Vào tab **Actions**, chờ build xong, tải artifact **AstraxRelic** (file `.jar`) và bỏ vào `plugins/`.

Build tay: `mvn package` → `target/AstraxRelic-1.0.0.jar`.

## Lệnh
| Lệnh | Mô tả |
|---|---|
| `/relic` | Mở menu |
| `/relic codex` `/relic sets` `/relic top` | Codex, Bộ, Xếp hạng |
| `/relic equip\|unequip\|upgrade\|recycle <id>` | Thao tác nhanh |
| `/relicadmin give <người> <relic> [cấp]` | Cấp Di Vật |
| `/relicadmin remove <người> <relic>` | Xóa Di Vật |
| `/relicadmin material <người> <shard\|dust\|core> <số>` | Chỉnh nguyên liệu |
| `/relicadmin trigger <người> <trigger> [số]` | Cộng tiến độ (để plugin khác gọi) |
| `/relicadmin reload` | Reload config |

Quyền: `astraxrelic.use` (mặc định), `astraxrelic.admin` (op).

## Nối với plugin nhiệm vụ / EXP của server
Plugin chỉ **tính** bonus, bạn dùng bonus đó trong hệ thống EXP/tiền của mình:
- PlaceholderAPI: `%astraxrelic_bonus_mining_exp%` (vd 12.5), `%astraxrelic_multiplier_mining_exp%` (vd 1.125)
- Stat: `MINING_EXP, COMBAT_EXP, EVENT_POINTS, BOSS_CONTRIBUTION, QUEST_POINTS, TREASURE, MONEY, MATERIAL_LUCK`
- Khi người chơi xong nhiệm vụ / vào sự kiện, cho plugin kia chạy lệnh console:
  `relicadmin trigger %player% QUEST_COMPLETE 1` hoặc `relicadmin trigger %player% EVENT_JOIN 1`
- Java API: `AstraxRelic.get().getBonus(player, Stat.MINING_EXP)`

Placeholder khác: `shard`, `dust`, `core`, `slot_1..5`, `level_<id>`, `set_<id>`, `equipped_count`, `owned_count`, `total_levels`.

## Chưa có trong V1
Evolution, Spirit (Linh Hồn), Awakening, Preset, Relic Season (theo kế hoạch ban đầu).
