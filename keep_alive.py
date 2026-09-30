import os
import time
import requests
import random

# Credenciales de GitHub Secrets
LOGIN = os.getenv('STREAMTAPE_LOGIN')
KEY = os.getenv('STREAMTAPE_KEY')

API_BASE = "https://api.streamtape.com"

# Sesión con Headers humanos
session = requests.Session()
session.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
})

def obtener_todos_los_files():
    files_to_ping = []
    folders_to_scan = [None]
    
    print("--- 1. Escaneando catálogo completo ---")
    while folders_to_scan:
        folder_id = folders_to_scan.pop(0)
        params = {'login': LOGIN, 'key': KEY}
        if folder_id: params['folder'] = folder_id
            
        try:
            response = session.get(f"{API_BASE}/file/listfolder", params=params, timeout=20).json()
            if response.get('status') == 200:
                result = response.get('result', {})
                for file in result.get('files', []):
                    files_to_ping.append(file.get('linkid'))
                for folder in result.get('folders', []):
                    folders_to_scan.append(folder.get('id'))
            time.sleep(1)
        except Exception as e:
            print(f"Error escaneando carpeta: {e}")
            
    return list(set(files_to_ping))

def renovar_enlaces():
    if not LOGIN or not KEY:
        print("ERROR: Configura STREAMTAPE_LOGIN y STREAMTAPE_KEY en Secrets.")
        return

    file_ids = obtener_todos_los_files()
    total = len(file_ids)
    print(f"Archivos a procesar: {total}")

    exitosos = 0
    print("\n--- 2. Iniciando Simulación de Visualización Real (30s de streaming) ---")
    
    for index, file_id in enumerate(file_ids, start=1):
        try:
            # PASO A: Obtener Ticket
            ticket_resp = session.get(f"{API_BASE}/file/dlticket", params={'login': LOGIN, 'key': KEY, 'file': file_id}).json()
            
            if ticket_resp.get('status') == 200:
                ticket = ticket_resp['result']['ticket']
                wait_time = ticket_resp['result']['wait_time']
                
                # Tiempo de espera obligatorio de la API
                time.sleep(wait_time + 1)
                
                # PASO B: Obtener Link Directo
                dl_resp = session.get(f"{API_BASE}/file/dl", params={'file': file_id, 'ticket': ticket}).json()
                
                if dl_resp.get('status') == 200:
                    final_url = dl_resp['result']['url']
                    
                    # PASO C: Simular 30 Segundos de Reproducción
                    # Abrimos el stream y leemos datos durante 31 segundos
                    print(f"[{index}/{total}] Reproduciendo {file_id}...", end="", flush=True)
                    
                    start_time = time.time()
                    with session.get(final_url, stream=True, timeout=30) as r:
                        if r.status_code in [200, 206]:
                            # Leemos el stream en trozos pequeños
                            for _ in r.iter_content(chunk_size=1024 * 64): # 64KB chunks
                                if time.time() - start_time > 31: # Superamos los 30 seg
                                    break
                            print(" [VISTA CONTADA]")
                            exitosos += 1
                        else:
                            print(f" [ERROR HTTP {r.status_code}]")
                else:
                    print(f"[{index}/{total}] ERROR DL LINK: {file_id}")
            else:
                print(f"[{index}/{total}] ERROR TICKET: {file_id}")

        except Exception as e:
            print(f"\n[{index}/{total}] ERROR CRÍTICO en {file_id}: {e}")
        
        # Pausa aleatoria para no parecer un bot masivo
        time.sleep(random.uniform(2, 4))

    print(f"\n--- 3. Proceso finalizado ---")
    print(f"Resumen: {exitosos} archivos mantenidos con actividad real.")

if __name__ == "__main__":
    renovar_enlaces()
