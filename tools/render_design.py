"""Render the design review board. This is a mockup, not an emulator capture."""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
W,H=1560,1050
im=Image.new('RGB',(W,H),'#101010'); d=ImageDraw.Draw(im)
font='/usr/share/fonts/google-noto/NotoSans-Regular.ttf'
bold='/usr/share/fonts/google-noto/NotoSans-Bold.ttf'
if not Path(font).exists():
 font='/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf';bold='/usr/share/fonts/dejavu-sans-fonts/DejaVuSans-Bold.ttf'
def text(x,y,s,size=15,c='#F4F4F1',b=False):d.text((x,y),s,font=ImageFont.truetype(bold if b else font,size),fill=c)
def card(x,y,w,h):d.rounded_rectangle((x,y,x+w,y+h),radius=17,fill='#191919')
def note(x,y,s):text(x,y,s,12,'#A5A5A5')
def metric(x,y,name,desc,pct,value,sub):
 card(x,y,306,99);text(x+15,y+11,name,25,b=True);note(x+15,y+45,desc);text(x+15,y+73,sub,10,'#A5A5A5')
 cx=x+255;cy=y+46
 d.ellipse((cx-31,cy-31,cx+31,cy+31),outline='#383838',width=5)
 if pct is not None:d.arc((cx-31,cy-31,cx+31,cy+31),-90,-90+3.6*pct,fill='#F4F4F1',width=5)
 text(cx-20,cy-11,value,17,b=True)
def pair(x,y,l,v):note(x,y,l);text(x+153,y,v,12)
text(42,28,'PC SENTINEL',26,b=True);text(42,68,'TITUS  /  NATIVE ANDROID DESIGN STUDY',12,'#A5A5A5')
text(1115,39,'DESIGN MOCKUP · DEMO DATA',12,'#E4C68A')
for idx,title in enumerate(['MY PC STATUS','PERFORMANCE','SYSTEM DETAILS','ACTIVITY']):
 x=40+idx*380
 d.rounded_rectangle((x,115,x+340,947),radius=30,fill='#080808',outline='#373737',width=2)
 text(x+19,137,'12:04',10);text(x+266,137,'•••',10)
 d.rectangle((x+19,182,x+34,192),outline='#F4F4F1',width=1);d.line((x+26,192,x+26,198),fill='#F4F4F1');text(x+42,177,'PC SENTINEL',15,b=True);text(x+268,180,'TITUS',9,'#A5A5A5')
 text(x+19,222,'DESIGN PREVIEW / DEMO DATA',9,'#E4C68A')
 text(x+19,246,title,24,b=True)
 note(x+19,284,['Your workstation. Within reach.','A clear view of the essentials.','The details behind the numbers.','What’s running, and who owns it.'][idx])
 for j,label in enumerate(['Power','Health','Details','Activity']):
  xx=x+12+j*81
  if j==idx:d.rounded_rectangle((xx,869,xx+77,922),radius=12,fill='#202020')
  text(xx+29,875,'0'+str(j+1),10,'#F4F4F1' if j==idx else '#A5A5A5')
  text(xx+12,895,label,11,'#F4F4F1' if j==idx else '#A5A5A5')
 x+=17
 if idx==0:
  card(x,325,306,425);d.ellipse((x+18,355,x+25,362),fill='#F4F4F1');text(x+33,348,'SYSTEM RESPONDING',17,b=True);note(x+18,379,'Demo state · authenticated OS response')
  cx=x+153;cy=509
  for rad,col,width in [(83,'#252525',16),(73,'#575757',1),(65,'#EEEEEE',3)]:d.ellipse((cx-rad,cy-rad,cx+rad,cy+rad),outline=col,width=width)
  d.arc((cx-19,cy-19,cx+19,cy+19),-45,225,fill='#F4F4F1',width=4);d.line((cx,cy-26,cx,cy+1),fill='#F4F4F1',width=4)
  text(x+48,617,'POWER OFF PC',26,b=True);note(x+85,655,'Preview confirmation')
  pair(x+18,701,'Last contact','Sample · 12:04:32')
  note(x+4,771,'Shutdown ends remote access.');note(x+4,791,'Live shutdown is disabled in this preview.')
 elif idx==1:
  for y,args in zip([325,433,541,649,757],[('CPU','Intel Core i5-9400',28,'28%','Package temperature · 43°C'),('RAM','11.5 GiB usable',58,'58%','6.7 GiB used · 4.8 GiB available'),('SSD','Kingston · system volume',71,'71%','76.2 GiB used · 31.1 GiB free'),('HDD','Seagate · data volume',1,'<1%','0.15 GiB used · 1.70 TiB available'),('GPU','RTX 2070 SUPER · nouveau',None,'—','Telemetry unavailable')]):metric(x,y,*args)
 elif idx==2:
  card(x,325,306,228);text(x+16,344,'SYSTEM / SAMPLE READINGS',10,'#A5A5A5')
  for y,l,v in [(378,'Operating system','Fedora 44'),(414,'Desktop','KDE Plasma 6.6.4'),(450,'Uptime','8 h 24 min'),(486,'Load · 1 / 5 / 15','0.74 / 0.88 / 0.91')]:pair(x+16,y,l,v)
  card(x,564,306,140);text(x+16,583,'PRIVATE CONNECTION',10,'#A5A5A5');pair(x+16,619,'Tailscale','Sample · connected');pair(x+16,656,'Latency','Sample · 24 ms')
  card(x,715,306,141);text(x+16,734,'SENSORS & HEALTH',10,'#A5A5A5');pair(x+16,771,'Fan / power','Unavailable');note(x+16,813,'Whole-PC power is not measured.')
 else:
  for j,l in enumerate(['All','Amus','skyler']):
   d.rounded_rectangle((x+j*91,321,x+80+j*91,354),radius=8,fill='#282828' if j==0 else '#191919');text(x+20+j*91,329,l,12)
  for y,n,o,k,r in [(372,'Firefox','Amus','Application','CPU 4.2% · RAM 820 MiB'),(487,'Android Studio','Amus','Application','CPU 8.1% · RAM 1.4 GiB'),(602,'python3','skyler','Background process','CPU 12.0% · RAM 184 MiB')]:
   card(x,y,306,104);text(x+17,y+13,n,18,b=True);note(x+17,y+43,o+' · '+k);text(x+17,y+72,r,12)
  text(x+2,733,'WINDOWS & BROWSER TABS',10,'#A5A5A5');note(x+2,763,'Not connected. Separate authorisation');note(x+2,784,'is required for desktop and tab metadata.');note(x+2,812,'No history, cookies or content collected.')
text(42,983,'Monochrome surfaces. Honest availability. Owner-aware activity. No live controls in the prototype.',15,'#A5A5A5')
im.save('design/pc-sentinel-design.png')
