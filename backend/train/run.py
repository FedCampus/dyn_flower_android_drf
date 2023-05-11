from logging import getLogger
from multiprocessing.connection import Connection
from time import sleep

PORT = 8080

logger = getLogger(__name__)


def execute(conn: Connection):
    """Execute one instruction received."""
    kind, msg = conn.recv()
    if kind == "ping":
        logger.warning(f"Received `{msg}`.")
        conn.send("pong")
    elif kind == "server":
        logger.warning(f"Launching server with arguments `{msg}`.")
        # TODO: Use `msg` and create a server.
        sleep(10)
        conn.send("done")
    else:
        logger.error(f"Unknown kind `{kind}` with message `{msg}`.")
        conn.send("cannot understand")


def run(conn: Connection):
    """Run a procedure that listens to instructions and executes them."""
    logger.warning("Runner started.")
    while True:
        try:
            execute(conn)
        except KeyboardInterrupt:
            break
