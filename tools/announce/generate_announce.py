#!/usr/bin/env python3
"""Генератор баннера для TG-анонса новой версии Aniko (фирменный стиль, см. README.md рядом).

Запуск из корня репозитория:
    /opt/homebrew/bin/python3 tools/announce/generate_announce.py \
        --version 0.2.0 \
        --subtitle "Автообновление уже внутри" \
        --bullet "Обновления ставятся из приложения" \
        --bullet "Любимая озвучка — прямо в ссылке" \
        --bullet "Новый градиентный прогресс-бар" \
        --out docs/media/aniko-0.2.0-announce.png

Текст — кириллица, держите строки короткими: правая колонка вмещает ~34 символа в буллете.
"""
import argparse

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1280, 640
TOP = (23, 14, 58)        # #170E3A глубокий индиго (верх-лево)
BOTTOM = (74, 38, 158)    # #4A269E фиолет (низ-право)
PRIMARY = (131, 109, 240)  # #836DF0 фирменный акцент (PrimaryDark темы)
CORAL = (247, 92, 97)      # #F75C61 акцент2 (SecondaryDark темы)
WHITE = (245, 243, 255)
MUTED = (190, 182, 224)
STAR_GOLD = (255, 222, 130)

# (cx, cy, r, цвет) — четырёхлучевые блики, как на иконке
STARS = [
    (120, 90, 16, STAR_GOLD), (520, 70, 10, PRIMARY),
    (90, 520, 9, PRIMARY), (480, 590, 8, STAR_GOLD),
    (1180, 90, 12, STAR_GOLD), (1120, 560, 9, PRIMARY), (1240, 300, 7, PRIMARY),
]

FONT_CANDIDATES = [
    ("/System/Library/Fonts/SFNS.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf"),
    ("/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf"),
]


def fonts():
    for regular, bold in FONT_CANDIDATES:
        try:
            return (
                ImageFont.truetype(bold, 92),   # заголовок «Aniko X.Y.Z»
                ImageFont.truetype(bold, 40),   # подзаголовок
                ImageFont.truetype(regular, 34),  # буллеты
                ImageFont.truetype(bold, 26),   # чипы платформ
            )
        except OSError:
            continue
    raise SystemExit("no usable font")


def diagonal_gradient(w, h, c1, c2):
    base = Image.new("RGB", (64, 64))
    px = base.load()
    for y in range(64):
        for x in range(64):
            t = (x + y) / 126
            px[x, y] = tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))
    return base.resize((w, h), Image.BICUBIC)


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return m


def star(draw, cx, cy, r, color):
    s = r // 4
    draw.polygon([(cx, cy - r), (cx + s, cy - s), (cx + r, cy), (cx + s, cy + s),
                  (cx, cy + r), (cx - s, cy + s), (cx - r, cy), (cx - s, cy - s)], fill=color)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", required=True, help="номер версии, например 0.2.0")
    parser.add_argument("--subtitle", required=True, help="главная фраза релиза, ~25 символов")
    parser.add_argument("--bullet", action="append", default=[], help="фича-буллет, ~34 символа; до 3 штук")
    parser.add_argument("--icon", default="docs/icon.png")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    img = diagonal_gradient(W, H, TOP, BOTTOM)

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([60, 90, 560, 590], fill=PRIMARY + (90,))
    img = Image.alpha_composite(img.convert("RGBA"), glow.filter(ImageFilter.GaussianBlur(90)))

    sd = ImageDraw.Draw(img)
    for cx, cy, r, col in STARS:
        star(sd, cx, cy, r, col + (255,))

    icon = Image.open(args.icon).convert("RGBA").resize((400, 400), Image.LANCZOS)
    img.paste(icon, (110, 120), rounded_mask((400, 400), 92))
    ImageDraw.Draw(img).rounded_rectangle([110, 120, 509, 519], radius=92,
                                          outline=(255, 255, 255, 60), width=3)

    f_title, f_sub, f_text, f_chip = fonts()
    d = ImageDraw.Draw(img)
    x_text = 590
    d.text((x_text, 120), f"Aniko {args.version}", font=f_title, fill=WHITE)

    bar = diagonal_gradient(560, 14, PRIMARY, CORAL)
    img.paste(bar, (x_text, 250), rounded_mask((560, 14), 7))

    d.text((x_text, 292), args.subtitle, font=f_sub, fill=WHITE)

    y = 372
    for b in args.bullet[:3]:
        d.ellipse([x_text + 4, y + 12, x_text + 20, y + 28], fill=PRIMARY)
        d.text((x_text + 38, y), b, font=f_text, fill=WHITE)
        y += 52

    chip_y, x = 548, x_text
    for label in ["Android", "iOS", "macOS"]:
        tw = d.textlength(label, font=f_chip)
        d.rounded_rectangle([x, chip_y, x + tw + 44, chip_y + 44], radius=22,
                            outline=PRIMARY + (255,), width=2)
        d.text((x + 22, chip_y + 8), label, font=f_chip, fill=MUTED)
        x += tw + 44 + 18

    img.convert("RGB").save(args.out, "PNG")
    print(f"saved {args.out}")


if __name__ == "__main__":
    main()
