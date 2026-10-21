#!/usr/bin/env python3
"""Serve a music folder for Gramo sync.

Usage:
    python3 serve_music.py [folder] [port]

Then in Gramo: IMPORT -> SYNC URL, enter:
    http://<this-computer-ip>:<port>/gramo.json
"""
import json
import os
import sys
from functools import partial
from http.server import HTTPServer, SimpleHTTPRequestHandler

AUDIO = {".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".oga", ".opus"}


def manifest(root):
    files = []
    for dirpath, _, names in os.walk(root):
        for name in names:
            if os.path.splitext(name)[1].lower() in AUDIO:
                files.append({
                    "name": name,
                    "size": os.path.getsize(os.path.join(dirpath, name)),
                })
    files.sort(key=lambda f: f["name"])
    return json.dumps({"files": files}).encode()


class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.split("?")[0] == "/gramo.json":
            body = manifest(self.directory)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            super().do_GET()

    def log_message(self, fmt, *args):
        sys.stderr.write(" ".join(map(str, args)) + "\n")


def main():
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else ".")
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 8765
    handler = partial(Handler, directory=root)
    server = HTTPServer(("0.0.0.0", port), handler)
    print(f"Serving {root}")
    print(f"Gramo sync URL: http://<this-computer-ip>:{port}/gramo.json")
    server.serve_forever()


if __name__ == "__main__":
    main()
