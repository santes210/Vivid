#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generador de la paleta de marca de Vivid ("Vivid Sunset").

Por qué existe este script
--------------------------
La paleta anterior era, literalmente, la paleta baseline de Material 3
(0xFF6750A4 / 0xFFEADDFF / 0xFF7D5260…): la que sale por defecto en cualquier
plantilla de Android Studio. Eso hace que la app parezca una plantilla.

Este script construye rampas tonales *perceptuales* a partir de unas semillas
de marca y emite `theme/VividColors.kt` con los 30+ roles de color de M3 para
tema claro y oscuro, más los acentos de producto (corazón del like, anillo de
historias, verificado, en línea).

Modelo de color
---------------
Material genera sus rampas en HCT (hue de CAM16 + tone = L* de CIELAB).
Aquí se usa una aproximación fiel y sin dependencias:

  * el **tono** (0..100) es exactamente L* de CIELAB, igual que HCT;
  * el **matiz** y el **croma** se fijan en OkLCh, que es perceptualmente
    uniforme y evita los virajes de matiz de HSL (el clásico "el rosa se me
    volvió naranja al aclararlo");
  * para cada tono se busca por bisección la L de OkLab que da ese L* exacto y
    después se reduce el croma hasta que el color entra en gamut sRGB.

Uso
---
    python3 scripts/generate_vivid_palette.py \
        > vivid-app/app/src/main/java/com/vivid/app/theme/VividColors.kt

Cambiar la identidad de marca = cambiar SEEDS y volver a ejecutar.
"""

from __future__ import annotations

import math

# ── sRGB ⇄ CIELAB ────────────────────────────────────────────────────────────

def _srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _linear_to_srgb(c: float) -> float:
    return 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055


_D65 = (0.95047, 1.0, 1.08883)


def _linear_rgb_to_xyz(r: float, g: float, b: float) -> tuple[float, float, float]:
    x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
    return x, y, z


def _lab_l(r: float, g: float, b: float) -> float:
    """L* de CIELAB (= "tone" de HCT) para un sRGB lineal."""
    _, y, _ = _linear_rgb_to_xyz(r, g, b)
    y /= _D65[1]
    fy = y ** (1 / 3) if y > 216 / 24389 else (24389 / 27 * y + 16) / 116
    return 116 * fy - 16


# ── OkLab / OkLCh ────────────────────────────────────────────────────────────

def _oklab_to_linear_rgb(L: float, a: float, b: float) -> tuple[float, float, float]:
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    return (
        +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )


def _oklch_to_linear_rgb(L: float, C: float, h_deg: float) -> tuple[float, float, float]:
    h = math.radians(h_deg)
    return _oklab_to_linear_rgb(L, C * math.cos(h), C * math.sin(h))


def _in_gamut(rgb: tuple[float, float, float], eps: float = 1e-4) -> bool:
    return all(-eps <= c <= 1 + eps for c in rgb)


def _tone_color(hue: float, chroma: float, tone: float) -> str:
    """Devuelve el hex del color con ese matiz/croma cuyo L* de CIELAB es `tone`."""
    if tone >= 100:
        return "#FFFFFF"
    if tone <= 0:
        return "#000000"

    # 1) L de OkLab que produce el L* pedido (monótona ⇒ bisección segura).
    lo, hi = 0.0, 1.0
    for _ in range(48):
        mid = (lo + hi) / 2
        rgb = _oklch_to_linear_rgb(mid, 0.0, hue)
        if _lab_l(*rgb) < tone:
            lo = mid
        else:
            hi = mid
    ok_l = (lo + hi) / 2

    # 2) Croma máximo representable en sRGB para ese matiz y esa luminosidad.
    target = min(chroma, 0.4)
    lo_c, hi_c = 0.0, target
    if not _in_gamut(_oklch_to_linear_rgb(ok_l, target, hue)):
        for _ in range(40):
            mid = (lo_c + hi_c) / 2
            if _in_gamut(_oklch_to_linear_rgb(ok_l, mid, hue)):
                lo_c = mid
            else:
                hi_c = mid
        target = lo_c

    # 3) Reajuste fino de L: añadir croma mueve un poco el L* real.
    lo, hi = 0.0, 1.0
    for _ in range(48):
        mid = (lo + hi) / 2
        rgb = _oklch_to_linear_rgb(mid, target, hue)
        if _lab_l(*rgb) < tone:
            lo = mid
        else:
            hi = mid
    rgb = _oklch_to_linear_rgb((lo + hi) / 2, target, hue)

    out = [max(0.0, min(1.0, _linear_to_srgb(max(0.0, min(1.0, c))))) for c in rgb]
    return "#" + "".join(f"{round(c * 255):02X}" for c in out)


class Ramp:
    """Rampa tonal de un matiz: `ramp[40]` = tono 40."""

    def __init__(self, hue: float, chroma: float) -> None:
        self.hue, self.chroma = hue, chroma
        self._cache: dict[float, str] = {}

    def __getitem__(self, tone: float) -> str:
        if tone not in self._cache:
            self._cache[tone] = _tone_color(self.hue, self.chroma, tone)
        return self._cache[tone]


# ── Semillas de marca "Vivid Sunset" ─────────────────────────────────────────
#
#   primary    magenta-coral  → la marca: energía, foto, "vivid"
#   secondary  rosa apagado   → soporte, no compite con el contenido
#   tertiary   ámbar/dorado   → el atardecer; acentos y momentos de celebración
#   error      rojo estándar  → señal de peligro, deliberadamente distinto al
#                               magenta de marca (matiz más cálido y bajo croma)
#   neutral    gris cálido    → nunca gris azulado: las fotos se ven mejor sobre
#                               neutros cálidos y refuerza el atardecer
SEEDS = {
    "primary": Ramp(hue=6.0, chroma=0.230),
    "secondary": Ramp(hue=8.0, chroma=0.075),
    "tertiary": Ramp(hue=68.0, chroma=0.150),
    "error": Ramp(hue=27.0, chroma=0.160),
    "neutral": Ramp(hue=40.0, chroma=0.008),
    "neutral_variant": Ramp(hue=30.0, chroma=0.022),
}

# Acentos de producto: no son roles de M3, son "el corazón rojo de Vivid".
ACCENTS = {
    "Like": _tone_color(10.0, 0.240, 55),
    "LikeDark": _tone_color(10.0, 0.240, 68),
    "StoryRingStart": _tone_color(6.0, 0.230, 60),
    "StoryRingMid": _tone_color(35.0, 0.200, 65),
    "StoryRingEnd": _tone_color(75.0, 0.170, 72),
    "Verified": _tone_color(240.0, 0.140, 55),
    "Online": _tone_color(150.0, 0.150, 55),
    "Live": _tone_color(25.0, 0.220, 52),
}


def kt(hex_color: str) -> str:
    return f"Color(0xFF{hex_color.lstrip('#')})"


LIGHT = [
    ("Primary", "primary", 40), ("OnPrimary", "primary", 100),
    ("PrimaryContainer", "primary", 90), ("OnPrimaryContainer", "primary", 10),
    ("Secondary", "secondary", 40), ("OnSecondary", "secondary", 100),
    ("SecondaryContainer", "secondary", 90), ("OnSecondaryContainer", "secondary", 10),
    ("Tertiary", "tertiary", 40), ("OnTertiary", "tertiary", 100),
    ("TertiaryContainer", "tertiary", 90), ("OnTertiaryContainer", "tertiary", 10),
    ("Error", "error", 40), ("OnError", "error", 100),
    ("ErrorContainer", "error", 90), ("OnErrorContainer", "error", 10),
    ("Background", "neutral", 99), ("OnBackground", "neutral", 10),
    ("Surface", "neutral", 99), ("OnSurface", "neutral", 10),
    ("SurfaceVariant", "neutral_variant", 90), ("OnSurfaceVariant", "neutral_variant", 30),
    ("Outline", "neutral_variant", 50), ("OutlineVariant", "neutral_variant", 80),
    ("SurfaceContainerLowest", "neutral", 100), ("SurfaceContainerLow", "neutral", 96),
    ("SurfaceContainer", "neutral", 94), ("SurfaceContainerHigh", "neutral", 92),
    ("SurfaceContainerHighest", "neutral", 90),
    ("SurfaceBright", "neutral", 98), ("SurfaceDim", "neutral", 87),
    ("InverseSurface", "neutral", 20), ("InverseOnSurface", "neutral", 95),
    ("InversePrimary", "primary", 80), ("Scrim", "neutral", 0),
]

DARK = [
    ("Primary", "primary", 80), ("OnPrimary", "primary", 20),
    ("PrimaryContainer", "primary", 30), ("OnPrimaryContainer", "primary", 90),
    ("Secondary", "secondary", 80), ("OnSecondary", "secondary", 20),
    ("SecondaryContainer", "secondary", 30), ("OnSecondaryContainer", "secondary", 90),
    ("Tertiary", "tertiary", 80), ("OnTertiary", "tertiary", 20),
    ("TertiaryContainer", "tertiary", 30), ("OnTertiaryContainer", "tertiary", 90),
    ("Error", "error", 80), ("OnError", "error", 20),
    ("ErrorContainer", "error", 30), ("OnErrorContainer", "error", 90),
    ("Background", "neutral", 6), ("OnBackground", "neutral", 90),
    ("Surface", "neutral", 6), ("OnSurface", "neutral", 90),
    ("SurfaceVariant", "neutral_variant", 30), ("OnSurfaceVariant", "neutral_variant", 80),
    ("Outline", "neutral_variant", 60), ("OutlineVariant", "neutral_variant", 30),
    ("SurfaceContainerLowest", "neutral", 4), ("SurfaceContainerLow", "neutral", 10),
    ("SurfaceContainer", "neutral", 12), ("SurfaceContainerHigh", "neutral", 17),
    ("SurfaceContainerHighest", "neutral", 22),
    ("SurfaceBright", "neutral", 24), ("SurfaceDim", "neutral", 6),
    ("InverseSurface", "neutral", 90), ("InverseOnSurface", "neutral", 20),
    ("InversePrimary", "primary", 40), ("Scrim", "neutral", 0),
]

HEADER = '''package com.vivid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de marca "Vivid Sunset" — GENERADA, no editar a mano.
 *
 *   Fuente: scripts/generate_vivid_palette.py
 *   Regenerar:
 *     python3 scripts/generate_vivid_palette.py > \\
 *       vivid-app/app/src/main/java/com/vivid/app/theme/VividColors.kt
 *
 * Se usa cuando NO hay Material You dinámico (Android < 12, o el usuario
 * desactivó "Color dinámico" en Ajustes → Apariencia). Con color dinámico,
 * el esquema sale del wallpaper y solo se conservan los acentos de producto
 * de [VividAccentColors], armonizados hacia el color del sistema.
 *
 * Construcción (ver el script para el detalle): matiz y croma fijos en OkLCh,
 * tono = L* de CIELAB, exactamente el mismo eje de "tone" que usa HCT en
 * Material. Resultado: rampas sin virajes de matiz y con contraste
 * predecible entre pares on-/container.
 *
 * Semillas:
 *   primary   magenta-coral (hue 6°, croma 0.23)   → identidad
 *   secondary rosa apagado  (hue 8°, croma 0.075)  → soporte
 *   tertiary  ámbar         (hue 68°, croma 0.15)  → atardecer / celebración
 *   error     rojo          (hue 27°, croma 0.16)  → peligro, distinto al brand
 *   neutral   gris CÁLIDO   (croma 0.008)          → las fotos respiran mejor
 */
'''


def emit_object(name: str, doc: str, roles: list[tuple[str, str, int]]) -> str:
    width = max(len(role) for role, _, _ in roles)
    lines = [f"/** {doc} */", f"internal object {name} {{"]
    for role, ramp, tone in roles:
        color = SEEDS[ramp][tone]
        lines.append(f"    val {role.ljust(width)} = {kt(color)}  // {ramp} {tone}")
    lines.append(f"    val {'SurfaceTint'.ljust(width)} = Primary")
    lines.append("}")
    return "\n".join(lines)


def main() -> None:
    print(HEADER)
    print(emit_object(
        "VividBrandColors",
        "Roles M3 en tema claro. Tonos: primary 40/90, neutrales cálidos 99→90.",
        LIGHT,
    ))
    print()
    print(emit_object(
        "VividBrandColorsDark",
        "Roles M3 en tema oscuro. Tonos: primary 80/30, neutrales cálidos 6→22.",
        DARK,
    ))
    print()
    print('''/**
 * Acentos de producto. NO son roles de Material: son constantes de marca que
 * deben sobrevivir al color dinámico (un corazón de like verde porque el
 * wallpaper es verde sería un bug de producto, no una feature).
 *
 * [com.vivid.app.theme.harmonizeWith] los inclina ligeramente hacia el color
 * del sistema para que convivan con la paleta dinámica sin perder identidad.
 */
object VividAccentColors {''')
    width = max(len(k) for k in ACCENTS)
    for name, color in ACCENTS.items():
        print(f"    val {name.ljust(width)} = {kt(color)}")
    print("}")


if __name__ == "__main__":
    main()
