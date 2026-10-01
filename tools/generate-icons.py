#!/usr/bin/env python3
"""
MarbleMD icon factory.

One geometry definition feeds every launcher asset the app needs:

    adaptive icon (API 26+)   -> res/mipmap-anydpi-v26/ic_launcher[_round].xml
                                 res/drawable/ic_launcher_{background,foreground,monochrome}.xml
    legacy vector (API 24-25) -> res/mipmap-anydpi/ic_launcher[_round].xml
    legacy bitmaps            -> res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher[_round].png
    store / documentation     -> branding/*.png

Usage:  python3 tools/generate-icons.py
Requires: Pillow. DejaVu fonts are used for the optional feature graphic.
"""

from __future__ import annotations

import math
import os
import re
from dataclasses import dataclass
from typing import Iterable, Sequence

from PIL import Image, ImageDraw, ImageFilter, ImageFont

VIEWPORT = 108.0
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
BRANDING = os.path.join(ROOT, "branding")
SUPERSAMPLE = 4
GRADIENT_SAMPLES = 320

# ---------------------------------------------------------------- palette ---
MIDNIGHT = "#06132F"
DEEP_BLUE = "#0B2A6E"
VIOLET = "#2B1E86"
CYAN = "#00D5E8"
INDIGO = "#135DF5"
PURPLE = "#7655FF"
PAPER = "#FFFFFF"
PAPER_SHADE = "#E6EFFF"
FOLD = "#A9D4FF"
INK = "#0A1B3D"

BACKGROUND_GRADIENT = ((0.0, MIDNIGHT), (0.52, DEEP_BLUE), (1.0, VIOLET))
MONOGRAM_GRADIENT = ((0.0, CYAN), (0.5, INDIGO), (1.0, PURPLE))
PAPER_GRADIENT = ((0.0, PAPER), (1.0, PAPER_SHADE))

# ------------------------------------------------------------- path model ---


@dataclass
class Shape:
    """A single filled path with either a flat colour or a linear gradient."""

    path: str
    color: str | None = None
    gradient: tuple[tuple[float, str], ...] | None = None
    start: tuple[float, float] | None = None
    end: tuple[float, float] | None = None
    alpha: float = 1.0
    stroke: float | None = None
    shadow: bool = False
    name: str = ""


def rgb(hex_color: str) -> tuple[int, int, int]:
    value = hex_color.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))  # type: ignore[return-value]


def with_alpha(hex_color: str, alpha: float) -> str:
    return "#{:02X}{}".format(max(0, min(255, round(alpha * 255))), hex_color.lstrip("#"))


def sample_gradient(stops: Sequence[tuple[float, str]], t: float) -> tuple[int, int, int]:
    t = max(0.0, min(1.0, t))
    for index in range(len(stops) - 1):
        offset_a, color_a = stops[index]
        offset_b, color_b = stops[index + 1]
        if offset_a <= t <= offset_b:
            local = (t - offset_a) / ((offset_b - offset_a) or 1.0)
            a, b = rgb(color_a), rgb(color_b)
            return tuple(round(a[i] + (b[i] - a[i]) * local) for i in range(3))  # type: ignore[return-value]
    return rgb(stops[-1][1])


# ------------------------------------------------------ path construction ---
def rounded_rect_points(
    x0: float, y0: float, x1: float, y1: float, r: float, corner_cut: bool = False
) -> list[tuple[float, float]]:
    """Flattened outline of a rounded rectangle with an optional folded corner."""
    r = min(r, (x1 - x0) / 2.0, (y1 - y0) / 2.0)
    pts: list[tuple[float, float]] = []

    def arc(cx: float, cy: float, start_deg: float, end_deg: float) -> None:
        steps = 16
        for i in range(steps + 1):
            angle = math.radians(start_deg + (end_deg - start_deg) * i / steps)
            pts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))

    arc(x0 + r, y0 + r, 180, 270)  # top-left
    if corner_cut:
        cut = 8.0
        pts.append((x1 - cut, y0))
        pts.append((x1, y0 + cut))
    else:
        pts.append((x1 - r, y0))
        arc(x1 - r, y0 + r, 270, 360)  # top-right
    pts.append((x1, y1 - r))
    arc(x1 - r, y1 - r, 0, 90)  # bottom-right
    pts.append((x0 + r, y1))
    arc(x0 + r, y1 - r, 90, 180)  # bottom-left
    pts.append((x0, y0 + r))
    return pts


def points_to_path(points: Sequence[tuple[float, float]], close: bool = True) -> str:
    body = " ".join(
        f"{'M' if index == 0 else 'L'}{x:.2f},{y:.2f}" for index, (x, y) in enumerate(points)
    )
    return body + (" Z" if close else "")


def circle_path(cx: float, cy: float, r: float) -> str:
    k = 0.5523 * r
    return (
        f"M{cx:.2f},{cy - r:.2f} "
        f"C{cx + k:.2f},{cy - r:.2f} {cx + r:.2f},{cy - k:.2f} {cx + r:.2f},{cy:.2f} "
        f"C{cx + r:.2f},{cy + k:.2f} {cx + k:.2f},{cy + r:.2f} {cx:.2f},{cy + r:.2f} "
        f"C{cx - k:.2f},{cy + r:.2f} {cx - r:.2f},{cy + k:.2f} {cx - r:.2f},{cy:.2f} "
        f"C{cx - r:.2f},{cy - k:.2f} {cx - k:.2f},{cy - r:.2f} {cx:.2f},{cy - r:.2f} Z"
    )


def stroke_outline(
    points: Sequence[tuple[float, float]], width: float
) -> list[list[tuple[float, float]]]:
    """Turn a polyline into filled polygons (round caps + round joins)."""
    polys: list[list[tuple[float, float]]] = []
    half = width / 2.0

    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / length * half, dx / length * half
        polys.append(
            [(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)]
        )

    for x, y in points:
        polys.append(
            [
                (x + half * math.cos(index * math.pi / 8), y + half * math.sin(index * math.pi / 8))
                for index in range(16)
            ]
        )
    return polys


def polys_to_path(polys: Iterable[Sequence[tuple[float, float]]]) -> str:
    return " ".join(points_to_path(poly, close=True) for poly in polys)


# ----------------------------------------------------------- SVG parsing ----
_TOKEN = re.compile(r"([MmLlHhVvCcSsQqTtAaZz])|(-?\d*\.?\d+(?:e-?\d+)?)")


def parse_path(d: str, samples: int = 18) -> list[list[tuple[float, float]]]:
    """Flatten an absolute/relative SVG path into polygon point lists."""
    tokens = [match.group(0) for match in _TOKEN.finditer(d)]
    contours: list[list[tuple[float, float]]] = []
    current: list[tuple[float, float]] = []
    cursor = (0.0, 0.0)
    start = (0.0, 0.0)
    index = 0
    command = "M"

    def number() -> float:
        nonlocal index
        value = float(tokens[index])
        index += 1
        return value

    def lerp(a, b, t):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

    def quadratic(p0, p1, p2) -> None:
        for step in range(1, samples + 1):
            t = step / samples
            current.append(lerp(lerp(p0, p1, t), lerp(p1, p2, t), t))

    def cubic(p0, p1, p2, p3) -> None:
        for step in range(1, samples + 1):
            t = step / samples
            current.append(
                lerp(lerp(lerp(p0, p1, t), lerp(p1, p2, t), t),
                     lerp(lerp(p1, p2, t), lerp(p2, p3, t), t), t)
            )

    while index < len(tokens):
        token = tokens[index]
        if token.isalpha():
            command = token
            index += 1
            if command in "Zz":
                if current:
                    contours.append(current)
                    current = []
                cursor = start
                continue
        relative = command.islower()
        upper = command.upper()

        if upper == "M":
            x, y = number(), number()
            if relative:
                x, y = cursor[0] + x, cursor[1] + y
            if current:
                contours.append(current)
            current = [(x, y)]
            cursor = start = (x, y)
            command = "l" if relative else "L"
        elif upper == "L":
            x, y = number(), number()
            if relative:
                x, y = cursor[0] + x, cursor[1] + y
            current.append((x, y))
            cursor = (x, y)
        elif upper == "Q":
            cx, cy, x, y = number(), number(), number(), number()
            if relative:
                cx, cy = cursor[0] + cx, cursor[1] + cy
                x, y = cursor[0] + x, cursor[1] + y
            quadratic(cursor, (cx, cy), (x, y))
            cursor = (x, y)
        elif upper == "C":
            x1, y1, x2, y2, x, y = (number() for _ in range(6))
            if relative:
                x1, y1 = cursor[0] + x1, cursor[1] + y1
                x2, y2 = cursor[0] + x2, cursor[1] + y2
                x, y = cursor[0] + x, cursor[1] + y
            cubic(cursor, (x1, y1), (x2, y2), (x, y))
            cursor = (x, y)
        else:  # pragma: no cover - the icon never uses H/V/S/T/A
            raise ValueError(f"Unsupported path command: {command}")

    if current:
        contours.append(current)
    return contours


# -------------------------------------------------------------- geometry ----
PAGE_PATH = points_to_path(rounded_rect_points(37.0, 27.5, 71.0, 80.5, 5.6, corner_cut=True))
FOLD_PATH = points_to_path([(63.5, 27.5), (71.0, 35.0), (63.5, 35.0)])

MONOGRAM_POINTS = [(44.5, 68.5), (44.5, 45.5), (54.0, 61.0), (63.5, 45.5), (63.5, 68.5)]
MONOGRAM_PATH = polys_to_path(stroke_outline(MONOGRAM_POINTS, 6.2))

def arrow_path(x_from: float, x_to: float, y: float, width: float = 1.55) -> str:
    """Thin horizontal arrow with an open head - the RTL/LTR reading cue."""
    direction = 1.0 if x_to > x_from else -1.0
    head = 2.8
    line = stroke_outline([(x_from, y), (x_to, y)], width)
    head_points = stroke_outline(
        [
            (x_to - direction * head, y - head * 0.78),
            (x_to, y),
            (x_to - direction * head, y + head * 0.78),
        ],
        width,
    )
    return polys_to_path(line + head_points)


RTL_ARROW_PATH = arrow_path(63.5, 44.5, 73.4)
LTR_ARROW_PATH = arrow_path(44.5, 63.5, 78.3)

# Marble veins: wide translucent ribbons that break the flat gradient without
# stealing attention from the mark.
MARBLE_VEINS = (
    (
        "M-8,24 C16,6 40,12 57,28 C73,43 92,44 116,28 L116,36 "
        "C94,52 72,52 55,37 C38,23 16,20 -8,38 Z",
        0.045,
        PAPER,
    ),
    (
        "M-8,58 C18,42 40,48 58,64 C74,78 94,80 116,64 L116,71 "
        "C96,87 72,86 54,71 C37,57 17,54 -8,70 Z",
        0.04,
        CYAN,
    ),
    (
        "M-8,94 C20,78 44,84 62,98 C78,110 96,110 116,98 L116,106 "
        "C96,118 72,118 52,104 C34,92 14,90 -8,104 Z",
        0.035,
        PAPER,
    ),
    ("M88,-8 C78,10 84,28 99,42 C108,51 111,62 107,74 L116,74 L116,-8 Z", 0.035, CYAN),
)
def background_shapes() -> list[Shape]:
    shapes = [
        Shape(
            path=points_to_path(rounded_rect_points(0, 0, VIEWPORT, VIEWPORT, 0.0)),
            gradient=BACKGROUND_GRADIENT,
            start=(0.0, 0.0),
            end=(VIEWPORT, VIEWPORT),
            name="bg-base",
        )
    ]
    for index, (path, alpha, color) in enumerate(MARBLE_VEINS):
        shapes.append(
            Shape(path=path, color=with_alpha(color, alpha), name=f"bg-vein-{index}")
        )
    return shapes


def foreground_shapes() -> list[Shape]:
    """The launcher mark: folded page + gradient M + bidi flow cue."""
    shadow = rounded_rect_points(38.5, 31.5, 73.5, 84.0, 7.0)
    return [
        Shape(
            path=points_to_path(shadow),
            color="#02071A",
            alpha=0.26,
            shadow=True,
            name="page-shadow",
        ),
        Shape(
            path=PAGE_PATH,
            gradient=PAPER_GRADIENT,
            start=(54.0, 28.0),
            end=(54.0, 80.0),
            name="page",
        ),
        Shape(path=FOLD_PATH, color=FOLD, name="page-fold"),
        Shape(
            path=MONOGRAM_PATH,
            gradient=MONOGRAM_GRADIENT,
            start=(40.0, 44.0),
            end=(68.0, 70.0),
            name="monogram",
        ),
        Shape(path=RTL_ARROW_PATH, color=INK, alpha=0.85, name="bidi-rtl"),
        Shape(path=LTR_ARROW_PATH, color=INK, alpha=0.85, name="bidi-ltr"),
    ]


def monochrome_shapes() -> list[Shape]:
    """Themed-icon silhouette: page outline, solid monogram, bidi cue."""
    return [
        Shape(
            path=PAGE_PATH,
            color="#FFFFFFFF",
            alpha=0.0,
            stroke=4.6,
            name="mono-page",
        ),
        Shape(path=MONOGRAM_PATH, color="#FFFFFFFF", name="mono-monogram"),
        Shape(path=RTL_ARROW_PATH, color="#FFFFFFFF", name="mono-bidi-rtl"),
        Shape(path=LTR_ARROW_PATH, color="#FFFFFFFF", name="mono-bidi-ltr"),
    ]


# ------------------------------------------------------------- rasteriser ---
def gradient_image(size: int, shape: Shape, scale: float) -> Image.Image:
    """Linear gradient rendered at a small size and smoothly upscaled.

    Gradient ramps are continuous, so rendering the ramp at ~256px and
    upscaling is visually identical to a per-pixel render - and orders of
    magnitude faster for the 4x supersampled layers.
    """
    assert shape.gradient is not None and shape.start and shape.end
    samples = min(size, GRADIENT_SAMPLES)
    factor = samples / size
    x0, y0 = (shape.start[0] * scale * factor, shape.start[1] * scale * factor)
    x1, y1 = (shape.end[0] * scale * factor, shape.end[1] * scale * factor)
    dx, dy = x1 - x0, y1 - y0
    length_sq = dx * dx + dy * dy or 1.0
    pixels = bytearray()
    for py in range(samples):
        for px in range(samples):
            t = ((px - x0) * dx + (py - y0) * dy) / length_sq
            pixels += bytes(sample_gradient(shape.gradient, t))
    image = Image.frombytes("RGB", (samples, samples), bytes(pixels))
    if samples != size:
        image = image.resize((size, size), Image.BICUBIC)
    return image.convert("RGBA")


def render_layer(shapes: Sequence[Shape], size: int, scale: float) -> Image.Image:
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    for shape in shapes:
        mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(mask)
        contours = parse_path(shape.path)
        for contour in contours:
            points = [(x * scale, y * scale) for x, y in contour]
            if len(points) >= 3:
                draw.polygon(points, fill=255)
        if shape.stroke is not None:
            outline = Image.new("L", (size, size), 0)
            outline_draw = ImageDraw.Draw(outline)
            for contour in contours:
                points = [(x * scale, y * scale) for x, y in contour]
                outline_draw.line(
                    points + [points[0]],
                    fill=255,
                    width=max(1, round(shape.stroke * scale)),
                    joint="curve",
                )
            mask = Image.composite(mask, outline, outline)

        if shape.shadow:
            mask = mask.filter(ImageFilter.GaussianBlur(radius=2.2 * scale))

        if shape.gradient is not None:
            fill_image = gradient_image(size, shape, scale)
        else:
            fill_image = Image.new("RGBA", (size, size), (*rgb(shape.color or "#000000"), 255))

        alpha = round(max(0.0, min(1.0, shape.alpha)) * 255)
        if alpha < 255:
            mask = mask.point(lambda value, a=alpha: value * a // 255)

        layer.paste(fill_image, (0, 0), mask)

    return layer


def rounded_square_mask(size: int, radius_ratio: float = 0.225) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=round(size * radius_ratio), fill=255
    )
    return mask


def circle_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def compose(
    size: int,
    foreground_scale: float,
    mask: Image.Image,
    veiled: bool = False,
) -> Image.Image:
    """Composite background + (optionally enlarged) mark, then apply a mask."""
    big = size * SUPERSAMPLE
    scale = big / VIEWPORT
    background = render_layer(background_shapes(), big, scale)
    foreground = render_layer(foreground_shapes(), big, scale * foreground_scale)

    if foreground_scale > 1.0:
        offset = round((foreground_scale - 1.0) * big / 2.0)
        shifted = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        shifted.paste(foreground, (-offset, -offset), foreground)
        foreground = shifted

    if veiled:
        foreground = foreground.transform(
            foreground.size, Image.AFFINE, (1, 0, 0, 0, 1, -0.06 * big), resample=Image.BICUBIC
        )

    composed = Image.alpha_composite(background, foreground)
    composed = composed.resize((size, size), Image.LANCZOS)
    if mask is not None:
        composed.putalpha(mask.resize((size, size), Image.LANCZOS))
    return composed


# ------------------------------------------------------------ XML emitters ---
HEADER = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    "<!-- Generated by tools/generate-icons.py - do not edit by hand. -->\n"
)


def shape_to_xml(shape: Shape, indent: str = "    ") -> str:
    attributes = [f'android:pathData="{shape.path}"']
    if shape.stroke is not None:
        attributes += [
            f'android:strokeWidth="{shape.stroke:g}"',
            'android:strokeLineJoin="round"',
            'android:strokeLineCap="round"',
            f'android:strokeColor="{with_alpha(shape.color or "#FFFFFFFF", shape.alpha)}"',
            'android:fillColor="#00000000"',
        ]
    if shape.gradient is not None:
        assert shape.start and shape.end
        items = "\n".join(
            f'{indent}      <item android:offset="{offset:g}" '
            f'android:color="{with_alpha(color, shape.alpha)}" />'
            for offset, color in shape.gradient
        )
        gradient = (
            f'{indent}    <aapt:attr name="android:fillColor">\n'
            f'{indent}      <gradient android:type="linear" '
            f'android:startX="{shape.start[0]:g}" android:startY="{shape.start[1]:g}" '
            f'android:endX="{shape.end[0]:g}" android:endY="{shape.end[1]:g}">\n'
            f"{items}\n"
            f"{indent}      </gradient>\n"
            f"{indent}    </aapt:attr>\n"
        )
    else:
        gradient = ""
    if shape.stroke is None:
        attributes.append(f'android:fillColor="{with_alpha(shape.color or "#00000000", shape.alpha)}"')
    body = "".join(f"{indent}    {attribute}\n" for attribute in attributes)
    return f"{indent}<path\n{body}{gradient}{indent}/>\n"


def vector_document(
    width_dp: float, height_dp: float, body: str
) -> str:
    return (
        HEADER
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        + f'    android:width="{width_dp:g}dp"\n'
        + f'    android:height="{height_dp:g}dp"\n'
        + f'    android:viewportWidth="{VIEWPORT:g}"\n'
        + f'    android:viewportHeight="{VIEWPORT:g}">\n'
        + body
        + "</vector>\n"
    )


def legacy_vector(round_icon: bool, scale: float = 1.22) -> str:
    background = "".join(
        shape_to_xml(shape, indent="        " if round_icon else "    ")
        for shape in background_shapes()
    )
    foreground = "".join(shape_to_xml(shape, indent="        ") for shape in foreground_shapes())
    if round_icon:
        background = (
            '    <group android:name="round-clip">\n'
            f'        <clip-path android:pathData="{circle_path(54, 54, 54)}" />\n'
            f"{background}"
            "    </group>\n"
        )
    body = (
        background
        + '    <group android:name="mark" android:pivotX="54" android:pivotY="54" '
        + f'android:scaleX="{scale:g}" android:scaleY="{scale:g}">\n'
        + foreground
        + "    </group>\n"
    )
    return vector_document(48, 48, body)


def write(path: str, content: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)
    print("wrote", os.path.relpath(path, ROOT))


LEGACY_DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FEATURE_FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FEATURE_FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def in_app_mark() -> None:
    """Small single-colour mark used inside the app UI (tinted with the theme)."""
    shapes = [
        Shape(path=PAGE_PATH, color="#FFFFFFFF", alpha=0.0, stroke=5.0, name="mark-page"),
        Shape(path=MONOGRAM_PATH, color="#FFFFFFFF", name="mark-monogram"),
        Shape(path=RTL_ARROW_PATH, color="#FFFFFFFF", alpha=0.6, name="mark-bidi-rtl"),
        Shape(path=LTR_ARROW_PATH, color="#FFFFFFFF", alpha=0.6, name="mark-bidi-ltr"),
    ]
    write(
        os.path.join(RES, "drawable", "ic_marblemd_mark.xml"),
        vector_document(48, 48, "".join(shape_to_xml(shape) for shape in shapes)),
    )


def feature_graphic() -> None:
    if not os.path.exists(FEATURE_FONT_BOLD):
        print("skip feature graphic (DejaVu fonts missing)")
        return

    width, height = 1024, 500
    canvas = Image.new("RGB", (width, height))
    pixels = canvas.load()
    for y in range(height):
        for x in range(width):
            t = (x / width) * 0.62 + (y / height) * 0.38
            pixels[x, y] = sample_gradient(BACKGROUND_GRADIENT, t)
    canvas = canvas.convert("RGBA")

    veins = render_layer(background_shapes(), width, 1.0)
    canvas = Image.alpha_composite(canvas, Image.new("RGBA", canvas.size, (0, 0, 0, 0)))
    canvas.alpha_composite(veins.resize(canvas.size), (0, 0))

    icon = compose(512, 1.16, rounded_square_mask(512, 0.235), veiled=True)
    glow = Image.new("RGBA", (632, 632), (0, 0, 0, 0))
    glow.paste(icon, (60, 60), icon)
    glow = glow.filter(ImageFilter.GaussianBlur(26))
    canvas.alpha_composite(glow.resize((316, 316)), (128, 92))
    canvas.alpha_composite(icon.resize((256, 256), Image.LANCZOS), (158, 122))

    draw = ImageDraw.Draw(canvas)
    title = ImageFont.truetype(FEATURE_FONT_BOLD, 122)
    subtitle = ImageFont.truetype(FEATURE_FONT, 34)
    draw.text((470, 168), "Marble", font=title, fill=rgb(PAPER))
    offset = draw.textlength("Marble", font=title)
    draw.text((470 + offset, 168), "MD", font=title, fill=rgb(CYAN))
    draw.text(
        (474, 306),
        "MULTILINGUAL MARKDOWN READER",
        font=subtitle,
        fill=(168, 189, 232),
    )
    draw.text(
        (474, 350),
        "RTL • LTR • CUSTOM TTF • EDITOR",
        font=subtitle,
        fill=(120, 146, 200),
    )
    canvas.convert("RGB").save(os.path.join(BRANDING, "marblemd-feature-graphic.png"))
    print("wrote branding/marblemd-feature-graphic.png")


def main() -> None:
    # Adaptive icon layers: the 108dp canvas leaves bleed for launcher masks,
    # so the mark itself is drawn inside the 66dp safe zone.
    write(
        os.path.join(RES, "drawable", "ic_launcher_background.xml"),
        vector_document(108, 108, "".join(shape_to_xml(s) for s in background_shapes())),
    )
    write(
        os.path.join(RES, "drawable", "ic_launcher_foreground.xml"),
        vector_document(108, 108, "".join(shape_to_xml(s) for s in foreground_shapes())),
    )
    write(
        os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"),
        vector_document(108, 108, "".join(shape_to_xml(s) for s in monochrome_shapes())),
    )

    adaptive = (
        HEADER
        + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        + '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        + '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        + '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        + "</adaptive-icon>\n"
    )
    for name in ("ic_launcher", "ic_launcher_round"):
        write(os.path.join(RES, "mipmap-anydpi-v26", f"{name}.xml"), adaptive)

    # Legacy vector icons for API 24/25 launchers: no bleed, larger mark.
    write(os.path.join(RES, "mipmap-anydpi", "ic_launcher.xml"), legacy_vector(False))
    write(os.path.join(RES, "mipmap-anydpi", "ic_launcher_round.xml"), legacy_vector(True))

    # Legacy bitmaps: every density bucket, square + round, so old launchers,
    # third-party launchers and stores always get a pixel-perfect asset.
    for density, size in LEGACY_DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        compose(size, 1.22, rounded_square_mask(size), veiled=True).save(
            os.path.join(folder, "ic_launcher.png")
        )
        compose(size, 1.22, circle_mask(size), veiled=True).save(
            os.path.join(folder, "ic_launcher_round.png")
        )
        print("wrote", os.path.relpath(folder, ROOT), f"({size}px square + round)")

    compose(512, 1.22, rounded_square_mask(512, 0.235), veiled=True).save(
        os.path.join(BRANDING, "marblemd-app-icon.png")
    )
    compose(512, 1.22, Image.new("L", (512, 512), 255), veiled=True).convert("RGB").save(
        os.path.join(BRANDING, "marblemd-play-store-512.png")
    )
    print("wrote branding/marblemd-app-icon.png (512px)")
    print("wrote branding/marblemd-play-store-512.png (512px, full bleed)")

    in_app_mark()
    feature_graphic()


if __name__ == "__main__":
    main()
