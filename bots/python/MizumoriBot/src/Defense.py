"""Causal 1v1 defense using public scans/hit events only (API 1.4.0).

No observer/replay, opponent internals, names or starting-coordinate rules.
Energy reconciliation is deferred until _step, after the turn's handlers.
"""
import math
from collections import deque

def rel(a):return (a+180)%360-180
def clamp(x,a,b):return max(a,min(x,b))
def bearing(x,y,tx,ty):return math.degrees(math.atan2(ty-y,tx-x))
def advance(x,y,h,v,error,target):
    # Exact 1.4.0 MathKt.calcNewBotSpeed, verified against the local server JAR.
    # In this release target zero stops immediately; reversing uses v/2 - 1.
    # Do not assume Classic Robocode's usual two-units deceleration here.
    sign=1 if v>=0 else -1
    speed=abs(v);wanted=target*sign
    if speed>0:
        if wanted>0:speed+=clamp(wanted-speed,-2,1)
        elif wanted==0:speed=0
        else:speed=speed/2-1
    else:speed=clamp(wanted,-1,1)
    v=clamp(speed*sign,-8,8)
    x+=math.cos(math.radians(h))*v;y+=math.sin(math.radians(h))*v
    h+=clamp(error,-10+.75*abs(v),10-.75*abs(v))
    return x,y,h,v

def steering(x,y,h,ex,ey,orbit,width,height):
    d=math.hypot(ex-x,ey-y)
    desired=bearing(x,y,ex,ey)+orbit*(90+clamp((270-d)*.16,-35,35))
    for _ in range(72):
        a=math.radians(desired);px=x+145*math.cos(a);py=y+145*math.sin(a)
        if 45<px<width-45 and 45<py<height-45:break
        desired+=orbit*5
    error=rel(desired-h);drive=1
    if abs(error)>90:error=rel(error+180);drive=-1
    return error,drive*(5 if abs(error)>45 else 8)

class Defense:
    def __init__(self,mode='timed'):
        self.mode=mode
        self.hits=[]  # only already observed real hits, retained between rounds
        self.new_round()

    def new_round(self):
        self.old=None;self.pending=None;self.ledger=[];self.waves=[]
        self.history=deque(maxlen=6);self.last_fire=-100;self.last_choice=-100
        self.choice=None;self.next_id=0;self.log=deque(maxlen=256);self.actions=0
        self.diagnostic={};self.hit_turns=deque(maxlen=64)

    def observe(self,enemy):self.pending=dict(enemy)
    # 1.4.0 scans precede bullet impacts but follow bot contacts.
    # A hit at t first affects scanned energy at t+1. Firing is already reflected
    # in the t scan. Credit the effects to their first observable scan, not t.
    def damage(self,turn,amount):
        self.ledger.append((turn+1,-amount));self.hit_turns.append(turn)
    def gain(self,turn,power):
        self.ledger.append((turn+1,3*power));self.hit_turns.append(turn)
    def collision(self,turn):self.ledger.append((turn,-.6))

    def hit(self,bot,event):
        candidates=[w for w in self.waves if abs(w['power']-event.bullet.power)<.05 and
            abs(math.hypot(bot.x-w['x'],bot.y-w['y'])-(event.turn_number-w['time'])*w['speed'])<55]
        if candidates:
            w=min(candidates,key=lambda q:abs(math.hypot(bot.x-q['x'],bot.y-q['y'])-(event.turn_number-q['time'])*q['speed']))
            gf=clamp(rel(event.bullet.direction-w['bearing'])/w['mea']*w['sign'],-1.5,1.5)
            self.hits.append(gf);self.hits=self.hits[-80:]
            w['hit']=True
            self.log.append(dict(type='matchedHit',turn=event.turn_number,wave=w['id'],gf=gf))

    def update(self,bot):
        t=bot.turn_number
        self.history.append(dict(turn=t,x=bot.x,y=bot.y,h=bot.direction,v=bot.speed))
        if self.pending:
            e=self.pending;self.pending=None;old=self.old
            if old and e['turn']>old['turn']:
                corrections=sum(delta for turn,delta in self.ledger if old['turn']<turn<=e['turn'])
                # Global 1v1 inactivity zapping is not enemy fire. In 1.4.0
                # only bullet-to-bot impacts reset this counter. Public hit
                # callbacks reveal both sides of every such impact in a duel.
                last_hit=max((n for n in self.hit_turns if n<e['turn']),default=0)
                if e['turn']-last_hit>bot.max_inactivity_turns+1:
                    corrections-=.1
                power=old['energy']-e['energy']+corrections
                edge=min(e['x'],e['y'],bot.arena_width-e['x'],bot.arena_height-e['y'])
                collision_suspected=any(old['turn']<turn<=e['turn'] and abs(delta+.6)<1e-8 for turn,delta in self.ledger)
                if (.0999<=power<=3.0001 and e['turn']-old['turn']==1 and
                    e['turn']>=30 and e['turn']-self.last_fire>=11 and
                    not collision_suspected and not (edge<19 and abs(e['speed'])<.01)):
                    power=clamp(power,.1,3)
                    own=next((a for a in self.history if a['turn']==old['turn']),self.history[-1])
                    b=bearing(old['x'],old['y'],own['x'],own['y'])
                    lateral=own['v']*math.sin(math.radians(own['h']-b))
                    sign=1 if lateral>=0 else -1
                    self.next_id+=1
                    w=dict(id=self.next_id,x=old['x'],y=old['y'],time=old['turn'],detected=e['turn'],
                        power=power,speed=20-3*power,bearing=b,sign=sign,mea=math.degrees(math.asin(8/(20-3*power))),
                        lateral=abs(lateral)/8,reacted=False,hit=False)
                    self.waves.append(w);self.last_fire=e['turn']
                    self.log.append(dict(type='inferredShot',**w,rawDrop=old['energy']-e['energy'],correction=corrections))
            self.old=e
            self.ledger=[a for a in self.ledger if a[0]>e['turn']]
        self.waves=[w for w in self.waves if not w['hit'] and t-w['time']<110 and
                    (t-w['time'])*w['speed']<math.hypot(bot.x-w['x'],bot.y-w['y'])+45]

    def danger(self,w,x,y):
        gf=rel(bearing(w['x'],w['y'],x,y)-w['bearing'])/w['mea']*w['sign']
        if self.hits:
            learned=sum(math.exp(-.5*((gf-a)/.18)**2) for a in self.hits)/len(self.hits)
        else:learned=0
        prior=.4*math.exp(-.5*(gf/.22)**2)+.6*math.exp(-.5*((gf-w['lateral'])/.22)**2)
        return (prior+min(len(self.hits),12)*learned)/(1+min(len(self.hits),12))

    def cost(self,bot,w,orbit,brake):
        x,y,h,v=bot.x,bot.y,bot.direction,bot.speed
        # Current scanned position is all that is available; no future target states.
        ex,ey=bot.enemy['x'],bot.enemy['y']
        penalty=0
        for dt in range(1,61):
            error,target=steering(x,y,h,ex,ey,orbit,bot.arena_width,bot.arena_height)
            if brake and dt<=6:target=0
            x,y,h,v=advance(x,y,h,v,error,target)
            margin=min(x,y,bot.arena_width-x,bot.arena_height-y)
            if margin<20:return 100+(20-margin)
            penalty=max(penalty,max(0,55-margin)*.001)
            if (bot.turn_number+dt-w['time'])*w['speed']>=math.hypot(x-w['x'],y-w['y'])-18:
                return self.danger(w,x,y)+penalty,dt
        return .5+penalty,60

    def control(self,bot):
        self.diagnostic=dict(waves=len(self.waves),actions=self.actions,mode=self.mode,selected=None)
        if not bot.enemy or bot.turn_number-bot.enemy['turn']>2:return None
        if bot._startup_guard or (bot._startup_watch and bot.turn_number<35):return None
        active=[w for w in self.waves if 0<(math.hypot(bot.x-w['x'],bot.y-w['y'])-(bot.turn_number-w['time'])*w['speed'])/w['speed']<38]
        if not active:return None
        w=min(active,key=lambda q:math.hypot(bot.x-q['x'],bot.y-q['y'])/q['speed']-bot.turn_number+q['time'])
        eta=(math.hypot(bot.x-w['x'],bot.y-w['y'])-(bot.turn_number-w['time'])*w['speed'])/w['speed']
        if self.mode=='timed':
            if not w['reacted'] and 8<=eta<=16:
                w['reacted']=True
                if bot.turn_number-bot.last_reverse>=20:
                    bot._reverse();self.actions+=1
                    self.log.append(dict(type='evade',turn=bot.turn_number,wave=w['id'],orbit=bot.orbit,eta=eta,brake=False))
            return None
        if bot.turn_number-self.last_choice>=3:
            opts=[]
            for orbit in (bot.orbit,-bot.orbit):
                for brake in ((False,True) if self.mode.endswith('stop') else (False,)):
                    value=self.cost(bot,w,orbit,brake)
                    cost,impact=value if isinstance(value,tuple) else (value,0)
                    cost+=.025*(orbit!=bot.orbit)+.025*brake
                    opts.append((cost,orbit,brake,impact))
            best=min(opts)
            self.choice=(best[1],best[2]);self.last_choice=bot.turn_number
            changed=best[1]!=bot.orbit or best[2]
            if changed:
                self.actions+=1
                self.log.append(dict(type='evade',turn=bot.turn_number,wave=w['id'],orbit=best[1],eta=eta,brake=best[2],cost=best[0]))
            bot.orbit=best[1]
        self.diagnostic.update(actions=self.actions,selected=self.choice,wave=w['id'],eta=eta)
        return 0 if self.choice and self.choice[1] else None
