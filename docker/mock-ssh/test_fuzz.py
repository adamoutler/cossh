import paramiko
import os
import time
import hashlib

def get_ssh_client(host=None, port=None, username='testuser', password='testpassword', pkey=None):
    if host is None:
        host = os.environ.get('MOCK_SSH_HOST', '127.0.0.1')
    if port is None:
        port = int(os.environ.get('MOCK_SSH_PORT', '32222'))
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    if pkey is not None:
        client.connect(hostname=host, port=port, username=username, pkey=pkey, timeout=10)
    else:
        client.connect(hostname=host, port=port, username=username, password=password, timeout=10)
    return client

def test_connection_and_prompt():
    print("Testing connection and prompt...")
    client = get_ssh_client()
    chan = client.invoke_shell()
    
    output = ""
    start_time = time.time()
    while not "1$ > " in output:
        if chan.recv_ready():
            output += chan.recv(1024).decode('utf-8')
        if time.time() - start_time > 5:
            break
            
    assert "1$ > " in output, f"Prompt not received. Output: {output}"
    chan.close()
    client.close()
    print("test_connection_and_prompt passed.")

def test_long_random_fuzzing():
    print("Testing long random fuzzing...")
    client = get_ssh_client()
    chan = client.invoke_shell()
    
    time.sleep(0.5)
    large_payload = b'A' * 5000
    chan.send(large_payload + b'\n')
    time.sleep(2)
    
    try:
        chan.send("foo\n")
    except Exception:
        pass
    
    time.sleep(1)
    
    output = ""
    try:
        while chan.recv_ready():
            data = chan.recv(8192).decode('utf-8', 'replace')
            output += data
    except Exception:
        pass
        
    if "Buffer size exceeded." in output:
        print("Buffer size correctly exceeded and handled.")
    else:
        foo_hash = hashlib.sha256(b"foo").hexdigest()
        assert foo_hash in output, f"Hash not found in output: {output}"
        
    client.close()
    print("test_long_random_fuzzing passed.")

def test_port_forwarding_allowed_http_only():
    print("Testing port forwarding allowed HTTP only...")
    client = get_ssh_client()
    transport = client.get_transport()
    http_port = int(os.environ.get('MOCK_INTERNAL_HTTP_PORT', os.environ.get('MOCK_HTTP_PORT', '32223')))

    for target_host in ['127.0.0.1', 'localhost']:
        chan = transport.open_channel('direct-tcpip', (target_host, http_port), ('127.0.0.1', 12345))
        assert chan is not None, f"Failed to open direct-tcpip to {target_host}:{http_port}"
        req = f"GET /status HTTP/1.1\r\nHost: {target_host}\r\nConnection: close\r\n\r\n".encode('utf-8')
        chan.sendall(req)
        resp = b""
        while True:
            chunk = chan.recv(1024)
            if not chunk:
                break
            resp += chunk
        chan.close()
        assert b"200 OK" in resp, f"Expected 200 OK, got: {resp}"
        assert b"status: ok" in resp, f"Expected status: ok, got: {resp}"

    # Also test GET /echo with query
    chan_echo = transport.open_channel('direct-tcpip', ('127.0.0.1', http_port), ('127.0.0.1', 12346))
    chan_echo.sendall(b"GET /echo?q=cossh_test HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
    resp_echo = b""
    while True:
        chunk = chan_echo.recv(1024)
        if not chunk:
            break
        resp_echo += chunk
    chan_echo.close()
    assert b"200 OK" in resp_echo, f"Expected 200 OK for echo, got: {resp_echo}"
    assert b"cossh_test" in resp_echo
    assert b"X-Payload-Sha256" in resp_echo

    # Test POST /echo with body
    chan_post = transport.open_channel('direct-tcpip', ('127.0.0.1', http_port), ('127.0.0.1', 12347))
    body = b"hello_world_cossh_post"
    req_post = (
        f"POST /echo HTTP/1.1\r\nHost: localhost\r\nContent-Length: {len(body)}\r\nConnection: close\r\n\r\n"
    ).encode('utf-8') + body
    chan_post.sendall(req_post)
    resp_post = b""
    while True:
        chunk = chan_post.recv(1024)
        if not chunk:
            break
        resp_post += chunk
    chan_post.close()
    assert b"200 OK" in resp_post, f"Expected 200 OK for POST echo, got: {resp_post}"
    expected_hash = hashlib.sha256(body).hexdigest().encode('utf-8')
    assert expected_hash in resp_post, f"Expected hash {expected_hash} in response"

    client.close()
    print("test_port_forwarding_allowed_http_only passed.")

def test_port_forwarding_concurrent_shell_and_http():
    print("Testing concurrent shell and http port forwarding...")
    client = get_ssh_client()
    transport = client.get_transport()
    http_port = int(os.environ.get('MOCK_INTERNAL_HTTP_PORT', os.environ.get('MOCK_HTTP_PORT', '32223')))

    shell_chan = client.invoke_shell()
    http_chan = transport.open_channel('direct-tcpip', ('127.0.0.1', http_port), ('127.0.0.1', 12348))
    assert http_chan is not None, "Failed to open direct-tcpip channel alongside shell"

    shell_chan.send(b"concurrent_test\n")
    http_chan.sendall(b"GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")

    http_resp = b""
    while True:
        chunk = http_chan.recv(1024)
        if not chunk:
            break
        http_resp += chunk
    http_chan.close()

    assert b"200 OK" in http_resp, f"Expected 200 OK on HTTP channel, got: {http_resp}"
    assert b"status: ok" in http_resp, f"Expected status: ok on HTTP channel, got: {http_resp}"

    shell_resp = ""
    start = time.time()
    while "RESPONSE 1" not in shell_resp and time.time() - start < 5:
        if shell_chan.recv_ready():
            shell_resp += shell_chan.recv(1024).decode('utf-8', 'replace')
        time.sleep(0.05)

    shell_chan.close()
    client.close()

    assert "RESPONSE 1" in shell_resp, f"Expected RESPONSE 1 on shell channel, got: {shell_resp}"
    assert "concurrent_test" in shell_resp
    print("test_port_forwarding_concurrent_shell_and_http passed.")

def test_port_forwarding_adversarial_rejections():
    print("Testing adversarial port forwarding rejections...")
    client = get_ssh_client()
    transport = client.get_transport()
    http_port = int(os.environ.get('MOCK_INTERNAL_HTTP_PORT', os.environ.get('MOCK_HTTP_PORT', '32223')))
    ssh_port = int(os.environ.get('MOCK_SSH_PORT', '32222'))
    telnet_port = int(os.environ.get('MOCK_TELNET_PORT', '32224'))

    forbidden_destinations = [
        ('0.0.0.0', http_port),
        ('127.0.0.1', ssh_port),
        ('127.0.0.1', telnet_port),
        ('169.254.169.254', 80),
        ('192.168.1.1', 80),
        ('10.0.0.1', 443),
    ]

    for dest_host, dest_port in forbidden_destinations:
        rejected = False
        try:
            chan = transport.open_channel('direct-tcpip', (dest_host, dest_port), ('127.0.0.1', 12349))
            if chan is None:
                rejected = True
            else:
                chan.close()
        except paramiko.ChannelException:
            rejected = True
        except Exception:
            rejected = True
        assert rejected, f"Target {dest_host}:{dest_port} was unexpectedly allowed!"

    reverse_rejected = False
    try:
        port = transport.request_port_forward('127.0.0.1', http_port)
        if not port:
            reverse_rejected = True
    except paramiko.SSHException:
        reverse_rejected = True
    except Exception:
        reverse_rejected = True

    assert reverse_rejected, "Reverse port forward was unexpectedly accepted!"
    client.close()
    print("test_port_forwarding_adversarial_rejections passed.")

def test_publickey_authentication():
    print("Testing public key authentication...")
    host = os.environ.get('MOCK_SSH_HOST', '127.0.0.1')
    port = int(os.environ.get('MOCK_SSH_PORT', '32222'))
    rsa_key = paramiko.RSAKey.generate(2048)

    # Valid testuser succeeds
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(hostname=host, port=port, username='testuser', pkey=rsa_key, timeout=10)
    chan = client.invoke_shell()
    assert chan is not None
    chan.close()
    client.close()

    # Invalid user fails
    client_bad = paramiko.SSHClient()
    client_bad.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    failed = False
    try:
        client_bad.connect(hostname=host, port=port, username='invaliduser', pkey=rsa_key, timeout=5)
    except paramiko.AuthenticationException:
        failed = True
    except Exception:
        failed = True
    finally:
        client_bad.close()
    assert failed, "Invalid user unexpectedly authenticated with public key!"
    print("test_publickey_authentication passed.")

if __name__ == '__main__':
    print("Running fuzz & security tests...")
    test_connection_and_prompt()
    try:
        test_long_random_fuzzing()
    except EOFError:
        print("EOFError handled safely (expected during OOM kill simulation)")
    test_port_forwarding_allowed_http_only()
    test_port_forwarding_concurrent_shell_and_http()
    test_port_forwarding_adversarial_rejections()
    test_publickey_authentication()
    print("All mock SSH tests completed successfully.")
