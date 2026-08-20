#!/usr/bin/env python3
"""
Valida que un google-services.json sirva para "Continuar con Google".

Uso:
    python3 scripts/validate-google-services.py [ruta/al/google-services.json]
    (por defecto: vivid-app/app/google-services.json)

Comprueba las tres condiciones que el login con Google necesita y que,
si faltan, producen el clásico "error de Google sign-in" en runtime:

  1. Contiene la app Android com.vivid.app.
  2. Tiene un oauth_client con client_type 3 (Web client): sin él el
     plugin google-services NO genera `default_web_client_id` y la app
     muestra "Google Sign-In no está configurado".
  3. Registra al menos una huella SHA-1 (certificate_hash): si el APK se
     firma con un keystore cuyo SHA-1 no está en Firebase Console (y por
     tanto no aparece en el JSON), Google devuelve DEVELOPER_ERROR.

Para usarlo en CI (build-apk.yml), después de restaurar el secret:

    - name: Validate google-services.json
      run: python3 scripts/validate-google-services.py vivid-app/app/google-services.json
"""
import json
import sys
from pathlib import Path

PACKAGE = "com.vivid.app"
DEFAULT = Path(__file__).resolve().parent.parent / "vivid-app" / "app" / "google-services.json"


def validate(path: Path) -> int:
    try:
        cfg = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        print(f"::error::No existe {path}")
        return 1
    except json.JSONDecodeError as e:
        print(f"::error::{path} no es un JSON válido ({e}).")
        return 1

    errors = []
    clients = cfg.get("client", [])
    android = [
        c for c in clients
        if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE
    ]

    if not android:
        errors.append(
            f"El JSON no contiene la app Android '{PACKAGE}'. Descárgalo de "
            "Firebase Console → Configuración del proyecto → Tus apps (la app "
            "Android, no la web)."
        )
    else:
        oauth = android[0].get("oauth_client", [])

        if not any(o.get("client_type") == 3 for o in oauth):
            errors.append(
                "El JSON no tiene oauth_client con client_type 3 (Web client). "
                "Sin él el plugin google-services NO genera 'default_web_client_id' "
                "y 'Continuar con Google' falla siempre. Habilita el proveedor "
                "Google en Firebase Console → Authentication → Sign-in method y "
                "vuelve a descargar el google-services.json."
            )

        hashes = [
            o.get("android_client_info", {}).get("certificate_hash")
            for o in oauth
            if o.get("client_type") == 1
            and o.get("android_client_info", {}).get("certificate_hash")
        ]
        if not hashes:
            errors.append(
                "El JSON no registra ninguna huella SHA-1. Google rechaza el "
                "sign-in (DEVELOPER_ERROR) de cualquier APK firmado con un "
                "keystore no registrado. Agrega el SHA-1 del keystore de release "
                "(y el del de debug) en Firebase Console → Configuración del "
                "proyecto → Tus apps → Huellas del certificado, y vuelve a "
                "descargar el google-services.json."
            )
        else:
            print("SHA-1 registrados en el JSON (debe incluir el del keystore del APK):")
            for h in hashes:
                print(f"  - {h}")

    if errors:
        for e in errors:
            print(f"::error::{e}")
        return 1

    print(f"OK: google-services.json apto para Google Sign-In ({PACKAGE}).")
    return 0


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT
    sys.exit(validate(target))
