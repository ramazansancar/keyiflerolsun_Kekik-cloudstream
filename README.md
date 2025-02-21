# RecTV Windows

RecTV Windows, IPTV içeriklerini görüntülemek ve .m3u dosyaları oluşturmak için geliştirilmiş bir Python uygulamasıdır.

## İndirme

Windows için son sürümü indirmek için [tıklayın](https://github.com/muhammetaliaydin/rectv-windows/releases/latest/download/rectv-windows.exe).

## Özellikler

- Canlı TV yayınlarını izleme
- Film ve dizi içeriklerini görüntüleme
- İçerikleri kategorilere göre listeleme
- Arama yapabilme
- Spor yayınlarını listeleme
- Otomatik güncelleme sistemi
- M3U dosyası oluşturma

## Kurulum

### Gereksinimler

- Python 3.7 veya üzeri
- pip (Python paket yöneticisi)

### Bağımlılıkları Yükleme

```bash
pip install -r requirements.txt
```

### Çalıştırma

Programı başlatmak için:

```bash
python rectv.py
```

veya Windows'ta:
```bash
start.bat
```

## Kullanım

Program başlatıldığında aşağıdaki menü seçenekleri sunulur:

1. **Kategoriler**: Film ve dizileri kategorilere göre listeler
2. **Arama Yap**: İçeriklerde arama yapmanızı sağlar
3. **Spor Canlı Yayınlarını Listele**: Aktif spor yayınlarını listeler
4. **Tüm Canlı Yayınları Listele**: Tüm aktif yayınları listeler
5. **Çıkış**: Programdan çıkış yapar

## Önerilen Uygulamalar

IPTV içeriklerini daha iyi deneyimlemek ve .m3u dosyalarını oynatabilmek için aşağıdaki uygulamaları kullanabilirsiniz:

- [IPTVnator](https://github.com/4gray/iptvnator/releases/latest) - IPTV yayınları için özel olarak geliştirilmiş modern bir oynatıcı
- [VLC Media Player](https://www.videolan.org/vlc/) - Açık kaynak kodlu, güçlü bir medya oynatıcı

## Otomatik Güncelleme

Program her başlatıldığında:
1. Güncel config dosyasını kontrol eder
2. Yeni bir program sürümü varsa otomatik olarak günceller

## Dosya Yapısı

- `rectv.py`: Ana program dosyası
- `current_url.py`: URL yönetimi ve config işlemleri
- `config.json`: Yapılandırma dosyası
- `start.bat`: Windows için başlatma script'i
- `requirements.txt`: Python bağımlılıkları

## Notlar

- M3U dosyaları program ile aynı dizinde oluşturulur

## Lisans

Bu proje MIT lisansı altında lisanslanmıştır.

## Ekstra Notlar

Proje tamamen eğitim amaçlı geliştirilmiştir.
Projenin RecTV ile hiçbir ilişkisi yoktur.