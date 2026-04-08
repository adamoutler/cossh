import socket
import paramiko
import threading

class Server(paramiko.ServerInterface):
    def check_auth_password(self, username, password):
        if username == 'user' and password == 'password':
            return paramiko.AUTH_SUCCESSFUL
        return paramiko.AUTH_FAILED
    def check_channel_request(self, kind, chanid):
        if kind == 'session':
            return paramiko.OPEN_SUCCEEDED
        return paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED
    def check_channel_pty_request(self, channel, term, width, height, pixelwidth, pixelheight, modes):
        return True
    def check_channel_shell_request(self, channel):
        return True

def handle_client(client_socket):
    transport = paramiko.Transport(client_socket)
    transport.add_server_key(paramiko.RSAKey.generate(2048))
    server = Server()
    try:
        transport.start_server(server=server)
    except paramiko.SSHException:
        return
    channel = transport.accept(20)
    if channel is None:
        return
    channel.send("Hello from mock sshd!\r\n")
    while True:
        try:
            data = channel.recv(1024)
            if not data:
                break
            channel.send(data)
        except Exception:
            break
    channel.close()

server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server_socket.bind(('0.0.0.0', 2222))
server_socket.listen(100)
print("Mock SSHD listening on 2222")

def serve():
    while True:
        client, addr = server_socket.accept()
        threading.Thread(target=handle_client, args=(client,)).start()

t = threading.Thread(target=serve)
t.daemon = True
t.start()
