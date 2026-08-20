#!/usr/bin/env python3
"""
Imprime la huella SHA-1 (y SHA-256) del certificado firmante de:
  - un APK (firma v2/v3, sin Java ni Android SDK), o
  - un keystore JKS (los certificados van sin cifrar: NO pide contraseña), o
  - un keystore PKCS12/.p12/.pfx (si los certs van cifrados avisa y sugiere
    keytool, porque entonces sí hace falta la contraseña).

Python puro: sirve en Termux con solo `pkg install python`.

Uso:
    python3 scripts/apk-sha1.py ruta/al/archivo
    python3 scripts/apk-sha1.py vivid.apk
    python3 scripts/apk-sha1.py vivid.jks
    python3 scripts/apk-sha1.py vivid.p12

El SHA-1 impreso es el que debe estar en:
Firebase Console → Configuración del proyecto → Tus apps →
Huellas del certificado. Si el del APK/keystore no aparece ahí,
"Continuar con Google" falla con DEVELOPER_ERROR.

Por qué no keytool para el APK: los APK con minSdk >= 24 (el caso de
Vivid) se firman con esquema v2/v3 y NO llevan el certificado en
META-INF/*.RSA, así que `keytool -printcert -jarfile` falla con
"no certificate". Para un keystore JKS/P12 sí sirve keytool (pide la
contraseña): keytool -list -v -keystore vivid.jks
"""
import hashlib
import struct
import sys
import zipfile
from pathlib import Path

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
V3_BLOCK_ID = 0xF05368C0
JKS_MAGIC = 0xFEEDFEED

# OIDs en DER (para escanear PKCS12 sin parser ASN.1 completo).
OID_CERT_BAG = bytes.fromhex("060B2A864886F70D010C0A0103")      # 1.2.840.113549.1.12.10.1.3
OID_X509_CERT_TYPE = bytes.fromhex("060A2A864886F70D01091601")   # 1.2.840.113549.1.9.22.1


def u32(buf: bytes, off: int) -> int:
    return struct.unpack_from("<I", buf, off)[0]


def u32be(buf: bytes, off: int) -> int:
    return struct.unpack_from(">I", buf, off)[0]


def u64(buf: bytes, off: int) -> int:
    return struct.unpack_from("<Q", buf, off)[0]


def read_length_prefixed(buf: bytes, off: int):
    """Bloque con longitud uint32 al inicio: devuelve (contenido, nuevo_offset)."""
    length = u32(buf, off)
    return buf[off + 4 : off + 4 + length], off + 4 + length


def read_tlv(buf: bytes, off: int):
    """Un TLV DER: devuelve (tag, contenido, siguiente_offset)."""
    tag = buf[off]
    len_byte = buf[off + 1]
    if len_byte < 0x80:
        length, hdr = len_byte, 2
    else:
        n = len_byte & 0x7F
        length = int.from_bytes(buf[off + 2 : off + 2 + n], "big")
        hdr = 2 + n
    start = off + hdr
    return tag, buf[start : start + length], start + length


# ── APK (bloque de firma v2/v3) ────────────────────────────────────────────

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
    signed_data, _ = read_length_prefixed(signer, 0)
    _, off = read_length_prefixed(signed_data, 0)      # digests
    certificates_seq, _ = read_length_prefixed(signed_data, off)
    cert_der, _ = read_length_prefixed(certificates_seq, 0)
    return cert_der


def cert_from_apk(data: bytes):
    block = locate_signing_block(data)
    if block:
        pairs = parse_pairs(data, *block)
        for block_id, name in ((V3_BLOCK_ID, "v3"), (V2_BLOCK_ID, "v2")):
            if block_id in pairs:
                try:
                    return first_certificate(pairs[block_id]), f"APK firma {name}"
                except (struct.error, IndexError):
                    continue
    return None


# ── Keystore JKS (certificados en claro: no requiere contraseña) ───────────

def cert_from_jks(data: bytes):
    """Primer certificado X.509 de un JKS. Formato:
    magic u32, version u32, count u32 y por entrada: tag u32 (1 clave
    privada / 2 cert), alias (u16 len + bytes), fecha u64; para claves:
    clave cifrada (u32 len + bytes) y cadena de certificados."""
    if u32be(data, 0) != JKS_MAGIC:
        return None
    off = 12
    certs = []
    while off + 6 <= len(data) - 4:
        tag = u32be(data, off)
        off += 4
        alias_len = struct.unpack_from(">H", data, off)[0]
        off += 2 + alias_len + 8  # alias + timestamp
        if tag == 1:  # clave privada: saltar la clave cifrada
            key_len = u32be(data, off)
            off += 4 + key_len
            chain_len = u32be(data, off)
            off += 4
            for _ in range(chain_len):
                cert_type = u32be(data, off)
                cert_len = u32be(data, off + 4)
                off += 8
                if cert_type == 1 and cert_len > 0 and not certs:
                    certs.append(data[off : off + cert_len])
                off += cert_len
        elif tag == 2:  # certificado de confianza
            cert_type = u32be(data, off)
            cert_len = u32be(data, off + 4)
            off += 8
            if cert_type == 1 and cert_len > 0 and not certs:
                certs.append(data[off : off + cert_len])
            off += cert_len
        else:
            raise ValueError(f"Entrada JKS desconocida (tag {tag}).")
    if not certs:
        raise ValueError("JKS sin certificados.")
    return certs[0], "JKS (cert en claro, sin contraseña)"


# ── Keystore PKCS12 (mejor esfuerzo: certs suelen ir sin cifrar) ───────────

def cert_from_pkcs12(data: bytes):
    """Busca certBags con certificado X.509 dentro de los SafeContents en
    claro. Estructura: SafeBag { bagId OID, bagValue [0] CertBag {
    certId OID, certValue [0] { OCTET STRING cert } } }. Si el archivo
    cifra también los certificados (openssl 3.x y JDK 9+ por defecto), no
    se puede sin la contraseña: se avisa."""
    pos = 0
    certs = []
    while True:
        pos = data.find(OID_CERT_BAG, pos)
        if pos < 0:
            break
        try:
            off = pos + len(OID_CERT_BAG)
            tag, bag_value, _ = read_tlv(data, off)      # [0] bagValue
            if tag == 0xA0:
                btag, cert_bag, _ = read_tlv(bag_value, 0)   # SEQUENCE CertBag
                if btag == 0x30:
                    itag, oid, _ = read_tlv(cert_bag, 0)     # OID x509Certificate
                    if itag == 0x06 and oid == OID_X509_CERT_TYPE[2:]:
                        ctag, cert_value, _ = read_tlv(cert_bag, len(oid) + 2)  # [0]
                        if ctag == 0xA0:
                            otag, cert_der, _ = read_tlv(cert_value, 0)  # OCTET STRING
                            if otag == 0x04 and cert_der:
                                certs.append(cert_der)
        except (IndexError, struct.error):
            pass
        pos += 1
    if not certs:
        return "ENCRYPTED"
    return certs[0], "PKCS12 (certs en claro)"


# ── Utilidad común ─────────────────────────────────────────────────────────

def fingerprint(der: bytes, algo: str) -> str:
    digest = hashlib.new(algo, der).hexdigest().upper()
    return ":".join(digest[i : i + 2] for i in range(0, len(digest), 2))


def report(source: str, scheme: str, cert_der: bytes) -> int:
    print(f"Origen:     {source}")
    print(f"Formato:    {scheme}")
    print(f"SHA-1:      {fingerprint(cert_der, 'sha1')}")
    print(f"SHA-256:    {fingerprint(cert_der, 'sha256')}")
    print()
    print("Compara el SHA-1 con Firebase Console → Configuración del "
          "proyecto → Tus apps → Huellas del certificado.")
    print("Si no coincide, agrégalo ahí y vuelve a descargar "
          "google-services.json.")
    return 0


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        print("::error::Indica la ruta: python3 scripts/apk-sha1.py <apk|jks|p12>")
        return 1

    target = Path(sys.argv[1])
    if not target.is_file():
        print(f"::error::No existe {target}")
        return 1

    data = target.read_bytes()

    # 1) Keystore JKS (magia FEEDFEED big-endian).
    try:
        result = cert_from_jks(data)
        if result:
            return report(str(target), result[1], result[0])
    except ValueError as e:
        print(f"::error::JKS inválido: {e}")
        return 1

    # 2) Keystore PKCS12 (DER/ASN.1: empieza con SEQUENCE).
    if data[:1] == b"\x30":
        result = cert_from_pkcs12(data)
        if result == "ENCRYPTED":
            print("Este PKCS12 también cifra los certificados: hace falta "
                  "la contraseña.")
            print("Usa keytool (en Termux: pkg install openjdk-17):")
            print(f"  keytool -list -v -keystore {target}")
            return 2
        return report(str(target), result[1], result[0])

    # 3) APK con bloque de firma v2/v3.
    try:
        result = cert_from_apk(data)
    except (ValueError, struct.error) as e:
        print(f"::error::{e}")
        return 1
    if result:
        return report(str(target), result[1], result[0])

    # 4) Fallback v1 (JAR): el certificado va en META-INF/*.RSA|DSA|EC.
    try:
        with zipfile.ZipFile(target) as zf:
            names = [n for n in zf.namelist()
                     if n.startswith("META-INF/")
                     and n.upper().endswith((".RSA", ".DSA", ".EC"))]
    except zipfile.BadZipFile:
        print("::error::No es un APK, JKS ni PKCS12 reconocible.")
        return 1
    if names:
        print("Firma v1 (JAR) detectada: el PKCS#7 de META-INF no se "
              "interpreta sin openssl/keytool.")
        print("Usa:  unzip -p '%s' '%s' | openssl pkcs7 -inform DER "
              "-print_certs | openssl x509 -noout -fingerprint -sha1"
              % (target, names[0]))
        return 0
    print("::error::No se encontró bloque de firma v2/v3 ni certificado "
          "v1 en META-INF. ¿Es un APK válido y firmado?")
    return 1


if __name__ == "__main__":
    sys.exit(main())
