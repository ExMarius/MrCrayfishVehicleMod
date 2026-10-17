"""Listeneri temporari pe multe porturi — folosit de workflow-ul vm-port-probe.
Ruleaza sub `timeout 130` pe VM (nohup), accepta si inchide fie care conexiune.
Nu ruleaza ca serviciu permanent.
"""
import select
import socket
import time

PORTS = [80, 443, 3000, 3478, 7000, 7777, 8000, 8080, 8443, 8888, 9000, 19132, 27015]

socks = []
for p in PORTS:
    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        s.bind(("0.0.0.0", p))
        s.listen(1)
        socks.append(s)
    except OSError:
        s.close()

end = time.time() + 130
while time.time() < end and socks:
    r, _, _ = select.select(socks, [], [], 1)
    for s in r:
        try:
            c, _ = s.accept()
            c.close()
        except OSError:
            pass
