import math
def gear(cx,cy,r_out,r_in,teeth,hole,rot=0):
    pts=[]
    n=teeth
    for i in range(n):
        a=rot+2*math.pi*i/n
        w=2*math.pi/n
        # tooth: root-start, tip-start, tip-end, root-end
        for off,r in ((0.0,r_in),(0.18,r_out),(0.5-0.18,r_out),(0.5,r_in)):
            ang=a+off*w
            pts.append((cx+r*math.cos(ang),cy+r*math.sin(ang)))
        # next root continues
    d="M"+" L".join(f"{x:.2f},{y:.2f}" for x,y in pts)+" Z"
    # hole (counter-direction circle)
    d+=f" M{cx+hole:.2f},{cy:.2f} A{hole},{hole} 0 1 0 {cx-hole:.2f},{cy:.2f} A{hole},{hole} 0 1 0 {cx+hole:.2f},{cy:.2f} Z"
    return d
# 108x108 canvas; safe zone center 66
g1=gear(50,56,27,20,12,9,0.1)
g2=gear(76,34,15,11,9,5,0.3)
g3=gear(74,80,12,8.8,8,4,0.0)
svg=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
<defs>
<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a475e"/><stop offset="1" stop-color="#0e1621"/></linearGradient>
<linearGradient id="m" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="0.5" stop-color="#a9d6f2"/><stop offset="1" stop-color="#3f78a0"/></linearGradient>
<linearGradient id="a" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#b8f06a"/><stop offset="1" stop-color="#4c7d12"/></linearGradient>
</defs>
<rect width="108" height="108" fill="url(#bg)"/>
<path d="{g1}" fill="url(#m)" fill-rule="evenodd" stroke="#0a1a2a" stroke-width="0.8"/>
<path d="{g2}" fill="url(#a)" fill-rule="evenodd" stroke="#243d08" stroke-width="0.8"/>
<path d="{g3}" fill="url(#m)" fill-rule="evenodd" stroke="#0a1a2a" stroke-width="0.8"/>
</svg>'''
open('/root/projects/PunkStore/tools/icon.svg','w').write(svg)
def vec(paths):
    return paths
bg='''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
<path android:pathData="M0,0h108v108h-108z"><aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="android:fillColor"><gradient android:startX="54" android:startY="0" android:endX="54" android:endY="108" android:type="linear"><item android:offset="0" android:color="#FF8D96A3"/><item android:offset="1" android:color="#FF3B414B"/></gradient></aapt:attr></path></vector>'''
def p(d,c1,c2,stroke):
    return f'''<path android:pathData="{d}" android:fillType="evenOdd" android:strokeColor="{stroke}" android:strokeWidth="0.8"><aapt:attr name="android:fillColor"><gradient android:startX="54" android:startY="10" android:endX="54" android:endY="100" android:type="linear"><item android:offset="0" android:color="{c1}"/><item android:offset="1" android:color="{c2}"/></gradient></aapt:attr></path>'''
fg='<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'+p(g1,"#FFFFFFFF","#FF7D8794","#FF2A2F37")+p(g2,"#FF8FE0FF","#FF1B7FC4","#FF0E3A5C")+p(g3,"#FFFFFFFF","#FF7D8794","#FF2A2F37")+'</vector>'
bg=bg.replace('<vector xmlns:android','<vector xmlns:aapt="http://schemas.android.com/aapt" xmlns:android').replace('xmlns:aapt="http://schemas.android.com/aapt" name=','name=')
r='/root/projects/PunkStore/app/src/main/res/'
open(r+'drawable/ic_launcher_background.xml','w').write(bg)
open(r+'drawable/ic_launcher_foreground.xml','w').write(fg)
open(r+'mipmap-anydpi-v26/ic_launcher.xml','w').write('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/></adaptive-icon>')
open(r+'mipmap-anydpi-v26/ic_launcher_round.xml','w').write('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/></adaptive-icon>')
