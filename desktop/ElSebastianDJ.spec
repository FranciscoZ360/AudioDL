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
    hiddenimports=["build_info"],
    excludes=["tkinter", "unittest", "pydoc", "PySide6.QtQml", "PySide6.QtQuick",
              "PySide6.QtWebEngineCore", "PySide6.QtPdf", "PySide6.Qt3DCore"],
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
    upx=False,
)

coll = COLLECT(exe, a.binaries, a.datas, name="ElSebastianDJ", upx=False)
