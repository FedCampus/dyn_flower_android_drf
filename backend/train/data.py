from dataclasses import dataclass


@dataclass
class ServerData:
    status: str
    port: int | None
