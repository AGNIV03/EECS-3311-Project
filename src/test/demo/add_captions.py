"""Adds a caption bar under a recorded walkthrough.

The GUI walkthrough writes a timeline (seconds <TAB> caption) while it runs. This script turns
it into subtitles and burns them into a bar below the picture, so each scene is labelled with
its feature number, what is being shown and where the AI is involved.

Usage: python add_captions.py <ffmpeg.exe> <raw.mp4> <timeline.txt> <output.mp4>
"""
import os
import subprocess
import sys
import textwrap


def stamp(seconds):
    ms = int(round(seconds * 1000))
    return "%d:%02d:%02d.%02d" % (ms // 3600000, ms // 60000 % 60, ms // 1000 % 60, ms % 1000 // 10)


def main():
    ffmpeg, raw, timeline, output = sys.argv[1:5]
    rows = []
    for line in open(timeline, encoding="utf8"):
        if "\t" in line:
            when, text = line.rstrip("\n").split("\t", 1)
            rows.append((float(when), text))
    bar = 96
    folder = os.path.dirname(os.path.abspath(output))
    ass = os.path.join(folder, "captions.ass")
    with open(ass, "w", encoding="utf8") as out:
        out.write("[Script Info]\nScriptType: v4.00+\nPlayResX: 1582\nPlayResY: %d\n\n" % (972 + bar))
        out.write("[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, OutlineColour, BackColour, "
                  "Bold, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV\n")
        out.write("Style: Cap,Segoe UI,27,&H00FFFFFF,&H005F3A1F,&H005F3A1F,0,1,0,0,2,24,24,14\n\n")
        out.write("[Events]\nFormat: Layer, Start, End, Style, Text\n")
        for i, (start, text) in enumerate(rows):
            if text == "END" or text.startswith("STOPPED"):
                continue
            end = rows[i + 1][0] if i + 1 < len(rows) else start + 5
            head, _, rest = text.partition(". ")
            if head.startswith("F") and rest:
                text = "{\\b1}" + head + ".{\\b0} " + rest
            lines = textwrap.wrap(text, 118)
            out.write("Dialogue: 0,%s,%s,Cap,%s\n" % (stamp(start), stamp(end), "\\N".join(lines)))
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-i", os.path.abspath(raw),
                    "-vf", "scale=1582:972,pad=1582:%d:0:0:color=0x1f3a5f,ass=captions.ass" % (972 + bar),
                    "-c:v", "libx264", "-preset", "medium", "-crf", "21", "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart", os.path.abspath(output)], cwd=folder, check=True)


if __name__ == "__main__":
    main()
