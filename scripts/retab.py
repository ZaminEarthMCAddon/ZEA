#!/usr/bin/env python3
"""Convert leading 8-space indentation groups to tabs in the given files.

The map package (and most newer ZEA packages) indent with tabs; a few
editor pipelines hand back spaces. Run over a file to normalize its
leading indentation to one tab per level, leaving string literals and
trailing content untouched.
"""
import sys


def retab(path: str) -> None:
    out = []
    for line in open(path):
        stripped = line.lstrip(' ')
        depth = (len(line) - len(stripped)) // 8
        remainder = (len(line) - len(stripped)) % 8
        out.append('\t' * depth + ' ' * remainder + stripped)
    open(path, 'w').writelines(out)


if __name__ == '__main__':
    for target in sys.argv[1:]:
        retab(target)
        print('retabbed', target)
