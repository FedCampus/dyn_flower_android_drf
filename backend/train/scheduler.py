from multiprocessing import Pipe, Process

from train.run import run

_conn, conn1 = Pipe()
process = Process(target=run, args=(conn1,))
started = False


def conn():
    global started
    if not started:
        process.start()
        started = True
    return _conn


__all__ = ["conn"]
