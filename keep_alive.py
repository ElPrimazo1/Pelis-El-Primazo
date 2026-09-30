import os
import time
import requests

# Configuración
URLS_FILE = "urls.txt"
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

def obtener_urls():
    if not os.path.exists(URLS_FILE):
        print(f"Error: No se encontró el archivo {URLS_FILE}")
        return []
    
    with open(URLS_FILE, "r") as f:
        # Lee cada línea, quita espacios y omite líneas vacías o comentarios (#)
        return [line.strip() for line in f if line.strip() and not line.startswith("#")]

def renovar_enlaces():
    urls = obtener_urls()
    if not urls:
        print("No hay URLs para procesar.")
        return

    print(f"Iniciando renovación de {len(urls)} enlaces...")
    exitosos = 0
    fallidos = 0

    for index, url in enumerate(urls, start=1):
        try:
            response = requests.get(url, headers=HEADERS, timeout=10)
            if response.status_code == 200:
                print(f"[{index}/{len(urls)}] SUCCESS: {url}")
                exitosos += 1
            else:
                print(f"[{index}/{len(urls)}] HTTP {response.status_code}: {url}")
                fallidos += 1
        except Exception as e:
            print(f"[{index}/{len(urls)}] ERROR: {url} -> {e}")
            fallidos += 1
        
        time.sleep(2)

    print(f"\nProceso finalizado. Éxitos: {exitosos} | Fallos: {fallidos}")

if __name__ == "__main__":
    renovar_enlaces()
