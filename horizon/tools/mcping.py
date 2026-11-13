"""Ping Minecraft status (protocol 1.7+) si afiseaza JSON-ul serverului."""
import socket, struct, json, sys

HOST = sys.argv[1] if len(sys.argv) > 1 else "jacob.hidencloud.com"
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 24601


def varint(value):
    out = b""
    while True:
        byte = value & 0x7F
        value >>= 7
        out += bytes([byte | (0x80 if value else 0)])
        if not value:
            return out


def read_varint(sock):
    result = 0
    shift = 0
    while True:
        byte = sock.recv(1)
        if not byte:
            raise EOFError("conexiune inchisa")
        result |= (byte[0] & 0x7F) << shift
        if not byte[0] & 0x80:
            return result
        shift += 7


def main():
    sock = socket.create_connection((HOST, PORT), timeout=10)
    payload = varint(0x00) + varint(-1) + varint(len(HOST)) + HOST.encode() + struct.pack(">H", PORT) + varint(1)
    sock.sendall(varint(len(payload)) + payload)
    sock.sendall(varint(1) + varint(0x00))
    length = read_varint(sock)
    read_varint(sock)
    data = b""
    while len(data) < length - 1:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
    text = data[read_varint(sock):] if False else data
    print(text.decode("utf-8", "replace")[:1200])
    sock.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("PING ESUAT:", error)
