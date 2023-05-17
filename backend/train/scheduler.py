from logging import getLogger
from multiprocessing import Pipe, Process
from multiprocessing.connection import Connection
from threading import Thread
from time import sleep

from train.models import ModelParams, TFLiteModel
from train.run import PORT, run

logger = getLogger(__name__)
_conn: Connection | None = None
db_conn: Connection | None = None
process: Process
running: TFLiteModel | None = None
thread: Thread | None = None


def monitor_db_conn_once(db_conn: Connection):
    kind, msg = db_conn.recv()
    if kind == "done":
        logger.info("DB monitor thread shutting down")
        return True
    elif kind == "save_params":
        if not isinstance(msg, list):
            logger.error(f"Wrong parameters {msg} for `save_params`.")
            return False
        if running is None:
            logger.error(f"Received `save_params` while no running models is found.")
            return False
        to_save = ModelParams(params=msg, tflite_model=running)
        try:
            to_save.save()
        except RuntimeError as err:
            logger.error(err)
    return False


def monitor_db_conn(db_conn: Connection):
    while not db_conn.closed:
        try:
            if monitor_db_conn_once(db_conn):
                break
        except RuntimeError as err:
            logger.error(err)
    logger.warning("DB monitor thread exiting.")


def spawn():
    """Spawn a new background process and assign `conn`."""
    global _conn, db_conn, process, thread
    _conn, conn1 = Pipe()
    process = Process(target=run, args=(conn1,))
    process.start()
    db_conn, db_conn1 = Pipe()
    if thread is not None:
        try:
            thread.join(timeout=1e-6)
        except RuntimeError as err:
            logger.error(err)
    thread = Thread(target=monitor_db_conn, args=(db_conn1,))
    thread.start()
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
        conn().send(("server", db_conn))
        running = model
        return "new", PORT
