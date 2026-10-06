import subprocess
for src, dst in [('home-lavender.jpg', 'palette-lavender.jpg'), ('home-sakura.jpg', 'palette-sakura.jpg')]:
    b = subprocess.run(['gh', 'api', 'repos/xUmutKx/Palette/contents/screenshots/' + src, '-H', 'Accept: application/vnd.github.raw'], capture_output=True)
    print(src, b.returncode, len(b.stdout))
    if b.returncode == 0 and b.stdout[:2] == b'\xff\xd8':
        open('/root/projects/PunkStore/app/src/main/assets/umutk/' + dst, 'wb').write(b.stdout)
