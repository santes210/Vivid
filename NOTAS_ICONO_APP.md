# Icono de Vivid corregido

Se corrigió el icono de la app para que Android no muestre el icono genérico del robot al instalar.

## Qué se hizo

- Se tomó la imagen existente:

```text
images/vivid_icon.png
```

- Se generaron iconos reales en tamaños Android:

```text
vivid-app/app/src/main/res/mipmap-mdpi/ic_launcher.png
vivid-app/app/src/main/res/mipmap-hdpi/ic_launcher.png
vivid-app/app/src/main/res/mipmap-xhdpi/ic_launcher.png
vivid-app/app/src/main/res/mipmap-xxhdpi/ic_launcher.png
vivid-app/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
```

- También se generaron versiones round:

```text
ic_launcher_round.png
```

- Se eliminaron los XML adaptativos de `mipmap-anydpi-v26` porque estaban apuntando a `@mipmap/ic_launcher` y podían causar que Android resolviera mal el icono o mostrara el icono genérico.

El manifest ya apunta correctamente a:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```
