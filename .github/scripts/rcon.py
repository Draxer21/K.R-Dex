"""Cliente RCON minimo: python3 rcon.py <comando> (puerto 25575, clave dexrider)."""
import socket, struct, sys, time
def pkt(i, t, body):
    b = body.encode() + b"\x00\x00"
    return struct.pack("<iii", len(b) + 8, i, t) + b
for attempt in range(30):
    try:
        s = socket.create_connection(("127.0.0.1", 25575), timeout=10); break
    except OSError:
        time.sleep(2)
else:
    sys.exit("sin rcon")
s.sendall(pkt(1, 3, "dexrider")); s.recv(4096)
s.sendall(pkt(2, 2, " ".join(sys.argv[1:]))); time.sleep(1)
try: print(s.recv(4096)[12:-2].decode(errors="ignore"))
except Exception: pass
