#!/usr/bin/env python3
"""Synchronize Minecraft's login-time resource pack with the Paper plugin config."""

import argparse
import json
import re
import uuid
from pathlib import Path
from typing import Dict, List, Set


RESOURCE_KEYS = (
    "resource-pack",
    "resource-pack-sha1",
    "resource-pack-id",
    "resource-pack-prompt",
    "require-resource-pack",
)


def plugin_pack(config_text: str) -> Dict[str, str]:
    """Read the deliberately simple resource-pack section without a YAML dependency."""
    lines = config_text.splitlines()
    try:
        start = next(index for index, line in enumerate(lines) if line.strip() == "resource-pack:")
    except StopIteration as error:
        raise ValueError("config.yml has no resource-pack section") from error
    section_lines = []  # type: List[str]
    for line in lines[start + 1:]:
        if line and not line[0].isspace():
            break
        if line.strip():
            section_lines.append(line)
    body = "\n".join(section_lines)

    def quoted(name: str) -> str:
        match = re.search(rf'(?m)^\s+{re.escape(name)}:\s*"([^"\r\n]*)"\s*$', body)
        if match is None:
            raise ValueError(f"resource-pack.{name} is missing or is not a quoted scalar")
        return match.group(1)

    required_match = re.search(r"(?m)^\s+required:\s*(true|false)\s*$", body, re.IGNORECASE)
    if required_match is None:
        raise ValueError("resource-pack.required is missing or invalid")

    url = quoted("url").strip()
    sha1 = quoted("sha1").strip().lower()
    prompt = quoted("prompt")
    if not url:
        raise ValueError("resource-pack.url is empty")
    if not re.fullmatch(r"[0-9a-f]{40}", sha1):
        raise ValueError("resource-pack.sha1 must contain exactly 40 hexadecimal characters")

    return {
        "resource-pack": url,
        "resource-pack-sha1": sha1,
        "resource-pack-id": str(uuid.uuid5(uuid.NAMESPACE_URL, url)),
        "resource-pack-prompt": json.dumps({"text": prompt}, ensure_ascii=True, separators=(",", ":")),
        "require-resource-pack": required_match.group(1).lower(),
    }


def synchronize(properties_text: str, values: Dict[str, str]) -> str:
    """Replace only resource-pack properties while preserving every unrelated line."""
    output = []  # type: List[str]
    seen = set()  # type: Set[str]
    for line in properties_text.splitlines():
        stripped = line.lstrip()
        if stripped and not stripped.startswith(("#", "!")):
            match = re.match(r"([^:=\s]+)\s*[:=]", stripped)
            if match is not None and match.group(1) in values:
                key = match.group(1)
                if key not in seen:
                    output.append(f"{key}={values[key]}")
                    seen.add(key)
                continue
        output.append(line)

    for key in RESOURCE_KEYS:
        if key not in seen:
            output.append(f"{key}={values[key]}")
    return "\n".join(output) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--server-properties", type=Path, required=True)
    parser.add_argument("--plugin-config", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    properties = args.server_properties.read_text(encoding="utf-8", errors="strict")
    config = args.plugin_config.read_text(encoding="utf-8", errors="strict")
    result = synchronize(properties, plugin_pack(config))
    with args.output.open("w", encoding="utf-8", newline="\n") as output:
        output.write(result)


if __name__ == "__main__":
    main()
