# Avisos de terceros

## exercises-dataset (datos de ejercicios)

La «Biblioteca ampliada» de GymTrack usa **datos** (nombres, categorías, músculos, equipamiento e instrucciones en español)
del repositorio <https://github.com/hasaneyldrm/exercises-dataset>, publicados bajo licencia MIT:

```
MIT License

Copyright (c) 2026 Hasan Emir Yıldırım

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation and data files (the "Software"),
to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Contenido multimedia — NO incluido

Las imágenes y animaciones (GIF) de ese repositorio son © [Gym visual](https://gymvisual.com/) y **no** están cubiertas por la
licencia MIT: se redistribuyen allí con un permiso específico del titular y se rigen por sus
[Términos y Condiciones](https://gymvisual.com/content/3-terms-and-conditions-of-use). **GymTrack no las incluye ni las descarga.**
Si quieres mostrar demostraciones animadas, necesitas tu propia licencia de Gym visual.

Los activos generados a partir del dataset se crean con `tools/build_dataset_asset.py`.

### Imágenes y GIF para uso personal

El repositorio **no contiene** los archivos multimedia (la carpeta `app/src/main/assets/media/` está en `.gitignore`).
Para tenerlos en *tu* copia de la app, para uso personal y no comercial:

```
git clone --depth 1 https://github.com/hasaneyldrm/exercises-dataset /ruta/exercises-dataset
python3 tools/media.py copy /ruta/exercises-dataset              # imágenes + GIF (~137 MB)
python3 tools/media.py copy /ruta/exercises-dataset --no-gifs    # solo miniaturas (~9 MB)
```

La app muestra siempre «© Gym visual — https://gymvisual.com/» junto a cada demostración, a 180×180 como exige el aviso original.
Si publicas o distribuyes tu APK, o el uso no es personal, revisa antes los
[Términos de Gym visual](https://gymvisual.com/content/3-terms-and-conditions-of-use) y obtén tu propia licencia.
