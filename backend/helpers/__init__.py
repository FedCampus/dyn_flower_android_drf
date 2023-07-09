from sys import stderr


def eprint(*args, **kwargs):
    print(*args, file=stderr, **kwargs)
