import os
import time
import requests
import random
from concurrent.futures import ThreadPoolExecutor, as_completed

# Credenciales de GitHub Secrets
LOGIN = os.getenv('STREAMTAPE_LOGIN')
KEY = os.getenv('STREAMTAPE_KEY')

API_BASE = "https://api.streamtape.com"
MAX_THREADS = 5  # Número de archivos procesados en paralelo (ajustar si es necesario)

def obtener_todos_los_files():
    session = requests.Session()
    session.headers.update({'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'})
    
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

def simular_reproduccion(file_id, index, total):
    """
    Función trabajadora: realiza todo el proceso para un solo archivo.
    """
    local_session = requests.Session()
    local_session.headers.update({
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
    })
    
    try:
        # PASO A: Obtener Ticket
        ticket_resp = local_session.get(f"{API_BASE}/file/dlticket", params={'login': LOGIN, 'key': KEY, 'file': file_id}).json()
        
        if ticket_resp.get('status') == 200:
            ticket = ticket_resp['result']['ticket']
            wait_time = ticket_resp['result']['wait_time']
            
            # Tiempo de espera obligatorio
            time.sleep(wait_time + 1)
            
            # PASO B: Obtener Link Directo
            dl_resp = local_session.get(f"{API_BASE}/file/dl", params={'file': file_id, 'ticket': ticket}).json()
            
            if dl_resp.get('status') == 200:
                final_url = dl_resp['result']['url']
                
                # PASO C: Simular 31 Segundos de Reproducción
                start_time = time.time()
                with local_session.get(final_url, stream=True, timeout=30) as r:
                    if r.status_code in [200, 206]:
                        for _ in r.iter_content(chunk_size=1024 * 128):
                            if time.time() - start_time > 31:
                                break
                        return True, f"[{index}/{total}] OK: {file_id} (Vista contada)"
                    else:
                        return False, f"[{index}/{total}] ERROR STREAM {r.status_code}: {file_id}"
            else:
                return False, f"[{index}/{total}] ERROR DL LINK: {file_id}"
        else:
            return False, f"[{index}/{total}] ERROR TICKET: {file_id}"
            
    except Exception as e:
        return False, f"[{index}/{total}] ERROR CRÍTICO {file_id}: {str(e)}"

def renovar_enlaces():
    if not LOGIN or not KEY:
        print("ERROR: Configura los Secrets en GitHub.")
        return

    file_ids = obtener_todos_los_files()
    total = len(file_ids)
    
    if total == 0:
        print("No se encontraron archivos.")
        return

    print(f"\n--- 2. Iniciando Multi-Streaming ({MAX_THREADS} hilos) ---")
    
    exitosos = 0
    # Usamos ThreadPoolExecutor para procesar múltiples archivos a la vez
    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        # Mapeamos los trabajos
        futures = [executor.submit(simular_reproduccion, fid, i, total) for i, fid in enumerate(file_ids, start=1)]
        
        for future in as_completed(futures):
            success, message = future.result()
            print(message)
            if success:
                exitosos += 1
            
            # Pequeña pausa aleatoria entre el lanzamiento de hilos para no saturar la API
            time.sleep(random.uniform(0.5, 1.5))

    print(f"\n--- 3. Proceso finalizado ---")
    print(f"Resumen: {exitosos} de {total} archivos renovados correctamente.")

if __name__ == "__main__":
    renovar_enlaces()
