import socket
import paramiko
import threading
import re

injected_keys = set()

class Server(paramiko.ServerInterface):
    def get_allowed_auths(self, username):
        return 'password,publickey'

    def check_auth_password(self, username, password):
        if password == "wrongpassword":
            return paramiko.AUTH_FAILED
        return paramiko.AUTH_SUCCESSFUL

    def check_auth_publickey(self, username, key):
        # Convert the received key to OpenSSH format
        key_b64 = key.get_base64()
        key_type = key.get_name()
        ssh_rsa_str = f"{key_type} {key_b64}"
        print(f"Auth publickey requested: {ssh_rsa_str}")
        print(f"Currently injected keys: {injected_keys}")
        
        # Check if it was injected
        for injected in injected_keys:
            if ssh_rsa_str in injected:
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

    def check_channel_exec_request(self, channel, command):
        cmd = command.decode('utf-8') if isinstance(command, bytes) else command
        # Extract the key from echo "key" >> authorized_keys
        match = re.search(r'echo\s+"([^"]+)"\s*>>', cmd)
        if match:
            injected_keys.add(match.group(1))
        
        def finish_channel():
            import time
            time.sleep(0.1)
            try:
                channel.send_exit_status(0)
                channel.send("done\n")
                channel.close()
            except Exception as e:
                print("Error in finish_channel: ", e)

        threading.Thread(target=finish_channel).start()
        return True

host_key = paramiko.RSAKey.generate(1024)

def handle_client(client_socket):
    try:
        transport = paramiko.Transport(client_socket)
        transport.add_server_key(host_key)
        server = Server()
        try:
            transport.start_server(server=server)
        except paramiko.SSHException as e:
            print("SSHException during start_server: ", str(e))
            return
        except Exception as e:
            print("Exception during start_server: ", str(e))
            return
        
        channel = transport.accept(20)
        if channel is None:
            return
            
        channel.send("Hello from mock sshd!\r\n")
        while True:
            try:
                if channel.recv_ready():
                    data = channel.recv(1024)
                    if not data:
                        break
                    print(f"Received bytes from SSH client: {[hex(b) for b in data]}", flush=True)
                    # PTYs typically echo \r\n for \n
                    echo_data = data.replace(b'\n', b'\r\n') if b'\n' in data else data
                    channel.send(echo_data)
                elif channel.exit_status_ready():
                    break
                else:
                    import time
                    time.sleep(0.05)
            except EOFError:
                    print("Exit command received. Closing channel.")
                    try:
                        channel.send_exit_status(0)
                    except:
                        pass
                    break
            except Exception:
                break
    finally:
        try:
            channel.close()
        except:
            pass
        try:
            transport.close()
        except:
            pass
        try:
            client_socket.close()
        except:
            pass

import sys
port = int(sys.argv[1]) if len(sys.argv) > 1 else 2222

server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server_socket.bind(('127.0.0.1', port))
server_socket.listen(100)
print(f"Mock SSHD listening on {port}")

def serve():
    while True:
        try:
            client, addr = server_socket.accept()
            threading.Thread(target=handle_client, args=(client,)).start()
        except Exception:
            break

t = threading.Thread(target=serve)
t.daemon = True
t.start()

import time
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    pass
except Exception:
    pass
finally:
    server_socket.close()
