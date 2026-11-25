# Luma Desktop (PC)

Versión de escritorio ejecutable de **Luma** diseñada con la experiencia de **Notion** (menú lateral proporcional redimensionable, pestañas múltiples, búsqueda `Ctrl+K`, tabla/galería y vista dividida) y **Canva** (estudio de cuadernos visuales con barra lateral de herramientas, plantillas, formas, capas, notas adhesivas, tablas y exportación HD).

## Diseño adaptable a cualquier resolución
El menú y los paneles se reajustan automáticamente en 5 niveles según el tamaño de la ventana y monitor:
- **Ventana compacta (`< 820px`)**: menú deslizable superpuesto y controles compactos.
- **Pantalla dividida (`820px – 1119px`)**: barra lateral de iconos fina (`56px`) expandible al clic.
- **Portátil (`1120px – 1439px`)**: menú lateral proporcional redimensionable + soporte para Vista Dividida (Lector + Cuaderno).
- **Escritorio FHD / 1440p (`1440px – 1919px`)**: menú completo, lectura en 2 páginas e inspector lateral desplegable.
- **Monitor 4K / UltraWide (`>= 1920px`)**: proporciones amplias para lectura editorial y lienzo simultáneo.

## Cómo generar el ejecutable en tu PC

### Windows (`.exe` instalador + `.exe` portable)
Haz doble clic en `desktop/build-windows.bat` o ejecuta en PowerShell:
```bash
cd desktop
npm install
npm run dist:win
```
Los ejecutables quedarán en `desktop/release/`.

### macOS (`.dmg`) y Linux (`.AppImage` / `.deb`)
```bash
cd desktop
chmod +x build-mac-linux.sh
./build-mac-linux.sh
```

### Automático desde GitHub Actions
El repositorio incluye `.github/workflows/build-pc.yml`, que compila y sube automáticamente los ejecutables para **Windows (`.exe`)**, **macOS (`.dmg`)** y **Linux (`.AppImage` / `.deb`)** desde la pestaña *Actions* de GitHub.

### Ejecutar en modo desarrollo
```bash
cd desktop
npm install
npm run dev
```
