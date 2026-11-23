# AstraxRelic

Hệ thống **Di Vật** độc quyền cho AstraxBox / BoxMine. Paper 1.21.x (build với 1.21.1, đổi `paper-api` trong `pom.xml` nếu cần), Java 21.

## Ý tưởng
- Chỉ **3 slot** trang bị, buff chỉ gồm **Máu, Sát thương, Giáp, Tốc độ**.
- Mỗi Relic có **nội tại ẩn** (hiện ra khi đạt cấp mở khóa), ví dụ Hút Máu, Chí Mạng, Né Tránh, Kết Liễu, Sát Thủ Boss, Ý Chí Cuối Cùng (thoát chết), Phản Đòn...
- Mỗi Relic có **aura particle riêng** (vòng sáng, quả cầu xoay, hào quang, xoắn ốc, tia sáng, vệt đuôi). Người khác nhìn là biết bạn đang build gì.
- Trang bị đủ 3 món cùng **bộ Set** nhận bonus và thêm aura của bộ.
- 20 Relic, 6 độ hiếm, Level 1–30, nguyên liệu Shard / Dust / Core rơi từ đào mỏ, quái, Boss.
- GUI `/relic`, lưu SQLite, PlaceholderAPI, mọi thứ chỉnh trong `config.yml`.

## Build bằng GitHub
1. Upload toàn bộ file lên repo (đều ở thư mục gốc).
2. Tạo file `.github/workflows/build.yml` (chép từ `build.yml`).
3. Tab **Actions** → tải artifact **AstraxRelic** (file `.jar`) bỏ vào `plugins/`.

## Lệnh
| Lệnh | Mô tả |
|---|---|
| `/relic` | Mở menu |
| `/relic codex` `sets` `top` | Codex, Bộ, Xếp hạng |
| `/relic equip\|unequip\|upgrade\|recycle <id>` | Thao tác nhanh |
| `/relicadmin give <người> <relic> [cấp]` | Cấp Di Vật |
| `/relicadmin remove <người> <relic>` | Xóa Di Vật |
| `/relicadmin material <người> <shard\|dust\|core> <số>` | Chỉnh nguyên liệu |
| `/relicadmin reload` | Reload config |

Quyền: `astraxrelic.use` (mặc định), `astraxrelic.admin` (op).

## Placeholder
`shard`, `dust`, `core`, `slot_1..3`, `bonus_health|damage|armor|speed`, `level_<id>`, `set_<id>`, `equipped_count`, `owned_count`, `total_levels`.

## Cân bằng
`caps` trong config giới hạn tổng Máu/Sát thương/Giáp/Tốc độ. Sát thương Relic tính qua event, nên cộng được cả với vũ khí/skill gây sát thương Bukkit thông thường.
