# RC-8 — Masaüstü tek örnek (single-instance) + öne getir

**Durum: ✅ TAMAM (2026-07-05).** Kullanıcı bildirdi: masaüstünde her çift tıkta YENİ pencere/instance açılıyor;
zaten çalışan varsa onu öne getirmeli, yoksa açmalı.

## Ne yapıldı
- `desktop/SingleInstance.kt`: ilk örnek 127.0.0.1:47324'ü `ServerSocket` ile tutar (yalnız loopback, RC portu
  46464'ten ayrı) ve gelen bağlantıları "öne getir" sinyali sayar → `focusRequests: StateFlow<Int>` artar.
  `acquire()` port doluysa false → ikinci örnek `signalExisting()` (kısa TCP bağlantısı) ile mevcut örneği öne
  getirtip `main()`'den çıkar (pencere/RC server hiç kurulmaz).
- `Main.kt`: `main()` başında `if (!SingleInstance.acquire()) { signalExisting(); return }`. Uygulama scope'unda
  `focusRequests` izlenir → `showWindow()` (tepside/minimize'dan getirir) + `windowRef.toFront()/requestFocus()`.
  Pencere AWT referansı Window içeriğinde `SideEffect { windowRef = window }` ile alınır (tepsi/gizli durumda da
  öne getirebilmek için app-scope'ta tutulur).
- "Tepsiye küçült" davranışıyla uyumlu: kapatınca tepside çalışmaya devam eder → 47324 bağlı kalır → yeniden açış
  yeni pencere yerine mevcut olanı öne getirir.

## Doğrulama (gerçek uygulama)
- App A çalışırken 47324 LISTEN ✓; harici TCP sinyali gönderildi → A çökmedi, 47324+46464 hâlâ listen (focus isteği
  işlendi, toFront çağrıldı).
- İkinci `:desktop:run` **12sn'de exit=0 ile bitti** (yeni pencere açmadan çıktı; çalışan app olsa süresiz sürerdi) +
  46464'te HÂLÂ TEK listener (ikinci örnek kendi RC server'ını başlatmadı).

## Not
- 47324 başka bir uygulamada doluysa acquire false döner → NaviCloud yanlışlıkla "zaten açık" sanıp sinyal atıp
  çıkabilir (nadir; loopback + spesifik port ile risk düşük). Gerekirse port değiştirilir.
- Yalnız masaüstü; Android'i etkilemez.
