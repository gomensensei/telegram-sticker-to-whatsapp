import traceback

from tgwa.core import LOCAL_DIR
from tgwa.server import run


if __name__ == "__main__":
    try:
        run()
    except Exception:
        LOCAL_DIR.mkdir(parents=True, exist_ok=True)
        (LOCAL_DIR / "app-error.log").write_text(
            traceback.format_exc(),
            encoding="utf-8",
        )
        raise
