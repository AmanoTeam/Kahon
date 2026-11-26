#!/usr/bin/env python3

import urllib.request
from pathlib import Path

filter_lists = {
  "ultraprivacy.txt": "https://gitweb.stoutner.com/?p=PrivacyBrowserAndroid.git;a=blob_plain;f=app/src/main/assets/filterlists/ultraprivacy.txt",
  "ultralist.txt": "https://gitweb.stoutner.com/?p=PrivacyBrowserAndroid.git;a=blob_plain;f=app/src/main/assets/filterlists/ultralist.txt",
  "easyprivacy.txt": "https://easylist.to/easylist/easyprivacy.txt",
  "easylist.txt": "https://easylist.to/easylist/easylist.txt",
  "fanboy-annoyance.txt": "https://easylist.to/easylist/fanboy-annoyance.txt",
}

output_directory = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "filterlists"
output_directory.mkdir(parents=True, exist_ok=True)

headers = {
  "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
}

for file_name, url in filter_lists.items():
  print(f"Fetching {url}...")
  request = urllib.request.Request(url, headers=headers)
  with urllib.request.urlopen(request, timeout=120) as response:
    content = response.read()
  output_path = output_directory / file_name
  if output_path.exists() and output_path.read_bytes() == content:
    print(f"Up to date {output_path}")
  else:
    output_path.write_bytes(content)
    print(f"Wrote {output_path} ({len(content)} bytes)")
