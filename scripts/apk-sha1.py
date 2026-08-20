#!/usr/bin/env python3
"""
Imprime la huella SHA-1 (y SHA-256) del certificado con el que está
firmado un APK, leyendo directamente el APK Signing Block (esquemas
v2 y v3). Python puro: sirve en CI y en Termux sin Java ni Android SDK.

Uso:
    python3 scripts/apk-sha1.py ruta/al/vivid.apk

Por qué no keytool: los APK con minSdk >= 24 (el caso de Vivid) se
firman con los esquemas v2/v3 y NO llevan el certificado en
META-INF/*.RSA, así que `keytool -printcert -jarfile` falla con
"no certificate". apksigner (build-tools) sí lo lee, pero no siempre
está disponible.

El SHA-1 impreso es el que debe estar registrado en:
Firebase Console → Configuración del proyecto → Tus apps →
Huellas del certificado (SHA-1). Si el del APK no aparece ahí,
"Continuar con Google" falla con DEVELOPER_ERROR.
"""
import hashlib
import struct
import sys
import zipfile
from pathlib import Path

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
V3_BLOCK_ID = 0xF05368C0


def u32(buf: bytes, off: int) -> int:
    return struct.unpack_from("<I", buf, off)[0]


def u64(buf: bytes, off: int) -> int:
    return struct.unpack_from("<Q", buf, off)[0]


def read_length_prefixed(buf: bytes, off: int):
    """Bloque con longitud uint32 al inicio: devuelve (contenido, nuevo_offset)."""
    length = u32(buf, off)
    return buf[off + 4 : off + 4 + length], off + 4 + length


def find_eocd(data: bytes) -> int:
    """Offset del End Of Central Directory (0x06054b50)."""
    scan_from = max(0, len(data) - (65536 + 22))
    pos = data.rfind(b"PK\x05\x06", scan_from)
    if pos < 0:
        raise ValueError("No es un ZIP/APK válido (falta EOCD).")
    return pos


def locate_signing_block(data: bytes):
    """Devuelve (inicio, fin) del APK Signing Block según el offset del CD."""
    eocd = find_eocd(data)
    cd_offset = u32(data, eocd + 16)
    block_end = cd_offset
    if block_end < 32 or data[block_end - 16 : block_end] != APK_SIG_BLOCK_MAGIC:
        return None
    size_bottom = u64(data, block_end - 24)
    block_start = block_end - size_bottom - 8
    if block_start < 0 or u64(data, block_start) != size_bottom:
        raise ValueError("APK Signing Block corrupto.")
    return block_start, block_end


def parse_pairs(data: bytes, block_start: int, block_end: int) -> dict:
    """Pares ID→valor del bloque (uint64 len + uint32 id + valor)."""
    pairs = {}
    off = block_start + 8
    limit = block_end - 24
    while off + 12 <= limit:
        pair_len = u64(data, off)
        block_id = u32(data, off + 8)
        pairs[block_id] = data[off + 12 : off + 8 + pair_len]
        off += 8 + pair_len
    return pairs


def first_certificate(block_value: bytes) -> bytes:
    """Extrae el primer certificado X.509 (DER) de un bloque v2/v3."""
    signers_seq, _ = read_length_prefixed(block_value, 0)
    signer, _ = read_length_prefixed(signers_seq, 0)
    # signer: 0) signed data  1) signatures  2) public key
    signed_data, _ = read_length_prefixed(signer, 0)
    # signed data: 0) digests  1) certificates  (v3: luego min/max SDK)
    _, off = read_length_prefixed(signed_data, 0)
    certificates_seq, _ = read_length_prefixed(signed_data, off)
    cert_der, _ = read_length_prefixed(certificates_seq, 0)
    return cert_der


def fingerprint(der: bytes, algo: str) -> str:
    digest = hashlib.new(algo, der).hexdigest().upper()
    return ":".join(digest[i : i + 2] for i in range(0, len(digest), 2))


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        print("::error::Indica la ruta del APK: python3 scripts/apk-sha1.py app.apk")
        return 1

    apk_path = Path(sys.argv[1])
    if not apk_path.is_file():
        print(f"::error::No existe {apk_path}")
        return 1

    data = apk_path.read_bytes()
    block = locate_signing_block(data)

    cert_der = None
    scheme = None
    if block:
        pairs = parse_pairs(data, *block)
        for block_id, name in ((V3_BLOCK_ID, "v3"), (V2_BLOCK_ID, "v2")):
            if block_id in pairs:
                try:
                    cert_der = first_certificate(pairs[block_id])
                    scheme = name
                    break
                except (struct.error, IndexError):
                    continue

    if cert_der is None:
        # Fallback v1 (JAR): el certificado va en META-INF/*.RSA|DSA|EC.
        with zipfile.ZipFile(apk_path) as zf:
            names = [n for n in zf.namelist()
                     if n.startswith("META-INF/")
                     and n.upper().endswith((".RSA", ".DSA", ".EC"))]
        if names:
            print("Firma v1 (JAR) detectada: el PKCS#7 de META-INF no se "
                  "interpreta sin openssl/keytool.")
            print("Usa:  unzip -p '%s' '%s' | openssl pkcs7 -inform DER "
                  "-print_certs | openssl x509 -noout -fingerprint -sha1"
                  % (apk_path, names[0]))
            return 0
        print("::error::No se encontró bloque de firma v2/v3 ni certificado "
              "v1 en META-INF. ¿Es un APK válido y firmado?")
        return 1

    print(f"APK:        {apk_path}")
    print(f"Esquema:    {scheme}")
    print(f"SHA-1:      {fingerprint(cert_der, 'sha1')}")
    print(f"SHA-256:    {fingerprint(cert_der, 'sha256')}")
    print()
    print("Compara el SHA-1 con Firebase Console → Configuración del "
          "proyecto → Tus apps → Huellas del certificado.")
    print("Si no coincide, agrégalo ahí y vuelve a descargar "
          "google-services.json.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
