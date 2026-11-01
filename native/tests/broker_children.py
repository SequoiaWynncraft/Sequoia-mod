"""Finds a broker's decoder in a Windows process list; kept free of Windows APIs so it is tested everywhere."""


def decoder_pids(processes, broker_pid, decoder_name):
    """PIDs in PROCESSES, (pid, parent pid, image name) rows from Toolhelp, of the broker's decoder.

    Toolhelp's parent PID names whichever process created a process, even after that creator exited
    and Windows gave its PID to another process. A broker can therefore appear as the parent of an
    unrelated process, such as csrss.exe, which outlives the smss.exe instance that started it, and
    that a test cannot open. The broker starts exactly one process, from DECODER_NAME, so only that
    image counts.
    """
    name = decoder_name.casefold()
    return [pid for pid, parent, image in processes if parent == broker_pid and image.casefold() == name]
