from gtts import gTTS
import sys

if len(sys.argv) < 3:
    print("Usage: tts.py \"text\" say-output.mp3")
    exit(1)

text = sys.argv[1]
output = sys.argv[2]

tts = gTTS(text=text, lang="ru")
tts.save(output)
