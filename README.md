# AstraxRelic

Hệ thống **Di Vật** độc quyền cho AstraxBox / BoxMine. Paper 1.21.x (build với 1.21.1, đổi `paper-api` trong `pom.xml` nếu cần), Java 21.

## Ý tưởng
- **3 slot** trang bị + **slot thứ 4 đặc biệt**, buff chỉ gồm **Máu, Sát thương, Giáp, Tốc độ**.
- **Slot thứ 4** mở bằng *Thử Thách Tối Thượng* (hạ Boss, hạ quái, đào block, sở hữu Di Vật, Relic cấp tối đa, có Relic Thần Thoại) hoặc **Admin cấp**: `/relicadmin slot <người> unlock`. Chỉ tiêu chỉnh trong `ultimate-quest` của `config.yml`.
- Mỗi Relic có **nội tại ẩn** (hiện ra khi đạt cấp mở khóa), ví dụ Hút Máu, Chí Mạng, Né Tránh, Kết Liễu, Sát Thủ Boss, Ý Chí Cuối Cùng (thoát chết), Phản Đòn...
- Mỗi Relic hiện thành **vật phẩm mini bay quanh người** (bay vòng ngực, xoay trên đầu, xoay quanh chân, lơ lửng sau lưng như cánh). Người khác nhìn là biết bạn đang build gì.
- Trang bị 2/3/4 món cùng **bộ Set** nhận bonus; từ 3 món, các vật phẩm bay phát sáng theo màu của bộ.
- **Cộng hưởng cặp:** 8 cặp Relic đi cùng nhau mở thêm chỉ số và nội tại (xem `/relic synergy`, chỉnh trong `synergies`).
- **Relic Rush:** sự kiện nhân tỉ lệ rơi nguyên liệu toàn server, có bossbar đếm ngược. Bật tay `/relicadmin rush start [phút]` hoặc tự động theo chu kỳ (`relic-rush`).
- **Nội tại mới:** Bão Đòn (đánh liên tiếp cộng dồn sát thương), Khiên Năng Lượng (chặn một đòn theo chu kỳ), Cuồng Nộ (máu càng thấp càng mạnh), Cướp Đoạt (cơ hội rơi thêm nguyên liệu).
- Chế tạo Relic từ Huyền Thoại trở lên sẽ báo cả server.
- **Relic bí ẩn & Discovery:** vài Relic hiện `???` trong Codex kèm gợi ý, tự mở khi đạt điều kiện bí mật (đào sâu, hạ Boss, hoàn thiện bộ...). Người khám phá **đầu tiên trên server** được ghi vào *Astrax Archive* và báo cả server. Xem `/relic archive`.
- **Ký ức & Hồ sơ Relic:** mỗi Relic ghi ngày thức tỉnh, số block/Boss/quái đã cùng trải qua và các cột mốc (cấp 10, tối đa, nội tại ẩn, 1K/10K/100K block, 1/10/50 Boss). Mở bằng phím Q ở Codex.
- **Lời nguyền:** vài Relic mạnh bị trừ một chỉ số cố định (có sàn tối thiểu trong `floors`), buộc người chơi cân nhắc build.
- **Socket & Gem:** Relic có 1–3 ổ khảm theo độ hiếm. Chế tạo Gem bằng Dust/Core rồi khảm vào Relic để đổi hướng build, tháo ra khảm lại không mất.
- 23 Relic (3 Relic bí ẩn), 6 độ hiếm, Level 1–30, nguyên liệu Shard / Dust / Core rơi từ đào mỏ, quái, Boss.
- GUI `/relic`, lưu SQLite, PlaceholderAPI, mọi thứ chỉnh trong `config.yml`.

## Build bằng GitHub
1. Upload toàn bộ file lên repo (đều ở thư mục gốc).
2. Tạo file `.github/workflows/build.yml` (chép từ `build.yml`).
3. Tab **Actions** → tải artifact **AstraxRelic** (file `.jar`) bỏ vào `plugins/`.

## Lệnh
| Lệnh | Mô tả |
|---|---|
| `/relic` | Mở menu |
| `/relic codex` `sets` `synergy` `archive` `quest` `top` | Codex, Bộ, Cộng hưởng, Astrax Archive, Thử Thách, Xếp hạng |
| `/relic equip\|unequip\|upgrade\|recycle <id>` | Thao tác nhanh |
| `/relicadmin give <người> <relic> [cấp]` | Cấp Di Vật |
| `/relicadmin remove <người> <relic>` | Xóa Di Vật |
| `/relicadmin material <người> <shard\|dust\|core> <số>` | Chỉnh nguyên liệu |
| `/relicadmin slot <người> <unlock\|lock>` | Mở/khóa slot thứ 4 |
| `/relicadmin discover <người> <relic>` | Cho khám phá Relic ẩn (dùng cho sự kiện) |
| `/relicadmin gem <người> <gem> <số>` | Cấp Gem |
| `/relicadmin rush <start [phút]\|stop>` | Bật/tắt Relic Rush |
| `/relicadmin reload` | Reload config |

Quyền: `astraxrelic.use` (mặc định), `astraxrelic.admin` (op).

## Placeholder
`shard`, `dust`, `core`, `slot_1..4`, `bonus_health|damage|armor|speed`, `level_<id>`, `set_<id>`, `rush_active`, `rush_left`, `equipped_count`, `owned_count`, `total_levels`.

## Cân bằng
`caps` trong config giới hạn tổng Máu/Sát thương/Giáp/Tốc độ. Sát thương Relic tính qua event, nên cộng được cả với vũ khí/skill gây sát thương Bukkit thông thường.

## Vật phẩm bay
Dùng thực thể `ItemDisplay` (không lưu vào world, tự xóa khi người chơi thoát/chết). Nếu server nặng, tăng `orbit.update-ticks` hoặc đặt `orbit.enabled: false`.
