from logging import getLogger
from multiprocessing.connection import Connection

logger = getLogger(__name__)


def execute(conn: Connection):
    msg = conn.recv()
    # TODO: Use the message.
    logger.warning(f"Received `{msg}`.")
    conn.send(msg)


def run(conn: Connection):
    logger.warning("Runner started.")
    while True:
        try:
            execute(conn)
        except KeyboardInterrupt:
            break
