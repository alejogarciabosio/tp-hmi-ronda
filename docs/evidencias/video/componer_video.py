#!/usr/bin/env python3
"""Arma docs/evidencias/video/demo-ronda.mp4 a partir de los cuadros de `./gradlew :app:recordVideo`.

Lee app/build/video-frames/frames.json (archivo, duración, escenario, título y texto de cada cuadro),
compone cada cuadro a 1920x1080 con PIL (teléfono a la izquierda, panel de texto a la derecha),
agrega portada, cierre y fundidos entre partes, y codifica con ffmpeg (H.264, yuv420p, 30 fps).

Uso, desde la raíz del repo:
    python3 docs/evidencias/video/componer_video.py [--frames DIR] [--salida MP4] [--muestras DIR]
"""
import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parents[3]
FUENTES = RAIZ / "app/src/main/res/font"

W, H = 1920, 1080
FPS = 30
FUNDIDO_S = 0.6

FONDO = (0x14, 0x13, 0x0F)
FONDO_2 = (0x1C, 0x1B, 0x17)
LINEA = (0x3A, 0x37, 0x2E)
AMBAR = (0xF2, 0xA9, 0x00)
HUESO = (0xE8, 0xE2, 0xD0)
HUESO_TENUE = (0x9C, 0x96, 0x86)

ESCENARIOS = {
    0: "Apertura",
    1: "Misión normal",
    2: "Batería baja",
    3: "Desconexión",
    4: "Obstáculo en el pasillo",
    5: "Persona en zona restringida",
}

# Teléfono: pantalla de ~930 px de alto dentro de un marco simple de ~962 px.
PANTALLA_H = 930
BISEL = 16
TEL_X = 170

# Panel de texto
PANEL_X = 790
PANEL_W = 1000


def fuente(nombre, tam, variacion=None):
    f = ImageFont.truetype(str(FUENTES / nombre), tam)
    if variacion:
        f.set_variation_by_name(variacion)
    return f


F_TITULO = fuente("archivo_variable.ttf", 64, "Bold")
F_NUMERO = fuente("archivo_variable.ttf", 64, "Bold")
F_TEXTO = fuente("ibm_plex_sans_variable.ttf", 44, "Regular")
F_MONO = fuente("ibm_plex_mono_medium.ttf", 24)
F_PORTADA = fuente("archivo_variable.ttf", 150, "Black")
F_PORTADA_SUB = fuente("archivo_variable.ttf", 56, "SemiBold")
F_PORTADA_MATERIA = fuente("ibm_plex_sans_variable.ttf", 36, "Medium")
F_NOTA = fuente("ibm_plex_sans_variable.ttf", 32, "Regular")
F_CIERRE = fuente("ibm_plex_sans_variable.ttf", 40, "Regular")
F_CIERRE_B = fuente("ibm_plex_sans_variable.ttf", 40, "SemiBold")


def envolver(draw, texto, f, ancho):
    """Parte el texto en líneas que entran en `ancho` px."""
    lineas, actual = [], ""
    for palabra in texto.split():
        prueba = (actual + " " + palabra).strip()
        if draw.textlength(prueba, font=f) <= ancho:
            actual = prueba
        else:
            if actual:
                lineas.append(actual)
            actual = palabra
    if actual:
        lineas.append(actual)
    return lineas


def fondo():
    img = Image.new("RGB", (W, H), FONDO)
    d = ImageDraw.Draw(img)
    # Franja derecha apenas más clara, para separar el panel del teléfono.
    d.rectangle([PANEL_X - 90, 0, W, H], fill=FONDO_2)
    d.line([PANEL_X - 90, 0, PANEL_X - 90, H], fill=LINEA, width=2)
    return img


def telefono(img, captura):
    pw = round(captura.width * PANTALLA_H / captura.height)
    pantalla = captura.convert("RGB").resize((pw, PANTALLA_H), Image.LANCZOS)
    cuerpo_w, cuerpo_h = pw + 2 * BISEL, PANTALLA_H + 2 * BISEL
    x0 = TEL_X + (PANEL_X - 90 - TEL_X - cuerpo_w) // 2 - 40
    y0 = (H - cuerpo_h) // 2
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([x0 - 2, y0 - 2, x0 + cuerpo_w + 1, y0 + cuerpo_h + 1], radius=54, fill=LINEA)
    d.rounded_rectangle([x0, y0, x0 + cuerpo_w - 1, y0 + cuerpo_h - 1], radius=52, fill=(0x0A, 0x0A, 0x08))
    mascara = Image.new("L", (pw, PANTALLA_H), 0)
    ImageDraw.Draw(mascara).rounded_rectangle([0, 0, pw - 1, PANTALLA_H - 1], radius=38, fill=255)
    img.paste(pantalla, (x0 + BISEL, y0 + BISEL), mascara)


def progreso(d, escenario):
    y = 930
    etiqueta = f"ESCENARIO {escenario}/5" if escenario else "ANTES DE LOS ESCENARIOS"
    d.text((PANEL_X, y), etiqueta, font=F_MONO, fill=HUESO_TENUE)
    bx = PANEL_X
    for i in range(1, 6):
        color = AMBAR if escenario and i <= escenario else LINEA
        d.rectangle([bx, y + 44, bx + 64, y + 49], fill=color)
        bx += 76


def cuadro_app(captura, escenario, texto):
    img = fondo()
    telefono(img, captura)
    d = ImageDraw.Draw(img)
    nombre = ESCENARIOS[escenario]
    titulo = f"{escenario} · {nombre}" if escenario else nombre
    lineas_t = envolver(d, titulo, F_TITULO, PANEL_W)
    y = 130
    for ln in lineas_t:
        d.text((PANEL_X, y), ln, font=F_TITULO, fill=AMBAR)
        y += 80
    d.rectangle([PANEL_X, y + 24, PANEL_X + 72, y + 29], fill=AMBAR)
    lineas = envolver(d, texto, F_TEXTO, PANEL_W)
    alto = 66
    if len(lineas) * alto > 520:
        raise SystemExit(f"Texto demasiado largo para el panel: {texto!r}")
    ty = max(y + 110, 470 - len(lineas) * alto // 2)
    for ln in lineas:
        d.text((PANEL_X, ty), ln, font=F_TEXTO, fill=HUESO)
        ty += alto
    progreso(d, escenario)
    return img


def centrado(d, y, texto, f, color):
    ancho = d.textlength(texto, font=f)
    d.text(((W - ancho) / 2, y), texto, font=f, fill=color)


def portada():
    img = Image.new("RGB", (W, H), FONDO)
    d = ImageDraw.Draw(img)
    centrado(d, 220, "RONDA", F_PORTADA, AMBAR)
    centrado(d, 420, "Supervisión de un Unitree Go2 en rondas nocturnas", F_PORTADA_SUB, HUESO)
    d.rectangle([W / 2 - 40, 530, W / 2 + 40, 535], fill=AMBAR)
    centrado(d, 575, "Desarrollo de Aplicaciones I · UADE", F_PORTADA_MATERIA, HUESO_TENUE)
    nota = ("Todo corre sobre un simulador local, sin robot, servidor ni internet. "
            "El contrato JSON es una propuesta del grupo, no el protocolo oficial de Unitree.")
    caja_w = 1160
    lineas = envolver(d, nota, F_NOTA, caja_w - 80)
    caja_h = len(lineas) * 46 + 56
    x0, y0 = (W - caja_w) // 2, 720
    d.rounded_rectangle([x0, y0, x0 + caja_w, y0 + caja_h], radius=10, fill=FONDO_2, outline=LINEA, width=2)
    y = y0 + 28
    for ln in lineas:
        centrado(d, y, ln, F_NOTA, HUESO)
        y += 46
    return img


def cierre():
    img = Image.new("RGB", (W, H), FONDO)
    d = ImageDraw.Draw(img)
    x = 260
    d.text((x, 200), "Para cerrar", font=F_TITULO, fill=AMBAR)
    d.rectangle([x, 300, x + 72, 305], fill=AMBAR)
    puntos = [
        ("Arquitectura MVVM + Repository.",
         "Cambiar el simulador por un robot real es escribir otro transporte, sin tocar las pantallas."),
        ("Detención operativa (software).", "No reemplaza la parada física del robot."),
        ("Contrato JSON propio.", "Es una propuesta del grupo, no el protocolo oficial de Unitree."),
    ]
    y = 380
    for i, (fuerte, resto) in enumerate(puntos, 1):
        d.text((x, y), f"{i}", font=F_TITULO, fill=AMBAR)
        lineas = envolver(d, fuerte + " " + resto, F_CIERRE, 1300)
        ly = y + 10
        for j, ln in enumerate(lineas):
            if j == 0 and ln.startswith(fuerte):
                d.text((x + 90, ly), fuerte, font=F_CIERRE_B, fill=HUESO)
                d.text((x + 90 + d.textlength(fuerte + " ", font=F_CIERRE_B), ly), ln[len(fuerte):].strip(),
                       font=F_CIERRE, fill=HUESO)
            else:
                d.text((x + 90, ly), ln, font=F_CIERRE, fill=HUESO)
            ly += 56
        y = max(y + 150, ly + 50)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frames", default=str(RAIZ / "app/build/video-frames"))
    ap.add_argument("--salida", default=str(RAIZ / "docs/evidencias/video/demo-ronda.mp4"))
    ap.add_argument("--trabajo", default=str(RAIZ / "app/build/video-compuesto"))
    ap.add_argument("--muestras", help="carpeta donde copiar un cuadro compuesto por parte")
    args = ap.parse_args()

    frames_dir = Path(args.frames)
    cuadros = json.loads((frames_dir / "frames.json").read_text(encoding="utf-8"))
    trabajo = Path(args.trabajo)
    shutil.rmtree(trabajo, ignore_errors=True)
    trabajo.mkdir(parents=True)

    # Partes: portada, una por escenario (0 = apertura) y cierre. Cada parte es una lista de (imagen, segundos).
    partes = [("portada", [(portada(), 6.0)])]
    actual = None
    for c in cuadros:
        if actual is None or actual[0] != c["escenario"]:
            actual = (c["escenario"], [])
            partes.append(actual)
        img = cuadro_app(Image.open(frames_dir / c["archivo"]), c["escenario"], c["texto"])
        actual[1].append((img, float(c["duracion"])))
    partes.append(("cierre", [(cierre(), 10.0)]))

    lista = []  # (ruta, segundos)
    n = 0

    def guardar(img, seg):
        nonlocal n
        ruta = trabajo / f"c_{n:05d}.png"
        img.save(ruta, compress_level=1)
        lista.append((ruta, seg))
        n += 1

    pasos = int(FUNDIDO_S * FPS)
    negro = Image.new("RGB", (W, H), (0, 0, 0))
    liso = Image.new("RGB", (W, H), FONDO)
    tiempos = []
    t = 0.0
    for i, (nombre, items) in enumerate(partes):
        primero = items[0][0]
        anterior = partes[i - 1][1][-1][0] if i > 0 else negro
        # Fundido pasando por el fondo liso, así los textos de dos partes nunca se superponen.
        mitad = pasos // 2
        for k in range(1, mitad + 1):
            guardar(Image.blend(anterior, liso, k / mitad), 1 / FPS)
        for k in range(1, pasos - mitad + 1):
            guardar(Image.blend(liso, primero, k / (pasos - mitad + 1)), 1 / FPS)
        t += pasos / FPS
        inicio = t
        for img, seg in items:
            guardar(img, seg)
            t += seg
        tiempos.append((nombre, inicio, t))
        if args.muestras:
            m = Path(args.muestras)
            m.mkdir(parents=True, exist_ok=True)
            idx = len(items) // 2
            items[idx][0].save(m / f"muestra-{i:02d}-{nombre}.png")
    ultimo = partes[-1][1][-1][0]
    for k in range(1, pasos + 1):
        guardar(Image.blend(ultimo, negro, k / pasos), 1 / FPS)
        t += 1 / FPS

    concat = trabajo / "lista.txt"
    with concat.open("w", encoding="utf-8") as f:
        for ruta, seg in lista:
            f.write(f"file '{ruta}'\nduration {seg:.4f}\n")
        f.write(f"file '{lista[-1][0]}'\n")

    salida = Path(args.salida)
    salida.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(concat),
        "-vf", f"fps={FPS},format=yuv420p", "-r", str(FPS),
        "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-tune", "stillimage",
        "-movflags", "+faststart", "-an", str(salida),
    ], check=True)

    for nombre, ini, fin in tiempos:
        etiqueta = ESCENARIOS.get(nombre, nombre) if isinstance(nombre, int) else nombre
        print(f"{int(ini // 60)}:{ini % 60:04.1f} – {int(fin // 60)}:{fin % 60:04.1f}  {etiqueta}  ({fin - ini:.1f} s)")
    print(f"Total: {t:.1f} s → {salida}")


if __name__ == "__main__":
    sys.exit(main())
