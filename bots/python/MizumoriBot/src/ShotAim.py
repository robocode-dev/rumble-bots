"""Actual-shot targeting; inputs are only public scans and confirmed fire events.

No all-turn virtual waves, observer files, opponent names or future positions.
The enemy's reaction is scored only after an actual shot's wave arrives.
"""
from collections import deque
import math

def rel(a):return (a+180)%360-180
def clamp(x,a,b):return max(a,min(b,x))

class ShotAim:
    names=('circular','half','head_on')
    def __init__(self,mode):
        self.mode=mode;self.memory=deque(maxlen=256)
        self.global_stats={n:[0.,0] for n in self.names};self.local={}
        self.errors={n:[0.,0] for n in self.names}
        self.completed=0;self.confirmed=0;self.censored=0;self.selected='circular'
        self.new_round()

    def new_round(self):
        self.pending=[];self.requests={};self.last_plan=None;self.last_turn=None;self.last_switch=-100

    @staticmethod
    def half(e,origin,arena,speed):
        x,y=e['x'],e['y'];h=math.radians(e['direction']);v=.5*e['speed']
        for tick in range(1,121):
            x=clamp(x+v*math.cos(h),18,arena[0]-18);y=clamp(y+v*math.sin(h),18,arena[1]-18)
            if tick*speed>=math.hypot(x-origin[0],y-origin[1]):break
        return x,y

    def observe(self,e):
        t=e['turn']
        if self.last_turn is not None and t<=self.last_turn:return
        gap=1 if self.last_turn is None else t-self.last_turn;self.last_turn=t
        remain=[]
        for w in self.pending:
            if t<w['fire']:remain.append(w);continue
            dx,dy=e['x']-w['x'],e['y']-w['y'];d=math.hypot(dx,dy)
            # In 1.4.0 the bullet already moves once in its firing tick.
            if (t-w['fire']+1)*w['speed']<d-18:
                if t-w['fire']<150:remain.append(w)
                else:self.censored+=1
                continue
            if gap>2:self.censored+=1;continue
            actual=math.degrees(math.atan2(dy,dx));width=max(.5,math.degrees(math.atan2(18,max(d,1))))
            local=self.local.setdefault(w['context'],{n:[0.,0] for n in self.names})
            for name,bearing in w['headings'].items():
                error=abs(rel(actual-bearing));reward=math.exp(-.5*(error/width)**2)
                for bucket in (self.global_stats[name],local[name]):
                    alpha=1/min(24,bucket[1]+1);bucket[0]+=alpha*(reward-bucket[0]);bucket[1]+=1
                self.errors[name][0]+=error;self.errors[name][1]+=1
            gf=clamp(rel(actual-w['bearing'])/w['mae']*w['sign'],-1.2,1.2)
            self.memory.append((w['features'],gf));self.completed+=1
        self.pending=remain
        self.requests={k:v for k,v in self.requests.items() if t-k<=3}

    def plan(self,e,origin,arena,power,turn,circular):
        speed=20-3*max(.1,min(3,power));dx=e['x']-origin[0];dy=e['y']-origin[1];d=math.hypot(dx,dy)
        bearing=math.degrees(math.atan2(dy,dx));mae=math.degrees(math.asin(8/speed))
        lateral=e['speed']*math.sin(math.radians(e['direction']-bearing));advancing=e['speed']*math.cos(math.radians(e['direction']-bearing))
        sign=1 if lateral>=0 else -1
        features=(d/650,abs(lateral)/8,advancing/8,abs(e['rate'])/10,min(e['x'],e['y'],arena[0]-e['x'],arena[1]-e['y'])/150)
        context=(d>=250,abs(e['speed'])>=4)
        points=dict(circular=circular,half=self.half(e,origin,arena,speed),head_on=(e['x'],e['y']))
        model='circular';scores={n:s[0] for n,s in self.global_stats.items()}
        if self.mode=='half':model='half'
        elif self.mode=='realvg' and self.completed>=6:
            local=self.local.get(context,{})
            for n in self.names:
                value,count=local.get(n,(0.,0));mix=min(.65,count/24)
                scores[n]=(1-mix)*scores[n]+mix*value
            best=max(self.names,key=lambda n:scores[n])
            if turn-self.last_switch>=8 and scores[best]>scores[self.selected]+.02:
                self.selected=best;self.last_switch=turn
            model=self.selected
        elif self.mode=='density' and len(self.memory)>=12:
            weights=(1.,2.,1.,.5,.5)
            neighbors=sorted(((sum(w*(a-b)**2 for w,a,b in zip(weights,features,f)),gf,index)
                for index,(f,gf) in enumerate(self.memory)),key=lambda a:a[0])[:36]
            width=clamp(math.degrees(math.atan2(18,max(d,1)))/mae,.08,.3)
            def density(g):return sum(.98**(len(self.memory)-1-index)/(.1+delta)*math.exp(-.5*((g-gf)/width)**2) for delta,gf,index in neighbors)
            gf=max((i/15-1 for i in range(31)),key=density)
            heading=math.radians(bearing+gf*mae*sign)
            points['density']=(origin[0]+d*math.cos(heading),origin[1]+d*math.sin(heading));model='density'
        if e.get('stationary',0)>=12:model='circular'
        self.last_plan=dict(turn=turn,x=origin[0],y=origin[1],speed=speed,bearing=bearing,mae=mae,sign=sign,
            features=features,context=context,points={n:points[n] for n in self.names})
        return points[model],dict(model=model,scores=scores,completed=self.completed,confirmed=self.confirmed,
            pending=len(self.pending),censored=self.censored,actualOnly=True)

    def requested(self,turn):
        if self.last_plan and self.last_plan['turn']==turn:self.requests[turn]=dict(self.last_plan)

    def fired(self,event):
        w=self.requests.pop(event.turn_number-1,None)
        if not w:return
        b=event.bullet;w.update(fire=event.turn_number,x=b.x,y=b.y,speed=20-3*b.power)
        w['headings']={n:math.degrees(math.atan2(y-b.y,x-b.x)) for n,(x,y) in w.pop('points').items()}
        self.pending.append(w);self.confirmed+=1
