from Kekik.cli import konsol
from httpx     import Client, Timeout
from parsel    import Selector
from re        import findall

def sinemaCX(film_url:str) -> dict:
    oturum = Client(timeout=Timeout(10))
    istek  = oturum.get(film_url)
    secici = Selector(istek.text)

    parts = []
    # for part in secici.css("div#videos ul li"):
    #     part_link = part.css("a::attr(href)").get()
    #     # part_img  = part.xpath("./a/text()").get()
    #     # part_img  = findall(r"flags/(.*)\.png", part.css("span::attr(style)").get())[0]
    #     # if part_img in ("DFLT", "frag"):
    #     #     continue

    #     konsol.print(part_img, part_link)
        

    iframe_link  = secici.css("iframe::attr(data-vsrc)").get().split("?img=")[0]

    oturum.headers.update({"Referer": "https://www.sinemax.cc/"})
    iframe_istek = oturum.get(iframe_link)
    alt_yazi     = findall(r'playerjsSubtitle = "\[(.*?)\](https?://[^\s]+)"', iframe_istek.text)

    oturum.headers.update({"X-Requested-With": "XMLHttpRequest"})
    video_istek = oturum.post("https://player.filmizle.in/player/index.php?data=" + iframe_link.split("/")[-1] + "&do=getVideo")
    video_url   = video_istek.json()["securedLink"]

    parts.append({
        "altyazi" : alt_yazi,
        "video"   : video_url
    })

    return parts

konsol.print(sinemaCX("https://www.sinemax.cc/film/intihar-odasi/"))
