# GenRL GameMaster AI - Sandbox Execution Service

import ast
import io
import json
import sys
import threading
import time
import traceback
import uuid
from contextlib import redirect_stdout, redirect_stderr
from typing import Any, Dict, List, Optional

import structlog

from app.core.config import settings

logger = structlog.get_logger()

# ---------------------------------------------------------------------------
# Security: patterns blocked in submitted code
# ---------------------------------------------------------------------------
_BLOCKED_PATTERNS: List[str] = [
    "os.system",
    "subprocess",
    "__import__",
]

# AST-based checks: function-call names that are always blocked
_BLOCKED_CALL_NAMES: frozenset = frozenset({"exec", "eval"})

# open() mode flags that indicate write access
_WRITE_MODES: frozenset = frozenset({"w", "a", "x", "wb", "ab", "xb", "w+", "a+", "r+", "r+b"})


class SandboxService:
    """Provides lightweight code validation and sandboxed execution."""

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    async def validate_code(self, code: str, language: str = "python") -> Dict[str, Any]:
        """Validate code for syntax correctness and basic security violations.

        Returns:
            {"valid": bool, "error": str | None}
        """
        if language != "python":
            return {"valid": False, "error": f"Unsupported language: {language}"}

        # 1. Syntax check
        try:
            tree = compile(code, "<sandbox>", "exec", ast.PyCF_ONLY_AST)
        except SyntaxError as exc:
            return {"valid": False, "error": f"SyntaxError: {exc}"}

        # 2. Pattern-based checks (fast, textual)
        for pattern in _BLOCKED_PATTERNS:
            if pattern in code:
                violation = f"Blocked pattern detected: '{pattern}'"
                await self._log_violation(violation, code)
                return {"valid": False, "error": violation}

        # 3. AST-based checks
        error = self._ast_security_check(tree)
        if error:
            await self._log_violation(error, code)
            return {"valid": False, "error": error}

        return {"valid": True, "error": None}

    async def execute_code(
        self,
        code: str,
        timeout_seconds: Optional[int] = None,
    ) -> Dict[str, Any]:
        """Execute code in a thread with a hard timeout.

        Returns:
            {"stdout": str, "stderr": str, "exit_code": int, "execution_time_ms": int}
        """
        cap = min(
            timeout_seconds or settings.SANDBOX_TIMEOUT_SECONDS,
            settings.SANDBOX_TIMEOUT_SECONDS,
        )

        # Validate first
        validation = await self.validate_code(code, "python")
        if not validation["valid"]:
            return {
                "stdout": "",
                "stderr": validation["error"],
                "exit_code": 1,
                "execution_time_ms": 0,
            }

        result: Dict[str, Any] = {
            "stdout": "",
            "stderr": "",
            "exit_code": 0,
            "execution_time_ms": 0,
        }

        def _run() -> None:
            stdout_buf = io.StringIO()
            stderr_buf = io.StringIO()
            start = time.perf_counter()
            try:
                with redirect_stdout(stdout_buf), redirect_stderr(stderr_buf):
                    exec(compile(code, "<sandbox>", "exec"), {"__builtins__": _safe_builtins()})  # noqa: S102
                result["exit_code"] = 0
            except Exception:  # noqa: BLE001
                stderr_buf.write(traceback.format_exc())
                result["exit_code"] = 1
            finally:
                elapsed_ms = int((time.perf_counter() - start) * 1000)
                result["stdout"] = stdout_buf.getvalue()
                result["stderr"] = stderr_buf.getvalue()
                result["execution_time_ms"] = elapsed_ms

        thread = threading.Thread(target=_run, daemon=True)
        thread.start()
        thread.join(timeout=cap)

        if thread.is_alive():
            result["stderr"] = f"Execution timed out after {cap} seconds"
            result["exit_code"] = 124
            logger.warning("sandbox_timeout", timeout_seconds=cap)

        return result

    # ------------------------------------------------------------------
    # Private – AST security analysis
    # ------------------------------------------------------------------

    def _ast_security_check(self, tree: ast.AST) -> Optional[str]:
        """Walk the AST and return an error string if a violation is found."""
        for node in ast.walk(tree):
            # Block exec() / eval() calls
            if isinstance(node, ast.Call):
                func = node.func
                # bare name: exec(...) or eval(...)
                if isinstance(func, ast.Name) and func.id in _BLOCKED_CALL_NAMES:
                    return f"Blocked call: '{func.id}()'"
                # attribute: something.exec(...) – less common but block anyway
                if isinstance(func, ast.Attribute) and func.attr in _BLOCKED_CALL_NAMES:
                    return f"Blocked call: '*.{func.attr}()'"

            # Block open() with write modes
            if isinstance(node, ast.Call):
                func = node.func
                if isinstance(func, ast.Name) and func.id == "open":
                    mode = self._extract_open_mode(node)
                    if mode and mode in _WRITE_MODES:
                        return f"Blocked open() with write mode: '{mode}'"

        return None

    @staticmethod
    def _extract_open_mode(call_node: ast.Call) -> Optional[str]:
        """Extract the mode argument from an open() call node."""
        # open(file, mode=...) — positional arg at index 1
        if len(call_node.args) >= 2:
            arg = call_node.args[1]
            if isinstance(arg, ast.Constant) and isinstance(arg.value, str):
                return arg.value
        # open(file, mode=...) — keyword arg
        for kw in call_node.keywords:
            if kw.arg == "mode" and isinstance(kw.value, ast.Constant):
                return str(kw.value.value)
        return None

    # ------------------------------------------------------------------
    # Private – DB logging
    # ------------------------------------------------------------------

    async def _log_violation(self, violation: str, code: str) -> None:
        from app.core.database import get_raw_connection

        payload = json.dumps(
            {
                "violation": violation,
                "code_preview": code[:500],
            }
        )
        try:
            conn = await get_raw_connection()
            try:
                await conn.execute(
                    """
                    INSERT INTO system_events
                        (id, event_type, service, experiment_id, payload, severity, created_at)
                    VALUES ($1, $2, $3, NULL, $4::jsonb, $5, NOW())
                    """,
                    str(uuid.uuid4()),
                    "sandbox_violation",
                    "sandbox",
                    payload,
                    "warning",
                )
            finally:
                await conn.close()
        except Exception as exc:  # noqa: BLE001
            logger.error("sandbox_violation_log_error", error=str(exc))


# ---------------------------------------------------------------------------
# Restricted builtins for sandboxed exec
# ---------------------------------------------------------------------------

def _safe_builtins() -> Dict[str, Any]:
    """Return a restricted __builtins__ dict for sandboxed execution."""
    safe_names = {
        "abs", "all", "any", "bin", "bool", "bytearray", "bytes",
        "callable", "chr", "complex", "dict", "dir", "divmod",
        "enumerate", "filter", "float", "format", "frozenset",
        "getattr", "hasattr", "hash", "hex", "int", "isinstance",
        "issubclass", "iter", "len", "list", "map", "max", "min",
        "next", "object", "oct", "ord", "pow", "print", "property",
        "range", "repr", "reversed", "round", "set", "setattr",
        "slice", "sorted", "str", "sum", "super", "tuple", "type",
        "vars", "zip", "None", "True", "False",
        "Exception", "ValueError", "TypeError", "KeyError",
        "IndexError", "AttributeError", "StopIteration",
        "ArithmeticError", "ZeroDivisionError", "RuntimeError",
        "NotImplementedError", "OverflowError",
    }
    import builtins
    return {name: getattr(builtins, name) for name in safe_names if hasattr(builtins, name)}
