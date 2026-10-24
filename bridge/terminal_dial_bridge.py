#!/usr/bin/env python3
"""Loopback-only PTY bridge for the Terminal Dial Android client.

Run this script from Termux (not from inside Debian). It keeps one Debian shell
alive while the app disconnects/reconnects. Protocol: one JSON object per line.
"""
import fcntl
import json
import os
import pty
import select
import socket
import struct
import subprocess
import termios
import time
import tty

HOST = "127.0.0.1"
PORT = 8765
MAX_LINE = 64 * 1024
BACKLOG_LIMIT = 256 * 1024

ACTIONS = {
    "RECON": "printf '\\n[RECON tools]\\n'; command -v nmap dig nslookup whois",
    "WEB": "printf '\\n[WEB tools]\\n'; command -v ffuf curl wget",
    "REVERSE": "printf '\\n[REVERSE tools]\\n'; command -v file readelf objdump strings strace",
    "CODEX": "command -v codex >/dev/null 2>&1 && codex || printf '\\nCodex CLI not found in PATH.\\n'",
    "OPENCODE": "command -v opencode >/dev/null 2>&1 && opencode || printf '\\nOpenCode CLI not found in PATH.\\n'",
    "SYSTEM": "printf '\\n[Storage]\\n'; df -h; printf '\\n[Memory]\\n'; free -h 2>/dev/null || true; printf '\\n[Processes]\\n'; ps -ef | head -n 20",
}


def send_packet(client, packet):
    payload = (json.dumps(packet, ensure_ascii=False) + "\n").encode("utf-8")
    client.sendall(payload)


def set_terminal_size(fd, rows=32, cols=100):
    packed = struct.pack("HHHH", rows, cols, 0, 0)
    fcntl.ioctl(fd, termios.TIOCSWINSZ, packed)


def main():
    master, slave = pty.openpty()
    set_terminal_size(slave)
    command = ["proot-distro", "login", "debian", "--isolated", "--", "/bin/bash", "--login"]
    try:
        child = subprocess.Popen(
            command,
            stdin=slave,
            stdout=slave,
            stderr=slave,
            close_fds=True,
            start_new_session=True,
        )
    except Exception:
        os.close(master)
        os.close(slave)
        raise
    os.close(slave)
    tty.setraw(master, termios.TCSANOW)

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(1)
    server.setblocking(False)
    print(f"Terminal Dial bridge listening on {HOST}:{PORT}; Debian shell PID {child.pid}", flush=True)

    client = None
    client_buffer = bytearray()
    backlog = bytearray()
    try:
        while True:
            readers = [server, master]
            if client is not None:
                readers.append(client)
            readable, _, _ = select.select(readers, [], [], 0.25)

            for ready in readable:
                if ready is server:
                    new_client, address = server.accept()
                    if address[0] not in ("127.0.0.1", "::1"):
                        new_client.close()
                        continue
                    new_client.setblocking(False)
                    if client is not None:
                        try:
                            send_packet(new_client, {"type": "status", "message": "Another client is already connected"})
                        except OSError:
                            pass
                        new_client.close()
                        continue
                    client = new_client
                    client_buffer.clear()
                    try:
                        send_packet(client, {"type": "status", "message": "Connected to persistent Debian PTY"})
                        if backlog:
                            send_packet(client, {"type": "output", "data": backlog.decode("utf-8", errors="replace")})
                            backlog.clear()
                    except OSError:
                        client.close()
                        client = None

                elif ready == master:
                    try:
                        chunk = os.read(master, 8192)
                    except OSError:
                        chunk = b""
                    if not chunk:
                        time.sleep(0.1)
                        continue
                    text = chunk.decode("utf-8", errors="replace")
                    if client is not None:
                        try:
                            send_packet(client, {"type": "output", "data": text})
                        except OSError:
                            client.close()
                            client = None
                            backlog.extend(chunk)
                    else:
                        backlog.extend(chunk)
                    if len(backlog) > BACKLOG_LIMIT:
                        del backlog[:-BACKLOG_LIMIT]

                elif client is not None and ready is client:
                    try:
                        chunk = client.recv(8192)
                    except OSError:
                        chunk = b""
                    if not chunk:
                        client.close()
                        client = None
                        client_buffer.clear()
                        continue
                    client_buffer.extend(chunk)
                    if len(client_buffer) > MAX_LINE and b"\n" not in client_buffer:
                        send_packet(client, {"type": "status", "message": "Input frame too large"})
                        client_buffer.clear()
                        continue
                    while b"\n" in client_buffer:
                        raw, _, remainder = client_buffer.partition(b"\n")
                        client_buffer = bytearray(remainder)
                        try:
                            packet = json.loads(raw.decode("utf-8"))
                        except (UnicodeDecodeError, json.JSONDecodeError):
                            send_packet(client, {"type": "status", "message": "Invalid JSON packet"})
                            continue
                        kind = packet.get("type")
                        if kind == "input":
                            data = packet.get("data", "")
                            if isinstance(data, str) and len(data) <= MAX_LINE:
                                os.write(master, data.encode("utf-8"))
                        elif kind == "action":
                            name = packet.get("name")
                            action_command = ACTIONS.get(name)
                            if action_command:
                                os.write(master, (action_command + "\n").encode("utf-8"))
                            else:
                                send_packet(client, {"type": "status", "message": "Unknown action"})
                        elif kind == "resize":
                            try:
                                rows = max(10, min(100, int(packet.get("rows", 32))))
                                cols = max(30, min(240, int(packet.get("cols", 100))))
                                set_terminal_size(master, rows, cols)
                            except (TypeError, ValueError, OSError):
                                pass
                        else:
                            send_packet(client, {"type": "status", "message": "Unknown packet type"})

            if child.poll() is not None:
                if client is not None:
                    try:
                        send_packet(client, {"type": "status", "message": "Debian shell exited"})
                    except OSError:
                        pass
                    client.close()
                    client = None
                break
    finally:
        if client is not None:
            client.close()
        server.close()
        os.close(master)
        if child.poll() is None:
            child.terminate()
            try:
                child.wait(timeout=2)
            except subprocess.TimeoutExpired:
                child.kill()


if __name__ == "__main__":
    main()
