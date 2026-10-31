import threading
import time
import hashlib
import binascii
import socket
import logging
import re
import os
import io
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import paramiko

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

MOCK_SSH_PORT = int(os.environ.get('MOCK_SSH_PORT', '2222'))
INTERNAL_HTTP_PORT = int(os.environ.get('MOCK_HTTP_PORT', '32223'))
MOCK_TELNET_PORT = int(os.environ.get('MOCK_TELNET_PORT', '2323'))
ALLOWED_FORWARD_HOSTS = {'127.0.0.1', 'localhost'}
MAX_CONCURRENT_FORWARDS_PER_SESSION = 8

STATIC_RSA_KEY = """-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABFwAAAAdzc2gtcn
NhAAAAAwEAAQAAAQEAn5V9sM3+PLw8BSPphFW+klqYv81qWTZjdttdWKgyY2x31xUpdP6N
anWGedAJMLfbrJ6EoldGsTr0XhKXtO7cn5kCpoX2v4Lc3gKtr09wnjGSGror22iI7ImoC+
o0AujSj6l9W+KnSvp9LTckwM68Y1aoM+jwo0jMiGVydKoXWhMVb4cbzTTRSffabVjxG09u
stUVZDtiH6CxnN3vgwYb+TADyKn3ntbl+hhNJPkVRTXtqaj8NqZhtM11cf8dNnTgA92RlM
c/C9IRIiyecobMV2YR45o+0Sp8wKJP2VCUt1NPcgD/8HA2J5Q4hdhcETr6EU6NSECFtYt/
5UBPSHbkyQAAA8gjeoyAI3qMgAAAAAdzc2gtcnNhAAABAQCflX2wzf48vDwFI+mEVb6SWp
i/zWpZNmN2211YqDJjbHfXFSl0/o1qdYZ50Akwt9usnoSiV0axOvReEpe07tyfmQKmhfa/
gtzeAq2vT3CeMZIauivbaIjsiagL6jQC6NKPqX1b4qdK+n0tNyTAzrxjVqgz6PCjSMyIZX
J0qhdaExVvhxvNNNFJ99ptWPEbT26y1RVkO2IfoLGc3e+DBhv5MAPIqfee1uX6GE0k+RVF
Ne2pqPw2pmG0zXVx/x02dOAD3ZGUxz8L0hEiLJ5yhsxXZhHjmj7RKnzAok/ZUJS3U09yAP
/wcDYnlDiF2FwROvoRTo1IQIW1i3/lQE9IduTJAAAAAwEAAQAAAQAjwm5TV52AQ5fZeQYj
OsmE5xsyY5fQnODl6dr9elcsM0qZXisXhdDviZ1UH3tnuNNXV4AWFqOHJFRM3jKzfxDeDH
3tDbjQikTu1L/RuGXedPIt3gjk1gRUqLYI0XbNLhm8yniGRgBBy2aQYrpwIANT6T+wviJq
HG6Eo+LlZJXt/10cb05UNipQ9w+pimEmhBzJv9uIAWeOqthS5svtY2RkSKTu5R36hL+HQJ
e3nRFlC0B0V2KG3Pn35ghjPzlqHjNLSKTw4IwXPWJCmw3EXQth5jJxofEiB8iKthiZNTbs
W84eYu1lGYomRR+Ugq0kXlY3EV/A7yNs4E8yI7x5D+I3AAAAgAj+a9umRDcmOl4ePPaac5
Se9Kyb5TojA4x5MD/hPZUI1ZxkbDFYe9qhFGPyZxBGKMe9bt6Exd3YM/Pb1z1bt40I9TMl
GCn1JH46YxPbQomHVWDFKANDeLAn0GGNqwg4PfRYmqWvywkM2XhNbh2uXCArLUswR/IIX3
dCaM9mKgbjAAAAgQDQ8GqTcXR/w1zjczfRbZWWuAcEA5jr/1kT7NcbYQZK36dCsiCV0PK2
dxL3BtVYlSruiYPA9hTSnqpx3MPcDaG1BEjdAJGsvdwTIJBGMTHpDY/Qx+LjrbuGJFJc+h
0eTw3bcGSGFqeTyWt2X+UK/9HVcdfv4GmBgDV/YboKFxrNYwAAAIEAw4c4Bvw9k6cFxzGZ
CUe1V0NWb03p4UESQ9aDyQ0rxpx5qbX4ZqXl5KQCVHMJuCu4ubw351IdL0yGbLPkhtasfw
oBopI2pyGG2n0K2XSBye8wNP7ySh3LrM2MCmrcrtV9D+hQ/WSxa0JkzCA190P0pHjXQM8w
OCE5TrKpiJkIAuMAAAATYWRhbW91dGxlckBITEFCLUEyNQ==
-----END OPENSSH PRIVATE KEY-----
"""

class MockServerInterface(paramiko.ServerInterface):
    def __init__(self):
        self.channel_info = {}
        self.channel_queue = []
        self.lock = threading.Lock()
        self.env_vars = {}
        self.forward_semaphore = threading.Semaphore(MAX_CONCURRENT_FORWARDS_PER_SESSION)

    def check_channel_request(self, kind, chanid):
        logging.info(f"check_channel_request kind={kind} chanid={chanid}")
        with self.lock:
            info = {'kind': kind}
            self.channel_info[chanid] = info
            self.channel_queue.append((chanid, info))
        if kind == 'session':
            return paramiko.OPEN_SUCCEEDED
        return paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

    def check_channel_direct_tcpip_request(self, chanid, origin, destination):
        logging.info(f"check_channel_direct_tcpip_request CALL! chanid={chanid} origin={origin} dest={destination}")
        dest_addr, dest_port = destination
        norm_addr = (dest_addr or '').strip().lower()
        if norm_addr in ALLOWED_FORWARD_HOSTS and dest_port == INTERNAL_HTTP_PORT:
            with self.lock:
                info = {
                    'kind': 'direct-tcpip',
                    'origin': origin,
                    'destination': destination
                }
                self.channel_info[chanid] = info
                self.channel_queue.append((chanid, info))
            return paramiko.OPEN_SUCCEEDED
        logging.warning(f"Prohibited direct-tcpip request to {dest_addr}:{dest_port}")
        return paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

    def get_channel_info(self, chanid):
        with self.lock:
            if chanid in self.channel_info:
                return self.channel_info[chanid]
            if self.channel_queue:
                return self.channel_queue.pop(0)[1]
            return {'kind': 'session'}

    def check_auth_password(self, username, password):
        if password == "wrongpassword":
            return paramiko.AUTH_FAILED
        return paramiko.AUTH_SUCCESSFUL

    def check_auth_publickey(self, username, key):
        logging.info(f"check_auth_publickey user={username}")
        if username == "testuser":
            return paramiko.AUTH_SUCCESSFUL
        return paramiko.AUTH_FAILED

    def get_allowed_auths(self, username):
        return 'password,publickey'

    def check_channel_shell_request(self, channel):
        return True

    def check_channel_pty_request(self, channel, term, width, height, pixelwidth, pixelheight, modes):
        return True

    def check_channel_env_request(self, channel, name, value):
        norm_name = (name or '').strip()
        upper_name = norm_name.upper()
        dangerous_keys = {
            'LD_PRELOAD', 'LD_LIBRARY_PATH', 'BASH_ENV', 'ENV', 'IFS',
            'SHELL', 'PATH', 'PYTHONPATH', 'PYTHONHOME'
        }
        if upper_name in dangerous_keys or upper_name.startswith('LD_'):
            logging.warning(f"Rejected dangerous env var request: {norm_name}")
            return False
        with self.lock:
            self.env_vars[norm_name] = value
        logging.info(f"Accepted env var: {norm_name}")
        return True

    def check_port_forward_request(self, address, port):
        logging.warning(f"Rejected reverse port forwarding bind to {address}:{port}")
        return False

    def check_channel_exec_request(self, channel, command):
        """ Allow independent command execution out of shell mode """
        def handle_exec(command_bytes):
            command_str = command_bytes.decode('utf-8')
            channel.send(command_str + "\n")
            channel.send_exit_status(0)
            channel.close()
            
        t = threading.Thread(target=handle_exec, args=(command,))
        t.start()
        return True

def sanitize_input(raw_bytes: bytes) -> str:
    """ Strips ANSI sequences, null bytes, backspaces, etc. Returns normalized utf-8 """
    text = raw_bytes.decode('utf-8', 'replace')
    ansi_escape = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')
    text = ansi_escape.sub('', text)
    text = re.sub(r'[\x00-\x1F\x7F]', '', text)
    return text

def handle_shell_channel(channel):
    try:
        req_num = 1
        buffer_size = 4096
        
        channel.send(f"\r\nWelcome to MockSSH server. Interactive test shell.\r\n")
        channel.send(f"{req_num}$ > ")
        
        recv_buffer = bytearray()
        last_request_time = 0.0
        
        while True:
            data = channel.recv(1024)
            if not data:
                break
            
            channel.send(data)
            recv_buffer.extend(data)
            
            if len(recv_buffer) > buffer_size:
                logging.warning("Buffer size exceeded, dropping connection to prevent OOM.")
                channel.send("\r\nERROR: Buffer size exceeded.\r\n")
                break
            
            if b'\n' in recv_buffer or b'\r' in recv_buffer:
                current_time = time.time()
                elapsed = current_time - last_request_time
                if elapsed < 1.0:
                    time.sleep(1.0 - elapsed)
                
                last_request_time = time.time()
                
                full_buf = recv_buffer.replace(b'\r', b'').replace(b'\n', b'')
                sanitized_str = sanitize_input(full_buf)
                
                if sanitized_str.lower().strip() == "exit":
                    channel.send("\r\nGoodbye.\r\n")
                    break
                
                if sanitized_str:
                    sha256 = hashlib.sha256(sanitized_str.encode('utf-8')).hexdigest()
                    str_len = len(sanitized_str)
                    response = f"\r\nRESPONSE {req_num}, '{sanitized_str}', {str_len}, {sha256}\r\n"
                else:
                    response = f"\r\n"
                
                channel.send(response)
                req_num += 1
                channel.send(f"{req_num}$ > ")
                recv_buffer.clear()
    except Exception as e:
        logging.error(f"Channel loop error: {e}")
    finally:
        channel.close()

def handle_direct_tcpip(channel, dest_addr, dest_port, semaphore):
    acquired = semaphore.acquire(blocking=False)
    if not acquired:
        logging.warning("Forward concurrency limit exceeded per session.")
        try:
            channel.close()
        except Exception:
            pass
        return

    dest_sock = None
    try:
        channel.settimeout(30.0)
        dest_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        dest_sock.settimeout(30.0)
        connect_host = '127.0.0.1' if dest_addr.lower() == 'localhost' else dest_addr
        dest_sock.connect((connect_host, dest_port))

        def pipe(src, dst):
            try:
                while True:
                    data = src.recv(4096)
                    if not data:
                        break
                    if hasattr(dst, 'sendall'):
                        dst.sendall(data)
                    else:
                        dst.send(data)
            except Exception:
                pass
            finally:
                try:
                    src.close()
                except Exception:
                    pass
                try:
                    dst.close()
                except Exception:
                    pass

        t1 = threading.Thread(target=pipe, args=(channel, dest_sock), daemon=True)
        t2 = threading.Thread(target=pipe, args=(dest_sock, channel), daemon=True)
        t1.start()
        t2.start()
        t1.join()
        t2.join()
    except Exception as e:
        logging.error(f"direct-tcpip forward failed: {e}")
    finally:
        if dest_sock:
            try:
                dest_sock.close()
            except Exception:
                pass
        try:
            channel.close()
        except Exception:
            pass
        semaphore.release()

def handle_connection(client, addr):
    try:
        t = paramiko.Transport(client)
        t.set_gss_host(socket.getfqdn(""))
        
        rsa_key = paramiko.RSAKey.from_private_key(io.StringIO(STATIC_RSA_KEY))
        t.add_server_key(rsa_key)
        
        server = MockServerInterface()
        try:
            t.start_server(server=server)
        except paramiko.SSHException:
            logging.error("SSH negotiation failed.")
            return

        while True:
            channel = t.accept(20)
            if channel is None:
                if not t.is_active():
                    break
                continue
                
            chan_id = channel.get_id()
            chan_info = server.get_channel_info(chan_id)
            kind = chan_info.get('kind', 'session')
            logging.info(f"Authenticated channel: {channel.get_name()} id: {chan_id} remote_chanid: {getattr(channel, 'remote_chanid', None)} kind: {kind}")
            
            if kind == 'direct-tcpip':
                dest = chan_info.get('destination', ('127.0.0.1', INTERNAL_HTTP_PORT))
                dest_addr, dest_port = dest
                threading.Thread(
                    target=handle_direct_tcpip,
                    args=(channel, dest_addr, dest_port, server.forward_semaphore),
                    daemon=True
                ).start()
                continue

            threading.Thread(target=handle_shell_channel, args=(channel,), daemon=True).start()

    except Exception as e:
        logging.error(f"Connection exception: {e}")
    finally:
        try:
            client.close()
        except Exception:
            pass

def handle_telnet_connection(client, addr):
    try:
        client.sendall(b"\r\nWelcome to MockTelnet server. Interactive test shell.\r\n")
        req_num = 1
        client.sendall(f"{req_num}$ > ".encode('utf-8'))
        recv_buffer = bytearray()
        
        while True:
            data = client.recv(1024)
            if not data:
                break
            
            client.sendall(data)
            recv_buffer.extend(data)
            
            if b'\n' in recv_buffer or b'\r' in recv_buffer:
                full_buf = recv_buffer.replace(b'\r', b'').replace(b'\n', b'')
                sanitized_str = sanitize_input(full_buf)
                
                if sanitized_str.lower().strip() == "exit":
                    client.sendall(b"\r\nGoodbye.\r\n")
                    break
                
                if sanitized_str:
                    sha256 = hashlib.sha256(sanitized_str.encode('utf-8')).hexdigest()
                    str_len = len(sanitized_str)
                    response = f"\r\nRESPONSE {req_num}, '{sanitized_str}', {str_len}, {sha256}\r\n"
                else:
                    response = f"\r\n"
                
                client.sendall(response.encode('utf-8'))
                req_num += 1
                client.sendall(f"{req_num}$ > ".encode('utf-8'))
                recv_buffer.clear()
                
    except Exception as e:
        logging.error(f"Telnet connection exception: {e}")
    finally:
        try:
            client.close()
        except Exception:
            pass

def start_telnet_server(port=None):
    port = port or MOCK_TELNET_PORT
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('0.0.0.0', port))
        sock.listen(100)
        logging.info(f"Listening for Telnet connections on port {port}...")
        while True:
            client, addr = sock.accept()
            logging.info(f"Accepted Telnet connection from {addr}")
            t = threading.Thread(target=handle_telnet_connection, args=(client, addr), daemon=True)
            t.start()
    except Exception as e:
        logging.error(f"Telnet server failed: {e}")

class StatusHTTPRequestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path in ('/status', '/'):
            body = b"status: ok"
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif parsed.path == '/echo':
            qs = urllib.parse.parse_qs(parsed.query)
            q_val = qs.get('q', [''])[0].encode('utf-8')
            sha256_hex = hashlib.sha256(q_val).hexdigest()
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain')
            self.send_header('Content-Length', str(len(q_val)))
            self.send_header('X-Payload-Sha256', sha256_hex)
            self.end_headers()
            self.wfile.write(q_val)
        else:
            self.send_response(404)
            self.send_header('Content-Length', '0')
            self.end_headers()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == '/echo':
            try:
                content_len = int(self.headers.get('Content-Length', '0'))
            except (ValueError, TypeError):
                content_len = 0
            content_len = max(0, min(content_len, 65536))
            body = self.rfile.read(content_len)
            sha256_hex = hashlib.sha256(body).hexdigest()
            self.send_response(200)
            self.send_header('Content-Type', 'application/octet-stream')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('X-Payload-Sha256', sha256_hex)
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.send_header('Content-Length', '0')
            self.end_headers()

    def log_message(self, format, *args):
        pass  # Suppress HTTP logs to keep console clean

def start_http_server(port=None):
    port = port or INTERNAL_HTTP_PORT
    httpd = ThreadingHTTPServer(('0.0.0.0', port), StatusHTTPRequestHandler)
    logging.info(f"Started HTTP status server on port {port}")
    httpd.serve_forever()

if __name__ == '__main__':
    t_telnet = threading.Thread(target=start_telnet_server, daemon=True)
    t_telnet.start()

    t_http = threading.Thread(target=start_http_server, daemon=True)
    t_http.start()
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('0.0.0.0', MOCK_SSH_PORT))
    except Exception as e:
        logging.error(f"Bind failed on port {MOCK_SSH_PORT}: {e}")
        exit(1)
        
    sock.listen(100)
    logging.info(f"Listening for SSH connections on port {MOCK_SSH_PORT}...")
    
    while True:
        try:
            client, addr = sock.accept()
            logging.info(f"Accepted connection from {addr}")
            t = threading.Thread(target=handle_connection, args=(client, addr), daemon=True)
            t.start()
        except Exception as e:
            logging.error(f"Accept failed: {e}")
