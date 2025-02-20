import logging
import json
import re
from typing import List, Dict, Optional, Set
from dataclasses import dataclass
from enum import Enum
from current_url import get_api_url
import requests
import zipfile
import shutil
import tempfile
import os
import sys

class TvType(Enum):
    Movie = "movie"
    Live = "live"
    TvSeries = "tvseries"

class DubStatus(Enum):
    Subbed = "subbed"
    Dubbed = "dubbed"
    None_ = "none"

class ExtractorLinkType(Enum):
    VIDEO = "video"
    M3U8 = "m3u8"

class Qualities(Enum):
    Unknown = 0

@dataclass
class ExtractorLink:
    source: str
    name: str
    url: str
    referer: str
    quality: int
    type: ExtractorLinkType

class RecTV:
    def __init__(self):
        self.main_url = None
        self.name = "RecTV"
        self.has_main_page = True
        self.lang = "tr"
        self.has_quick_search = False
        self.has_chromecast_support = True
        self.has_download_support = True
        self.supported_types = {TvType.Movie, TvType.Live, TvType.TvSeries}
        self.sw_key = None
        
        # Kotlin'deki mainPage kategorileri
        self.categories = {
            f"{self.main_url}/api/channel/by/filtres/0/0/SAYFA/{self.sw_key}/": "Canlı",
            f"{self.main_url}/api/movie/by/filtres/0/created/SAYFA/{self.sw_key}/": "Son Filmler",
            f"{self.main_url}/api/serie/by/filtres/0/created/SAYFA/{self.sw_key}/": "Son Diziler",
            f"{self.main_url}/api/movie/by/filtres/14/created/SAYFA/{self.sw_key}/": "Aile",
            f"{self.main_url}/api/movie/by/filtres/1/created/SAYFA/{self.sw_key}/": "Aksiyon",
            f"{self.main_url}/api/movie/by/filtres/13/created/SAYFA/{self.sw_key}/": "Animasyon",
            f"{self.main_url}/api/movie/by/filtres/19/created/SAYFA/{self.sw_key}/": "Belgesel",
            f"{self.main_url}/api/movie/by/filtres/4/created/SAYFA/{self.sw_key}/": "Bilim Kurgu",
            f"{self.main_url}/api/movie/by/filtres/2/created/SAYFA/{self.sw_key}/": "Dram",
            f"{self.main_url}/api/movie/by/filtres/10/created/SAYFA/{self.sw_key}/": "Fantastik",
            f"{self.main_url}/api/movie/by/filtres/3/created/SAYFA/{self.sw_key}/": "Komedi",
            f"{self.main_url}/api/movie/by/filtres/8/created/SAYFA/{self.sw_key}/": "Korku",
            f"{self.main_url}/api/movie/by/filtres/17/created/SAYFA/{self.sw_key}/": "Macera",
            f"{self.main_url}/api/movie/by/filtres/5/created/SAYFA/{self.sw_key}/": "Romantik"
        }

    async def get_main_page(self, page: int, request) -> dict:
        page = page - 1
        url = request.data.replace("SAYFA", str(page))
        
        try:
            async with self.session.get(url, headers={"user-agent": "okhttp/4.12.0"}) as response:
                text_response = await response.text()
                try:
                    home = json.loads(text_response)
                except json.JSONDecodeError as e:
                    logging.error(f"JSON decode error: {e}")
                    logging.debug(f"Raw response: {text_response[:200]}...")  # İlk 200 karakteri logla
                    return {"name": request.name, "results": []}
                
            movies = []
            for item in home:
                to_dict = json.dumps(item)
                
                if item["label"] not in ["CANLI", "Canlı"]:
                    movies.append({
                        "title": item["title"],
                        "url": to_dict,
                        "type": TvType.Movie,
                        "poster_url": item["image"]
                    })
                else:
                    movies.append({
                        "name": item["title"],
                        "url": to_dict,
                        "type": TvType.Live,
                        "poster_url": item["image"]
                    })
                    
            return {"name": request.name, "results": movies}
        except Exception as e:
            logging.error(f"Error in get_main_page: {e}")
            return {"name": request.name, "results": []}

    async def search(self, query: str) -> List[dict]:
        url = f"{self.main_url}/api/search/{query}/{self.sw_key}/"
        
        try:
            async with self.session.get(url, headers={"user-agent": "okhttp/4.12.0"}) as response:
                text_response = await response.text()
                try:
                    data = json.loads(text_response)
                except json.JSONDecodeError as e:
                    logging.error(f"JSON decode error in search: {e}")
                    logging.debug(f"Raw response: {text_response[:200]}...")
                    return []
                
            results = []
            
            if "channels" in data:
                for item in data["channels"]:
                    to_dict = json.dumps(item)
                    results.append({
                        "title": item["title"],
                        "url": to_dict,
                        "type": TvType.Movie,
                        "poster_url": item["image"]
                    })
                    
            if "posters" in data:
                for item in data["posters"]:
                    to_dict = json.dumps(item)
                    results.append({
                        "title": item["title"],
                        "url": to_dict,
                        "type": TvType.Movie,
                        "poster_url": item["image"]
                    })
                    
            return results
        except Exception as e:
            logging.error(f"Error in search: {e}")
            return []

    async def load(self, url: str) -> Optional[dict]:
        try:
            data = json.loads(url)
        except json.JSONDecodeError:
            logging.error(f"URL JSON decode hatası: {url}")
            return None

        try:
            # Canlı yayın kontrolü - label'a göre kontrol ediyoruz
            if data.get("label") in ["CANLI", "Canlı"]:
                return {
                    "type": TvType.Live,
                    "name": data["title"],
                    "url": json.dumps(data),  # sources bilgisini korumak için tüm datayı gönderiyoruz
                    "poster_url": data["image"],
                    "plot": data.get("description"),
                    "tags": [g["title"] for g in data.get("categories", [])]
                }
            
            # Dizi kontrolü
            if data.get("type") == "serie":
                serie_url = f"{self.main_url}/api/season/by/serie/{data['id']}/{self.sw_key}/"
                
                async with self.session.get(serie_url, headers={"user-agent": "okhttp/4.12.0"}) as response:
                    if response.status != 200:
                        logging.error(f"Dizi verisi alınamadı. Status: {response.status}")
                        return None
                        
                    text_response = await response.text()
                    try:
                        seasons = json.loads(text_response)
                    except json.JSONDecodeError:
                        logging.error(f"Dizi JSON decode hatası. Response: {text_response[:200]}...")
                        return None

                episodes = {}
                number_regex = re.compile(r'\d+')

                for season in seasons:
                    dub_status = (DubStatus.Subbed if "altyazı" in season["title"].lower() else
                                DubStatus.Dubbed if "dublaj" in season["title"].lower() else
                                DubStatus.None_)
                    
                    for episode in season["episodes"]:
                        if dub_status not in episodes:
                            episodes[dub_status] = []
                            
                        season_num = next((int(x) for x in number_regex.findall(season["title"])), None)
                        episode_num = next((int(x) for x in number_regex.findall(episode["title"])), None)
                        
                        if episode.get("sources") and len(episode["sources"]) > 0:
                            episodes[dub_status].append({
                                "data": episode["sources"][0]["url"],
                                "name": episode["title"],
                                "season": season_num,
                                "episode": episode_num,
                                "description": season["title"].split(".S ", 1)[1] if ".S " in season["title"] else season["title"],
                                "poster_url": data["image"]
                            })

                if not any(episodes.values()):
                    logging.error("Hiç bölüm bulunamadı")
                    return None

                return {
                    "type": TvType.TvSeries,
                    "name": data["title"],
                    "episodes": episodes,
                    "poster_url": data["image"],
                    "plot": data["description"],
                    "year": data["year"],
                    "tags": [g["title"] for g in data.get("genres", [])],
                    "rating": float(data["rating"]) if data.get("rating") else None
                }

            # Film kontrolü - varsayılan olarak film kabul ediyoruz
            return {
                "type": TvType.Movie,
                "title": data["title"],
                "url": json.dumps(data),  # sources bilgisini korumak için tüm datayı gönderiyoruz
                "poster_url": data["image"],
                "plot": data.get("description"),
                "year": data.get("year"),
                "tags": [g["title"] for g in data.get("categories", [])],
                "rating": float(data["rating"]) if data.get("rating") else None
            }

        except Exception as e:
            logging.error(f"Load fonksiyonu hatası: {str(e)}")
            logging.debug(f"Data: {data}")
            return None

    async def load_links(self, data: str, is_casting: bool) -> List[ExtractorLink]:
        links = []
        
        if data.startswith("http"):
            logging.debug(f"RCTV data » {data}")
            links.append(ExtractorLink(
                source=self.name,
                name=self.name,
                url=data,
                referer="https://twitter.com/",
                quality=Qualities.Unknown.value,
                type=ExtractorLinkType.M3U8
            ))
            return links

        try:
            veri = json.loads(data)
        except json.JSONDecodeError:
            return links

        for source in veri["sources"]:
            logging.debug(f"RCTV source » {source}")
            links.append(ExtractorLink(
                source=self.name,
                name=f"{self.name} - {source['type']}",
                url=source["url"],
                referer="https://twitter.com/",
                quality=Qualities.Unknown.value,
                type=ExtractorLinkType.VIDEO if source["type"] == "mp4" else ExtractorLinkType.M3U8
            ))

        return links

    def get_video_interceptor(self, extractor_link: ExtractorLink):
        def interceptor(request):
            # HTTP istek başlıklarını modifiye et
            headers = request.headers.copy()
            headers.pop("If-None-Match", None)
            headers["User-Agent"] = "googleusercontent"
            
            return request.replace(headers=headers)
            
        return interceptor

    async def initialize(self):
        """Session'ı başlat ve gerekli kontrolleri yap"""
        import aiohttp
        from current_url import get_api_url
        
        # Güncelleme kontrolü
        if await check_and_update():
            print("Program yeniden başlatılacak...")
            os.execv(sys.executable, ['python'] + sys.argv)
        
        self.session = aiohttp.ClientSession()
        
        # Çalışan URL ve sw_key'i al
        result = await get_api_url()
        if not result:
            raise Exception("Çalışan API URL'si bulunamadı!")
        
        self.main_url, self.sw_key = result
        
        # Kategorileri güncelle
        self.categories = {
            f"{self.main_url}/api/channel/by/filtres/0/0/SAYFA/{self.sw_key}/": "Canlı",
            f"{self.main_url}/api/movie/by/filtres/0/created/SAYFA/{self.sw_key}/": "Son Filmler",
            f"{self.main_url}/api/serie/by/filtres/0/created/SAYFA/{self.sw_key}/": "Son Diziler",
            f"{self.main_url}/api/movie/by/filtres/14/created/SAYFA/{self.sw_key}/": "Aile",
            f"{self.main_url}/api/movie/by/filtres/1/created/SAYFA/{self.sw_key}/": "Aksiyon",
            f"{self.main_url}/api/movie/by/filtres/13/created/SAYFA/{self.sw_key}/": "Animasyon",
            f"{self.main_url}/api/movie/by/filtres/19/created/SAYFA/{self.sw_key}/": "Belgesel",
            f"{self.main_url}/api/movie/by/filtres/4/created/SAYFA/{self.sw_key}/": "Bilim Kurgu",
            f"{self.main_url}/api/movie/by/filtres/2/created/SAYFA/{self.sw_key}/": "Dram",
            f"{self.main_url}/api/movie/by/filtres/10/created/SAYFA/{self.sw_key}/": "Fantastik",
            f"{self.main_url}/api/movie/by/filtres/3/created/SAYFA/{self.sw_key}/": "Komedi",
            f"{self.main_url}/api/movie/by/filtres/8/created/SAYFA/{self.sw_key}/": "Korku",
            f"{self.main_url}/api/movie/by/filtres/17/created/SAYFA/{self.sw_key}/": "Macera",
            f"{self.main_url}/api/movie/by/filtres/5/created/SAYFA/{self.sw_key}/": "Romantik"
        }
        
        return self

    async def close(self):
        """Session'ı kapat"""
        if hasattr(self, 'session'):
            await self.session.close()

    async def export_m3u(self, filename: str = "canli_yayinlar.m3u"):
        """Tüm canlı yayın linklerini m3u formatında dışa aktar"""
        m3u_content = "#EXTM3U\n"
        processed_channels = set()  # Tekrar eden kanalları önlemek için
        
        try:
            print("\n=== Canlı Yayınlar İçin M3U Oluşturuluyor ===")
            
            # Canlı yayın kategorisi URL'si
            canli_url = f"{self.main_url}/api/channel/by/filtres/0/0/SAYFA/{self.sw_key}/"
            page = 1
            
            while True:
                url = canli_url.replace("SAYFA", str(page-1))
                results = await self.get_main_page(
                    page=page,
                    request=DummyRequest(data=url, name="Canlı")
                )
                
                if not results["results"]:
                    break
                    
                for item in results["results"]:
                    try:
                        channel_name = item.get('name', '')
                        
                        # Tekrar eden kanalları atla
                        if channel_name in processed_channels:
                            continue
                        
                        links = await self.load_links(item["url"], False)
                        
                        if links:
                            for link in links:
                                if not "otolinkaff.com" in link.url:
                                    print(f"Ekleniyor: {channel_name}")
                                    m3u_content += f'#EXTINF:-1 tvg-name="{channel_name}" tvg-language="Turkish" tvg-country="TR" tvg-logo="{item.get("poster_url", "")}" group-title="Canlı",{channel_name}\n'
                                    m3u_content += '#EXTVLCOPT:http-user-agent=googleusercontent\n'
                                    m3u_content += '#EXTVLCOPT:http-referrer=https://twitter.com/\n'
                                    m3u_content += f'{link.url}\n\n'
                                    
                                    processed_channels.add(channel_name)
                                    
                    except Exception as e:
                        channel_name = item.get('name', 'Bilinmeyen Kanal')
                        logging.error(f"Kanal işleme hatası ({channel_name}): {e}")
                        continue
                
                page += 1
                
            if processed_channels:
                # Dosyayı kaydet
                with open(filename, "w", encoding="utf-8") as f:
                    f.write(m3u_content)
                print(f"\nToplam {len(processed_channels)} kanal kaydedildi: {filename}")
            else:
                print("Hiç canlı yayın bulunamadı.")
                
        except Exception as e:
            logging.error(f"M3U oluşturma hatası: {e}")

    async def export_content_m3u(self, content_data: dict, filename: str):
        """Seçilen içeriği m3u formatında dışa aktar"""
        m3u_content = "#EXTM3U\n"
        
        try:
            # Eğer canlı yayın ise direkt link bilgilerini al
            if content_data.get("type") == TvType.Live:
                links = await self.load_links(content_data["url"], False)
                if links:
                    for link in links:
                        if not "otolinkaff.com" in link.url:
                            title = content_data.get("name", "")
                            m3u_content += f'#EXTINF:-1 tvg-name="{title}" tvg-language="Turkish" tvg-country="TR" tvg-logo="{content_data.get("poster_url", "")}" group-title="Canlı",{title}\n'
                            m3u_content += '#EXTVLCOPT:http-user-agent=googleusercontent\n'
                            m3u_content += '#EXTVLCOPT:http-referrer=https://twitter.com/\n'
                            m3u_content += f'{link.url}\n\n'
                
                with open(filename, "w", encoding="utf-8") as f:
                    f.write(m3u_content)
                print(f"\nM3U dosyası oluşturuldu: {filename}")
                return
            
            # Diğer içerik tipleri için normal akış
            content = await self.load(content_data["url"])
            if content:
                if content["type"] == TvType.TvSeries:
                    # Dizi bölümlerini ekle
                    for dub_status, episodes in content["episodes"].items():
                        # Dublaj durumunu Türkçe'ye çevir
                        dub_text = {
                            DubStatus.Subbed: "Altyazılı",
                            DubStatus.Dubbed: "Dublajlı",
                            DubStatus.None_: ""
                        }[dub_status]
                        
                        for episode in episodes:
                            # Sezon ve bölüm numaralarını al
                            season_num = episode.get("season", 1)
                            episode_num = episode.get("episode", 1)
                            
                            # Başlığı oluştur
                            series_title = content.get("name", content_data.get("title", ""))
                            episode_title = f"{series_title} {season_num}. Sezon {episode_num}. Bölüm"
                            if dub_text:  # Eğer dublaj durumu varsa ekle
                                episode_title = f"{episode_title} ({dub_text})"
                            
                            m3u_content += f'#EXTINF:-1 tvg-name="{episode_title}" tvg-language="Turkish" tvg-country="TR" tvg-logo="{content.get("poster_url", "")}" group-title="Dizi",{episode_title}\n'
                            m3u_content += '#EXTVLCOPT:http-user-agent=googleusercontent\n'
                            m3u_content += '#EXTVLCOPT:http-referrer=https://twitter.com/\n'
                            m3u_content += f'{episode["data"]}\n\n'
                else:
                    # Film linklerini ekle
                    links = await self.load_links(content_data["url"], False)
                    for link in links:
                        if not "otolinkaff.com" in link.url:
                            title = (content.get("name") or 
                                   content.get("title") or 
                                   content_data.get("title") or 
                                   content_data.get("name", "Bilinmeyen İçerik"))
                            
                            m3u_content += f'#EXTINF:-1 tvg-name="{title}" tvg-language="Turkish" tvg-country="TR" tvg-logo="{content_data.get("poster_url", "")}" group-title="Film",{title}\n'
                            m3u_content += '#EXTVLCOPT:http-user-agent=googleusercontent\n'
                            m3u_content += '#EXTVLCOPT:http-referrer=https://twitter.com/\n'
                            m3u_content += f'{link.url}\n\n'

                with open(filename, "w", encoding="utf-8") as f:
                    f.write(m3u_content)
                print(f"\nM3U dosyası oluşturuldu: {filename}")
            else:
                print("İçerik yüklenemedi.")
        except Exception as e:
            logging.error(f"M3U oluşturma hatası: {e}")
            logging.debug(f"Content data: {content_data}")

    async def export_sports_m3u(self, filename: str = "spor_canli_yayinlar.m3u"):
        """Spor canlı yayın linklerini m3u formatında dışa aktar"""
        m3u_content = "#EXTM3U\n"
        processed_channels = set()  # Tekrar eden kanalları önlemek için
        
        # Spor kanallarını belirlemek için anahtar kelimeler
        sports_keywords = [
            "spor", "sport", "futbol", "football", "basketball", "basketbol",
            "beinsports", "s sport", "tivibu spor", "nba", "uefa", "şampiyonlar ligi",
            "champions league", "premier", "laliga", "bundesliga", "serie a"
        ]
        
        try:
            print("\n=== Spor Canlı Yayınları İçin M3U Oluşturuluyor ===")
            
            # Canlı yayın kategorisi URL'si
            canli_url = f"{self.main_url}/api/channel/by/filtres/0/0/SAYFA/{self.sw_key}/"
            page = 1
            
            while True:
                url = canli_url.replace("SAYFA", str(page-1))
                results = await self.get_main_page(
                    page=page,
                    request=DummyRequest(data=url, name="Canlı")
                )
                
                if not results["results"]:
                    break
                    
                for item in results["results"]:
                    try:
                        channel_name = item.get('name', '').lower()
                        
                        # Spor kanalı kontrolü
                        is_sports_channel = any(keyword.lower() in channel_name for keyword in sports_keywords)
                        if not is_sports_channel:
                            continue
                        
                        # Tekrar eden kanalları atla
                        if channel_name in processed_channels:
                            continue
                        
                        links = await self.load_links(item["url"], False)
                        
                        if links:
                            for link in links:
                                if not "otolinkaff.com" in link.url:
                                    original_name = item.get('name', '')
                                    print(f"Ekleniyor: {original_name}")
                                    m3u_content += f'#EXTINF:-1 tvg-name="{original_name}" tvg-language="Turkish" tvg-country="TR" tvg-logo="{item.get("poster_url", "")}" group-title="Spor",{original_name}\n'
                                    m3u_content += '#EXTVLCOPT:http-user-agent=googleusercontent\n'
                                    m3u_content += '#EXTVLCOPT:http-referrer=https://twitter.com/\n'
                                    m3u_content += f'{link.url}\n\n'
                                    
                                    processed_channels.add(channel_name)
                                    
                    except Exception as e:
                        channel_name = item.get('name', 'Bilinmeyen Kanal')
                        logging.error(f"Kanal işleme hatası ({channel_name}): {e}")
                        continue
                
                page += 1
                
            if processed_channels:
                # Dosyayı kaydet
                with open(filename, "w", encoding="utf-8") as f:
                    f.write(m3u_content)
                print(f"\nToplam {len(processed_channels)} spor kanalı kaydedildi: {filename}")
            else:
                print("Hiç spor kanalı bulunamadı.")
                
        except Exception as e:
            logging.error(f"M3U oluşturma hatası: {e}")

def sanitize_filename(filename: str) -> str:
    """Dosya adını düzenle"""
    # Dosya adından .m3u uzantısını kaldır
    if filename.endswith('.m3u'):
        filename = filename[:-4]
    
    # Dosya adındaki geçersiz karakterleri temizle
    invalid_chars = '<>:"/\\|?*'
    for char in invalid_chars:
        filename = filename.replace(char, '')
    
    # Birden fazla boşluğu tek boşluğa indir
    filename = ' '.join(filename.split())
    
    # Başındaki ve sonundaki boşlukları temizle
    filename = filename.strip()
    
    # .m3u uzantısını geri ekle
    return f"{filename}.m3u"

async def check_and_update():
    """Config ve kod güncellemelerini kontrol et ve uygula"""
    try:
        # Mevcut config'i oku
        config_path = os.path.join(os.path.dirname(__file__), "config.json")
        with open(config_path, 'r', encoding='utf-8') as f:
            current_config = json.load(f)
            current_version = current_config.get("version", "0.0")
        
        # GitHub'daki güncel config'i kontrol et
        print("Güncellemeler kontrol ediliyor...")
        response = requests.get(current_config["config_url"])
        if response.status_code != 200:
            logging.error("Güncel config dosyasına erişilemedi!")
            return
        
        remote_config = response.json()
        remote_version = remote_config.get("version", "0.0")
        
        # Versiyon kontrolü
        if remote_version <= current_version:
            print(f"Program güncel! (Versiyon: {current_version})")
            return
            
        # Güncelleme gerekli
        print(f"Yeni versiyon bulundu! ({current_version} -> {remote_version})")
        print("Güncelleme indiriliyor...")
        
        # Geçici dizin oluştur
        with tempfile.TemporaryDirectory() as temp_dir:
            # Zip dosyasını indir
            zip_path = os.path.join(temp_dir, "update.zip")
            update_response = requests.get(current_config["update_code"])
            
            if update_response.status_code != 200:
                logging.error("Güncelleme dosyası indirilemedi!")
                return
                
            # Zip dosyasını kaydet
            with open(zip_path, 'wb') as f:
                f.write(update_response.content)
            
            # Zip dosyasını aç
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                # Zip içeriğini geçici dizine çıkart
                zip_ref.extractall(temp_dir)
                
                # Çıkartılan dizini bul (genelde zip içinde tek bir ana dizin olur)
                extracted_dir = None
                for item in os.listdir(temp_dir):
                    if os.path.isdir(os.path.join(temp_dir, item)):
                        extracted_dir = os.path.join(temp_dir, item)
                        break
                
                if not extracted_dir:
                    logging.error("Güncelleme dosyası geçerli değil!")
                    return
                
                # Dosyaları güncelle
                current_dir = os.path.dirname(__file__)
                
                print("Dosyalar güncelleniyor...")
                for root, dirs, files in os.walk(extracted_dir):
                    # Klasör yapısını koru
                    relative_path = os.path.relpath(root, extracted_dir)
                    target_dir = os.path.join(current_dir, relative_path)
                    
                    # Hedef dizini oluştur
                    os.makedirs(target_dir, exist_ok=True)
                    
                    # Dosyaları kopyala
                    for file in files:
                        src_file = os.path.join(root, file)
                        dst_file = os.path.join(target_dir, file)
                        shutil.copy2(src_file, dst_file)
        
        print(f"Güncelleme tamamlandı! Yeni versiyon: {remote_version}")
        
        # Config'i güncelle
        with open(config_path, 'w', encoding='utf-8') as f:
            json.dump(remote_config, f, indent=4, ensure_ascii=False)
            
        return True
        
    except Exception as e:
        logging.error(f"Güncelleme hatası: {e}")
        logging.debug("Hata detayı:", exc_info=True)
        return False

if __name__ == "__main__":
    import asyncio
    import os
    
    logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s - %(levelname)s - %(message)s'
    )
    
    async def interactive_menu():
        rectv = await RecTV().initialize()
        
        try:
            while True:
                print("\n=== RecTV Menü ===")
                print("1. Kategoriler")
                print("2. Arama Yap")
                print("3. Spor Canlı Yayınlarını Listele")
                print("4. Tüm Canlı Yayınları Listele")
                print("5. Çıkış")
                
                # Ana menü seçimi
                try:
                    choice = input("\nSeçiminiz (1-5): ").strip()
                    if not choice:  # Boş girişi kontrol et
                        print("\nLütfen bir seçim yapın!")
                        continue
                    if not choice.isdigit() or int(choice) < 1 or int(choice) > 5:
                        print("\nGeçersiz seçim! Lütfen 1-5 arası bir sayı girin.")
                        continue
                    choice = int(choice)
                except ValueError:
                    print("\nGeçersiz giriş! Lütfen bir sayı girin.")
                    continue
                
                if choice == 1:
                    # Kategorileri listele
                    print("\n=== Kategoriler ===")
                    categories = list(rectv.categories.items())
                    for i, (_, name) in enumerate(categories, 1):
                        print(f"{i}. {name}")
                    
                    # Kategori seçimi
                    try:
                        cat_input = input(f"\nKategori seçin (1-{len(categories)}) [Geri dönmek için Enter]: ").strip()
                        if not cat_input:  # Enter'a basılırsa ana menüye dön
                            continue
                        
                        cat_choice = int(cat_input)
                        if cat_choice < 1 or cat_choice > len(categories):
                            print(f"\nGeçersiz seçim! Lütfen 1-{len(categories)} arası bir sayı girin.")
                            continue
                        
                        url, name = categories[cat_choice-1]
                        
                        # Seçilen kategorideki içerikleri listele
                        results = await rectv.get_main_page(
                            page=1,
                            request=DummyRequest(data=url, name=name)
                        )
                        
                        if results["results"]:
                            print(f"\n=== {name} ===")
                            for i, item in enumerate(results["results"], 1):
                                print(f"{i}. {item.get('title') or item.get('name')}")
                            
                            # İçerik seçimi
                            content_input = input(f"\nİçerik seçin (1-{len(results['results'])}) [Geri dönmek için Enter]: ").strip()
                            if not content_input:  # Enter'a basılırsa ana menüye dön
                                continue
                            
                            content_choice = int(content_input)
                            if 1 <= content_choice <= len(results["results"]):
                                selected = results["results"][content_choice-1]
                                filename = selected.get('title') or selected.get('name', '')
                                filename = sanitize_filename(filename)
                                await rectv.export_content_m3u(selected, filename)
                            else:
                                print(f"\nGeçersiz seçim! Lütfen 1-{len(results['results'])} arası bir sayı girin.")
                            
                    except ValueError:
                        print("\nGeçersiz giriş! Lütfen bir sayı girin.")
                        continue
                    
                elif choice == 2:
                    # Arama yap
                    query = input("\nArama terimi [Geri dönmek için Enter]: ").strip()
                    if not query:  # Enter'a basılırsa ana menüye dön
                        continue
                    
                    results = await rectv.search(query)
                    
                    if results:
                        print("\n=== Arama Sonuçları ===")
                        for i, item in enumerate(results, 1):
                            print(f"{i}. {item['title']}")
                        
                        try:
                            content_input = input(f"\nİçerik seçin (1-{len(results)}) [Geri dönmek için Enter]: ").strip()
                            if not content_input:  # Enter'a basılırsa ana menüye dön
                                continue
                            
                            content_choice = int(content_input)
                            if 1 <= content_choice <= len(results):
                                selected = results[content_choice-1]
                                filename = selected['title']
                                filename = sanitize_filename(filename)
                                await rectv.export_content_m3u(selected, filename)
                            else:
                                print(f"\nGeçersiz seçim! Lütfen 1-{len(results)} arası bir sayı girin.")
                        except ValueError:
                            print("\nGeçersiz giriş! Lütfen bir sayı girin.")
                    else:
                        print("Sonuç bulunamadı.")
                
                elif choice == 3:
                    # Spor canlı yayınlarını listele
                    await rectv.export_sports_m3u()
                
                elif choice == 4:
                    # Tüm canlı yayınları listele
                    await rectv.export_m3u("tum_canli_yayinlar.m3u")
                
                elif choice == 5:
                    print("\nProgramdan çıkılıyor...")
                    break
                
                input("\nDevam etmek için Enter'a basın...")
                os.system('cls' if os.name == 'nt' else 'clear')
                
        except Exception as e:
            logging.error(f"Program hatası: {e}")
            logging.debug("Hata detayı:", exc_info=True)  # Hata stack trace'ini logla
        finally:
            await rectv.close()
    
    # DummyRequest sınıfı
    class DummyRequest:
        def __init__(self, data, name):
            self.data = data
            self.name = name
    
    # Test fonksiyonunu çalıştır
    asyncio.run(interactive_menu()) 