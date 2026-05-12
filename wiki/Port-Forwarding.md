# Port Forwarding

The **Port Forwarding** section allows you to configure local, remote, and dynamic SSH port forwarding (tunneling).

## Types of Forwarding

- **Local (L):** Forwards a port on your local Android device to a specific port on a remote host through the SSH server. Useful for accessing internal web panels.
- **Remote (R):** Forwards a port on the remote SSH server back to a port on your local device. 
- **Dynamic (D):** Creates a SOCKS proxy on your local device that routes all traffic dynamically through the SSH server.

## Example
If you want to access a web server running on `localhost:8080` on the remote server, configure a **Local** forward with local port `8080` and remote target `localhost:8080`. You can then open your Android web browser and navigate to `http://localhost:8080`.