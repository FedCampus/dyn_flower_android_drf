from logging import getLogger
from multiprocessing import Pipe, Process
from multiprocessing.connection import Connection
from time import sleep

from train.models import TFLiteModel
from train.run import PORT, run

logger = getLogger(__name__)
_conn: Connection | None = None
process: Process
running: TFLiteModel | None = None


def spawn():
    """Spawn a new background process and assign `conn`."""
    global _conn, process
    _conn, conn1 = Pipe()
    process = Process(target=run, args=(conn1,))
    process.start()
    return _conn


def conn():
    """Get the connection to the background process."""
    if _conn is None:
        return spawn()
    return _conn


def clear():
    """Clear the messages received from the background process."""
    while conn().poll():
        received = conn().recv()
        logger.warning(f"Clearing message `{received}`.")


def ping():
    """Ping the background process to check if it is available."""
    conn().send(("ping", "ping"))


def check_alive():
    """Check if the background process is alive by pinging it and waiting for
    response for at most 1ms."""
    clear()
    ping()
    for _ in range(1000):
        if conn().poll():
            conn().recv()
            return True
        sleep(1e-6)
    return False


def ensure_alive():
    """Check if the background process is alive. Spawn a new one if not."""
    global process, _conn
    if not check_alive():
        process.kill()
        spawn()


def finish_unfinished():
    """Update `running` on whether the background process has finished."""
    global running
    while running and conn().poll():
        msg = conn().recv()
        if msg == "done":
            running = None
            break
        else:
            logger.error("Unknown message `{msg}` from runner.")


def server(model: TFLiteModel) -> tuple[str, int | None]:
    """Request a Flower server. Return `(status, port)`.
    `status` is "started" if the server is already running,
    "new" if newly started,
    or "occupied" if the background process is unavailable."""
    global running
    finish_unfinished()
    if running:
        if running == model:
            return "started", PORT
        else:
            return "occupied", None
    else:
        # Start new server.
        ensure_alive()
        conn().send(("server", model))
        running = model
        return "new", PORT
