# guiAPI — Notlar

## Bu mod nadir güncelleme alıyor
Değişiklik yapmadan önce mevcut davranışı bozmamaya özellikle dikkat et — güncelleme
sıklığı düşük olduğu için hatalar uzun süre fark edilmeden kalabilir.

## Yakında: Gate sistemi
Gate desteği planlanıyor ancak henüz eklenmedi. Bu bölüm, gate'ler eklendiğinde
güncellenecek.

## tickwarden-patch-1 dalında eklenenler

### 1. Buton başına cooldown / rate-limit
- `GuiDefinition.Button` record'una `int cooldown` alanı eklendi (tick cinsinden).
- JSON'da `"cooldown": 100` şeklinde tanımlanır (0 = cooldown yok, varsayılan).
- `BarrelGuiHandler` içinde `BUTTON_COOLDOWNS: Map<UUID, Map<String, Long>>` ile
  oyuncu başına, `guiId:slot` anahtarıyla son tıklama tick'i tutulur.
- Kontrol, aksiyon zinciri tetiklenmeden önce yapılır; kayıt, tetiklendikten
  hemen sonra yapılır (delayed action chain'lerle çakışmaz).
- Cooldown, GUI kapatılıp tekrar açılarak bypass edilemez — bilinçli tasarım.
- Oyuncu disconnect olduğunda `ServerPlayConnectionEvents.DISCONNECT` ile
  cooldown state'i temizlenir (memory leak önlemi).
- Toggle butonları da `Button.cooldown()`'ı miras aldığı için `"cooldown"` alanı
  toggle tanımlı butonlarda da ek kod gerekmeden çalışır.

### 2. Dinamik slot item (placeholder çözümü)
- `buildStack()` içinde `itemId` artık `resolve()`'dan geçiyor — `{score:...}`
  ve `{var:...}` placeholder'ları item ID'sinde de çözülüyor.
- **Önemli kısıtlama:** placeholder'ın çözüm sonucu geçerli bir Minecraft item
  ID'si olmalı (örn. `minecraft:diamond`). Bir skor/değer sayısı item ID'si
  olarak kullanılamaz — bu tarz "tier'e göre farklı item" ihtiyacı için doğru
  yöntem, aynı slotta birden fazla `condition`'lı buton tanımı kullanmaktır
  (bkz. örnek datapack'teki slot 10 kalıbı: score_lt / score_gt ile ayrılmış
  iki ayrı buton tanımı).
- Toggle butonlarında da otomatik çalışır (ortak `itemId` değişkeni üzerinden).

## Bilinmeyen / doğrulanmamış
- Gerçek `./gradlew build` CI'da başarılı geçti (bkz. build log — BUILD SUCCESSFUL).
- Yerel ortamda Fabric/Minecraft bağımlılıkları indirilemediği için değişiklikler
  sadece elle/statik olarak doğrulanabildi, CI onayı asıl doğrulama oldu.
