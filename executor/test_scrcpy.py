"""Quick scrcpy-server connectivity test."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import scrcpy_stream


async def test(serial: str = "ACTH022219007784"):
    server_jar = scrcpy_stream.find_scrcpy_server()
    print("server:", server_jar)
    await scrcpy_stream._cleanup_serial(serial)
    await asyncio.sleep(0.2)
    port = 27200
    code, _, err = await scrcpy_stream._run_adb(
        "-s", serial, "push", str(server_jar), scrcpy_stream.REMOTE_JAR
    )
    print("push", code)
    shell = (
        f"CLASSPATH={scrcpy_stream.REMOTE_JAR} app_process / com.genymobile.scrcpy.Server "
        f"{scrcpy_stream.SERVER_VERSION} tunnel_forward=true audio=false control=false "
        f"cleanup=false raw_stream=true max_size=720 max_fps=30"
    )
    proc = await asyncio.create_subprocess_exec(
        "adb", "-s", serial, "shell", shell,
        stdout=asyncio.subprocess.DEVNULL,
        stderr=asyncio.subprocess.PIPE,
    )
    await asyncio.sleep(0.8)
    await scrcpy_stream._run_adb("-s", serial, "forward", f"tcp:{port}", "localabstract:scrcpy")
    reader, writer = await asyncio.wait_for(asyncio.open_connection("127.0.0.1", port), timeout=3)
    parser = scrcpy_stream._AnnexBParser()
    frames = 0
    for _ in range(30):
        chunk = await asyncio.wait_for(reader.read(65536), timeout=5)
        if not chunk:
            break
        nals = parser.feed(chunk)
        for nal in nals:
            ntype = scrcpy_stream._nal_type(nal)
            if ntype in (1, 5):
                frames += 1
                print(f"frame {frames}: type={ntype} key={ntype == 5} len={len(nal)}")
    writer.close()
    proc.terminate()
    await scrcpy_stream._run_adb("-s", serial, "forward", "--remove", f"tcp:{port}")
    print("OK", frames, "frames")


if __name__ == "__main__":
    asyncio.run(test())
