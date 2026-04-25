#!/usr/bin/env python3
import re
import sys
from pathlib import Path

import yaml


REPO_ROOT = Path(__file__).resolve().parents[3]
SKILLS_ROOT = REPO_ROOT / ".skills"
NAME_PATTERN = re.compile(r"^[a-z0-9-]+$")
FRONTMATTER_PATTERN = re.compile(r"^---\n(.*?)\n---", re.DOTALL)


def fail(message):
    print(f"FAIL: {message}")
    return 1


def validate_skill_dir(skill_dir):
    errors = []
    skill_md = skill_dir / "SKILL.md"
    if not skill_md.exists():
        return [f"{skill_dir}: missing SKILL.md"]

    content = skill_md.read_text(encoding="utf-8")
    match = FRONTMATTER_PATTERN.match(content)
    if not match:
        return [f"{skill_md}: missing or invalid YAML frontmatter"]

    try:
        frontmatter = yaml.safe_load(match.group(1))
    except yaml.YAMLError as exc:
        return [f"{skill_md}: invalid YAML frontmatter: {exc}"]

    if not isinstance(frontmatter, dict):
        return [f"{skill_md}: frontmatter must be a mapping"]

    name = frontmatter.get("name")
    description = frontmatter.get("description")

    if not isinstance(name, str) or not name:
        errors.append(f"{skill_md}: name is required")
    elif not NAME_PATTERN.match(name):
        errors.append(f"{skill_md}: name must be lowercase hyphen-case")
    elif name != skill_dir.name:
        errors.append(f"{skill_md}: name '{name}' does not match directory '{skill_dir.name}'")

    if not isinstance(description, str) or not description.strip():
        errors.append(f"{skill_md}: description is required")
    elif len(description) > 1024:
        errors.append(f"{skill_md}: description exceeds 1024 characters")

    openai_yaml = skill_dir / "agents" / "openai.yaml"
    if not openai_yaml.exists():
        errors.append(f"{openai_yaml}: missing agents metadata")
        return errors

    try:
        metadata = yaml.safe_load(openai_yaml.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        errors.append(f"{openai_yaml}: invalid YAML: {exc}")
        return errors

    interface = metadata.get("interface") if isinstance(metadata, dict) else None
    if not isinstance(interface, dict):
        errors.append(f"{openai_yaml}: missing interface mapping")
        return errors

    display_name = interface.get("display_name")
    short_description = interface.get("short_description")
    default_prompt = interface.get("default_prompt")

    if not isinstance(display_name, str) or not display_name.strip():
        errors.append(f"{openai_yaml}: interface.display_name is required")

    if not isinstance(short_description, str):
        errors.append(f"{openai_yaml}: interface.short_description is required")
    elif not 25 <= len(short_description) <= 64:
        errors.append(f"{openai_yaml}: short_description must be 25-64 characters")

    if not isinstance(default_prompt, str) or f"${skill_dir.name}" not in default_prompt:
        errors.append(f"{openai_yaml}: default_prompt must mention ${skill_dir.name}")

    return errors


def main():
    if not SKILLS_ROOT.exists():
        return fail(f"skills root not found: {SKILLS_ROOT}")

    skill_dirs = sorted(path for path in SKILLS_ROOT.iterdir() if (path / "SKILL.md").exists())
    if not skill_dirs:
        return fail("no skills found")

    errors = []
    for skill_dir in skill_dirs:
        errors.extend(validate_skill_dir(skill_dir))

    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        return 1

    print(f"Validated {len(skill_dirs)} DTS skill(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
