# -*- mode: python ; coding: utf-8 -*-
# PyInstaller: compila El Sebastián DJ para Windows en una carpeta (arranca rápido).
from pathlib import Path

here = Path(SPECPATH)

a = Analysis(
    [str(here / "app.py")],
    pathex=[str(here)],
    datas=[
        (str(here / "assets"), "assets"),
        (str(here / "bin"), "bin"),  # yt-dlp.exe, ffmpeg.exe y deno.exe (los baja Actions)
    ],
    hiddenimports=["build_info", "PySide6.QtMultimedia"],
    excludes=["tkinter", "unittest", "pydoc", "PySide6.QtWebEngineCore", "PySide6.QtPdf",
              "PySide6.Qt3DCore"],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="ElSebastianDJ",
    icon=str(here / "assets" / "icon.ico"),
    console=False,
    # Sin ventana de error que se quede esperando un clic (importante en Actions).
    disable_windowed_traceback=True,
    upx=False,
)

coll = COLLECT(exe, a.binaries, a.datas, name="ElSebastianDJ", upx=False)
